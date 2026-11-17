/*
 * JPA Admin: bulk selection.
 *
 * The list page works without this file: the row checkboxes are plain checkboxes of a plain POST form, and "Delete
 * selected" is a details element with a submit button. This file only adds what a script can add:
 *   - the header checkbox "select all on this page" (it selects the rows of this page and no others),
 *   - the count of selected rows in the bulk bar, and the bar itself, which floats at the bottom and appears only while
 *     something is selected,
 *   - Escape closes the confirmation, and the count in the question follows the selection,
 *   - the selection is cleared whenever the page is shown (a new page, search, filter or sort is a new page; Back and the
 *     browser's page cache would otherwise bring an old selection back, and a selection that survived a filter change
 *     would delete rows the visitor can no longer see).
 *
 * It stores nothing and reads nothing but the form. It is no security measure: the server deletes only the ids posted,
 * checks the entity's flags, and caps a request at 100 records.
 */
(function () {
  'use strict';

  var form = document.querySelector('[data-bulk-form]');
  if (!form) {
    return;
  }

  var all = form.querySelector('[data-select-all]');
  var bar = form.querySelector('[data-bulk-bar]');
  var confirm = form.querySelector('[data-bulk-confirm]');

  function rows() {
    return Array.prototype.slice.call(form.querySelectorAll('[data-select-row]'));
  }

  function update() {
    var boxes = rows();
    var selected = boxes.filter(function (box) { return box.checked; }).length;
    Array.prototype.forEach.call(form.querySelectorAll('[data-bulk-count]'), function (node) {
      node.textContent = String(selected);
    });
    // The stylesheet keeps room under the content while the floating bar is shown, and none otherwise.
    if (selected === 0) {
      document.documentElement.removeAttribute('data-bulk-active');
    } else {
      document.documentElement.setAttribute('data-bulk-active', '');
    }
    if (bar) {
      if (selected === 0) {
        bar.setAttribute('data-empty', '');
        if (confirm) {
          confirm.open = false;
        }
      } else {
        bar.removeAttribute('data-empty');
      }
    }
    if (all) {
      all.checked = boxes.length > 0 && selected === boxes.length;
      all.indeterminate = selected > 0 && selected < boxes.length;
    }
  }

  function clear() {
    rows().forEach(function (box) { box.checked = false; });
    update();
  }

  form.addEventListener('change', function (event) {
    var target = event.target;
    if (target === all) {
      rows().forEach(function (box) { box.checked = all.checked; });
    }
    update();
  });

  form.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && confirm && confirm.open) {
      confirm.open = false;
      confirm.querySelector('summary').focus();
    }
  });

  // Also runs when the page comes back from the browser's cache or history: start with nothing selected.
  window.addEventListener('pageshow', clear);
  clear();
})();
