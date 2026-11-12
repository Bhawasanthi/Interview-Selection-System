// =====================================================
// "Are you sure you want to log out?" confirmation
// Applies to every <form data-logout> on the page.
// =====================================================

(function () {

    var pendingForm = null;

    document.addEventListener("submit", function (e) {
        var form = e.target;
        if (!form.matches("form[data-logout]")) return;

        e.preventDefault();
        e.stopImmediatePropagation();

        var modalEl = document.getElementById("logoutModal");
        if (!modalEl || typeof bootstrap === "undefined") {
            form.submit();
            return;
        }

        pendingForm = form;
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }, true);

    document.addEventListener("click", function (e) {
        var yes = e.target.closest("[data-logout-yes]");
        if (!yes || !pendingForm) return;

        yes.disabled = true;
        yes.insertAdjacentHTML("afterbegin",
            '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>');
        pendingForm.submit();   // submit() skips the submit event, so no second prompt
    });

    document.addEventListener("hidden.bs.modal", function (e) {
        if (e.target.id === "logoutModal") pendingForm = null;
    });

})();
