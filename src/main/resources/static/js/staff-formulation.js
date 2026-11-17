(() => {
    const panel = document.querySelector('#staff-formulation');
    const form = document.querySelector('#staff-message-form');
    if (!panel || !form) return;
    const input = form.elements.text;
    const generate = panel.querySelector('[data-formulate]');
    const abort = panel.querySelector('[data-formulation-abort]');
    const adopt = panel.querySelector('[data-formulation-adopt]');
    const status = panel.querySelector('[data-formulation-status]');
    const output = panel.querySelector('[data-formulation-output]');
    const labels = { idle: 'Bereit', waiting: 'Wartet …', streaming: 'Wird formuliert …',
        completed: 'Vorschlag vollständig. Bitte prüfen.', aborted: 'Abgebrochen',
        error: 'Die Nachricht konnte nicht formuliert werden.' };
    let state = 'idle';
    let current;
    let proposal = '';

    function controls() {
        const saving = document.querySelector('[data-communication-saving="true"]');
        const closed = document.querySelector('#case-status')?.textContent.trim() === 'Abgeschlossen';
        const running = state === 'waiting' || state === 'streaming';
        const internal = document.querySelector('#internal-message-toggle')?.checked;
        generate.disabled = Boolean(internal) || Boolean(saving) || input.readOnly || closed || running || !input.value.trim();
        adopt.disabled = Boolean(internal) || Boolean(saving) || input.readOnly || closed || state !== 'completed';
        abort.hidden = !running;
        status.dataset.state = state;
        status.textContent = labels[state];
    }

    function publish(next) {
        state = next;
        output.textContent = proposal;
        controls();
    }

    function cancelAttempt(attempt) {
        attempt.controller.abort();
        if (attempt.sent) {
            // Auch bei beendetem Lesestrom erreicht der explizite Abbruch den Server.
            void fetch(`${panel.dataset.url}/${attempt.id}/abbrechen`, {
                method: 'POST', body: new URLSearchParams({ csrf: attempt.csrf }), keepalive: true,
            }).catch(() => {});
        }
    }

    generate.addEventListener('click', async () => {
        if (generate.disabled) return;
        const attempt = { id: crypto.randomUUID(), controller: new AbortController(), text: input.value };
        current = attempt;
        proposal = '';
        publish('waiting');
        const deadline = setTimeout(() => {
            if (current !== attempt) return;
            current = undefined;
            cancelAttempt(attempt);
            publish('error');
        }, 70000);
        try {
            const availability = await fetch(panel.dataset.availabilityUrl, {
                cache: 'no-store', signal: attempt.controller.signal,
            });
            if (!availability.ok) throw new Error('ACCESS_UNAVAILABLE');
            const capability = await availability.json();
            if (current !== attempt) return;
            if (!capability.available || capability.actor !== form.dataset.actor) throw new Error('ACCESS_CHANGED');
            attempt.csrf = capability.csrf;
            attempt.sent = true;
            const response = await fetch(panel.dataset.url, {
                method: 'POST', signal: attempt.controller.signal,
                body: new URLSearchParams({ text: attempt.text, attemptId: attempt.id, csrf: attempt.csrf, internal: 'false' }),
            });
            if (!response.ok || !response.body || !response.headers.get('content-type')?.includes('text/event-stream')) {
                throw new Error('FORMULATION_FAILED');
            }
            const reader = response.body.getReader();
            const decoder = new TextDecoder('utf-8', { fatal: true });
            let buffer = '';
            let terminal = false;
            while (current === attempt && !terminal) {
                const { done, value } = await reader.read();
                if (current !== attempt) return;
                buffer += done ? decoder.decode() : decoder.decode(value, { stream: true });
                if (buffer.length > 100000) throw new Error('STREAM_TOO_LONG');
                let separator;
                while ((separator = /\r?\n\r?\n/.exec(buffer))) {
                    const block = buffer.slice(0, separator.index);
                    buffer = buffer.slice(separator.index + separator[0].length);
                    const lines = block.split(/\r?\n/);
                    const event = lines.find(line => line.startsWith('event:'))?.slice(6).trim();
                    if (!event) continue;
                    const data = JSON.parse(lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n'));
                    if (event === 'delta') {
                        if (typeof data.text !== 'string' || proposal.length + data.text.length > 5000) throw new Error('INVALID_DELTA');
                        proposal += data.text;
                        publish('streaming');
                    } else if (['complete', 'error', 'aborted'].includes(event)) {
                        terminal = true;
                        publish(event === 'complete' && proposal.trim() ? 'completed' : event === 'aborted' ? 'aborted' : 'error');
                        break;
                    } else throw new Error('INVALID_EVENT');
                }
                if (done && !terminal) throw new Error('INCOMPLETE_STREAM');
            }
            await reader.cancel();
        } catch {
            if (current === attempt) {
                cancelAttempt(attempt);
                publish('error');
            }
        } finally {
            clearTimeout(deadline);
            if (current === attempt) current = undefined;
        }
    });
    abort.addEventListener('click', () => {
        const attempt = current;
        if (!attempt) return;
        current = undefined;
        cancelAttempt(attempt);
        publish('aborted');
    });
    adopt.addEventListener('click', () => {
        if (adopt.disabled) return;
        input.value = proposal;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        proposal = '';
        publish('idle');
        input.focus();
    });
    input.addEventListener('input', controls);
    document.addEventListener('propertyflow-communication-lock', controls);
    document.addEventListener('propertyflow-composer-mode-changed', controls);
    document.addEventListener('propertyflow-case-refreshed', controls);
    window.addEventListener('pagehide', () => {
        if (current) cancelAttempt(current);
        current = undefined;
    });
    controls();
})();
