import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

const script = await readFile('src/main/resources/static/js/staff-close.js', 'utf8');
const origin = 'http://127.0.0.1:39997';
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t) {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const state = { status: 200, posts: [] };
    await page.route(`${origin}/**`, route => {
        const request = route.request();
        if (request.method() === 'POST') {
            state.posts.push(Object.fromEntries(new URLSearchParams(request.postData())));
            return route.fulfill({ status: state.status, json: { state: 'CONFIRMED' } });
        }
        if (new URL(request.url()).pathname.endsWith('/pruefung')) {
            return route.fulfill({ json: { actor: 'staff-one', csrf: 'csrf-test', task: {
                available: true, canClose: true, taskKey: '700', version: 2, reviewToken: 'reasons-one',
            } } });
        }
        return route.fulfill({ contentType: 'text/html; charset=utf-8', body: `<!doctype html><html lang="de">
            <head><meta charset="utf-8"></head><body><div id="case-head" data-version="2"></div>
            <span id="case-status">Mitarbeiterprüfung erforderlich</span>
            <section id="staff-close-panel" data-task-url="/pruefung" data-actor="staff-one" hidden>
                <button data-close-open disabled>Fall abschliessen</button><button data-close-reload>Neu laden</button>
                <p role="status"></p><dialog aria-labelledby="question"><h2 id="question">Fall wirklich abschliessen?</h2>
                    <form action="/abschliessen"><button type="button" data-close-cancel>Abbrechen</button>
                        <button type="submit">Abschliessen</button></form>
                </dialog>
            </section></body></html>` });
    });
    await page.goto(origin);
    await page.addScriptTag({ content: script });
    await page.waitForFunction(() => !document.querySelector('[data-close-open]').disabled);
    return { page, state };
}

test('closure requires the explicit dialog confirmation and does not invent the stored status', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('button', { name: 'Fall abschliessen', exact: true }).click();
    assert.equal(await page.getByRole('dialog').isVisible(), true);
    assert.equal(state.posts.length, 0);
    await page.getByRole('button', { name: 'Abbrechen', exact: true }).click();
    assert.equal(state.posts.length, 0);
    assert.equal(await page.getByRole('dialog').isVisible(), false);
    await page.getByRole('button', { name: 'Fall abschliessen', exact: true }).click();
    await page.getByRole('button', { name: 'Abschliessen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Abschlussentscheidung bestätigt'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].userTaskKey, '700');
    assert.equal('text' in state.posts[0], false);
    assert.equal(await page.locator('#case-status').textContent(), 'Mitarbeiterprüfung erforderlich');
    await page.evaluate(() => {
        document.querySelector('#case-status').textContent = 'Abgeschlossen';
        document.dispatchEvent(new Event('propertyflow-case-refreshed'));
    });
    assert.equal(await page.locator('#staff-close-panel').isHidden(), true);
});

test('a changed case cancels the stale confirmation without a request', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('button', { name: 'Fall abschliessen', exact: true }).click();
    await page.evaluate(() => {
        document.querySelector('#case-head').dataset.version = '3';
        document.dispatchEvent(new Event('propertyflow-case-refreshed'));
    });
    assert.equal(await page.getByRole('dialog').isVisible(), false);
    assert.equal(await page.locator('[data-close-open]').isDisabled(), true);
    assert.equal(state.posts.length, 0);
});

test('an unknown closure outcome cannot trigger an automatic second completion', async t => {
    const { page, state } = await setup(t);
    state.status = 500;
    await page.getByRole('button', { name: 'Fall abschliessen', exact: true }).click();
    await page.getByRole('button', { name: 'Abschliessen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('nicht automatisch wiederholt'));
    await page.evaluate(() => document.dispatchEvent(new Event('propertyflow-case-refreshed')));
    assert.equal(state.posts.length, 1);
    assert.equal(await page.locator('[data-close-open]').isDisabled(), true);
    assert.equal(await page.getByRole('dialog').isVisible(), false);
});
