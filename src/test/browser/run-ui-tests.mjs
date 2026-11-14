import { browserName, launchBrowser } from './browser-launcher.mjs';
// Start a disposable frontend-demo instance first; DEMO_URL defaults to port 8082.
// node src/test/browser/run-ui-tests.mjs
import { readdir, mkdir, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { staffStorageState } from './staff-test-login.mjs';

const directory = path.dirname(fileURLToPath(import.meta.url));
const coreFiles = ['core-workflows.test.mjs', 'tenant-account.test.mjs', 'tenant-registration.test.mjs', 'keyboard-focus.test.mjs'];
const args = process.argv.slice(2);
const expirySuite = args.length === 1 && args[0] === '--session-expiry';
const selected = expirySuite ? ['session-expiry.test.mjs'] : args.length === 1 && args[0] === '--core' ? coreFiles : args;
if (selected.includes('session-expiry.test.mjs') && (!expirySuite || process.env.DEMO_SESSION_EXPIRY_TEST !== '60')) {
    throw new Error('Use run-browser-regression.mjs --session-expiry to start the isolated one-minute session server.');
}
const available = (await readdir(directory)).filter(name => name.endsWith('.test.mjs'));
for (const name of selected) {
    if (!available.includes(name)) throw new Error(`Unknown browser test file: ${name}`);
}
const files = (selected.length ? selected : available.filter(name => name !== 'session-expiry.test.mjs')).sort()
    .map(name => path.join(directory, name));
await mkdir('target/frontend-quality', { recursive: true });
let report = `# Browser: ${browserName}\n`;
let code = 1;
try {
    if (process.env.CI && !process.env.DEMO_TENANT_EMAIL) throw new Error('CI requires DEMO_TENANT_EMAIL; account tests must not be skipped.');
    let storageState;
    if (!expirySuite) {
        const browser = await launchBrowser();
        try {
            storageState = await staffStorageState(browser);
        } finally {
            await browser.close();
        }
    }
    const child = spawn(process.execPath, ['--test', '--test-concurrency=1', '--test-reporter=tap', ...files], {
        env: { ...process.env, DEMO_STAFF_STORAGE_STATE: storageState ? JSON.stringify(storageState) : '' },
        stdio: ['ignore', 'pipe', 'pipe'],
    });
    child.stdout.on('data', chunk => { report += chunk; process.stdout.write(chunk); });
    child.stderr.on('data', chunk => { report += chunk; process.stderr.write(chunk); });
    code = await new Promise((resolve, reject) => {
        child.on('error', reject);
        child.on('close', code => resolve(code ?? 1));
    });
} catch (error) {
    report += `# Test startup failed: ${error.stack}\n`;
    console.error(error);
} finally {
    await writeFile(`target/frontend-quality/ui-tests-${browserName}${expirySuite ? '-session-expiry' : ''}.tap`, report);
    await writeFile('target/frontend-quality/ui-tests.tap', report);
}
process.exitCode = code;
