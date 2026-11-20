/*
 * JPA Admin: keyboard shortcuts.
 *
 * One shortcut: the "/" key puts the cursor in the search box of a list (the box marked data-search-input), the way many web
 * tools do; on a page with no such box it goes to the sidebar's entity search when that is on screen (desktop width), as the
 * design prototype does. It is an enhancement only. The page works without this file: a "Skip to search" link before the sidebar is a plain
 * link, the box is reachable with Tab, and the hint next to the box is shown only when this file runs.
 *
 * What it does and does not do:
 *   - it acts only on the "/" key pressed on its own: no Ctrl, Alt or Meta, not while an input method is composing text, and
 *     not when something else already handled the key;
 *   - it never acts while the visitor is typing: not in a text box, a text area, a select, an editable element or a text-like
 *     role (so "/" can still be typed into a search, a filter or a view name);
 *   - it does nothing on a page without a search box to go to, and does nothing to the key there, so "/" keeps its normal meaning;
 *   - it reads nothing from the page except the search box, stores nothing, writes no cookie and sends no request.
 */
(function () {
  'use strict';

  var TEXT_TYPES = ['text', 'search', 'url', 'tel', 'email', 'password', 'number', 'date', 'datetime-local', 'month', 'time', 'week'];

  /** Whether the focused element takes typed text, so "/" must reach it untouched. */
  function typing(element) {
    if (!element || element === document.body || element === document.documentElement) {
      return false;
    }
    var tag = element.tagName ? element.tagName.toLowerCase() : '';
    if (tag === 'textarea' || tag === 'select') {
      return true;
    }
    if (tag === 'input') {
      var type = (element.getAttribute('type') || 'text').toLowerCase();
      return TEXT_TYPES.indexOf(type) >= 0;
    }
    if (element.isContentEditable) {
      return true;
    }
    var role = element.getAttribute ? element.getAttribute('role') : null;
    return role === 'textbox' || role === 'searchbox' || role === 'combobox';
  }

  document.addEventListener('keydown', function (event) {
    if (event.key !== '/' || event.defaultPrevented || event.isComposing) {
      return;
    }
    if (event.ctrlKey || event.altKey || event.metaKey) {
      return;
    }
    if (typing(document.activeElement)) {
      return;
    }
    var box = document.querySelector('[data-search-input]');
    if (!box && window.matchMedia('(min-width: 900px)').matches) {
      box = document.querySelector('[data-nav-search]'); // on a phone the sidebar is a closed drawer: nothing to focus
    }
    if (!box || box.disabled || box.type === 'hidden' || box.offsetParent === null) {
      return;
    }
    event.preventDefault();
    box.focus();
    if (typeof box.select === 'function') {
      box.select();
    }
  });
})();
