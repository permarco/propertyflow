// UI3 warnt vor Textverlust; Mieteransichten verlassen die Seite ausdrücklich ohne Warnung.
window.addEventListener('beforeunload', event => {
    const hasDraft = ['#staff-message-text', '#memo-text'].some(selector => document.querySelector(selector)?.value.trim());
    if (!hasDraft) return;
    event.preventDefault();
    event.returnValue = '';
});
