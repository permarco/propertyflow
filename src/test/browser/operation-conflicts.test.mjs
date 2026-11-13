import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fixture, createCase, editor, submit, base } from './ui-test-helpers.mjs';

test('UI1: geänderter Retry meldet Konflikt, erhält Eingabe und legt keinen zweiten Fall an', async t => {
    const { page, context } = await fixture(t, { staff: false });
    await page.goto(`${base}/mieter/fall`);
    const form = { csrf: await page.locator('#intake-form input[name=csrf]').inputValue(),
        operation: await page.locator('#intake-form input[name=operation]').inputValue(),
        title: 'Originaler Betreff', unit: 'Wohnung 1', email: 'retry@example.test', description: 'Originaler Text',
        accountSubmission: 'false' };
    const first = await context.request.post(`${base}/mieter/fall`, { form, maxRedirects: 0 });
    assert.equal(first.status(), 303);
    const originalUrl = base + first.headers().location;
    for (const field of ['title', 'unit', 'email', 'description']) await page.locator(`#${field}`).fill(form[field]);
    await page.locator('#title').fill('Geänderter Betreff');
    const rejected = page.waitForResponse(r => r.request().method() === 'POST');
    await page.getByRole('button', { name: /Anliegen absenden/ }).click();
    assert.equal((await rejected).status(), 409);
    await page.locator('#form-error').waitFor();
    assert.match(await page.locator('#form-error').textContent(), /bereits mit anderem Inhalt/);
    assert.equal(await page.locator('#title').inputValue(), 'Geänderter Betreff');
    assert.equal(await page.locator('#intake-form input[name=operation]').inputValue(), form.operation);
    const replay = await context.request.post(`${base}/mieter/fall`, { form, maxRedirects: 0 });
    assert.equal(replay.status(), 303);
    assert.equal(base + replay.headers().location, originalUrl);
    await page.goto(originalUrl);
    assert.equal(await page.getByRole('heading', { name: 'Originaler Betreff', exact: true }).count(), 1);
    assert.equal(await page.locator('#history article').count(), 1);
});

test('UI3: geänderter Aktions-Retry meldet Konflikt und erhält den gespeicherten Stand', async t => {
    const { page, context } = await fixture(t);
    const c = await createCase(page, context);
    const assignment = editor(page, '#assigned-unit');
    await assignment.locator('summary').click();
    const form = { csrf: c.csrf, operation: await assignment.locator('input[name=operation]').inputValue(),
        version: await assignment.locator('input[name=version]').inputValue(), action: 'unit', value: 'Wohnung A', reason: '' };
    const first = await context.request.post(c.staffUrl + '/aktion', { form, maxRedirects: 0 });
    assert.equal(first.status(), 303);
    await page.locator('#assigned-unit').fill('Wohnung B');
    assert.equal(await submit(page, assignment.getByRole('button', { name: 'Zuordnung bestätigen' })), 409);
    assert.match(await page.locator('#save-state').textContent(), /bereits mit anderem Inhalt/);
    const replay = await context.request.post(c.staffUrl + '/aktion', { form, maxRedirects: 0 });
    assert.equal(replay.status(), 303);
    await page.reload();
    assert.match(await editor(page, '#assigned-unit').locator('summary').textContent(), /Wohnung A/);
    assert.equal(await page.locator('#history article').count(), 2);
    assert.equal(await page.locator('#history article').filter({ hasText: 'Wohnung B' }).count(), 0);
});
