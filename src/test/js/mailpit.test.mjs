import test from 'node:test';
import assert from 'node:assert/strict';
import { existingCodeMessages, waitForCode } from '../browser/mailpit.mjs';

test('Mailpit selects a fresh opaque ID for the exact recipient and subject', async t => {
    const email = 'tenant@example.test';
    const message = (ID, address = email, subject = 'Ihr Einmalcode für PropertyFlow') => ({
        ID, To: [{ Address: address }], Subject: subject,
    });
    let delivered = false;
    const fetchedDetails = [];
    t.mock.method(globalThis, 'fetch', async url => {
        const path = new URL(url).pathname;
        if (path === '/api/v1/messages') {
            const messages = delivered
                ? [message('wrong-recipient', 'other@example.test'), message('wrong-subject', email, 'Other'),
                    message('aaa-new'), message('zzz-old')]
                : [message('zzz-old')];
            return Response.json({ messages, total: messages.length });
        }
        fetchedDetails.push(path);
        return Response.json({ Text: 'Ihr Einmalcode für PropertyFlow lautet: 123456' });
    });
    const baseline = await existingCodeMessages(email);
    delivered = true;
    assert.equal(await waitForCode(email, baseline), '123456');
    assert.deepEqual(fetchedDetails, ['/api/v1/message/aaa-new']);
});

test('Mailpit API errors fail explicitly instead of appearing as an empty mailbox', async t => {
    t.mock.method(globalThis, 'fetch', async () => new Response('', { status: 503 }));
    await assert.rejects(existingCodeMessages('tenant@example.test'), /Mailpit API unavailable/);
});
