// Run against a fresh frontend-demo instance with the standard mock cases.
// node --test src/test/browser/list-filters.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { chromium } = require('playwright');
const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';

test('UI2: Dringlichkeit, abgeschlossene Fälle und Filter zurücksetzen', { timeout: 30000 }, async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ locale: 'de-CH', viewport: { width: 1440, height: 900 } });
        assert.equal((await page.goto(`${base}/mitarbeiter/faelle`)).status(), 200);
        async function rows() {
            return page.locator('#results tr[data-case]').evaluateAll(entries => entries.map(row => ({
                id: row.dataset.case,
                urgency: row.querySelector('[data-label="Dringlichkeit"]').textContent.trim(),
                status: row.querySelector('[data-label="Bearbeitungsstatus"]').textContent.trim(),
            })));
        }
        const initialRows = await rows();
        const initialCount = await page.locator('.result-count').textContent();
        assert.ok(initialRows.length > 0);
        assert.ok(initialRows.every(row => row.status !== 'Abgeschlossen'));
        assert.ok(initialRows.some(row => row.urgency !== 'Hoch'), 'Mock muss verschiedene Dringlichkeiten enthalten');
        let documentRequests = 0;
        page.on('request', request => { if (request.isNavigationRequest()) documentRequests++; });
        async function filter(action) {
            const response = page.waitForResponse(response => new URL(response.url()).pathname === '/mitarbeiter/faelle'
                && response.request().resourceType() === 'fetch');
            await action();
            assert.equal((await response).status(), 200);
            await page.waitForFunction(() => !document.querySelector('#results').hasAttribute('aria-busy') &&
                document.querySelector('#load-state').textContent.startsWith('Zuletzt aktualisiert'));
        }
        await filter(() => page.locator('#urgency').selectOption('Hoch'));
        const activeHigh = await rows();
        assert.ok(activeHigh.length > 0);
        assert.ok(activeHigh.every(row => row.urgency === 'Hoch' && row.status !== 'Abgeschlossen'));
        assert.ok(activeHigh.some(row => row.id === 'REQ-2026-001'));

        await filter(() => page.locator('#closed').check());
        const allHigh = await rows();
        assert.ok(allHigh.every(row => row.urgency === 'Hoch'));
        assert.ok(allHigh.some(row => row.status === 'Abgeschlossen'), 'Passende abgeschlossene Mock-Fälle müssen hinzukommen');
        assert.ok(activeHigh.every(row => allHigh.some(candidate => candidate.id === row.id)), 'Aktive Fälle bleiben sichtbar');
        assert.ok(allHigh.length > activeHigh.length);
        assert.equal(await page.locator('#urgency').inputValue(), 'Hoch');

        await filter(() => page.locator('#reset').click());
        assert.equal(await page.locator('#urgency').inputValue(), '');
        assert.equal(await page.locator('#closed').isChecked(), false);
        assert.deepEqual(await rows(), initialRows);
        assert.equal(await page.locator('.result-count').textContent(), initialCount);
        assert.equal(documentRequests, 0, 'Filter müssen die Liste ohne vollständiges Neuladen aktualisieren');
    } finally {
        await browser.close();
    }
});
