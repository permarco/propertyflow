// Run against a disposable frontend-demo instance; creates local mock cases.
// node --test src/test/browser/tenant-message.test.mjs
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

async function openNewCase(page) {
    assert.equal((await page.goto(`${base}/mieter/fall`)).status(), 200);
    for (const [id, value] of Object.entries({ title: 'Nachrichten-Browsertest', unit: 'Testwohnung 3',
        email: 'test@example.ch', description: 'Die Heizung bleibt kalt.' })) {
        await page.locator(`#${id}`).fill(value);
    }
    await page.getByRole('button', { name: 'Anliegen absenden' }).click();
    await page.waitForURL('**/mieter/fall/zugang/*');
    await page.locator('#message').waitFor();
}

for (const failFirst of [false, true]) {
    test(failFirst ? 'UI1: Fehler erhält Text und erlaubt erneutes Senden' :
        'UI1: Speichern sperrt Eingabe und zeigt die Nachricht genau einmal', { timeout: 30000 }, async () => {
        const context = await browser.newContext({ locale: 'de-CH' });
        const page = await context.newPage();
        let release;
        const gate = new Promise(resolve => { release = resolve; });
        const requests = [];
        try {
            await openNewCase(page);
            const caseUrl = page.url();
            const text = 'Auch im Schlafzimmer bleibt der Heizkörper kalt.';
            const notice = 'Speichern momentan nicht möglich. Bitte erneut versuchen.';
            const beforeCount = await page.locator('#history article').count();
            await context.route('**/mieter/fall/zugang/*/nachrichten', async route => {
                requests.push(new URLSearchParams(route.request().postData()));
                if (requests.length === 1) {
                    await gate;
                    if (failFirst) {
                        await route.fulfill({ status: 503, contentType: 'text/html',
                            body: `<div role="alert">${notice}</div>` });
                        return;
                    }
                }
                await route.continue();
            });
            await page.locator('#message').fill(text);
            await page.locator('#send').click();
            await page.waitForFunction(() => document.querySelector('#save-state').textContent === 'Wird gespeichert …');
            assert.equal(await page.locator('#message').isDisabled(), true);
            assert.equal(await page.locator('#send').isDisabled(), true);
            assert.equal(await page.locator('#message').inputValue(), text);
            assert.equal(await page.locator('#history article').count(), beforeCount);
            release();

            if (failFirst) {
                await page.waitForFunction(expected => document.querySelector('#save-state').textContent === expected, notice);
                await page.waitForFunction(() => !document.querySelector('#send').disabled);
                assert.equal(await page.locator('#save-state').isVisible(), true);
                assert.equal(await page.locator('#message').isEnabled(), true);
                assert.equal(await page.locator('#message').inputValue(), text);
                assert.equal(await page.locator('#history article').count(), beforeCount);
                assert.equal(requests.length, 1);
                await page.locator('#send').click();
            }

            await page.waitForFunction(() => document.querySelector('#message').value === '' &&
                !document.querySelector('#message').disabled);
            assert.equal(page.url(), caseUrl);
            assert.equal(await page.locator('#history article').count(), beforeCount + 1);
            assert.equal(await page.locator('#history article').filter({ hasText: text }).count(), 1);
            assert.equal(await page.locator('#send').isDisabled(), true, 'Leere Eingabe darf nicht erneut gesendet werden');
            assert.equal(requests.length, failFirst ? 2 : 1);
            assert.ok(requests.every(request => request.get('text') === text));
            if (failFirst) assert.equal(requests[0].get('operation'), requests[1].get('operation'),
                'Wiederholung muss dieselbe Absende-ID verwenden');
            // Confirm the message was saved in the mock, not only appended in the browser.
            await page.reload();
            assert.equal(await page.locator('#history article').filter({ hasText: text }).count(), 1);
        } finally {
            release();
            await context.close();
        }
    });
}

test('UI1: Zu lange Nachricht bleibt korrigierbar, genau 2000 Codeeinheiten werden gespeichert', async () => {
    const context = await browser.newContext({ locale: 'de-CH' });
    try {
        const page = await context.newPage();
        await openNewCase(page);
        const field = page.locator('#message');
        const hint = page.locator('#message-length-hint');
        const originalCount = await page.locator('#history article').count();
        const text = 'Weitere Beobachtung:\n' + 'x'.repeat(1978) + '🚿';
        assert.equal(text.length, 2001);
        const requests = [];
        await context.route('**/mieter/fall/zugang/*/nachrichten', async route => {
            requests.push(new URLSearchParams(route.request().postData()).get('text'));
            await route.continue();
        });

        assert.equal(await field.getAttribute('aria-describedby'), 'message-length-hint');
        await field.focus();
        await page.keyboard.insertText(text);
        assert.equal(await field.inputValue(), text);
        assert.equal(await hint.isVisible(), true);
        assert.equal(await hint.textContent(), 'Die Nachricht darf höchstens 2’000 Zeichen enthalten.');
        assert.equal(await field.getAttribute('aria-invalid'), 'true');
        await page.locator('#send').click();
        assert.equal(await field.evaluate(element => element.validity.customError), true);
        assert.equal(await field.inputValue(), text);
        assert.equal(await page.locator('#history article').count(), originalCount);
        assert.deepEqual(requests, []);

        const corrected = text.replace('xx', 'x');
        assert.equal(corrected.length, 2000);
        await field.fill(corrected);
        assert.equal(await field.getAttribute('aria-invalid'), null);
        await page.locator('#send').click();
        await page.waitForFunction(() => document.querySelector('#message').value === '');
        assert.deepEqual(requests.map(value => value.replace(/\r\n/g, '\n')), [corrected]);
        assert.equal(await page.locator('#history article').count(), originalCount + 1);
        assert.equal(await page.locator('#history article').last().locator('p').first().textContent(), corrected);
    } finally {
        await context.close();
    }
});

test('UI1 ohne JavaScript: Server weist zu lange Nachricht ab und rendert den vollständigen Entwurf', async () => {
    const context = await browser.newContext({ locale: 'de-CH', javaScriptEnabled: false });
    try {
        const page = await context.newPage();
        await openNewCase(page);
        const originalCount = await page.locator('#history article').count();
        const text = '\n' + 'x'.repeat(1998) + '🚿';
        const field = page.locator('#message');
        await field.fill(text);

        const pendingResponse = page.waitForResponse(response => response.request().method() === 'POST'
            && response.url().endsWith('/nachrichten'));
        await page.locator('#send').click();
        assert.equal((await pendingResponse).status(), 400);
        assert.equal(await page.locator('#save-state').textContent(), 'Die Nachricht darf höchstens 2’000 Zeichen enthalten.');
        assert.equal(await field.inputValue(), text);
        assert.equal(await page.locator('#history article').count(), originalCount);
    } finally {
        await context.close();
    }
});
