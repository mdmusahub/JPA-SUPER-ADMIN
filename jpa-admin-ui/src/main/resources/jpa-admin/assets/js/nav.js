/*
 * JPA Admin: sidebar drawer.
 *
 * On narrow screens the sidebar is a drawer that [data-nav-toggle] opens and [data-nav-close] (the close button, the
 * backdrop) closes. Escape closes it, following a link in it closes it, and growing the window to the desktop layout
 * closes it. Focus moves into the drawer when it opens and back to the button that opened it when it closes.
 *
 * It also scrolls a long breadcrumb trail on a phone to its end, where the current page is.
 *
 * The page works without this file: the stylesheet only turns the sidebar into a drawer when theme.js has marked
 * <html> with the "js" class, and without scripts the sidebar stays above the content as plain links.
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
})();
