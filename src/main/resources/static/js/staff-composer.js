(() => {
    const toggle = document.querySelector('#internal-message-toggle');
    const external = document.querySelector('#staff-message-form');
    const memo = document.querySelector('#memo-panel');
    if (!toggle || !external || !memo) return;

    function syncLock() {
        toggle.disabled = Boolean(document.querySelector('[data-communication-saving="true"]'));
    }

    toggle.addEventListener('change', () => {
        external.hidden = toggle.checked;
        memo.hidden = !toggle.checked;
        document.dispatchEvent(new Event('propertyflow-composer-mode-changed'));
    });
    document.addEventListener('propertyflow-communication-lock', syncLock);
    syncLock();
})();
