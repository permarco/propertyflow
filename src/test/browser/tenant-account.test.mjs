import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir } from 'node:fs/promises';
import { existingCodeMessages, waitForCode } from './mailpit.mjs';
import { fixture, base } from './ui-test-helpers.mjs';

test('tenant account: OTP, responsive identity, trusted email and logout', {
    skip: !process.env.DEMO_TENANT_EMAIL,
}, async t => {
    const { page, context } = await fixture(t, { staff: false });
    const email = process.env.DEMO_TENANT_EMAIL;
    const previousIds = await existingCodeMessages(email);
    await page.goto(`${base}/anmelden`);
    await page.locator('#login-email').fill(email);
    await page.getByRole('button', { name: 'Code anfordern', exact: true }).click();
    const code = await waitForCode(email, previousIds);
    await page.locator('#login-code').fill(code);
    await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
    await page.waitForURL(`${base}/mieter/faelle`);
    await page.getByRole('link', { name: 'Neues Anliegen melden', exact: true }).click();
    assert.equal(await page.locator('input[name=email]').count(), 0);
    assert.equal(await page.locator('.account-identity strong').textContent(), email);
    assert.equal((await context.request.get(`${base}/mitarbeiter/faelle`)).status(), 403);
    await mkdir('target/account-review', { recursive: true });
    for (const width of [1440, 768, 390]) {
        await page.setViewportSize({ width, height: 900 });
        assert.equal(await page.getByRole('button', { name: 'Abmelden', exact: true }).isVisible(), true);
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
        await page.screenshot({ path: `target/account-review/intake-${width}.png`, fullPage: true });
    }
    const csrf = await page.locator('#intake-form input[name=csrf]').inputValue();
    const created = await context.request.post(`${base}/mieter/fall`, {
        form: { csrf, operation: randomUUID(), title: 'Konto-Test', unit: 'Wohnung 2',
            description: 'Heizung kalt', email: 'forged@example.test', accountSubmission: 'false' },
        maxRedirects: 0,
    });
    assert.equal(created.status(), 303);
    const caseUrl = base + created.headers().location;
    await page.goto(caseUrl);
    assert.equal(await page.locator('.account-identity strong').textContent(), email);
    const logout = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/abmelden'));
    await page.getByRole('button', { name: 'Abmelden', exact: true }).click();
    assert.equal((await logout).status(), 303);
    await page.waitForURL(`${base}/mieter/fall`);
    assert.equal(await page.locator('input[name=email]').count(), 1);
    assert.equal(await page.getByRole('button', { name: 'Abmelden', exact: true }).count(), 0);
    await page.goto(caseUrl);
    assert.equal(await page.getByRole('heading', { name: 'Konto-Test', exact: true }).isVisible(), true);
    assert.equal(await page.locator('.account-identity').count(), 0);
});
