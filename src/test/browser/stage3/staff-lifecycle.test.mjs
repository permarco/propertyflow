import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

const scripts = {
    wait: await readFile('src/main/resources/static/js/staff-waits.js', 'utf8'),
    change: await readFile('src/main/resources/static/js/staff-follow-up.js', 'utf8'),
    reopen: await readFile('src/main/resources/static/js/staff-reopen.js', 'utf8'),
    busy: await readFile('src/main/resources/static/js/staff-busy.js', 'utf8'),
};
const origin = 'http://127.0.0.1:39998';
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t, mode) {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const state = { actor: 'staff-one', task: '700', version: 2, status: 200, posts: [] };
    await page.route(`${origin}/**`, route => {
        const request = route.request();
        if (request.method() === 'POST') {
            state.posts.push({ path: new URL(request.url()).pathname, fields: Object.fromEntries(new URLSearchParams(request.postData())) });
            return route.fulfill({ status: state.status, body: '' });
        }
        if (new URL(request.url()).pathname.endsWith('/wiedervorlage/verfuegbar')) {
            return route.fulfill({ json: { actor: state.actor, csrf: 'csrf-test', current: {
                available: true, waitId: state.task, version: state.version,
            } } });
        }
        if (new URL(request.url()).pathname.includes('/auftraege/')) return route.fulfill({ json: { state: 'APPLIED' } });
        if (new URL(request.url()).pathname.endsWith('/pruefung')) {
            return route.fulfill({ json: { actor: state.actor, csrf: 'csrf-test', task: {
                available: true, canContinue: true, canClose: true, taskKey: state.task, version: state.version, reviewToken: 'review-token',
            } } });
        }
        const body = mode === 'change' ? `<section hidden><form id="staff-follow-up-form" action="/wiedervorlage-aendern"
            data-availability-url="/wiedervorlage/verfuegbar" data-delete-url="/wiedervorlage-loeschen" data-close-url="/wiedervorlage-abschliessen"
            data-result-url="/wiedervorlage/auftraege/" data-actor="staff-one">
            <input name="date" type="date" aria-label="Datum"><input name="days" type="number" aria-label="Tage">
            <textarea name="reason" aria-label="Begründung"></textarea><button type="submit" disabled>Ändern</button>
            <button type="button" data-follow-up-delete disabled>Löschen</button><button type="button" data-follow-up-reload>Laden</button>
            <details><summary>Fall abschliessen</summary><button type="button" data-follow-up-close disabled>Abschluss bestätigen</button></details><p role="status"></p></form></section>` : mode === 'wait' ? `<section hidden><form id="staff-wait-form" action="/wiedervorlage-setzen"
                data-task-url="/pruefung" data-external-url="/externe-rueckmeldung-abwarten" data-actor="staff-one">
            <input name="date" type="date" aria-label="Datum"><input name="days" type="number" aria-label="Tage">
            <textarea name="reason" aria-label="Begründung"></textarea><button type="submit" disabled>Wiedervorlage setzen</button>
            <button type="button" data-wait-external disabled>Extern warten</button><button type="button" data-wait-reload>Laden</button>
            <p role="status"></p></form></section>`
            : `<section id="staff-reopen-panel" data-task-url="/pruefung" data-actor="staff-one"><button data-reopen-open>Fall wiedereröffnen</button>
                <p role="status"></p><dialog><form action="/wiedereroeffnen"><h2>Fall wiedereröffnen?</h2>
                <button type="button" data-reopen-cancel>Abbrechen</button><button type="submit">Wiedereröffnen</button></form></dialog></section>`;
        return route.fulfill({ contentType: 'text/html; charset=utf-8', body: `<!doctype html><html lang="de"><head><meta charset="utf-8"></head>
            <body><div id="case-head" data-version="2"></div><span id="case-status">${mode === 'change' ? 'Wartet auf Wiedervorlage' : mode === 'wait' ? 'In Bearbeitung' : 'Abgeschlossen'}</span>
            ${body}</body></html>` });
    });
    await page.goto(origin);
    await page.addScriptTag({ content: scripts.busy });
    await page.addScriptTag({ content: scripts[mode] });
    if (mode !== 'reopen') await page.waitForFunction(() => !document.querySelector('[type="submit"]').disabled);
    return { page, state };
}

test('follow-up sends the reviewed native task and preserves the draft after an unknown response', async t => {
    const { page, state } = await setup(t, 'wait');
    await page.getByLabel('Tage').fill('3');
    await page.getByLabel('Begründung').fill('Nur intern');
    state.task = '701';
    state.status = 500;
    await page.getByRole('button', { name: 'Wiedervorlage setzen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('unklar'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].fields.userTaskKey, '700');
    assert.equal(state.posts[0].fields.days, '3');
    assert.equal(state.posts[0].fields.reason, 'Nur intern');
    assert.equal(await page.getByLabel('Begründung').inputValue(), 'Nur intern');
    assert.equal(await page.locator('[type="submit"]').isDisabled(), true);
    assert.equal(state.posts.length, 1);
});

test('follow-up blocks a changed account and stale displayed version', async t => {
    const { page, state } = await setup(t, 'wait');
    state.actor = 'staff-two';
    await page.getByRole('button', { name: 'Laden', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('nicht verfügbar'));
    assert.equal(await page.locator('[type="submit"]').isDisabled(), true);
    assert.equal(state.posts.length, 0);
});

test('reopening requires confirmation and cancelling sends no command', async t => {
    const { page, state } = await setup(t, 'reopen');
    await page.getByRole('button', { name: 'Fall wiedereröffnen', exact: true }).click();
    await page.getByRole('button', { name: 'Abbrechen', exact: true }).click();
    assert.equal(state.posts.length, 0);
    await page.getByRole('button', { name: 'Fall wiedereröffnen', exact: true }).click();
    state.status = 200;
    await page.getByRole('button', { name: 'Wiedereröffnen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('wiedereröffnet'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].fields.version, '2');
    assert.equal(await page.locator('#case-status').textContent(), 'Abgeschlossen');
});

test('unknown reopening response never retries or exposes a free status choice', async t => {
    const { page, state } = await setup(t, 'reopen');
    await page.getByRole('button', { name: 'Fall wiedereröffnen', exact: true }).click();
    state.status = 500;
    await page.getByRole('button', { name: 'Wiedereröffnen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('unklar'));
    assert.equal(state.posts.length, 1);
    assert.equal('status' in state.posts[0].fields, false);
    assert.equal(state.posts[0].fields.csrf, 'csrf-test');
});


test('changing a follow-up uses the displayed epoch and never resends an uncertain command', async t => {
    const { page, state } = await setup(t, 'change');
    await page.getByLabel('Tage').fill('7');
    await page.getByLabel('Begründung').fill('Interner Änderungsgrund');
    state.task = 'later-wait';
    state.status = 500;
    await page.getByRole('button', { name: 'Ändern', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('unklar'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].fields.waitId, '700');
    assert.equal(state.posts[0].fields.version, '2');
    assert.equal(state.posts[0].fields.days, '7');
    assert.equal(await page.getByLabel('Begründung').inputValue(), 'Interner Änderungsgrund');
    assert.equal(await page.getByRole('button', { name: 'Ändern', exact: true }).isDisabled(), true);
});

test('deleting a follow-up sends no date and waits for the persisted result', async t => {
    const { page, state } = await setup(t, 'change');
    await page.getByLabel('Tage').fill('7');
    await page.getByLabel('Begründung').fill('Wieder bearbeiten');
    state.status = 202;
    await page.getByRole('button', { name: 'Löschen', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('gelöscht'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].path, '/wiedervorlage-loeschen');
    assert.equal('days' in state.posts[0].fields, false);
    assert.equal('date' in state.posts[0].fields, false);
    assert.equal(state.posts[0].fields.reason, 'Wieder bearbeiten');
});

test('changing a follow-up blocks account changes without losing the form', async t => {
    const { page, state } = await setup(t, 'change');
    await page.getByLabel('Tage').fill('7');
    state.actor = 'different-staff';
    await page.getByRole('button', { name: 'Laden', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('nicht verfügbar'));
    assert.equal(await page.getByRole('button', { name: 'Ändern', exact: true }).isDisabled(), true);
    assert.equal(await page.getByLabel('Tage').inputValue(), '7');
    assert.equal(state.posts.length, 0);
});


test('follow-up closure requires explicit confirmation and sends the reviewed wait without date fields', async t => {
    const { page, state } = await setup(t, 'change');
    await page.getByLabel('Tage').fill('8');
    await page.getByLabel('Begründung').fill('Erledigung geprüft');
    await page.locator('summary').click();
    assert.equal(state.posts.length, 0);
    await page.getByRole('button', { name: 'Abschluss bestätigen' }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Abschluss bestätigt'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].path, '/wiedervorlage-abschliessen');
    assert.equal(state.posts[0].fields.waitId, '700');
    assert.equal(state.posts[0].fields.version, '2');
    assert.equal(state.posts[0].fields.csrf, 'csrf-test');
    assert.equal(state.posts[0].fields.days, undefined);
    assert.equal(state.posts[0].fields.date, undefined);
    assert.equal(await page.locator('#case-status').textContent(), 'Wartet auf Wiedervorlage');
});

test('unknown follow-up closure response preserves the reason and never repeats the command', async t => {
    const { page, state } = await setup(t, 'change');
    await page.getByLabel('Begründung').fill('Nicht verlieren');
    state.status = 500;
    await page.locator('summary').click();
    await page.getByRole('button', { name: 'Abschluss bestätigen' }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('unklar'));
    assert.equal(state.posts.length, 1);
    assert.equal(await page.getByLabel('Begründung').inputValue(), 'Nicht verlieren');
    assert.equal(await page.getByRole('button', { name: 'Abschluss bestätigen' }).isDisabled(), true);
});
