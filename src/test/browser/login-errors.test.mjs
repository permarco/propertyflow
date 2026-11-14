import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout as pause } from 'node:timers/promises';
import { fixture, base } from './ui-test-helpers.mjs';
import { existingCodeMessages, waitForCode } from './mailpit.mjs';

async function clickPost(page, name, path) {
    const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === path);
    await page.getByRole('button', { name, exact: true }).click();
    const result = await response;
    await page.waitForLoadState();
    return result.status();
}

async function denied(context) {
    assert.equal((await context.request.get(base + '/mieter/faelle')).status(), 401);
    assert.equal((await context.request.get(base + '/mitarbeiter/faelle', {
        headers: { 'X-PropertyFlow-Request': 'partial' }, maxRedirects: 0,
    })).status(), 401);
}

async function requestAfterCooldown(page, path, email) {
    const deadline = Date.now() + 65000;
    for (;;) {
        if (email) await page.locator('#login-email').fill(email);
        const status = await clickPost(page, email ? 'Code anfordern' : 'Neuen Code anfordern', path + '/code');
        if (status === 200) return;
        assert.equal(status, 429);
        assert.match(await page.locator('#login-error').textContent(), /Bitte warten/);
        assert.ok(Date.now() < deadline, 'The real server resend cooldown must end');
        await pause(2000);
    }
}

for (const registration of [false, true]) {
    const path = registration ? '/registrieren' : '/anmelden';
    const verify = registration ? 'E-Mail bestätigen' : 'Anmelden';
    test(`${path}: Fehlversuchslimit, zu früher Versand und erfolgreicher neuer Code${registration ? '' : ' nach Adresskorrektur'}`,
        { timeout: 180000 }, async t => {
        const { page, context } = await fixture(t, { staff: false });
        const email = `login-errors-${randomUUID()}@example.test`;
        if (!registration) {
            // Create a private account so the runner's staff session and other tests' limits stay independent.
            const previous = await existingCodeMessages(email);
            await page.goto(base + '/registrieren');
            await page.locator('#login-email').fill(email);
            assert.equal(await clickPost(page, 'Code anfordern', '/registrieren/code'), 200);
            await page.locator('#login-code').fill(await waitForCode(email, previous));
            assert.equal(await clickPost(page, 'E-Mail bestätigen', '/registrieren/pruefen'), 303);
            await page.waitForURL(base + '/mieter/faelle');
            await page.getByRole('button', { name: 'Abmelden', exact: true }).click();
            await page.waitForURL(base + '/mieter/fall');
        }
        await page.goto(base + path);
        let unknownNotice;
        if (!registration) {
            await page.locator('#login-email').fill(`unknown-${randomUUID()}@example.test`);
            assert.equal(await clickPost(page, 'Code anfordern', path + '/code'), 200);
            unknownNotice = await page.locator('.notice').textContent();
            await page.locator('#login-code').fill('123456');
            assert.equal(await clickPost(page, verify, path + '/pruefen'), 400);
            await denied(context);
            await page.getByRole('link', { name: 'E-Mail-Adresse ändern', exact: true }).click();
            await page.locator('#login-email').waitFor();
            assert.equal(await page.locator('#login-error').count(), 0);
        }
        const previous = await existingCodeMessages(email);
        await requestAfterCooldown(page, path, email);
        if (unknownNotice) assert.equal(await page.locator('.notice').textContent(), unknownNotice,
            'The response must not disclose whether an account exists');
        const code = await waitForCode(email, previous);
        const incorrect = code === '123456' ? '654321' : '123456';
        for (let attempt = 0; attempt < 5; attempt++) {
            await page.locator('#login-code').fill(incorrect);
            assert.equal(await clickPost(page, verify, path + '/pruefen'), 400);
            assert.match(await page.locator('#login-error').textContent(), /ungültig oder abgelaufen/);
            assert.equal(await page.locator('#login-code').getAttribute('aria-describedby'), 'login-error');
            assert.equal(await page.getByRole('button', { name: verify, exact: true }).isEnabled(), true);
            await denied(context);
        }
        await page.locator('#login-code').fill(code);
        assert.equal(await clickPost(page, verify, path + '/pruefen'), 400, 'Five failures must invalidate even the correct code');
        await denied(context);
        const delivered = await existingCodeMessages(email);
        assert.equal(await clickPost(page, 'Neuen Code anfordern', path + '/code'), 429);
        assert.match(await page.locator('#login-error').textContent(), /Bitte warten/);
        assert.equal(await page.locator('#login-code').isVisible(), true, 'A denied resend must keep the verification step');
        assert.deepEqual(await existingCodeMessages(email), delivered, 'A denied resend must not send another mail');
        await requestAfterCooldown(page, path);
        assert.equal(await page.locator('#login-error').count(), 0);
        await page.locator('#login-code').fill(await waitForCode(email, delivered));
        assert.equal(await clickPost(page, verify, path + '/pruefen'), 303);
        await page.waitForURL(base + '/mieter/faelle');
        assert.equal(await page.locator('.account-identity strong').textContent(), email);
        assert.equal((await context.request.get(base + '/mitarbeiter/faelle')).status(), 403);
    });
}
