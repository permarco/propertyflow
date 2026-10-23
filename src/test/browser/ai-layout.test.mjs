import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fixture, createCase, freeze, until } from './ui-test-helpers.mjs';

for (const viewport of [{ width: 1440, height: 900 }, { width: 1366, height: 768 },
    { width: 1280, height: 600 }, { width: 390, height: 844 }]) {
    test(`UI3: langer KI-Vorschlag ist bei ${viewport.width}×${viewport.height} erreichbar und übernehmbar`, async t => {
        const { page, context } = await fixture(t, { viewport });
        await freeze(page);
        await createCase(page, context);
        await page.locator('#message').fill('Bitte Zugang zur Wohnung ermöglichen.\n'.repeat(30));
        await page.locator('#generate').click();
        await page.clock.runFor(15000);
        await until(async () => await page.locator('#ai-status').getAttribute('data-state') === 'completed');
        const proposal = await page.locator('#ai-output').textContent();
        assert.ok(proposal.length > 500);

        if (viewport.width >= 1200) {
            const composer = await page.locator('#composer').boundingBox();
            assert.ok(composer.y + composer.height <= viewport.height,
                'Der Eingabebereich darf nicht unter dem abgeschnittenen Seitenrand liegen');
            assert.ok((await page.locator('#history').boundingBox()).height >= 200);
            const head = await page.locator('#case-head').boundingBox();
            const sidebar = await page.locator('#case-sidebar').boundingBox();
            // Scroll as a user would, outside the separately scrollable proposal text.
            await page.mouse.move(composer.x + 8, composer.y + composer.height / 2);
            await page.mouse.wheel(0, 2000);
            await until(async () => {
                const button = await page.locator('#adopt').boundingBox();
                return button.y >= composer.y && button.y + button.height <= composer.y + composer.height;
            }, 'Vorschlag übernehmen muss durch Scrollen vollständig erreichbar sein');
            assert.deepEqual(await page.locator('#case-head').boundingBox(), head);
            assert.deepEqual(await page.locator('#case-sidebar').boundingBox(), sidebar);
        }

        await page.locator('#adopt').click({ timeout: 3000 });
        assert.equal(await page.locator('#message').inputValue(), proposal);
        assert.equal(await page.locator('#ai').isHidden(), true);
        assert.equal(await page.locator('#history article').count(), 1, 'Übernehmen veröffentlicht nichts');
        await page.locator('#send').click({ trial: true, timeout: 3000 });
    });
}
