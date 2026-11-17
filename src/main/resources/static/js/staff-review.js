(() => {
    const form = document.querySelector('#staff-review-form');
    if (!form) return;
    const panel = form.closest('section');
    const submit = form.querySelector('[type="submit"]');
    const reload = form.querySelector('[data-review-reload]');
    const feedback = form.querySelector('[role="status"]');
    const actor = form.dataset.actor;
    const reason = form.elements.reason;
    let selected;
    let csrf;
    let busy = false;
    let operation;

    async function load() {
        if (busy) return;
        busy = true;
        submit.disabled = true;
        reload.disabled = true;
        try {
            const response = await fetch(form.dataset.reviewUrl, { cache: 'no-store' });
            if (!response.ok) throw new Error('Review unavailable');
            const data = await response.json();
            if (data.actor !== actor) throw new Error('Account changed');
            csrf = data.csrf;
            if (!data.task.canContinue) {
                selected = undefined;
                panel.hidden = !['Mitarbeiterprüfung erforderlich', 'Wartet auf externe Rückmeldung'].includes(document.querySelector('#case-status')?.textContent.trim());
                feedback.textContent = 'Derzeit liegt keine freigegebene Mitarbeiterprüfung vor. Sie können die aktuelle Prüfung erneut laden.';
                return;
            }
            panel.hidden = false;
            if (String(data.task.version) !== document.querySelector('#case-head')?.dataset.version) {
                selected = undefined;
                feedback.textContent = 'Der Fall hat sich geändert. Prüfen Sie den aktuellen Stand und laden Sie die Prüfung erneut.';
                document.dispatchEvent(new Event('propertyflow-case-changed'));
                return;
            }
            operation = crypto.randomUUID();
            selected = data.task;
            feedback.textContent = '';
            submit.disabled = false;
            if (reason) reason.readOnly = false;
        } catch {
            selected = undefined;
            panel.hidden = !['Mitarbeiterprüfung erforderlich', 'Wartet auf externe Rückmeldung'].includes(document.querySelector('#case-status')?.textContent.trim());
            feedback.textContent = 'Die Prüfung ist derzeit nicht verfügbar. Bitte prüfen Sie Verbindung und Anmeldung.';
        } finally {
            busy = false;
            reload.disabled = false;
        }
    }

    reload.addEventListener('click', load);
    reason?.addEventListener('input', () => {
        form.dataset.dirty = 'true';
        operation = crypto.randomUUID();
    });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (busy || !selected) return;
        busy = true;
        submit.disabled = true;
        reload.disabled = true;
        if (reason) reason.readOnly = true;
        try {
            const response = await fetch(form.action, {
                method: 'POST', body: new URLSearchParams({ operationId: operation, userTaskKey: selected.taskKey,
                    version: selected.version, reviewToken: selected.reviewToken, reason: reason?.value || '', csrf }),
            });
            feedback.textContent = response.ok
                ? 'Entscheidung bestätigt. Der gespeicherte Bearbeitungsstand wird aktualisiert.'
                : response.status === 400
                    ? 'Die Prüfung oder der Fallstand hat sich geändert. Bitte prüfen Sie den aktuellen Stand erneut.'
                    : 'Die Entscheidung konnte nicht bestätigt werden. Der Fallstand wird neu geladen; die Aktion wird nicht automatisch wiederholt.';
            if (response.ok && form.closest('details')) form.closest('details').open = false;
        } catch {
            feedback.textContent = 'Die Entscheidung konnte nicht bestätigt werden. Der Fallstand wird neu geladen; die Aktion wird nicht automatisch wiederholt.';
        } finally {
            busy = false;
            reload.disabled = false;
            document.dispatchEvent(new Event('propertyflow-case-changed'));
        }
    });

    document.addEventListener('propertyflow-case-refreshed', () => {
        if (busy) return;
        if (!['Mitarbeiterprüfung erforderlich', 'Wartet auf externe Rückmeldung'].includes(document.querySelector('#case-status')?.textContent.trim())) {
            panel.hidden = true;
            submit.disabled = true;
            return;
        }
        if (selected && String(selected.version) !== document.querySelector('#case-head')?.dataset.version) {
            submit.disabled = true;
            feedback.textContent = 'Der Fall hat sich geändert. Bitte prüfen Sie den Stand und laden Sie die aktuelle Prüfung.';
        }
        // Der Fachstatus kann vor der Camunda-Aufgabe sichtbar werden. Nur erstmals verfügbare Aufgaben automatisch lesen.
        if (!selected && form.dataset.dirty !== 'true') void load();
    });
    document.addEventListener('propertyflow-access-lost', () => { submit.disabled = true; });
    void load();
})();
