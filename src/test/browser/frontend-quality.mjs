// Run against a disposable frontend-demo instance; writes only local test reports.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { chromium } = require('playwright');
const { default: AxeBuilder } = require('@axe-core/playwright');
const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';
const reportDir = path.resolve('target/frontend-quality');
await mkdir(reportDir, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const report = { at: new Date().toISOString(), base, browser: browser.version(), a11y: [], checks: [] };
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const errors = [];
page.on('pageerror', error => errors.push(error.message));
try {
    for (const route of ['/mieter/fall', '/mieter/fall/zugang/demo-aktiver-fall', '/mitarbeiter/faelle', '/mitarbeiter/faelle/REQ-2026-001']) {
        assert.equal((await page.goto(base + route)).status(), 200);
        const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        report.a11y.push({ route, violations: result.violations, incomplete: result.incomplete.map(x => ({ id: x.id, impact: x.impact })) });
    }
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
    report.checks.push('Keyboard: message, generate, abort reachable; output stable 1000 ms after abort; adoption disabled');

    await page.locator('.mock-controls summary').click();
    await page.locator('#mock-mode').selectOption('error');
    await page.locator('#generate').click();
    await page.waitForFunction(() => document.querySelector('#ai-status').dataset.state === 'error');
    assert.equal(await page.locator('#adopt').isDisabled(), true);
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
    report.checks.push('HTML payload remains literal text; 0 child elements and 0 alert dialogs; successful retry');
    const streamed = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    report.a11y.push({ route: 'UI3 with completed stream', violations: streamed.violations, incomplete: streamed.incomplete.map(x => ({ id: x.id, impact: x.impact })) });
    assert.deepEqual(errors, []);
    report.pageErrors = errors;
    await page.screenshot({ path: path.join(reportDir, 'stream-safe-text.png'), fullPage: true });
    const severe = report.a11y.flatMap(r => r.violations).filter(v => ['serious', 'critical'].includes(v.impact));
    report.severeViolations = severe.length;
    assert.equal(severe.length, 0, 'Serious/critical axe violations');
} catch (error) {
    report.failure = error.stack;
    process.exitCode = 1;
} finally {
    await writeFile(path.join(reportDir, 'browser.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify({ checks: report.checks, a11y: report.a11y.map(r => ({ route: r.route, violations: r.violations.map(v => ({ id: v.id, impact: v.impact, targets: v.nodes.map(n => n.target) })) })), failure: report.failure }, null, 2));
    await browser.close();
}
