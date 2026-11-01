# RULES.md

Engineering rules for JPA Super Admin. Every rule has an ID so reviews can cite it ("violates SEC-4"). Rules marked **MUST** are never skipped. Rules marked **SHOULD** can be broken with a recorded reason in `docs/DECISIONS.md`.

## 1. Security (SEC)

- **SEC-1 MUST** Sensitive fields (names in `jpa.admin.sensitive-fields` plus `@AdminSensitive`) never appear in list, detail, form values, CSV export, JSON, logs or error messages. A `FormView` field for a sensitive property has an input type of `masked` and carries no value; a blank submit on edit keeps the stored value. View-model records never hold a sensitive value.
- **SEC-2 MUST** Sort, search, filter and column parameters are untrusted. Accept only names that exist in the entity metadata and are not hidden or sensitive.
  Sensitive matching is case-insensitive, ignores `_` and `-`, and uses substring match against `jpa.admin.sensitive-fields`, plus `@AdminSensitive`.
- **SEC-3 MUST** Never concatenate user input into JPQL, SQL or Criteria strings. Use the Criteria API and bound parameters.
- **SEC-4 MUST** The console uses its own `SecurityFilterChain`, matched to the console path only. It must not change, replace or weaken the host application's security configuration.
- **SEC-5 MUST** CSRF protection stays enabled for every state-changing request. Forms include the token. JavaScript requests send it in a header.
- **SEC-6 MUST** Passwords are BCrypt-hashed in memory. Never log the password or the hash.
- **SEC-7 MUST** `jpa.admin.username` and `jpa.admin.password` are validated at startup. Missing values stop the application with a message that names the property. Under the `prod` profile, a password under 12 characters or a common one also stops startup; otherwise it logs a warning.
- **SEC-8 MUST** Do not change global session cookie settings; rely on Spring Boot defaults so the host app is unaffected. Document that host apps should set `Secure` and `SameSite`. Log a startup warning when the console runs over plain HTTP under the `prod` profile.
- **SEC-9 MUST** Escape all database values on output. Never use `th:utext` on stored data.
- **SEC-10 MUST** CSV cells that begin with `=`, `+`, `-` or `@` are prefixed so spreadsheets do not execute them.
- **SEC-11 MUST** Server-side flags (`readOnly`, `creatable`, `updatable`, `deletable`, `exportable`, hidden) are enforced in the service layer, not just hidden in the UI.
- **SEC-12 MUST** Database exceptions are translated to friendly messages. No SQL, table names or stack traces reach the browser.
- **SEC-13 SHOULD** Add security response headers on console pages (`X-Content-Type-Options: nosniff`, frame protection, a restrictive referrer policy).
- **SEC-14 MUST** Console path is validated against a strict pattern and cannot be `/`.
- **SEC-15 MUST** Theme and colour values are validated (`#rrggbb` only) and reach the browser only through a generated stylesheet, never through inline `style` attributes, inline `<style>` blocks or inline scripts.

## 2. Uploads (UPL)

- **UPL-1 MUST** Check type by an allow-list and by content sniffing, not only by the filename or the `Content-Type` header.
- **UPL-2 MUST** Enforce a maximum size per field, with a global ceiling.
- **UPL-2a MUST** `POST /admin/uploads` requires a valid signed upload ticket (about 10 minutes, bound to entity and field, carrying max size and expiry, signed with `jpa.admin.upload.signing-key`) in addition to the session and CSRF token.
- **UPL-3 MUST** Store under server-generated random names in `folder/yyyy/MM/`. Never use the client's filename as a path.
- **UPL-4 MUST** Path traversal must be impossible by construction (resolve and verify the final path is inside the storage root).
- **UPL-5 MUST** SVG and HTML uploads are rejected unless explicitly allowed in the field's `accept` list.
- **UPL-6 MUST** Serve uploaded files with the detected content type, `nosniff`, and `Content-Disposition: attachment` for anything that is not an image.
- **UPL-7 MUST** Local files are served only to authenticated admins unless `public-path` is explicitly configured.
- **UPL-8 MUST** Startup fails when an `@AdminUpload` field names a storage that is not configured, and lists the fields.
- **UPL-9 SHOULD** Replacing or removing a file keeps the old file in v0.1. Document this limitation.
- **UPL-10 MUST** Storage access goes through the `StorageProvider` interface so providers can be added without touching controllers.

## 3. Architecture and code (ARCH, CODE)

- **ARCH-1 MUST** Entity metadata is read once at startup from the JPA metamodel and held in immutable records.
- **ARCH-2 MUST** Controllers are thin. Logic lives in services. Controllers never touch `EntityManager`.
- **ARCH-3 MUST** Never put raw entities in the model or serialize them. Convert to display maps first.
- **ARCH-4 MUST** Never render collections in tables. Show relation labels only.
- **ARCH-5 MUST** Read services use `@Transactional(readOnly = true)`. Write services use `@Transactional` and roll back on validation failure.
- **ARCH-6 MUST** Avoid N+1 queries when showing relation labels (fetch or batch).
- **ARCH-7 MUST** Auto-configuration is conditional (`@ConditionalOnClass`, servlet web app, property switch) and backs off cleanly. Use `@ConditionalOnMissingBean` for replaceable beans.
- **ARCH-8 MUST** Do not leak bean names or packages that can clash with the host app. Prefix beans and keep templates under `jpa-admin/`.
- **ARCH-9 SHOULD** Keep the public API (annotations, properties, SPI interfaces) small. Everything else is package-private. Exception: the view-model records in `io.github.mdmusahub.jpaadmin.web.view` are public because Thymeleaf and `jpa-admin-ui-preview` need them. They are internal, not stable API, and their Javadoc says so.
- **ARCH-10 MUST** CSV export and list use the same validated query pipeline. Export streams rows in pages and respects the row cap. The cap has a fixed internal ceiling of 50,000 rows; `jpa.admin.export-max-rows` may lower it, never raise it.
- **ARCH-11 MUST** Preview code (`PreviewController` and fixtures) lives only in the separate, unpublished module `jpa-admin-ui-preview`. It is never a dependency of the starter, `sample-app` or any published module, and the build checks this (Maven Enforcer banned-dependency rule). The preview app fails startup when the `prod` profile is active.
- **CODE-1 MUST** Java 17 minimum, Spring Boot 4.1.x, Jakarta namespace, no deprecated Spring APIs.
- **CODE-2 SHOULD** Prefer records and constructor injection. No field injection.
- **CODE-3 MUST** No `System.out`. Use SLF4J. Never log request bodies.
- **CODE-4 SHOULD** Methods stay short and named for what they do. No clever generics where plain code reads better.
- **CODE-5 MUST** No new dependency without a recorded reason. Prefer what Spring Boot already brings.
- **CODE-6 MUST** No Node, npm or front-end build step. JavaScript is plain and lives in static files.
- **CODE-7 SHOULD** Add Javadoc to every public type and method of the public API.
- **CODE-8 MUST** Verify Spring Boot, Spring Security, Spring Data JPA, Hibernate and Thymeleaf APIs, and Spring Boot auto-configuration packages and class names, against the official Spring Boot 4.1 documentation or the library source for the pinned version. Do not rely on memory or on Boot 3.x examples; Boot 4 reorganised modules and packages.

## 4. UI (UI)

- **UI-1 MUST** Templates live in the module `jpa-admin-ui` under `templates/jpa-admin/` and share one layout fragment. Assets live under `META-INF/resources/jpa-admin/assets/` in the same module.
- **UI-2 MUST** All pages work without external network access (no CDN, no remote fonts or scripts).
- **UI-3 MUST** Pages are responsive down to phone width and work in light and dark themes through CSS variables.
- **UI-4 MUST** Controls have visible keyboard focus and accessible labels. Icon-only buttons have `aria-label`.
- **UI-5 MUST** Destructive actions always ask for confirmation.
- **UI-6 MUST** Every list has an empty state that explains what to do next. Every error message says what went wrong and how to fix it.
- **UI-7 SHOULD** Use the HTML prototype (`docs/prototype/admin-console.html`, reference only, not shipped) as the visual reference: grouped sidebar, breadcrumbs, stat cards, badges, floating bulk bar.
- **UI-8 MUST** Respect `prefers-reduced-motion`. No decorative animation.
- **UI-9 SHOULD** Use sentence case, active verbs ("Save changes", not "Submit"), and the same name for an action through its whole flow.
- **UI-10 MUST** Table state (search, filters, sort, page) lives in the URL so pages are bookmarkable and Back works.
- **UI-11 SHOULD** Saved views, theme and column choices live in `localStorage`, wrapped in try/catch, and the page works when storage is unavailable.
- **UI-12 MUST** Templates may only read view-model records (`web.view`). They never call services, repositories or `EntityManager`, and never receive entities, collections of entities or sensitive values.
- **UI-13 MUST** No inline scripts, inline event handlers or inline styles in templates. The theme is applied before first paint by a small blocking `theme.js` in `<head>`; all other scripts are static files. This keeps a strict Content-Security-Policy possible (SEC-13).

## 5. Testing (TEST)

- **TEST-1 MUST** Every feature ships with tests in the same change.
- **TEST-2 MUST** Every security rule above has at least one negative test (for example: password never in response, bad sort field rejected, traversal path rejected, disguised file rejected, export escapes formulas).
- **TEST-3 MUST** `mvn clean verify` passes before a task is declared done. While two track sessions share the working tree, a module build (`-pl <module> -am`) is enough per task; the full `mvn clean verify` runs when the other session is idle and always before a phase is merged and tagged.
- **TEST-4 SHOULD** Use H2 for fast tests and Testcontainers (PostgreSQL, MySQL) for integration tests.
- **TEST-5 MUST** Fix every bug with a regression test first.
- **TEST-6 SHOULD** Tests are deterministic. No sleeps, no dependence on clock or ordering.
- **TEST-7 MUST** The sample app must start and serve `/admin` after every phase.

## 6. Git, docs and releases (GIT, DOC)

- **GIT-1 MUST** One phase per branch, small commits, clear messages in imperative mood. Backend and UI sessions share that one branch; there are no per-track branches. When both are active, commit by path (`docs/UI_PLAN.md` section 9).
- **GIT-2 MUST** Merge the phase branch into `main` and tag it (`v0.0.<phase>`) only when the phase is connected (backend phase done and its UI task connected). Tag the release as `v0.1.0`.
- **GIT-3 MUST** Never commit secrets, passwords, upload folders or IDE files.
- **GIT-5 MUST** `.gitattributes` enforces LF line endings for text files.
- **GIT-4 SHOULD** Keep changes focused. Do not mix refactors with features.
- **DOC-1 MUST** Update `CHANGELOG.md` and the relevant docs page with every user-visible change.
- **DOC-2 MUST** Document every property and annotation option, with its default.
- **DOC-3 MUST** Keep an honest Limitations page. Anything accepted but not implemented is marked "not yet".
- **DOC-4 SHOULD** Record design decisions in `docs/DECISIONS.md` with date and reason.
- **DOC-5 MUST** Do not copy text, code or images from other projects. Reference projects are for ideas only.

## 7. Rules for Claude Code itself (AI)

- **AI-1 MUST** Read `CLAUDE.md`, `docs/PLAN.md`, `docs/RULES.md`, `docs/PROJECT_DOCS.md` and `docs/UI_PLAN.md` before starting a session's work.
- **AI-2 MUST** Propose a plan and wait for approval before coding a phase, and before any change to security, the metadata model or the public API.
- **AI-3 MUST** Work on one phase at a time. Do not add features from the out-of-scope list.
- **AI-4 MUST** Do not invent requirements. If something is unclear, ask with two options and a recommendation.
- **AI-5 MUST** Do not claim something works without running the build and tests. Report real results, including failures.
- **AI-6 MUST** Do not weaken a test, disable a check or loosen a security rule to make something pass. Fix the cause.
- **AI-7 SHOULD** After each phase, review your own changes against this file and list any rule you could not satisfy.
- **AI-8 MUST** Do not add dependencies, change the Java or Spring Boot version, or rename public API without approval.
- **AI-9 SHOULD** Keep explanations short. Show what changed, why, and how to verify it.
- **AI-10 MUST** Never print or store real secrets. Use placeholders such as `${ADMIN_PASSWORD}`.
- **AI-11 MUST** In a parallel track session, state which track you are on and work only inside that track's paths (`docs/UI_PLAN.md` section 2). Changes to the view-model records need approval and a `docs/DECISIONS.md` entry. Never change a record on one side only.

## 8. Pre-merge checklist

- [ ] Phase acceptance criteria met
- [ ] `mvn clean verify` green
- [ ] Negative and security tests added
- [ ] No sensitive value in any response, export or log
- [ ] Sample app starts and `/admin` works
- [ ] Docs, CHANGELOG, DECISIONS updated
- [ ] No out-of-scope feature added
- [ ] No new dependency without a recorded reason
