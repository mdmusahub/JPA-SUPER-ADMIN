# Endpoints

These are the routes of the console. They serve the console's own pages and script, and **they are not a stable public API in version 0.1**: do not build another application on them.

Upload routes. `POST {path}/uploads`, `multipart/form-data`, parts `file`, `ticket`, `entity`, `field` and, for the plain form, `record` (the id of the record being edited, used only to build the way back). Needs the signed-in admin and the CSRF token. With `Accept: application/json`: status 200 and `{"url":"...","name":"...","size":N,"error":null}`, or the refusal's status and `{"url":null,"name":null,"size":0,"error":"..."}`; statuses: 400 nothing to store or not multipart, 403 bad, expired or missing ticket, 413 too large (the field's, the storage's or the host's limit), 415 wrong content, 500 not stored. Without that header: 303 to the form page with a one-time message, or the console's error page with the refusal's status. `GET {path}/files/{storage}/{key}` serves a stored file to the signed-in admin (404 for anything that is not one); with a `public-path`, `GET {public-path}/{key}` serves it to everyone.

Export route. `GET {path}/{entity}/export.csv` with the list's `q`, `filter.*`, `sort` and `dir` (a `page` or `size` is ignored) returns the CSV file of that list as an attachment (see "CSV export" under "Using the console"). It needs the signed-in admin and answers GET only (a POST is refused with the console's 404, or 403 without the CSRF token). An unknown or hidden entity is the console's 404, identical in both cases; an entity or a console with the export switched off is 403. A file cut at the cap carries `X-Export-Truncated: true` and `X-Export-Row-Limit`. An entity whose identifier is the text `export.csv` cannot open its detail page, as one named `new` cannot.

Chart route. `GET {path}/api/stats?range=7|30|90` returns the data of the dashboard chart as JSON (see "The Records created chart" under "Using the console"). It needs the signed-in admin, answers GET only (a POST is refused with the console's 404, or 403 without the CSRF token), reads only the creation column of the entities that have one, and replaces a `range` that is not 7, 30 or 90 by 30 and says so in `notes`. The body is written by the console itself, so the host's JSON settings do not change it.

## All routes

`{path}` is the console path, `/admin` by default (`jpa.admin.path`).

| Method | Path | Purpose |
|---|---|---|
| GET | `{path}` | Dashboard |
| GET/POST | `{path}/login`, POST `{path}/logout` | Auth |
| GET | `{path}/{entity}` | List (`q`, `filter.*`, `hide`, `sort`, `dir`, `page`, `size`) |
| GET | `{path}/{entity}/export.csv` | CSV export of the current query (`q`, `filter.*`, `hide`, `sort`, `dir`) |
| GET | `{path}/{entity}/new` | Create form (`?copyFrom={id}` for duplicate) |
| POST | `{path}/{entity}` | Create |
| GET | `{path}/{entity}/{id}` | Detail |
| GET/POST | `{path}/{entity}/{id}/edit`, `{path}/{entity}/{id}` | Edit and update |
| POST | `{path}/{entity}/{id}/delete`, `{path}/{entity}/bulk-delete` | Delete |
| POST | `{path}/uploads` | Upload a file (needs session, CSRF and a valid upload ticket). Returns the stored URL as JSON for `Accept: application/json`; a plain form gets a redirect back to the form page, or an error page |
| GET | `{path}/files/{storage}/{key}` | Authenticated file serving for local storage. With a `public-path`, also `{public-path}/{key}` without a session |
| GET | `{path}/api/stats?range=30` | Chart data (JSON) |
| GET | `{path}/assets/**` | CSS, JS, images from the jar |
