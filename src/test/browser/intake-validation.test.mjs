// UI1 AK-02: run against frontend-demo (default port 8082).
// node --test src/test/browser/intake-validation.test.mjs
import { before, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { chromium } = require('playwright');
const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';
let browser;
before(async () => { browser = await chromium.launch({ channel: 'msedge', headless: true }); });
after(async () => { await browser?.close(); });

const valid = {
    title: 'Heizung funktioniert nicht',
    unit: 'Teststrasse 12, Wohnung 3',
    email: 'mieter@example.ch',
    description: 'Seit gestern bleibt das Wohnzimmer kalt.',
};
const scenarios = [
    ...Object.keys(valid).map(field => ({ field, value: '', validity: 'valueMissing', name: `${field} fehlt` })),
    { field: 'email', value: 'keine-email-adresse', validity: 'typeMismatch', name: 'E-Mail-Adresse ist ungültig' },
];

for (const scenario of scenarios) {
    test(`UI1: ${scenario.name} – Feldhinweis, kein Absenden, Eingaben bleiben erhalten`, async () => {
        const context = await browser.newContext({ locale: 'de-CH' });
        const page = await context.newPage();
        const posts = [];
        // Prevent accidental case creation if validation regresses.
        await context.route('**/mieter/fall', async route => {
            if (route.request().method() === 'POST') {
                posts.push(route.request().url());
                await route.abort();
            } else await route.continue();
        });
        try {
            assert.equal((await page.goto(`${base}/mieter/fall`)).status(), 200);
            const values = { ...valid, [scenario.field]: scenario.value };
            for (const [field, value] of Object.entries(values)) await page.locator(`#${field}`).fill(value);
            await page.locator('#intake-form').evaluate(form => {
                window.intakeValidationEvents = [];
                window.intakeSubmitCount = 0;
                form.addEventListener('invalid', event => window.intakeValidationEvents.push(event.target.id), true);
                form.addEventListener('submit', () => window.intakeSubmitCount++);
            });

            await page.getByRole('button', { name: 'Anliegen absenden' }).click();

            assert.deepEqual(await page.evaluate(() => window.intakeValidationEvents), [scenario.field]);
            assert.equal(await page.evaluate(() => window.intakeSubmitCount), 0);
            assert.deepEqual(posts, []);
            assert.equal(page.url(), `${base}/mieter/fall`);
            const feedback = await page.locator(`#${scenario.field}`).evaluate((field, validity) => ({
                invalid: field.validity[validity],
                message: field.validationMessage,
                focused: document.activeElement === field,
            }), scenario.validity);
            assert.equal(feedback.invalid, true);
            // Native browser validation focuses the field and supplies a localized hint.
            assert.ok(feedback.message.trim(), 'Feldbezogener Browserhinweis fehlt');
            assert.equal(feedback.focused, true);
            for (const [field, value] of Object.entries(values)) {
                assert.equal(await page.locator(`#${field}`).inputValue(), value, `${field}: Eingabe muss erhalten bleiben`);
            }
            assert.equal(await page.getByRole('button', { name: 'Anliegen absenden' }).isEnabled(), true);
            await page.locator(`#${scenario.field}`).fill(valid[scenario.field]);
            assert.equal(await page.locator('#intake-form').evaluate(form => form.checkValidity()), true,
                'Nach Korrektur muss das Formular wieder gültig sein');
        } finally {
            await context.close();
        }
    });
}
