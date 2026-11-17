import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { before, after, test } from 'node:test';
import { launchBrowser } from './browser-launcher.mjs';
import { base, createCase, review, until, login } from './integrated-helpers.mjs';

let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });
async function fixture(t, options = {}) {
    const context = await browser.newContext({ locale: 'de-CH', ...options });
    const page = await context.newPage();
    page.setDefaultTimeout(30000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    t.after(async () => { await context.close(); assert.deepEqual(errors, []); });
    return { context, page };
}
const staffOptions = () => ({ storageState: process.env.PROPERTYFLOW_E2E_STAFF_STATE });

test('Textverlustwarnung nur für Mitarbeiter; Abbrechen erhält die Eingabe', async t => {
    const { page } = await fixture(t, staffOptions());
    const c = await createCase(page);
    await page.goto(c.staffUrl);
    await page.locator('#internal-message-toggle').check();
    await page.locator('#memo-text').fill('Lokaler Entwurf');
    const warning = page.waitForEvent('dialog');
    const navigation = page.getByRole('link', { name: 'Zur Fallübersicht', exact: true }).click({ noWaitAfter: true });
    const dialog = await warning;
    assert.equal(dialog.type(), 'beforeunload');
    await dialog.dismiss();
    await navigation;
    assert.equal(await page.locator('#memo-text').inputValue(), 'Lokaler Entwurf');
    await page.locator('#memo-text').fill('');
    await page.goto(c.tenantUrl);
    await page.locator('#tenant-message-text').fill('Mieterentwurf');
    let warned = false;
    page.on('dialog', async dialog => { warned = true; await dialog.accept(); });
    await page.reload();
    assert.equal(warned, false);
    assert.equal(await page.locator('#tenant-message-text').inputValue(), '');
});

test('Mobile Fallanlage: echte Validierung, exakte Länge, Escape und idempotenter Wiederholungsauftrag', async t => {
    const { page } = await fixture(t, { viewport: { width: 390, height: 844 } });
    await page.goto(base + '/mieter/fall');
    for (const [id, value] of Object.entries({ title: '<script>Fenster</script>', unit: 'Wohnung 2', email: 'a@example.test', description: 'x'.repeat(2000) })) {
        await page.locator('#' + id).fill(value);
    }
    assert.equal(await page.locator('#description').getAttribute('maxlength'), '2000');
    const operation = await page.locator('[name=operation]').inputValue();
    const csrf = await page.locator('#intake-form [name=csrf]').inputValue();
    const form = { operation, csrf, title: '<script>Fenster</script>', unit: 'Wohnung 2', email: 'a@example.test', description: 'x'.repeat(2001) };
    assert.equal((await page.request.post(base + '/mieter/fall', { form })).status(), 400);
    await page.getByRole('button', { name: 'Anliegen absenden' }).click();
    await page.waitForURL('**/mieter/fall/zugang/*');
    assert.equal(await page.locator('#case-title').innerText(), form.title);
    assert.equal(await page.locator('#case-title script').count(), 0);
    assert.equal(await page.locator('#history p').filter({ hasText: 'x'.repeat(2000) }).count(), 1);
    form.description = 'x'.repeat(2000);
    const retry = await page.request.post(base + '/mieter/fall', { form, maxRedirects: 0 });
    assert.equal(retry.status(), 303);
    assert.equal(base + retry.headers().location, page.url());
    form.title = 'Geändert';
    assert.equal((await page.request.post(base + '/mieter/fall', { form, maxRedirects: 0 })).status(), 409);
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
});

test('Mieternachrichten: Grenze, unklare Antwort, gleicher Auftrag, kein doppelter Verlauf', async t => {
    const { page } = await fixture(t);
    const c = await createCase(page);
    const text = page.locator('#tenant-message-text');
    const button = page.locator('#tenant-message-form button');
    await text.fill('x'.repeat(2001));
    let posts = 0;
    page.on('request', request => { if (request.method() === 'POST') posts++; });
    await button.click();
    assert.equal(posts, 0);
    assert.match(await page.locator('#tenant-message-form [role=status]').innerText(), /2’000/);
    const message = 'Nachricht ' + randomUUID();
    await text.fill(message);
    const requests = [];
    let loseResponse = true;
    await page.route(c.tenantUrl + '/nachrichten', async route => {
        requests.push(new URLSearchParams(route.request().postData()));
        const response = await route.fetch();
        if (loseResponse) { loseResponse = false; await route.abort(); }
        else await route.fulfill({ response });
    });
    await button.click();
    await until(async () => (await page.locator('#tenant-message-form [role=status]').innerText()).includes('nicht bestätigt'), 'unklare Übergabe');
    assert.equal(await text.inputValue(), message);
    assert.equal(await text.evaluate(node => node.readOnly), true);
    await button.click();
    await until(async () => await text.inputValue() === '', 'bestätigte Wiederholung');
    assert.equal(requests.length, 2);
    assert.equal(requests[0].get('operation'), requests[1].get('operation'));
    await until(async () => { await page.reload(); return await page.locator('#history article').filter({ hasText: message }).count() === 1; }, 'einmaliger Verlauf');
});

test('Ohne JavaScript: Fall und Nachricht bleiben über normale Formulare nutzbar', async t => {
    const { page } = await fixture(t, { javaScriptEnabled: false });
    const c = await createCase(page);
    const message = 'Ohne JavaScript ' + randomUUID();
    await page.locator('#tenant-message-text').fill(message);
    const response = page.waitForResponse(r => r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Nachricht senden', exact: true }).click();
    assert.equal((await response).status(), 303);
    await page.waitForURL(c.tenantUrl);
    await page.locator('#history').getByText(message, { exact: true }).waitFor();
});

test('Mitarbeiter: Memo bleibt intern; Zuordnung, Dringlichkeit, Abschlussdialog und Wiedereröffnung', { timeout: 240000 }, async t => {
    const { page: tenant } = await fixture(t);
    const { page } = await fixture(t, staffOptions());
    const c = await createCase(tenant);
    await review(page, c);
    const memo = 'Vertraulich ' + randomUUID();
    await page.locator('#internal-message-toggle').check();
    await page.locator('#memo-text').fill(memo);
    await page.getByRole('button', { name: 'Memo intern speichern' }).click();
    await page.locator('#history').getByText(memo, { exact: true }).waitFor();
    await tenant.reload();
    assert.equal(await tenant.getByText(memo, { exact: true }).count(), 0);
    await page.locator('#assignment-editor summary').click();
    await page.locator('#assigned-unit').fill('Bestätigte Wohnung 6');
    await page.getByRole('button', { name: 'Zuordnung bestätigen' }).click();
    await until(async () => await page.locator('#case-data').getAttribute('data-assigned-unit') === 'Bestätigte Wohnung 6', 'Zuordnung');
    await page.locator('#urgency-edit-title').click();
    await page.getByRole('radio', { name: 'Niedrig', exact: true }).check();
    await page.locator('#urgency-form button[type=submit]').click();
    await until(async () => (await page.locator('#case-urgency').innerText()).includes('Niedrig'), 'Dringlichkeit');
    const list = await page.context().newPage();
    await list.goto(`${base}/mitarbeiter/faelle?q=${encodeURIComponent(memo)}&urgency=Niedrig`);
    assert.equal(await list.locator(`tr[data-case="${c.id}"]`).count(), 1);
    await list.locator('#urgency').selectOption('Hoch');
    await until(async () => await list.locator('#results tbody tr').count() === 0, 'kombinierte Filter');
    await list.close();
    // Der Abschluss wird ausschliesslich über die von Camunda angebotene Aktion bestätigt.
    await page.reload();
    const open = page.locator('[data-direct-close-open], [data-close-open]').filter({ visible: true });
    await open.click();
    assert.equal(await page.getByRole('dialog').isVisible(), true);
    await page.keyboard.press('Escape');
    assert.notEqual(await page.locator('#case-status').innerText(), 'Abgeschlossen');
    await open.click();
    await page.getByRole('dialog').getByRole('button', { name: /Abschliessen|Abschluss bestätigen/ }).click();
    await until(async () => (await page.locator('#case-status').innerText()).trim() === 'Abgeschlossen', 'manueller Abschluss');
    await tenant.reload();
    assert.equal(await tenant.locator('#tenant-message-panel').isVisible(), false);
    const active = await page.request.get(`${base}/mitarbeiter/faelle?q=${c.id}`);
    assert.ok(!(await active.text()).includes(`data-case="${c.id}"`));
    const closed = await page.request.get(`${base}/mitarbeiter/faelle?q=${c.id}&closed=true`);
    assert.ok((await closed.text()).includes(`data-case="${c.id}"`));
    await page.locator('[data-reopen-open]').click();
    await page.locator('#staff-reopen-panel button[type=submit]').click();
    await until(async () => (await page.locator('#case-status').innerText()).trim() !== 'Abgeschlossen', 'Wiedereröffnung');
    await tenant.reload();
    assert.equal(await tenant.locator('#tenant-message-text').inputValue(), '');
});

test('Registrierung: Eigentum, Logout und getrennte Mitarbeiterrolle', async t => {
    const { page, context } = await fixture(t);
    const email = 'registration-' + randomUUID() + '@example.test';
    const initialSession = await page.request.get(base + '/registrieren');
    const sessionHeader = initialSession.headers()['set-cookie'];
    assert.match(sessionHeader, /JSESSIONID=.*; HttpOnly/i);
    assert.match(sessionHeader, /; SameSite=Lax/i);
    await login(page, email, '/registrieren');
    const c = await createCase(page, { accountSubmission: 'true', email: 'forged@example.test' });
    assert.match(c.tenantUrl, /\/mieter\/faelle\//);
    assert.equal((await page.request.get(base + '/mitarbeiter/faelle', { maxRedirects: 0 })).status(), 303);
    const cookie = (await context.cookies()).find(cookie => cookie.name === 'JSESSIONID');
    assert.equal(cookie.httpOnly, true);
    await page.goto(base + '/mieter/faelle');
    const logout = page.waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).pathname === '/abmelden');
    await page.getByRole('button', { name: 'Abmelden', exact: true }).click();
    assert.equal((await logout).status(), 303);
    assert.equal((await page.request.get(c.tenantUrl, { maxRedirects: 0 })).status(), 404);
});

test('Liste: Volltext, Memo, Filter, sieben Sortierungen, Rücksprung und Fehlererholung', { timeout: 120000 }, async t => {
    const { page } = await fixture(t, staffOptions());
    const needle = 'Suche' + randomUUID().replaceAll('-', '');
    const c = await createCase(page, { title: needle + ' Zulu', description: 'Küche, Fenster!' });
    await createCase(page, { title: needle + ' Alpha', description: 'Küche, Fenster!' });
    await page.goto(base + '/mitarbeiter/faelle?q=' + needle);
    assert.equal(await page.locator('#results tbody tr').count(), 2);
    for (const key of ['title', 'unit', 'received', 'contact', 'followUp', 'urgency', 'status']) {
        for (const desc of [false, true]) {
            await page.goto(`${base}/mitarbeiter/faelle?q=${needle}&sort=${key}&desc=${desc}`);
            assert.equal(await page.locator('#results tbody tr').count(), 2);
            assert.equal(await page.locator(`th[aria-sort=${desc ? 'descending' : 'ascending'}]`).count(), 1);
            if (key === 'title') assert.match(await page.locator('tbody tr').first().innerText(), desc ? /Zulu/ : /Alpha/);
        }
    }
    await page.goto(`${base}/mitarbeiter/faelle?q=${needle}&page=999`);
    assert.equal(await page.locator('#results').getAttribute('data-page'), '1');
    await page.locator(`tr[data-case="${c.id}"] .case-link`).click();
    await page.getByRole('link', { name: 'Zur Fallübersicht', exact: true }).click();
    assert.equal(new URL(page.url()).searchParams.get('q'), needle);
    let fail = true;
    await page.route('**/mitarbeiter/faelle?**', route => {
        if (route.request().headers()['x-propertyflow-request'] !== 'partial') return route.continue();
        return fail ? route.fulfill({ status: 503, body: 'Temporär' }) : route.continue();
    });
    await page.locator('#q').fill(needle + ' Alpha');
    await until(async () => (await page.locator('#load-state').innerText()).includes('nicht aktualisiert'), 'Lesefehler');
    assert.equal(await page.locator('#results tbody tr').count(), 2);
    fail = false;
    await page.locator('#q').press('Enter');
    await until(async () => await page.locator('#results tbody tr').count() === 1, 'erneute Suche');
    assert.equal(await page.locator('#q').inputValue(), needle + ' Alpha');
});

test('Tastatur und WCAG: echte öffentliche und geschützte Seiten in schmaler und breiter Ansicht', { timeout: 180000 }, async t => {
    const require = createRequire(path.resolve('target/frontend-tools/package.json'));
    const { default: AxeBuilder } = require('@axe-core/playwright');
    const { page } = await fixture(t, staffOptions());
    const c = await createCase(page);
    const results = [];
    await mkdir('target/frontend-quality', { recursive: true });
    for (const width of [390, 1440]) {
        await page.setViewportSize({ width, height: 900 });
        for (const route of ['/mieter/fall', '/anmelden', '/registrieren', '/mitarbeiter/faelle', c.staffUrl, c.tenantUrl]) {
            await page.goto(route.startsWith('http') ? route : base + route);
            const scan = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
            results.push({ route, width, violations: scan.violations });
            await writeFile('target/frontend-quality/integrated-a11y.json', JSON.stringify(results, null, 2));
            assert.deepEqual(scan.violations.map(item => item.id), [], `${route}, ${width}`);
            const overflow = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1 ? [] :
                [...document.querySelectorAll('body *')].filter(node => node.getBoundingClientRect().right > innerWidth + 1)
                    .slice(0, 8).map(node => `${node.tagName}.${node.className}`));
            assert.deepEqual(overflow, [], route);
        }
    }
    await mkdir('target/frontend-quality', { recursive: true });
    await writeFile('target/frontend-quality/integrated-a11y.json', JSON.stringify(results, null, 2));
    await page.goto(base + '/mieter/fall');
    await page.locator('#title').focus();
    await page.keyboard.press('Tab');
    assert.equal(await page.locator('#unit').evaluate(node => node === document.activeElement), true);
});

