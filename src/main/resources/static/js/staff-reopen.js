(() => {
    const panel = document.querySelector('#staff-reopen-panel');
    if (!panel) return;
    const open = panel.querySelector('[data-reopen-open]');
    const dialog = panel.querySelector('dialog');
    const form = dialog.querySelector('form');
    const submit = form.querySelector('[type="submit"]');
    const feedback = panel.querySelector('[role="status"]');
    let snapshot;
    let busy = false;

    function refresh() {
        panel.hidden = document.querySelector('#case-status')?.textContent.trim() !== 'Abgeschlossen';
    }

    open.addEventListener('click', async () => {
        if (busy) return;
        busy = true;
        open.disabled = true;
        const version = document.querySelector('#case-head')?.dataset.version;
        try {
            const response = await fetch(panel.dataset.taskUrl, { cache: 'no-store' });
            if (!response.ok) throw new Error('Unavailable');
            const data = await response.json();
            if (data.actor !== panel.dataset.actor || version !== document.querySelector('#case-head')?.dataset.version) {
                throw new Error('Context changed');
            }
            snapshot = { version, csrf: data.csrf, operationId: crypto.randomUUID() };
            submit.disabled = false;
            dialog.showModal();
        } catch {
            feedback.textContent = 'Die Wiedereröffnung ist derzeit nicht verfügbar. Bitte prüfen Sie Anmeldung und Fallstand.';
        } finally {
            busy = false;
            open.disabled = false;
        }
    });
    panel.querySelector('[data-reopen-cancel]').addEventListener('click', () => { snapshot = undefined; dialog.close(); });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (busy || !snapshot) return;
        busy = true;
        submit.disabled = true;
        const done = window.propertyflowBusy(submit);
        try {
            const response = await fetch(form.action, { method: 'POST', body: new URLSearchParams(snapshot) });
            feedback.textContent = response.ok
                ? 'Der Fall ist wiedereröffnet.'
                : [400, 409].includes(response.status)
                    ? 'Wiedereröffnung nicht möglich. Bitte prüfen Sie den aktuellen Fallstand.'
                    : 'Die Bestätigung ist unklar. Bitte prüfen Sie den Fallstand; der Auftrag wird nicht automatisch wiederholt.';
        } catch {
            feedback.textContent = 'Die Bestätigung ist unklar. Bitte prüfen Sie den Fallstand; der Auftrag wird nicht automatisch wiederholt.';
        } finally {
            done();
            snapshot = undefined;
            busy = false;
            dialog.close();
            document.dispatchEvent(new Event('propertyflow-case-changed'));
        }
    });
    document.addEventListener('propertyflow-case-refreshed', refresh);
    document.addEventListener('propertyflow-access-lost', () => { snapshot = undefined; open.disabled = true; submit.disabled = true; dialog.close(); });
    refresh();
})();
