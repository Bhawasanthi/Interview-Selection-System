// =====================================================
// PUBLIC SITE BEHAVIOUR (Home, About, Contact Us, Schools)
// =====================================================

document.addEventListener("DOMContentLoaded", function () {

    // ---------- Today's date in the top strip ----------

    var dateEl = document.getElementById("todayDate");

    if (dateEl) {
        dateEl.textContent = new Date().toLocaleDateString("en-GB", {
            weekday: "long", day: "numeric", month: "long", year: "numeric"
        });
    }


    // ---------- Re-open the right modal after a redirect ----------

    var params = new URLSearchParams(window.location.search);
    var state = window.pageState || {};
    var loginModal = document.getElementById("loginModal");
    var signupModal = document.getElementById("signupModal");

    var forgotModal = document.getElementById("forgotModal");

    if (forgotModal && state.forgotOpen) {
        bootstrap.Modal.getOrCreateInstance(forgotModal).show();
    } else if (signupModal && state.signupError) {
        bootstrap.Modal.getOrCreateInstance(signupModal).show();
    } else if (loginModal && (state.signupSuccess || params.has("loginError") || params.has("login"))) {
        bootstrap.Modal.getOrCreateInstance(loginModal).show();
    }

    var logoutToast = document.getElementById("logoutToast");
    if (logoutToast && params.has("logout")) {
        bootstrap.Toast.getOrCreateInstance(logoutToast).show();
    }

    // Clean the query string so a refresh doesn't re-trigger messages
    if (params.has("loginError") || params.has("login") || params.has("logout") || params.has("forgot")) {
        history.replaceState(null, "", window.location.pathname);
    }


    // ---------- Show / hide password ----------

    document.querySelectorAll("[data-toggle-password]").forEach(function (box) {
        box.addEventListener("change", function () {
            box.getAttribute("data-toggle-password").split(",").forEach(function (id) {
                var input = document.getElementById(id.trim());
                if (input) input.type = box.checked ? "text" : "password";
            });
        });
    });


    // ---------- Check passwords match before submitting ----------

    var signupForm = document.getElementById("signupForm");
    var clientError = document.getElementById("signupClientError");

    if (signupForm) {
        signupForm.addEventListener("submit", function (e) {
            var pw = document.getElementById("signupPassword").value;
            var confirm = document.getElementById("confirmPassword").value;

            if (pw !== confirm) {
                e.preventDefault();
                clientError.classList.remove("d-none");
                document.getElementById("confirmPassword").focus();
            } else {
                clientError.classList.add("d-none");
            }
        });
    }


    // ---------- Focus the first field when a modal opens ----------

    if (loginModal) {
        loginModal.addEventListener("shown.bs.modal", function () {
            document.getElementById("loginUsername").focus();
        });
    }

    if (signupModal) {
        signupModal.addEventListener("shown.bs.modal", function () {
            document.getElementById("signupFullName").focus();
        });
    }


    // ---------- Disable submit buttons while sending ----------

    document.querySelectorAll("form").forEach(function (form) {
        form.addEventListener("submit", function (e) {
            if (e.defaultPrevented) return;
            var btn = form.querySelector("button[type=submit]");
            if (btn) {
                btn.disabled = true;
                btn.insertAdjacentHTML("afterbegin",
                    '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>');
            }
        });
    });


    // ---------- Reveal sections on scroll ----------

    var items = document.querySelectorAll(".reveal");

    if ("IntersectionObserver" in window) {
        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    entry.target.classList.add("visible");
                    observer.unobserve(entry.target);
                }
            });
        }, { threshold: 0.12 });

        items.forEach(function (el) { observer.observe(el); });
    } else {
        items.forEach(function (el) { el.classList.add("visible"); });
    }

});
