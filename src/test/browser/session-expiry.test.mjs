import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout as pause } from 'node:timers/promises';
import { base, fixture, freeze, createCase, until, submit } from './ui-test-helpers.mjs';
import { existingCodeMessages, waitForCode } from './mailpit.mjs';

assert.equal(process.env.DEMO_SESSION_EXPIRY_TEST, '60',
    'This suite requires the isolated one-minute session server started with --session-expiry');

async function login(page, email) {
    await page.goto(base + '/anmelden');
    const previous = await existingCodeMessages(email);
    await page.locator('#login-email').fill(email);
    const requested = page.waitForResponse(response => response.url().endsWith('/anmelden/code'));
    await page.getByRole('button', { name: 'Code anfordern', exact: true }).click();
    assert.equal((await requested).status(), 200);
    await page.locator('#login-code').fill(await waitForCode(email, previous));
    await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
    await page.waitForURL(/\/(mieter|mitarbeiter)\/faelle(?:\/[^?]+)?(?:\?.*)?$/);
}

async function sessionCookie(context) {
    const cookie = (await context.cookies(base)).find(cookie => cookie.name === 'JSESSIONID');
    assert.ok(cookie, 'The real server must issue a session cookie');
    assert.equal(cookie.httpOnly, true);
    assert.equal(cookie.sameSite, 'Lax');
    return cookie.value;
}

for (const role of ['staff', 'tenant']) {
    test(`${role}: echter Leerlaufablauf sperrt Lesen und Schreiben; Wiederanmeldung sendet keinen Entwurf automatisch`,
        { timeout: 120000 }, async t => {
        const { page, context, browser } = await fixture(t, { staff: false });
        const email = role === 'staff' ? process.env.DEMO_STAFF_EMAIL : process.env.DEMO_TENANT_EMAIL;
        assert.ok(email);
        await login(page, email);
        // Pause client timers to stop polling. Server time and servlet session expiry stay real.
        await freeze(page);
        const c = await createCase(page, context, role === 'staff');
        const path = role === 'staff' ? c.staffUrl : `${base}/mieter/faelle/${c.id}`;
        await page.goto(path);
        const reader = await context.newPage();
        await freeze(reader);
        await reader.goto(path);
        const draft = `Nach Inaktivität ${randomUUID()}`;
        await page.locator('#message').fill(draft);
        await reader.locator('#message').fill('Lesender Entwurf');
        const version = await page.locator('#case-head').getAttribute('data-version');
        const originalCookie = await sessionCookie(context);
        const requests = [];
        const record = request => { if (request.url().startsWith(base)) requests.push(request.method() + ' ' + request.url()); };
        context.on('request', record);
        console.log(`${role}: waiting 65 real seconds without HTTP requests for servlet session expiry`);
        await pause(65000);
        assert.deepEqual(requests, [], 'Background requests would renew the server session and invalidate this test');
        assert.equal(await sessionCookie(context), originalCookie, 'Do not simulate expiry by deleting browser cookies');

        const deniedWrite = page.waitForResponse(response => response.request().method() === 'POST');
        await page.locator('#send').click();
        assert.equal((await deniedWrite).status(), 401);
        const deniedRead = reader.waitForResponse(response => response.url() === path && response.request().method() === 'GET');
        await reader.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
        assert.equal((await deniedRead).status(), 401);
        for (const tab of [page, reader]) {
            await until(async () => (await tab.locator('#save-state').textContent()).includes('erneut an'));
            assert.equal(await tab.locator('#case-head').isVisible(), false);
            assert.equal(await tab.locator('#history').isVisible(), false);
            assert.equal(await tab.locator('#send').isDisabled(), true);
            assert.equal(await tab.locator('#message').evaluate(element => element.readOnly), true);
        }
        assert.equal(await page.locator('#message').inputValue(), draft);
        assert.equal(await reader.locator('#message').inputValue(), 'Lesender Entwurf');

        const guest = await browser.newContext();
        t.after(() => guest.close());
        const tokenPage = await guest.newPage();
        await freeze(tokenPage);
        await tokenPage.goto(c.tenantUrl);
        assert.equal(await tokenPage.locator('#history article').count(), 1);
        assert.equal(await tokenPage.locator('#case-head').getAttribute('data-version'), version);
        assert.equal(await tokenPage.locator('#message').isEditable(), true, 'Session expiry must not revoke token access');

        const restored = await context.newPage();
        await freeze(restored);
        await login(restored, email);
        assert.notEqual(await sessionCookie(context), originalCookie);
        await restored.goto(path);
        assert.equal(await restored.locator('#message').inputValue(), '');
        assert.equal(await restored.locator('#history article').count(), 1);
        // Playwright's clock is shared by this context. The newly authenticated tab
        // may poll normally; only the two locked tabs must remain inactive.
        context.off('request', record);
        page.on('request', record);
        reader.on('request', record);
        requests.length = 0;
        for (const tab of [page, reader]) {
            await tab.clock.runFor(40001);
            assert.equal(await tab.locator('#send').isDisabled(), true);
        }
        assert.deepEqual(requests, [], 'Locked tabs must not resume polling or replay a write after another tab logs in');
        await restored.locator('#message').fill(draft);
        assert.equal(await submit(restored, restored.locator('#send')), 303);
        await tokenPage.reload();
        assert.equal(await tokenPage.locator('#history article').filter({ hasText: draft }).count(), 1);
        assert.equal(await tokenPage.locator('#history article').count(), 2);
        await tokenPage.locator('#message').fill('Weiterhin erlaubte Nachricht über den persönlichen Link');
        assert.equal(await submit(tokenPage, tokenPage.locator('#send')), 303);
        assert.equal(await tokenPage.locator('#history article').count(), 3);
        page.off('request', record);
        reader.off('request', record);
    });
}
