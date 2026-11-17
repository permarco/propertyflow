import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { before, after, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

const script = await readFile('src/main/resources/static/js/staff-case-read.js', 'utf8');
const origin = 'http://127.0.0.1:39994';
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t) {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    const state = { reads: [], status: 200, held: null, hold: false, text: 'Alt', page: 1, row: true };
    state.heldPromise = new Promise(resolve => { state.holdReady = resolve; });
    const html = () => `<!doctype html><html lang="de"><body data-login-url="/anmelden">
        <form id="filters" action="${origin}/liste"><label>Suche<input name="q" id="q"></label><button type="submit">Suchen</button></form>
        <p id="load-state" role="status"></p><section id="results" tabindex="-1" data-page="${state.page}">
        <div class="table-card" style="height:50px;overflow:auto">${state.row ? `<a href="#" id="row">${state.text}</a>` : state.text}<div style="height:500px"></div></div></section></body></html>`;
    await page.route(origin + '/**', async route => {
        if (route.request().headers()['x-propertyflow-request'] === 'partial') {
            state.reads.push(route.request().url());
            if (state.hold) { state.held = route; state.holdReady(); return; }
        }
        await route.fulfill({ status: state.status, contentType: 'text/html', body: html() });
    });
    await page.goto(origin + '/liste');
    await page.clock.install();
    await page.addScriptTag({ content: script });
    return { page, state };
}

test('Suche entprellt Eingaben und verwirft verspätete Antworten', async t => {
    const { page, state } = await setup(t);
    state.hold = true;
    await page.locator('#q').fill('alt');
    await page.clock.runFor(1001);
    await state.heldPromise;
    await page.locator('#q').fill('neu');
    await page.clock.runFor(900);
    assert.equal(state.reads.length, 1);
    state.hold = false;
    state.text = 'Neu';
    await page.clock.runFor(101);
    await page.waitForFunction(() => document.querySelector('#row').textContent === 'Neu');
    await state.held.fulfill({ contentType: 'text/html', body: '<section id="results" data-page="9">Veraltet</section>' }).catch(() => {});
    assert.equal(await page.locator('#row').innerText(), 'Neu');
    assert.equal(new URL(page.url()).searchParams.get('q'), 'neu');
});

test('Lesefehler behalten Ergebnisse; Polling erhält Scrollposition und setzt Fokus nach Ersatz', async t => {
    const { page, state } = await setup(t);
    await page.locator('#row').focus();
    await page.locator('.table-card').evaluate(node => { node.scrollTop = 30; });
    state.status = 429;
    await page.clock.runFor(20001);
    await page.waitForFunction(() => document.querySelector('#load-state').textContent.includes('nicht aktualisiert'));
    assert.equal(await page.locator('#row').innerText(), 'Alt');
    state.status = 200;
    state.text = 'Aktuell';
    await page.clock.runFor(20001);
    await page.waitForFunction(() => document.querySelector('#row').textContent === 'Aktuell');
    assert.equal(await page.locator('.table-card').evaluate(node => node.scrollTop), 30);
    assert.equal(await page.locator('#row').evaluate(node => node === document.activeElement), true);
    state.row = false;
    state.text = 'Fall entfernt';
    await page.clock.runFor(20001);
    await page.waitForFunction(() => document.querySelector('#row') === null);
    assert.equal(await page.locator('#results').evaluate(node => node === document.activeElement), true);
});

test('Langsame Leseanfragen überlappen nicht mit dem Hintergrundintervall', async t => {
    const { page, state } = await setup(t);
    state.hold = true;
    const requested = page.waitForRequest(request => request.headers()['x-propertyflow-request'] === 'partial');
    await page.clock.runFor(60001);
    await requested;
    await state.heldPromise;
    assert.equal(state.reads.length, 1);
    await state.held.abort();
});

test('Versteckte Liste pausiert; Rückkehr aktualisiert sofort ohne zweite parallele Anfrage', async t => {
    const { page, state } = await setup(t);
    await page.evaluate(() => {
        Object.defineProperty(document, 'hidden', { configurable: true, get: () => true });
        document.dispatchEvent(new Event('visibilitychange'));
    });
    await page.clock.runFor(40001);
    assert.equal(state.reads.length, 0);
    state.hold = true;
    await page.evaluate(() => {
        Object.defineProperty(document, 'hidden', { configurable: true, get: () => false });
        document.dispatchEvent(new Event('visibilitychange'));
    });
    await state.heldPromise;
    await page.clock.runFor(20001);
    assert.equal(state.reads.length, 1);
    await state.held.abort();
});
