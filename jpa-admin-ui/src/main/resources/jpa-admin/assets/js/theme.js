/*
 * JPA Admin: theme.
 *
 * A small BLOCKING script in <head> (no async, no defer), so the theme is on the page before the first paint and the
 * page never flashes the wrong colours (UI-13). It is a static file, not an inline script, so a strict
 * Content-Security-Policy stays possible.
 *
 * What it does:
 *   1. Marks <html> with the "js" class, so the stylesheets can tell that scripts run.
 *   2. Applies the visitor's saved choice ("system", "light" or "dark"), or the server's default from the
 *      data-theme-default attribute of <html>. "system" removes data-theme, so the operating system decides.
 *   3. After the page is parsed, makes every [data-theme-toggle] button cycle system -> light -> dark.
 *
 * The only thing stored in the browser is that one choice, under one key. Storage can be missing or blocked, so every
 * access is in try/catch and the page works without it (UI-11).
 */
(function () {
  'use strict';

  var KEY = 'jpa-admin.theme';
  var CHOICES = ['system', 'light', 'dark'];
  var root = document.documentElement;

  root.classList.add('js');

  function isChoice(value) {
    return CHOICES.indexOf(value) !== -1;
  }

  function readStoredChoice() {
    try {
      var stored = window.localStorage.getItem(KEY);
      return isChoice(stored) ? stored : null;
    } catch (error) {
      return null;
    }
  }

  function storeChoice(choice) {
    try {
      window.localStorage.setItem(KEY, choice);
    } catch (error) {
      // Storage is unavailable: the choice then lasts until the page is closed.
    }
  }

  function apply(choice) {
    if (choice === 'system') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', choice);
    }
    root.setAttribute('data-theme-choice', choice);
  }

  function next(choice) {
    return CHOICES[(CHOICES.indexOf(choice) + 1) % CHOICES.length];
  }

  function describe(choice) {
    return 'Theme: ' + choice + '. Switch to ' + next(choice) + '.';
  }

  var serverDefault = root.getAttribute('data-theme-default');
  var current = readStoredChoice() || (isChoice(serverDefault) ? serverDefault : 'system');
  apply(current);

  document.addEventListener('DOMContentLoaded', function () {
    var buttons = document.querySelectorAll('[data-theme-toggle]');

    function refresh() {
      buttons.forEach(function (button) {
        button.setAttribute('aria-label', describe(current));
        button.setAttribute('title', describe(current));
      });
    }

    buttons.forEach(function (button) {
      button.addEventListener('click', function () {
        current = next(current);
        apply(current);
        storeChoice(current);
        refresh();
      });
    });
    refresh();
  });
})();
