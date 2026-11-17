import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

const script = await readFile('src/main/resources/static/js/staff-takeover.js', 'utf8');
const origin = 'http://127.0.0.1:39998';
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t) {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const state = { actor: 'staff-one', available: true, status: 200, result: 'APPLIED', posts: [], reads: 0 };
    await page.route(`${origin}/**`, route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (request.method() === 'POST') {
            state.posts.push(Object.fromEntries(new URLSearchParams(request.postData())));
            return route.fulfill({ status: state.status, json: { state: state.result } });
        }
        if (path === '/available') return route.fulfill({ json: { available: state.available, actor: state.actor, csrf: 'csrf-test' } });
        if (path.startsWith('/results/')) {
            state.reads++;
            return route.fulfill({ json: { state: state.result } });
        }
        return route.fulfill({ contentType: 'text/html; charset=utf-8', body: `<meta charset="utf-8"><div id="case-head" data-version="7"></div>
            <section id="staff-takeover" hidden data-actor="staff-one" data-availability-url="/available"
                data-submit-url="/takeover" data-result-url="/results/">
                <button data-takeover-open disabled>Bearbeitung übernehmen</button>
                <div data-takeover-confirmation hidden><button data-takeover-confirm>Übernahme bestätigen</button>
                    <button data-takeover-cancel>Abbrechen</button></div><p role="status"></p>
            </section><textarea aria-label="Entwurf">Ungesendeter Text</textarea>` });
    });
    await page.goto(origin);
    await page.clock.install();
    await page.addScriptTag({ content: script });
    return { page, state };
}

test('Übernahme verlangt Bestätigung und erhält den ungesendeten Entwurf', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('button', { name: 'Bearbeitung übernehmen' }).click();
    assert.equal(state.posts.length, 0);
    await page.getByRole('button', { name: 'Übernahme bestätigen' }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Übernahme bestätigt'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].version, '7');
    assert.equal(state.posts[0].csrf, 'csrf-test');
    assert.equal(await page.getByLabel('Entwurf').inputValue(), 'Ungesendeter Text');
    assert.equal(await page.locator('#case-head').getAttribute('data-version'), '7');
});

test('Unklare Antwort wird ausschliesslich lesend abgeglichen', async t => {
    const { page, state } = await setup(t);
    state.status = 503;
    state.result = 'PENDING';
    await page.getByRole('button', { name: 'Bearbeitung übernehmen' }).click();
    await page.getByRole('button', { name: 'Übernahme bestätigen' }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('unklar'));
    assert.equal(await page.getByRole('button', { name: 'Übernahme bestätigen' }).isDisabled(), true);
    state.result = 'APPLIED';
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Übernahme bestätigt'));
    assert.equal(state.posts.length, 1);
    assert.ok(state.reads > 0);
});

test('Geänderter Fallstand oder Konto verhindert die bestätigte Übernahme', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('button', { name: 'Bearbeitung übernehmen' }).click();
    await page.locator('#case-head').evaluate(element => { element.dataset.version = '8'; });
    await page.getByRole('button', { name: 'Übernahme bestätigen' }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Fall hat sich geändert'));
    assert.equal(state.posts.length, 0);
    await page.getByRole('button', { name: 'Bearbeitung übernehmen' }).click();
    state.actor = 'staff-two';
    await page.getByRole('button', { name: 'Übernahme bestätigen' }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Konto hat gewechselt'));
    assert.equal(state.posts.length, 0);
});

test('Abbrechen erzeugt keinen Auftrag', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('button', { name: 'Bearbeitung übernehmen' }).click();
    await page.getByRole('button', { name: 'Abbrechen' }).click();
    assert.equal(await page.locator('[data-takeover-confirmation]').isHidden(), true);
    assert.equal(state.posts.length, 0);
});

test('Freigaben werden ohne Auftrag erst nach 20 Sekunden erneut gelesen', async t => {
    const { page } = await setup(t);
    await page.waitForFunction(() => !document.querySelector('section button').disabled);
    const reads = [];
    page.on('request', request => { if (new URL(request.url()).pathname === '/available') reads.push(request.url()); });
    await page.clock.runFor(19000);
    assert.equal(reads.length, 0);
    const refresh = page.waitForResponse(response => new URL(response.url()).pathname === '/available');
    await page.clock.runFor(1100);
    await refresh;
    assert.equal(reads.length, 1);
});
