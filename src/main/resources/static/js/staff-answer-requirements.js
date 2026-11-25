(() => {
    const pending = new Set();
    const feedback = document.querySelector('#answer-action-feedback');
    if (!feedback) return;

    document.addEventListener('submit', async event => {
        const form = event.target.closest('form[data-clear-answer]');
        if (!form) return;
        event.preventDefault();
        const message = form.elements.messageId.value;
        if (pending.has(message)) return;
        pending.add(message);
        const button = form.querySelector('button');
        button.disabled = true;
        const operation = crypto.randomUUID();
        let submitted = false;
        let done = () => {};
        try {
            const response = await fetch(form.dataset.availabilityUrl, { cache: 'no-store' });
            if (!response.ok) throw new Error('Nicht verfügbar');
            const permission = await response.json();
            if (!permission.available || permission.actor !== form.dataset.actor
                    || form.elements.version.value !== document.querySelector('#case-head')?.dataset.version) {
                feedback.textContent = 'Der Fallstand oder die Berechtigung hat sich geändert. Bitte neu prüfen.';
                document.dispatchEvent(new Event('propertyflow-case-changed'));
                return;
            }
            const fields = new URLSearchParams({ operationId: operation, version: form.elements.version.value,
                messageId: message, csrf: permission.csrf });
            submitted = true;
            done = window.propertyflowBusy(button);
            // Der Server antwortet erst, wenn die Aufhebung gespeichert oder abgewiesen ist.
            const result = await fetch(form.action, { method: 'POST', body: fields });
            if (result.ok) {
                feedback.textContent = 'Antwortbedarf aufgehoben.';
            } else if ([400, 409].includes(result.status)) {
                submitted = false;
                feedback.textContent = 'Die Aufhebung wurde abgewiesen. Bitte den aktuellen Fallstand prüfen.';
            } else {
                if ([401, 403, 404].includes(result.status)) submitted = false;
                throw new Error('Nicht bestätigt');
            }
        } catch {
            feedback.textContent = submitted
                ? 'Die Bestätigung ist unklar. Bitte den Fallstand prüfen; der Auftrag wird nicht automatisch wiederholt.'
                : 'Die Aktion ist derzeit nicht verfügbar. Bitte Verbindung, Anmeldung und Fallstand prüfen.';
        } finally {
            done();
            if (!submitted) {
                pending.delete(message);
                button.disabled = false;
            }
            document.dispatchEvent(new Event('propertyflow-case-changed'));
        }
    });
})();
