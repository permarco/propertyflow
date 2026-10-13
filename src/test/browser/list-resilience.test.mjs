import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fixture, base, freeze, until, change } from './ui-test-helpers.mjs';

async function seedReference(page) {
    await page.goto(`${base}/mitarbeiter/faelle/REQ-2026-001`);
    return { staffUrl: `${base}/mitarbeiter/faelle/REQ-2026-001`, csrf: await page.locator('#message-form input[name=csrf]').inputValue() };
}
test('UI2: Polling erhält Auswahl und Leseposition, zeigt Änderungen und entfernt abgeschlossene Fälle', async t => {
    const { page, context } = await fixture(t);
    await freeze(page);
    const c = await seedReference(page);
    await page.goto(`${base}/mitarbeiter/faelle?q=Heizung&sort=received&desc=false`);
    await page.locator('.table-card').evaluate(el => { el.scrollTop = 350; });
    const top = await page.locator('.table-card').evaluate(el => el.scrollTop);
    assert.ok(top > 0);
    await change(context, c, 'urgency', 'Niedrig');
    await page.clock.runFor(20000);
    await until(async () => (await page.locator('[data-case="REQ-2026-001"] [data-label="Dringlichkeit"]').textContent()).trim() === 'Niedrig');
    assert.ok(Math.abs(await page.locator('.table-card').evaluate(el => el.scrollTop) - top) <= 1);
    assert.equal(await page.locator('#q').inputValue(), 'Heizung');
    assert.equal(new URL(page.url()).searchParams.get('sort'), 'received');
    assert.equal(new URL(page.url()).searchParams.get('desc'), 'false');
    await change(context, c, 'close');
    await page.clock.runFor(20000);
    await until(async () => await page.locator('[data-case="REQ-2026-001"]').count() === 0);
    await change(context, c, 'reopen');
    await page.clock.runFor(20000);
    await until(async () => await page.locator('[data-case="REQ-2026-001"]').count() === 1);
    await change(context, c, 'urgency', 'Hoch');
});

test('UI2: Polling bleibt auf Seite 2 und korrigiert eine entfallene letzte Seite', async t => {
    const { page, context } = await fixture(t);
    await freeze(page);
    await page.goto(`${base}/mitarbeiter/faelle?q=REQ&closed=true&sort=received&desc=true&page=2`);
    await page.clock.runFor(20000);
    await until(async () => (await page.locator('#load-state').textContent()).startsWith('Zuletzt aktualisiert'));
    assert.equal(await page.locator('#results').getAttribute('data-page'), '2');
    assert.equal(await page.locator('#closed').isChecked(), true);
    assert.equal(new URL(page.url()).searchParams.get('page'), '2');
    // Simulate a valid server response whose filtered result now has only one page.
    const response = await context.request.get(`${base}/mitarbeiter/faelle?q=REQ2026001&closed=true&sort=received&desc=true`);
    const html = await response.text();
    await page.route('**/mitarbeiter/faelle?**', route => route.fulfill({ status: 200, contentType: 'text/html', body: html }));
    await page.clock.runFor(20000);
    await until(async () => await page.locator('#results').getAttribute('data-page') === '1');
    assert.match(await page.locator('#load-state').textContent(), /bisherige Seite.*nicht mehr verfügbar/);
    assert.equal(new URL(page.url()).searchParams.get('page'), '1');
});

test('UI2: Netzwerkfehler und Rate Limit erhalten Daten; Wiederholen funktioniert', async t => {
    const { page } = await fixture(t);
    await freeze(page);
    await page.goto(`${base}/mitarbeiter/faelle`);
    const original = await page.locator('#results').innerText();
    let mode = 'network';
    let requests = 0;
    await page.route('**/mitarbeiter/faelle*', async route => {
        requests++;
        if (mode === 'network') await route.abort('internetdisconnected');
        else if (mode === 'limit') await route.fulfill({ status: 429, body: 'Zu viele Anfragen' });
        else await route.continue();
    });
    await page.clock.runFor(20000);
    await until(async () => await page.getByRole('button', { name: 'Erneut versuchen' }).count() === 1);
    assert.equal(await page.locator('#results').innerText(), original);
    mode = 'limit';
    await page.getByRole('button', { name: 'Erneut versuchen' }).click();
    await until(() => requests === 2);
    await until(async () => await page.getByRole('button', { name: 'Erneut versuchen' }).isEnabled());
    assert.equal(await page.locator('#results').innerText(), original);
    await page.clock.runFor(10000);
    assert.equal(requests, 2, 'Kein sofortiger Retry-Loop');
    mode = 'success';
    await page.getByRole('button', { name: 'Erneut versuchen' }).click();
    await until(async () => (await page.locator('#load-state').textContent()).startsWith('Zuletzt aktualisiert'));
    assert.equal(await page.getByRole('button', { name: 'Erneut versuchen' }).count(), 0);
});

test('UI2: verspätete Suche überschreibt keine neuere Filterauswahl', async t => {
    const { page } = await fixture(t);
    await freeze(page);
    await page.goto(`${base}/mitarbeiter/faelle`);
    let release, intercepted = false;
    const gate = new Promise(resolve => { release = resolve; });
    t.after(() => release());
    await page.route('**/mitarbeiter/faelle?**', async route => {
        const params = new URL(route.request().url()).searchParams;
        if (params.get('q') === 'Heiz' && !params.get('urgency')) {
            intercepted = true;
            const response = await route.fetch();
            await gate;
            await route.fulfill({ response }).catch(() => {}); // Aborted old request is expected.
        } else await route.continue();
    });
    await page.locator('#q').fill('He');
    await page.clock.runFor(600);
    await page.locator('#q').fill('Heiz');
    await page.clock.runFor(999);
    assert.equal(intercepted, false);
    await page.clock.runFor(1);
    await until(() => intercepted);
    await page.locator('#urgency').selectOption('Hoch');
    await until(async () => (await page.locator('#load-state').textContent()).startsWith('Zuletzt aktualisiert'));
    const result = await page.locator('#results').innerText();
    release();
    await page.clock.runFor(1000);
    assert.equal(await page.locator('#results').innerText(), result);
    assert.equal(await page.locator('#q').inputValue(), 'Heiz');
    assert.equal(await page.locator('#urgency').inputValue(), 'Hoch');
    assert.ok((await page.locator('[data-label="Dringlichkeit"]').allTextContents()).every(v => v.trim() === 'Hoch'));
});

test('UI2: langsamer Abruf bleibt einzeln; versteckter Tab pausiert und Rückkehr aktualisiert', async t => {
    const { page } = await fixture(t);
    await freeze(page);
    await page.goto(`${base}/mitarbeiter/faelle`);
    let count = 0, release;
    const gate = new Promise(resolve => { release = resolve; });
    t.after(() => release());
    await page.route('**/mitarbeiter/faelle*', async route => { count++; await gate; await route.continue(); });
    await page.clock.runFor(20000);
    await until(() => count === 1);
    await page.clock.runFor(40000);
    assert.equal(count, 1);
    release();
    await until(async () => (await page.locator('#load-state').textContent()).startsWith('Zuletzt aktualisiert'));
    // Model the browser's visibility signal deterministically in headless execution.
    await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, get: () => true }); document.dispatchEvent(new Event('visibilitychange')); });
    await page.clock.runFor(40000);
    assert.equal(count, 1);
    await page.evaluate(() => { delete document.hidden; document.dispatchEvent(new Event('visibilitychange')); });
    await until(() => count === 2);
});
