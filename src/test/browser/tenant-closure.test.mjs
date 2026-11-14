import { launchBrowser } from './browser-launcher.mjs';
// Run against a disposable frontend-demo instance; creates a local mock case.
// node --test src/test/browser/tenant-closure.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { staffStorageState } from './staff-test-login.mjs';

const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';

test('UI1: Abschluss verwirft den Entwurf; Wiedereröffnung zeigt eine leere Eingabe', { timeout: 65000 }, async () => {
    const browser = await launchBrowser();
    try {
        const staff = await browser.newContext({ storageState: await staffStorageState(browser) });
        const context = await browser.newContext({ locale: 'de-CH' });
        const page = await context.newPage();
        await page.goto(`${base}/mieter/fall`);
        for (const [id, value] of Object.entries({ title: 'Abschluss-Browsertest', unit: 'Testwohnung 3',
            email: 'test@example.ch', description: 'Die Heizung bleibt kalt.' })) {
            await page.locator(`#${id}`).fill(value);
        }
        await page.getByRole('button', { name: 'Anliegen absenden' }).click();
        await page.waitForURL('**/mieter/fall/zugang/*');
        const tenantUrl = page.url();
        const caseId = await page.locator('#case-head .eyebrow').textContent();
        const staffUrl = `${base}/mitarbeiter/faelle/${caseId}`;
        const staffPage = await staff.newPage();
        await staffPage.goto(staffUrl);
        const csrf = await staffPage.locator('#message-form input[name=csrf]').inputValue();
        const originalEntryIds = await page.locator('#history article').evaluateAll(entries => entries.map(e => e.dataset.entry));
        async function change(action) {
            const response = await staff.request.post(`${staffUrl}/aktion`, {
                form: { csrf, action, operation: randomUUID(),
                    version: await page.locator('#case-head').getAttribute('data-version') }, maxRedirects: 0,
            });
            assert.equal(response.status(), 303);
        }
        const navigations = [];
        page.on('framenavigated', frame => { if (frame === page.mainFrame()) navigations.push(frame.url()); });
        const draft = 'Diese ungesendete Ergänzung soll beim Abschluss verschwinden.';
        await page.locator('#message').fill(draft);
        await change('close');
        await page.waitForFunction(() => document.querySelector('#case-head').dataset.closed === 'true', { }, { timeout: 25000 });
        assert.equal((await page.locator('#case-status').textContent()).trim(), 'Abgeschlossen');
        assert.equal(await page.locator('#message').inputValue(), '');
        assert.equal(await page.locator('#composer-fields').isHidden(), true);
        assert.equal(await page.locator('#message').isDisabled(), true);
        assert.equal(await page.locator('#send').isDisabled(), true);
        assert.equal(await page.locator('#closed-notice').isVisible(), true);
        assert.equal(await page.locator('#history article').filter({ hasText: draft }).count(), 0);

        await change('reopen');
        await page.waitForFunction(() => document.querySelector('#case-head').dataset.closed === 'false', { }, { timeout: 25000 });
        assert.equal(await page.locator('#composer-fields').isVisible(), true);
        assert.equal(await page.locator('#message').isEnabled(), true);
        assert.equal(await page.locator('#message').inputValue(), '');
        assert.equal(await page.locator('#send').isDisabled(), true, 'Leeres Feld bleibt nicht sendbar');
        assert.equal(await page.locator('#closed-notice').isHidden(), true);
        assert.equal(await page.locator('#case-head .eyebrow').textContent(), caseId);
        const entryIds = await page.locator('#history article').evaluateAll(entries => entries.map(e => e.dataset.entry));
        assert.ok(originalEntryIds.every(id => entryIds.includes(id)), 'Der gespeicherte Verlauf bleibt erhalten');
        assert.equal(page.url(), tenantUrl);
        assert.deepEqual(navigations, [], 'Abschluss und Wiedereröffnung werden ohne Neuladen erkannt');
        await page.locator('#message').fill('Neue Nachricht nach Wiedereröffnung');
        assert.equal(await page.locator('#send').isEnabled(), true);
    } finally {
        await browser.close();
    }
});
