/*
 * JPA Admin: filter form.
 *
 * The search box and the filter controls are one plain GET form (see list.html). A browser sends every control of a GET
 * form, empty ones too, so without this file the address would grow a "filter.x=" for each control the visitor never
 * touched. The server ignores those without a message, so nothing breaks; this file only keeps the address short:
 * when the form is submitted it switches off the filter controls that are empty, so the browser leaves them out.
 *
 * It switches them back on when the page is shown again (Back, or the browser's page cache), so a control is never left
 * disabled. The page works without this file. It stores nothing and reads nothing but the form.
 */
(function () {
  'use strict';

  var form = document.querySelector('[data-filter-form]');
  if (!form) {
    return;
  }

  function filterControls() {
    return form.querySelectorAll('[name^="filter."]');
  }

  form.addEventListener('submit', function () {
    Array.prototype.forEach.call(filterControls(), function (control) {
      if (control.value === '') {
        control.disabled = true;
      }
    });
  });

  window.addEventListener('pageshow', function () {
    Array.prototype.forEach.call(filterControls(), function (control) {
      control.disabled = false;
    });
  });
})();
