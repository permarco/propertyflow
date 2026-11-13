import test from 'node:test';
import assert from 'node:assert/strict';
import { fixture, createCase, freeze, until, submit } from './ui-test-helpers.mjs';

for (const state of ['waiting', 'streaming']) {
    test(`UI3: KI-Abbruch während ${state} und Neustart vermischen keine Ausgaben`, { timeout: 30000 }, async t => {
        const { page, context } = await fixture(t);
        await freeze(page);
        await createCase(page, context);
        let posts = 0;
        page.on('request', request => { if (request.method() === 'POST') posts++; });
        await page.locator('#message').fill('ERSTER AUFTRAG');
        await page.locator('#generate').click();
        if (state === 'streaming') await page.clock.runFor(900);
        assert.equal(await page.locator('#ai-status').getAttribute('data-state'), state);
        await page.locator('#abort').click();
        assert.equal(await page.locator('#ai-status').getAttribute('data-state'), 'aborted');
        const stopped = await page.locator('#ai-output').textContent();
        await page.clock.runFor(5000);
        assert.equal(await page.locator('#ai-output').textContent(), stopped);
        assert.equal(await page.locator('#adopt').isDisabled(), true);
        assert.equal(await page.locator('#generate').isEnabled(), true);
        await page.locator('#message').fill('ZWEITER AUFTRAG');
        await page.locator('#generate').click();
        assert.equal(await page.locator('#ai-output').textContent(), '');
        await page.clock.runFor(5000);
        assert.equal(await page.locator('#ai-status').getAttribute('data-state'), 'completed');
        const proposal = await page.locator('#ai-output').textContent();
        assert.match(proposal, /ZWEITER AUFTRAG/);
        assert.doesNotMatch(proposal, /ERSTER AUFTRAG/);
        assert.equal(await page.locator('#message').inputValue(), 'ZWEITER AUFTRAG');
        await page.locator('#adopt').click();
        assert.equal(await page.locator('#message').inputValue(), proposal);
        assert.equal(await page.locator('#ai').isHidden(), true);
        assert.equal(posts, 0, 'Generate, abort and adopt must not send any case operation');
        assert.equal(await page.locator('#history article').count(), 1);
    });
}

for (const failSave of [false, true]) {
    test(`UI3: KI-Fehler gibt Eingaben während einer ${failSave ? 'fehlgeschlagenen' : 'erfolgreichen'} Speicherung nicht frei`,
        { timeout: 30000 }, async t => {
        const { page, context } = await fixture(t);
        await freeze(page);
        await createCase(page, context);
        const draft = 'Unveränderter Nachrichtentext';
        await page.locator('#message').fill(draft);
        await page.locator('#generate').click();
        await page.locator('#abort').click();
        await page.locator('.mock-controls summary').click();
        await page.locator('#mock-mode').selectOption('error');
        await page.locator('#answer').check();
        await page.locator('#generate').click();
        let release;
        const gate = new Promise(resolve => { release = resolve; });
        t.after(() => release());
        let intercepted = false;
        await page.route('**/nachrichten', async route => {
            intercepted = true;
            await gate;
            if (failSave) await route.fulfill({ status: 503, contentType: 'text/html', body: '<p role="alert">Speichern im Test fehlgeschlagen</p>' });
            else await route.continue();
        });
        await page.locator('#send').click();
        await until(() => intercepted);
        await page.clock.runFor(2000);
        assert.equal(await page.locator('#ai-status').getAttribute('data-state'), 'error');
        for (const id of ['message', 'internal', 'answer', 'generate', 'adopt', 'discard-proposal', 'send']) {
            assert.equal(await page.locator('#' + id).isDisabled(), true, id);
        }
        assert.equal(await page.locator('#message').inputValue(), draft);
        assert.equal(await page.locator('#answer').isChecked(), true);
        assert.equal(await page.locator('#history article').count(), 1);
        release();
        await until(async () => !(await page.locator('#save-state').textContent()).includes('Wird gespeichert'));
        assert.equal(await page.locator('#message').isEnabled(), true);
        assert.equal(await page.locator('#adopt').isDisabled(), true, 'Incomplete AI output must remain unusable');
        assert.equal(await page.locator('#message').inputValue(), failSave ? draft : '');
        assert.equal(await page.locator('#answer').isChecked(), failSave);
        assert.equal(await page.locator('#history article').filter({ hasText: draft }).count(), failSave ? 0 : 1);
        if (failSave) {
            await page.unroute('**/nachrichten');
            assert.equal(await submit(page, page.locator('#send')), 303);
            assert.equal(await page.locator('#history article').filter({ hasText: draft }).count(), 1);
        }
        assert.equal(await page.locator('#history article').count(), 2);
    });
}

test('UI3: abgebrochene Navigation erhält den laufenden KI-Versuch und den ungesendeten Entwurf', { timeout: 30000 }, async t => {
    const { page, context } = await fixture(t);
    await freeze(page);
    await createCase(page, context);
    const url = page.url();
    await page.locator('#message').fill('Entwurf beim Navigationsversuch');
    await page.locator('#generate').click();
    await page.clock.runFor(900);
    assert.equal(await page.locator('#ai-status').getAttribute('data-state'), 'streaming');
    let warnings = 0;
    let posts = 0;
    page.on('request', request => { if (request.method() === 'POST') posts++; });
    page.on('dialog', async dialog => {
        assert.equal(dialog.type(), 'beforeunload');
        warnings++;
        await dialog.dismiss();
    });
    await page.getByRole('link', { name: 'Zur Fallübersicht' }).click({ noWaitAfter: true });
    await until(() => warnings === 1);
    assert.equal(page.url(), url);
    await page.clock.runFor(5000);
    assert.equal(await page.locator('#ai-status').getAttribute('data-state'), 'completed');
    assert.equal(await page.locator('#message').inputValue(), 'Entwurf beim Navigationsversuch');
    await page.locator('#adopt').click();
    assert.match(await page.locator('#message').inputValue(), /Entwurf beim Navigationsversuch/);
    assert.equal(posts, 0);
    assert.equal(await page.locator('#history article').count(), 1);
});
