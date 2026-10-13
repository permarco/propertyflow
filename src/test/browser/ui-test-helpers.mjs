import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { randomUUID } from 'node:crypto';
import { setTimeout as pause } from 'node:timers/promises';
import path from 'node:path';
const require = createRequire(path.resolve('target/frontend-tools/package.json'));
export const { chromium } = require('playwright');
export const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';
export async function until(predicate, message = 'UI did not reach expected state') {
    const end = Date.now() + 5000;
    while (Date.now() < end) { if (await predicate()) return; await pause(20); }
    assert.fail(message);
}
export async function fixture(t, options = {}) {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    t.after(() => browser.close());
    const context = await browser.newContext({ locale: 'de-CH', viewport: { width: 1440, height: 900 }, ...options });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    t.after(() => assert.deepEqual(errors, [], 'Browser runtime errors'));
    return { browser, context, page };
}
export async function freeze(page) {
    const time = new Date('2030-01-01T12:00:00Z');
    await page.clock.install({ time });
    await page.clock.pauseAt(time);
}
export async function createCase(page, context, staff = true) {
    await page.goto(`${base}/mieter/fall`);
    const csrf = await page.locator('#intake-form input[name=csrf]').inputValue();
    const response = await context.request.post(`${base}/mieter/fall`, {
        form: { csrf, operation: randomUUID(), title: `UI-Test ${randomUUID()}`, unit: 'Originalwohnung 3',
            email: 'test@example.ch', description: 'Ursprüngliche Beschreibung.' }, maxRedirects: 0,
    });
    assert.equal(response.status(), 303);
    const tenantUrl = base + response.headers().location;
    await page.goto(tenantUrl);
    const id = await page.locator('#case-head .eyebrow').textContent();
    const staffUrl = `${base}/mitarbeiter/faelle/${id}`;
    if (staff) await page.goto(staffUrl);
    return { id, csrf, tenantUrl, staffUrl };
}
export async function change(context, c, action, value = '', reason = '') {
    const html = await (await context.request.get(c.staffUrl)).text();
    const version = html.match(/data-version="(\d+)"/)[1];
    const response = await context.request.post(c.staffUrl + '/aktion', {
        form: { csrf: c.csrf, operation: randomUUID(), version, action, value, reason }, maxRedirects: 0,
    });
    assert.equal(response.status(), 303, await response.text());
}
export async function publish(context, c, text, internal = false, tenant = false) {
    const response = await context.request.post((tenant ? c.tenantUrl : c.staffUrl) + '/nachrichten', {
        form: { csrf: c.csrf, operation: randomUUID(), text, internal: String(internal) }, maxRedirects: 0,
    });
    assert.equal(response.status(), 303);
}
export async function submit(page, button) {
    const response = page.waitForResponse(r => r.request().method() === 'POST');
    await button.click();
    const result = await response;
    await until(async () => !(await page.locator('#save-state').textContent()).includes('Wird gespeichert'));
    return result.status();
}
export function editor(page, field) { return page.locator('#case-sidebar details').filter({ has: page.locator(field) }); }
