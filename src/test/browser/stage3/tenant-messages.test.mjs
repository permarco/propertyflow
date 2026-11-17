import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { after, before, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

const script = await readFile('src/main/resources/static/js/tenant-messages.js', 'utf8');
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t) {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const state = { posts: [], status: 200, readStatus: 200, closed: false };
    const server = createServer(async (request, response) => {
        if (request.method === 'POST') {
            let body = '';
            for await (const chunk of request) body += chunk;
            state.posts.push(Object.fromEntries(new URLSearchParams(body)));
            if (state.status === 200) {
                response.writeHead(303, { location: '/' });
                response.end();
                return;
            }
            response.writeHead(state.status);
            response.end('Übergabe nicht bestätigt');
            return;
        }
        response.writeHead(state.readStatus, { 'content-type': 'text/html; charset=utf-8' });
        response.end(`<!doctype html>
            <html><head><meta charset="utf-8"></head><body>
            <section id="case-head" data-closed="${state.closed}">Fall</section><section id="history">Gespeicherter Verlauf</section>
            <section id="tenant-message-panel"><form id="tenant-message-form" action="/nachrichten" data-csrf="csrf-test">
                <textarea name="text" aria-label="Nachricht"></textarea><button type="submit">Nachricht senden</button><p role="status"></p>
            </form></section></body></html>`);
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    t.after(() => { server.closeAllConnections(); server.close(); });
    await page.goto(`http://127.0.0.1:${server.address().port}`);
    await page.addScriptTag({ content: script });
    return { page, state };
}

test('unknown correlation keeps the complete text and operation until explicit retry succeeds', async t => {
    const { page, state } = await setup(t);
    state.status = 500;
    await page.getByRole('textbox').fill('Meine Ergänzung');
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('nicht bestätigt'));
    assert.equal(await page.getByRole('textbox').inputValue(), 'Meine Ergänzung');
    assert.equal(state.posts.length, 1);
    assert.equal(await page.getByRole('textbox').getAttribute('readonly'), '');
    state.status = 200;
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('textarea').value === '');
    assert.equal(state.posts.length, 2);
    assert.ok(state.posts[0].operation);
    assert.equal(state.posts[0].operation, state.posts[1].operation);
    assert.equal(state.posts[0].text, state.posts[1].text);
});

for (const status of [401, 403, 404]) {
    test(`Zugriffsverlust ${status} erhält den Entwurf und sperrt das alte Fenster dauerhaft`, async t => {
        const { page, state } = await setup(t);
        await page.clock.install();
        state.status = status;
        await page.getByRole('textbox').fill('Nicht automatisch übertragen');
        await page.getByRole('button').click();
        await page.waitForFunction(() => document.querySelector('[role=status]').textContent.includes('nicht mehr gültig'));
        assert.equal(await page.getByRole('textbox').inputValue(), 'Nicht automatisch übertragen');
        assert.equal(await page.getByRole('button').isDisabled(), true);
        assert.equal(await page.locator('#history').innerText(), '');
        state.status = 200;
        await page.clock.runFor(40000);
        assert.equal(await page.getByRole('button').isDisabled(), true);
        assert.equal(state.posts.length, 1);
    });
}

test('oversized text remains editable and emoji count as two UTF-16 units', async t => {
    const { page, state } = await setup(t);
    const oversized = 'a'.repeat(1999) + '😀';
    await page.getByRole('textbox').fill(oversized);
    await page.getByRole('button').click();
    assert.equal(state.posts.length, 0);
    assert.equal(await page.getByRole('textbox').inputValue(), oversized);
    assert.equal(await page.getByRole('textbox').getAttribute('readonly'), null);
    await page.getByRole('textbox').fill('a'.repeat(1998) + '😀');
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('textarea').value === '');
    assert.equal(state.posts.length, 1);
});

test('read failures preserve drafts and only confirmed closure clears and hides the input', async t => {
    const { page, state } = await setup(t);
    state.status = 500;
    state.readStatus = 500;
    await page.getByRole('textbox').fill('Noch unbestätigt');
    await page.getByRole('button').click();
    await page.waitForFunction(() => !document.querySelector('button').disabled);
    assert.equal(await page.getByRole('textbox').inputValue(), 'Noch unbestätigt');
    assert.equal(await page.locator('#tenant-message-panel').isVisible(), true);
    state.readStatus = 200;
    state.closed = true;
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('#tenant-message-panel').hidden);
    assert.equal(await page.locator('textarea').inputValue(), '');
});
