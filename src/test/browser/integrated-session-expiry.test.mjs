import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { test } from 'node:test';
import { setTimeout as delay } from 'node:timers/promises';
import { launchBrowser } from './browser-launcher.mjs';
import { base, login, createCase, review, until } from './integrated-helpers.mjs';

test('Echter Sitzungsablauf: Mieter und Mitarbeiter behalten Entwürfe, ohne Übertragung nach Wiederanmeldung', { timeout: 240000 }, async t => {
    const browser = await launchBrowser();
    t.after(() => browser.close());
    const staffContext = await browser.newContext();
    const tenantContext = await browser.newContext();
    const staff = await staffContext.newPage();
    const tenant = await tenantContext.newPage();
    const email = 'expiry-' + randomUUID() + '@example.test';
    await login(tenant, email, '/registrieren');
    const c = await createCase(tenant, { accountSubmission: 'true' });
    await login(staff, process.env.PROPERTYFLOW_E2E_STAFF_EMAIL);
    await review(staff, c);
    // Nur Browser-Timer pausieren: Cookie und Servlet-Uhr bleiben unverändert.
    for (const page of [tenant, staff]) {
        const now = new Date();
        await page.clock.install({ time: now });
        await page.clock.pauseAt(now);
        // Bereits vor install() erzeugte native Timer werden erst mit einem neuen Dokument ersetzt.
        await page.reload();
        await page.waitForLoadState('networkidle');
    }
    await staff.locator('#staff-message-text').fill('Mitarbeiterentwurf bleibt lokal');
    await tenant.locator('#tenant-message-text').fill('Mieterentwurf bleibt lokal');
    const traffic = [];
    const record = request => traffic.push(request.method() + ' ' + request.url());
    staffContext.on('request', record);
    tenantContext.on('request', record);
    const cookies = await Promise.all([staffContext.cookies(), tenantContext.cookies()]);
    console.log('65 Sekunden tatsächlicher Leerlauf ohne HTTP-Zugriff.');
    await delay(65000);
    assert.deepEqual(traffic, []);
    assert.deepEqual(await Promise.all([staffContext.cookies(), tenantContext.cookies()]), cookies);
    await tenant.locator('#tenant-message-form button').click();
    await staff.locator('#staff-message-form button[type=submit]').click();
    await until(async () => (await tenant.locator('#tenant-message-form [role=status]').innerText()).includes('nicht mehr gültig'), 'Mieterzugriff gesperrt');
    await until(async () => (await staff.locator('#staff-message-form > [role=status]').innerText()).includes('erneut an'), 'Mitarbeiterzugriff gesperrt');
    assert.equal(await tenant.locator('#tenant-message-text').inputValue(), 'Mieterentwurf bleibt lokal');
    assert.equal(await staff.locator('#staff-message-text').inputValue(), 'Mitarbeiterentwurf bleibt lokal');
    assert.equal(await tenant.locator('#history').innerText(), '');
    const newTenant = await tenantContext.newPage();
    const newStaff = await staffContext.newPage();
    await login(newTenant, email, '/mieter/anmelden');
    await login(newStaff, process.env.PROPERTYFLOW_E2E_STAFF_EMAIL);
    await newTenant.goto(c.tenantUrl);
    await newStaff.goto(c.staffUrl);
    assert.equal(await newTenant.locator('#tenant-message-text').inputValue(), '');
    assert.equal(await newStaff.locator('#staff-message-text').inputValue(), '');
    assert.doesNotMatch(await newStaff.locator('#history').innerText(), /entwurf bleibt lokal/);
    const writes = [];
    for (const page of [staff, tenant]) page.on('request', request => { if (request.method() === 'POST') writes.push(request.url()); });
    await tenant.clock.runFor(20001);
    await staff.clock.runFor(20001);
    assert.equal(await tenant.locator('#tenant-message-form button').isDisabled(), true);
    assert.equal(await staff.locator('#staff-message-form button[type=submit]').isDisabled(), true);
    assert.deepEqual(writes, []);
});
