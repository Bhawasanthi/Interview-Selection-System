// =====================================================
// WORKSPACE BEHAVIOUR (shared by every role)
// =====================================================

(function () {

    var root = document.documentElement;
    var STORAGE_KEY = "iss.sidebar";

    function isMobile() {
        return window.matchMedia("(max-width: 991.98px)").matches;
    }

    function store(value) {
        try { localStorage.setItem(STORAGE_KEY, value); } catch (e) { /* storage unavailable */ }
    }


    document.addEventListener("DOMContentLoaded", function () {

        // ---------- Number-only fields: <input data-digits> keeps digits only (typing or pasting) ----------

        document.addEventListener("input", function (e) {
            var input = e.target;
            if (!input.matches || !input.matches("input[data-digits]")) return;
            var digits = input.value.replace(/\D/g, "");
            if (digits !== input.value) input.value = digits;
        });

        // ---------- Sidebar: collapse on desktop, drawer on mobile ----------

        document.querySelectorAll("[data-sidebar-toggle]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                if (isMobile()) {
                    root.classList.toggle("sidebar-open");
                } else {
                    var collapsed = root.classList.toggle("sidebar-collapsed");
                    store(collapsed ? "collapsed" : "expanded");
                }
                btn.setAttribute("aria-expanded",
                    String(isMobile() ? root.classList.contains("sidebar-open") : !root.classList.contains("sidebar-collapsed")));
            });
        });

        document.querySelectorAll("[data-sidebar-close]").forEach(function (el) {
            el.addEventListener("click", function () { root.classList.remove("sidebar-open"); });
        });

        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape") root.classList.remove("sidebar-open");
        });


        // ---------- Confirmation modal for destructive / important actions ----------
        // <form data-confirm="Message" data-confirm-title="Title" data-confirm-ok="Delete">

        var modalEl = document.getElementById("confirmModal");
        var modal = modalEl ? bootstrap.Modal.getOrCreateInstance(modalEl) : null;
        var pendingForm = null;
        var pendingSubmitter = null;

        document.addEventListener("submit", function (e) {
            var form = e.target;
            var submitter = e.submitter;
            var source = submitter && submitter.dataset.confirm ? submitter : form;
            var message = source.dataset.confirm;

            if (!message || !modal || form.dataset.confirmed === "yes") return;

            e.preventDefault();
            e.stopImmediatePropagation();

            pendingForm = form;
            pendingSubmitter = submitter || null;

            modalEl.querySelector("[data-confirm-title]").textContent = source.dataset.confirmTitle || "Please confirm";
            modalEl.querySelector("[data-confirm-message]").textContent = message;

            var ok = modalEl.querySelector("[data-confirm-ok]");
            ok.textContent = source.dataset.confirmOk || "Confirm";
            ok.className = "btn " + (source.dataset.confirmDanger === "true" ? "btn-danger" : "btn-maroon");

            modal.show();
        }, true);

        if (modalEl) {
            modalEl.querySelector("[data-confirm-ok]").addEventListener("click", function () {
                if (!pendingForm) return;
                pendingForm.dataset.confirmed = "yes";
                modal.hide();
                if (pendingForm.requestSubmit) {
                    pendingForm.requestSubmit(pendingSubmitter || undefined);
                } else {
                    pendingForm.submit();
                }
            });
        }


        // ---------- Loading state on submit ----------

        document.addEventListener("submit", function (e) {
            if (e.defaultPrevented) return;
            var form = e.target;
            var btn = e.submitter || form.querySelector("button[type=submit]");

            if (btn && !btn.hasAttribute("data-no-loading")) {
                // Deferred so the browser has already captured the form data (incl. the clicked button)
                setTimeout(function () {
                    btn.disabled = true;
                    btn.insertAdjacentHTML("afterbegin",
                        '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>');
                }, 0);
            }
            showLoader();
        });

        // Top progress bar while navigating to another page
        document.addEventListener("click", function (e) {
            var link = e.target.closest("a[href]");
            if (!link || e.ctrlKey || e.metaKey || e.shiftKey || link.target === "_blank"
                || link.hasAttribute("download") || link.getAttribute("href").charAt(0) === "#"
                || link.hasAttribute("data-bs-toggle") || link.href.indexOf("/export") !== -1) {
                return;
            }
            showLoader();
        });


        // ---------- Live table search + status filter ----------
        // <input data-filter-search="#table">  <select data-filter-status="#table">
        // Rows: <tr data-status="..."> ; optional <tr class="filter-empty"> shown when nothing matches

        function applyFilter(tableSel) {
            var table = document.querySelector(tableSel);
            if (!table) return;

            var searchEl = document.querySelector('[data-filter-search="' + tableSel + '"]');
            var statusEl = document.querySelector('[data-filter-status="' + tableSel + '"]');
            var query = searchEl ? searchEl.value.trim().toLowerCase() : "";
            var status = statusEl ? statusEl.value : "";
            var visible = 0;

            table.querySelectorAll("tbody tr:not(.filter-empty):not(.empty-row)").forEach(function (row) {
                var matchesText = !query || row.textContent.toLowerCase().indexOf(query) !== -1;
                var matchesStatus = !status || row.dataset.status === status;
                var show = matchesText && matchesStatus;
                row.hidden = !show;
                if (show) visible++;
            });

            var empty = table.querySelector("tr.filter-empty");
            if (empty) empty.hidden = visible > 0;

            var counter = document.querySelector('[data-filter-count="' + tableSel + '"]');
            if (counter) counter.textContent = visible;
        }

        document.querySelectorAll("[data-filter-search], [data-filter-status]").forEach(function (el) {
            var target = el.dataset.filterSearch || el.dataset.filterStatus;
            el.addEventListener(el.tagName === "SELECT" ? "change" : "input", function () { applyFilter(target); });
            applyFilter(target);
        });


        // ---------- Select-all checkboxes + live count ----------
        // <input type=checkbox data-check-all="applicationIds">  <span data-check-count="applicationIds">

        function updateCount(name) {
            var boxes = document.querySelectorAll('input[type=checkbox][name="' + name + '"]');
            var checked = Array.prototype.filter.call(boxes, function (b) { return b.checked; }).length;

            document.querySelectorAll('[data-check-count="' + name + '"]').forEach(function (el) {
                el.textContent = checked;
            });
            document.querySelectorAll('[data-check-all="' + name + '"]').forEach(function (all) {
                all.checked = boxes.length > 0 && checked === boxes.length;
                all.indeterminate = checked > 0 && checked < boxes.length;
            });
            document.querySelectorAll('[data-requires-check="' + name + '"]').forEach(function (btn) {
                btn.disabled = checked === 0;
            });
        }

        document.querySelectorAll("[data-check-all]").forEach(function (all) {
            var name = all.dataset.checkAll;
            all.addEventListener("change", function () {
                document.querySelectorAll('input[type=checkbox][name="' + name + '"]').forEach(function (b) {
                    if (!b.closest("[hidden]")) b.checked = all.checked;
                });
                updateCount(name);
            });
            document.querySelectorAll('input[type=checkbox][name="' + name + '"]').forEach(function (b) {
                b.addEventListener("change", function () { updateCount(name); });
            });
            updateCount(name);
        });


        // ---------- Show / hide password ----------

        document.querySelectorAll("[data-toggle-password]").forEach(function (box) {
            box.addEventListener("change", function () {
                box.getAttribute("data-toggle-password").split(",").forEach(function (id) {
                    var input = document.getElementById(id.trim());
                    if (input) input.type = box.checked ? "text" : "password";
                });
            });
        });


        // ---------- Matching password check ----------
        // <input data-match="#otherId">

        document.querySelectorAll("[data-match]").forEach(function (input) {
            var other = document.querySelector(input.dataset.match);
            function check() {
                input.setCustomValidity(input.value && other && input.value !== other.value
                    ? "The passwords do not match." : "");
            }
            input.addEventListener("input", check);
            if (other) other.addEventListener("input", check);
        });


        // ---------- Live letter preview ----------
        // <div data-letter-template="...text with [Tokens]...">  <input data-letter-token="[Token]">

        document.querySelectorAll("[data-letter-preview]").forEach(function (preview) {
            var template = preview.dataset.letterTemplate || preview.textContent;
            var inputs = document.querySelectorAll("[data-letter-token]");

            function escapeHtml(s) {
                return s.replace(/[&<>"]/g, function (c) {
                    return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c];
                });
            }

            function render() {
                var html = escapeHtml(template);
                inputs.forEach(function (input) {
                    var token = input.dataset.letterToken;
                    var value = formatValue(input);
                    var shown = value ? escapeHtml(value) : escapeHtml(token);
                    html = html.split(escapeHtml(token)).join("<mark>" + shown + "</mark>");
                });
                preview.innerHTML = html;
            }

            inputs.forEach(function (input) {
                input.addEventListener("input", render);
                input.addEventListener("change", render);
            });
            render();
        });

        function formatValue(input) {
            var v = input.value;
            if (!v) return "";
            if (input.type === "date") {
                var p = v.split("-");
                return p[2] + "/" + p[1] + "/" + p[0];
            }
            if (input.type === "time") {
                var t = v.split(":");
                var h = parseInt(t[0], 10);
                var suffix = h >= 12 ? "PM" : "AM";
                h = h % 12 || 12;
                return (h < 10 ? "0" + h : h) + ":" + t[1] + " " + suffix;
            }
            return v;
        }


        // ---------- Reusable add/edit modal ----------
        // <button data-bs-toggle="modal" data-bs-target="#m" data-fill data-action="/url"
        //         data-heading="Edit School" data-f-school-name="..." data-f-district="...">
        // Each data-f-some-field fills the modal input named someField.

        document.addEventListener("show.bs.modal", function (e) {
            var btn = e.relatedTarget;
            if (!btn || !btn.hasAttribute("data-fill")) return;

            var modalEl = e.target;
            var form = modalEl.querySelector("form");
            if (form) {
                form.reset();
                // reset() leaves script-set hidden values in place, so clear ids explicitly
                form.querySelectorAll("[data-reset]").forEach(function (el) { el.value = ""; });
                if (btn.dataset.action) form.action = btn.dataset.action;
            }

            var heading = modalEl.querySelector("[data-heading]");
            if (heading) heading.textContent = btn.dataset.heading || heading.dataset.heading;

            modalEl.querySelectorAll("[data-show-when]").forEach(function (el) {
                el.hidden = el.dataset.showWhen !== (btn.dataset.mode || "");
            });

            Object.keys(btn.dataset).forEach(function (key) {
                if (key.length < 2 || key.charAt(0) !== "f" || key.charAt(1) !== key.charAt(1).toUpperCase()) return;
                var name = key.charAt(1).toLowerCase() + key.slice(2);
                var field = form && form.elements.namedItem(name);
                if (field) field.value = btn.dataset[key];
            });
        });

        document.addEventListener("shown.bs.modal", function (e) {
            var first = e.target.querySelector("form input:not([type=hidden]):not([readonly]), form select");
            if (first) first.focus();
        });


        // ---------- Copy to clipboard ----------

        document.querySelectorAll("[data-copy]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var el = document.querySelector(btn.dataset.copy);
                if (!el || !navigator.clipboard) return;
                navigator.clipboard.writeText(el.textContent.trim()).then(function () {
                    btn.innerHTML = '<i class="fa-solid fa-check"></i> Copied';
                });
            });
        });


        // ---------- Tooltips on the collapsed sidebar ----------

        document.querySelectorAll(".sidebar [data-tip]").forEach(function (el) {
            new bootstrap.Tooltip(el, {
                title: el.dataset.tip,
                placement: "right",
                trigger: "hover",
                customClass: "sidebar-tip"
            });
            el.addEventListener("show.bs.tooltip", function (e) {
                if (!root.classList.contains("sidebar-collapsed") || isMobile()) e.preventDefault();
            });
        });
    });


    function showLoader() {
        var loader = document.querySelector(".page-loader");
        if (loader) loader.classList.add("loading");
    }

    // Returning with the back button restores the page from cache: reset loading states
    window.addEventListener("pageshow", function (e) {
        if (!e.persisted) return;
        var loader = document.querySelector(".page-loader");
        if (loader) loader.classList.remove("loading");
        document.querySelectorAll("button[disabled] .spinner-border").forEach(function (s) {
            var btn = s.parentElement;
            s.remove();
            btn.disabled = false;
        });
        document.querySelectorAll("form[data-confirmed]").forEach(function (f) {
            delete f.dataset.confirmed;
        });
    });

})();
