// Start a disposable frontend-demo instance first; DEMO_URL defaults to port 8082.
// node src/test/browser/run-ui-tests.mjs
import { readdir, mkdir, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { chromium } from './ui-test-helpers.mjs';
import { staffStorageState } from './staff-test-login.mjs';

const directory = path.dirname(fileURLToPath(import.meta.url));
const selected = process.argv.slice(2);
const available = (await readdir(directory)).filter(name => name.endsWith('.test.mjs'));
for (const name of selected) {
    if (!available.includes(name)) throw new Error(`Unknown browser test file: ${name}`);
}
const files = (selected.length ? selected : available).sort()
    .map(name => path.join(directory, name));
const browser = await chromium.launch({ channel: 'msedge', headless: true });
let storageState;
try {
    storageState = await staffStorageState(browser);
} finally {
    await browser.close();
}
const child = spawn(process.execPath, ['--test', '--test-concurrency=1', '--test-reporter=tap', ...files], {
    env: { ...process.env, DEMO_STAFF_STORAGE_STATE: JSON.stringify(storageState) },
    stdio: ['ignore', 'pipe', 'pipe'],
});
let report = '';
child.stdout.on('data', chunk => { report += chunk; process.stdout.write(chunk); });
child.stderr.on('data', chunk => { report += chunk; process.stderr.write(chunk); });
const code = await new Promise((resolve, reject) => {
    child.on('error', reject);
    child.on('close', code => resolve(code ?? 1));
});
await mkdir('target/frontend-quality', { recursive: true });
await writeFile('target/frontend-quality/ui-tests.tap', report);
process.exitCode = code;
