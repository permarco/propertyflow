// Run against a disposable frontend-demo instance; creates a local mock case.
// node --test src/test/browser/tenant-navigation.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { chromium } = require('playwright');
const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';

test('UI1: Neuladen und Verlassen verwerfen ungesendeten Text ohne Warnung', { timeout: 30000 }, async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const context = await browser.newContext({ locale: 'de-CH' });
        const page = await context.newPage();
        const dialogs = [];
        page.on('dialog', async dialog => {
            dialogs.push(dialog.type());
            await dialog.accept();
        });
        await page.goto(`${base}/mieter/fall`);
        for (const [id, value] of Object.entries({ title: 'Navigations-Browsertest', unit: 'Testwohnung 3',
            email: 'test@example.ch', description: 'Die Heizung bleibt kalt.' })) {
            await page.locator(`#${id}`).fill(value);
        }
        await page.getByRole('button', { name: 'Anliegen absenden' }).click();
        await page.waitForURL('**/mieter/fall/zugang/*');
        const tenantUrl = page.url();
        const beforeCount = await page.locator('#history article').count();
        const draft = 'Dieser ungesendete Text wird verworfen.';
        // Genuine interaction activates the browser's beforeunload warning mechanism.
        await page.locator('#message').click();
        await page.locator('#message').fill(draft);
        await page.reload();
        assert.equal(await page.locator('#message').inputValue(), '');
        assert.equal(await page.locator('#history article').count(), beforeCount);
        assert.deepEqual(dialogs, [], 'Neuladen darf keine Warnung zeigen');

        await page.locator('#message').click();
        await page.locator('#message').fill(draft);
        await page.getByRole('link', { name: 'Neues Anliegen melden' }).click();
        await page.waitForURL(`${base}/mieter/fall`);
        assert.deepEqual(dialogs, [], 'Verlassen darf keine Warnung zeigen');
        await page.goto(tenantUrl);
        assert.equal(await page.locator('#message').inputValue(), '');
        assert.equal(await page.locator('#history article').count(), beforeCount);
        await page.reload();
        assert.deepEqual(dialogs, [], 'Auch eine leere Eingabe benötigt keine Warnung');
    } finally {
        await browser.close();
    }
});
