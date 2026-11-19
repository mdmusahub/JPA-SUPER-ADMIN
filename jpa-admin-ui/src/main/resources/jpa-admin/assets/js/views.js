/*
 * JPA Admin: saved views.
 *
 * A saved view is a name for a list address: the search, the filters, the sort, the page size and the hidden columns of one
 * entity's list. Views live in this browser only (localStorage); nothing about a view is sent anywhere or kept on the server,
 * so clearing the browser's site data removes them and two browsers do not share them. The page works without this file and
 * without storage: the Columns and Filters panels are plain form controls, and the "Saved views" panel then says what it needs.
 *
 * What this file does:
 *   - reads the stored views of this entity inside try/catch, cleans each one against the columns and filters the page
 *     offers, and quietly drops any that does not fit (not valid JSON, not an object, a name that is missing or too long, a
 *     column or filter this page does not have, a page size that is not on offer, a 21st view). It never fails or shows an
 *     error page because of what it found in storage;
 *   - saves the state of the page as the server validated it (the address of the current page-size link, handed over in
 *     data-views-state), not the raw address, with a name of 1 to 60 characters; saving under an existing name replaces that
 *     view; at most 20 views are kept;
 *   - lists the views as plain links to the list's address, with the same percent-encoding as the server's link writer, and
 *     never a page number, so applying a view always starts on the first page;
 *   - renames and deletes a view (delete asks first, with a details element, like every other destructive action).
 *
 * It stores only the view JSON, never writes a cookie, never sends a request, never builds markup from text (every name is put
 * in with textContent and every link with setAttribute), and reads nothing from the page but the data attributes of its panel
 * and the controls it owns. The server checks every address again, so this file is no security measure.
 */
(function () {
  'use strict';

  var MAX_VIEWS = 20;
  var MAX_NAME = 60;
  var MAX_TEXT = 100;
  var MAX_FILTERS = 10;
  var DEFAULT_SIZE = 25;
  var PAGE_SIZES = [10, 25, 50, 100];

  var panel = document.querySelector('[data-views]');
  if (!panel) {
    return;
  }

  function part(selector) {
    return panel.querySelector(selector);
  }

  var list = part('[data-views-list]');
  var empty = part('[data-views-empty]');
  var status = part('[data-views-status]');
  var nameInput = part('[data-views-name]');
  var saveButton = part('[data-views-save]');
  var count = part('[data-views-count]');
  var template = part('template[data-views-item]');
  if (!list || !empty || !status || !nameInput || !saveButton || !template || !template.content) {
    return;
  }

  function words(attribute) {
    var value = panel.getAttribute(attribute) || '';
    return value === '' ? [] : value.split(' ');
  }

  var base = panel.getAttribute('data-views-base') || '';
  var stateHref = panel.getAttribute('data-views-state') || '';
  var columns = words('data-views-columns');
  var filterKeys = words('data-views-filters');

  // The base is a local path under the console: one leading slash, plain segments, no "//", no backslash, no space.
  if (!/^\/[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*$/.test(base)) {
    say('Saved views are not available on this page.');
    saveButton.disabled = true;
    nameInput.disabled = true;
    return;
  }
  var KEY = 'jpa-admin.views.' + base;

  function say(text) {
    if (status) {
      status.textContent = text;
    }
  }

  // ---- encoding: the same rule as the server's link writer (only letters, digits and - _ . stay as they are) ---------------

  function enc(value) {
    return encodeURIComponent(value).replace(/[!~*'()]/g, function (c) {
      return '%' + c.charCodeAt(0).toString(16).toUpperCase();
    });
  }

  function encodable(value) {
    try {
      encodeURIComponent(value);
      return true;
    } catch (e) {
      return false; // a lone surrogate half
    }
  }

  // ---- cleaning: what a stored or new view must look like -------------------------------------------------------------

  // Control characters (and the invisible characters that reorder text) never belong in a name or a value.
  var CONTROL = /[\x00-\x1f\x7f-\x9f]/;

  function reorders(value) {
    for (var i = 0; i < value.length; i++) {
      var code = value.charCodeAt(i);
      if ((code >= 0x202a && code <= 0x202e) || (code >= 0x2066 && code <= 0x2069)) {
        return true;
      }
    }
    return false;
  }

  function plainText(value, max) {
    return typeof value === 'string' && value.length <= max && !CONTROL.test(value) && !reorders(value) && encodable(value);
  }

  function cleanName(value) {
    if (typeof value !== 'string') {
      return null;
    }
    var name = value.trim();
    return name.length >= 1 && plainText(name, MAX_NAME) ? name : null;
  }

  function filterBase(key) {
    var rest = key.slice('filter.'.length);
    var suffix = /^(.+)\.(min|max|from|to)$/.exec(rest);
    return suffix ? suffix[1] : rest;
  }

  function cleanFilters(value) {
    var clean = {};
    if (value === undefined || value === null) {
      return clean;
    }
    if (typeof value !== 'object' || Array.isArray(value)) {
      return null;
    }
    var keys = Object.keys(value);
    if (keys.length > MAX_FILTERS) {
      return null;
    }
    for (var i = 0; i < keys.length; i++) {
      var key = keys[i];
      if (key.indexOf('filter.') !== 0 || filterKeys.indexOf(filterBase(key)) < 0) {
        return null; // a filter this page does not have
      }
      var text = value[key];
      if (typeof text !== 'string' || text === '' || !plainText(text, MAX_TEXT)) {
        return null;
      }
      clean[key] = text;
    }
    return clean;
  }

  function cleanHidden(value) {
    if (value === undefined || value === null) {
      return [];
    }
    if (!Array.isArray(value) || value.length >= columns.length) {
      return null; // not a list, or it would hide every column
    }
    var clean = [];
    for (var i = 0; i < value.length; i++) {
      if (typeof value[i] !== 'string' || columns.indexOf(value[i]) < 0 || clean.indexOf(value[i]) >= 0) {
        return null; // a column this page does not have, or a repeat
      }
      clean.push(value[i]);
    }
    return clean;
  }

  /** The view in its one valid shape, or null when anything about it is wrong. */
  function clean(item) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
      return null;
    }
    var name = cleanName(item.name);
    var q = item.q === undefined || item.q === null ? '' : item.q;
    var filters = cleanFilters(item.filters);
    var hidden = cleanHidden(item.hiddenColumns);
    var size = item.pageSize === undefined || item.pageSize === null ? DEFAULT_SIZE : item.pageSize;
    var sort = item.sort === undefined ? null : item.sort;
    var dir = item.dir === undefined ? null : item.dir;
    if (name === null || filters === null || hidden === null || (q !== '' && !plainText(q, MAX_TEXT))) {
      return null;
    }
    if (typeof size !== 'number' || PAGE_SIZES.indexOf(size) < 0) {
      return null;
    }
    if (sort !== null && (typeof sort !== 'string' || columns.indexOf(sort) < 0)) {
      return null;
    }
    if (sort === null ? dir !== null : (dir !== 'asc' && dir !== 'desc')) {
      return null;
    }
    return { name: name, q: q, filters: filters, sort: sort, dir: dir, pageSize: size, hiddenColumns: hidden };
  }

  // ---- storage: every access inside try/catch; null means "not available" ------------------------------------------------

  function load() {
    var raw;
    try {
      raw = window.localStorage.getItem(KEY);
    } catch (e) {
      return null;
    }
    var views = [];
    var dropped = false;
    var parsed = null;
    if (raw !== null) {
      try {
        parsed = JSON.parse(raw);
      } catch (e) {
        parsed = null;
      }
      if (!Array.isArray(parsed)) {
        dropped = true;
        parsed = [];
      }
    } else {
      parsed = [];
    }
    for (var i = 0; i < parsed.length; i++) {
      var view = views.length < MAX_VIEWS ? clean(parsed[i]) : null;
      if (view === null || indexOfName(views, view.name) >= 0) {
        dropped = true;
      } else {
        views.push(view);
      }
    }
    if (dropped) {
      store(views); // forget what was dropped, quietly
    }
    return views;
  }

  function store(views) {
    try {
      window.localStorage.setItem(KEY, JSON.stringify(views));
      return true;
    } catch (e) {
      return false;
    }
  }

  function indexOfName(views, name, except) {
    var wanted = name.toLowerCase();
    for (var i = 0; i < views.length; i++) {
      if (i !== except && views[i].name.toLowerCase() === wanted) {
        return i;
      }
    }
    return -1;
  }

  // ---- links: the list address plus the view's parts, never a page ---------------------------------------------------------

  function hrefOf(view) {
    var parts = [];
    if (view.q !== '') {
      parts.push('q=' + enc(view.q));
    }
    Object.keys(view.filters).forEach(function (key) {
      parts.push(enc(key) + '=' + enc(view.filters[key]));
    });
    view.hiddenColumns.forEach(function (column) {
      parts.push('hide=' + enc(column));
    });
    if (view.sort !== null) {
      parts.push('sort=' + enc(view.sort));
      parts.push('dir=' + enc(view.dir));
    }
    if (view.pageSize !== DEFAULT_SIZE) {
      parts.push('size=' + view.pageSize);
    }
    return parts.length === 0 ? base : base + '?' + parts.join('&');
  }

  /** The view the page is showing now, read from the address the server validated, or null when it cannot be saved. */
  function currentView(name) {
    var at = stateHref.indexOf('?');
    var filters = {};
    var hidden = [];
    var item = { name: name, q: '', filters: filters, sort: null, dir: null, pageSize: DEFAULT_SIZE, hiddenColumns: hidden };
    if (at >= 0 && typeof URLSearchParams === 'function') {
      var params = new URLSearchParams(stateHref.slice(at + 1));
      item.q = params.get('q') || '';
      params.forEach(function (value, key) {
        if (key.indexOf('filter.') === 0) {
          filters[key] = value;
        }
      });
      hidden.push.apply(hidden, params.getAll('hide'));
      item.sort = params.get('sort');
      item.dir = params.get('dir');
      item.pageSize = params.has('size') ? parseInt(params.get('size'), 10) : DEFAULT_SIZE;
    }
    return clean(item);
  }

  // ---- the menu ---------------------------------------------------------------------------------------------------------------

  function show(views) {
    while (list.firstChild) {
      list.removeChild(list.firstChild);
    }
    views.forEach(function (view, index) {
      list.appendChild(row(view, index));
    });
    empty.hidden = views.length > 0;
    if (count) {
      count.textContent = String(views.length);
      count.hidden = views.length === 0;
    }
  }

  function row(view, index) {
    var item = template.content.firstElementChild.cloneNode(true);
    function own(selector) {
      return item.querySelector(selector);
    }
    var link = own('[data-views-apply]');
    link.textContent = view.name;
    link.setAttribute('href', hrefOf(view));
    own('[data-views-rename-summary]').setAttribute('aria-label', 'Rename view ' + view.name);
    own('[data-views-delete-summary]').setAttribute('aria-label', 'Delete view ' + view.name);
    own('[data-views-delete]').setAttribute('aria-label', 'Yes, delete view ' + view.name);
    own('[data-views-delete-question]').textContent = 'Delete the view "' + view.name + '"? This cannot be undone.';
    var field = own('[data-views-rename-name]');
    field.value = view.name;
    // The template's label points at one id; every row needs its own, so the label and the field are paired again here.
    var fieldId = 'views-rename-' + index;
    field.id = fieldId;
    var fieldLabel = own('[data-views-rename-label]');
    fieldLabel.setAttribute('for', fieldId);
    fieldLabel.textContent = 'New name for view ' + view.name;
    var renameButton = own('[data-views-rename]');
    renameButton.addEventListener('click', function () {
      rename(view.name, field.value);
    });
    field.addEventListener('keydown', function (event) {
      if (event.key === 'Enter') {
        event.preventDefault();
        renameButton.click();
      } else if (event.key === 'Escape') {
        field.closest('details').open = false;
      }
    });
    own('[data-views-delete]').addEventListener('click', function () {
      remove(view.name);
    });
    return item;
  }

  function nameProblem(raw) {
    if (cleanName(raw) === null) {
      return 'Give the view a name of 1 to ' + MAX_NAME + ' characters, without control characters.';
    }
    return null;
  }

  function save() {
    var problem = nameProblem(nameInput.value);
    if (problem) {
      say(problem);
      return;
    }
    var name = cleanName(nameInput.value);
    var view = currentView(name);
    if (view === null) {
      say('The list as it is now cannot be saved as a view.');
      return;
    }
    var views = load();
    if (views === null) {
      return unavailable();
    }
    var at = indexOfName(views, name);
    if (at < 0 && views.length >= MAX_VIEWS) {
      say('You can keep at most ' + MAX_VIEWS + ' views for this list. Delete one to save another.');
      return;
    }
    if (at >= 0) {
      views[at] = view;
    } else {
      views.push(view);
    }
    if (!store(views)) {
      return unavailable();
    }
    nameInput.value = '';
    show(views);
    say((at >= 0 ? 'Replaced the view "' : 'Saved the view "') + name + '".');
  }

  function rename(oldName, raw) {
    var problem = nameProblem(raw);
    if (problem) {
      say(problem);
      return;
    }
    var name = cleanName(raw);
    var views = load();
    if (views === null) {
      return unavailable();
    }
    var at = indexOfName(views, oldName);
    if (at < 0) {
      show(views);
      return;
    }
    if (indexOfName(views, name, at) >= 0) {
      say('Another view is already called "' + name + '". Choose a different name.');
      return;
    }
    views[at].name = name;
    if (!store(views)) {
      return unavailable();
    }
    show(views);
    say('Renamed the view to "' + name + '".');
  }

  function remove(name) {
    var views = load();
    if (views === null) {
      return unavailable();
    }
    var at = indexOfName(views, name);
    if (at >= 0) {
      views.splice(at, 1);
      if (!store(views)) {
        return unavailable();
      }
    }
    show(views);
    say('Deleted the view "' + name + '".');
  }

  function unavailable() {
    saveButton.disabled = true;
    nameInput.disabled = true;
    say('This browser does not allow saved views here: site storage is blocked, full or unavailable. Everything else on this page still works.');
  }

  function refresh() {
    var views = load();
    if (views === null) {
      show([]);
      unavailable();
    } else {
      show(views);
    }
  }

  saveButton.addEventListener('click', save);
  nameInput.addEventListener('keydown', function (event) {
    if (event.key === 'Enter') {
      event.preventDefault();
      save();
    }
  });
  window.addEventListener('pageshow', refresh);
  window.addEventListener('storage', function (event) {
    if (event.key === KEY || event.key === null) {
      refresh();
    }
  });
  refresh();
})();
