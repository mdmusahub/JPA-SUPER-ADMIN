# Deployment

Putting the console on a real server is mostly about three things: HTTPS, who can reach it, and where uploaded files live.

## HTTPS and the session cookie

Serve the application over HTTPS, either with TLS in the application (`server.ssl.*`) or by terminating TLS in a reverse proxy. Behind a proxy, make the application trust the proxy's forwarded headers so it sees requests as HTTPS:

```yaml
server:
  forward-headers-strategy: native        # or: framework
  servlet:
    session:
      cookie:
        secure: true
        same-site: lax
```

The console never changes the cookie settings (they are global and would change your application too), so set `secure` and `same-site` yourself. Under the `prod` profile the console logs one warning at startup when the configuration says nothing about HTTPS (no `server.ssl.*` and no trusted forwarded headers); the warning goes away once one of them is set.

## Who can reach it

Put the console behind a **VPN or an IP allow-list** whenever the application faces the internet. The console has one administrator and no login rate limiting yet (planned for 0.2), so network access is your main protection. A reverse proxy rule for `/admin` is enough.

If your application has its own Spring Security rules, leave them as they are: the console's chain is separate and is matched first, only for its own path.

## The console path

`jpa.admin.path` (default `/admin`) can be changed, for example to `/backoffice`. A **servlet path prefix** (`spring.mvc.servlet.path`) is not supported in 0.1: leave it unset, otherwise the console answers 500 and logs why.

## More than one instance

- Set **`jpa.admin.upload.signing-key`** (at least 32 characters, from a secret) to the **same value** on every instance. Unset, each instance makes a random key at start, so an upload ticket issued by one instance is refused by another and a restart invalidates the forms that are open.
- The console keeps its sign-in in the HTTP session, so the session handling you already use for your application (sticky sessions, or a shared session store) decides how sign-in behaves across instances.
- Local upload storage is a folder on one machine. With several instances, point `jpa.admin.storage.default.directory` at a shared volume, or write your own `StorageProvider` bean (the S3 provider is planned for 0.2).

## Uploaded files

- The storage folder must be **outside the web root**; startup stops when it is not, and when it cannot be created or written to.
- **Back it up with the database**: the database holds only the address of each file.
- Replacing or removing a file keeps the old file on disk in 0.1, so unreferenced files accumulate. There is no automatic cleanup.
- The host's multipart limits (`spring.servlet.multipart.max-file-size` and `max-request-size`; Spring Boot 4.1 defaults are 1MB and 10MB) apply first. The console never changes them; raise them if you accept larger files.
- A `public-path` serves a storage's files to everyone without a sign-in. It is off by default and is protected only by your own security rules.

## Before you go live

Work through the [hardening checklist](security.md#hardening-checklist). In short: a secret password, HTTPS with secure cookies, network access limited, sensitive entities hidden or read-only, upload storage private and backed up, and Hibernate's value-printing loggers off.

## Large tables

Search is a case-insensitive "contains" and reads the table: on a table of hundreds of thousands of rows it takes a noticeable fraction of a second, and `count(*)` is not free. Index the columns you sort by; on PostgreSQL a `pg_trgm` index per searched column makes a rare search about ten times faster. The measurements and the advice are on [Limitations and roadmap](limitations-and-roadmap.md).
