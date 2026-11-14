import test from 'node:test';
import assert from 'node:assert/strict';
import { fixture, base, createCase, change, publish, freeze, until, editor } from './ui-test-helpers.mjs';

async function focused(locator) {
    return locator.evaluate(element => element === document.activeElement);
}

// Exercise the real tab order; do not use locator.focus() or clicks to reach controls.
async function tabTo(page, locator) {
    for (let step = 0; step < 100; step++) {
        if (await focused(locator)) return;
        await page.keyboard.press('Tab');
    }
    assert.fail('Control cannot be reached with Tab: ' + locator);
}

async function keyboardSave(page, button) {
    await tabTo(page, button);
    const response = page.waitForResponse(r => r.request().method() === 'POST');
    await page.keyboard.press('Enter');
    const status = (await response).status();
    await until(async () => !(await page.locator('#save-state').textContent()).includes('Wird gespeichert'));
    return status;
}

for (const updated of [false, true]) {
    test(`UI3: Editor per Tastatur abbrechen stellt Fokus wieder her${updated ? ' nach Polling' : ''}`, async t => {
        const { page, context } = await fixture(t);
        await freeze(page);
        const c = await createCase(page, context);
        const assignment = editor(page, '#assigned-unit');
        await tabTo(page, assignment.locator('summary'));
        await page.keyboard.press('Enter');
        await tabTo(page, page.locator('#assigned-unit'));
        await page.keyboard.press('ControlOrMeta+A');
        await page.keyboard.type('Ungespeicherte Eingabe');
        if (updated) {
            await change(context, c, 'unit', 'Extern bestätigte Wohnung');
            await page.clock.runFor(20000);
            await page.locator('#updates').waitFor();
            await page.keyboard.press('Escape');
        }
        await tabTo(page, assignment.getByRole('button', { name: 'Abbrechen', exact: true }));
        await page.keyboard.press('Enter');
        assert.equal(await assignment.evaluate(element => element.open), false);
        assert.equal(await focused(assignment.locator('summary')), true);
        await page.keyboard.press('Enter');
        await tabTo(page, page.locator('#assigned-unit'));
        assert.equal(await page.locator('#assigned-unit').inputValue(), updated ? 'Extern bestätigte Wohnung' : 'Originalwohnung 3');
    });
}

for (const conflict of [false, true]) {
    test(`UI3: Tastaturfokus nach ${conflict ? 'Versionskonflikt auf Fehlermeldung' : 'Speichern auf Editorüberschrift'}`, async t => {
        const { page, context } = await fixture(t);
        await freeze(page);
        const c = await createCase(page, context);
        const assignment = editor(page, '#assigned-unit');
        await tabTo(page, assignment.locator('summary'));
        await page.keyboard.press('Enter');
        await tabTo(page, page.locator('#assigned-unit'));
        await page.keyboard.press('ControlOrMeta+A');
        await page.keyboard.type('Tastaturwohnung');
        if (conflict) await change(context, c, 'unit', 'Andere Bearbeitung');
        assert.equal(await keyboardSave(page, assignment.getByRole('button', { name: 'Zuordnung bestätigen' })), conflict ? 409 : 303);
        assert.equal(await focused(conflict ? page.locator('#save-state') : assignment.locator('summary')), true);
        assert.match(await assignment.locator('summary').textContent(), conflict ? /Andere Bearbeitung/ : /Tastaturwohnung/);
        await tabTo(page, assignment.locator('summary'));
        await page.keyboard.press('Enter');
        await tabTo(page, page.locator('#assigned-unit'));
        assert.equal(await page.locator('#assigned-unit').isVisible(), true);
    });
}

test('UI3: eine langsame Speicherantwort stiehlt keinen inzwischen weiterbewegten Fokus', async t => {
    const { page, context } = await fixture(t);
    await freeze(page);
    await createCase(page, context);
    const assignment = editor(page, '#assigned-unit');
    await tabTo(page, assignment.locator('summary'));
    await page.keyboard.press('Enter');
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    t.after(() => release());
    await page.route('**/aktion', async route => { await gate; await route.continue(); });
    await tabTo(page, assignment.getByRole('button', { name: 'Zuordnung bestätigen' }));
    await page.keyboard.press('Enter');
    await tabTo(page, page.locator('#history'));
    // The focused region is also replaced by the successful update.
    release();
    await until(async () => (await page.locator('#save-state').textContent()).includes('Änderung gespeichert'));
    assert.equal(await focused(page.locator('#history')), true);
});

test('UI3: benannter Aktualisierungsdialog schliesst mit Escape und erhält Entwurf und Schreibposition', async t => {
    const { page, context } = await fixture(t);
    await freeze(page);
    const c = await createCase(page, context);
    await tabTo(page, page.locator('#message'));
    await page.keyboard.type('Entwurf');
    await page.keyboard.press('ArrowLeft');
    await publish(context, c, 'Neue externe Information');
    await page.clock.runFor(20000);
    await page.locator('#updates').waitFor();
    assert.equal(await page.getByRole('dialog', { name: 'Fall aktualisiert', exact: true }).count(), 1);
    assert.equal(await focused(page.locator('#updates button')), true);
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#updates').isVisible(), false);
    assert.equal(await focused(page.locator('#message')), true);
    await page.keyboard.type('X');
    assert.equal(await page.locator('#message').inputValue(), 'EntwurXf');
    await publish(context, c, 'Zweite externe Information');
    await page.clock.runFor(20000);
    await page.locator('#updates').waitFor();
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('#updates').isVisible(), false);
    assert.equal(await focused(page.locator('#message')), true);
});

test('UI2: Polling erhält den fokussierten Falllink und setzt bei Wegfall Fokus auf die Ergebnisregion', async t => {
    const { page, context } = await fixture(t);
    await freeze(page);
    const c = await createCase(page, context);
    await page.goto(`${base}/mitarbeiter/faelle?q=${c.id}`);
    const link = page.locator('.case-link');
    await tabTo(page, link);
    await change(context, c, 'unit', 'Neue Listenwohnung');
    await page.clock.runFor(20000);
    await until(async () => (await page.locator('#results').textContent()).includes('Neue Listenwohnung'));
    assert.equal(await focused(link), true);
    await change(context, c, 'close');
    await page.clock.runFor(20000);
    await until(async () => await link.count() === 0);
    assert.equal(await focused(page.locator('#results')), true);
    assert.match(await page.locator('#load-state').textContent(), /zuvor fokussierte Fall/);
    await page.keyboard.press('Shift+Tab');
    assert.equal(await focused(page.locator('#reset')), true);
});

test('UI1: serverseitiger Formularfehler lässt sich per Tastatur finden und korrigieren', async t => {
    const { page } = await fixture(t, { staff: false });
    await page.goto(base + '/mieter/fall');
    for (const [id, value] of Object.entries({ title: 'Tastaturprüfung', unit: 'Wohnung 1', email: 'a@b', description: 'Heizung kalt' })) {
        await tabTo(page, page.locator('#' + id));
        await page.keyboard.type(value);
    }
    await tabTo(page, page.locator('#intake-form button'));
    const response = page.waitForResponse(r => r.request().method() === 'POST');
    await page.keyboard.press('Enter');
    assert.equal((await response).status(), 400);
    await page.locator('#form-error').waitFor();
    assert.equal(await focused(page.locator('#form-error')), true);
    await tabTo(page, page.locator('#form-error a[href="#email"]'));
    await page.keyboard.press('Enter');
    assert.equal(await focused(page.locator('#email')), true);
    await page.keyboard.press('ControlOrMeta+A');
    await page.keyboard.type('keyboard@example.test');
    await tabTo(page, page.locator('#intake-form button'));
    await page.keyboard.press('Enter');
    await page.waitForURL(/\/mieter\/fall\/zugang\//);
    assert.equal(await page.locator('#history article').count(), 1);
});

test('UI1: Nachricht bleibt nach Fehler erreichbar und erfolgreicher Versand setzt Fokus zurück ins Textfeld', async t => {
    const { page, context } = await fixture(t, { staff: false });
    await freeze(page);
    await createCase(page, context, false);
    await tabTo(page, page.locator('#message'));
    await page.keyboard.type('Nachricht per Tastatur');
    await page.route('**/nachrichten', route => route.abort('failed'));
    await tabTo(page, page.locator('#send'));
    await page.keyboard.press('Enter');
    await until(async () => await page.locator('#save-state.error').count() === 1);
    assert.equal(await focused(page.locator('#save-state')), true);
    assert.equal(await page.locator('#message').inputValue(), 'Nachricht per Tastatur');
    await page.unroute('**/nachrichten');
    assert.equal(await keyboardSave(page, page.locator('#send')), 303);
    assert.equal(await focused(page.locator('#message')), true);
    assert.equal(await page.locator('#message').inputValue(), '');
});
