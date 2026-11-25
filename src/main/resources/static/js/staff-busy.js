// Zeigt an einem Button, dass der Server eine Aktion noch verarbeitet.
// Die Anzeige erscheint erst nach kurzer Verzögerung, damit schnelle Antworten nicht flackern.
window.propertyflowBusy = (button, delay = 300) => {
    const original = button.textContent;
    const form = button.closest('form') || button.parentElement;
    const timer = setTimeout(() => {
        if (button.dataset.busyLabel) button.textContent = button.dataset.busyLabel;
        form.setAttribute('aria-busy', 'true');
    }, delay);
    return () => {
        clearTimeout(timer);
        button.textContent = original;
        form.removeAttribute('aria-busy');
    };
};
