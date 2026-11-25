import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

const script = await readFile('src/main/resources/static/js/staff-answer-requirements.js', 'utf8');
const busyScript = await readFile('src/main/resources/static/js/staff-busy.js', 'utf8');
const origin = 'http://127.0.0.1:39997';
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t) {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const state = { actor: 'staff-one', status: 200, posts: [] };
    await page.route(`${origin}/**`, route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (request.method() === 'POST') {
            state.posts.push(Object.fromEntries(new URLSearchParams(request.postData())));
            return route.fulfill({ status: state.status, json: { state: 'APPLIED' } });
        }
        if (path === '/available') return route.fulfill({ json: { available: true, actor: state.actor, csrf: 'csrf-test' } });
        return route.fulfill({ contentType: 'text/html', body: `<meta charset="utf-8"><div id="case-head" data-version="7"></div>
            <details><summary>Antwortbedarf aufheben</summary><form data-clear-answer action="/clear"
                data-actor="staff-one" data-availability-url="/available" data-result-url="/results/">
                <input name="messageId" type="hidden" value="question-one"><input name="version" type="hidden" value="7">
                <button>Aufheben bestätigen</button></form></details><p id="answer-action-feedback"></p>
                <textarea aria-label="Entwurf">Nicht gesendeter Text</textarea>` });
    });
    await page.goto(origin);
    await page.addScriptTag({ content: busyScript });
    await page.addScriptTag({ content: script });
    return { page, state };
}

test('cancellation requires explicit confirmation and preserves the unsent draft', async t => {
    const { page, state } = await setup(t);
    await page.getByText('Antwortbedarf aufheben', { exact: true }).click();
    assert.equal(state.posts.length, 0);
    await page.getByRole('button', { name: 'Aufheben bestätigen' }).click();
    await page.waitForFunction(() => document.querySelector('#answer-action-feedback').textContent.includes('Antwortbedarf aufgehoben'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].messageId, 'question-one');
    assert.equal(state.posts[0].version, '7');
    assert.equal(state.posts[0].csrf, 'csrf-test');
    assert.equal(await page.getByLabel('Entwurf').inputValue(), 'Nicht gesendeter Text');
});

test('unknown cancellation response never repeats the mutation', async t => {
    const { page, state } = await setup(t);
    state.status = 503;
    await page.getByText('Antwortbedarf aufheben', { exact: true }).click();
    await page.getByRole('button', { name: 'Aufheben bestätigen' }).click();
    await page.waitForFunction(() => document.querySelector('#answer-action-feedback').textContent.includes('unklar'));
    assert.equal(state.posts.length, 1);
    assert.equal(await page.getByRole('button', { name: 'Aufheben bestätigen' }).isDisabled(), true);
});

test('changed account or displayed version prevents cancellation', async t => {
    const { page, state } = await setup(t);
    state.actor = 'staff-two';
    await page.getByText('Antwortbedarf aufheben', { exact: true }).click();
    await page.getByRole('button', { name: 'Aufheben bestätigen' }).click();
    await page.waitForFunction(() => document.querySelector('#answer-action-feedback').textContent.includes('Berechtigung'));
    assert.equal(state.posts.length, 0);
    state.actor = 'staff-one';
    await page.locator('#case-head').evaluate(node => { node.dataset.version = '8'; });
    await page.getByRole('button', { name: 'Aufheben bestätigen' }).click();
    assert.equal(state.posts.length, 0);
});
