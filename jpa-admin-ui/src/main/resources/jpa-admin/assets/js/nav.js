/*
 * JPA Admin: sidebar drawer.
 *
 * On narrow screens the sidebar is a drawer that [data-nav-toggle] opens and [data-nav-close] (the close button, the
 * backdrop) closes. Escape closes it, following a link in it closes it, and growing the window to the desktop layout
 * closes it. Focus moves into the drawer when it opens and back to the button that opened it when it closes.
 *
 * It also scrolls a long breadcrumb trail on a phone to its end, where the current page is, filters the entity
 * list as you type in the sidebar's search box, and remembers which entity groups you closed.
 *
 * The page works without this file: the stylesheet only turns the sidebar into a drawer when theme.js has marked
 * <html> with the "js" class, without scripts the sidebar stays above the content as plain links, the groups open
 * and close by themselves because they are <details> elements, and the search box is hidden because it would do
 * nothing. The only thing stored in the browser is which groups are closed, under one key, in try/catch (UI-11).
 */
(function () {
  'use strict';

  var shell = document.querySelector('[data-shell]');
  var sidebar = document.getElementById('sidebar');
  if (!shell || !sidebar) {
    return;
  }

  var openers = document.querySelectorAll('[data-nav-toggle]');
  var closers = document.querySelectorAll('[data-nav-close]');
  var desktop = window.matchMedia('(min-width: 900px)');
  var lastOpener = null;

  // On a phone a long breadcrumb trail scrolls sideways (see pages.css). Start at its end, where the current page is.
  var trail = document.querySelector('.topbar > nav[aria-label="Breadcrumb"]');
  if (trail && trail.scrollWidth > trail.clientWidth) {
    trail.scrollLeft = trail.scrollWidth;
  }

  function isOpen() {
    return shell.getAttribute('data-nav-open') === 'true';
  }

  function setOpen(open, opener) {
    shell.setAttribute('data-nav-open', open ? 'true' : 'false');
    openers.forEach(function (button) {
      button.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    if (open) {
      lastOpener = opener || null;
      var first = sidebar.querySelector('a[href], button');
      if (first) {
        first.focus();
      }
    } else if (lastOpener) {
      lastOpener.focus();
      lastOpener = null;
    }
  }

  openers.forEach(function (button) {
    button.addEventListener('click', function () {
      setOpen(!isOpen(), button);
    });
  });

  closers.forEach(function (element) {
    element.addEventListener('click', function () {
      setOpen(false);
    });
  });

  sidebar.addEventListener('click', function (event) {
    if (event.target.closest('a[href]') && isOpen()) {
      setOpen(false);
    }
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && isOpen()) {
      setOpen(false);
    }
  });

  desktop.addEventListener('change', function (event) {
    if (event.matches && isOpen()) {
      setOpen(false);
    }
  });

  // ---- entity groups: remember which ones are closed -----------------------------------------------------------

  var GROUPS_KEY = 'jpa-admin.nav.closedGroups';
  var groups = Array.prototype.slice.call(sidebar.querySelectorAll('[data-nav-group]'));

  function readClosedGroups() {
    try {
      var stored = JSON.parse(window.localStorage.getItem(GROUPS_KEY));
      return Object.prototype.toString.call(stored) === '[object Array]' ? stored : [];
    } catch (error) {
      return [];
    }
  }

  function storeClosedGroups(names) {
    try {
      window.localStorage.setItem(GROUPS_KEY, JSON.stringify(names));
    } catch (error) {
      // Storage is unavailable: the groups then start open again on the next page.
    }
  }

  var closed = readClosedGroups();
  groups.forEach(function (group) {
    if (closed.indexOf(group.getAttribute('data-nav-group')) !== -1) {
      group.open = false;
    }
    group.addEventListener('toggle', function () {
      if (searching) {
        return;
      }
      var names = groups.filter(function (other) {
        return !other.open;
      }).map(function (other) {
        return other.getAttribute('data-nav-group');
      });
      storeClosedGroups(names);
    });
  });

  // ---- entity search: filter what is already on the page --------------------------------------------------------

  var search = sidebar.querySelector('[data-nav-search]');
  var nothingFound = sidebar.querySelector('[data-nav-empty]');
  var searching = false;

  function filter(term) {
    var text = term.trim().toLowerCase();
    searching = text.length > 0;
    var matches = 0;
    groups.forEach(function (group) {
      var items = Array.prototype.slice.call(group.querySelectorAll('[data-nav-item]'));
      var shown = 0;
      items.forEach(function (item) {
        var hit = !searching || item.getAttribute('data-nav-item').toLowerCase().indexOf(text) !== -1;
        item.hidden = !hit;
        if (hit) {
          shown++;
        }
      });
      matches += shown;
      group.hidden = shown === 0;
      if (searching) {
        group.open = true;
      } else if (closed.indexOf(group.getAttribute('data-nav-group')) !== -1) {
        group.open = false;
      }
    });
    if (nothingFound) {
      nothingFound.hidden = !searching || matches > 0;
    }
  }

  if (search) {
    search.addEventListener('input', function () {
      filter(search.value);
    });
    search.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && search.value !== '') {
        event.stopPropagation();
        search.value = '';
        filter('');
      }
    });
  }
})();
