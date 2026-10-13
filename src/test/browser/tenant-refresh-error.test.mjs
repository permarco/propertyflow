// Run against a disposable frontend-demo instance; creates a local mock case.
// node --test src/test/browser/tenant-refresh-error.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { chromium } = require('playwright');
const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';

test('UI1: Abruffehler erhält Verlauf und Entwurf; nächster Abruf behebt den Fehler', { timeout: 65000 }, async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const context = await browser.newContext({ locale: 'de-CH' });
        const page = await context.newPage();
        await page.goto(`${base}/mieter/fall`);
        for (const [id, value] of Object.entries({ title: 'Abruffehler-Browsertest', unit: 'Testwohnung 3',
            email: 'test@example.ch', description: 'Die Heizung bleibt kalt.' })) {
            await page.locator(`#${id}`).fill(value);
        }
        await page.getByRole('button', { name: 'Anliegen absenden' }).click();
        await page.waitForURL('**/mieter/fall/zugang/*');
        const tenantUrl = page.url();
        const caseId = await page.locator('#case-head .eyebrow').textContent();
        const csrf = await page.locator('#message-form input[name=csrf]').inputValue();
        const draft = 'Diese Ergänzung ist noch nicht gesendet.';
        await page.locator('#message').fill(draft);
        const originalHistory = await page.locator('#history').innerText();
        const originalCount = await page.locator('#history article').count();
        const navigations = [];
        const posts = [];
        page.on('framenavigated', frame => { if (frame === page.mainFrame()) navigations.push(frame.url()); });
        page.on('request', request => { if (request.method() === 'POST') posts.push(request.url()); });
        let polls = 0;
        await context.route(tenantUrl, async route => {
            if (route.request().method() === 'GET' && route.request().resourceType() === 'fetch') {
                polls++;
                if (polls === 1) {
                    await route.abort('internetdisconnected');
                    return;
                }
            }
            await route.continue();
        });

        await page.waitForFunction(() => document.querySelector('#save-state').textContent.includes(
            'Aktualisierung momentan nicht möglich'), undefined, { timeout: 25000 });
        assert.equal(polls, 1);
        assert.equal(await page.locator('#save-state').isVisible(), true);
        assert.equal(await page.locator('#history').isVisible(), true);
        assert.equal(await page.locator('#history').innerText(), originalHistory);
        assert.equal(await page.locator('#message').inputValue(), draft);
        assert.equal(await page.locator('#message').isEnabled(), true);

        const answer = 'Neue Verwaltungsnachricht nach dem Verbindungsfehler.';
        const saved = await context.request.post(`${base}/mitarbeiter/faelle/${caseId}/nachrichten`, {
            form: { csrf, operation: randomUUID(), text: answer }, maxRedirects: 0,
        });
        assert.equal(saved.status(), 303);
        assert.equal(await page.locator('#history article').filter({ hasText: answer }).count(), 0);
        // Let the regular timer recover; do not reload or press the retry button.
        await page.waitForFunction(text => document.querySelector('#history').textContent.includes(text) &&
            document.querySelector('#save-state').textContent.trim() === '', answer, { timeout: 25000 });
        assert.equal(polls, 2);
        assert.equal(await page.locator('#history article').count(), originalCount + 1);
        assert.equal(await page.locator('#history article').filter({ hasText: answer }).count(), 1);
        assert.ok((await page.locator('#history').innerText()).startsWith(originalHistory));
        assert.equal(await page.locator('#message').inputValue(), draft);
        assert.equal(await page.locator('#message').isEnabled(), true);
        assert.equal(await page.locator('#save-state').getAttribute('class').then(value => value?.split(/\s+/).includes('error') ?? false), false);
        assert.equal(page.url(), tenantUrl);
        assert.deepEqual(navigations, []);
        assert.deepEqual(posts, [], 'Ungesendeter Text darf nicht automatisch gespeichert werden');
    } finally {
        await browser.close();
    }
});
