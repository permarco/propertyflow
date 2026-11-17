document.querySelectorAll('[data-case-action]').forEach(form => {
    const memo = form.dataset.caseAction === 'memo';
    const assignment = form.dataset.caseAction === 'assignment';
    const inputs = [...form.querySelectorAll('textarea, select, input:not([type="hidden"])')];
    const button = form.querySelector('button[type="submit"], button:not([type])');
    const cancel = form.querySelector('[data-action-cancel]');
    const feedback = form.querySelector('[data-action-feedback]');
    const login = form.querySelector('[data-action-login]');
    const commandBase = form.dataset.commandBase;
    let actor = form.dataset.actor;
    let csrf;
    let operation;
    let submitting = false;
    let polling = false;
    let available = false;
    let editedVersion;
    let availabilityRequest;

    function message(text) {
        feedback.textContent = text;
    }

    function urgencyColor() {
        const select = form.elements.urgency;
        if (!select || select.tagName !== 'SELECT') return;
        select.classList.remove('urgency-0', 'urgency-1', 'urgency-2', 'urgency-3', 'urgency-4');
        select.classList.add(`urgency-${{ CRITICAL: 1, HIGH: 2, NORMAL: 3, LOW: 4 }[select.value] ?? 0}`);
    }

    function lockInputs(locked) {
        if (memo) {
            form.dataset.communicationSaving = String(locked);
            document.dispatchEvent(new Event('propertyflow-communication-lock'));
            syncMemoLock();
            return;
        }
        if (cancel) cancel.disabled = locked;
        inputs.forEach(input => {
            if (input.tagName === 'SELECT' || input.type === 'radio') input.disabled = locked;
            else input.readOnly = locked;
        });
    }

    function syncMemoLock() {
        if (!memo) return;
        const externalSaving = document.querySelector('#staff-message-form')?.dataset.communicationSaving === 'true';
        const locked = form.dataset.communicationSaving === 'true' || externalSaving;
        inputs.forEach(input => { input.readOnly = locked; });
        button.disabled = !available || submitting || polling || externalSaving;
    }

    if (memo) document.addEventListener('propertyflow-communication-lock', syncMemoLock);

    function availability() {
        if (!availabilityRequest) {
            availabilityRequest = checkAvailability().finally(() => { availabilityRequest = undefined; });
        }
        return availabilityRequest;
    }

    async function checkAvailability() {
        try {
            const response = await fetch(`${commandBase}/verfuegbar`, { cache: 'no-store' });
            if (!response.ok) {
                available = false;
                login.hidden = response.status !== 401;
                if (response.status === 401) message('Bitte melden Sie sich erneut an. Ihre Eingabe bleibt hier erhalten.');
                else if (response.status === 403) message('Sie haben keine Berechtigung für diese Aktion.');
                else message('Die Verfügbarkeit konnte nicht geprüft werden. Ihre Eingabe bleibt erhalten.');
                return false;
            }
            const data = await response.json();
            if (actor && actor !== data.actor) {
                available = false;
                message('Das angemeldete Konto hat gewechselt. Bitte öffnen Sie den Fall neu. Ihre Eingabe wird nicht übertragen.');
                return false;
            }
            actor = data.actor;
            csrf = data.csrf;
            available = data.available;
            login.hidden = true;
            if (!document.querySelector('#case-head')?.dataset.version) {
                document.dispatchEvent(new Event('propertyflow-case-changed'));
            }
            if (!operation && !form.dataset.dirty) message(available ? '' : 'Änderungen sind erst bei laufender Fallbearbeitung verfügbar.');
            return available;
        } catch {
            available = false;
            message('Die Verbindung ist unterbrochen. Ihre Eingabe bleibt erhalten.');
            return false;
        } finally {
            button.disabled = !available || submitting || polling;
            syncMemoLock();
        }
    }

    function accepted(result) {
        if (result.state === 'APPLIED') {
            form.querySelectorAll('textarea').forEach(input => { input.value = ''; });
            operation = undefined;
            editedVersion = undefined;
            delete form.dataset.dirty;
            lockInputs(false);
            message(memo ? 'Internes Memo gespeichert.' : assignment ? 'Zuordnung bestätigt.' : 'Dringlichkeit gespeichert.');
            const editor = form.closest('details');
            if (editor) editor.open = false;
            document.dispatchEvent(new Event('propertyflow-case-changed'));
            return true;
        }
        if (result.state === 'REJECTED') {
            operation = undefined;
            lockInputs(false);
            message('Konnte nicht erfolgreich gespeichert werden. Bitte prüfen Sie den aktuellen Fallstand und Ihre Berechtigung. Ihre Eingabe bleibt erhalten.');
            document.dispatchEvent(new Event('propertyflow-case-changed'));
            return true;
        }
        return false;
    }

    async function poll() {
        if (!operation || polling || submitting) return;
        polling = true;
        button.disabled = true;
        try {
            const response = await fetch(`${commandBase}/${operation.id}`, { cache: 'no-store' });
            if (response.ok) accepted(await response.json());
            else if (response.status === 401 || response.status === 403) {
                await availability();
            }
        } catch {
            message('Noch keine Speicherbestätigung. Ihre Eingabe und der Auftrag bleiben erhalten.');
        } finally {
            polling = false;
            button.disabled = !available;
            syncMemoLock();
        }
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (submitting || polling || (memo && (!form.elements.text.value.trim()
            || document.querySelector('#staff-message-form')?.dataset.communicationSaving === 'true'))) return;
        submitting = true;
        button.disabled = true;
        if (memo) lockInputs(true);
        try {
            if (!await availability()) return;
            if (!operation) {
                const version = document.querySelector('#case-head')?.dataset.version;
                if (!version) return;
                if (editedVersion !== undefined && editedVersion !== version) {
                    editedVersion = version;
                    message('Der Fall hat sich geändert. Bitte prüfen Sie den aktuellen Stand und bestätigen Sie das Speichern erneut.');
                    return;
                }
                operation = { id: crypto.randomUUID(), version, fields: Object.fromEntries(new FormData(form)) };
            }
            lockInputs(true);
            const response = await fetch(form.action, {
                method: 'POST',
                body: new URLSearchParams({ ...operation.fields,
                    ...(!memo ? { version: operation.version } : {}),
                    ...(!memo && !assignment ? { urgency: { CRITICAL: 'Kritisch', HIGH: 'Hoch', NORMAL: 'Normal', LOW: 'Niedrig' }[operation.fields.urgency] } : {}),
                    operation: operation.id, csrf }),
                headers: { 'X-PropertyFlow-Request': 'partial' },
            });
            if (response.ok) {
                if (!accepted({ state: memo ? 'APPLIED' : 'PENDING' })) message('Speicherung angefordert. Die Bestätigung wird abgewartet.');
                document.dispatchEvent(new Event('propertyflow-case-changed'));
            } else if (response.status === 400 || response.status === 409) {
                operation = undefined;
                lockInputs(false);
                message('Konnte nicht erfolgreich gespeichert werden. Bitte prüfen Sie den aktualisierten Fallstand und versuchen Sie es erneut.');
                document.dispatchEvent(new Event('propertyflow-case-changed'));
            } else {
                message('Noch keine Speicherbestätigung. Erneutes Speichern verwendet denselben Auftrag. Ihre Eingabe bleibt erhalten.');
                if (response.status === 401 || response.status === 403) await availability();
            }
        } catch {
            message('Noch keine Speicherbestätigung. Erneutes Speichern verwendet denselben Auftrag. Ihre Eingabe bleibt erhalten.');
        } finally {
            submitting = false;
            button.disabled = !available;
            if (memo) lockInputs(Boolean(operation));
        }
    });

    form.addEventListener('input', () => {
        form.dataset.dirty = 'true';
        if (editedVersion === undefined) editedVersion = document.querySelector('#case-head')?.dataset.version;
        urgencyColor();
    });

    document.addEventListener('propertyflow-access-lost', () => {
        available = false;
        button.disabled = true;
        void availability();
    });

    if (assignment) {
        const editor = form.closest('details');
        let suggestionsRequest;
        let suggestionsTimer;
        const resetAssignment = () => {
            const saved = document.querySelector('#case-data')?.dataset;
            if (saved) form.elements.assignment.value = saved.assignedUnit || saved.originalUnit || '';
        };
        const suggestions = async () => {
            suggestionsRequest?.abort();
            const request = new AbortController();
            suggestionsRequest = request;
            try {
                const response = await fetch(`${form.action}/vorschlaege?q=${encodeURIComponent(form.elements.assignment.value.trim())}`,
                    { cache: 'no-store', signal: request.signal });
                if (!response.ok || request.signal.aborted) return;
                const values = await response.json();
                if (request.signal.aborted) return;
                form.querySelector('datalist').replaceChildren(...values.map(value => {
                    const option = document.createElement('option');
                    option.value = value;
                    return option;
                }));
            } catch {
                // Suggestions are optional; a permitted free-text assignment remains usable.
            }
        };
        editor.addEventListener('toggle', () => {
            if (!editor.open || operation) return;
            if (!form.dataset.dirty) {
                resetAssignment();
                editedVersion = document.querySelector('#case-head')?.dataset.version;
            }
            form.dataset.dirty = 'true';
            void suggestions();
        });
        form.elements.assignment.addEventListener('input', () => {
            clearTimeout(suggestionsTimer);
            suggestionsTimer = setTimeout(suggestions, 300);
        });
        cancel.addEventListener('click', () => {
            if (operation || submitting || polling) return;
            resetAssignment();
            editedVersion = undefined;
            delete form.dataset.dirty;
            editor.open = false;
            message('');
        });
    }

    document.addEventListener('propertyflow-case-refreshed', () => {
        if (operation || form.dataset.dirty || !form.elements.urgency) return;
        const saved = document.querySelector('#case-urgency')?.dataset.value;
        if (saved !== undefined) form.elements.urgency.value = saved;
        urgencyColor();
    });
    setInterval(() => {
        if (document.hidden || submitting || polling) return;
        if (operation) void poll();
    }, 5000);
    setInterval(() => {
        if (!document.hidden && !submitting && !polling && !operation) void availability();
    }, 20000);
    void availability();
});
