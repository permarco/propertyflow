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
            const result = await fetch(form.action, { method: 'POST', body: fields });
            if (!result.ok) {
                if ([400, 401, 403, 404, 409].includes(result.status)) submitted = false;
                throw new Error('Nicht bestätigt');
            }
            let outcome = await result.json();
            for (let attempt = 0; outcome.state === 'PENDING' && attempt < 15; attempt++) {
                await new Promise(resolve => setTimeout(resolve, 1000));
                const current = await fetch(form.dataset.resultUrl + encodeURIComponent(operation), { cache: 'no-store' });
                if (!current.ok) throw new Error('Nicht bestätigt');
                outcome = await current.json();
            }
            if (outcome.state === 'APPLIED') {
                feedback.textContent = 'Antwortbedarf aufgehoben. Der bestätigte Fallstand wird aktualisiert.';
            } else if (outcome.state === 'REJECTED') {
                submitted = false;
                feedback.textContent = 'Die Aufhebung wurde abgewiesen. Bitte den aktuellen Fallstand prüfen.';
            } else {
                feedback.textContent = 'Die Aufhebung ist noch nicht bestätigt. Der Auftrag wird nicht erneut gesendet.';
            }
        } catch {
            feedback.textContent = submitted
                ? 'Die Bestätigung ist unklar. Bitte den Fallstand prüfen; der Auftrag wird nicht automatisch wiederholt.'
                : 'Die Aktion ist derzeit nicht verfügbar. Bitte Verbindung, Anmeldung und Fallstand prüfen.';
        } finally {
            if (!submitted) {
                pending.delete(message);
                button.disabled = false;
            }
            document.dispatchEvent(new Event('propertyflow-case-changed'));
        }
    });
})();
