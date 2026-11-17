// Run against a disposable regular backend with PostgreSQL and Camunda disabled.
// Set PROPERTYFLOW_STAGE1_URL, then run node --test src/test/browser/stage1/guest-intake.test.mjs.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { after, before, test } from 'node:test';

const target = process.env.PROPERTYFLOW_STAGE1_URL;
assert.ok(target, 'PROPERTYFLOW_STAGE1_URL must point to a disposable regular test backend');
assert.ok(URL.canParse(target), 'PROPERTYFLOW_STAGE1_URL must be an absolute application URL');
const base = new URL(target);
assert.ok(['http:', 'https:'].includes(base.protocol) && !base.username && !base.password && !base.search && !base.hash,
    'PROPERTYFLOW_STAGE1_URL must be an HTTP(S) application URL');
if (!base.pathname.endsWith('/')) {
    base.pathname += '/';
}

const intakeUrl = new URL('mieter/fall', base);
const casePath = new URL('mieter/fall/zugang/', base).pathname;
const { launchBrowser } = await import('../browser-launcher.mjs');
let browser;

before(async () => {
    browser = await launchBrowser();
});

after(async () => {
    await browser?.close();
});

async function guest(t) {
    const context = await browser.newContext({ locale: 'de-CH', javaScriptEnabled: false });
    t.after(() => context.close());
    return context;
}

function example() {
    const reference = randomUUID();
    return {
        title: `Stufe 1 ${reference}`,
        unit: 'Teststrasse 12, Wohnung 3',
        email: `${reference}@example.test`,
        description: 'Nachricht mit <em>wörtlichem HTML</em>.',
    };
}

function assertProtection(headers) {
    assert.equal(headers['cache-control'], 'no-store');
    assert.equal(headers['referrer-policy'], 'no-referrer');
}

function personalLocation(headers) {
    const location = headers.location;
    assert.ok(location?.startsWith(casePath) && /^[^/?#]+$/.test(location.slice(casePath.length)),
        '303 must return a local personal link with exactly one token segment');
    return location;
}

async function prepare(context, fields) {
    const page = await context.newPage();
    const response = await page.goto(intakeUrl.href);
    assert.equal(response.status(), 200);
    assert.ok((await context.cookies(intakeUrl.href)).some(cookie => cookie.name === 'JSESSIONID'),
        'The guest form must establish a browser session');

    const form = page.locator('#intake-form');
    for (const [name, value] of Object.entries(fields)) {
        await form.locator(`[name="${name}"]`).fill(value);
    }
    const csrf = await form.locator('input[name=csrf]').inputValue();
    const operation = await form.locator('input[name=operation]').inputValue();
    assert.ok(csrf && operation, 'The form must provide both CSRF and an operation ID');
    return { page, form, fields: { ...fields, csrf, operation } };
}

function post(context, fields) {
    return context.request.post(intakeUrl.href, { form: fields, maxRedirects: 0 });
}

test('Stufe 1 J1: Gastformular öffnet den gespeicherten Fall auch ohne Sitzung', { timeout: 30_000 }, async t => {
    const context = await guest(t);
    const fields = example();
    const { page, form } = await prepare(context, fields);
    const [submitted] = await Promise.all([
        page.waitForResponse(response => response.url() === intakeUrl.href && response.request().method() === 'POST'),
        form.getByRole('button', { name: /Anliegen absenden/ }).click(),
    ]);
    assert.equal(submitted.status(), 303);
    assertProtection(submitted.headers());
    const location = personalLocation(submitted.headers());
    const link = new URL(location, base);

    await page.waitForURL(url => url.href === link.href)
        .catch(() => assert.fail('The browser did not navigate to the personal link'));
    assert.equal(await page.getByRole('heading', { name: fields.title, exact: true }).count(), 1);
    const caseId = (await page.locator('#case-head .eyebrow').textContent()).trim();
    assert.ok(caseId, 'The case ID must be visible');

    const reader = await guest(t);
    assert.equal((await reader.cookies()).length, 0);
    const readPage = await reader.newPage();
    const read = await readPage.goto(link.href).catch(() => assert.fail('Opening the personal link failed'));
    assert.equal(read.status(), 200);
    assertProtection(read.headers());
    assert.ok(readPage.url() === link.href, 'Reading must keep the personal link URL');
    assert.equal((await readPage.locator('#case-head .eyebrow').textContent()).trim(), caseId);
    assert.equal(await readPage.getByText('Eingegangen', { exact: true }).count(), 1);
    assert.equal(await readPage.getByText('Noch nicht bewertet', { exact: true }).count(), 1);
    assert.equal(await readPage.locator('#history').getByText(fields.description, { exact: true }).count(), 1);
    assert.equal(await readPage.locator('form[action$="/nachrichten"]').count(), 0);
    assert.ok(!(await read.text()).includes(fields.email), 'The personal view must not contain the raw contact address');

    const reread = await reader.request.get(link.href, { maxRedirects: 0 })
        .catch(() => assert.fail('Reading the personal link again failed'));
    assert.equal(reread.status(), 200, 'Reading must not consume the link');
});

test('Stufe 1 J3: unveränderte Wiederholung liefert denselben Link, geänderter Inhalt ergibt 409', { timeout: 30_000 }, async t => {
    const context = await guest(t);
    const { page, fields } = await prepare(context, example());

    // Keep the form and its session in place, as if the browser never received the first 303.
    const first = await post(context, fields);
    assert.equal(first.status(), 303);
    const location = personalLocation(first.headers());
    await first.dispose();

    const replay = await post(context, fields);
    assert.equal(replay.status(), 303);
    assertProtection(replay.headers());
    assert.ok(personalLocation(replay.headers()) === location, 'The replay must return the original personal link');

    const title = 'Geänderter Betreff beim Wiederholungsversuch';
    const conflict = await post(context, { ...fields, title });
    assert.equal(conflict.status(), 409);
    assertProtection(conflict.headers());
    assert.ok(!conflict.headers().location, 'A conflict must not return a personal link');
    await page.setContent(await conflict.text());
    assert.equal(await page.locator('#intake-form [name=title]').inputValue(), title);
    assert.ok(await page.locator('#intake-form [name=operation]').inputValue() === fields.operation,
        'The conflict form must keep the same operation ID');
});

test('Stufe 1 A1/A4: CSRF ist sitzungsgebunden und unbekannte Fall-Links bleiben neutral', { timeout: 30_000 }, async t => {
    const context = await guest(t);
    const { fields } = await prepare(context, example());
    const outsider = await guest(t);
    const withoutSession = await post(outsider, fields);
    assert.equal(withoutSession.status(), 403);
    assert.ok(!withoutSession.headers().location, 'A rejected request must not return a personal link');

    await prepare(outsider, example());
    const wrongSession = await post(outsider, fields);
    assert.equal(wrongSession.status(), 403);

    const forgedAccount = await post(context, { ...fields, accountSubmission: 'true' });
    assert.equal(forgedAccount.status(), 401);
    assert.ok(!forgedAccount.headers().location, 'A forged account submission must not be accepted as a guest');

    const invalidToken = randomUUID();
    const unknown = await outsider.request.get(new URL(casePath + invalidToken, base).href, { maxRedirects: 0 });
    assert.equal(unknown.status(), 404);
    assertProtection(unknown.headers());
    const html = await unknown.text();
    assert.ok(!html.includes(invalidToken) && !html.includes(fields.title) && !html.includes(fields.email),
        'The not-found view must not echo the token or disclose case data');
});
