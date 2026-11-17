(() => {
    const form = document.querySelector('#tenant-message-form');
    if (!form) return;
    const panel = document.querySelector('#tenant-message-panel');
    const text = form.elements.text;
    const submit = form.querySelector('[type="submit"]');
    const feedback = form.querySelector('[role="status"]');
    const sendUrl = new URL(location.href);
    sendUrl.pathname = sendUrl.pathname.replace(/\/$/, '') + '/nachrichten';
    sendUrl.search = '';
    sendUrl.hash = '';
    let operation = crypto.randomUUID();
    let pending = false;
    let busy = false;
    let reading = false;
    let accessLost = false;

    function denyAccess(status) {
        if (![401, 403, 404].includes(status)) return false;
        accessLost = true;
        submit.disabled = true;
        text.readOnly = true;
        document.querySelector('#case-head')?.replaceChildren();
        document.querySelector('#history')?.replaceChildren();
        feedback.textContent = 'Der Zugriff ist nicht mehr gültig. Ihre Eingabe bleibt erhalten. Bitte melden Sie sich erneut an oder öffnen Sie Ihren Fall-Link neu.';
        return true;
    }

    async function refresh() {
        if (reading || accessLost) return;
        reading = true;
        try {
            const response = await fetch(location.href, { cache: 'no-store', headers: { 'X-PropertyFlow-Request': 'partial' } });
            if (denyAccess(response.status) || !response.ok) return;
            const page = new DOMParser().parseFromString(await response.text(), 'text/html');
            const head = page.querySelector('#case-head');
            const history = page.querySelector('#history');
            const nextForm = page.querySelector('#tenant-message-form');
            if (!head || !history || !nextForm || nextForm.getAttribute('action') !== form.getAttribute('action')) return;
            const scroll = document.querySelector('#history').scrollTop;
            document.querySelector('#case-head').replaceWith(head);
            document.querySelector('#history').replaceWith(history);
            history.scrollTop = scroll;
            document.dispatchEvent(new Event('propertyflow-case-refreshed'));
            form.dataset.csrf = nextForm.dataset.csrf;
            const closed = head.dataset.closed === 'true';
            panel.hidden = closed;
            if (closed) {
                text.value = '';
                pending = false;
                operation = crypto.randomUUID();
                text.readOnly = false;
            }
        } catch {
            // A network failure is never evidence of closure and must not erase the draft.
        } finally {
            reading = false;
        }
    }

    text.addEventListener('input', () => {
        if (!pending) operation = crypto.randomUUID();
    });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (busy || panel.hidden || accessLost) return;
        const value = text.value.replace(/\r\n?/g, '\n');
        if (!value.trim() || value.length > 2000) {
            feedback.textContent = 'Bitte geben Sie eine Nachricht mit höchstens 2’000 Zeichen ein.';
            return;
        }
        busy = true;
        pending = true;
        submit.disabled = true;
        text.readOnly = true;
        try {
            const response = await fetch(sendUrl, { method: 'POST', body: new URLSearchParams({
                operation, text: value, csrf: form.dataset.csrf,
            }) });
            if (denyAccess(response.status)) return;
            if (response.ok) {
                text.value = '';
                pending = false;
                operation = crypto.randomUUID();
                feedback.textContent = 'Ihre Nachricht wurde gespeichert und zur Verarbeitung übergeben.';
            } else if (response.status === 400) {
                pending = false;
                feedback.textContent = 'Die Nachricht konnte nicht angenommen werden. Bitte prüfen Sie den Inhalt und die Grenze von 2’000 Zeichen.';
            } else {
                feedback.textContent = 'Die Übergabe konnte nicht bestätigt werden. Ihr Text bleibt erhalten; erneutes Senden wiederholt denselben Auftrag.';
            }
        } catch {
            feedback.textContent = 'Die Übergabe konnte nicht bestätigt werden. Ihr Text bleibt erhalten; erneutes Senden wiederholt denselben Auftrag.';
        } finally {
            busy = false;
            submit.disabled = accessLost;
            text.readOnly = pending || accessLost;
            await refresh();
        }
    });
    setInterval(() => { if (!document.hidden && !busy) void refresh(); }, 20000);
})();
