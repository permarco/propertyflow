import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { base, fixture, createCase, submit, until } from './ui-test-helpers.mjs';

test('Core: Gast reicht mobil ein, korrigiert Pflichtfeld und sendet genau eine Nachricht', { timeout: 30000 }, async t => {
    const { page } = await fixture(t, { staff: false, viewport: { width: 390, height: 844 } });
    await page.goto(base + '/mieter/fall');
    const title = `Kernablauf ${randomUUID()}`;
    await page.locator('#title').fill(title);
    await page.locator('#unit').fill('Wohnung 3');
    await page.locator('#email').fill('core-guest@example.test');
    let posts = 0;
    page.on('request', request => { if (request.method() === 'POST') posts++; });
    await page.getByRole('button', { name: 'Anliegen absenden' }).click();
    assert.equal(await page.locator('#description').evaluate(element => element.validity.valueMissing), true);
    assert.equal(posts, 0);
    await page.locator('#description').fill('Heizung bleibt kalt.\nBitte prüfen.');
    await page.getByRole('button', { name: 'Anliegen absenden' }).click();
    await page.waitForURL('**/mieter/fall/zugang/*');
    assert.equal(posts, 1);
    assert.equal(await page.getByRole('heading', { name: title, exact: true }).isVisible(), true);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.locator('#message').fill('Auch im Schlafzimmer ist es kalt.');
    assert.equal(await submit(page, page.locator('#send')), 303);
    assert.equal(await page.locator('#message').inputValue(), '');
    await page.reload();
    assert.equal(await page.locator('#history article').filter({ hasText: 'Auch im Schlafzimmer ist es kalt.' }).count(), 1);
    assert.equal(posts, 2);
});

test('Core: Mitarbeiter findet Fall, schreibt intern und extern, schliesst und eröffnet erneut', { timeout: 30000 }, async t => {
    const { page, context, browser } = await fixture(t);
    const c = await createCase(page, context);
    await page.goto(base + '/mitarbeiter/faelle');
    await page.locator('#q').fill(c.id);
    await until(async () => await page.locator('#results tr[data-case]').count() === 1
        && (await page.locator('#load-state').textContent()).startsWith('Zuletzt aktualisiert'));
    const link = page.getByRole('link', { name: c.id, exact: true });
    await link.waitFor();
    await link.click();
    await page.locator('#message').fill('Vertrauliche interne Rücksprache');
    await page.locator('#internal').check();
    assert.equal(await submit(page, page.locator('#send')), 303);
    await page.locator('#message').fill('Die Verwaltung meldet sich morgen.');
    assert.equal(await submit(page, page.locator('#send')), 303);
    const guest = await browser.newContext();
    t.after(() => guest.close());
    const guestPage = await guest.newPage();
    await guestPage.goto(c.tenantUrl);
    assert.equal(await guestPage.getByText('Vertrauliche interne Rücksprache', { exact: true }).count(), 0);
    assert.equal(await guestPage.getByText('Die Verwaltung meldet sich morgen.', { exact: true }).count(), 1);
    for (const [summary, button, closed] of [
        ['Fall abschliessen', 'Abschliessen', true],
        ['Fall wiedereröffnen', 'Wiedereröffnen', false],
    ]) {
        await page.locator('summary').filter({ hasText: summary }).click();
        assert.equal(await submit(page, page.getByRole('button', { name: button, exact: true })), 303);
        await guestPage.reload();
        assert.equal(await guestPage.locator('#case-head').getAttribute('data-closed'), String(closed));
        assert.equal(await guestPage.locator('#message').isDisabled(), closed);
    }
    await page.getByRole('link', { name: 'Zur Fallübersicht' }).click();
    assert.equal(await page.locator('#q').inputValue(), c.id);
});

test('Core: KI-Vorschlag wird erst übernommen und danach explizit gespeichert', { timeout: 30000 }, async t => {
    const { page, context } = await fixture(t);
    await createCase(page, context);
    await page.locator('#message').fill('Termin für die Heizung ankündigen');
    await page.locator('#generate').click();
    await until(async () => await page.locator('#ai-status').getAttribute('data-state') === 'completed');
    const proposal = await page.locator('#ai-output').textContent();
    assert.equal(await page.locator('#history article').count(), 1);
    await page.locator('#adopt').click();
    assert.equal(await page.locator('#message').inputValue(), proposal);
    assert.equal(await page.locator('#history article').count(), 1);
    assert.equal(await submit(page, page.locator('#send')), 303);
    await page.reload();
    assert.equal(await page.locator('#history article').filter({ hasText: proposal }).count(), 1);
    assert.equal(await page.locator('#history article').count(), 2);
});
