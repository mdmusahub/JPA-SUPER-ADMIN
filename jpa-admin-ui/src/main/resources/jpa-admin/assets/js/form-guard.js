/*
 * JPA Admin: unsaved-changes guard of the create and edit form.
 *
 * The form works without this file: it is a plain POST form, and "Cancel" is a details element that asks "Discard your
 * changes?" and holds the real link. This file only adds what a script can add:
 *   - the browser's own warning when the visitor leaves a form they changed by another way than Save or "Yes, discard
 *     changes" (a link in the sidebar, the Back button, closing the tab),
 *   - "Cancel" goes straight back when nothing was changed (there is nothing to discard),
 *   - focus moves to the first field with an error, or to the summary at the top, when the page is shown again after a
 *     refused save (the same typed values are on the page, so that form counts as changed),
 *   - a finished upload, a replaced file and a removed file count as a change ("jpa-admin:upload-changed", sent by uploader.js):
 *     the file is not saved until the record is.
 *
 * The Save button and the Cancel question sit outside the form element (the upload sections are between them, and a form cannot
 * hold another form), so they are looked up on the document.
 *
 * It stores nothing, reads nothing but the form, and never reads or sends a value: it only watches for input events. It is
 * no security measure; the server checks everything again.
 */
(function () {
  'use strict';

  var form = document.querySelector('[data-form-guard]');
  if (!form) {
    return;
  }

  var summary = form.querySelector('[data-form-summary]');
  var discard = document.querySelector('[data-form-discard]');
  var cancel = document.querySelector('[data-form-cancel] > summary');
  var dirty = summary !== null; // a page shown again after a refused save holds what the visitor typed
  var leaving = false;

  function markChanged() {
    dirty = true;
  }

  form.addEventListener('input', markChanged);
  form.addEventListener('change', markChanged);
  document.addEventListener('jpa-admin:upload-changed', markChanged);

  // Saving is not leaving: the server answers, and the next page is not this form.
  form.addEventListener('submit', function () {
    leaving = true;
  });

  if (discard) {
    discard.addEventListener('click', function () {
      leaving = true; // the visitor was asked and said yes
    });
  }

  // Nothing changed: Cancel needs no question.
  if (cancel && discard) {
    cancel.addEventListener('click', function (event) {
      if (!dirty) {
        event.preventDefault();
        leaving = true;
        window.location.href = discard.href;
      }
    });
  }

  window.addEventListener('beforeunload', function (event) {
    if (dirty && !leaving) {
      event.preventDefault();
      event.returnValue = '';
    }
  });

  // The page can come back from the browser's cache with the form as it was left; it starts clean again.
  window.addEventListener('pageshow', function (event) {
    if (event.persisted) {
      leaving = false;
    }
  });

  var invalid = form.querySelector('[aria-invalid="true"]');
  var target = invalid || summary;
  if (target && typeof target.focus === 'function') {
    target.focus();
  }
})();
