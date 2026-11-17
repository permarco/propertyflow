// Browser contract tests for the real memo script with controlled HTTP failure windows.
// PostgreSQL/worker/HTTP integration is covered separately by the Java integration tests.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

const script = await readFile('src/main/resources/static/js/staff-case-data.js', 'utf8');
const readScript = await readFile('src/main/resources/static/js/staff-case-read.js', 'utf8');
const origin = 'http://127.0.0.1:39999';
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t, kind = 'memo') {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const path = '/mitarbeiter/faelle/CASE-TEST/' + { memo: 'internes-memo-speichern', assignment: 'objekt-wohnung-zuordnen', urgency: 'dringlichkeit-aendern' }[kind];
    const state = { actor: 'staff-one', available: true, status: 200, result: 'PENDING', posts: [], abortNext: false,
        postStatus: kind === 'memo' ? 500 : 200, version: 1, readStatus: 200 };
    await page.route(`${origin}/**`, async route => {
        const request = route.request();
        const url = new URL(request.url());
        if (url.pathname.endsWith('/verfuegbar')) {
            return route.fulfill({ status: state.status, json: { actor: state.actor, available: state.available, csrf: 'csrf-test' } });
        }
        if (url.pathname.includes('/aktionen/')) {
            return route.fulfill({ json: { state: state.result } });
        }
        if (url.pathname.endsWith('/vorschlaege')) return route.fulfill({ json: ['Lindenhof 3'] });
        if (request.method() === 'POST') {
            state.posts.push(Object.fromEntries(new URLSearchParams(request.postData())));
            if (state.abortNext) {
                state.abortNext = false;
                return route.abort();
            }
            return route.fulfill({ status: state.postStatus, body: state.postStatus === 200 ? '' : 'Nicht bestätigt' });
        }
        const fields = kind === 'assignment' ? '<input id="memo" name="assignment" list="suggestions" required><datalist id="suggestions"></datalist>'
            : kind === 'memo' ? '<textarea id="memo" name="text" required></textarea>'
            : '<label><input type="radio" name="urgency" value="HIGH" required>Hoch</label><label><input type="radio" name="urgency" value="LOW" required>Niedrig</label>'
                + '<textarea id="memo" name="reason"></textarea>';
        return route.fulfill({ status: state.readStatus, contentType: 'text/html; charset=utf-8', body: `<!doctype html><html lang="de"><head><meta charset="utf-8"></head>
            <body data-login-url="/anmelden"><main id="case-page"><p id="save-state"></p>
            <section id="case-head" data-version="${state.version}"></section>
            <dl id="case-data" data-original-unit="Ursprüngliche Wohnung"></dl>
            <div id="case-review"></div><div id="case-recommendation"></div><section id="history">Verlauf ${state.version}</section>
            ${kind === 'assignment' ? '<details><summary>Zuordnung ändern</summary>' : ''}
            <form id="memo-form" action="${path}" data-actor="staff-one" data-case-action="${kind}"
                  data-command-base="/mitarbeiter/faelle/CASE-TEST/aktionen"><label for="memo">Internes Memo</label>
            ${fields}<button disabled>Speichern</button>${kind === 'assignment' ? '<button type="button" data-action-cancel>Abbrechen</button>' : ''}
            <p id="memo-feedback" data-action-feedback role="status"></p>
            <a id="memo-login" data-action-login href="/anmelden" hidden>Anmelden</a></form>${kind === 'assignment' ? '</details>' : ''}</main></body></html>` });
    });
    await page.goto(origin);
    await page.clock.install();
    await page.addScriptTag({ content: script });
    await page.waitForFunction(() => !document.querySelector('button').disabled);
    return { page, state };
}

test('unknown save outcome preserves input and reuses the identical operation', async t => {
    const { page, state } = await setup(t);
    state.abortNext = true;
    await page.locator('textarea').fill('Interne Abklärung');
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('#memo-feedback').textContent.includes('Noch keine Speicherbestätigung'));
    assert.equal(await page.locator('textarea').inputValue(), 'Interne Abklärung');
    await page.getByRole('button').click();
    await page.waitForFunction(() => !document.querySelector('button').disabled);
    assert.equal(state.posts.length, 2);
    assert.deepEqual(state.posts[0], state.posts[1]);
    state.result = 'APPLIED';
    await page.waitForFunction(() => document.querySelector('textarea').value === '', null, { timeout: 8000 });
    assert.equal(await page.locator('textarea').getAttribute('readonly'), null);
});

test('assignment is only persisted after confirmation and cancellation keeps the saved origin', async t => {
    const { page, state } = await setup(t, 'assignment');
    assert.equal(await page.locator('details').getAttribute('open'), null);
    await page.getByText('Zuordnung ändern', { exact: true }).click();
    await page.waitForFunction(() => document.querySelector('input').value === 'Ursprüngliche Wohnung');
    await page.locator('input').fill('Entwurf');
    await page.getByRole('button', { name: 'Abbrechen' }).click();
    assert.equal(state.posts.length, 0);
    await page.getByText('Zuordnung ändern', { exact: true }).click();
    await page.waitForFunction(() => document.querySelector('input').value === 'Ursprüngliche Wohnung');
    await page.locator('input').fill('Lindenhof 3');
    await page.waitForFunction(() => document.querySelectorAll('datalist option').length === 1);
    assert.equal(state.posts.length, 0);
    state.result = 'APPLIED';
    await page.getByRole('button', { name: 'Speichern' }).click();
    await page.waitForFunction(() => !document.querySelector('details').open, null, { timeout: 8000 });
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].assignment, 'Lindenhof 3');
});

test('a case update requires renewed confirmation before any memo request', async t => {
    const { page, state } = await setup(t);
    await page.locator('textarea').fill('Nachweis zur Prüfung');
    await page.evaluate(() => { document.querySelector('#case-head').dataset.version = '2'; });
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('#memo-feedback').textContent.includes('Der Fall hat sich geändert'));
    assert.equal(state.posts.length, 0);
    await page.getByRole('button').click();
    await page.waitForFunction(() => !document.querySelector('button').disabled);
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].version, undefined);
    assert.ok(state.posts[0].operation);
});

test('background refresh and session expiry preserve an open assignment draft', async t => {
    const { page, state } = await setup(t, 'assignment');
    await page.addScriptTag({ content: readScript });
    await page.getByText('Zuordnung ändern', { exact: true }).click();
    await page.waitForFunction(() => document.querySelector('input').value === 'Ursprüngliche Wohnung');
    await page.locator('input').fill('Ungespeicherte Zuordnung');
    state.version = 2;
    await page.evaluate(() => document.dispatchEvent(new Event('propertyflow-case-changed')));
    await page.waitForFunction(() => document.querySelector('#case-head').dataset.version === '2');
    assert.equal(await page.locator('input').inputValue(), 'Ungespeicherte Zuordnung');
    assert.equal(await page.locator('details').getAttribute('open'), '');
    state.readStatus = 401;
    state.status = 401;
    await page.evaluate(() => document.dispatchEvent(new Event('propertyflow-case-changed')));
    await page.waitForFunction(() => document.querySelector('#save-state').textContent.includes('Sitzung ist abgelaufen'));
    assert.equal(await page.locator('input').inputValue(), 'Ungespeicherte Zuordnung');
    assert.equal(state.posts.length, 0);
    assert.equal(new URL(page.url()).pathname, '/');
});

test('session loss and account switching retain the memo without submitting it', async t => {
    const { page, state } = await setup(t);
    await page.locator('textarea').fill('Vertrauliche Notiz');
    state.status = 401;
    await page.getByRole('button').click();
    await page.waitForFunction(() => !document.querySelector('#memo-login').hidden);
    assert.equal(await page.locator('textarea').inputValue(), 'Vertrauliche Notiz');
    assert.equal(state.posts.length, 0);
    state.status = 200;
    state.actor = 'different-staff';
    await page.evaluate(() => document.dispatchEvent(new Event('propertyflow-access-lost')));
    await page.waitForFunction(() => document.querySelector('#memo-feedback').textContent.includes('Konto hat gewechselt'));
    assert.equal(state.posts.length, 0);
    assert.equal(await page.getByRole('button').isDisabled(), true);
    assert.equal(await page.locator('textarea').inputValue(), 'Vertrauliche Notiz');
});

test('urgency selection is not submitted until confirmation and conflict keeps the form usable', async t => {
    const { page, state } = await setup(t, 'urgency');
    await page.getByRole('radio', { name: 'Niedrig' }).check();
    await page.locator('textarea').fill('Nur interne Begründung');
    assert.equal(state.posts.length, 0);
    state.postStatus = 409;
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('#memo-feedback').textContent.includes('Konnte nicht erfolgreich gespeichert werden'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].urgency, 'Niedrig');
    assert.equal(state.posts[0].reason, 'Nur interne Begründung');
    assert.equal(await page.getByRole('radio', { name: 'Niedrig' }).isDisabled(), false);
    assert.equal(await page.locator('textarea').inputValue(), 'Nur interne Begründung');
});

test('Freigaben werden ohne Auftrag erst nach 20 Sekunden erneut gelesen', async t => {
    const { page } = await setup(t);
    const reads = [];
    page.on('request', request => { if (request.url().endsWith('/verfuegbar')) reads.push(request.url()); });
    await page.clock.runFor(19000);
    assert.equal(reads.length, 0);
    const refresh = page.waitForResponse(response => response.url().endsWith('/verfuegbar'));
    await page.clock.runFor(1100);
    await refresh;
    assert.equal(reads.length, 1);
});
