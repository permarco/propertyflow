import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout as pause } from 'node:timers/promises';
import { existingCodeMessages, waitForCode } from './mailpit.mjs';
import { fixture, base, createCase, freeze, until, staffStorageState } from './ui-test-helpers.mjs';

// Use independent sessions: logging out the runner's shared staff session would
// invalidate every other staff fixture. Respect the real server OTP cooldown.
async function authenticate(page, email, register = false) {
    const path = register ? '/registrieren' : '/anmelden';
    await page.goto(base + path);
    const previous = await existingCodeMessages(email);
    const deadline = Date.now() + 65000;
    for (;;) {
        await page.locator('#login-email').fill(email);
        const requested = page.waitForResponse(r => r.url().endsWith(path + '/code'));
        await page.getByRole('button', { name: 'Code anfordern', exact: true }).click();
        const status = (await requested).status();
        if (status === 200) break;
        assert.equal(status, 429);
        assert.ok(Date.now() < deadline, 'OTP cooldown did not end');
        await pause(2000);
    }
    await page.locator('#login-code').fill(await waitForCode(email, previous));
    await page.getByRole('button', { name: register ? 'E-Mail bestätigen' : 'Anmelden', exact: true }).click();
    await page.waitForURL(/\/(mieter|mitarbeiter)\/faelle(?:\/[^?]+)?(?:\?.*)?$/, { timeout: 10000 });
}

async function logout(context) {
    const tab = await context.newPage();
    await tab.goto(base + '/mieter/fall');
    await tab.getByRole('button', { name: 'Abmelden', exact: true }).click();
    await tab.waitForURL(base + '/mieter/fall');
    assert.equal(await tab.locator('#email').isVisible(), true);
    await tab.close();
}

for (const role of ['staff', 'tenant']) {
    test(`${role}: echte Abmeldung in zweitem Tab sperrt Lesen und Schreiben ohne späteren Replay`, { timeout: 180000 }, async t => {
        const { page, context } = await fixture(t, { staff: false });
        const email = role === 'staff' ? process.env.DEMO_STAFF_EMAIL || 'staff@example.test' : `loss-${randomUUID()}@example.test`;
        await authenticate(page, email, role === 'tenant');
        const c = await createCase(page, context, role === 'staff');
        const url = role === 'staff' ? c.staffUrl : `${base}/mieter/faelle/${c.id}`;
        await page.goto(url);
        const reader = await context.newPage();
        await reader.goto(url);
        await freeze(page);
        await freeze(reader);
        const draft = `Nicht gesendet ${randomUUID()}`;
        await page.locator('#message').fill(draft);
        await reader.locator('#message').fill('Lesender Entwurf');
        await logout(context);
        const deniedWrite = page.waitForResponse(r => r.request().method() === 'POST');
        await page.locator('#send').click();
        assert.equal((await deniedWrite).status(), 401);
        const deniedRead = reader.waitForResponse(r => r.url() === url && r.request().method() === 'GET');
        await reader.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
        assert.equal((await deniedRead).status(), 401);
        for (const tab of [page, reader]) {
            await until(async () => (await tab.locator('#save-state').textContent()).includes('erneut an'));
            for (const selector of ['#case-head', '#history']) assert.equal(await tab.locator(selector).isVisible(), false);
            if (role === 'staff') assert.equal(await tab.locator('#case-sidebar').isVisible(), false);
            assert.equal(await tab.locator('#send').isDisabled(), true);
            assert.equal(await tab.locator('#message').evaluate(e => e.readOnly), true);
        }
        assert.equal(await page.locator('#message').inputValue(), draft);
        const requests = [];
        for (const tab of [page, reader]) tab.on('request', r => { if (r.url().startsWith(url)) requests.push(r.method()); });
        await page.clock.runFor(40001);
        await reader.clock.runFor(40001);
        assert.deepEqual(requests, [], 'Polling must stop after denial');
        const token = await context.newPage();
        await token.goto(c.tenantUrl);
        assert.equal(await token.locator('#history article').count(), 1, 'Token access remains available after logout');
        await token.close();
        page.on('dialog', dialog => dialog.accept());
        await page.getByRole('link', { name: 'Erneut anmelden', exact: true }).click();
        await page.waitForURL(base + '/anmelden');
        await authenticate(page, email);
        await page.goto(url);
        assert.equal(await page.locator('#history article').filter({ hasText: draft }).count(), 0);
        assert.equal(await page.locator('#message').inputValue(), '');
        await reader.clock.runFor(40001);
        assert.equal(await reader.locator('#send').isDisabled(), true, 'An old tab stays locked after another tab logs in');
        assert.equal(requests.includes('POST'), false, 'Rejected writes must never replay on login');
    });
}

test('UI1: Kontoabmeldung vor Anlage erhält Eingaben und erlaubt explizite Gastanlage', { timeout: 30000 }, async t => {
    const { page, context, browser } = await fixture(t, { staff: false });
    await authenticate(page, `intake-${randomUUID()}@example.test`, true);
    await page.goto(base + '/mieter/fall');
    const fields = { title: `Sitzungsverlust ${randomUUID()}`, unit: 'Wohnung 3', description: 'Heizung bleibt kalt.' };
    for (const [name, value] of Object.entries(fields)) await page.locator('#' + name).fill(value);
    const operation = await page.locator('input[name=operation]').inputValue();
    await logout(context);
    const denied = page.waitForResponse(r => r.request().method() === 'POST');
    await page.getByRole('button', { name: /Anliegen absenden/ }).click();
    assert.equal((await denied).status(), 401);
    await page.locator('#form-error').waitFor();
    assert.match(await page.locator('#form-error').textContent(), /Anmeldung.*nicht mehr gültig/);
    for (const [name, value] of Object.entries(fields)) assert.equal(await page.locator('#' + name).inputValue(), value);
    assert.equal(await page.locator('input[name=operation]').inputValue(), operation);
    const observer = await browser.newContext({ storageState: await staffStorageState(browser) });
    const list = await observer.newPage();
    await list.goto(base + '/mitarbeiter/faelle?q=' + encodeURIComponent(fields.title));
    assert.equal(await list.locator('#results tr[data-case]').count(), 0);
    await page.locator('#email').fill('explicit-guest@example.test');
    await page.getByRole('button', { name: /Anliegen absenden/ }).click();
    await page.waitForURL(/\/mieter\/fall\/zugang\//);
    assert.equal(await page.locator('#history article').count(), 1);
    await list.reload();
    assert.equal(await list.locator('#results tr[data-case]').count(), 1);
    await observer.close();
});
