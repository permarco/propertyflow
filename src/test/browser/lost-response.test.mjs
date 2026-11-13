import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { fixture, base, createCase, editor, freeze, submit, until, staffStorageState } from './ui-test-helpers.mjs';

test('UI1: verlorene erfolgreiche Anlageantwort lässt sich ohne zweiten Fall im Browser wiederholen', { timeout: 30000 }, async t => {
    const { page, browser } = await fixture(t, { staff: false });
    await page.goto(base + '/mieter/fall');
    const fields = { title: `Verlorene Antwort ${randomUUID()}`, unit: 'Wohnung 4', email: 'retry@example.test', description: 'Testbeschreibung' };
    for (const [name, value] of Object.entries(fields)) await page.locator('#' + name).fill(value);
    const operation = await page.locator('input[name=operation]').inputValue();
    let savedUrl;
    await page.route('**/mieter/fall', async route => {
        if (route.request().method() !== 'POST') return route.continue();
        const response = await route.fetch({ maxRedirects: 0 });
        assert.equal(response.status(), 303);
        savedUrl = base + response.headers().location;
        await route.abort('failed');
    });
    await page.getByRole('button', { name: /Anliegen absenden/ }).click();
    await until(() => Boolean(savedUrl));
    await page.locator('#form-error').waitFor({ timeout: 5000 });
    for (const [name, value] of Object.entries(fields)) assert.equal(await page.locator('#' + name).inputValue(), value);
    assert.equal(await page.locator('input[name=operation]').inputValue(), operation);
    await page.unroute('**/mieter/fall');
    await page.getByRole('button', { name: /Anliegen absenden/ }).click();
    await page.waitForURL(savedUrl);
    assert.equal(await page.locator('#history article').count(), 1);
    const observer = await browser.newContext({ storageState: await staffStorageState(browser) });
    const list = await observer.newPage();
    await list.goto(base + '/mitarbeiter/faelle?q=' + encodeURIComponent(fields.title));
    assert.equal(await list.locator('#results tr[data-case]').count(), 1);
    await observer.close();
});

test('UI3: verlorene erfolgreiche Feldantwort lässt sich ohne zweiten Verlaufseintrag wiederholen', { timeout: 30000 }, async t => {
    const { page, context } = await fixture(t);
    const c = await createCase(page, context);
    await freeze(page);
    const assignment = editor(page, '#assigned-unit');
    await assignment.locator('summary').click();
    await page.locator('#assigned-unit').fill('Bestätigte Wohnung 7');
    const operation = await assignment.locator('input[name=operation]').inputValue();
    const version = Number(await page.locator('#case-head').getAttribute('data-version'));
    let saved = false;
    await page.route('**/aktion', async route => {
        const response = await route.fetch({ maxRedirects: 0 });
        assert.equal(response.status(), 303);
        saved = true;
        await route.abort('failed');
    });
    await assignment.getByRole('button', { name: 'Zuordnung bestätigen' }).click();
    await until(async () => saved && await page.locator('#save-state.error').count() === 1);
    assert.equal(await page.locator('#assigned-unit').inputValue(), 'Bestätigte Wohnung 7');
    assert.equal(await assignment.locator('input[name=operation]').inputValue(), operation);
    await page.unroute('**/aktion');
    assert.equal(await submit(page, assignment.getByRole('button', { name: 'Zuordnung bestätigen' })), 303);
    await page.reload();
    assert.match(await assignment.locator('summary').textContent(), /Bestätigte Wohnung 7/);
    assert.equal(Number(await page.locator('#case-head').getAttribute('data-version')), version + 1);
    assert.equal(await page.locator('#history article').count(), 2);
});
