import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fixture, createCase, publish, change, editor, submit, freeze, until, base } from './ui-test-helpers.mjs';

for (const screen of ['tenant', 'list', 'staff']) {
    test(`${screen}: Zugriffsverweigerung entfernt Falldaten und stoppt Abrufe`, async t => {
        const { page, context } = await fixture(t);
        await freeze(page);
        const c = await createCase(page, context, screen === 'staff');
        if (screen === 'list') await page.goto(`${base}/mitarbeiter/faelle`);
        else await page.locator('#message').fill('Lokaler Entwurf');
        let requests = 0;
        await page.route(screen === 'list' ? '**/mitarbeiter/faelle*' : page.url(), async route => {
            requests++;
            await route.fulfill({ status: 403, body: 'Zugriff verweigert' });
        });
        await page.clock.runFor(20000);
        const notice = page.locator(screen === 'list' ? '#load-state' : '#save-state');
        await until(async () => (await notice.textContent()).includes('Zugriff verweigert'));
        if (screen === 'list') assert.equal(await page.locator('#results tr').count(), 0);
        else {
            assert.equal(await page.locator('#history').isHidden(), true);
            assert.equal(await page.locator('#case-head').isHidden(), true);
            assert.equal(await page.locator('#send').isDisabled(), true);
            assert.equal(await page.locator('#message').inputValue(), 'Lokaler Entwurf');
        }
        await page.clock.runFor(60000);
        assert.equal(requests, 1);
    });
}

test('UI1: ungültiger Link zeigt keine Fallinhalte und bietet keinen Composer', async t => {
    const { page } = await fixture(t);
    assert.equal((await page.goto(`${base}/mieter/fall/zugang/ungueltig`)).status(), 404);
    assert.equal(await page.locator('#history').count(), 0);
    assert.equal(await page.locator('#message').count(), 0);
});

test('UI3: ungültige Zuordnung und vergangener Termin verändern keine gespeicherten Werte', async t => {
    const { page, context } = await fixture(t);
    await createCase(page, context);
    const version = await page.locator('#case-head').getAttribute('data-version');
    const assignment = editor(page, '#assigned-unit');
    await assignment.locator('summary').click();
    assert.equal(await page.locator('#assigned-unit').inputValue(), 'Originalwohnung 3', 'Ursprüngliche Angabe ist vorbelegt');
    await page.locator('#assigned-unit').fill('x'.repeat(201));
    assert.equal(await submit(page, assignment.getByRole('button', { name: 'Zuordnung bestätigen' })), 400);
    assert.equal(await page.locator('#case-head').getAttribute('data-version'), version);
    await assignment.getByRole('button', { name: 'Abbrechen' }).click();
    const follow = editor(page, '#followup-date');
    await follow.locator('summary').click();
    await page.locator('#followup-date').fill('2000-01-01');
    assert.equal(await submit(page, follow.getByRole('button', { name: 'Speichern', exact: true })), 400);
    assert.equal(await page.locator('#case-head').getAttribute('data-version'), version);
    assert.match(await page.locator('#save-state').textContent(), /Zukunft/);
});

test('UI3: nach Polling und Abbrechen eines Editors wird der aktuelle Wert angezeigt', async t => {
    const { page, context } = await fixture(t);
    await freeze(page);
    const c = await createCase(page, context);
    const assignment = editor(page, '#assigned-unit');
    await assignment.locator('summary').click();
    await page.locator('#assigned-unit').fill('Nicht gespeicherte Auswahl');
    await change(context, c, 'unit', 'Neue bestätigte Wohnung');
    await page.clock.runFor(20000);
    await until(async () => await page.locator('#updates').isVisible());
    assert.equal(await page.locator('#assigned-unit').inputValue(), 'Nicht gespeicherte Auswahl');
    await page.locator('#updates button').click();
    await assignment.getByRole('button', { name: 'Abbrechen' }).click();
    await until(async () => (await assignment.locator('summary').textContent()).includes('Neue bestätigte Wohnung'));
    await assignment.locator('summary').click();
    assert.equal(await page.locator('#assigned-unit').inputValue(), 'Neue bestätigte Wohnung');
});

test('UI1/UI2/UI3: HTML in Nachrichten, Betreff und Objekt bleibt Text', async t => {
    const { page, context } = await fixture(t);
    const c = await createCase(page, context);
    const payload = '<img src=x onerror=alert(1)><script>alert(2)</script>';
    let dialogs = 0;
    page.on('dialog', async d => { dialogs++; await d.dismiss(); });
    await publish(context, c, payload);
    await change(context, c, 'unit', payload);
    for (const url of [c.staffUrl, c.tenantUrl, `${base}/mitarbeiter/faelle?q=${c.id}`]) {
        await page.goto(url);
        assert.equal(await page.locator('#history img, #history script, #results img, #results script, #case-sidebar img, #case-sidebar script').count(), 0);
        assert.ok((await page.locator('main').textContent()).includes(payload));
    }
    assert.equal(dialogs, 0);
});

test('Ohne JavaScript: Anliegen, Nachricht sowie gefilterte Listennavigation funktionieren', async t => {
    const { page, context } = await fixture(t, { javaScriptEnabled: false });
    const c = await createCase(page, context, false);
    await page.locator('#message').fill('Nachricht ohne JavaScript');
    await Promise.all([page.waitForNavigation(), page.locator('#send').click()]);
    assert.equal(await page.locator('#history article').filter({ hasText: 'Nachricht ohne JavaScript' }).count(), 1);
    assert.equal(await page.locator('#message').inputValue(), '');
    await page.goto(`${base}/mitarbeiter/faelle`);
    await page.locator('#q').fill(c.id);
    await Promise.all([page.waitForNavigation(), page.getByRole('button', { name: 'Suchen / Filter anwenden' }).click()]);
    assert.equal(await page.locator('#results tr[data-case]').count(), 1);
    await page.locator('.case-link').click();
    await page.waitForURL('**/' + c.id + '?**');
    assert.equal(await page.locator('#case-head .eyebrow').textContent(), c.id);
    await page.getByRole('link', { name: 'Zur Fallübersicht' }).click();
    assert.equal(await page.locator('#q').inputValue(), c.id);
});

for (const width of [390, 1440]) {
    test(`UI1/UI2/UI3: Layout und erreichbare Aktionen bei ${width}px`, async t => {
        const { page, context } = await fixture(t, { viewport: { width, height: 900 } });
        const c = await createCase(page, context);
        for (let i = 0; i < 8; i++) await publish(context, c, `Verlauf ${i}: ` + 'Längere Nachricht. '.repeat(15));
        for (const url of [c.tenantUrl, c.staffUrl, `${base}/mitarbeiter/faelle`]) {
            await page.goto(url);
            assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'Kein horizontales Seitenscrollen');
            if (url.includes('/faelle') && url !== c.staffUrl) {
                if (width < 1200) {
                    assert.equal(await page.locator('#sort').isVisible(), true);
                    assert.equal(await page.locator('#results tr').filter({ has: page.locator('td') }).first().locator('td').count(), 7);
                }
            } else {
                await page.locator('#message').fill('Entwurf\n'.repeat(60));
                assert.equal(await page.locator('#send').isEnabled(), true);
                if (width >= 1200) {
                    const before = await page.locator('#composer').boundingBox();
                    await page.locator('#history').evaluate(el => { el.scrollTop = 0; });
                    const after = await page.locator('#composer').boundingBox();
                    assert.equal(after.y, before.y);
                    assert.ok((await page.locator('#history').boundingBox()).height >= 200);
                    assert.ok((await page.locator('#send').boundingBox()).y < 900);
                } else {
                    const head = await page.locator('#case-head').boundingBox();
                    const history = await page.locator('#history').boundingBox();
                    assert.ok(history.y > head.y);
                    assert.ok((await page.locator('#message').boundingBox()).height <= 242);
                    if (url === c.staffUrl) assert.ok((await page.locator('#case-sidebar').boundingBox()).y < history.y);
                }
                await page.locator('#message').fill(''); // Avoid the expected staff navigation warning.
            }
        }
    });
}
