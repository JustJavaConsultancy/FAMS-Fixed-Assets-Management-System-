/*
 * input-restrict.js
 * ----------------------------------------------------------------------------
 * App-wide input typing restriction.
 *
 * Every <input>/<textarea> that opts in via a `data-input-type` attribute is
 * sanitised as the user types AND on paste: characters that do not belong to
 * the field's "kind" are silently dropped, so an unaccepted character is never
 * displayed. The input's `type` attribute is never changed.
 *
 * Supported types (the allow-list regexes strip everything else):
 *   letters       – person / place / entity names (no digits)
 *   text          – general readable text (allows digits + basic punctuation)
 *   alphanumeric   – codes / identifiers / serials / usernames
 *   search        – search boxes
 *   numeric       – whole numbers only
 *   decimal       – numbers with at most one decimal point
 *   email         – email addresses
 *   phone         – phone numbers
 *
 * Fields that must keep their full charset (passwords) or have their own
 * native control (file / date / colour / range / checkbox / radio / hidden /
 * submit / button) are ignored.
 * ----------------------------------------------------------------------------
 */
(function () {
    'use strict';

    // Each pattern removes characters that are NOT allowed for that type.
    var PATTERNS = {
        letters:     /[^A-Za-zÀ-ÿ\s'.\-’]/g,
        text:        /[^A-Za-z0-9À-ÿ\s.,!?'\-’()&/]/g,
        alphanumeric:/[^A-Za-z0-9._\-]/g,
        search:      /[^A-Za-z0-9\s\-]/g,
        numeric:     /[^0-9]/g,
        decimal:     /[^0-9.]/g,
        email:       /[^A-Za-z0-9._%+\-@]/g,
        phone:       /[^0-9+\s().\-]/g
    };

    // Inputs whose value must not be touched by this restriction.
    var SKIP_TYPES = {
        file: true, date: true, checkbox: true, radio: true, range: true,
        color: true, hidden: true, submit: true, button: true, reset: true,
        image: true, password: true
    };

    function isRestricted(el) {
        if (!el || (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA')) return false;
        if (SKIP_TYPES[el.type]) return false;
        var type = el.getAttribute('data-input-type');
        return !!(type && PATTERNS[type]);
    }

    // Collapse a "decimal" string to at most one dot.
    function collapseDecimal(value) {
        var i = value.indexOf('.');
        if (i === -1) return value;
        return value.slice(0, i + 1) + value.slice(i + 1).replace(/\./g, '');
    }

    // Strip disallowed characters and keep the caret in a sensible position.
    function sanitize(el) {
        if (!isRestricted(el)) return;
        var type = el.getAttribute('data-input-type');
        var re = PATTERNS[type];
        var original = el.value;
        var cleaned = original.replace(re, '');
        if (type === 'decimal') cleaned = collapseDecimal(cleaned);
        if (cleaned === original) return;

        // Work out where the caret should land after characters before it are removed.
        var pos = el.selectionStart || 0;
        var prefix = original.slice(0, pos).replace(re, '');
        if (type === 'decimal') prefix = collapseDecimal(prefix);
        var newPos = prefix.length;

        el.value = cleaned;
        try { el.setSelectionRange(newPos, newPos); } catch (e) { /* read-only / unsupported */ }
    }

    // ── Delegated listeners: one set of handlers covers the whole document,
    //    including inputs injected later by Thymeleaf fragments / modals. ──
    document.addEventListener('input', function (e) {
        var t = e.target;
        if (isRestricted(t)) sanitize(t);
    });

    document.addEventListener('paste', function (e) {
        var t = e.target;
        if (!isRestricted(t)) return;
        var type = t.getAttribute('data-input-type');
        var re = PATTERNS[type];
        var clipboard = (e.clipboardData || window.clipboardData);
        if (!clipboard) return;
        var text = clipboard.getData('text');
        if (!text) return;
        var cleaned = text.replace(re, '');
        if (type === 'decimal') cleaned = collapseDecimal(cleaned);
        if (cleaned === text) return; // nothing to sanitise – let the default paste happen

        e.preventDefault();
        var start = t.selectionStart || 0;
        var end = t.selectionEnd || 0;
        var value = t.value;
        t.value = value.slice(0, start) + cleaned + value.slice(end);
        var caret = start + cleaned.length;
        try { t.setSelectionRange(caret, caret); } catch (err) { /* ignore */ }
        t.dispatchEvent(new Event('input', { bubbles: true }));
    });

    // Expose for debugging / manual re-application if ever needed.
    window.__inputRestrict = { sanitize: sanitize, PATTERNS: PATTERNS };
})();
