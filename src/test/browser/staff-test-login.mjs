import assert from 'node:assert/strict';
import { existingCodeMessages, waitForCode } from './mailpit.mjs';

export const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';

export async function staffStorageState(browser) {
    const shared = process.env.DEMO_STAFF_STORAGE_STATE;
    if (shared) return JSON.parse(shared);

    const email = process.env.DEMO_STAFF_EMAIL || 'staff@example.test';
    const previousIds = await existingCodeMessages(email);
    const context = await browser.newContext({ locale: 'de-CH' });
    try {
        const page = await context.newPage();
        const destination = `${base}/mitarbeiter/faelle?q=Heizung`;
        await page.goto(destination);
        assert.equal(new URL(page.url()).pathname, '/anmelden');
        await page.locator('#login-email').fill(email);
        const request = page.waitForResponse(response => new URL(response.url()).pathname === '/anmelden/code'
            && response.request().method() === 'POST');
        await page.getByRole('button', { name: 'Code anfordern', exact: true }).click();
        assert.equal((await request).status(), 200, 'OTP request failed. Check the demo test account and resend cooldown.');
        const code = await waitForCode(email, previousIds);
        await page.locator('#login-code').fill(code);
        await page.getByRole('button', { name: 'Anmelden', exact: true }).click();
        await page.waitForURL(destination);
        assert.equal(await page.getByRole('heading', { name: 'Fallübersicht' }).isVisible(), true);
        return await context.storageState();
    } finally {
        await context.close();
    }
}
