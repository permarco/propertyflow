(() => {
    const form = document.querySelector('#staff-wait-form');
    if (!form) return;
    const panel = form.closest('section');
    const feedback = form.querySelector('[role="status"]');
    const submit = form.querySelector('[type="submit"]');
    const external = form.querySelector('[data-wait-external]');
    const reload = form.querySelector('[data-wait-reload]');
    const actor = form.dataset.actor;
    let task;
    let csrf;
    let busy = false;

    async function load() {
        if (busy) return;
        busy = true;
        submit.disabled = external.disabled = reload.disabled = true;
        task = undefined;
        try {
            const response = await fetch(form.dataset.taskUrl, { cache: 'no-store' });
            if (!response.ok) throw new Error('Unavailable');
            const data = await response.json();
            if (data.actor !== actor) throw new Error('Account changed');
            panel.hidden = !data.task.available;
            if (!data.task.available) return;
            if (String(data.task.version) !== document.querySelector('#case-head')?.dataset.version) {
                feedback.textContent = 'Der Fall hat sich geändert. Prüfen Sie den aktuellen Stand und laden Sie die Freigabe erneut.';
                document.dispatchEvent(new Event('propertyflow-case-changed'));
                return;
            }
            task = data.task;
            csrf = data.csrf;
            submit.disabled = false;
            external.disabled = document.querySelector('#case-status')?.textContent.trim() !== 'In Bearbeitung';
            feedback.textContent = '';
        } catch {
            feedback.textContent = 'Die Aktion ist derzeit nicht verfügbar. Bitte prüfen Sie Verbindung und Anmeldung.';
        } finally {
            busy = false;
            reload.disabled = false;
        }
    }

    async function send(waitExternal) {
        if (busy || !task) return;
        busy = true;
        submit.disabled = external.disabled = reload.disabled = true;
        const fields = new URLSearchParams({ operationId: crypto.randomUUID(), userTaskKey: task.taskKey,
            version: task.version, reviewToken: task.reviewToken, csrf });
        if (!waitExternal) {
            fields.set('date', form.elements.date.value);
            fields.set('days', form.elements.days.value);
            fields.set('reason', form.elements.reason.value);
        }
        try {
            const response = await fetch(waitExternal ? form.dataset.externalUrl : form.action, { method: 'POST', body: fields });
            feedback.textContent = response.ok ? 'Entscheidung bestätigt. Der gespeicherte Fallstand wird aktualisiert.'
                : response.status === 400 ? 'Datum, Aufgabe oder Fallstand sind nicht mehr gültig. Bitte prüfen und erneut bestätigen.'
                    : 'Die Bestätigung ist unklar. Prüfen Sie den Fallstand; die Aktion wird nicht automatisch wiederholt.';
            if (response.ok && form.closest('details')) form.closest('details').open = false;
        } catch {
            feedback.textContent = 'Die Bestätigung ist unklar. Prüfen Sie den Fallstand; die Aktion wird nicht automatisch wiederholt.';
        } finally {
            task = undefined;
            busy = false;
            reload.disabled = false;
            document.dispatchEvent(new Event('propertyflow-case-changed'));
        }
    }

    form.addEventListener('submit', event => { event.preventDefault(); void send(false); });
    external.addEventListener('click', () => { void send(true); });
    reload.addEventListener('click', load);
    form.addEventListener('input', () => { form.dataset.dirty = 'true'; });
    document.addEventListener('propertyflow-case-refreshed', () => {
        if (task && String(task.version) !== document.querySelector('#case-head')?.dataset.version) {
            task = undefined;
            submit.disabled = external.disabled = true;
        }
        panel.hidden = !['Mitarbeiterprüfung erforderlich', 'In Bearbeitung', 'Wartet auf externe Rückmeldung']
            .includes(document.querySelector('#case-status')?.textContent.trim());
    });
    document.addEventListener('propertyflow-access-lost', () => { task = undefined; submit.disabled = external.disabled = true; });
    void load();
})();
