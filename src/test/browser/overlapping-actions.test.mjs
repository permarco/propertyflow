import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { fixture, base, createCase, editor, freeze, submit, until, staffStorageState } from './ui-test-helpers.mjs';

function hold(t) {
    let release;
    const waiting = new Promise(resolve => { release = resolve; });
    t.after(() => release());
    return { waiting, release };
}

test('UI1: Doppelklick und Enter während der Fallanlage senden und speichern genau einmal', { timeout: 30000 }, async t => {
    const { page, browser } = await fixture(t, { staff: false });
    await page.goto(base + '/mieter/fall');
    const title = `Doppelte Bedienung ${randomUUID()}`;
    for (const [name, value] of Object.entries({ title, unit: 'Wohnung 1', email: 'double@example.test', description: 'Heizung kalt' })) {
        await page.locator('#' + name).fill(value);
    }
    const gate = hold(t);
    let posts = 0;
    await page.route('**/mieter/fall', async route => {
        if (route.request().method() !== 'POST') return route.continue();
        posts++;
        await gate.waiting;
        await route.continue();
    });
    const button = page.getByRole('button', { name: /Anliegen absenden/ });
    await button.dblclick();
    await until(() => posts === 1);
    assert.equal(await page.locator('#intake-form button').isDisabled(), true);
    await page.locator('#title').press('Enter');
    gate.release();
    await page.waitForURL(/\/mieter\/fall\/zugang\//);
    assert.equal(posts, 1);
    assert.equal(await page.locator('#history article').count(), 1);
    const observer = await browser.newContext({ storageState: await staffStorageState(browser) });
    const list = await observer.newPage();
    await list.goto(base + '/mitarbeiter/faelle?q=' + encodeURIComponent(title));
    assert.equal(await list.locator('#results tr[data-case]').count(), 1);
    await observer.close();
});

for (const staff of [false, true]) {
    test(`${staff ? 'UI3' : 'UI1'}: Speichern bricht einen alten Abruf ab; dessen verspätete Antwort überschreibt keine Nachricht`,
        { timeout: 30000 }, async t => {
        const { page, context } = await fixture(t, { staff });
        await freeze(page);
        const c = await createCase(page, context, staff);
        const url = staff ? c.staffUrl : c.tenantUrl;
        const version = Number(await page.locator('#case-head').getAttribute('data-version'));
        const gate = hold(t);
        let captured = false;
        let delivered = false;
        let reads = 0;
        await page.route(url, async route => {
            if (route.request().redirectedFrom()) return route.continue();
            reads++;
            if (reads !== 1) return route.continue();
            const response = await route.fetch();
            assert.equal(response.status(), 200);
            const html = await response.text();
            assert.match(html, new RegExp(`data-version="${version}"`));
            captured = true;
            await gate.waiting;
            try {
                await route.fulfill({ response });
            } catch {
                // The browser's cancellation is asserted separately below.
            } finally {
                delivered = true;
            }
        });
        await page.clock.runFor(20000);
        await until(() => captured);
        const cancelled = page.waitForEvent('requestfailed', { predicate: request => request.url() === url });
        const text = `Gespeichert trotz altem Abruf ${randomUUID()}`;
        await page.locator('#message').fill(text);
        assert.equal(await submit(page, page.locator('#send')), 303);
        assert.match((await cancelled).failure().errorText, /ABORTED/);
        assert.equal(await page.locator('#history article').filter({ hasText: text }).count(), 1);
        assert.equal(Number(await page.locator('#case-head').getAttribute('data-version')), version + 1);
        gate.release();
        await until(() => delivered);
        await page.clock.runFor(1);
        assert.equal(await page.locator('#history article').filter({ hasText: text }).count(), 1);
        assert.equal(Number(await page.locator('#case-head').getAttribute('data-version')), version + 1);
        assert.equal(await page.locator('#message').inputValue(), '');
        await page.clock.runFor(20000);
        await until(() => reads === 2);
        assert.equal(await page.locator('#history article').count(), 2);
        assert.equal(await page.locator('#updates').isVisible(), false);
    });
}

test('UI3: zwei gleichzeitig gespeicherte Feldänderungen ergeben einen Erfolg und einen sichtbaren Konflikt',
    { timeout: 30000 }, async t => {
    const { page, context } = await fixture(t);
    await freeze(page);
    const c = await createCase(page, context);
    const other = await context.newPage();
    await freeze(other);
    await other.goto(c.staffUrl);
    const errors = [];
    other.on('pageerror', error => errors.push(error.message));
    t.after(() => assert.deepEqual(errors, []));
    const pages = [page, other];
    const values = ['Wohnung aus Tab A', 'Wohnung aus Tab B'];
    const gate = hold(t);
    const requests = [];
    for (const [index, tab] of pages.entries()) {
        await tab.locator('#message').fill(`Ungesendeter Entwurf ${index}`);
        await editor(tab, '#assigned-unit').locator('summary').click();
        await tab.locator('#assigned-unit').fill(values[index]);
        await tab.route('**/aktion', async route => {
            requests.push(new URLSearchParams(route.request().postData()));
            await gate.waiting;
            await route.continue();
        });
    }
    const saved = Promise.all(pages.map(tab => submit(tab, editor(tab, '#assigned-unit').getByRole('button', { name: 'Zuordnung bestätigen' }))));
    await until(() => requests.length === 2);
    assert.equal(requests[0].get('version'), requests[1].get('version'));
    assert.notEqual(requests[0].get('operation'), requests[1].get('operation'));
    gate.release();
    const statuses = await saved;
    assert.deepEqual([...statuses].sort(), [303, 409]);
    const winner = statuses.indexOf(303);
    const version = Number(requests[0].get('version')) + 1;
    for (const [index, tab] of pages.entries()) {
        assert.equal(Number(await tab.locator('#case-head').getAttribute('data-version')), version);
        assert.match(await editor(tab, '#assigned-unit').locator('summary').textContent(), new RegExp(values[winner]));
        assert.equal(await tab.locator('#history article').count(), 2);
        assert.equal(await tab.locator('#message').inputValue(), `Ungesendeter Entwurf ${index}`);
        if (index !== winner) assert.equal(await tab.locator('#save-state.error').isVisible(), true);
    }
    const observer = await context.newPage();
    await observer.goto(c.staffUrl);
    assert.equal(Number(await observer.locator('#case-head').getAttribute('data-version')), version);
    assert.equal(await observer.locator('#history article').count(), 2);
    assert.match(await editor(observer, '#assigned-unit').locator('summary').textContent(), new RegExp(values[winner]));
    assert.equal(await observer.locator('#history article').filter({ hasText: values[1 - winner] }).count(), 0);
});
