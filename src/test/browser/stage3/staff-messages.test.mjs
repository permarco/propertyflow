import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

const script = await readFile('src/main/resources/static/js/staff-messages.js', 'utf8');
const origin = 'http://127.0.0.1:39996';
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t) {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const state = { actor: 'staff-one', question: true, status: 200, postStatus: 500, result: 'PENDING', posts: [] };
    await page.route(`${origin}/**`, route => {
        const request = route.request();
        if (request.url().endsWith('/verfuegbar')) return route.fulfill({ status: state.status,
            json: { actor: state.actor, csrf: 'csrf-test', capability: { information: true, question: state.question } } });
        if (request.url().includes('/auftraege/')) return route.fulfill({ json: { state: state.result } });
        if (request.method() === 'POST') {
            state.posts.push(Object.fromEntries(new URLSearchParams(request.postData())));
            return route.fulfill({ status: state.postStatus, body: '' });
        }
        return route.fulfill({ contentType: 'text/html; charset=utf-8', body: `<!doctype html><html><head><meta charset="utf-8"></head><body>
            <section id="case-head" data-version="2"><span id="case-status">In Bearbeitung</span></section>
            <form id="staff-message-form" action="/externe-nachricht-veroeffentlichen" data-message-base="/nachrichten" data-actor="staff-one">
                <textarea name="text" aria-label="Nachricht"></textarea>
                <label><input name="answer" type="checkbox">Antwort erforderlich</label>
                <button type="submit" disabled>Nachricht senden</button><p role="status"></p>
                <a data-message-login href="/anmelden" hidden>Anmelden</a>
            </form></body></html>` });
    });
    await page.goto(origin);
    await page.clock.install();
    await page.addScriptTag({ content: script });
    await page.waitForFunction(() => !document.querySelector('button').disabled);
    return { page, state };
}

test('unknown publication preserves exact text answer and operation until explicit retry succeeds', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('textbox').fill('Termin am Montag? ');
    await page.getByRole('checkbox').check();
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('nicht bestätigt'));
    assert.equal(await page.getByRole('textbox').inputValue(), 'Termin am Montag? ');
    assert.equal(await page.getByRole('checkbox').isChecked(), true);
    assert.equal(await page.getByRole('checkbox').isDisabled(), true);
    assert.equal(state.posts.length, 1);
    state.postStatus = 200;
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('textarea').value === '');
    assert.deepEqual(state.posts[0], state.posts[1]);
    assert.equal(state.posts[0].answer, 'true');
    assert.equal(await page.getByRole('checkbox').isChecked(), false);
});

test('background reads can confirm publication without another send', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('textbox').fill('Zwischenstand');
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('nicht bestätigt'));
    state.result = 'PUBLISHED';
    await page.waitForFunction(() => document.querySelector('textarea').value === '', null, { timeout: 8000 });
    assert.equal(state.posts.length, 1);
});

async function changeStatus(page, status) {
    await page.evaluate(value => {
        document.querySelector('#case-status').textContent = value;
        document.dispatchEvent(new Event('propertyflow-case-refreshed'));
    }, status);
}

test('bereits erwartete Mieterantwort sperrt den Schalter, erlaubt Informationen und wird nach Statuswechsel freigegeben', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('textbox').fill('Zusätzliche Information');
    await page.getByRole('checkbox').check();
    await changeStatus(page, 'Wartet auf Mieterantwort');
    assert.equal(await page.getByRole('checkbox').isDisabled(), true);
    assert.equal(await page.getByRole('checkbox').isChecked(), false);
    state.postStatus = 200;
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('textarea').value === '');
    assert.equal(state.posts[0].answer, 'false');
    await changeStatus(page, 'In Bearbeitung');
    assert.equal(await page.getByRole('checkbox').isDisabled(), false);
});

test('Statuswechsel verändert keinen unbestätigten Rückfrageauftrag beim Wiederholen', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('textbox').fill('Bitte antworten');
    await page.getByRole('checkbox').check();
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('nicht bestätigt'));
    await changeStatus(page, 'Wartet auf Mieterantwort');
    assert.equal(await page.getByRole('checkbox').isChecked(), true);
    assert.equal(await page.getByRole('checkbox').isDisabled(), true);
    state.postStatus = 200;
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('textarea').value === '');
    assert.deepEqual(state.posts[0], state.posts[1]);
});

test('changed case and account require renewed confirmation and never transfer the draft', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('textbox').fill('Vertraulicher Entwurf');
    await page.evaluate(() => { document.querySelector('#case-head').dataset.version = '3'; });
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Der Fall hat sich geändert'));
    assert.equal(state.posts.length, 0);
    state.actor = 'staff-two';
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Konto hat gewechselt'));
    assert.equal(state.posts.length, 0);
    assert.equal(await page.getByRole('textbox').inputValue(), 'Vertraulicher Entwurf');
});

test('rejection retains text and answer choice and makes them editable', async t => {
    const { page, state } = await setup(t);
    state.postStatus = 409;
    await page.getByRole('textbox').fill('Rückfrage');
    await page.getByRole('checkbox').check();
    await page.getByRole('button').click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('nicht veröffentlicht'));
    assert.equal(await page.getByRole('textbox').inputValue(), 'Rückfrage');
    assert.equal(await page.getByRole('textbox').getAttribute('readonly'), null);
    assert.equal(await page.getByRole('checkbox').isChecked(), true);
    assert.equal(await page.getByRole('checkbox').isDisabled(), false);
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
