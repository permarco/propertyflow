// Run against a disposable frontend-demo instance; creates 51 local mock cases.
// node --test src/test/browser/list-pagination.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { chromium } = require('playwright');
const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';

test('UI2: 51 Treffer auf zwei Seiten; Navigation erhält Suche, Filter und Sortierung', { timeout: 60000 }, async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const context = await browser.newContext({ locale: 'de-CH', viewport: { width: 1440, height: 900 } });
        const page = await context.newPage();
        await page.goto(`${base}/mieter/fall`);
        const csrf = await page.locator('#intake-form input[name=csrf]').inputValue();
        const marker = `Seitentest${randomUUID().replaceAll('-', '')}`;
        const titles = [];
        for (let i = 1; i <= 51; i++) {
            const title = `${marker} Fall ${String(i).padStart(2, '0')}`;
            titles.push(title);
            const response = await context.request.post(`${base}/mieter/fall`, {
                form: { csrf, operation: randomUUID(), title, unit: 'Testwohnung 3',
                    email: 'test@example.ch', description: 'Testfall für die Seitennavigation.' }, maxRedirects: 0,
            });
            assert.equal(response.status(), 303, `Mock-Fall ${i} muss angelegt werden`);
        }
        const params = new URLSearchParams({ q: marker, urgency: 'Noch nicht bewertet', closed: 'true', sort: 'title', desc: 'true' });
        await page.goto(`${base}/mitarbeiter/faelle?${params}`);
        const readRows = () => page.locator('#results tr[data-case]').evaluateAll(rows => rows.map(row => ({
            id: row.dataset.case, title: row.querySelector('td[data-label="Fall"] p').textContent,
        })));
        async function assertSelection(number) {
            assert.equal(await page.locator('#q').inputValue(), marker);
            assert.equal(await page.locator('#urgency').inputValue(), 'Noch nicht bewertet');
            assert.equal(await page.locator('#closed').isChecked(), true);
            assert.equal(await page.locator('#results th').first().getAttribute('aria-sort'), 'descending');
            assert.equal((await page.locator('.pagination span').textContent()).trim(), `Seite ${number} von 2`);
            assert.equal((await page.locator('.result-count').textContent()).trim(), '51 Fälle');
            const current = new URL(page.url()).searchParams;
            for (const [key, value] of params) assert.equal(current.get(key), value, `URL-Auswahl ${key}`);
        }
        await assertSelection(1);
        const firstPage = await readRows();
        assert.equal(firstPage.length, 50);
        const expected = [...titles].reverse();
        assert.deepEqual(firstPage.map(row => row.title), expected.slice(0, 50));
        assert.equal(await page.getByRole('button', { name: '← Rückwärts', exact: true }).isDisabled(), true);
        assert.equal(await page.getByRole('link', { name: 'Vorwärts →', exact: true }).isVisible(), true);
        let navigations = 0;
        page.on('request', request => { if (request.isNavigationRequest()) navigations++; });
        async function go(name, number) {
            const response = page.waitForResponse(response => new URL(response.url()).pathname === '/mitarbeiter/faelle'
                && response.request().resourceType() === 'fetch');
            await page.getByRole('link', { name, exact: true }).click();
            assert.equal((await response).status(), 200);
            await page.waitForFunction(expectedPage => document.querySelector('#results').dataset.page === String(expectedPage)
                && !document.querySelector('#results').hasAttribute('aria-busy'), number);
            await assertSelection(number);
        }
        await go('Vorwärts →', 2);
        const secondPage = await readRows();
        assert.equal(secondPage.length, 1);
        assert.deepEqual(secondPage.map(row => row.title), expected.slice(50));
        assert.equal(new Set([...firstPage, ...secondPage].map(row => row.id)).size, 51, 'Keine fehlenden oder doppelten Fälle');
        assert.equal(await page.getByRole('button', { name: 'Vorwärts →', exact: true }).isDisabled(), true);
        assert.equal(await page.getByRole('link', { name: '← Rückwärts', exact: true }).isVisible(), true);
        await go('← Rückwärts', 1);
        assert.deepEqual(await readRows(), firstPage);
        assert.equal(await page.getByRole('button', { name: '← Rückwärts', exact: true }).isDisabled(), true);
        assert.equal(navigations, 0, 'Seitenwechsel aktualisieren nur die Liste');
    } finally {
        await browser.close();
    }
});
