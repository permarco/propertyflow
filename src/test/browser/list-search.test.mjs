// Run against a fresh frontend-demo instance with standard mock cases.
// node --test src/test/browser/list-search.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { chromium } = require('playwright');
const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';

test('UI2: Live-Suche, Filterkombination, leere Treffer und vollständiges Zurücksetzen', { timeout: 30000 }, async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ locale: 'de-CH', viewport: { width: 1440, height: 900 } });
        assert.equal((await page.goto(`${base}/mitarbeiter/faelle`)).status(), 200);
        const rows = () => page.locator('#results tr[data-case]').evaluateAll(entries => entries.map(row => ({
            id: row.dataset.case,
            urgency: row.querySelector('[data-label="Dringlichkeit"]').textContent.trim(),
            status: row.querySelector('[data-label="Bearbeitungsstatus"]').textContent.trim(),
        })));
        const initialRows = await rows();
        const initialCount = await page.locator('.result-count').textContent();
        let navigations = 0;
        const requests = [];
        page.on('request', request => {
            if (request.isNavigationRequest()) navigations++;
            if (request.resourceType() === 'fetch' && new URL(request.url()).pathname === '/mitarbeiter/faelle') {
                requests.push({ at: Date.now(), query: new URL(request.url()).searchParams.get('q') });
            }
        });
        async function update(action) {
            const response = page.waitForResponse(response => new URL(response.url()).pathname === '/mitarbeiter/faelle'
                && response.request().resourceType() === 'fetch');
            await action();
            assert.equal((await response).status(), 200);
            await page.waitForFunction(() => !document.querySelector('#results').hasAttribute('aria-busy') &&
                document.querySelector('#load-state').textContent.startsWith('Zuletzt aktualisiert'));
        }
        const started = Date.now();
        await update(() => page.locator('#q').fill('heiz'));
        assert.equal(requests.length, 1);
        assert.ok(requests[0].at - started >= 950, 'Suche muss etwa eine Sekunde Eingabepause abwarten');
        assert.equal(requests[0].query, 'heiz');
        const lowerCaseRows = await rows();
        assert.ok(lowerCaseRows.length > 0 && lowerCaseRows.length < initialRows.length);
        assert.ok(lowerCaseRows.some(row => row.id === 'REQ-2026-001'));
        // Full-text search also matches descriptions and messages, not only titles.
        for (const row of lowerCaseRows) {
            const detail = await page.request.get(`${base}/mitarbeiter/faelle/${row.id}`);
            assert.equal(detail.status(), 200);
            assert.ok((await detail.text()).toLowerCase().includes('heiz'), `Suchbegriff fehlt in ${row.id}`);
        }
        await update(() => page.locator('#q').fill('HEIZ'));
        assert.deepEqual(await rows(), lowerCaseRows, 'Gross- und Kleinschreibung liefern dieselben Fälle');

        await update(() => page.locator('#urgency').selectOption('Hoch'));
        assert.deepEqual(await rows(), lowerCaseRows.filter(row => row.urgency === 'Hoch'));
        assert.equal(await page.locator('#q').inputValue(), 'HEIZ');
        await update(() => page.locator('#q').fill(''));
        const highRows = await rows();
        assert.ok(highRows.length > lowerCaseRows.filter(row => row.urgency === 'Hoch').length);
        assert.ok(highRows.every(row => row.urgency === 'Hoch' && row.status !== 'Abgeschlossen'));

        await update(() => page.locator('#q').fill('KeinTrefferFuerDiesenSuchbegriff'));
        assert.deepEqual(await rows(), []);
        assert.equal((await page.locator('#results .empty h2').textContent()).trim(), 'Keine Fälle für diese Auswahl.');
        assert.equal((await page.locator('.result-count').textContent()).trim(), '0 Fälle');
        await update(() => page.locator('#closed').check());
        assert.equal(await page.locator('#closed').isChecked(), true);
        assert.equal(await page.locator('#urgency').inputValue(), 'Hoch');
        assert.notEqual(await page.locator('#q').inputValue(), '');
        await update(() => page.locator('#reset').click());
        assert.equal(await page.locator('#q').inputValue(), '');
        assert.equal(await page.locator('#urgency').inputValue(), '');
        assert.equal(await page.locator('#closed').isChecked(), false);
        assert.deepEqual(await rows(), initialRows);
        assert.equal(await page.locator('.result-count').textContent(), initialCount);
        assert.equal(await page.locator('#results .empty').count(), 0);
        assert.equal(navigations, 0, 'Suche und Zurücksetzen erfolgen ohne vollständiges Neuladen');
    } finally {
        await browser.close();
    }
});
