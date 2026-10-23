import { MockStream } from './mock-stream.mjs';

const $ = (selector, root = document) => root.querySelector(selector);
const labels = { idle: 'Bereit', waiting: 'Wartet …', streaming: 'Wird formuliert …',
    completed: 'Vollständig', aborted: 'Abgebrochen', error: 'Die Nachricht konnte nicht formuliert werden.' };
const parse = html => new DOMParser().parseFromString(html, 'text/html');
const timestamp = () => new Intl.DateTimeFormat('de-CH', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Europe/Zurich' }).format(new Date());
let saveNoticeTimer;
function showSaveNotice(text, error = false, temporary = false) {
    clearTimeout(saveNoticeTimer);
    const notice = $('#save-state');
    if (!notice) return;
    notice.textContent = text;
    notice.classList.toggle('error', error);
    if (temporary) saveNoticeTimer = setTimeout(() => { notice.textContent = ''; }, 5000);
}
let accessDenied = false;
function addRetry(notice, retry) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'secondary';
    button.textContent = 'Erneut versuchen';
    button.addEventListener('click', () => {
        if (saving || reading || accessDenied) return;
        button.disabled = true;
        retry();
    });
    notice.append(' ', button);
}
function denyAccess() {
    accessDenied = true;
    clearTimeout(saveNoticeTimer);
    clearTimeout(debounce);
    debounce = null;
    readController?.abort();
    stream?.dispose();
    for (const selector of ['#case-head', '#history', '#case-sidebar', '#ai', '.footer-note']) {
        const region = $(selector);
        if (region) region.hidden = true;
    }
    $('#updates')?.close();
    $('#results')?.replaceChildren();
    const notice = $('#load-state') || $('#save-state');
    notice.textContent = 'Zugriff verweigert. Automatische Aktualisierungen und Speichern sind gestoppt. Ungesendete Eingaben bleiben erhalten.';
    notice.classList.add('error');
    const reload = document.createElement('a');
    reload.href = location.href;
    reload.textContent = 'Seite neu laden';
    notice.append(' ', reload);
    syncComposer();
}
let saving = false;
let closed = $('#case-head')?.dataset.closed === 'true';
const message = $('#message');
const internal = $('#internal');
const answer = $('#answer');
const stream = $('#ai') ? new MockStream(({ state, text }) => {
    $('#ai').hidden = state === 'idle';
    $('#ai-status').textContent = labels[state];
    $('#ai-status').dataset.state = state;
    $('#ai-output').textContent = text; // Never interpret untrusted generated text as HTML.
    $('#abort').hidden = !['waiting', 'streaming'].includes(state);
    syncComposer();
    sizeComposer();
}) : null;

function syncComposer() {
    if (!message) return;
    if (accessDenied) {
        message.disabled = false;
        message.readOnly = true;
        $('#send').disabled = true;
        for (const selector of ['#internal', '#answer', '#generate', '#adopt', '#discard-proposal', '#abort']) {
            const control = $(selector);
            if (control) control.disabled = true;
        }
        $('#composer-fields').hidden = !message.value;
        return;
    }
    // A confirmed closure discards the tenant's unsent text.
    if (closed && !internal) message.value = '';
    const running = stream && ['waiting', 'streaming'].includes(stream.state);
    message.disabled = saving || closed;
    $('#send').disabled = saving || closed || !message.value.trim();
    $('#send').textContent = saving ? 'Wird gespeichert …' : internal?.checked ? 'Memo speichern' : 'Nachricht senden';
    if (internal) {
        internal.disabled = saving || closed;
        if (internal.checked) answer.checked = false;
        answer.disabled = saving || closed || internal.checked;
        $('#generate').disabled = saving || closed || running || internal.checked || !message.value.trim();
        $('#discard-proposal').disabled = saving || running || !stream.text;
        $('#adopt').disabled = saving || closed || internal.checked || stream.state !== 'completed';
        $('#message-label').textContent = internal.checked ? 'Interne Nachricht – nur für Mitarbeitende' : 'Externe Nachricht an den Mieter';
    }
    $('#closed-notice').hidden = !closed;
    // Staff drafts remain visible but locked; tenant drafts were cleared above.
    $('#composer-fields').hidden = closed && !message.value;
}
function sizeComposer() {
    if (!message) return;
    if (innerWidth < 1200) {
        message.style.height = 'auto';
        message.style.height = Math.min(240, Math.max(96, message.scrollHeight)) + 'px';
    } else {
        const conversation = $('.conversation');
        const composer = $('#composer');
        // Include overflowing content so the textarea still yields space to AI actions.
        const extra = composer.scrollHeight + composer.offsetHeight - composer.clientHeight - message.offsetHeight;
        const max = Math.max(96, conversation.clientHeight - 216 - extra);
        message.style.maxHeight = max + 'px';
        if (message.offsetHeight > max) message.style.height = max + 'px';
    }
}
message?.addEventListener('input', () => { syncComposer(); sizeComposer(); });
internal?.addEventListener('change', syncComposer);
$('#generate')?.addEventListener('click', () => stream.start(message.value, $('#mock-mode').value));
$('#abort')?.addEventListener('click', () => stream.abort());
$('#discard-proposal')?.addEventListener('click', () => {
    if ($('#discard-proposal').disabled) return;
    stream.dispose();
    stream.text = '';
    stream.publish('idle');
    message.focus();
});
$('#adopt')?.addEventListener('click', () => {
    if ($('#adopt').disabled) return;
    message.value = stream.text;
    stream.text = '';
    stream.publish('idle');
    syncComposer(); sizeComposer(); message.focus();
});
window.addEventListener('resize', sizeComposer);
window.addEventListener('pagehide', () => stream?.dispose());
window.addEventListener('beforeunload', event => {
    if (internal && message?.value.trim() && !saving) { event.preventDefault(); event.returnValue = ''; }
});
syncComposer(); sizeComposer();
if (message && typeof ResizeObserver !== 'undefined') {
    new ResizeObserver(sizeComposer).observe($('#composer'));
}
$('#form-error')?.focus();

// Polls are serialized, paused in hidden tabs, and do not modify the composer or stream.
let reading = false;
let readController;
let lastRead = timestamp();
let pendingSidebar = null;
async function readPage(url, signal) {
    const response = await fetch(url, { signal, headers: { Accept: 'text/html' }, cache: 'no-store' });
    const page = parse(await response.text());
    if (!response.ok) throw Object.assign(new Error($('.error, [role=alert]', page)?.textContent || 'Aktualisierung momentan nicht möglich.'), { status: response.status });
    return page;
}
function replaceRegion(selector, page) {
    const current = $(selector), next = $(selector, page);
    if (current && next) current.replaceWith(next.cloneNode(true));
}
function applyCase(page, notify = false, forceSidebar = false) {
    const oldHead = $('#case-head'), nextHead = $('#case-head', page);
    if (!nextHead) throw new Error('Die Fallansicht konnte nicht geladen werden.');
    const changed = oldHead.dataset.version !== nextHead.dataset.version;
    if (!changed && !forceSidebar) return;
    const changes = [];
    for (const id of ['case-status', 'case-urgency', 'case-followup']) {
        const old = $('#' + id)?.textContent, next = $('#' + id, page)?.textContent;
        if (old && next && old !== next) changes.push(old + ' → ' + next);
    }
    const history = $('#history');
    const atBottom = history.scrollHeight - history.scrollTop - history.clientHeight < 40;
    const scrollTop = history.scrollTop;
    replaceRegion('#case-head', page);
    if (changed) {
        replaceRegion('#history', page);
        $('#history').scrollTop = atBottom ? $('#history').scrollHeight : scrollTop;
    }
    if (forceSidebar || !$('#case-sidebar details[open]')) {
        replaceRegion('#case-sidebar', page);
        pendingSidebar = null;
    } else {
        // Keep the newest confirmed sidebar while the open editor retains its original version.
        pendingSidebar = $('#case-sidebar', page)?.cloneNode(true) || null;
    }
    // Open edit forms retain their original version, so concurrent changes produce a conflict.
    closed = nextHead.dataset.closed === 'true';
    syncComposer();
    if (notify && changed && $('#case-sidebar')) {
        $('#update-text').textContent = changes.length ? changes.join('\n') : 'Neue Daten im Verlauf.';
        if (!$('#updates').open) $('#updates').showModal();
    }
    sizeComposer();
}
document.addEventListener('click', event => {
    const button = event.target.closest('.cancel-edit');
    if (button) {
        const details = button.closest('details');
        $('form', details)?.reset();
        details.open = false;
        if (pendingSidebar && details.closest('#case-sidebar')) {
            $('#case-sidebar').replaceWith(pendingSidebar);
            pendingSidebar = null;
        }
    }
});
document.addEventListener('submit', async event => {
    const form = event.target;
    if (!form.matches('#message-form, .case-action')) return;
    event.preventDefault();
    if (saving || accessDenied) return;
    const data = new URLSearchParams(new FormData(form));
    if (event.submitter?.name) data.set(event.submitter.name, event.submitter.value);
    const isMessage = form.id === 'message-form';
    saving = true;
    readController?.abort();
    const buttons = [...form.querySelectorAll('button')];
    buttons.forEach(b => b.disabled = true);
    syncComposer();
    showSaveNotice('Wird gespeichert …');
    try {
        const response = await fetch(form.getAttribute('action'), { method: 'POST', body: data, headers: { Accept: 'text/html' } });
        const page = parse(await response.text());
        if (!response.ok) {
            if ($('#case-head', page)) applyCase(page, false, response.status === 409);
            throw new Error($('#save-state, [role=alert]', page)?.textContent || 'Konnte nicht erfolgreich gespeichert werden.');
        }
        if (isMessage) {
            message.value = '';
            if (internal) { internal.checked = false; answer.checked = false; }
            $('input[name=operation]', form).value = crypto.randomUUID();
        }
        applyCase(page, false, true);
        showSaveNotice(isMessage ? 'Im Demo-Arbeitsspeicher gespeichert.' : 'Änderung gespeichert.', false, true);
        if (isMessage) $('#history').scrollTop = $('#history').scrollHeight;
    } catch (error) {
        showSaveNotice(error.message || 'Speichern momentan nicht möglich. Ihre Eingabe bleibt erhalten.', true);
    } finally {
        saving = false;
        buttons.forEach(b => b.disabled = false);
        syncComposer(); sizeComposer();
    }
});
$('#intake-form')?.addEventListener('submit', event => {
    if ([...event.target.querySelectorAll('[required]')].some(field => !field.value.trim())) {
        event.preventDefault(); return;
    }
    const button = $('button', event.target);
    button.disabled = true; button.textContent = 'Wird gespeichert …';
});

const filters = $('#filters');
let debounce;
let generation = 0;
let listUrl = new URL(location.href);
function filterUrl() {
    const url = new URL(filters.action);
    url.search = new URLSearchParams(new FormData(filters)).toString();
    return url;
}
function invalidate() {
    generation++;
    readController?.abort();
    clearTimeout(debounce);
    debounce = null;
}
function captureListPosition() {
    const container = $('#results .table-card');
    if (!container) return null;
    const top = container.getBoundingClientRect().top;
    const bottom = container.getBoundingClientRect().bottom;
    const heading = $('thead', container);
    const visibleTop = heading?.getClientRects().length ? Math.max(top, heading.getBoundingClientRect().bottom) : top;
    const row = [...container.querySelectorAll('tr[data-case]')].find(row => {
        const bounds = row.getBoundingClientRect();
        return bounds.bottom > visibleTop && bounds.top < bottom;
    });
    return { scrollTop: container.scrollTop, scrollLeft: container.scrollLeft,
        caseId: row?.dataset.case, offset: row ? row.getBoundingClientRect().top - top : 0 };
}
function restoreListPosition(position) {
    const container = $('#results .table-card');
    if (!position || !container) return;
    container.scrollTop = position.scrollTop;
    container.scrollLeft = position.scrollLeft;
    const row = [...container.querySelectorAll('tr[data-case]')].find(row => row.dataset.case === position.caseId);
    if (row) container.scrollTop += row.getBoundingClientRect().top - container.getBoundingClientRect().top - position.offset;
}
async function loadList(url, updateUrl = true, preservePosition = false) {
    if (accessDenied) return;
    const mine = ++generation;
    readController?.abort();
    readController = new AbortController();
    reading = true;
    $('#load-state').textContent = 'Wird aktualisiert …';
    $('#results').setAttribute('aria-busy', 'true');
    try {
        const page = await readPage(url, readController.signal);
        if (mine !== generation) return;
        if (!$('#results', page)) throw new Error('Die Fallübersicht konnte nicht geladen werden.');
        // Capture immediately before replacement: the user may scroll or move focus during the request.
        const active = document.activeElement;
        const hadResultFocus = $('#results').contains(active);
        const focusHref = active?.closest('#results a')?.getAttribute('href');
        const focusCase = active?.closest('tr[data-case]')?.dataset.case;
        const position = preservePosition ? captureListPosition() : null;
        const requestedPage = Number(new URL(url).searchParams.get('page') || 1);
        const actualPage = Number($('#results', page).dataset.page);
        replaceRegion('#results', page);
        restoreListPosition(position);
        listUrl = new URL(url);
        listUrl.searchParams.set('page', $('#results').dataset.page);
        if (updateUrl) history.replaceState(null, '', listUrl);
        lastRead = timestamp();
        let notice = 'Zuletzt aktualisiert am ' + lastRead;
        if (requestedPage !== actualPage) {
            notice = 'Die bisherige Seite ist nicht mehr verfügbar. Seite ' + actualPage + ' wird angezeigt.';
        }
        if (hadResultFocus) {
            const row = focusCase ? [...document.querySelectorAll('#results tr[data-case]')].find(row => row.dataset.case === focusCase) : null;
            const link = focusCase ? row?.querySelector('.case-link')
                : [...document.querySelectorAll('#results a')].find(a => a.getAttribute('href') === focusHref);
            (link || $('#results')).focus({ preventScroll: true });
            if (focusCase && !row) notice += ' Der zuvor fokussierte Fall ist nicht mehr in dieser Ansicht.';
        }
        $('#load-state').textContent = notice;
    } catch (error) {
        if (error.name !== 'AbortError' && mine === generation) {
            if (error.status === 403 || error.status === 401) {
                denyAccess();
            } else {
                $('#load-state').textContent = 'Aktualisierung momentan nicht möglich. Stand: ' + lastRead;
                addRetry($('#load-state'), () => loadList(url, updateUrl, preservePosition));
            }
        }
    } finally {
        if (mine === generation) { reading = false; $('#results').removeAttribute('aria-busy'); }
    }
}
filters?.addEventListener('input', event => {
    if (accessDenied || event.target.id !== 'q') return;
    invalidate();
    $('#load-state').textContent = 'Suche wird aktualisiert …';
    debounce = setTimeout(() => { debounce = null; loadList(filterUrl()); }, 1000);
});
filters?.addEventListener('change', event => {
    if (accessDenied || event.target.id === 'q') return;
    invalidate(); loadList(filterUrl());
});
filters?.addEventListener('submit', event => { event.preventDefault(); if (accessDenied) return; invalidate(); loadList(filterUrl()); });
document.addEventListener('click', event => {
    if (accessDenied || !filters || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const link = event.target.closest('#results th a, #results .pagination a, #reset');
    if (!link) return;
    event.preventDefault(); invalidate();
    const url = new URL(link.href);
    if (link.id === 'reset') {
        $('#q').value = ''; $('#urgency').value = ''; $('#closed').checked = false;
        $('#sort').value = 'default'; $('#desc').value = 'false';
    } else {
        $('#sort').value = url.searchParams.get('sort') || 'default';
        $('#desc').value = url.searchParams.get('desc') || 'false';
    }
    loadList(url);
});
async function refresh() {
    if (accessDenied || document.hidden || saving || reading || debounce) return;
    if (filters) { await loadList(listUrl, true, true); return; }
    if (!$('#case-head')) return;
    reading = true;
    readController = new AbortController();
    try {
        const page = await readPage(location.href, readController.signal);
        if (!saving) applyCase(page, true);
        lastRead = timestamp();
        if ($('#save-state button[data-read-retry]')) showSaveNotice('');
    } catch (error) {
        if (error.name !== 'AbortError') {
            if (error.status === 403 || error.status === 401) denyAccess();
            else {
                showSaveNotice('Aktualisierung momentan nicht möglich. Stand: ' + lastRead, true);
                addRetry($('#save-state'), refresh);
                $('#save-state button').dataset.readRetry = 'true';
            }
        }
    } finally { reading = false; }
}
setInterval(refresh, 20000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
if ($('#history')) $('#history').scrollTop = $('#history').scrollHeight;
