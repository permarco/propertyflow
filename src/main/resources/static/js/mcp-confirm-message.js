const confirmation = document.querySelector("[data-mcp-confirmation]");
if (confirmation) {
    confirmation.addEventListener("submit", (event) => {
        if (confirmation.dataset.submitting === "true") {
            event.preventDefault();
            return;
        }
        confirmation.dataset.submitting = "true";
        if (event.submitter?.value === "send") {
            window.setTimeout(() => {
                event.submitter.textContent = event.submitter.dataset.busyLabel;
                confirmation.querySelectorAll("button").forEach((button) => { button.disabled = true; });
            }, 0);
        }
    });
}
