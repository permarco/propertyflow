import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fixture, createCase, change, publish, submit, freeze, until, base } from './ui-test-helpers.mjs';

test('UI1: langsame Leseanfrage läuft nur einmal, versteckter Tab pausiert', async t => {
    const { page, context } = await fixture(t);
    await freeze(page);
    const c = await createCase(page, context, false);
    let count = 0, release;
    const gate = new Promise(resolve => { release = resolve; });
    t.after(() => release());
    await page.route(c.tenantUrl, async route => { count++; await gate; await route.continue(); });
    await page.clock.runFor(20000);
    await until(() => count === 1);
    await page.clock.runFor(40000);
    assert.equal(count, 1);
    await publish(context, c, 'Antwort für den langsamen Abruf');
    release();
    await until(async () => await page.locator('#history article').filter({ hasText: 'Antwort für den langsamen Abruf' }).count() === 1);
    await page.unroute(c.tenantUrl);
    await page.clock.runFor(1);
    await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, get: () => true }); });
    await publish(context, c, 'Neue Nachricht während verborgenem Tab');
    await page.clock.runFor(40000);
    assert.equal(await page.locator('#history article').filter({ hasText: 'Neue Nachricht während verborgenem Tab' }).count(), 0);
    await page.evaluate(() => { delete document.hidden; document.dispatchEvent(new Event('visibilitychange')); });
    await until(async () => await page.locator('#history article').filter({ hasText: 'Neue Nachricht während verborgenem Tab' }).count() === 1);
});

test('UI3: interne Speicherung sperrt Optionen, Fehler erhält Memo, Wiederholung speichert einmal', async t => {
    const { page, context } = await fixture(t);
    const c = await createCase(page, context);
    await page.locator('#message').fill('Internes Memo bleibt bei Fehler');
    await page.locator('#internal').check();
    let fail = true;
    await page.route('**/nachrichten', async route => {
        if (fail) await route.fulfill({ status: 503, contentType: 'text/html', body: '<p role="alert">Memo nicht gespeichert</p>' });
        else await route.continue();
    });
    assert.equal(await submit(page, page.locator('#send')), 503);
    assert.equal(await page.locator('#internal').isChecked(), true);
    assert.equal(await page.locator('#message').inputValue(), 'Internes Memo bleibt bei Fehler');
    assert.equal(await page.locator('#history article').count(), 1);
    fail = false;
    await submit(page, page.locator('#send'));
    assert.equal(await page.locator('#internal').isChecked(), false);
    assert.equal(await page.locator('#message').inputValue(), '');
    assert.equal(await page.locator('#history article').filter({ hasText: 'Internes Memo bleibt bei Fehler' }).count(), 1);
    assert.ok(!(await (await context.request.get(c.tenantUrl)).text()).includes('Internes Memo bleibt bei Fehler'));
});

test('UI3: verspätetes Senden nach Abschluss wird abgewiesen und Mitarbeiterentwurf erhalten', async t => {
    const { page, context } = await fixture(t);
    const c = await createCase(page, context);
    await page.locator('#message').fill('Zu spät gesendeter Text');
    await change(context, c, 'close');
    assert.equal(await submit(page, page.locator('#send')), 409);
    assert.equal(await page.locator('#message').inputValue(), 'Zu spät gesendeter Text');
    assert.equal(await page.locator('#send').isDisabled(), true);
    assert.equal(await page.locator('#history article').filter({ hasText: 'Zu spät gesendeter Text' }).count(), 0);
});

test('UI1: verlorene Speicherantwort und identischer Retry erzeugen keine zweite Nachricht', async t => {
    const { page, context } = await fixture(t);
    await createCase(page, context, false);
    await page.locator('#message').fill('Einmal gespeicherte Nachricht');
    let first = true;
    await page.route('**/nachrichten', async route => {
        if (first) {
            first = false;
            const response = await route.fetch();
            assert.equal(response.status(), 200);
            await route.abort('failed'); // Server already saved; browser did not receive confirmation.
        } else await route.continue();
    });
    await page.locator('#send').click();
    await until(async () => await page.locator('#send').isEnabled());
    assert.equal(await page.locator('#message').inputValue(), 'Einmal gespeicherte Nachricht');
    await submit(page, page.locator('#send'));
    assert.equal(await page.locator('#history article').filter({ hasText: 'Einmal gespeicherte Nachricht' }).count(), 1);
    assert.equal(await page.locator('#message').inputValue(), '');
});

test('UI2: Falllink und Rücknavigation erhalten die vollständige Listenauswahl', async t => {
    const { page } = await fixture(t);
    const url = `${base}/mitarbeiter/faelle?q=Heiz&urgency=Hoch&closed=true&sort=received&desc=true&page=1`;
    await page.goto(url);
    const id = await page.locator('.case-link').first().textContent();
    await page.locator('.case-link').first().click();
    assert.equal(await page.locator('#case-head .eyebrow').textContent(), id);
    await page.getByRole('link', { name: 'Zur Fallübersicht' }).click();
    assert.equal(page.url(), url);
    assert.equal(await page.locator('#q').inputValue(), 'Heiz');
    assert.equal(await page.locator('#urgency').inputValue(), 'Hoch');
    assert.equal(await page.locator('#closed').isChecked(), true);
});
