(() => {
    const form = document.querySelector('#staff-message-form');
    if (!form) return;
    const text = form.elements.text;
    const answer = form.elements.answer;
    const button = form.querySelector('[type="submit"]');
    const feedback = form.querySelector(':scope > [role="status"]');
    const login = form.querySelector('[data-message-login]');
    let actor = form.dataset.actor;
    let csrf;
    let capability = { information: false, question: false };
    let operation;
    let busy = false;
    let editedVersion;

    function controls() {
        const saving = String(busy || Boolean(operation));
        const changed = form.dataset.communicationSaving !== saving;
        form.dataset.communicationSaving = saving;
        const memoSaving = document.querySelector('#memo-form')?.dataset.communicationSaving === 'true';
        const waitingForTenant = document.querySelector('#case-status')?.textContent.trim() === 'Wartet auf Mieterantwort';
        if (waitingForTenant && !operation) answer.checked = false;
        answer.title = waitingForTenant ? 'Eine Mieterantwort wird bereits erwartet.' : '';
        button.disabled = busy || memoSaving || !capability.information;
        text.readOnly = accessLost || busy || memoSaving || Boolean(operation);
        answer.disabled = Boolean(document.querySelector('#internal-message-toggle')?.checked)
            || waitingForTenant || busy || memoSaving || Boolean(operation) || !capability.question && !answer.checked;
        if (changed) document.dispatchEvent(new Event('propertyflow-communication-lock'));
    }

    let accessLost = false;
    document.addEventListener('propertyflow-composer-mode-changed', controls);
    document.addEventListener('propertyflow-case-refreshed', controls);
    async function availability() {
        if (accessLost) return false;
        try {
            const response = await fetch(`${form.dataset.messageBase}/verfuegbar`, { cache: 'no-store' });
            if (!response.ok) {
                if (response.status === 401 || response.status === 403) accessLost = true;
                capability = { information: false, question: false };
                login.hidden = response.status !== 401;
                feedback.textContent = response.status === 401
                    ? 'Bitte melden Sie sich erneut an. Ihre Eingabe bleibt hier erhalten.'
                    : 'Eine Veröffentlichung ist derzeit nicht verfügbar. Ihre Eingabe bleibt erhalten.';
                return false;
            }
            const current = await response.json();
            if (actor && current.actor !== actor) {
                capability = { information: false, question: false };
                feedback.textContent = 'Das angemeldete Konto hat gewechselt. Bitte öffnen Sie den Fall neu. Ihre Eingabe wird nicht übertragen.';
                return false;
            }
            actor = current.actor;
            csrf = current.csrf;
            capability = current.capability;
            login.hidden = true;
            if (!form.dataset.dirty && !operation) feedback.textContent = capability.information ? '' : 'Eine Veröffentlichung ist derzeit nicht verfügbar.';
            return capability.information;
        } catch {
            capability = { information: false, question: false };
            feedback.textContent = 'Die Verbindung ist unterbrochen. Ihre Eingabe bleibt erhalten.';
            return false;
        } finally {
            controls();
        }
    }

    function published() {
        text.value = '';
        answer.checked = false;
        operation = undefined;
        editedVersion = undefined;
        delete form.dataset.dirty;
        feedback.textContent = 'Nachricht veröffentlicht. Die E-Mail-Benachrichtigung folgt.';
        controls();
        document.dispatchEvent(new Event('propertyflow-case-changed'));
    }

    function rejected() {
        operation = undefined;
        feedback.textContent = 'Die Nachricht wurde nicht veröffentlicht. Bitte prüfen Sie den aktuellen Fallstand. Ihre Eingabe bleibt erhalten.';
        controls();
        document.dispatchEvent(new Event('propertyflow-case-changed'));
    }

    async function result() {
        if (!operation || busy || accessLost) return;
        busy = true;
        controls();
        try {
            const response = await fetch(`${form.dataset.messageBase}/auftraege/${encodeURIComponent(operation.id)}`, { cache: 'no-store' });
            if (response.ok && (await response.json()).state === 'PUBLISHED') published();
            else if (response.status === 409 || response.status === 400) rejected();
            else if (response.status === 401 || response.status === 403) await availability();
        } catch {
            // A failed read cannot establish whether publication succeeded.
        } finally {
            busy = false;
            controls();
        }
    }

    form.addEventListener('input', () => {
        form.dataset.dirty = 'true';
        if (editedVersion === undefined) editedVersion = document.querySelector('#case-head')?.dataset.version;
    });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (busy || document.querySelector('#memo-form')?.dataset.communicationSaving === 'true' || !text.value.trim()) return;
        busy = true;
        controls();
        try {
            if (!await availability()) return;
            if (!operation) {
                const version = document.querySelector('#case-head')?.dataset.version;
                if (editedVersion !== undefined && version !== editedVersion) {
                    editedVersion = version;
                    feedback.textContent = 'Der Fall hat sich geändert. Bitte prüfen Sie den aktuellen Stand und bestätigen Sie das Senden erneut.';
                    return;
                }
                if (answer.checked && !capability.question) {
                    feedback.textContent = 'Eine Rückfrage mit Antwortbedarf ist in der aktuellen Bearbeitungsphase nicht freigegeben.';
                    return;
                }
                operation = { id: crypto.randomUUID(), text: text.value, answer: answer.checked };
            }
            controls();
            const response = await fetch(form.action, { method: 'POST', body: new URLSearchParams({
                operation: operation.id, text: operation.text, answer: String(operation.answer), csrf,
            }) });
            if (response.ok) published();
            else if (response.status === 400 || response.status === 409) rejected();
            else {
                feedback.textContent = 'Die Veröffentlichung konnte nicht bestätigt werden. Text und Antwortbedarf bleiben erhalten. Erneutes Senden verwendet denselben Auftrag.';
                if (response.status === 401 || response.status === 403) await availability();
            }
        } catch {
            feedback.textContent = 'Die Veröffentlichung konnte nicht bestätigt werden. Text und Antwortbedarf bleiben erhalten. Erneutes Senden verwendet denselben Auftrag.';
        } finally {
            busy = false;
            controls();
        }
    });
    document.addEventListener('propertyflow-access-lost', () => { void availability(); });
    document.addEventListener('propertyflow-communication-lock', controls);
    setInterval(() => {
        if (document.hidden || busy || accessLost) return;
        if (operation) void result();
    }, 5000);
    setInterval(() => {
        if (!document.hidden && !busy && !accessLost && !operation) void availability();
    }, 20000);
    void availability();
})();
