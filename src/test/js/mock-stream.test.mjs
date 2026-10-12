import { test } from 'node:test';
import assert from 'node:assert/strict';
import { setTimeout as pause } from 'node:timers/promises';
import { MockStream } from '../../main/resources/static/js/mock-stream.mjs';

async function until(predicate) {
    const deadline = Date.now() + 3000;
    while (!predicate() && Date.now() < deadline) await pause(10);
    assert.ok(predicate(), 'Expected stream state within 3 seconds');
}

test('complete response preserves the instruction including HTML as literal text', async () => {
    const states = [];
    const stream = new MockStream(s => states.push(s), { wait: 1, interval: 1 });
    stream.start('<img onerror=alert(1)>');
    await until(() => stream.state === 'completed');
    assert.equal(stream.state, 'completed');
    assert.match(stream.text, /<img onerror=alert\(1\)>/);
    assert.equal(states[0].state, 'waiting');
    assert.ok(states.some(s => s.state === 'streaming'));
});
test('abort while waiting cancels all future output', async () => {
    const updates = [];
    const stream = new MockStream(s => updates.push(s), { wait: 30, interval: 1 });
    stream.start('Bitte antworten.');
    stream.abort();
    const count = updates.length;
    await pause(80);
    assert.equal(stream.state, 'aborted');
    assert.equal(stream.text, '');
    assert.equal(updates.length, count);
});
test('abort during output, restart and stale callbacks', async () => {
    const stream = new MockStream(() => {}, { wait: 1, interval: 1 });
    stream.start('ERSTER VERSUCH');
    assert.equal(stream.start('parallel'), false);
    await until(() => stream.state === 'streaming');
    stream.abort();
    const text = stream.text;
    await pause(20);
    assert.equal(stream.text, text);
    assert.ok(text.length > 0);
    stream.start('NEUER VERSUCH');
    await until(() => stream.state === 'completed');
    assert.equal(stream.state, 'completed');
    assert.match(stream.text, /NEUER VERSUCH/);
    assert.doesNotMatch(stream.text, /ERSTER VERSUCH/);
});
test('error stays incomplete and allows retry; disposal stops callbacks', async () => {
    const updates = [];
    const stream = new MockStream(s => updates.push(s), { wait: 1, interval: 1 });
    stream.start('Test', 'error');
    await until(() => stream.state === 'error');
    assert.equal(stream.state, 'error');
    assert.ok(stream.text.length > 0);
    stream.start('Test', 'unsafe');
    await until(() => stream.state === 'completed');
    assert.equal(stream.state, 'completed');
    assert.match(stream.text, /<script>/);
    stream.start('Test');
    stream.dispose();
    const count = updates.length;
    await pause(50);
    assert.equal(updates.length, count);
});
