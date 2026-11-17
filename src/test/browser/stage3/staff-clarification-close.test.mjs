import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

const script = await readFile('src/main/resources/static/js/staff-clarification-close.js', 'utf8');
const origin = 'http://127.0.0.1:39998';
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t, panel = 'staff-clarification-close', submit = '/takeover') {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const state = { actor: 'staff-one', available: true, version: 7, reviewToken: 'review-v1', status: 200, result: 'APPLIED', posts: [], paths: [], reads: 0 };
    await page.route(`${origin}/**`, route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (request.method() === 'POST') {
            state.paths.push(path);
            state.posts.push(Object.fromEntries(new URLSearchParams(request.postData())));
            return route.fulfill({ status: state.status, json: { state: state.result } });
        }
        if (path === '/available') return route.fulfill({ json: { approval: { available: state.available, version: state.version, reviewToken: state.reviewToken }, actor: state.actor, csrf: 'csrf-test' } });
        if (path.startsWith('/results/')) {
            state.reads++;
            return route.fulfill({ json: { state: state.result } });
        }
        return route.fulfill({ contentType: 'text/html; charset=utf-8', body: `<meta charset="utf-8"><div id="case-head" data-version="7"></div>
            <section id="${panel}" data-direct-close hidden data-actor="staff-one" data-availability-url="/available"
                data-submit-url="${submit}" data-result-url="/results/">
                <button data-direct-close-open disabled>Fall abschliessen</button>
                <dialog data-direct-close-confirmation><button data-direct-close-confirm>Abschliessen</button>
                    <button data-direct-close-cancel>Abbrechen</button></dialog><p role="status"></p>
            </section><textarea aria-label="Entwurf">Ungesendeter Text</textarea>` });
    });
    await page.goto(origin);
    await page.clock.install();
    await page.addScriptTag({ content: script });
    return { page, state };
}

test('Abschluss verlangt Bestätigung und erhält den ungesendeten Entwurf', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('button', { name: 'Fall abschliessen' }).click();
    assert.equal(state.posts.length, 0);
    await page.getByRole('button', { name: 'Abschliessen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Abschluss bestätigt'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].version, '7');
    assert.equal(state.posts[0].csrf, 'csrf-test');
    assert.equal(state.posts[0].reviewToken, 'review-v1');
    assert.equal(await page.getByLabel('Entwurf').inputValue(), 'Ungesendeter Text');
    assert.equal(await page.locator('#case-head').getAttribute('data-version'), '7');
});

test('Unklare Antwort wird ausschliesslich lesend abgeglichen', async t => {
    const { page, state } = await setup(t);
    state.status = 503;
    state.result = 'PENDING';
    await page.getByRole('button', { name: 'Fall abschliessen' }).click();
    await page.getByRole('button', { name: 'Abschliessen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('unklar'));
    assert.equal(await page.locator('[data-direct-close-confirm]').isDisabled(), true);
    assert.equal(await page.locator('[data-direct-close-confirmation]').isHidden(), true);
    state.result = 'APPLIED';
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Abschluss bestätigt'));
    assert.equal(state.posts.length, 1);
    assert.ok(state.reads > 0);
});

test('Geänderter Fallstand oder Konto verhindert die bestätigte Abschluss', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('button', { name: 'Fall abschliessen' }).click();
    await page.locator('#case-head').evaluate(element => { element.dataset.version = '8'; });
    await page.getByRole('button', { name: 'Abschliessen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Fall hat sich geändert'));
    assert.equal(state.posts.length, 0);
    await page.getByRole('button', { name: 'Fall abschliessen' }).click();
    state.actor = 'staff-two';
    await page.getByRole('button', { name: 'Abschliessen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Konto hat gewechselt'));
    assert.equal(state.posts.length, 0);
});

test('Abbrechen erzeugt keinen Auftrag', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('button', { name: 'Fall abschliessen' }).click();
    await page.getByRole('button', { name: 'Abbrechen' }).click();
    assert.equal(await page.locator('[data-direct-close-confirmation]').isHidden(), true);
    assert.equal(state.posts.length, 0);
});


test('Neue Prüfgründe verlangen eine erneute bewusste Bestätigung', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('button', { name: 'Fall abschliessen' }).click();
    state.reviewToken = 'review-v2';
    await page.getByRole('button', { name: 'Abschliessen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Fall hat sich geändert'));
    assert.equal(state.posts.length, 0);
});


test('Derselbe Abschlussdialog bindet den Mieterwarteauftrag an dessen eigenen Eingang', async t => {
    const { page, state } = await setup(t, 'staff-reply-close', '/mieterwarten-abschliessen');
    await page.getByRole('button', { name: 'Fall abschliessen' }).click();
    await page.getByRole('button', { name: 'Abschliessen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Abschluss bestätigt'));
    assert.deepEqual(state.paths, ['/mieterwarten-abschliessen']);
    assert.equal(state.posts[0].reviewToken, 'review-v1');
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
