import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

const script = await readFile('src/main/resources/static/js/staff-review.js', 'utf8');
const origin = 'http://127.0.0.1:39998';
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t, initial = {}) {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const state = { taskKey: '700', version: 2, reviewToken: 'reasons-one', actor: 'staff-one', available: true, postStatus: 200, posts: [], ...initial };
    await page.route(`${origin}/**`, route => {
        const request = route.request();
        if (request.method() === 'POST') {
            state.posts.push(Object.fromEntries(new URLSearchParams(request.postData())));
            return route.fulfill({ status: state.postStatus, json: { state: 'CONFIRMED' } });
        }
        if (new URL(request.url()).pathname.endsWith('/pruefung')) {
            return route.fulfill({ json: { actor: state.actor, csrf: 'csrf-test', task: {
                available: state.available, canContinue: state.available, taskKey: state.taskKey, version: state.version, reviewToken: state.reviewToken,
            } } });
        }
        return route.fulfill({ contentType: 'text/html; charset=utf-8', body: `<!doctype html><html lang="de">
            <head><meta charset="utf-8"></head><body><div id="case-head" data-version="2"></div>
            <span id="case-status">Mitarbeiterprüfung erforderlich</span><section hidden>
            <form id="staff-review-form" action="/mitarbeiter/faelle/CASE/fortsetzen" data-review-url="/mitarbeiter/faelle/CASE/pruefung"
                  data-actor="staff-one"><textarea name="reason" aria-label="Interne Begründung"></textarea>
                <button type="submit" disabled>Bearbeitung fortsetzen</button>
                <button type="button" data-review-reload>Neu laden</button><p role="status"></p>
            </form></section></body></html>` });
    });
    await page.goto(origin);
    await page.addScriptTag({ content: script });
    if (state.available) await page.waitForFunction(() => !document.querySelector('[type="submit"]').disabled);
    else await page.getByRole('status').getByText('Derzeit liegt keine freigegebene Mitarbeiterprüfung vor.', { exact: false }).waitFor();
    return { page, state };
}

test('an old tab submits only its displayed task and never silently targets a new task', async t => {
    const { page, state } = await setup(t);
    state.taskKey = '701';
    state.postStatus = 400;
    await page.getByRole('button', { name: 'Bearbeitung fortsetzen' }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('hat sich geändert'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].userTaskKey, '700');
    assert.equal(await page.locator('[type="submit"]').isDisabled(), true);
    await page.getByRole('button', { name: 'Neu laden' }).click();
    await page.waitForFunction(() => !document.querySelector('[type="submit"]').disabled);
    state.postStatus = 200;
    await page.getByRole('button', { name: 'Bearbeitung fortsetzen' }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Entscheidung bestätigt'));
    assert.equal(state.posts[1].userTaskKey, '701');
    assert.notEqual(state.posts[0].operationId, state.posts[1].operationId);
    assert.equal(await page.locator('#case-status').textContent(), 'Mitarbeiterprüfung erforderlich');
});

test('a task that becomes visible after the case status is loaded without resubmitting or losing a draft', async t => {
    const { page, state } = await setup(t, { available: false });
    assert.equal(await page.getByRole('button', { name: 'Neu laden' }).isVisible(), true);
    assert.equal(await page.locator('[type="submit"]').isDisabled(), true);
    state.available = true;
    await page.evaluate(() => document.dispatchEvent(new Event('propertyflow-case-refreshed')));
    await page.waitForFunction(() => !document.querySelector('[type="submit"]').disabled);
    assert.equal(state.posts.length, 0);
    await page.getByRole('textbox').fill('Bewusste Begründung');
    state.taskKey = '701';
    await page.evaluate(() => document.dispatchEvent(new Event('propertyflow-case-refreshed')));
    await page.getByRole('button', { name: 'Bearbeitung fortsetzen' }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('Entscheidung bestätigt'));
    assert.equal(state.posts[0].userTaskKey, '700');
    assert.equal(state.posts[0].reason, 'Bewusste Begründung');
});

test('an unknown completion response reloads the view without automatically repeating the decision', async t => {
    const { page, state } = await setup(t);
    await page.getByRole('textbox', { name: 'Interne Begründung' }).fill('Nur intern dokumentieren');
    state.postStatus = 500;
    await page.evaluate(() => {
        window.refreshes = 0;
        document.addEventListener('propertyflow-case-changed', () => { window.refreshes++; });
    });
    await page.getByRole('button', { name: 'Bearbeitung fortsetzen' }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('nicht automatisch wiederholt'));
    assert.equal(state.posts.length, 1);
    assert.equal(state.posts[0].reason, 'Nur intern dokumentieren');
    assert.equal(await page.getByRole('textbox').inputValue(), 'Nur intern dokumentieren');
    assert.equal(await page.getByRole('textbox').getAttribute('readonly'), '');
    assert.equal(await page.evaluate(() => window.refreshes), 1);
    assert.equal(await page.locator('[type="submit"]').isDisabled(), true);
    await page.evaluate(() => document.dispatchEvent(new Event('propertyflow-case-refreshed')));
    assert.equal(state.posts.length, 1);
    assert.equal(await page.locator('[type="submit"]').isDisabled(), true);
});

test('background version change and account switch require fresh review instead of transferring the action', async t => {
    const { page, state } = await setup(t);
    await page.evaluate(() => {
        document.querySelector('#case-head').dataset.version = '3';
        document.dispatchEvent(new Event('propertyflow-case-refreshed'));
    });
    assert.equal(await page.locator('[type="submit"]').isDisabled(), true);
    state.actor = 'another-staff';
    state.version = 3;
    await page.getByRole('button', { name: 'Neu laden' }).click();
    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.includes('nicht verfügbar'));
    assert.equal(await page.locator('[type="submit"]').isDisabled(), true);
    assert.equal(state.posts.length, 0);
});
