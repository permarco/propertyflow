(() => {
    const panel = document.querySelector('#staff-close-panel');
    if (!panel) return;
    const open = panel.querySelector('[data-close-open]');
    const reload = panel.querySelector('[data-close-reload]');
    const dialog = panel.querySelector('dialog');
    const form = dialog.querySelector('form');
    const confirm = form.querySelector('[type="submit"]');
    const cancel = form.querySelector('[data-close-cancel]');
    const feedback = panel.querySelector('[role="status"]');
    let selected;
    let operation;
    let csrf;
    let busy = false;

    async function load() {
        if (busy) return;
        busy = true;
        open.disabled = true;
        reload.disabled = true;
        try {
            const response = await fetch(panel.dataset.taskUrl, { cache: 'no-store' });
            if (!response.ok) throw new Error('Task unavailable');
            const data = await response.json();
            if (data.actor !== panel.dataset.actor) throw new Error('Account changed');
            panel.hidden = !data.task.canClose;
            if (!data.task.canClose) {
                selected = undefined;
                return;
            }
            if (String(data.task.version) !== document.querySelector('#case-head')?.dataset.version) {
                selected = undefined;
                feedback.textContent = 'Der Fall hat sich geändert. Prüfen Sie den aktuellen Stand und laden Sie die Freigabe erneut.';
                document.dispatchEvent(new Event('propertyflow-case-changed'));
                return;
            }
            operation = crypto.randomUUID();
            selected = data.task;
            csrf = data.csrf;
            feedback.textContent = '';
            open.disabled = false;
        } catch {
            selected = undefined;
            feedback.textContent = 'Der Abschluss ist derzeit nicht freigegeben. Bitte prüfen Sie Verbindung und Anmeldung.';
        } finally {
            busy = false;
            reload.disabled = false;
        }
    }

    reload.addEventListener('click', load);
    open.addEventListener('click', () => {
        if (!selected || busy) return;
        dialog.showModal();
    });
    cancel.addEventListener('click', () => { if (!busy) dialog.close(); });
    dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (!selected || busy) return;
        busy = true;
        open.disabled = true;
        confirm.disabled = true;
        cancel.disabled = true;
        reload.disabled = true;
        try {
            const response = await fetch(form.action, { method: 'POST', body: new URLSearchParams({
                operationId: operation, userTaskKey: selected.taskKey, version: selected.version, reviewToken: selected.reviewToken, csrf,
            }) });
            feedback.textContent = response.ok
                ? 'Abschlussentscheidung bestätigt. Der gespeicherte Fallstand wird aktualisiert.'
                : response.status === 400
                    ? 'Der Fall oder die Aufgabe hat sich geändert. Bitte prüfen Sie den aktuellen Stand und bestätigen Sie erneut.'
                    : 'Der Abschluss konnte nicht bestätigt werden. Die Aktion wird nicht automatisch wiederholt.';
        } catch {
            feedback.textContent = 'Der Abschluss konnte nicht bestätigt werden. Die Aktion wird nicht automatisch wiederholt.';
        } finally {
            busy = false;
            confirm.disabled = false;
            cancel.disabled = false;
            reload.disabled = false;
            dialog.close();
            document.dispatchEvent(new Event('propertyflow-case-changed'));
        }
    });
    document.addEventListener('propertyflow-case-refreshed', () => {
        if (busy) return;
        if (document.querySelector('#case-status')?.textContent.trim() === 'Abgeschlossen') {
            panel.hidden = true;
            open.disabled = true;
            dialog.close();
        } else if (selected && String(selected.version) !== document.querySelector('#case-head')?.dataset.version) {
            open.disabled = true;
            dialog.close();
            feedback.textContent = 'Der Fall hat sich geändert. Bitte prüfen Sie den Stand und laden Sie die aktuelle Freigabe.';
        }
    });
    document.addEventListener('propertyflow-access-lost', () => {
        open.disabled = true;
        if (!busy) dialog.close();
    });
    void load();
})();
