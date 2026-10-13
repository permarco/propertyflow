// Run against a disposable frontend-demo instance; creates a local mock case.
// node --test src/test/browser/tenant-refresh.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';
import path from 'node:path';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { chromium } = require('playwright');
const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';

test('UI1: automatische Aktualisierung erhält Entwurf und Leseposition', { timeout: 65000 }, async () => {
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const context = await browser.newContext({ locale: 'de-CH', viewport: { width: 1440, height: 900 } });
        const page = await context.newPage();
        await page.goto(`${base}/mieter/fall`);
        for (const [id, value] of Object.entries({ title: 'Aktualisierungs-Browsertest', unit: 'Testwohnung 3',
            email: 'test@example.ch', description: 'Die Heizung bleibt kalt.' })) {
            await page.locator(`#${id}`).fill(value);
        }
        await page.getByRole('button', { name: 'Anliegen absenden' }).click();
        await page.waitForURL('**/mieter/fall/zugang/*');
        const tenantUrl = page.url();
        const caseId = await page.locator('#case-head .eyebrow').textContent();
        await page.goto(`${base}/mitarbeiter/faelle/${caseId}`);
        const csrf = await page.locator('#message-form input[name=csrf]').inputValue();
        async function publish(text) {
            const response = await context.request.post(`${base}/mitarbeiter/faelle/${caseId}/nachrichten`, {
                form: { csrf, operation: randomUUID(), text }, maxRedirects: 0,
            });
            assert.equal(response.status(), 303, 'Verwaltungsnachricht muss im Mock gespeichert sein');
        }
        // Enough real saved messages to make reading older entries a meaningful scroll test.
        for (let i = 1; i <= 12; i++) await publish(`Bisherige Rückmeldung ${i}: Wir prüfen das Anliegen und informieren Sie über die nächsten Schritte.`);
        await page.goto(tenantUrl);
        const draft = 'Meine noch nicht gesendete Ergänzung zur Heizung.';
        await page.locator('#message').fill(draft);
        const position = await page.locator('#history').evaluate(history => {
            history.scrollTop = 100;
            return { top: history.scrollTop, remaining: history.scrollHeight - history.clientHeight - history.scrollTop };
        });
        assert.ok(position.top > 0 && position.remaining > 200, 'Der Verlauf muss oberhalb seines Endes scrollbar sein');
        const beforeCount = await page.locator('#history article').count();
        const navigations = [];
        page.on('framenavigated', frame => { if (frame === page.mainFrame()) navigations.push(frame.url()); });
        const tenantPosts = [];
        page.on('request', request => { if (request.method() === 'POST') tenantPosts.push(request.url()); });
        const answer = 'Neue Verwaltungsnachricht: Wir haben Ihre Rückmeldung erhalten.';
        const nextPoll = page.waitForResponse(response => response.url() === tenantUrl &&
            response.request().resourceType() === 'fetch', { timeout: 25000 });
        await publish(answer);
        assert.equal(await page.locator('#history article').filter({ hasText: answer }).count(), 0);
        assert.equal((await nextPoll).status(), 200);
        await page.waitForFunction(text => [...document.querySelectorAll('#history article')].some(entry => entry.textContent.includes(text)), answer);

        async function assertPreserved() {
            assert.equal(await page.locator('#history article').filter({ hasText: answer }).count(), 1);
            assert.equal(await page.locator('#history article').count(), beforeCount + 1);
            assert.equal(await page.locator('#message').inputValue(), draft);
            assert.equal(await page.locator('#message').isEnabled(), true);
            assert.equal(await page.locator('#message').evaluate(el => document.activeElement === el), true);
            const top = await page.locator('#history').evaluate(el => el.scrollTop);
            assert.ok(Math.abs(top - position.top) <= 1, `Leseposition verändert: ${position.top} → ${top}`);
            assert.equal(page.url(), tenantUrl);
            assert.deepEqual(navigations, [], 'Kein vollständiges Neuladen');
            assert.deepEqual(tenantPosts, [], 'Der Entwurf darf nicht automatisch gesendet werden');
        }
        await assertPreserved();
        // A second normal 20-second poll must not duplicate the new message.
        const repeated = await page.waitForResponse(response => response.url() === tenantUrl &&
            response.request().resourceType() === 'fetch', { timeout: 25000 });
        await repeated.finished();
        await assertPreserved();
    } finally {
        await browser.close();
    }
});
