(() => {
    const form = document.querySelector('#staff-follow-up-form');
    if (!form) return;
    const panel = form.closest('section');
    const feedback = form.querySelector('[role="status"]');
    const save = form.querySelector('[type="submit"]');
    const remove = form.querySelector('[data-follow-up-delete]');
    const close = form.querySelector('[data-follow-up-close]');
    const reload = form.querySelector('[data-follow-up-reload]');
    const actor = form.dataset.actor;
    let current;
    let csrf;
    let busy = false;

    function invalidate() {
        current = undefined;
        save.disabled = remove.disabled = true;
        if (close) close.disabled = true;
    }

    async function load() {
        if (busy) return;
        busy = true;
        invalidate();
        reload.disabled = true;
        try {
            const response = await fetch(form.dataset.availabilityUrl, { cache: 'no-store' });
            if (!response.ok) throw new Error('Unavailable');
            const data = await response.json();
            if (data.actor !== actor) throw new Error('Account changed');
            panel.hidden = document.querySelector('#case-status')?.textContent.trim() !== 'Wartet auf Wiedervorlage';
            if (!data.current.available) {
                feedback.textContent = 'Die Wiedervorlage kann derzeit nicht geändert werden. Prüfen Sie den aktuellen Fallstand.';
                return;
            }
            if (String(data.current.version) !== document.querySelector('#case-head')?.dataset.version) {
                feedback.textContent = 'Der Fall hat sich geändert. Prüfen Sie den aktuellen Stand und laden Sie die Freigabe erneut.';
                document.dispatchEvent(new Event('propertyflow-case-changed'));
                return;
            }
            current = data.current;
            csrf = data.csrf;
            save.disabled = remove.disabled = false;
            if (close) close.disabled = false;
            feedback.textContent = '';
        } catch {
            feedback.textContent = 'Die Aktion ist derzeit nicht verfügbar. Bitte prüfen Sie Verbindung und Anmeldung.';
        } finally {
            busy = false;
            reload.disabled = false;
        }
    }

    async function send(action) {
        if (busy || !current) return;
        busy = true;
        const fields = new URLSearchParams({ operationId: crypto.randomUUID(), waitId: current.waitId,
            version: current.version, csrf, reason: form.elements.reason.value });
        if (action === 'CHANGE') {
            fields.set('days', form.elements.days.value);
            fields.set('date', form.elements.date.value);
        }
        invalidate();
        reload.disabled = true;
        try {
            const response = await fetch(action === 'CLOSE' ? form.dataset.closeUrl : action === 'DELETE' ? form.dataset.deleteUrl : form.action, { method: 'POST', body: fields });
            if (!response.ok) {
                feedback.textContent = [400, 409].includes(response.status)
                    ? 'Datum oder Fallstand sind nicht mehr gültig. Bitte prüfen und erneut bestätigen.'
                    : 'Die Bestätigung ist unklar. Prüfen Sie den Fallstand; die Aktion wird nicht automatisch wiederholt.';
                return;
            }
            feedback.textContent = 'Änderung angefordert. Der bestätigte Fallstand wird aktualisiert.';
            for (let attempt = 0; attempt < 15; attempt++) {
                const result = await fetch(form.dataset.resultUrl + encodeURIComponent(fields.get('operationId')), { cache: 'no-store' });
                if (!result.ok) {
                    feedback.textContent = 'Die Änderung wurde nicht bestätigt. Prüfen Sie den aktuellen Fallstand.';
                    break;
                }
                if ((await result.json()).state === 'APPLIED') {
                    const editor = form.closest('details');
                    if (editor) editor.open = false;
                    feedback.textContent = action === 'CLOSE' ? 'Abschluss bestätigt.'
                        : action === 'DELETE' ? 'Wiedervorlage gelöscht. Die Bearbeitung wird fortgesetzt.' : 'Terminänderung bestätigt.';
                    break;
                }
                await new Promise(resolve => setTimeout(resolve, 1000));
            }
        } catch {
            feedback.textContent = 'Die Bestätigung ist unklar. Prüfen Sie den Fallstand; die Aktion wird nicht automatisch wiederholt.';
        } finally {
            busy = false;
            reload.disabled = false;
            document.dispatchEvent(new Event('propertyflow-case-changed'));
        }
    }

    form.addEventListener('submit', event => { event.preventDefault(); void send('CHANGE'); });
    remove.addEventListener('click', () => { void send('DELETE'); });
    close?.addEventListener('click', () => { void send('CLOSE'); });
    reload.addEventListener('click', load);
    form.addEventListener('input', () => { form.dataset.dirty = 'true'; });
    document.addEventListener('propertyflow-access-lost', invalidate);
    document.addEventListener('propertyflow-case-refreshed', () => {
        if (current && String(current.version) !== document.querySelector('#case-head')?.dataset.version) invalidate();
        panel.hidden = document.querySelector('#case-status')?.textContent.trim() !== 'Wartet auf Wiedervorlage';
    });
    void load();
})();
