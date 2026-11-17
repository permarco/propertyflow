(() => {
    const panel = document.querySelector('#staff-takeover');
    if (!panel) return;
    const open = panel.querySelector('[data-takeover-open]');
    const confirm = panel.querySelector('[data-takeover-confirm]');
    const cancel = panel.querySelector('[data-takeover-cancel]');
    const confirmation = panel.querySelector('[data-takeover-confirmation]');
    const feedback = panel.querySelector('[role="status"]');
    let available = false;
    let busy = false;
    let operation;
    let version;
    let csrf;
    let finished = false;

    function controls() {
        open.disabled = confirm.disabled = busy || !!operation || !available;
        cancel.disabled = busy || !!operation;
    }

    async function refresh() {
        try {
            const response = await fetch(panel.dataset.availabilityUrl, { cache: 'no-store' });
            if (!response.ok) throw new Error('Freigabe nicht verfügbar');
            const permission = await response.json();
            available = permission.available && permission.actor === panel.dataset.actor;
            csrf = permission.csrf;
            if (permission.actor !== panel.dataset.actor) {
                feedback.textContent = 'Das angemeldete Konto hat gewechselt. Bitte öffnen Sie den Fall neu.';
            }
            panel.hidden = !available && !operation && confirmation.hidden;
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
            finished = true;
            confirmation.hidden = true;
            feedback.textContent = 'Übernahme bestätigt. Der bestätigte Fallstand wird geladen.';
        } else if (result.state === 'REJECTED') {
            operation = undefined;
            confirmation.hidden = true;
            feedback.textContent = 'Übernahme abgewiesen. Bitte den aktuellen Fallstand prüfen.';
        } else {
            feedback.textContent = 'Übernahme angefordert. Die Bestätigung wird abgewartet.';
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
        version = document.querySelector('#case-head')?.dataset.version;
        confirmation.hidden = false;
    });
    cancel.addEventListener('click', () => { confirmation.hidden = true; });
    confirm.addEventListener('click', async () => {
        if (busy || operation || confirmation.hidden) return;
        busy = true;
        controls();
        try {
            if (!await refresh()) return;
            if (!version || version !== document.querySelector('#case-head')?.dataset.version) {
                confirmation.hidden = true;
                feedback.textContent = 'Der Fall hat sich geändert. Bitte erneut prüfen und die Übernahme bestätigen.';
                return;
            }
            operation = crypto.randomUUID();
            const response = await fetch(panel.dataset.submitUrl, { method: 'POST',
                body: new URLSearchParams({ operationId: operation, version, csrf }) });
            if (response.ok) accept(await response.json());
            else if ([400, 401, 403, 404, 409].includes(response.status)) {
                operation = undefined;
                confirmation.hidden = true;
                feedback.textContent = 'Übernahme abgewiesen. Bitte Fallstand und Berechtigung prüfen.';
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
})();
