import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

const scripts = await Promise.all(['staff-messages', 'staff-case-data', 'staff-formulation']
    .map(name => readFile(`src/main/resources/static/js/${name}.js`, 'utf8')));
const origin = 'http://127.0.0.1:39995';
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t) {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const state = { actor: 'staff-one', posts: [], postStatus: 200 };
    await page.route(`${origin}/**`, route => {
        const request = route.request();
        if (request.url().endsWith('/verfuegbar')) return route.fulfill({ json: {
            actor: state.actor, csrf: 'csrf-test', available: true, capability: { information: true, question: true },
        } });
        if (request.method() === 'POST') {
            state.posts.push(Object.fromEntries(new URLSearchParams(request.postData())));
            return route.fulfill({ status: state.postStatus, body: '' });
        }
        return route.fulfill({ contentType: 'text/html; charset=utf-8', body: `<!doctype html><html><head><meta charset="utf-8"></head><body>
            <section id="case-head" data-version="2"><span id="case-status">In Bearbeitung</span></section>
            <form id="staff-message-form" action="/senden" data-message-base="/nachrichten" data-actor="staff-one">
                <textarea name="text" aria-label="Nachricht"></textarea>
                <section id="staff-formulation" data-url="/ki-formulierung" data-availability-url="/aktionen/verfuegbar">
                    <button type="button" data-formulate disabled>Mit KI formulieren</button>
                    <button type="button" data-formulation-abort hidden>Abbrechen</button>
                    <p data-formulation-status role="status"></p><p data-formulation-output></p>
                    <button type="button" data-formulation-adopt disabled>Vorschlag übernehmen</button>
                </section>
                <input type="checkbox" name="answer"><button type="submit" disabled>Nachricht senden</button>
                <p role="status"></p><a data-message-login hidden>Anmelden</a>
            </form>
            <form id="memo-form" data-case-action="memo" action="/memo" data-command-base="/aktionen" data-actor="staff-one">
                <textarea name="text" aria-label="Memo"></textarea><button type="submit" disabled>Memo speichern</button>
                <p data-action-feedback></p><a data-action-login hidden>Anmelden</a>
            </form></body></html>` });
    });
    await page.goto(origin);
    await page.evaluate(() => {
        const original = window.fetch;
        window.formulationTest = { calls: [], aborts: [], stream: undefined };
        window.fetch = async (url, options) => {
            if (String(url).includes('/ki-formulierung')) {
                const record = Object.fromEntries(options.body);
                if (String(url).endsWith('/abbrechen')) {
                    window.formulationTest.aborts.push(record);
                    return new Response(null, { status: 204 });
                }
                window.formulationTest.calls.push(record);
                return new Response(new ReadableStream({ start(controller) { window.formulationTest.stream = controller; } }), {
                    headers: { 'Content-Type': 'text/event-stream' },
                });
            }
            return original(url, options);
        };
        window.pushFormulation = text => window.formulationTest.stream.enqueue(new TextEncoder().encode(text));
    });
    for (const script of scripts) await page.addScriptTag({ content: script });
    await page.waitForFunction(() => !document.querySelector('#staff-message-form [type=submit]').disabled);
    return { page, state };
}

async function start(page) {
    await page.getByLabel('Nachricht', { exact: true }).fill('Techniker kommt Dienstag.');
    await page.getByRole('button', { name: 'Mit KI formulieren', exact: true }).click();
    await page.waitForFunction(() => window.formulationTest.stream);
}

async function push(page, event, data = {}) {
    await page.evaluate(({ event, data }) => window.pushFormulation(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`), { event, data });
}

test('Stream bleibt ungespeichert, Markup bleibt Text und nur bewusste Übernahme ersetzt die aktuelle Eingabe', async t => {
    const { page, state } = await setup(t);
    await start(page);
    await page.getByLabel('Nachricht', { exact: true }).fill('Zwischenzeitlich bearbeitet');
    await push(page, 'delta', { text: 'Guten Tag,\n<script>window.exploited=true</script> ' });
    await page.waitForFunction(() => document.querySelector('[data-formulation-status]').dataset.state === 'streaming');
    assert.equal(await page.getByRole('button', { name: 'Vorschlag übernehmen' }).isDisabled(), true);
    await push(page, 'delta', { text: 'Der Techniker kommt Dienstag.' });
    await push(page, 'complete');
    await page.waitForFunction(() => document.querySelector('[data-formulation-status]').dataset.state === 'completed');
    assert.equal(await page.getByLabel('Nachricht', { exact: true }).inputValue(), 'Zwischenzeitlich bearbeitet');
    assert.equal(await page.evaluate(() => window.exploited), undefined);
    assert.equal(state.posts.length, 0);
    await page.getByRole('button', { name: 'Vorschlag übernehmen' }).click();
    assert.match(await page.getByLabel('Nachricht', { exact: true }).inputValue(), /<script>/);
    assert.equal(state.posts.length, 0);
    assert.equal((await page.evaluate(() => window.formulationTest.calls))[0].text, 'Techniker kommt Dienstag.');
});

test('Abbruch im Warten verwirft späte Ausgabe und erhält den zwischenzeitlich bearbeiteten Text', async t => {
    const { page } = await setup(t);
    await start(page);
    await page.getByLabel('Nachricht', { exact: true }).fill('Eigener Text');
    await page.getByRole('button', { name: 'Abbrechen', exact: true }).click();
    await push(page, 'delta', { text: 'Verspätet' });
    await push(page, 'complete');
    assert.equal(await page.locator('[data-formulation-status]').getAttribute('data-state'), 'aborted');
    assert.equal(await page.locator('[data-formulation-output]').innerText(), '');
    assert.equal(await page.getByLabel('Nachricht', { exact: true }).inputValue(), 'Eigener Text');
    assert.equal(await page.getByRole('button', { name: 'Vorschlag übernehmen' }).isDisabled(), true);
    assert.equal((await page.evaluate(() => window.formulationTest.aborts)).length, 1);
});

test('KI-Fehler blockiert manuelles Schreiben und anschliessendes Senden nicht', async t => {
    const { page, state } = await setup(t);
    await start(page);
    await push(page, 'error', { code: 'formulierung-fehlgeschlagen' });
    await page.waitForFunction(() => document.querySelector('[data-formulation-status]').dataset.state === 'error');
    await page.getByLabel('Nachricht', { exact: true }).fill('Manuell formuliert.');
    await page.getByRole('button', { name: 'Nachricht senden', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('#staff-message-form textarea').value === '');
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].text, 'Manuell formuliert.');
});

test('Unbestätigte Speicherung sperrt Eingabe und Übernahme auch bei gleichzeitigem Streamabschluss', async t => {
    const { page, state } = await setup(t);
    state.postStatus = 500;
    await start(page);
    await page.getByRole('button', { name: 'Nachricht senden', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('#staff-message-form > [role=status]').textContent.includes('nicht bestätigt'));
    await push(page, 'delta', { text: 'Vorschlag' });
    await push(page, 'complete');
    await page.waitForFunction(() => document.querySelector('[data-formulation-status]').dataset.state === 'completed');
    assert.equal(await page.getByRole('button', { name: 'Vorschlag übernehmen' }).isDisabled(), true);
    assert.equal(await page.getByLabel('Nachricht', { exact: true }).evaluate(input => input.readOnly), true);
    assert.equal(await page.getByLabel('Memo', { exact: true }).evaluate(input => input.readOnly), true);
});

test('Memo-Speicherung sperrt KI-Start und externe Eingabe', async t => {
    const { page, state } = await setup(t);
    state.postStatus = 500;
    await page.getByLabel('Nachricht', { exact: true }).fill('Externe Aussage');
    await page.getByLabel('Memo', { exact: true }).fill('Nur intern');
    await page.getByRole('button', { name: 'Memo speichern', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[data-action-feedback]').textContent.includes('keine Speicherbestätigung'));
    assert.equal(await page.getByRole('button', { name: 'Mit KI formulieren' }).isDisabled(), true);
    assert.equal(await page.getByLabel('Nachricht', { exact: true }).evaluate(input => input.readOnly), true);
    assert.equal((await page.evaluate(() => window.formulationTest.calls)).length, 0);
});

test('EOF ohne Abschlussereignis gilt als Fehler und ein Kontowechsel startet keinen Modellaufruf', async t => {
    const { page, state } = await setup(t);
    await start(page);
    await push(page, 'delta', { text: 'Unvollständig' });
    await page.evaluate(() => window.formulationTest.stream.close());
    await page.waitForFunction(() => document.querySelector('[data-formulation-status]').dataset.state === 'error');
    state.actor = 'staff-two';
    await page.getByRole('button', { name: 'Mit KI formulieren' }).click();
    await page.waitForFunction(() => document.querySelector('[data-formulation-status]').dataset.state === 'error');
    assert.equal((await page.evaluate(() => window.formulationTest.calls)).length, 1);
});

test('Geteilte UTF-8-Zeichen und Ereignisgrenzen werden korrekt zusammengesetzt', async t => {
    const { page } = await setup(t);
    await start(page);
    await page.evaluate(() => {
        const wire = new TextEncoder().encode('event: delta\r\ndata: {"text":"Grüsse 👋"}\r\n\r\nevent: complete\r\ndata: {}\r\n\r\n');
        for (const byte of wire) window.formulationTest.stream.enqueue(new Uint8Array([byte]));
    });
    await page.waitForFunction(() => document.querySelector('[data-formulation-status]').dataset.state === 'completed');
    assert.equal(await page.locator('[data-formulation-output]').innerText(), 'Grüsse 👋');
});

test('Zwischenzeitlicher Abschluss beendet den Stream nicht und gibt keine Übernahme frei', async t => {
    const { page } = await setup(t);
    await start(page);
    await push(page, 'delta', { text: 'Vorschlag' });
    await page.evaluate(() => {
        document.querySelector('#case-status').textContent = 'Abgeschlossen';
        document.dispatchEvent(new Event('propertyflow-case-refreshed'));
    });
    await push(page, 'complete');
    await page.waitForFunction(() => document.querySelector('[data-formulation-status]').dataset.state === 'completed');
    assert.equal(await page.locator('[data-formulation-output]').innerText(), 'Vorschlag');
    assert.equal(await page.getByLabel('Nachricht', { exact: true }).inputValue(), 'Techniker kommt Dienstag.');
    assert.equal(await page.getByRole('button', { name: 'Vorschlag übernehmen' }).isDisabled(), true);
});
