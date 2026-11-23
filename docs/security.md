# Security

An admin console can read and change every record of the application that hosts it, so the console is built to be safe by default and to say plainly what it leaves to you. This page lists what it does, what it does not do, and what you should do.

To report a vulnerability, **do not open a public issue**: see [SECURITY.md](https://github.com/mdmusahub/JPA-SUPER-ADMIN/blob/main/SECURITY.md) (GitHub private vulnerability reporting, or email).

## What the console does

### It keeps out of your security

The console has its **own** Spring Security filter chain, matched to the console path (`/admin` by default) only and registered before every other chain. It never changes, replaces or weakens your application's own security configuration. Everything under the console path needs the console's administrator, except the sign-in page and the console's own stylesheets and scripts.

### Sign-in and sessions

- One administrator, from `jpa.admin.username` and `jpa.admin.password`. The password is hashed with BCrypt in memory at startup and is never logged or printed; BCrypt reads at most 72 bytes, so a longer password stops startup.
- A wrong username and a wrong password get exactly the same answer, and nothing the visitor typed is put in the address.
- Signing out is a `POST`, so a link, an image or a prefetch can never sign the administrator out. It removes only the console's own session data and ends the session only when nothing else is in it, so a login of your own application in the same browser stays.
- The console keeps its sign-in, its CSRF token and the page you wanted under its own names (`JPA_ADMIN_...`) in the shared session, so it and your application never read or overwrite each other's.
- **The console does not change any global session or cookie setting**, because those would change your application too. Set `server.servlet.session.cookie.secure=true` and `server.servlet.session.cookie.same-site=lax` yourself (see [Deployment](deployment.md)).

### CSRF

Every request that changes something needs the CSRF token: forms carry it, and script sends it in a header. A request without it is refused with the console's own "access denied" page.

### Response headers

Every console page carries:

| Header | Value |
|---|---|
| `Content-Security-Policy` | `default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'`: no inline script and no inline style, so injected markup cannot run |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `no-referrer` |
| `X-Content-Type-Options` | `nosniff` |

The console sends **no `Strict-Transport-Security`**: HSTS covers the whole host name, not only the console path, so it is your decision for the whole site.

### Sensitive fields

A field named like one in `jpa.admin.sensitive-fields` (default `password, secret, token, apikey`; the match ignores case, `_` and `-` and is a substring match), or marked `@AdminSensitive`, never appears in a list, a detail page, a form value, the CSV export, a log line or an error message. It is shown as dots; on an edit form a blank box keeps the stored value. The match is deliberately a little too wide (a field called `tokenizer` is masked); narrow the list if that is not wanted.

### Everything from the address is untrusted

Sort, search, filter and column names are accepted only if they name something in the entity's metadata that is visible and not sensitive; anything else is ignored. Queries are built with the JPA Criteria API and bound parameters, never by joining text. Values from the database are escaped when they are shown (the console never uses unescaped output for stored data). A cell of the CSV export that starts with `=`, `+`, `-` or `@` gets a quote in front so a spreadsheet does not run it. A database error is turned into one plain sentence: no SQL, table name or stack trace reaches the browser.

### The rules are enforced on the server

`readOnly`, `creatable`, `updatable`, `deletable` and `exportable` are checked in the service layer, not only by hiding a button: a forged request is refused (403 for a visible entity, 404 for a hidden one).

### Uploads

A file is trusted for what its first bytes say and for nothing else. An upload needs the signed-in administrator, the CSRF token **and** a signed upload ticket (valid about 10 minutes, bound to one entity and field). The file is stored under a random name that is never the client's, below a folder outside the web root that no link or path trick can leave. It is served with `nosniff`, a sandboxing policy and as a download unless it is an image. SVG and HTML are never accepted. Details: [File uploads](file-uploads.md).

## What it does not do (yet)

- **No login rate limiting.** Planned for version 0.2. Until then, keep the console off the open internet (see the checklist).
- **One administrator, no roles, no audit log.** Planned for later versions.
- **It does not make your connection private.** HTTPS is yours to set up. Under the `prod` profile the console logs a warning at startup when nothing says it is reached over HTTPS.

## The `prod` profile

Under the Spring profile `prod`, a password shorter than 12 characters or on a list of common passwords **stops startup**, and a console served over plain HTTP logs a warning. Under other profiles a weak password logs a warning.

## Hardening checklist

- [ ] The password comes from a secret store or an environment variable (`${ADMIN_PASSWORD}`), not from a file in your repository, and is at least 12 characters.
- [ ] The application is served over **HTTPS**, with `Secure` and `SameSite` set on the session cookie.
- [ ] The console is reachable only from where it must be: a **VPN or an IP allow-list** in front of `/admin`, especially if the application faces the internet.
- [ ] Entities that hold secrets are hidden (`@AdminHidden`, `jpa.admin.excluded-entities`) or read-only (`@AdminResource(readOnly = true)`).
- [ ] Upload storage is a folder outside the web root that is backed up with the database; `jpa.admin.upload.signing-key` is set from a secret (and is the same on every instance).
- [ ] The Hibernate loggers `org.hibernate.orm.jdbc.bind` and `org.hibernate.orm.jdbc.extract` are **not** at TRACE in production: at TRACE they print every bound or read value, including a typed password. (The console's own log lines never hold a value.)
- [ ] The demo settings are not in the production configuration, and the application and its dependencies are kept up to date.
