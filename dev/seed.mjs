import assert from 'node:assert/strict';
import { readFile, writeFile, rename } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

// Synthetische Beispiele durchlaufen dieselben HTTP-Verträge wie die Oberfläche.
const base = 'http://127.0.0.1:8081';
const mail = 'http://127.0.0.1:8025';
const manifestPath = '.propertyflow-local/dev-cases.json';
let manifest;
try { manifest = JSON.parse(await readFile(manifestPath, 'utf8')); }
catch (error) { if (error.code !== 'ENOENT') throw error; manifest = { cases: [] }; }
const save = async () => {
    await writeFile(`${manifestPath}.tmp`, JSON.stringify(manifest, null, 2), 'utf8');
    await rename(`${manifestPath}.tmp`, manifestPath);
};

class Session {
    cookie = '';
    async request(path, fields) {
        const response = await fetch(new URL(path, base), {
            method: fields ? 'POST' : 'GET', redirect: 'manual',
            headers: { cookie: this.cookie, ...(fields ? { 'content-type': 'application/x-www-form-urlencoded' } : {}) },
            body: fields ? new URLSearchParams(fields) : undefined,
            signal: AbortSignal.timeout(15000),
        });
        const cookie = response.headers.getSetCookie().find(value => value.startsWith('JSESSIONID='));
        if (cookie) this.cookie = cookie.split(';')[0];
        return { status: response.status, location: response.headers.get('location'), text: await response.text() };
    }
    async json(path) {
        const response = await this.request(path);
        assert.equal(response.status, 200, `GET ${path}: HTTP ${response.status}`);
        return JSON.parse(response.text);
    }
}
const field = (html, name) => {
    const input = html.match(new RegExp(`<input\\b[^>]*name="${name}"[^>]*>`))?.[0];
    const value = input?.match(/value="([^"]*)"/)?.[1];
    assert.ok(value, `Formularfeld fehlt: ${name}`);
    return value;
};
async function until(operation, description) {
    for (let attempt = 0; attempt < 120; attempt++) {
        const result = await operation();
        if (result) return result;
        await delay(1000);
    }
    throw new Error(`Zeitüberschreitung: ${description}`);
}
const staff = new Session();
async function login() {
    const config = await readFile('.propertyflow-local/staff-bootstrap.properties', 'utf8');
    const email = config.match(/^propertyflow\.staff\.bootstrap\.email=(.+)$/m)?.[1].trim();
    assert.ok(email, 'Lokale Mitarbeiteradresse fehlt.');
    const previous = new Set((await (await fetch(`${mail}/api/v1/messages`)).json()).messages.map(item => item.ID));
    const form = await staff.request('/anmelden');
    const csrf = field(form.text, 'csrf');
    const requested = await staff.request('/anmelden/code', { email, csrf });
    assert.equal(requested.status, 200, 'Anmeldecode konnte nicht angefordert werden.');
    const code = await until(async () => {
        const inbox = await (await fetch(`${mail}/api/v1/messages`)).json();
        const message = inbox.messages.find(item => !previous.has(item.ID)
            && item.Subject === 'Ihr Einmalcode für PropertyFlow' && item.To.some(to => to.Address === email));
        if (!message) return null;
        const detail = await (await fetch(`${mail}/api/v1/message/${message.ID}`)).json();
        return detail.Text.match(/\b[0-9]{6}\b/)?.[0];
    }, 'Einmalcode in Mailpit');
    const verified = await staff.request('/anmelden/pruefen', { csrf, code });
    assert.equal(verified.status, 303, 'Mitarbeiteranmeldung fehlgeschlagen.');
}
const route = entry => `/mitarbeiter/faelle/${entry.id}`;
async function currentStatus(entry) {
    const page = await staff.request(route(entry));
    assert.equal(page.status, 200);
    return page.text.match(/id="case-status"[^>]*>([^<]+)</)?.[1].trim();
}
async function waitStatus(entry, expected) {
    await until(async () => (await currentStatus(entry)) === expected, expected);
    entry.status = expected;
    await save();
}
async function task(entry) {
    return until(async () => {
        const result = await staff.json(`${route(entry)}/pruefung`);
        return result.task.available ? result : null;
    }, 'Mitarbeiteraufgabe');
}
async function decide(entry, action, extra = {}) {
    const { task: selected, csrf } = await task(entry);
    const response = await staff.request(`${route(entry)}/${action}`, {
        operationId: randomUUID(), userTaskKey: selected.taskKey, version: selected.version,
        reviewToken: selected.reviewToken, csrf, ...extra,
    });
    assert.equal(response.status, 200, `Mitarbeiteraktion ${action}: HTTP ${response.status}`);
}
async function send(entry, text, answer = false) {
    const capability = await until(async () => {
        const current = await staff.json(`${route(entry)}/nachrichten/verfuegbar`);
        return current.capability[answer ? 'question' : 'information'] ? current : null;
    }, 'Nachrichtenfreigabe');
    const operation = randomUUID();
    const response = await staff.request(`${route(entry)}/externe-nachricht-veroeffentlichen`, {
        operation, text, answer: String(answer), csrf: capability.csrf,
    });
    // Ein langsamer Worker darf die Speicherung später bestätigen; niemals erneut senden.
    if (response.status !== 200) {
        await until(async () => (await staff.json(`${route(entry)}/nachrichten/auftraege/${operation}`)).state === 'PUBLISHED',
            'Nachrichtenveröffentlichung');
    }
}

const scenarios = [
    ['review', 'Heizung bleibt kalt', 'Mitarbeiterprüfung erforderlich'],
    ['progress', 'Fenstergriff klemmt', 'In Bearbeitung'],
    ['external', 'Offerte für Gegensprechanlage', 'Wartet auf externe Rückmeldung'],
    ['followup', 'Termin für Kellerbesichtigung', 'Wartet auf Wiedervorlage'],
    ['question', 'Geräusch an der Lüftung', 'Wartet auf Mieterantwort'],
    ['closed', 'Briefkastenschloss ersetzt', 'Abgeschlossen'],
    ['reopened', 'Türschliesser erneut auffällig', 'In Bearbeitung'],
];
const pending = scenarios.filter(([key]) => !manifest.cases.find(entry => entry.key === key)?.complete);
if (pending.length) await login();
for (const [key, title, finalStatus] of pending) {
    let entry = manifest.cases.find(item => item.key === key);
    if (!entry) {
        const tenant = new Session();
        const form = await tenant.request('/mieter/fall');
        const accepted = await tenant.request('/mieter/fall', {
            csrf: field(form.text, 'csrf'), operation: field(form.text, 'operation'),
            title: `[Dev] ${title}`, unit: 'Musterstrasse 12, Wohnung 3', email: `mieter.${key}@example.test`,
            description: `Synthetischer Testfall: ${title}. Bitte prüfen Sie das Anliegen.`,
        });
        assert.equal(accepted.status, 303, 'Fallannahme fehlgeschlagen.');
        const page = await tenant.request(accepted.location);
        const id = page.text.match(/class="eyebrow"[^>]*>([^<]+)</)?.[1].trim();
        assert.ok(id, 'Fall-ID fehlt.');
        entry = { key, id, title, tenantUrl: new URL(accepted.location, base).href, phase: 'created' };
        manifest.cases.push(entry);
        await save();
        console.log(`Fall angelegt: ${key} (${id})`);
    }
    if (entry.phase === 'created') {
        await waitStatus(entry, 'Mitarbeiterprüfung erforderlich');
        entry.phase = 'review';
        await save();
    }
    if (key !== 'review' && entry.phase === 'review') {
        // Ein bereits wirksamer Übergang wird bei einem erneuten Seed-Aufruf nur bestätigt.
        if ((await currentStatus(entry)) === 'Mitarbeiterprüfung erforderlich') await decide(entry, 'bearbeitung-fortsetzen');
        await waitStatus(entry, 'In Bearbeitung');
        entry.phase = 'progress';
        await save();
    }
    if (entry.phase === 'progress') {
        if (key === 'external') await decide(entry, 'externe-rueckmeldung-abwarten');
        if (key === 'followup') await decide(entry, 'wiedervorlage-setzen', { days: '7', reason: 'Besichtigung nächste Woche prüfen.' });
        if (key === 'question') await send(entry, 'Tritt das Geräusch auch bei ausgeschalteter Lüftung auf?', true);
        if (key === 'closed' || key === 'reopened') {
            await send(entry, key === 'closed'
                ? 'Das Briefkastenschloss wurde ersetzt und gemeinsam geprüft. Ihr Briefkasten lässt sich wieder öffnen. Wir schliessen das Anliegen ab.'
                : 'Der Türschliesser wurde eingestellt und funktioniert bei der Kontrolle wieder. Wir schliessen das Anliegen vorerst ab.');
            await decide(entry, 'abschliessen');
            await waitStatus(entry, 'Abgeschlossen');
        }
        entry.phase = 'action';
        await save();
    }
    if (key === 'reopened' && entry.phase === 'action') {
        const page = await staff.request(route(entry));
        const version = page.text.match(/id="case-head"[^>]*data-version="([0-9]+)"/)?.[1];
        const { csrf } = await staff.json(`${route(entry)}/aktionen/verfuegbar`);
        assert.ok(version, 'Fallversion fehlt.');
        const reopened = await staff.request(`${route(entry)}/wiedereroeffnen`, { csrf, version, operationId: randomUUID() });
        assert.equal(reopened.status, 202, 'Wiedereröffnung fehlgeschlagen.');
    }
    await waitStatus(entry, finalStatus);
    entry.complete = true;
    await save();
    console.log(`Beispiel bereit: ${key} – ${finalStatus}`);
}
console.log('Fall-Links stehen in .propertyflow-local/dev-cases.json; vorhandene abgeschlossene Seeds bleiben unverändert.');
