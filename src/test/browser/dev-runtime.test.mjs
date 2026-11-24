import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { test } from 'node:test';
import { setTimeout as delay } from 'node:timers/promises';
import { launchBrowser } from './browser-launcher.mjs';

const base = process.env.PROPERTYFLOW_E2E_URL || 'http://127.0.0.1:8081';
const mail = process.env.PROPERTYFLOW_E2E_MAIL_URL || 'http://127.0.0.1:8025';
const camunda = process.env.PROPERTYFLOW_E2E_CAMUNDA_URL || 'http://127.0.0.1:8080';
const reports = process.env.PROPERTYFLOW_E2E_REPORTS || 'target/dev-verification';

async function until(operation, label) {
    const deadline = Date.now() + 120000;
    do {
        const result = await operation();
        if (result) return result;
        await delay(1000);
    } while (Date.now() < deadline);
    throw new Error(`Zeitüberschreitung: ${label}`);
}

test('Devbetrieb ohne KI: Mieterfall, Mitarbeiterdialog und echte Camunda-Ansicht', {
    skip: process.env.PROPERTYFLOW_DEV_SMOKE !== 'true', timeout: 300000,
}, async t => {
    const browser = await launchBrowser();
    const tenantContext = await browser.newContext();
    const staffContext = await browser.newContext();
    const tenant = await tenantContext.newPage();
    const staff = await staffContext.newPage();
    t.after(async () => {
        try {
            await mkdir(reports, { recursive: true });
            await staff.screenshot({ path: `${reports}/staff-last-state.png`, fullPage: true });
            await writeFile(`${reports}/staff-last-state.txt`, await staff.locator('body').innerText());
        } finally { await browser.close(); }
    });
    const errors = [];
    tenant.on('pageerror', error => errors.push(error.message));
    staff.on('pageerror', error => errors.push(error.message));

    await tenant.goto(`${base}/mieter/fall`);
    await tenant.locator('#title').fill('[Dev-Prüfung] Küchenfenster klemmt');
    await tenant.locator('#unit').fill('Testweg 5, Wohnung 2');
    await tenant.locator('#email').fill('mieter.browser@example.test');
    await tenant.locator('#description').fill('Das Küchenfenster lässt sich seit gestern schwer öffnen. Bitte prüfen.');
    await tenant.getByRole('button', { name: 'Anliegen absenden' }).click();
    await tenant.waitForURL('**/mieter/fall/zugang/*');
    const caseId = (await tenant.locator('.eyebrow').innerText()).trim();
    assert.match(caseId, /^[0-9a-f-]{36}$/);
    await mkdir(reports, { recursive: true });
    await writeFile(`${reports}/runtime-case.json`, JSON.stringify({ base, camunda, caseId, createdAt: new Date().toISOString() }));

    const config = process.env.PROPERTYFLOW_E2E_STAFF_EMAIL ? '' : await readFile('.propertyflow-local/staff-bootstrap.properties', 'utf8');
    const email = process.env.PROPERTYFLOW_E2E_STAFF_EMAIL || config.match(/^propertyflow\.staff\.bootstrap\.email=(.+)$/m)[1].trim();
    const previous = new Set((await (await fetch(`${mail}/api/v1/messages`)).json()).messages.map(item => item.ID));
    await staff.goto(`${base}/anmelden`);
    await staff.getByLabel('E-Mail-Adresse', { exact: true }).fill(email);
    await staff.getByRole('button', { name: 'Code anfordern' }).click();
    const code = await until(async () => {
        const inbox = await (await fetch(`${mail}/api/v1/messages`)).json();
        const message = inbox.messages.find(item => !previous.has(item.ID)
            && item.Subject === 'Ihr Einmalcode für PropertyFlow' && item.To.some(to => to.Address === email));
        if (!message) return null;
        const detail = await (await fetch(`${mail}/api/v1/message/${message.ID}`)).json();
        return detail.Text.match(/\b[0-9]{6}\b/)?.[0];
    }, 'Einmalcode');
    await staff.getByLabel('Einmalcode', { exact: true }).fill(code);
    await staff.getByRole('button', { name: 'Anmelden', exact: true }).click();
    await staff.waitForURL('**/mitarbeiter/faelle');
    await until(async () => {
        await staff.goto(`${base}/mitarbeiter/faelle/${caseId}`);
        return (await staff.locator('#case-status').innerText()).trim() === 'Mitarbeiterprüfung erforderlich';
    }, 'manuelle Mitarbeiterprüfung ohne KI');
    await staff.locator('#staff-review-heading').click();
    await staff.getByRole('button', { name: 'Bearbeitung fortsetzen', exact: true }).click();
    await until(async () => (await staff.locator('#case-status').innerText()).trim() === 'In Bearbeitung', 'Fortsetzen');

    const staffMessage = 'Bitte teilen Sie uns mit, ob der Fenstergriff vollständig gedreht werden kann.';
    await staff.locator('#staff-message-text').fill(staffMessage);
    await staff.getByRole('button', { name: 'Mit KI formulieren', exact: true }).click();
    await staff.waitForFunction(() => document.querySelector('[data-formulation-status]').dataset.state === 'error');
    assert.equal(await staff.locator('#staff-message-text').inputValue(), staffMessage);
    assert.equal(await staff.getByRole('button', { name: 'Vorschlag übernehmen' }).isDisabled(), true);
    const formulationAccess = await (await staff.request.get(`${base}/mitarbeiter/faelle/${caseId}/aktionen/verfuegbar`)).json();
    const attemptId = crypto.randomUUID();
    const formulationUrl = `${base}/mitarbeiter/faelle/${caseId}/ki-formulierung`;
    assert.equal((await staff.request.post(`${formulationUrl}/${attemptId}/abbrechen`, {
        form: { csrf: formulationAccess.csrf },
    })).status(), 204);
    assert.equal((await staff.request.post(formulationUrl, {
        form: { csrf: formulationAccess.csrf, attemptId, text: staffMessage },
    })).status(), 409);
    assert.equal((await tenant.request.post(formulationUrl, {
        form: { csrf: formulationAccess.csrf, attemptId: crypto.randomUUID(), text: 'Kein Mitarbeiterzugriff' },
    })).status(), 401);
    await staff.locator('#staff-message-form input[name=answer]').check();
    await staff.locator('#staff-message-form').getByRole('button', { name: 'Nachricht senden' }).click();
    await staff.locator('#history').getByText(staffMessage, { exact: true }).waitFor({ timeout: 30000 });
    await tenant.reload();
    await tenant.locator('#history').getByText(staffMessage, { exact: true }).waitFor();
    const reply = 'Der Griff lässt sich vollständig drehen, aber das Fenster klemmt weiterhin.';
    await tenant.locator('#tenant-message-text').fill(reply);
    await tenant.getByRole('button', { name: 'Nachricht senden', exact: true }).click();
    await tenant.locator('#history').getByText(reply, { exact: true }).waitFor({ timeout: 30000 });
    await until(async () => {
        await staff.reload();
        return (await staff.locator('#case-status').innerText()).trim() === 'Mitarbeiterprüfung erforderlich';
    }, 'manuelle Auswertung der Mieterantwort ohne KI');
    await staff.locator('#history').getByText(reply, { exact: true }).waitFor();

    const [operate] = await Promise.all([
        staffContext.waitForEvent('page'), staff.getByRole('link', { name: 'Prozess in Camunda anzeigen' }).click(),
    ]);
    await operate.waitForURL(`${camunda}/operate/**`);
    await operate.getByRole('textbox', { name: 'Username' }).fill('demo');
    await operate.locator('input[name=password]').fill('demo');
    await operate.getByRole('button', { name: 'Login', exact: true }).click();
    await operate.getByRole('link', { name: 'Processes', exact: true }).waitFor();
    // Der erneute Fall-Link löst auch nach der Anmeldung die aktuelle führende Instanz auf.
    const response = await staffContext.request.get(`${base}/mitarbeiter/faelle/${caseId}/prozess`, { maxRedirects: 0 });
    assert.equal(response.status(), 303);
    const processUrl = response.headers().location;
    assert.equal(new URL(processUrl).origin, camunda);
    assert.match(new URL(processUrl).pathname, /^\/operate\/processes\/[0-9]+$/);
    await operate.goto(processUrl);
    try {
        await operate.getByTitle('Open Aktiven Fall bearbeiten', { exact: true }).waitFor();
    } catch (error) {
        await mkdir(reports, { recursive: true });
        await operate.screenshot({ path: `${reports}/operate-failure.png`, fullPage: true });
        await writeFile(`${reports}/operate-failure.txt`, await operate.locator('body').innerText());
        throw error;
    }
    const introduction = operate.getByRole('button', { name: 'Got it', exact: true });
    if (await introduction.isVisible()) await introduction.click();
    await operate.getByTitle('Open Aktiven Fall bearbeiten', { exact: true }).click();
    await operate.getByRole('button', { name: 'Reset diagram zoom', exact: true }).click();
    const staffCall = 'Mitarbeiterpruefung_und_Bearbeitung_CALL';
    await operate.locator(`[data-element-id="${staffCall}"]`).first().waitFor({ timeout: 30000 });
    await operate.locator(`[data-container-id="${staffCall}"] [data-testid="state-overlay-active"]`).waitFor();
    assert.ok((await operate.locator('body').innerText()).includes('Fall bearbeiten'));
    await mkdir(reports, { recursive: true });
    await operate.screenshot({ path: `${reports}/camunda-case.png`, fullPage: true });
    await staff.screenshot({ path: `${reports}/staff-case.png`, fullPage: true });
    await tenant.screenshot({ path: `${reports}/tenant-case.png`, fullPage: true });
    await staff.goto(`${base}/mitarbeiter/betrieb`);
    await staff.getByRole('heading', { name: 'Aufträge prüfen', exact: true }).waitFor();
    assert.equal(await staff.locator('[role=alert]').count(), 0);
    assert.equal((await tenant.request.get(`${base}/mitarbeiter/betrieb`, { maxRedirects: 0 })).status(), 303);
    assert.deepEqual(errors, []);
    if (process.env.PROPERTYFLOW_E2E_STAFF_STATE) {
        await staffContext.storageState({ path: process.env.PROPERTYFLOW_E2E_STAFF_STATE });
    }
    await writeFile(`${reports}/browser-result.json`, JSON.stringify({
        testedAt: new Date().toISOString(), caseId, processUrl,
        checks: ['intake', 'otp-login', 'manual-continue', 'formulation-fallback', 'formulation-early-abort',
            'formulation-tenant-denied', 'staff-question', 'tenant-reply', 'ai-failure-review', 'operate-diagram', 'operations-overview'],
        pageErrors: errors,
    }, null, 2));
});
