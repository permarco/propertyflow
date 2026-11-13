import assert from 'node:assert/strict';
import { setTimeout as pause } from 'node:timers/promises';

const base = process.env.MAILPIT_URL || 'http://127.0.0.1:8026';
const subject = 'Ihr Einmalcode für PropertyFlow';

async function json(path) {
    const response = await fetch(new URL(path, base), { signal: AbortSignal.timeout(3000) });
    assert.equal(response.status, 200, `Mailpit API unavailable (${response.status}); check MAILPIT_URL and test Compose.`);
    return response.json();
}

async function messages(email) {
    const matching = [];
    for (let start = 0; ; start += 100) {
        const page = await json(`/api/v1/messages?start=${start}&limit=100`);
        assert.ok(Array.isArray(page.messages), 'Unexpected Mailpit message list');
        matching.push(...page.messages.filter(message => message.Subject === subject
            && message.To?.some(to => to.Address.toLowerCase() === email.toLowerCase())));
        if (page.messages.length < 100 || start + 100 >= page.total) return matching;
    }
}

export async function existingCodeMessages(email) {
    return new Set((await messages(email)).map(message => message.ID));
}

export async function waitForCode(email, previousIds) {
    const deadline = Date.now() + 10000;
    while (Date.now() < deadline) {
        for (const message of await messages(email)) {
            if (previousIds.has(message.ID)) continue;
            const detail = await json(`/api/v1/message/${encodeURIComponent(message.ID)}`);
            const code = detail.Text?.match(/PropertyFlow lautet:\s*([0-9]{6})\b/)?.[1];
            if (code) return code;
        }
        await pause(100);
    }
    assert.fail('No fresh OTP arrived in Mailpit for the test account; check SMTP settings and resend cooldown.');
}
