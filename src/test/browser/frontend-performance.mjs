import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

const require = createRequire(path.resolve('target/frontend-tools/package.json'));
const { default: lighthouse } = await import(pathToFileURL(require.resolve('lighthouse')).href);
const { launch } = await import(pathToFileURL(require.resolve('chrome-launcher')).href);
const base = process.env.DEMO_URL || 'http://127.0.0.1:8082';
const chromePath = process.env.CHROME_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const out = path.resolve('target/frontend-quality');
await mkdir(out, { recursive: true });
const chrome = await launch({ chromePath, chromeFlags: ['--headless', '--disable-gpu', '--no-first-run'] });
const summary = { at: new Date().toISOString(), base, profile: 'desktop, provided throttling (no artificial CPU/network slowdown), 1440x1000, fresh storage each run, warm server', runs: [] };
try {
    const routes = ['/mieter/fall', '/mieter/fall/zugang/demo-aktiver-fall', '/mitarbeiter/faelle', '/mitarbeiter/faelle/REQ-2026-001'];
    for (const [index, route] of routes.entries()) {
        assert.equal((await fetch(base + route)).status, 200); // warm template/server
        for (let run = 1; run <= 3; run++) {
            const result = await lighthouse(base + route, {
                port: chrome.port, output: 'json', logLevel: 'error', onlyCategories: ['performance'],
                formFactor: 'desktop', throttlingMethod: 'provided',
                screenEmulation: { mobile: false, width: 1440, height: 1000, deviceScaleFactor: 1, disabled: false },
            });
            const lhr = result.lhr;
            await writeFile(path.join(out, `lighthouse-${index + 1}-${run}.json`), result.report);
            if (lhr.runtimeError) throw new Error(JSON.stringify(lhr.runtimeError));
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
