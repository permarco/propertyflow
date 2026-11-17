const form = document.querySelector('#filters');
const detail = document.querySelector('#case-page');
const feedback = document.querySelector('#load-state, #save-state');
const login = document.body.dataset.loginUrl;

let currentRead;
let debounce;
let readStopped = false;

function abortRead() {
    currentRead?.abort();
}

function announce(message) {
    feedback.textContent = message;
}

async function refresh(target, selectors) {
    if (readStopped) return;
    abortRead();
    const request = new AbortController();
    currentRead = request;
    try {
        const response = await fetch(target, {
            headers: { 'X-PropertyFlow-Request': 'partial' },
            cache: 'no-store',
            signal: request.signal,
        });
        if (request.signal.aborted) return;
        if (response.status === 401 || response.status === 403 || (detail && response.status === 404)) {
            readStopped = true;
            selectors.forEach(selector => document.querySelector(selector)?.replaceChildren());
            if (response.status === 401) {
                if (document.querySelector('form[data-dirty="true"]')) {
                    document.dispatchEvent(new Event('propertyflow-access-lost'));
                    announce('Ihre Sitzung ist abgelaufen. Die ungespeicherte Eingabe bleibt erhalten.');
                    return;
                }
                // A navigation records the now-unauthenticated page as the login destination.
                window.location.assign(target);
            } else if (response.status === 403) {
                form?.querySelector('fieldset')?.setAttribute('disabled', '');
                announce('Der Zugriff auf den Mitarbeiterbereich wurde entzogen.');
            } else {
                announce('Dieser Fall ist nicht verfügbar.');
            }
            return;
        }
        if (!response.ok) throw new Error('Read failed');
        const page = new DOMParser().parseFromString(await response.text(), 'text/html');
        if (request.signal.aborted) return;
        const next = selectors.map(selector => page.querySelector(selector));
        if (next.some(node => !node)) throw new Error('Missing content');
        selectors.forEach((selector, index) => {
            const node = document.querySelector(selector);
            const replacement = next[index];
            if (node.isEqualNode(replacement)) return;
            const container = node.matches('#history') ? node : node.querySelector('.table-card, #history');
            const scroll = container?.scrollTop ?? 0;
            const active = document.activeElement;
            const focused = node.contains(active);
            const focusId = focused ? active.id : '';
            const focusHref = focused && active.matches('a[href]') ? active.getAttribute('href') : undefined;
            node.replaceWith(replacement);
            const nextContainer = replacement.matches('#history') ? replacement : replacement.querySelector('.table-card, #history');
            if (nextContainer) nextContainer.scrollTop = scroll;
            if (focused) {
                const sameControl = focusId ? replacement.querySelector('#' + CSS.escape(focusId))
                    : focusHref ? [...replacement.querySelectorAll('a[href]')].find(link => link.getAttribute('href') === focusHref) : undefined;
                (sameControl || replacement).focus({ preventScroll: true });
            }
        });
        if (form) {
            const confirmedPage = document.querySelector('#results').dataset.page;
            target.searchParams.set('page', confirmedPage);
            history.replaceState(null, '', target);
        }
        announce('');
        if (detail) document.dispatchEvent(new Event('propertyflow-case-refreshed'));
    } catch (error) {
        if (!request.signal.aborted) {
            announce('Die Ansicht konnte nicht aktualisiert werden. Bitte versuchen Sie es erneut.');
        }
    } finally {
        if (currentRead === request) currentRead = undefined;
    }
}

function filterUrl() {
    const target = new URL(form.action);
    target.search = new URLSearchParams(new FormData(form)).toString();
    return target;
}

function applyFilters() {
    clearTimeout(debounce);
    debounce = undefined;
    void refresh(filterUrl(), ['#results']);
}

if (form && feedback && login) {
    document.addEventListener('click', event => {
        const link = event.target.closest('#results .pagination a, #results th a');
        if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        const target = new URL(link.href);
        if (target.origin !== location.origin || target.pathname !== new URL(form.action).pathname) return;
        event.preventDefault();
        clearTimeout(debounce);
        debounce = undefined;
        for (const name of ['sort', 'desc']) {
            if (form.elements[name] && target.searchParams.has(name)) form.elements[name].value = target.searchParams.get(name);
        }
        void refresh(target, ['#results']);
    });
    form.querySelector('button[type=submit]').hidden = true;
    form.addEventListener('submit', event => {
        event.preventDefault();
        applyFilters();
    });
    form.addEventListener('input', event => {
        if (event.target.name !== 'q') return;
        clearTimeout(debounce);
        abortRead();
        debounce = setTimeout(applyFilters, 1000);
    });
    form.addEventListener('change', event => {
        if (event.target.name !== 'q') applyFilters();
    });
    const refreshVisibleList = () => {
        if (!document.hidden && !debounce && !currentRead) {
            void refresh(new URL(window.location.href), ['#results']);
        }
    };
    setInterval(refreshVisibleList, 20000);
    document.addEventListener('visibilitychange', refreshVisibleList);
}

if (detail && feedback && login) {
    document.addEventListener('propertyflow-case-changed', () => {
        readStopped = false;
        void refresh(new URL(window.location.href), ['#case-head', '#case-data', '#case-review', '#case-recommendation', '#history']);
    });
    setInterval(() => {
        if (!document.hidden && !currentRead) {
            void refresh(new URL(window.location.href), ['#case-head', '#case-data', '#case-review', '#case-recommendation', '#history']);
        }
    }, 20000);
}
