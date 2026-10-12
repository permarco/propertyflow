// Deliberately local: receives only the employee's explicit instruction, never case data.
export class MockStream {
    constructor(onUpdate, { wait = 700, interval = 45 } = {}) {
        this.onUpdate = onUpdate;
        this.wait = wait;
        this.interval = interval;
        this.run = 0;
        this.timer = null;
        this.state = 'idle';
        this.text = '';
    }
    publish(state) {
        this.state = state;
        this.onUpdate({ state, text: this.text });
    }
    start(instruction, mode = 'success') {
        if (['waiting', 'streaming'].includes(this.state) || !instruction.trim()) return false;
        const run = ++this.run;
        this.text = '';
        this.publish('waiting');
        const answer = mode === 'unsafe'
            ? '<img src=x onerror=alert("Test")> <script>alert("Test")</script> – Diese Zeichen bleiben Text.'
            : 'Guten Tag\n\n' + instruction.trim() + '\n\nVielen Dank für Ihre Rückmeldung.\nFreundliche Grüsse\nIhre Immobilienverwaltung';
        const parts = answer.match(/.{1,8}|\n/g) || [];
        let index = 0;
        const next = () => {
            if (run !== this.run) return;
            if (mode === 'error' && index === 5) {
                this.timer = null;
                this.publish('error');
                return;
            }
            if (index === parts.length) {
                this.timer = null;
                this.publish('completed');
                return;
            }
            this.text += parts[index++];
            this.publish('streaming');
            this.timer = setTimeout(next, this.interval);
        };
        this.timer = setTimeout(next, this.wait);
        return true;
    }
    abort() {
        if (!['waiting', 'streaming'].includes(this.state)) return;
        ++this.run; // Reject callbacks already queued by the old attempt.
        clearTimeout(this.timer);
        this.timer = null;
        this.publish('aborted');
    }
    dispose() {
        ++this.run;
        clearTimeout(this.timer);
        this.timer = null;
    }
}
