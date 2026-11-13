import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { mkdir } from 'node:fs/promises';
import { existingCodeMessages, waitForCode } from './mailpit.mjs';
import { fixture, base } from './ui-test-helpers.mjs';

test('self-registration: Mailpit verification, previous guest cases, new case, ownership and logout', async t => {
    const { page, context } = await fixture(t, { staff: false });
    const email = `registration-${randomUUID()}@example.test`;
    await page.goto(`${base}/mieter/fall`);
    const csrf = await page.locator('#intake-form input[name=csrf]').inputValue();
    const createGuest = async (title, address) => {
        const response = await context.request.post(`${base}/mieter/fall`, {
            form: { csrf, operation: randomUUID(), title, unit: 'Wohnung 2', email: address, description: 'Heizung kalt' },
            maxRedirects: 0,
        });
        assert.equal(response.status(), 303);
        await page.goto(base + response.headers().location);
        return { id: (await page.locator('.case-heading .eyebrow').textContent()).trim(), url: page.url() };
    };
    const first = await createGuest('Früheres Anliegen A', email);
    const second = await createGuest('Früheres Anliegen B', email);
    const foreign = await createGuest('Fremdes Anliegen', `foreign-${randomUUID()}@example.test`);
    assert.equal((await context.request.get(`${base}/mieter/faelle/${first.id}`)).status(), 401);

    await page.goto(`${base}/mieter/fall`);
    await page.getByRole('link', { name: 'Userkonto eröffnen', exact: true }).click();
    const previousIds = await existingCodeMessages(email);
    await page.locator('#login-email').fill(email);
    await page.getByRole('button', { name: 'Code anfordern', exact: true }).click();
    const code = await waitForCode(email, previousIds);
    await page.locator('#login-code').fill(code === '000000' ? '111111' : '000000');
    await page.getByRole('button', { name: 'E-Mail bestätigen', exact: true }).click();
    assert.match(await page.locator('#login-error').textContent(), /ungültig/);
    assert.equal((await context.request.get(`${base}/mieter/faelle`)).status(), 401);
    await page.locator('#login-code').fill(code);
    await page.getByRole('button', { name: 'E-Mail bestätigen', exact: true }).click();
    await page.waitForURL(`${base}/mieter/faelle`);
    assert.equal(await page.locator('.tenant-case').count(), 2);
    assert.equal(await page.locator('.account-identity strong').textContent(), email);
    assert.equal(await page.getByRole('link', { name: 'Fremdes Anliegen', exact: true }).count(), 0);
    assert.equal((await context.request.get(`${base}/mieter/faelle/${foreign.id}?email=${email}`)).status(), 404);
    assert.equal((await context.request.get(`${base}/mitarbeiter/faelle`)).status(), 403);
    await mkdir('target/account-review', { recursive: true });
    for (const width of [1440, 768, 390]) {
        await page.setViewportSize({ width, height: 900 });
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
        await page.screenshot({ path: `target/account-review/registered-cases-${width}.png`, fullPage: true });
    }
    await page.getByRole('link', { name: 'Früheres Anliegen A', exact: true }).click();
    await page.locator('#message').fill('Ergänzung aus meinem neuen Konto');
    await page.getByRole('button', { name: 'Nachricht senden', exact: true }).click();
    await page.waitForFunction(() => document.querySelector('#history').textContent.includes('Ergänzung aus meinem neuen Konto'));
    await page.getByRole('link', { name: 'Meine Fälle', exact: true }).click();
    await page.getByRole('link', { name: 'Neues Anliegen melden', exact: true }).click();
    assert.equal(await page.locator('input[name=email]').count(), 0);
    await page.locator('#title').fill('Neues Anliegen nach Registrierung');
    await page.locator('#unit').fill('Wohnung 2');
    await page.locator('#description').fill('Fenster schliesst nicht');
    await page.getByRole('button', { name: /Anliegen absenden/ }).click();
    await page.waitForURL(/\/mieter\/fall\/zugang\//);
    await page.getByRole('link', { name: 'Meine Fälle', exact: true }).click();
    assert.equal(await page.locator('.tenant-case').count(), 3);
    assert.equal(await page.getByRole('link', { name: 'Neues Anliegen nach Registrierung', exact: true }).isVisible(), true);
    await page.getByRole('button', { name: 'Abmelden', exact: true }).click();
    await page.waitForURL(`${base}/mieter/fall`);
    assert.equal((await context.request.get(`${base}/mieter/faelle/${second.id}`)).status(), 401);
    await page.goto(first.url);
    assert.equal(await page.getByRole('heading', { name: 'Früheres Anliegen A', exact: true }).isVisible(), true);
});
