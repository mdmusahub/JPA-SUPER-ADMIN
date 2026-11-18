/*
 * JPA Admin: the uploader of a create or edit form (task U6, D-95, D-98).
 *
 * Every upload field works without this file: its section holds a plain multipart form that posts the file to the upload
 * endpoint, and the page is shown again with the new address in the field. This file only adds what a script can add, so the
 * page is not reloaded and nothing typed in the record form is lost:
 *   - drag and drop, or choosing a file, starts the upload at once;
 *   - the states idle, dragging, uploading (with a progress bar), done and error, shown in the section's data-state;
 *   - the address of the stored file goes into the record form's hidden input, and a preview (an image) or a link (anything
 *     else) replaces the old one;
 *   - "Replace file" and "Remove file" buttons (the plain form has a "remove" checkbox instead);
 *   - while a file is on its way the record form's Save button is disabled, and the form cannot be sent (Enter in a text
 *     input included), so a record is never saved with an upload half done;
 *   - a finished upload, a replace and a remove are announced on document as "jpa-admin:upload-changed", so form-guard.js
 *     counts them as unsaved changes.
 *
 * THE BROWSER CHECKS ARE ADVISORY. The type and the size are checked here only to say so early; the server checks the ticket, the
 * size, the real type of the file, hidden markup and everything else again and decides (UPL-1, UPL-2). A file this script lets
 * through can still be refused, and the refusal is shown in the same place.
 *
 * It sends the CSRF token in the request header and the field's ticket with the file. It stores nothing in the browser (no web
 * storage of any kind, no cookie). It reads nothing from the page but the upload forms, and it builds every element with
 * createElement and textContent, never with markup from a string, so a name or an address from the server is only ever text.
 */
(function () {
  'use strict';

  var sections = document.querySelectorAll('[data-upload]');
  if (!sections.length || !window.XMLHttpRequest || !window.FormData) {
    return;
  }

  var recordForm = document.getElementById('record-form');
  var submitButton = document.querySelector('[data-form-submit]');
  var running = 0;
  var IMAGE = /\.(png|jpg|gif|webp)$/i;
  var NAMES = {
    'image/png': 'PNG images',
    'image/jpeg': 'JPEG images',
    'image/gif': 'GIF images',
    'image/webp': 'WebP images',
    'application/pdf': 'PDF documents'
  };

  function size(bytes) {
    if (bytes < 1024) {
      return bytes + ' bytes';
    }
    var kb = bytes / 1024;
    if (kb < 1024) {
      return Math.round(kb * 10) / 10 + ' KB';
    }
    return Math.round(kb / 1024 * 10) / 10 + ' MB';
  }

  function updateSave() {
    if (!submitButton) {
      return;
    }
    submitButton.disabled = running > 0;
    if (running > 0) {
      submitButton.setAttribute('aria-disabled', 'true');
    } else {
      submitButton.removeAttribute('aria-disabled');
    }
  }

  // The record form is not sent while a file is on its way. Capture phase on the document, so it runs before every other
  // listener (form-guard.js must not think the visitor is leaving).
  document.addEventListener('submit', function (event) {
    if (running > 0 && event.target === recordForm) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }, true);

  function announce(key) {
    document.dispatchEvent(new CustomEvent('jpa-admin:upload-changed', { detail: { key: key } }));
  }

  function setup(section) {
    var key = section.getAttribute('data-upload-key');
    var form = section.querySelector('[data-upload-form]');
    var input = section.querySelector('[data-upload-input]');
    var drop = section.querySelector('[data-upload-drop]');
    var current = section.querySelector('[data-upload-current]');
    var status = section.querySelector('[data-upload-status]');
    var progress = section.querySelector('[data-upload-progress]');
    var actions = section.querySelector('[data-upload-actions]');
    var replace = section.querySelector('[data-upload-replace]');
    var remove = section.querySelector('[data-upload-remove]');
    var removeBox = section.querySelector('[data-upload-remove-check] input');
    var heading = section.querySelector('.upload-title label span');
    var label = heading ? heading.textContent : '';
    var accept = (section.getAttribute('data-accept') || '').split(',').filter(function (type) { return type; });
    var max = parseInt(section.getAttribute('data-max-bytes'), 10) || 0;
    var csrfHeader = section.getAttribute('data-csrf-header') || 'X-CSRF-TOKEN';
    var value = null;
    var uploading = false;

    if (!form || !input || !recordForm || !current || !status) {
      return;
    }
    var hidden = recordForm.querySelectorAll('[data-upload-value]');
    for (var i = 0; i < hidden.length; i++) {
      if (hidden[i].name === key) {
        value = hidden[i];
      }
    }
    if (!value) {
      return;
    }

    function state(name) {
      section.setAttribute('data-state', name);
    }

    function say(text) {
      status.textContent = text;
    }

    function hasFile() {
      return value.value !== '';
    }

    function showActions() {
      if (actions) {
        actions.hidden = !hasFile();
      }
    }

    function renderCurrent(url) {
      while (current.firstChild) {
        current.removeChild(current.firstChild);
      }
      if (!url) {
        var empty = document.createElement('p');
        empty.className = 'upload-empty';
        empty.textContent = 'No file yet.';
        current.appendChild(empty);
        return;
      }
      if (IMAGE.test(url)) {
        var image = document.createElement('img');
        image.className = 'upload-preview';
        image.alt = label;
        image.addEventListener('error', function () {
          var note = document.createElement('p');
          note.className = 'upload-empty';
          note.textContent = label + ': the file could not be shown.';
          if (image.parentNode) {
            image.parentNode.replaceChild(note, image);
          }
        });
        image.src = url;
        current.appendChild(image);
      } else {
        var link = document.createElement('a');
        link.className = 'cell-link cell-file';
        link.href = url;
        link.textContent = label;
        current.appendChild(link);
      }
    }

    function finish() {
      if (uploading) {
        uploading = false;
        running -= 1;
        updateSave();
      }
      if (progress) {
        progress.hidden = true;
        progress.value = 0;
      }
    }

    function fail(message) {
      finish();
      state('error');
      say(message);
    }

    // Only a path on this site is ever put in the page: the server made the address, and this is a second look at it.
    function safeAddress(url) {
      return typeof url === 'string' && url.charAt(0) === '/' && url.charAt(1) !== '/' && url.indexOf('\\') < 0;
    }

    function done(result) {
      finish();
      if (!safeAddress(result.url)) {
        state('error');
        say('The server sent an address this page cannot use. Reload the page and try again.');
        return;
      }
      value.value = result.url;
      if (removeBox) {
        removeBox.checked = false;
      }
      renderCurrent(result.url);
      input.value = '';
      state('done');
      var name = typeof result.name === 'string' ? result.name : 'The file';
      say(name + ' (' + size(result.size || 0) + ') is uploaded. It is saved with the record when you save.');
      showActions();
      announce(key);
    }

    function advice(file) {
      if (file.size === 0) {
        return 'The file is empty. Choose a file that has content.';
      }
      if (max > 0 && file.size > max) {
        return 'The file is ' + size(file.size) + ', more than the ' + size(max) + ' allowed for this field. Choose a smaller file.';
      }
      if (accept.length && file.type && accept.indexOf(file.type) < 0) {
        var names = accept.map(function (type) { return NAMES[type] || type; });
        return 'This kind of file is not accepted here. This field accepts ' + names.join(', ') + '.';
      }
      return '';
    }

    function token() {
      var fields = form.querySelectorAll('input[type="hidden"]');
      var other = { entity: 1, field: 1, ticket: 1, record: 1 };
      for (var n = 0; n < fields.length; n++) {
        if (!other[fields[n].name]) {
          return fields[n].value;
        }
      }
      return '';
    }

    function upload(file) {
      if (uploading) {
        return;
      }
      var problem = advice(file);
      if (problem) {
        state('error');
        say(problem);
        input.value = '';
        return;
      }
      uploading = true;
      running += 1;
      updateSave();
      state('uploading');
      say('Uploading ' + file.name + '...');
      if (progress) {
        progress.hidden = false;
        progress.value = 0;
      }
      var data = new FormData(form);
      data.set('file', file);
      var request = new XMLHttpRequest();
      request.open('POST', form.getAttribute('action'));
      request.setRequestHeader(csrfHeader, token());
      request.setRequestHeader('Accept', 'application/json');
      request.upload.addEventListener('progress', function (event) {
        if (progress && event.lengthComputable) {
          progress.value = Math.round(event.loaded / event.total * 100);
        }
      });
      request.addEventListener('load', function () {
        var result = null;
        try {
          result = JSON.parse(request.responseText);
        } catch (notJson) {
          result = null;
        }
        if (request.status === 200 && result && !result.error && result.url) {
          done(result);
        } else if (result && typeof result.error === 'string' && result.error) {
          fail(result.error);
        } else if (request.status === 413) {
          fail('The file is larger than this server accepts. Choose a smaller file.');
        } else if (request.status === 403) {
          fail('This page is no longer valid. Reload the page and try again.');
        } else {
          fail('The upload failed (status ' + request.status + '). Try again, or reload the page.');
        }
      });
      request.addEventListener('error', function () {
        fail('The upload could not be sent. Check your connection and try again.');
      });
      request.addEventListener('abort', function () {
        fail('The upload was cancelled.');
      });
      request.send(data);
    }

    input.addEventListener('change', function () {
      if (input.files && input.files.length) {
        upload(input.files[0]);
      }
    });

    // Enter in the file input of the plain form must not reload the page when the script is on.
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      if (input.files && input.files.length) {
        upload(input.files[0]);
      }
    });

    if (drop) {
      ['dragenter', 'dragover'].forEach(function (name) {
        drop.addEventListener(name, function (event) {
          event.preventDefault();
          if (!uploading) {
            state('dragging');
          }
        });
      });
      drop.addEventListener('dragleave', function () {
        if (!uploading) {
          state('idle');
        }
      });
      drop.addEventListener('drop', function (event) {
        event.preventDefault();
        if (uploading) {
          return;
        }
        state('idle');
        var files = event.dataTransfer && event.dataTransfer.files;
        if (files && files.length) {
          upload(files[0]);
        }
      });
    }

    if (replace) {
      replace.addEventListener('click', function () {
        input.click();
      });
    }
    if (remove) {
      remove.addEventListener('click', function () {
        value.value = '';
        if (removeBox) {
          removeBox.checked = true;
        }
        renderCurrent('');
        state('idle');
        say('The file is removed when you save the record.');
        showActions();
        announce(key);
      });
    }
    showActions();
  }

  for (var s = 0; s < sections.length; s++) {
    setup(sections[s]);
  }
})();
