// Start a disposable frontend-demo instance first; DEMO_URL defaults to port 8082.
// node src/test/browser/run-ui-tests.mjs
import { readdir, mkdir, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const directory = path.dirname(fileURLToPath(import.meta.url));
const files = (await readdir(directory)).filter(name => name.endsWith('.test.mjs')).sort()
    .map(name => path.join(directory, name));
const child = spawn(process.execPath, ['--test', '--test-concurrency=1', '--test-reporter=tap', ...files], {
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
