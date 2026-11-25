import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import path from 'node:path';
import { base, createCase, login } from './integrated-helpers.mjs';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { chromium } = require('playwright');
const { default: lighthouse } = await import(pathToFileURL(require.resolve('lighthouse')).href);
const { launch } = await import(pathToFileURL(require.resolve('chrome-launcher')).href);
const chromePath = process.env.CHROME_PATH || chromium.executablePath();
const out = path.resolve('target/frontend-quality');
await mkdir(out, { recursive: true });
const chrome = await launch({ chromePath, chromeFlags: ['--headless', '--disable-gpu', '--no-first-run'] });
const summary = { at: new Date().toISOString(), base, profile: 'desktop, provided throttling, 1440x1000, reset browser cache each run, warm server', runs: [] };
try {
    const browser = await chromium.launch({ executablePath: chromePath, headless: true });
    let sessionCookie;
    let c;
    try {
        const context = await browser.newContext();
        const page = await context.newPage();
        c = await createCase(page);
        let state;
        if (process.env.PROPERTYFLOW_E2E_STAFF_STATE) {
            state = JSON.parse(await readFile(process.env.PROPERTYFLOW_E2E_STAFF_STATE, 'utf8'));
        } else {
            await login(page, process.env.PROPERTYFLOW_E2E_STAFF_EMAIL);
            state = await context.storageState();
        }
        sessionCookie = state.cookies.find(cookie => cookie.name === 'JSESSIONID' && cookie.domain === new URL(base).hostname);
        assert.ok(sessionCookie, 'Staff login must issue a session cookie for the backend host');
    } finally {
        await browser.close();
    }
    const auditBrowser = await chromium.connectOverCDP(`http://127.0.0.1:${chrome.port}`);
    const [auditContext] = auditBrowser.contexts();
    const routes = ['/mieter/fall', new URL(c.tenantUrl).pathname, '/mitarbeiter/faelle', new URL(c.staffUrl).pathname];
    for (const [index, route] of routes.entries()) {
        const staffRoute = route.startsWith('/mitarbeiter/');
        const headers = staffRoute ? { Cookie: `${sessionCookie.name}=${sessionCookie.value}` } : {};
        assert.equal((await fetch(base + route, { headers, redirect: 'manual' })).status, 200); // Template und Server vor der Messung aufwärmen.
        if (staffRoute) await auditContext.addCookies([sessionCookie]);
        for (let run = 1; run <= 3; run++) {
            const result = await lighthouse(base + route, {
                port: chrome.port, output: 'json', logLevel: 'error', onlyCategories: ['performance'],
                formFactor: 'desktop', throttlingMethod: 'provided',
                screenEmulation: { mobile: false, width: 1440, height: 1000, deviceScaleFactor: 1, disabled: false },
            });
            const lhr = result.lhr;
            if (lhr.runtimeError) throw new Error(JSON.stringify(lhr.runtimeError));
            assert.equal(typeof lhr.categories.performance.score, 'number',
                'Unvollständige Lighthouse-Messung: ' + JSON.stringify(lhr.runWarnings));
            assert.equal(new URL(lhr.finalDisplayedUrl).pathname, route, 'Measure the requested page rather than a redirect to login');
            const report = result.report.replaceAll(sessionCookie.value, '[redacted]');
            await writeFile(path.join(out, `lighthouse-${index + 1}-${run}.json`), report);
            const row = { route, run, version: lhr.lighthouseVersion, score: lhr.categories.performance.score * 100,
                fcpMs: lhr.audits['first-contentful-paint'].numericValue,
                lcpMs: lhr.audits['largest-contentful-paint'].numericValue,
                cls: lhr.audits['cumulative-layout-shift'].numericValue,
                tbtMs: lhr.audits['total-blocking-time'].numericValue,
                bytes: lhr.audits['total-byte-weight'].numericValue };
            summary.runs.push(row);
            console.log(JSON.stringify(row));
        }
    }
    for (const route of routes) {
        const scores = summary.runs.filter(r => r.route === route).map(r => r.score).sort((a,b) => a-b);
        assert.ok(scores[1] >= 90, `Median score < 90 for ${route}`);
    }
    summary.passed = true;
} catch (error) {
    summary.failure = error.stack;
    process.exitCode = 1;
} finally {
    await writeFile(path.join(out, 'performance.json'), JSON.stringify(summary, null, 2));
    await chrome.kill();
}
