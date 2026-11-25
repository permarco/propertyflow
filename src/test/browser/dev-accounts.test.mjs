import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { test } from 'node:test';
import { setTimeout as delay } from 'node:timers/promises';
import { launchBrowser } from './browser-launcher.mjs';

const base = process.env.PROPERTYFLOW_E2E_URL || 'http://127.0.0.1:8081';
const mail = process.env.PROPERTYFLOW_E2E_MAIL_URL || 'http://127.0.0.1:8025';
const reports = process.env.PROPERTYFLOW_E2E_REPORTS || 'target/dev-verification';

async function until(fn, label) {
    const deadline = Date.now() + 90000;
    do {
        const result = await fn();
        if (result) return result;
        await delay(500);
    } while (Date.now() < deadline);
    throw new Error(`Zeitüberschreitung: ${label}`);
}

async function inboxIds() {
    return new Set((await (await fetch(`${mail}/api/v1/messages`)).json()).messages.map(entry => entry.ID));
}

async function codeFor(email, before, subject = 'Ihr Einmalcode für PropertyFlow') {
    return until(async () => {
        const inbox = await (await fetch(`${mail}/api/v1/messages`)).json();
        const found = inbox.messages.find(entry => !before.has(entry.ID) && entry.Subject === subject
            && entry.To.some(to => to.Address === email));
        if (!found) return null;
        const content = await (await fetch(`${mail}/api/v1/message/${found.ID}`)).json();
        return content.Text.match(/\b[0-9]{6}\b/)?.[0];
    }, 'Mailcode');
}

async function signIn(page, route, email) {
    await page.goto(base + route);
    const before = await inboxIds();
    await page.getByLabel('E-Mail-Adresse', { exact: true }).fill(email);
    await page.getByRole('button', { name: 'Code anfordern', exact: true }).click();
    await page.getByLabel('Einmalcode', { exact: true }).fill(await codeFor(email, before));
    await page.getByRole('button', { name: route === '/registrieren' ? 'E-Mail bestätigen' : 'Anmelden', exact: true }).click();
    await page.waitForURL(route === '/anmelden' ? '**/mitarbeiter/faelle' : '**/mieter/faelle');
}

async function intake(page, email, title) {
    await page.goto(`${base}/mieter/fall`);
    await page.locator('#title').fill(title);
    await page.locator('#unit').fill('Testweg 16, Wohnung 3');
    if (!(await page.locator('#email').getAttribute('readonly'))) {
        if (!(await page.locator('#email').evaluate(input => input.readOnly))) await page.locator('#email').fill(email);
    }
    await page.locator('#description').fill('Das Fenster klemmt. Bitte um Prüfung durch die Verwaltung.');
    await page.getByRole('button', { name: 'Anliegen absenden' }).click();
    await page.waitForURL(url => url.pathname.includes('/zugang/') || /^\/mieter\/faelle\//.test(url.pathname));
    return { id: (await page.locator('.eyebrow').innerText()).trim(), url: page.url() };
}

test('Dauerhafte Konten, Rechteentzug und geprüfter Adresswechsel im integrierten Devbetrieb', {
    skip: process.env.PROPERTYFLOW_DEV_SMOKE !== 'true', timeout: 300000,
}, async t => {
    const browser = await launchBrowser();
    t.after(() => browser.close());
    const suffix = Date.now();
    const email = `konto.${suffix}@example.test`;
    const nextEmail = `kontakt.${suffix}@example.test`;
    const workerEmail = `mitarbeiter.${suffix}@example.test`;
    const guest = await (await browser.newContext()).newPage();
    const tenant = await (await browser.newContext()).newPage();
    const staff = await (await browser.newContext()).newPage();
    const worker = await (await browser.newContext()).newPage();
    const nextTenant = await (await browser.newContext()).newPage();
    const errors = [];
    for (const page of [guest, tenant, staff, worker, nextTenant]) page.on('pageerror', error => errors.push(error.message));

    const first = await intake(guest, email, `[Kontentest ${suffix}] Erster Fall`);
    const second = await intake(guest, email, `[Kontentest ${suffix}] Zweiter Fall`);
    const foreign = await intake(guest, `fremd.${suffix}@example.test`, `[Kontentest ${suffix}] Fremder Fall`);
    await signIn(tenant, '/registrieren', email);
    assert.equal(await tenant.locator('main section.card').count(), 2);
    assert.equal((await tenant.request.get(`${base}/mieter/faelle/${foreign.id}`)).status(), 404);
    const accountCase = await intake(tenant, email, `[Kontentest ${suffix}] Aus Mieterkonto`);
    assert.match(accountCase.url, /\/mieter\/faelle\//);

    await staff.goto(`${base}/anmelden`);
    const adminEmail = (await staff.locator('main > p strong').innerText()).trim();
    await signIn(staff, '/anmelden', adminEmail);
    await until(async () => {
        await staff.goto(`${base}/mitarbeiter/faelle/${first.id}`);
        return (await staff.locator('#case-status').innerText()).trim() === 'Mitarbeiterprüfung erforderlich';
    }, 'Manueller Fall ohne KI');
    await tenant.goto(`${base}/mieter/faelle/${first.id}`);
    await tenant.locator('#tenant-message-text').fill('Ergänzung aus dem verifizierten Mieterkonto.');
    await tenant.getByRole('button', { name: 'Nachricht senden', exact: true }).click();
    await tenant.locator('#history').getByText('Ergänzung aus dem verifizierten Mieterkonto.', { exact: true }).waitFor();

    await staff.goto(`${base}/mitarbeiter/konten`);
    const adminCsrf = await staff.locator('form[action="/mitarbeiter/konten"] input[name=csrf]').inputValue();
    assert.equal((await staff.request.post(`${base}/mitarbeiter/konten`, {
        form: { csrf: adminCsrf, name: 'Unerlaubte Rolle', email: `no.${suffix}@example.test`, administrator: 'true' },
    })).status(), 400);
    await staff.getByLabel('Name', { exact: true }).fill(`Testmitarbeiter ${suffix}`);
    await staff.getByLabel('E-Mail-Adresse', { exact: true }).fill(workerEmail);
    await staff.getByRole('button', { name: 'Mitarbeiterkonto anlegen', exact: true }).click();
    await staff.getByText(workerEmail, { exact: true }).waitFor();
    assert.equal((await staff.request.post(`${base}/mitarbeiter/konten`, {
        form: { csrf: adminCsrf, name: 'Doppeltes Konto', email: workerEmail },
    })).status(), 409);
    await signIn(worker, '/anmelden', workerEmail);
    assert.equal((await worker.request.get(`${base}/mitarbeiter/konten`)).status(), 403);
    assert.equal((await worker.request.get(`${base}/mitarbeiter/fallzugaenge`)).status(), 403);
    const workerCsrf = await worker.locator('form[action="/abmelden"] input[name=csrf]').inputValue();
    assert.equal((await worker.request.post(`${base}/mitarbeiter/konten`, {
        form: { csrf: workerCsrf, name: 'Unberechtigt', email: `no.${suffix}@example.test` },
    })).status(), 403);
    const workerCard = staff.locator('section.card').filter({ hasText: workerEmail });
    await workerCard.getByRole('button', { name: 'Zugriff entziehen', exact: true }).click();
    await staff.locator('section.card').filter({ hasText: workerEmail }).getByText('Mitarbeiterzugriff entzogen', { exact: true }).waitFor();
    assert.equal((await worker.request.get(`${base}/mitarbeiter/faelle`)).status(), 403);

    await signIn(nextTenant, '/registrieren', nextEmail);
    await staff.goto(`${base}/mitarbeiter/fallzugaenge?caseId=${second.id}`);
    const before = await inboxIds();
    await staff.getByLabel('Bestätigte Kontaktadresse für den Ersatzlink', { exact: true }).fill(nextEmail);
    await staff.getByLabel('Wie wurde die Identität desselben Mieters unabhängig geprüft?', { exact: true })
        .fill('Synthetischer Test: Mietvertrag abgeglichen und Rückruf über bekannte Nummer durchgeführt.');
    await staff.getByRole('button', { name: 'Bestätigungscode senden', exact: true }).click();
    const command = {};
    for (const key of ['csrf', 'version', 'verificationId', 'operation', 'email']) {
        command[key] = await staff.locator(`form[action$="/anwenden"] input[name=${key}]`).inputValue();
    }
    const commandUrl = `${base}/mitarbeiter/fallzugaenge/${second.id}/kontaktadresse-aendern`;
    assert.equal((await staff.request.post(commandUrl, { form: command })).status(), 403);
    assert.equal((await staff.request.post(commandUrl, { form: { ...command, csrf: 'wrong' } })).status(), 403);
    await staff.getByLabel('Bestätigungscode', { exact: true }).fill(await codeFor(nextEmail, before, 'PropertyFlow: Kontaktadresse bestätigen'));
    const linkMailBefore = await inboxIds();
    await staff.getByRole('button', { name: 'Kontakt bestätigen und Link ersetzen', exact: true }).click();
    await staff.getByText('Der bisherige Link ist gesperrt.', { exact: false }).waitFor();
    assert.equal((await staff.request.post(commandUrl, { form: command })).status(), 204);
    assert.equal((await staff.request.post(commandUrl, { form: { ...command, operation: 'different' } })).status(), 409);
    assert.equal((await guest.request.get(second.url)).status(), 404);
    assert.equal((await tenant.request.get(`${base}/mieter/faelle/${second.id}`)).status(), 404);
    assert.equal((await nextTenant.request.get(`${base}/mieter/faelle/${second.id}`)).status(), 200);
    const replacement = await until(async () => {
        const inbox = await (await fetch(`${mail}/api/v1/messages`)).json();
        for (const entry of inbox.messages.filter(entry => !linkMailBefore.has(entry.ID) && entry.To.some(to => to.Address === nextEmail))) {
            const detail = await (await fetch(`${mail}/api/v1/message/${entry.ID}`)).json();
            if (detail.Text.includes('Fall-Link wurde nach Prüfung ersetzt')) return detail.Text.match(/https?:\/\/[^\s<>]+/g)?.find(link => link.startsWith(`${base}/mieter/fall/zugang/`));
        }
        return null;
    }, 'Ersatzlinkversand');
    assert.equal((await guest.request.get(replacement)).status(), 200);
    await tenant.goto(`${base}/mieter/faelle`);
    assert.equal(await tenant.locator('main section.card').count(), 2);
    await mkdir(reports, { recursive: true });
    await tenant.screenshot({ path: `${reports}/tenant-account.png`, fullPage: true });
    await staff.screenshot({ path: `${reports}/access-administration.png`, fullPage: true });
    const logout = tenant.waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).pathname === '/abmelden');
    await tenant.getByRole('button', { name: 'Abmelden', exact: true }).click();
    assert.equal((await logout).status(), 303);
    assert.equal((await tenant.request.get(`${base}/mieter/faelle/${first.id}`)).status(), 404);
    assert.deepEqual(errors, []);
    await writeFile(`${reports}/accounts-result.json`, JSON.stringify({
        testedAt: new Date().toISOString(), first: first.id, transferred: second.id, accountCase: accountCase.id,
        email, nextEmail, workerEmail, checks: ['registration', 'own-cases', 'foreign-case-denied', 'account-intake',
            'account-message', 'staff-login', 'staff-create', 'staff-revoke', 'administration-denied',
            'contact-verification', 'old-link-revoked', 'account-access-changed', 'replacement-mail', 'logout'],
        pageErrors: errors,
    }, null, 2));
});

test('Erhaltene Mieterkonten und Fallzuordnung nach vollständigem Neustart', {
    skip: process.env.PROPERTYFLOW_DEV_ACCOUNT_RESTART !== 'true', timeout: 120000,
}, async t => {
    const evidence = JSON.parse(await readFile(`${reports}/accounts-result.json`, 'utf8'));
    const browser = await launchBrowser();
    t.after(() => browser.close());
    const previous = await (await browser.newContext()).newPage();
    const current = await (await browser.newContext()).newPage();
    await signIn(previous, '/mieter/anmelden', evidence.email);
    assert.equal((await previous.request.get(`${base}/mieter/faelle/${evidence.first}`)).status(), 200);
    assert.equal((await previous.request.get(`${base}/mieter/faelle/${evidence.accountCase}`)).status(), 200);
    assert.equal((await previous.request.get(`${base}/mieter/faelle/${evidence.transferred}`)).status(), 404);
    await signIn(current, '/mieter/anmelden', evidence.nextEmail);
    assert.equal((await current.request.get(`${base}/mieter/faelle/${evidence.transferred}`)).status(), 200);
    await writeFile(`${reports}/account-restart-result.json`, JSON.stringify({
        testedAt: new Date().toISOString(), checks: ['existing-account-login', 'original-cases-retained', 'contact-transfer-retained'],
    }, null, 2));
});
