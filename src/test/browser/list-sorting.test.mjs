// Run against a disposable frontend-demo instance with standard mock cases.
// node --test src/test/browser/list-sorting.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { chromium } = require('playwright');
const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';
const columns = ['Fall', 'Objekt / Wohnung', 'Eingang', 'Letzte Aktion des Mieters', 'Wiedervorlage', 'Dringlichkeit', 'Bearbeitungsstatus'];
const urgency = ['Noch nicht bewertet', 'Kritisch', 'Hoch', 'Normal', 'Niedrig'];
function dateValue(text) {
    if (text === '–') return null;
    const match = text.match(/(\d{2})\.(\d{2})\.(\d{4})(?:.*?(\d{2}):(\d{2}))?/);
    assert.ok(match, `Unerwartetes Datumsformat: ${text}`);
    return Number(`${match[3]}${match[2]}${match[1]}${match[4] || '00'}${match[5] || '00'}`);
}

test('UI2: alle sieben Spalten sortieren in beide Richtungen und erhalten die Auswahl', { timeout: 30000 }, async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const context = await browser.newContext({ locale: 'de-CH', viewport: { width: 1440, height: 900 } });
        const page = await context.newPage();
        // Distinct follow-up dates ensure that reversing dates is actually exercised.
        for (const [id, value] of [['REQ-2026-002', '2030-01-10'], ['REQ-2026-003', '2030-02-20']]) {
            await page.goto(`${base}/mitarbeiter/faelle/${id}`);
            const response = await context.request.post(`${base}/mitarbeiter/faelle/${id}/aktion`, {
                form: { csrf: await page.locator('#message-form input[name=csrf]').inputValue(),
                    version: await page.locator('#case-head').getAttribute('data-version'),
                    operation: randomUUID(), action: 'followUp', value }, maxRedirects: 0,
            });
            assert.equal(response.status(), 303);
        }
        await page.goto(`${base}/mitarbeiter/faelle?q=REQ202600&closed=true`);
        const readRows = () => page.locator('#results tr[data-case]').evaluateAll(rows => rows.map(row => ({
            id: row.dataset.case,
            values: [...row.querySelectorAll('td')].map((td, index) =>
                (index === 0 ? td.querySelector('p') : index === 1 ? td.querySelector('span') : td).textContent.trim()),
        })));
        const original = await readRows();
        assert.equal(original.length, 9, 'Die neun Mock-Fälle müssen auf einer Seite liegen');
        let navigations = 0;
        page.on('request', request => { if (request.isNavigationRequest()) navigations++; });
        async function update(action) {
            const response = page.waitForResponse(response => new URL(response.url()).pathname === '/mitarbeiter/faelle'
                && response.request().resourceType() === 'fetch');
            await action();
            assert.equal((await response).status(), 200);
            await page.waitForFunction(() => !document.querySelector('#results').hasAttribute('aria-busy') &&
                document.querySelector('#load-state').textContent.startsWith('Zuletzt aktualisiert'));
        }
        for (const [index, column] of columns.entries()) {
            function key(row) {
                const value = row.values[index];
                return index >= 2 && index <= 4 ? dateValue(value) : index === 5 ? urgency.indexOf(value)
                    : index === 6 ? value : value.toLowerCase();
            }
            assert.ok(new Set(original.map(key)).size > 1, `${column}: verschiedene Testwerte erforderlich`);
            for (const descending of [false, true]) {
                await update(() => page.locator('#results th a').nth(index).click());
                const expected = [...original].sort((a, b) => {
                    const x = key(a), y = key(b);
                    if (x === null && y !== null) return 1;
                    if (y === null && x !== null) return -1;
                    const order = x < y ? -1 : x > y ? 1 : 0;
                    return order ? order * (descending ? -1 : 1) : a.id.localeCompare(b.id);
                });
                assert.deepEqual((await readRows()).map(row => row.id), expected.map(row => row.id),
                    `${column}: ${descending ? 'absteigend' : 'aufsteigend'}`);
                for (let i = 0; i < columns.length; i++) {
                    const th = page.locator('#results th').nth(i);
                    assert.equal(await th.getAttribute('aria-sort'), i === index ? (descending ? 'descending' : 'ascending') : 'none');
                    assert.equal((await th.textContent()).trim(), columns[i] + (i === index ? (descending ? ' ↓' : ' ↑') : ''));
                }
                assert.equal(await page.locator('#q').inputValue(), 'REQ202600');
                assert.equal(await page.locator('#closed').isChecked(), true);
                assert.equal(await page.locator('#urgency').inputValue(), '');
            }
        }
        // A restrictive urgency selection must also survive every column change.
        await update(() => page.locator('#urgency').selectOption('Hoch'));
        const filteredIds = (await readRows()).map(row => row.id).sort();
        assert.ok(filteredIds.length > 1);
        for (let index = 0; index < columns.length; index++) {
            await update(() => page.locator('#results th a').nth(index).click());
            assert.equal(await page.locator('#q').inputValue(), 'REQ202600');
            assert.equal(await page.locator('#closed').isChecked(), true);
            assert.equal(await page.locator('#urgency').inputValue(), 'Hoch');
            assert.deepEqual((await readRows()).map(row => row.id).sort(), filteredIds);
            assert.ok((await readRows()).every(row => row.values[5] === 'Hoch'));
        }
        assert.equal(navigations, 0, 'Sortieren benötigt kein vollständiges Neuladen');
    } finally {
        await browser.close();
    }
});
