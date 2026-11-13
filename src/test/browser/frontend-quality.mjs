// Run directly or through frontend-quality.test.mjs against a disposable demo.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { base, chromium, createCase, change, editor, staffStorageState } from './ui-test-helpers.mjs';
import { existingCodeMessages, waitForCode } from './mailpit.mjs';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { default: AxeBuilder } = require('@axe-core/playwright');

export async function runFrontendQuality() {
    const reportDir = path.resolve('target/frontend-quality');
    await mkdir(reportDir, { recursive: true });
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    const report = { at: new Date().toISOString(), base, browser: browser.version(), a11y: [], checks: [] };
    const errors = [];
    async function scan(page, route) {
        const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        report.a11y.push({ route, viewport: page.viewportSize(), violations: result.violations,
            incomplete: result.incomplete.map(x => ({ id: x.id, impact: x.impact })) });
    }
    try {
        const viewport = { width: 1440, height: 1000 };
        const anonymous = await browser.newContext({ viewport });
        const staff = await browser.newContext({ viewport, storageState: await staffStorageState(browser) });
        const publicPage = await anonymous.newPage();
        const page = await staff.newPage();
        for (const current of [publicPage, page]) {
            current.on('pageerror', error => errors.push(error.message));
        }
        const routes = ['/anmelden', '/registrieren', '/mieter/fall', '/mieter/fall/zugang/demo-aktiver-fall',
            '/mitarbeiter/faelle', '/mitarbeiter/faelle/REQ-2026-001'];
        for (const route of routes) {
            const current = route.startsWith('/mitarbeiter/') ? page : publicPage;
            assert.equal((await current.goto(base + route)).status(), 200);
            assert.equal(new URL(current.url()).pathname, route);
            await scan(current, route);
        }

        // Render an actual server validation error, including its correction links.
        await publicPage.goto(base + '/mieter/fall');
        for (const [id, value] of Object.entries({ title: 'A11y-Validierung', unit: 'Wohnung 1', email: 'a@b', description: 'Heizung kalt' })) {
            await publicPage.locator('#' + id).fill(value);
        }
        const rejected = publicPage.waitForResponse(r => r.request().method() === 'POST');
        await publicPage.getByRole('button', { name: /Anliegen absenden/ }).click();
        assert.equal((await rejected).status(), 400);
        await publicPage.locator('#form-error').waitFor();
        await scan(publicPage, 'UI1 with validation error');
        await publicPage.setViewportSize({ width: 390, height: 844 });
        await scan(publicPage, 'UI1 with validation error, narrow viewport');
        await publicPage.setViewportSize(viewport);

        await publicPage.goto(base + '/anmelden');
        await publicPage.locator('#login-email').fill(`unknown-a11y-${randomUUID()}@example.test`);
        await publicPage.getByRole('button', { name: 'Code anfordern', exact: true }).click();
        await publicPage.locator('#login-code').fill('123456');
        await publicPage.getByRole('button', { name: 'Anmelden', exact: true }).click();
        await publicPage.locator('#login-error').waitFor();
        await scan(publicPage, 'Login with invalid code');

        // A private tenant account keeps the suite's shared staff session intact.
        const email = `a11y-${randomUUID()}@example.test`;
        const previous = await existingCodeMessages(email);
        await publicPage.goto(base + '/registrieren');
        await publicPage.locator('#login-email').fill(email);
        await publicPage.getByRole('button', { name: 'Code anfordern', exact: true }).click();
        await publicPage.locator('#login-code').waitFor();
        await scan(publicPage, 'Registration code form');
        await publicPage.locator('#login-code').fill(await waitForCode(email, previous));
        await publicPage.getByRole('button', { name: 'E-Mail bestätigen', exact: true }).click();
        await publicPage.waitForURL(base + '/mieter/faelle');
        await scan(publicPage, 'Tenant account with empty overview');
        const tenantCase = await createCase(publicPage, anonymous, false);
        await publicPage.goto(base + '/mieter/faelle');
        await scan(publicPage, 'Tenant account with case overview');
        await publicPage.goto(`${base}/mieter/faelle/${tenantCase.id}`);
        await scan(publicPage, 'Tenant account case detail');
        await publicPage.setViewportSize({ width: 390, height: 844 });
        await scan(publicPage, 'Tenant account case detail, narrow viewport');

        const c = await createCase(page, staff);
        const assignment = editor(page, '#assigned-unit');
        await assignment.locator('summary').click();
        await scan(page, 'UI3 with open assignment editor');
        await assignment.getByRole('button', { name: 'Abbrechen', exact: true }).click();
        await change(staff, c, 'unit', 'Extern bestätigte A11y-Wohnung');
        await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
        await page.getByRole('dialog', { name: 'Fall aktualisiert', exact: true }).waitFor();
        await scan(page, 'UI3 with update dialog');
        await page.keyboard.press('Escape');

        // Reach controls by actual Tab navigation, not by programmatic focus.
        async function tabTo(selector) {
            for (let i = 0; i < 100; i++) {
                await page.keyboard.press('Tab');
                if (await page.locator(selector).evaluate(el => el === document.activeElement)) return;
            }
            throw new Error('Not keyboard reachable: ' + selector);
        }
        await tabTo('#message');
        await page.keyboard.type('Bitte geben Sie uns eine kurze Rueckmeldung.');
        await tabTo('#generate');
        await page.keyboard.press('Enter');
        await page.waitForFunction(() => document.querySelector('#ai-status').dataset.state === 'streaming');
        await tabTo('#abort');
        await page.keyboard.press('Enter');
        assert.equal(await page.locator('#ai-status').getAttribute('data-state'), 'aborted');
        const stopped = await page.locator('#ai-output').textContent();
        await page.waitForTimeout(1000);
        assert.equal(await page.locator('#ai-output').textContent(), stopped);
        assert.equal(await page.locator('#adopt').isDisabled(), true);
        assert.equal(await page.locator('#ai-status').getAttribute('aria-live'), 'polite');
        report.checks.push('Keyboard: message, generate, abort reachable; output stable after abort; adoption disabled');

        await page.locator('.mock-controls summary').click();
        await page.locator('#mock-mode').selectOption('error');
        await page.locator('#generate').click();
        await page.waitForFunction(() => document.querySelector('#ai-status').dataset.state === 'error');
        assert.equal(await page.locator('#adopt').isDisabled(), true);
        await scan(page, 'UI3 with stream error');
        report.checks.push('Error state visible; incomplete response cannot be adopted');

        const dialogs = [];
        page.on('dialog', async dialog => { dialogs.push(dialog.message()); await dialog.dismiss(); });
        await page.locator('#mock-mode').selectOption('unsafe');
        await page.locator('#generate').click();
        await page.waitForFunction(() => document.querySelector('#ai-status').dataset.state === 'completed');
        const output = page.locator('#ai-output');
        assert.match(await output.textContent(), /<img src=x onerror=alert/);
        assert.match(await output.textContent(), /<script>alert/);
        assert.equal(await output.locator('*').count(), 0);
        assert.equal(dialogs.length, 0);
        assert.equal(await page.locator('#adopt').isEnabled(), true);
        report.checks.push('HTML payload remains literal text; no child elements or alert dialogs; successful retry');
        await scan(page, 'UI3 with completed stream');
        await page.setViewportSize({ width: 390, height: 844 });
        await scan(page, 'UI3 with completed stream, narrow viewport');
        assert.deepEqual(errors, []);
        report.pageErrors = errors;
        await page.screenshot({ path: path.join(reportDir, 'stream-safe-text.png'), fullPage: true });
        const severe = report.a11y.flatMap(r => r.violations).filter(v => ['serious', 'critical'].includes(v.impact));
        report.severeViolations = severe.length;
        assert.equal(severe.length, 0, 'Serious/critical axe violations; see target/frontend-quality/browser.json');
        return report;
    } catch (error) {
        report.failure = error.stack;
        throw error;
    } finally {
        await writeFile(path.join(reportDir, 'browser.json'), JSON.stringify(report, null, 2));
        await browser.close();
    }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try {
        const report = await runFrontendQuality();
        console.log(JSON.stringify({ checks: report.checks, states: report.a11y.length, severeViolations: report.severeViolations }, null, 2));
    } catch (error) {
        console.error(error);
        process.exitCode = 1;
    }
}
