import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';
import { existingCodeMessages, waitForCode } from './mailpit.mjs';

export const base = process.env.PROPERTYFLOW_E2E_URL;
assert.ok(base, 'Den isolierten Backend-Runner verwenden.');

export async function until(check, label, timeout = 90000) {
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
        const result = await check();
        if (result) return result;
        await delay(250);
    }
    assert.fail(`Zeitüberschreitung: ${label}`);
}

export async function login(page, email, route = '/anmelden') {
    const before = await existingCodeMessages(email);
    await page.goto(base + route);
    await page.locator('#login-email').fill(email);
    await page.getByRole('button', { name: 'Code anfordern', exact: true }).click();
    await page.locator('#login-code').fill(await waitForCode(email, before));
    await page.locator('#login-code-verify button[type=submit], #login-code-verify button:not([type])').click();
    await page.waitForURL(/\/(mieter|mitarbeiter)\/faelle/);
}

export async function createCase(page, fields = {}) {
    await page.goto(base + '/mieter/fall');
    const csrf = await page.locator('#intake-form input[name=csrf]').inputValue();
    const form = { csrf, operation: randomUUID(), title: 'Backendprüfung ' + randomUUID(), unit: 'Testweg 2',
        email: 'browser@example.test', description: 'Heizung kalt, bitte prüfen.', ...fields };
    const response = await page.request.post(base + '/mieter/fall', { form, maxRedirects: 0 });
    assert.equal(response.status(), 303, await response.text());
    const tenantUrl = base + response.headers().location;
    await page.goto(tenantUrl);
    const id = (await page.locator('#case-head .eyebrow').innerText()).trim();
    await until(async () => {
        await page.reload();
        return (await page.locator('#case-status').innerText()).trim() === 'Mitarbeiterprüfung erforderlich';
    }, 'Fallaufnahme und manuelle Prüfung ohne KI');
    return { id, tenantUrl, staffUrl: `${base}/mitarbeiter/faelle/${id}`, form };
}

export async function review(page, c) {
    await until(async () => {
        await page.goto(c.staffUrl);
        return (await page.locator('#case-status').innerText()).trim() === 'Mitarbeiterprüfung erforderlich';
    }, 'Camunda-Mitarbeiterprüfung');
    await page.locator('#staff-review-heading').click();
    await page.getByRole('button', { name: 'Bearbeitung fortsetzen', exact: true }).click();
    await until(async () => (await page.locator('#case-status').innerText()).trim() === 'In Bearbeitung', 'Bearbeitungsfreigabe');
}
