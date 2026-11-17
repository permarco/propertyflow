// Run against a disposable regular application and its dedicated Mailpit.
// Set PROPERTYFLOW_STAGE2_URL, PROPERTYFLOW_STAGE2_STAFF_EMAIL, PROPERTYFLOW_STAGE2_CASE_ID and MAILPIT_URL.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { after, before, test } from 'node:test';

function localUrl(variable) {
    const value = process.env[variable];
    assert.ok(value && URL.canParse(value), variable + ' must be an explicit local test URL');
    const url = new URL(value);
    assert.ok(['http:', 'https:'].includes(url.protocol)
            && ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)
            && !url.username && !url.password && !url.search && !url.hash,
        variable + ' must point to a disposable HTTP(S) instance on loopback');
    return url;
}

const base = localUrl('PROPERTYFLOW_STAGE2_URL');
const mailpit = localUrl('MAILPIT_URL');
assert.notEqual(base.origin, mailpit.origin, 'The regular application and dedicated Mailpit need separate origins');
const email = process.env.PROPERTYFLOW_STAGE2_STAFF_EMAIL ?? '';
assert.ok(/^[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+\.test$/i.test(email), 'Provide a provisioned staff address using a .test domain');
const caseId = process.env.PROPERTYFLOW_STAGE2_CASE_ID ?? '';
assert.ok(/^[A-Za-z0-9_-]{1,80}$/.test(caseId), 'Provide the ID of an existing synthetic case');
if (!base.pathname.endsWith('/')) {
    base.pathname += '/';
}

const overview = new URL('mitarbeiter/faelle', base);
const detail = new URL('mitarbeiter/faelle/' + caseId, base);
const login = new URL('anmelden', base);
const requestCode = new URL('anmelden/code', base);
const verifyCode = new URL('anmelden/pruefen', base);
const logout = new URL('abmelden', base);
const intake = new URL('mieter/fall', base);
const { launchBrowser } = await import('../browser-launcher.mjs');
const { existingCodeMessages, waitForCode } = await import('../mailpit.mjs');
let browser;

before(async () => {
    browser = await launchBrowser();
});

after(async () => {
    await browser?.close();
});

async function isolatedBrowser(t, options = {}) {
    const context = await browser.newContext({ locale: 'de-CH', javaScriptEnabled: false, ...options });
    context.setDefaultTimeout(5000);
    t.after(() => context.close());
    return context;
}

function assertProtection(headers) {
    assert.equal(headers['cache-control'], 'no-store');
    assert.equal(headers['referrer-policy'], 'no-referrer');
}

function post(context, url, fields) {
    return context.request.post(url.href, { form: fields, maxRedirects: 0 });
}

function verify(context, fields) {
    return post(context, verifyCode, fields).catch(() => assert.fail('The OTP verification request failed'));
}

function partialCasePage(context, url = detail) {
    return context.request.get(url.href, {
        headers: { 'X-PropertyFlow-Request': 'partial' },
        maxRedirects: 0,
    });
}

async function clickPost(page, form, name, url) {
    const [response] = await Promise.all([
        page.waitForResponse(candidate => candidate.url() === url.href && candidate.request().method() === 'POST'),
        form.getByRole('button', { name, exact: true }).click(),
    ]);
    return response;
}

async function sessionCookie(context) {
    const cookie = (await context.cookies(detail.href)).find(item => item.name === 'JSESSIONID');
    assert.ok(cookie?.value, 'A session cookie must be present');
    assert.equal(cookie.httpOnly, true);
    assert.equal(cookie.sameSite, 'Lax');
    return cookie;
}

async function restoreCookie(context, cookie) {
    await context.clearCookies({ name: 'JSESSIONID' });
    await context.addCookies([cookie])
        .catch(() => assert.fail('Could not set the test session cookie'));
}

test('Stufe 2: Mitarbeiter-OTP öffnet die Fallübersicht und schützt das Falldetail', { timeout: 45000 }, async t => {
    const staff = await isolatedBrowser(t);
    const guest = await isolatedBrowser(t);
    const page = await staff.newPage();
    const guestPage = await guest.newPage();

    for (const url of [overview, detail]) {
        const anonymous = await partialCasePage(guest, url);
        assert.equal(anonymous.status(), 401);
        assert.equal(anonymous.headers().location, undefined);
        assertProtection(anonymous.headers());
        assert.ok(!(await anonymous.text()).includes(caseId), 'Anonymous refresh must not reveal the case');

        const redirected = await guest.request.get(url.href, { maxRedirects: 0 });
        assert.equal(redirected.status(), 303);
        assert.equal(redirected.headers().location, login.pathname);
        assertProtection(redirected.headers());
    }

    const opened = await page.goto(login.href);
    assert.equal(opened.status(), 200);
    assert.equal(page.url(), login.href);

    const initialCookie = await sessionCookie(staff);
    const requestForm = page.locator('#login-code-request');
    const initialCsrf = await requestForm.locator('input[name=csrf]').inputValue();
    assert.ok(initialCsrf, 'The login form must supply CSRF');
    assert.equal((await post(staff, requestCode, { email, csrf: 'invalid' })).status(), 403);

    await guestPage.goto(login.href);
    const guestAddress = 'stage2-unknown-' + randomUUID() + '@example.test';
    const guestRequestForm = guestPage.locator('#login-code-request');
    await guestRequestForm.getByLabel('E-Mail-Adresse').fill(guestAddress);
    const unknown = await clickPost(guestPage, guestRequestForm, 'Code anfordern', requestCode);
    assert.equal(unknown.status(), 200);
    const neutralNotice = (await guestPage.getByRole('status').textContent()).trim();
    assert.ok(neutralNotice, 'Code request must display a neutral notice');

    const previous = await existingCodeMessages(email);
    await requestForm.getByLabel('E-Mail-Adresse').fill(email);
    const requested = await clickPost(page, requestForm, 'Code anfordern', requestCode);
    assert.equal(requested.status(), 200);
    assertProtection(requested.headers());
    assert.equal((await page.getByRole('status').textContent()).trim(), neutralNotice);
    const code = await waitForCode(email, previous);

    const guestCsrf = await guestPage.locator('#login-code-verify input[name=csrf]').inputValue();
    const foreign = await verify(guest, { csrf: guestCsrf, email, code });
    assert.equal(foreign.status(), 400, 'A request field cannot select another browser’s challenge');
    assert.equal((await partialCasePage(guest)).status(), 401);

    const verifyForm = page.locator('#login-code-verify');
    const verificationCsrf = await verifyForm.locator('input[name=csrf]').inputValue();
    assert.equal((await verify(staff, { code, csrf: guestCsrf })).status(), 403);
    await verifyForm.getByLabel('Einmalcode').fill(code === '123456' ? '654321' : '123456');
    const wrong = await clickPost(page, verifyForm, 'Anmelden', verifyCode);
    assert.equal(wrong.status(), 400);
    assert.equal((await partialCasePage(staff)).status(), 401);
    await page.locator('#login-error').waitFor({ state: 'visible' });

    await verifyForm.getByLabel('Einmalcode').fill(code).catch(() => assert.fail('Could not enter the OTP in UI4'));
    const confirmed = await clickPost(page, verifyForm, 'Anmelden', verifyCode);
    assert.equal(confirmed.status(), 303);
    assert.equal(confirmed.headers().location, overview.pathname);
    assertProtection(confirmed.headers());
    await page.waitForURL(overview.href);
    await page.getByRole('heading', { name: 'Fallübersicht', exact: true }).waitFor({ state: 'visible' });
    const authenticatedCookie = await sessionCookie(staff);
    assert.ok(authenticatedCookie.value !== initialCookie.value, 'Login must rotate the session cookie');
    await restoreCookie(guest, initialCookie);
    assert.equal((await partialCasePage(guest)).status(), 401, 'The pre-login cookie must not grant staff access');

    const enhanced = await isolatedBrowser(t, { javaScriptEnabled: true, storageState: await staff.storageState() });
    const enhancedPage = await enhanced.newPage();
    const enhancedSearch = enhancedPage.locator('#filters input[name=q]');
    const enhancedLink = enhancedPage.getByRole('link', { name: caseId, exact: true });
    const enhancedQuery = 'keine-treffer-' + randomUUID();
    assert.equal((await enhancedPage.goto(overview.href)).status(), 200);
    await enhancedSearch.fill(enhancedQuery);
    await enhancedPage.waitForURL(url => url.searchParams.get('q') === enhancedQuery);
    await enhancedPage.getByRole('heading', { name: 'Keine Fälle für diese Auswahl.', exact: true }).waitFor({ state: 'visible' });
    assert.equal(await enhancedLink.count(), 0);
    await enhancedPage.getByRole('link', { name: 'Filter zurücksetzen', exact: true }).click();
    await enhancedPage.waitForURL(overview.href);
    assert.equal(await enhancedSearch.inputValue(), '');
    await enhancedSearch.fill(caseId);
    await enhancedPage.waitForURL(url => url.searchParams.get('q') === caseId);
    await enhancedLink.waitFor({ state: 'visible' });

    const filters = page.locator('#filters');
    const search = filters.locator('input[name=q]');
    const caseLink = page.getByRole('link', { name: caseId, exact: true });
    const unmatchedQuery = 'unbekannter-fall-' + randomUUID();
    await search.fill(unmatchedQuery);
    await filters.getByRole('button', { name: /Suchen|Filter anwenden/ }).click();
    await page.waitForURL(url => url.searchParams.get('q') === unmatchedQuery);
    assert.equal(await caseLink.count(), 0);
    await page.getByRole('heading', { name: 'Keine Fälle für diese Auswahl.', exact: true }).waitFor({ state: 'visible' });

    await search.fill(caseId);
    await filters.getByRole('button', { name: /Suchen|Filter anwenden/ }).click();
    await page.waitForURL(url => url.searchParams.get('q') === caseId);
    assert.equal(await caseLink.count(), 1, 'The overview must list the persisted matching case');
    await caseLink.click();
    await page.waitForURL(url => url.pathname === detail.pathname);
    assert.ok((await page.locator('main').textContent()).includes(caseId), 'The selected case must be visible');
    assert.equal((await partialCasePage(staff)).status(), 200);

    await page.getByRole('link', { name: 'Zur Fallübersicht', exact: true }).click();
    await page.waitForURL(url => url.pathname === overview.pathname && url.searchParams.get('q') === caseId);
    assert.equal(await search.inputValue(), caseId);
    await caseLink.click();
    await page.waitForURL(url => url.pathname === detail.pathname);

    const logoutForm = page.locator('form').filter({ has: page.getByRole('button', { name: 'Abmelden', exact: true }) });
    const logoutCsrf = await logoutForm.locator('input[name=csrf]').inputValue();
    assert.ok(logoutCsrf && logoutCsrf !== verificationCsrf, 'Login must rotate CSRF');
    assert.equal((await post(staff, logout, { csrf: verificationCsrf })).status(), 403);
    assert.equal((await partialCasePage(staff)).status(), 200, 'Rejected logout must keep the current session');
    const signedOut = await clickPost(page, logoutForm, 'Abmelden', logout);
    assert.equal(signedOut.status(), 303);
    assert.equal(signedOut.headers().location, intake.pathname);
    assertProtection(signedOut.headers());
    await page.waitForURL(intake.href);
    assert.equal((await partialCasePage(staff)).status(), 401);
    assert.equal((await partialCasePage(staff, overview)).status(), 401);
    await restoreCookie(guest, authenticatedCookie);
    assert.equal((await partialCasePage(guest)).status(), 401, 'The old cookie must lose staff access server-side');

    await page.goto(login.href);
    const freshCsrf = await page.locator('#login-code-request input[name=csrf]').inputValue();
    const replay = await verify(staff, { csrf: freshCsrf, email, code });
    assert.equal(replay.status(), 400, 'The recorded code must not open another session');
    assert.equal((await partialCasePage(staff)).status(), 401);
});
