// Isolierter echter Backendbetrieb für lokale Prüfung und CI; keine Entwicklungsdatenbank verwenden.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { mkdir, open, readFile, writeFile } from 'node:fs/promises';
import { createServer } from 'node:net';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

const repository = process.cwd();
const report = path.resolve('target/integrated-e2e');
const working = path.join(report, `run-${Date.now()}`);
const jar = path.resolve('target/propertyflow-0.0.1-SNAPSHOT.jar');
const project = `propertyflow-e2e-${process.pid}`;
const compose = ['compose', '-p', project, '-f', 'dev/compose.integration.yaml'];
const base = 'http://127.0.0.1:8084';
const camunda = 'http://127.0.0.1:8086';
const mail = 'http://127.0.0.1:8028';
const email = 'verwaltung.e2e@example.test';
const modelNames = ['propertyflow-fallprozess-entwurf', 'automatische-abklaerung', 'gespraech-auswerten', 'mitarbeiterbearbeitung',
    'mieterantwort-pruefen', 'mieternachricht-verarbeiten', 'wiedervorlage', 'fallbenachrichtigung-versenden'];
const key = randomBytes(32).toString('base64');
const common = ['-Xmx768m', '-jar', jar,
    '--spring.datasource.url=jdbc:postgresql://127.0.0.1:5440/propertyflow_e2e',
    '--spring.datasource.username=propertyflow_e2e', '--spring.datasource.password=propertyflow_e2e',
    `--propertyflow.links.encryption-key=${key}`, '--spring.ai.model.chat=none',
    '--spring.mail.host=127.0.0.1', '--spring.mail.port=1028',
    '--spring.mail.properties.mail.smtp.auth=false', '--spring.mail.properties.mail.smtp.starttls.enable=false',
    '--spring.mail.properties.mail.smtp.starttls.required=false'];
const browserEnv = { ...process.env, PROPERTYFLOW_DEV_SMOKE: 'true', PROPERTYFLOW_E2E_URL: base,
    PROPERTYFLOW_E2E_CAMUNDA_URL: camunda, PROPERTYFLOW_E2E_MAIL_URL: mail,
    PROPERTYFLOW_E2E_STAFF_EMAIL: email, PROPERTYFLOW_E2E_REPORTS: report,
    PROPERTYFLOW_E2E_STAFF_STATE: path.join(working, 'staff-state.json') };
let application;
let log;
let definitions;

async function run(command, args, options = {}) {
    const capture = command === process.execPath && args.includes('--test');
    let output = '';
    try { await new Promise((resolve, reject) => {
        const child = spawn(command, args, { cwd: repository, stdio: capture ? ['ignore', 'pipe', 'pipe'] : 'inherit', windowsHide: true, ...options });
        if (capture) {
            child.stdout.on('data', chunk => { output += chunk; process.stdout.write(chunk); });
            child.stderr.on('data', chunk => { output += chunk; process.stderr.write(chunk); });
        }
        child.once('error', reject);
        child.once('close', code => code === 0 ? resolve() : reject(new Error(`${command}: Exit ${code}`)));
    }); } finally {
        if (capture) await writeFile(path.join(report, `browser-${Date.now()}.log`), output);
    }
}

async function ready(url, label, count = 180) {
    for (let attempt = 0; attempt < count; attempt++) {
        const controller = new AbortController();
        // Eine referenzierte Frist hält Node auch während des ersten Verbindungsaufbaus aktiv.
        const timeout = setTimeout(() => controller.abort(), 2000);
        try {
            const response = await fetch(url, { signal: controller.signal });
            if (response.ok) return;
        } catch { /* Dienst startet noch. */ }
        finally { clearTimeout(timeout); }
        if (attempt % 15 === 0) console.log(`Warte auf ${label} (${attempt}/${count}).`);
        await delay(1000);
    }
    throw new Error(`${label} wurde nicht bereit.`);
}

async function startApplication(sessionTimeout = '30m') {
    log = await open(path.join(report, 'application.log'), 'a');
    application = spawn('java', [...common,
        '--spring.profiles.active=propertyflow-backend,propertyflow-dev', '--server.port=8084', '--server.address=127.0.0.1',
        `--server.servlet.session.timeout=${sessionTimeout}`,
        `--propertyflow.public-base-url=${base}`, `--camunda.client.rest-address=${camunda}`,
        '--camunda.client.grpc-address=http://127.0.0.1:26506',
        `--propertyflow.camunda.web-base-url=${camunda}`, '--camunda.client.auth.method=none',
        '--propertyflow.workflow.enabled=true', '--propertyflow.case-mail.enabled=true',
        `--propertyflow.workflow.definition-key=${definitions.caseKey}`, `--propertyflow.case-mail.definition-key=${definitions.mailKey}`,
    ], { cwd: working, stdio: ['ignore', log.fd, log.fd], windowsHide: true });
    application.on('error', error => console.error(error.message));
    await ready(`${base}/actuator/health`, 'PropertyFlow');
    assert.equal(application.exitCode, null, 'Backend wurde vorzeitig beendet');
}

async function stopApplication() {
    if (application && application.exitCode === null && application.signalCode === null) {
        const child = application;
        const stopped = new Promise(resolve => child.once('close', resolve));
        child.kill();
        const force = setTimeout(() => child.kill('SIGKILL'), 10000);
        await stopped;
        clearTimeout(force);
    }
    application = undefined;
    await log?.close();
    log = undefined;
}

// Feste getrennte Ports; bei Belegung keinen fremden Dienst übernehmen oder beenden.
for (const port of [8084, 8086, 26506, 5440, 8028, 1028]) {
    await new Promise((resolve, reject) => {
        const probe = createServer();
        probe.once('error', reject);
        probe.listen(port, '127.0.0.1', () => probe.close(resolve));
    });
}
await mkdir(working, { recursive: true });
const terminate = () => { application?.kill(); process.exitCode = 1; };
process.once('SIGINT', terminate);
process.once('SIGTERM', terminate);
try {
    await run('jar', ['xf', jar, 'BOOT-INF/classes/bpmn'], { cwd: working });
    await run('docker', [...compose, 'up', '-d']);
    await ready(`${camunda}/v2/topology`, 'Camunda');
    await ready(`${mail}/api/v1/messages`, 'Mailpit');
    const resources = new FormData();
    for (const name of modelNames) {
        const xml = await readFile(path.join(working, 'BOOT-INF/classes/bpmn', `${name}.bpmn`), 'utf8');
        assert.match(xml, /isExecutable="true"/);
        assert.doesNotMatch(xml, /isExecutable="false"/);
        resources.append('resources', new Blob([xml], { type: 'application/xml' }), `${name}.bpmn`);
    }
    const deployment = await fetch(`${camunda}/v2/deployments`, { method: 'POST', body: resources, signal: AbortSignal.timeout(30000) });
    assert.ok(deployment.ok, `BPMN-Deployment: ${await deployment.clone().text()}`);
    const deployed = (await deployment.json()).deployments.map(entry => entry.processDefinition);
    definitions = {
        caseKey: deployed.find(entry => entry.processDefinitionId === 'Fall_bearbeiten_PROCESS')?.processDefinitionKey,
        mailKey: deployed.find(entry => entry.processDefinitionId === 'Fallbenachrichtigung_versenden_PROCESS')?.processDefinitionKey,
    };
    assert.ok(definitions.caseKey && definitions.mailKey);
    const bootstrapLog = await open(path.join(report, 'bootstrap.log'), 'w');
    try {
        await run('java', [...common, '--spring.profiles.active=propertyflow-backend,staff-bootstrap',
            '--spring.main.web-application-type=none', '--propertyflow.workflow.enabled=false',
            '--propertyflow.case-mail.enabled=false', '--camunda.client.enabled=false',
            '--propertyflow.staff.bootstrap.name=CI-Verwaltung', `--propertyflow.staff.bootstrap.email=${email}`,
        ], { cwd: working, stdio: ['ignore', bootstrapLog.fd, bootstrapLog.fd] });
    } finally { await bootstrapLog.close(); }
    await startApplication();
    const runtimeStarted = Date.now();
    await run(process.execPath, ['--test', 'src/test/browser/dev-runtime.test.mjs'], { env: browserEnv });
    await run(process.execPath, ['--test', '--test-concurrency=1', 'src/test/browser/intake-validation.test.mjs',
        'src/test/browser/integrated-ui.test.mjs', 'src/test/browser/list-pagination.test.mjs'], { env: browserEnv });
    if (process.env.PROPERTYFLOW_PERFORMANCE === 'true') {
        await run(process.execPath, ['src/test/browser/frontend-performance.mjs'], { env: browserEnv });
    }
    // Der reguläre Einmalcode-Missbrauchsschutz bleibt auch in CI unverändert aktiv.
    let remaining = Math.max(0, 90000 - (Date.now() - runtimeStarted));
    while (remaining > 0) {
        console.log('Warte auf das reguläre Anmeldecode-Intervall.');
        await delay(Math.min(remaining, 30000));
        remaining = Math.max(0, 90000 - (Date.now() - runtimeStarted));
    }
    await run(process.execPath, ['--test', 'src/test/browser/dev-accounts.test.mjs'], { env: browserEnv });
    await stopApplication();
    await startApplication();
    await run(process.execPath, ['--test', 'src/test/browser/dev-accounts.test.mjs'], {
        env: { ...browserEnv, PROPERTYFLOW_DEV_SMOKE: 'false', PROPERTYFLOW_DEV_ACCOUNT_RESTART: 'true' },
    });
    await stopApplication();
    await startApplication('1m');
    await run(process.execPath, ['--test', 'src/test/browser/integrated-session-expiry.test.mjs'], { env: browserEnv });
    console.log('Integrierte Browserprüfung und Kontenprüfung nach Backendneustart bestanden.');
} catch (error) {
    if (application?.pid) {
        const dump = await open(path.join(report, 'threads.log'), 'w');
        try { await run('jcmd', [String(application.pid), 'Thread.print'], { stdio: ['ignore', dump.fd, dump.fd] }); }
        catch { /* Diagnose darf den ursprünglichen Fehler nicht verdecken. */ }
        finally { await dump.close(); }
    }
    throw error;
} finally {
    await stopApplication();
    for (const resource of ['process-instances', 'incidents']) {
        try {
            const response = await fetch(`${camunda}/v2/${resource}/search`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ page: { limit: 100 } }), signal: AbortSignal.timeout(5000),
            });
            if (response.ok) await writeFile(path.join(report, `${resource}.json`), await response.text());
        } catch { /* Diagnosefehler dürfen die Bereinigung der eigenen Container nicht verhindern. */ }
    }
    const engineLog = await open(path.join(report, 'infrastructure.log'), 'w');
    try { await run('docker', [...compose, 'logs', '--no-color'], { stdio: ['ignore', engineLog.fd, engineLog.fd] }); }
    finally {
        await engineLog.close();
        await run('docker', [...compose, 'down']);
    }
    process.removeListener('SIGINT', terminate);
    process.removeListener('SIGTERM', terminate);
}
