document.querySelectorAll('[data-direct-follow]').forEach(panel => {
    const open = panel.querySelector('[data-direct-follow-open]');
    const confirm = panel.querySelector('[data-direct-follow-confirm]');
    const cancel = panel.querySelector('[data-direct-follow-cancel]');
    const confirmation = panel.querySelector('[data-direct-follow-confirmation]');
    const days = panel.querySelector('[name=days]');
    const date = panel.querySelector('[name=date]');
    const reason = panel.querySelector('[name=reason]');
    const feedback = panel.querySelector('[role="status"]');
    let available = false;
    let busy = false;
    let operation;
    let version;
    let reviewToken;
    let current;
    let csrf;
    let finished = false;

    function controls() {
        open.disabled = confirm.disabled = busy || !!operation || !available;
        cancel.disabled = busy || !!operation;
        days.disabled = date.disabled = reason.disabled = busy || !!operation;
    }

    async function refresh() {
        try {
            const response = await fetch(panel.dataset.availabilityUrl, { cache: 'no-store' });
            if (!response.ok) throw new Error('Freigabe nicht verfügbar');
            const permission = await response.json();
            current = permission.approval;
            available = current.available && permission.actor === panel.dataset.actor;
            csrf = permission.csrf;
            if (permission.actor !== panel.dataset.actor) {
                feedback.textContent = 'Das angemeldete Konto hat gewechselt. Bitte öffnen Sie den Fall neu.';
            }
            panel.hidden = !available && !operation && !confirmation.open;
            return available;
        } catch {
            available = false;
            feedback.textContent = 'Die Verfügbarkeit konnte nicht geprüft werden. Bitte Anmeldung und Verbindung prüfen.';
            return false;
        } finally {
            controls();
        }
    }

    function accept(result) {
        if (result.state === 'APPLIED') {
            const editor = panel.querySelector('details.edit-card');
            if (editor) editor.open = false;
            finished = true;
            confirmation.close();
            feedback.textContent = 'Wiedervorlage bestätigt. Der bestätigte Fallstand wird geladen.';
        } else if (result.state === 'REJECTED') {
            operation = undefined;
            confirmation.close();
            feedback.textContent = 'Wiedervorlage abgewiesen. Bitte den aktuellen Fallstand prüfen.';
        } else {
            feedback.textContent = 'Wiedervorlage angefordert. Die Bestätigung wird abgewartet.';
        }
        document.dispatchEvent(new Event('propertyflow-case-changed'));
    }

    async function poll() {
        if (!operation || busy || finished) return;
        busy = true;
        controls();
        try {
            const response = await fetch(panel.dataset.resultUrl + encodeURIComponent(operation), { cache: 'no-store' });
            if (response.ok) accept(await response.json());
        } catch {
            feedback.textContent = 'Noch keine Bestätigung. Der Auftrag wird nicht erneut gesendet.';
        } finally {
            busy = false;
            controls();
        }
    }

    open.addEventListener('click', () => {
        if (!available || operation) return;
        if ((!days.value && !date.value) || (days.value && date.value) || !days.checkValidity() || !date.checkValidity()) {
            feedback.textContent = 'Bitte entweder gültige Kalendertage oder ein Datum wählen.';
            return;
        }
        version = document.querySelector('#case-head')?.dataset.version;
        reviewToken = current.reviewToken;
        confirmation.showModal();
    });
    cancel.addEventListener('click', () => { if (!busy && !operation) confirmation.close(); });
    confirmation.addEventListener('cancel', event => { if (busy || operation) event.preventDefault(); });
    confirm.addEventListener('click', async () => {
        if (busy || operation || !confirmation.open) return;
        busy = true;
        controls();
        try {
            if (!await refresh()) return;
            if (!version || version !== document.querySelector('#case-head')?.dataset.version
                    || version !== String(current.version) || reviewToken !== current.reviewToken) {
                confirmation.close();
                feedback.textContent = 'Der Fall hat sich geändert. Bitte erneut prüfen und die Wiedervorlage bestätigen.';
                return;
            }
            operation = crypto.randomUUID();
            confirmation.close();
            const response = await fetch(panel.dataset.submitUrl, { method: 'POST',
                body: new URLSearchParams({ operationId: operation, version, reviewToken, days: days.value, date: date.value, reason: reason.value, csrf }) });
            if (response.ok) accept(await response.json());
            else if ([400, 401, 403, 404, 409].includes(response.status)) {
                operation = undefined;
                confirmation.close();
                feedback.textContent = 'Wiedervorlage abgewiesen. Bitte Fallstand und Berechtigung prüfen.';
                document.dispatchEvent(new Event('propertyflow-case-changed'));
            } else {
                feedback.textContent = 'Bestätigung unklar. Der Auftrag wird nur noch gelesen und nicht erneut gesendet.';
            }
        } catch {
            feedback.textContent = 'Bestätigung unklar. Der Auftrag wird nicht automatisch wiederholt.';
        } finally {
            busy = false;
            controls();
        }
    });
    document.addEventListener('propertyflow-case-changed', () => { void refresh(); });
    document.addEventListener('propertyflow-access-lost', () => { available = false; controls(); });
    // Laufende Aufträge zügig abgleichen; Freigaben im Leerlauf im normalen Lesetakt prüfen.
    setInterval(() => { if (!document.hidden && operation) void poll(); }, 3000);
    setInterval(() => { if (!document.hidden && !busy && !operation) void refresh(); }, 20000);
    void refresh();
});
