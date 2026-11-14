// Starts and stops a fresh application; Mailpit must already be running.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, open } from 'node:fs/promises';
import { createServer } from 'node:net';
import { setTimeout as pause } from 'node:timers/promises';
import { browserName } from './browser-launcher.mjs';

const expirySuite = process.argv.slice(2).includes('--session-expiry');
if (expirySuite) assert.deepEqual(process.argv.slice(2), ['--session-expiry']);
const reportName = browserName + (expirySuite ? '-session-expiry' : '');
const base = process.env.DEMO_URL || 'http://127.0.0.1:8083';
const url = new URL(base);
assert.equal(url.hostname, '127.0.0.1', 'The disposable application must use loopback');
assert.equal(url.protocol, 'http:');
assert.ok(url.port, 'Specify an explicit test port');
const env = {
    ...process.env,
    DEMO_URL: base,
    MAILPIT_URL: process.env.MAILPIT_URL || 'http://127.0.0.1:8026',
    DEMO_STAFF_EMAIL: process.env.DEMO_STAFF_EMAIL || 'staff-ci@example.test',
    DEMO_TENANT_EMAIL: process.env.DEMO_TENANT_EMAIL || 'tenant-ci@example.test',
    ...(expirySuite ? { DEMO_SESSION_EXPIRY_TEST: '60' } : {}),
};

// Fail before launching if this port belongs to another application.
await new Promise((resolve, reject) => {
    const probe = createServer();
    probe.once('error', reject);
    probe.listen(Number(url.port), url.hostname, () => probe.close(resolve));
});
const mailpit = await fetch(new URL('/api/v1/messages?limit=1', env.MAILPIT_URL), { signal: AbortSignal.timeout(5000) });
assert.equal(mailpit.status, 200, 'Test Mailpit must be available');
await mkdir('target/frontend-quality', { recursive: true });
const log = await open(`target/frontend-quality/app-${reportName}.log`, 'w');
const app = spawn('java', [
    '-jar', 'target/propertyflow-0.0.1-SNAPSHOT.jar',
    '--spring.profiles.active=frontend-demo', '--server.address=127.0.0.1', `--server.port=${url.port}`,
    ...(expirySuite ? ['--server.servlet.session.timeout=1m'] : []),
    '--spring.mail.host=127.0.0.1', `--spring.mail.port=${process.env.TEST_SMTP_PORT || '1026'}`,
    '--spring.mail.properties.mail.smtp.auth=false',
    '--spring.mail.properties.mail.smtp.starttls.enable=false',
    '--spring.mail.properties.mail.smtp.starttls.required=false',
    '--propertyflow.staff.mail.from=propertyflow@example.test',
    `--propertyflow.staff.bootstrap.email=${env.DEMO_STAFF_EMAIL}`,
    `--propertyflow.tenant.demo.email=${env.DEMO_TENANT_EMAIL}`,
], { stdio: ['ignore', log.fd, log.fd], windowsHide: true });
let appStopped = false;
let appError;
const stopped = new Promise(resolve => {
    app.once('error', error => { appError = error; appStopped = true; resolve(); });
    app.once('close', () => { appStopped = true; resolve(); });
});
let tests;
const stop = () => { tests?.kill(); app.kill(); };
process.once('SIGINT', stop);
process.once('SIGTERM', stop);
try {
    let ready = false;
    for (let attempt = 0; attempt < 120; attempt++) {
        if (appStopped) throw appError || new Error(`Application exited; see app-${reportName}.log`);
        try {
            ready = (await fetch(new URL('/mieter/fall', base), { signal: AbortSignal.timeout(1000) })).ok;
        } catch { /* Application is still starting. */ }
        if (ready) break;
        await pause(500);
    }
    assert.ok(ready, `Application did not become ready; see app-${reportName}.log`);
    tests = spawn(process.execPath, ['src/test/browser/run-ui-tests.mjs', ...process.argv.slice(2)], {
        env, stdio: 'inherit', windowsHide: true,
    });
    process.exitCode = await new Promise((resolve, reject) => {
        tests.once('error', reject);
        tests.once('close', code => resolve(code ?? 1));
    });
} finally {
    app.kill();
    const timeout = setTimeout(() => app.kill('SIGKILL'), 5000);
    await stopped;
    clearTimeout(timeout);
    await log.close();
    process.removeListener('SIGINT', stop);
    process.removeListener('SIGTERM', stop);
}
