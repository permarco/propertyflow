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
            const container = node.querySelector('.table-card, #history');
            const scroll = container?.scrollTop ?? 0;
            const focused = node.contains(document.activeElement);
            node.replaceWith(replacement);
            const nextContainer = replacement.querySelector('.table-card, #history');
            if (nextContainer) nextContainer.scrollTop = scroll;
            if (focused) replacement.focus({ preventScroll: true });
        });
        if (form) {
            const confirmedPage = document.querySelector('#results').dataset.page;
            target.searchParams.set('page', confirmedPage);
            history.replaceState(null, '', target);
        }
        announce('');
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
    setInterval(() => {
        if (!document.hidden && !debounce && !currentRead) {
            void refresh(new URL(window.location.href), ['#results']);
        }
    }, 20000);
}

if (detail && feedback && login) {
    setInterval(() => {
        if (!document.hidden && !currentRead) {
            void refresh(new URL(window.location.href), ['#case-head', '#case-sidebar', '#history']);
        }
    }, 20000);
}
