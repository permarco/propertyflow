(() => {
    const storageKey = 'propertyflow-history-order';
    let order = 'newest';
    try {
        if (localStorage.getItem(storageKey) === 'oldest') order = 'oldest';
    } catch {
        // Ohne Browserspeicher gilt die Auswahl für die aktuelle Seite.
    }

    function apply() {
        const history = document.querySelector('#history');
        const messages = history?.querySelector('.messages');
        const button = history?.querySelector('[data-history-order]');
        if (!messages || !button) return;
        // Der Server liefert beide Verläufe aufsteigend nach Zeitpunkt.
        if ((messages.dataset.order || 'oldest') !== order) {
            messages.replaceChildren(...Array.from(messages.children).reverse());
        }
        messages.dataset.order = order;
        const newest = order === 'newest';
        button.textContent = newest ? '↓' : '↑';
        button.title = newest
            ? 'Neueste zuerst – zu Älteste zuerst wechseln'
            : 'Älteste zuerst – zu Neueste zuerst wechseln';
        button.setAttribute('aria-label', button.title);
        button.hidden = false;
    }

    document.addEventListener('click', event => {
        if (!event.target.closest('[data-history-order]')) return;
        order = order === 'newest' ? 'oldest' : 'newest';
        try {
            localStorage.setItem(storageKey, order);
        } catch {
            // Die Bedienung bleibt auch bei gesperrtem Browserspeicher möglich.
        }
        apply();
        document.querySelector('#history').scrollTop = 0;
    });
    document.addEventListener('propertyflow-case-refreshed', apply);
    apply();
})();
