import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import { launchBrowser } from '../browser-launcher.mjs';

// Echte Templates und Skripte; nur die HTTP-Antworten werden kontrolliert.
const root = 'src/main/resources/';
const origin = 'http://127.0.0.1:39990';
let browser;
before(async () => { browser = await launchBrowser(); });
after(async () => { await browser?.close(); });

async function setup(t, view = 'staff') {
    const context = await browser.newContext();
    t.after(() => context.close());
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    t.after(() => assert.deepEqual(errors, []));
    const state = { status: 200, result: 'PENDING', posts: [] };
    await page.route(`${origin}/**`, route => {
        if (route.request().method() === 'POST') {
            state.posts.push(Object.fromEntries(new URLSearchParams(route.request().postData())));
            return route.fulfill({ status: state.status, json: { state: 'CONFIRMED' } });
        }
        return route.fulfill({ json: { state: state.result, available: true, actor: 'staff', csrf: 'csrf',
            capability: { information: true, question: true },
            task: { available: true, canContinue: true, version: 1, taskKey: '10', reviewToken: 'review' } } });
    });
    await page.goto(origin);
    await page.setContent(await readFile(`${root}templates/${view}/${view === 'staff' ? 'detail' : 'case'}.html`, 'utf8'));
    await page.addStyleTag({ content: await readFile(`${root}static/css/propertyflow.css`, 'utf8') });
    await page.evaluate(() => {
        document.querySelector('#case-head').dataset.version = '1';
        document.querySelector('#case-status').textContent = 'Mitarbeiterprüfung erforderlich';
        document.querySelectorAll('form').forEach(form => {
            form.action = '/submit';
            Object.assign(form.dataset, { actor: 'staff', commandBase: '/actions', reviewUrl: '/review',
                taskUrl: '/review', messageBase: '/messages' });
        });
    });
    const script = async name => page.addScriptTag({ content: await readFile(`${root}static/js/${name}.js`, 'utf8') });
    return { page, state, script };
}

test('Dringlichkeit: vier Farben, Pflichtauswahl, Tastatur und Schliessen erst nach Speicherbestätigung', async t => {
    const { page, state, script } = await setup(t);
    await page.clock.install();
    await script('staff-case-data');
    const form = page.locator('#urgency-form');
    assert.equal(await form.isVisible(), false);
    await page.locator('#urgency-edit-title').click();
    assert.equal(await form.evaluate(f => f.checkValidity()), false);
    assert.deepEqual(await form.locator('input[type=radio]').evaluateAll(inputs => inputs.map(input =>
        getComputedStyle(input.parentElement).backgroundColor)),
    ['rgb(254, 226, 226)', 'rgb(255, 237, 213)', 'rgb(254, 243, 199)', 'rgb(220, 252, 231)']);
    await page.getByRole('radio', { name: 'Hoch', exact: true }).check();
    await page.getByRole('radio', { name: 'Hoch', exact: true }).press('ArrowRight');
    assert.equal(await page.getByRole('radio', { name: 'Normal', exact: true }).isChecked(), true);
    assert.equal(state.posts.length, 0);
    state.status = 409;
    await form.getByRole('button', { name: 'Speichern', exact: true }).click();
    await form.getByText('Konnte nicht erfolgreich gespeichert werden.', { exact: false }).waitFor();
    assert.equal(await form.isVisible(), true);
    assert.equal(state.posts[0].urgency, 'Normal');
    state.status = 200;
    await form.getByRole('button', { name: 'Speichern', exact: true }).click();
    await form.getByText('Speicherung angefordert.', { exact: false }).waitFor();
    assert.equal(await page.getByRole('radio', { name: 'Normal', exact: true }).isDisabled(), true);
    assert.equal(await form.isVisible(), true);
    state.result = 'APPLIED';
    await page.clock.runFor(5000);
    await form.waitFor({ state: 'hidden' });
});

for (const [name, scriptName, formId, heading] of [
    ['Status', 'staff-review', 'staff-review-form', 'staff-review-heading'],
    ['Wiedervorlage', 'staff-waits', 'staff-wait-form', 'staff-wait-heading'],
]) {
    test(`${name}: Fehler erhält offenen Editor, Erfolg schliesst ihn`, async t => {
        const { page, state, script } = await setup(t);
        await script(scriptName);
        await page.locator(`#${heading}`).click();
        const form = page.locator(`#${formId}`);
        await form.locator('[name=reason]').fill('Begründung erhalten');
        if (name === 'Wiedervorlage') await form.locator('[name=days]').fill('3');
        state.status = 400;
        await form.locator('[type=submit]').click();
        await form.locator('[role=status]').getByText('Bitte', { exact: false }).waitFor();
        assert.equal(await form.isVisible(), true);
        assert.equal(await form.locator('[name=reason]').inputValue(), 'Begründung erhalten');
        state.status = 200;
        await form.getByRole('button', { name: 'Neu laden', exact: true }).click();
        await form.locator('[type=submit]').click();
        await form.waitFor({ state: 'hidden' });
    });
}

test('Intern-Toggle erhält getrennte Entwürfe und sperrt Antwortbedarf und Umschalten während Speicherung', async t => {
    const { page, script } = await setup(t);
    await script('staff-messages');
    await script('staff-composer');
    await page.locator('#staff-message-text').fill('Extern');
    const toggle = page.getByRole('switch', { name: 'Intern', exact: true });
    await toggle.check();
    await page.locator('#memo-text').fill('Vertraulich');
    assert.equal(await page.getByRole('switch', { name: 'Mieterantwort erforderlich' }).isDisabled(), true);
    assert.equal(await page.locator('#staff-message-form').isVisible(), false);
    await toggle.uncheck();
    assert.equal(await page.locator('#staff-message-text').inputValue(), 'Extern');
    assert.equal(await page.locator('#memo-text').inputValue(), 'Vertraulich');
    await page.evaluate(() => {
        document.querySelector('#memo-form').dataset.communicationSaving = 'true';
        document.dispatchEvent(new Event('propertyflow-communication-lock'));
    });
    assert.equal(await toggle.isDisabled(), true);
});

for (const view of ['staff', 'tenant']) {
    test(`${view}: Verlaufspfeil sortiert, übersteht Nachladen und speichert die Auswahl`, async t => {
        const { page, script } = await setup(t, view);
        const reset = async () => page.evaluate(() => {
            const messages = document.querySelector('#history .messages');
            delete messages.dataset.order;
            messages.innerHTML = '<article>Alt</article><article>Neu</article>';
            document.dispatchEvent(new Event('propertyflow-case-refreshed'));
        });
        await reset();
        await script('history-order');
        const entries = page.locator('#history .messages article');
        assert.deepEqual(await entries.allTextContents(), ['Neu', 'Alt']);
        await page.locator('[data-history-order]').click();
        assert.deepEqual(await entries.allTextContents(), ['Alt', 'Neu']);
        await reset();
        assert.deepEqual(await entries.allTextContents(), ['Alt', 'Neu']);
        assert.equal(await page.evaluate(() => localStorage.getItem('propertyflow-history-order')), 'oldest');
        await page.reload();
        await page.setContent(await readFile(`${root}templates/${view}/${view === 'staff' ? 'detail' : 'case'}.html`, 'utf8'));
        await reset();
        await script('history-order');
        assert.deepEqual(await entries.allTextContents(), ['Alt', 'Neu']);
        await page.locator('[data-history-order]').press('Enter');
        await reset();
        assert.deepEqual(await entries.allTextContents(), ['Neu', 'Alt']);
    });
}

test('Klappkarten bleiben rechts; nur eine Bearbeitung ist offen und die Radioauswahl passt mobil', async t => {
    const { page } = await setup(t);
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.locator('#staff-review-form').evaluate(form => { form.closest('section').hidden = false; });
    await page.locator('#staff-review-heading').click();
    await page.locator('#urgency-edit-title').click();
    assert.equal(await page.locator('#staff-review-form').isVisible(), false);
    assert.equal(await page.locator('#case-sidebar #urgency-form').count(), 1);
    const sidebar = await page.locator('#case-sidebar').boundingBox();
    const conversation = await page.locator('.conversation').boundingBox();
    assert.ok(sidebar.x > conversation.x);
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.locator('.urgency-options').evaluate(node => node.scrollWidth <= node.clientWidth), true);
});
