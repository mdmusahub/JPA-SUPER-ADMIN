# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
- The phase plan was revised (D-60): Phase 5 is now "Sort and search quality" (relation-aware sort, nulls last in both directions, a sort proof on every sample column, a search that says what it covers); the filter links promised for Phase 4, relation links, PostgreSQL and MySQL runs, cost measurements and an automated browser test moved to Phases 6, 7, 12, 13 and 14, and `jpa.admin.page-size` moved to Phase 13. D-53 is superseded by D-60 for relations and null ordering. Documentation only.
- Documentation correction: Phase 4 is "delivered, with the filter-links exception". The filter links (`filter.*`, chip removal, clear all) were promised for Phase 4 but not delivered, and are now a Phase 6 item (D-60). A dated note was added to `docs/logs/phase-4-report.md`.

## [0.0.4] - 2026-11-11

### Added
- The list page (Phase 4, UI task U2). `GET {path}/{entity}` shows the entity as a table: a heading with the record count, a search box, sortable column headers with a direction indicator, rows with readable values, paging with a range summary, first/previous/next/last links and a page-size choice (10, 25, 50, 100; 25 by default), and an empty state that says what to do next. Sensitive fields appear as a masked column (dots only, never a value). Search, sort, page and size live in the address, so a reload, Back or a shared link shows the same page, and the page works without JavaScript.
- Search (`q`): case-insensitive "contains" over the entity's non-sensitive text fields; what you type is taken literally (`%`, `_`, quotes, a pasted `http://x`); text longer than 100 characters is cut. Sort (`sort`, `dir`): sortable columns only, both directions, with the identifier as the last sort key so paging is stable.
- Relation columns show the related record's label (the first text field named `name`, `title`, `label` and similar, else the first text field, else `#id`), read in the same query: a page costs two statements whatever its size. To-many relations, embedded values and binary data are not shown. A relation is not a link yet.
- Every value a visitor can type (page, size, sort, direction, search) is checked; a bad one falls back to its default and the page still renders. Every link the page prints is percent-encoded and lies under the console path.
- Preview app: 18 list fixtures (`/admin-preview/list?state=...`), built by the same code that builds the real links.
- Tests: the query service against a real database with the SQL recorded (which columns are selected, how many statements, that the search text is bound), a log test with SQL, bind and extract logging at TRACE, the real flow in the sample app with `spring.jpa.open-in-view` both true and false, a search-safety contract, and the `host-check` project listing `Book`, `Author` and `Publisher`. Mutation checks are in `docs/logs/phase-4-report.md`.
- Decisions D-50 to D-59.

### Changed
- Search (`q`) and sort (`sort`, `dir`) were built in Phase 4 together with the list, instead of Phase 5, because the list's links carry them.
- A sensitive field now has a column in the list, shown as dots. Earlier tests asserted that its name appeared nowhere; the rule that remains, and is tested, is that its value never appears in any response, link, sort key, search result or log (D-58).
- `EntityController` takes the list query service as an additional constructor argument (internal API).

### Removed
- The temporary "This table view is not available yet." notice on `GET {path}/{entity}` and its template `entity-notice.html` (Phase 4 replaces it with the real list page).

## [0.0.3] - 2026-11-11

### Added
- Entity discovery (Phase 3). The console reads the JPA metamodel once at startup and lists every entity it finds in a grouped sidebar, each with its record count, plus a dashboard with stat cards (total records, entities, groups, largest table) and a card per group. No code and no annotation are needed; entities are found automatically. Works for any host application's entities, proven by a second project (`host-check/`) with `Book`, `Author` and `Publisher` that adds only the starter.
- Annotations `@AdminHidden` (on an entity class or a field: removes it from the console completely) and `@AdminSensitive` (on a field: masked everywhere), in `io.github.mdmusahub.jpaadmin.annotation`, the console's first public API.
- Properties `jpa.admin.excluded-entities` (hide entities by simple or fully qualified class name, case-insensitive) and `jpa.admin.sensitive-fields` (names always masked; default `password, secret, token, apikey`, matched case-insensitively, ignoring `_` and `-`, as a substring). A blank entry stops startup and names the property without printing the value.
- Record counts are cached for five seconds and are read in a read-only transaction. A count that fails shows as "unavailable" and is logged without a stack trace; it never breaks the page. Counts are shown with thousands separators.
- A hidden, excluded or unsupported entity is not in the sidebar, not on the dashboard and not reachable by URL: its address answers exactly like an address that was never an entity, so the console does not reveal that it exists. An entity the console cannot manage (a composite primary key via `@EmbeddedId` or `@IdClass`) is skipped with a one-line startup warning, never an exception.
- The console reads one `EntityManagerFactory` (the unique or `@Primary` one). With several and none primary, it lists no entities and logs a warning, and never fails the host's startup.
- `GET {path}/{entity}` for a known entity returns the console shell with a notice that the table view is not available yet (the list arrives in the next phase); an unknown one returns the console's 404 page.
- UI (task U1): the sidebar gained a brand subtitle, a client-side entity search, collapsible groups, counts, an active-item highlight and a user chip with the signed-in name and the sign-out; the dashboard gained stat cards, group cards and empty states. All of it works without JavaScript and in both themes.
- An entity-name check (ARCH-12) with no new dependency: a test fails if any library module mentions a demo entity name, with an allow-list whose entries each carry a file path and a written reason and whose stale entries also fail.
- Decisions D-43 to D-49.

### Changed
- `PageChrome` gained a `user` field (the signed-in name, `null` when nobody is), and the record counts in `PageChrome.NavItem` and `DashboardView.EntityCard` became nullable (`null` = unavailable), so a broken count is shown honestly instead of as a wrong zero (D-44).
- The dashboard now shows real data. The Phase 2 welcome state remains only for a console that has found no entity.
- `jpa-admin-core` and `jpa-admin-autoconfigure` depend on `jakarta.persistence-api`/`spring-tx` and `jakarta.persistence-api`/`spring-orm` respectively, all `provided`, so nothing new reaches a host's runtime classpath (D-49).

### Notes
- A field whose name contains a configured sensitive word is masked, so a name such as `tokenizer` (which contains `token`) is masked too. Narrow `jpa.admin.sensitive-fields` if that is not wanted.

## [0.0.2] - 2026-11-10

### Added
- Sign-in (Phase 2). One security chain for the console path only, with the highest precedence: form login at `{path}/login`, sign-out only as a POST with the CSRF token (it clears the console's own session data and ends the session only if nothing else is in it, so a host login in the same browser stays; D-39), CSRF protection on every request, one admin from `jpa.admin.username` and `jpa.admin.password` (BCrypt hash made at startup, role `JPA_ADMIN`), session fixation protection, one generic message for a wrong username and a wrong password, and no redirect target ever taken from a request. Public: the sign-in page, `{path}/assets/**` and `{path}/theme.css`.
- The host application is left alone (SEC-4): the console adds no `UserDetailsService`, `AuthenticationManager`, `AuthenticationProvider` or `PasswordEncoder` bean, so a host's own users and Boot's defaults are neither used nor changed; a host without Spring Security gets no newly protected route and no generated password in its log; a host with its own chain, or one that relies on Spring Boot's default chain, behaves exactly as before. The console keeps its login, CSRF token and saved page in the shared session under its own names. It refuses to answer (500, one log line) if a request reaches it without passing its chain, and backs off completely when Spring Security or Thymeleaf is missing.
- The console renders its pages with its own private Thymeleaf engine and view resolver, limited to the console's templates (D-40). It does not read the host's `spring.thymeleaf.*` settings, template resolvers, dialects, engines or view resolvers, adds no bean of those types, and hands Spring MVC the finished view, so no view resolver of the host (another Thymeleaf setup, JSP, FreeMarker, Mustache, a catch-all resolver with a higher priority) is ever asked about a console page, and the host's own views render exactly as before. Console pages use the request's locale and are always `text/html;charset=UTF-8`.
- Security headers on console responses only (SEC-13): `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, a strict Content-Security-Policy with no inline script or style, and `no-store` on pages. No HSTS: it would apply to the host's whole host name.
- A startup warning under the `prod` profile when the console is served over plain HTTP, naming the TLS and reverse-proxy settings (SEC-8). No cookie or session setting is changed.
- `{path}/theme.css`: the accent colour from `jpa.admin.primary-color` as one CSS variable (SEC-15).
- Pages (UI task U0): sign-in page (works without JavaScript), dashboard (welcome state), error pages for 403, 404, 405 and 500 under the console path with fixed texts; light and dark theme with a toggle, responsive shell with a drawer sidebar, accent shades derived from one variable, accessible labels, focus rings and reduced-motion support. Theme choice is the only thing kept in the browser.
- Preview app: login, minimal dashboard and error fixtures and nine chrome variants at `/admin-preview/{page}?state=&chrome=`, with an index page.
- Tests: host isolation for four kinds of host, session isolation, open redirect, CSRF, secrets in responses and TRACE logs, headers, custom and nested paths, real-HTTP checks, the UI rules as code (with tests of the checker), every preview fixture in every chrome variant. Mutation checks are in `docs/logs/phase-2-report.md`.
- Decisions D-33 to D-42.

### Changed
- The starter now brings `spring-security-web`, `spring-security-config` and `org.thymeleaf:thymeleaf-spring6`. It deliberately does not bring `spring-boot-starter-security` (D-34) or `spring-boot-starter-thymeleaf` (D-40): a host that has no Thymeleaf does not get Boot's Thymeleaf auto-configuration, a view resolver or an engine from the console.
- `jpa.admin.password` must be at most 72 bytes (BCrypt's limit); a longer value stops startup and names the property.
- The console path now needs sign-in. The Phase 1 placeholder page ("JPA Admin console is running") is gone; the Phase 1 tests that asserted it were rewritten to keep every check.
- Assets and `theme.css` are sent with `Cache-Control: max-age=300, public`.

### Fixed
- Found by the Phase 2 tests before release: the admin could sign in only once per start (the stored user's hash was erased after the first success); a bare `?error` or `?logout` marker was not seen; an attempted username appeared in the DEBUG log.

### Notes
- Spring Boot's own security auto-configuration is active only if the host adds `spring-boot-starter-security` itself; the console then joins the host's filter chain and keeps its own login separate.
- A host with a servlet path prefix (`spring.mvc.servlet.path`) is not supported in v0.1 (D-41); the console answers 500 and says why in the log.
- Console templates are cached and not reloaded at run time (D-40).

## [0.0.1] - 2026-11-01

### Added
- Auto-configuration (Phase 1). With the starter plus `jpa.admin.username` and `jpa.admin.password`, the console path (`/admin` by default) answers in a servlet web app that has Spring MVC and JPA. It backs off completely, and then asks for no credentials, in a non-web app, without JPA or Spring MVC, and with `jpa.admin.enabled=false`. Bean names start with `jpaAdmin` (ARCH-7, ARCH-8).
- Properties `jpa.admin.enabled`, `path`, `username`, `password`, `title` and `primary-color`, with IDE completion metadata. A startup report lists every wrong property by name, with an example. `username` and `password` are required: missing, blank, or still a `${...}` placeholder (an environment variable that is not set) stops startup. `path` must be a strict path (`/admin`, `/tools/admin`; never `/`, no trailing slash, only letters, digits, `-` and `_`), and `primary-color` must be `#rrggbb` (SEC-7, SEC-14, SEC-15).
- Password rule: under the `prod` profile a password shorter than 12 characters or on a built-in common list stops startup; under other profiles it logs a warning. The password is never printed or logged, including in failure reports (SEC-6, D-7).
- Asset handler: `{path}/assets/**` is served from `classpath:/jpa-admin/assets/` and from nowhere else, so the files never appear at a second URL in the host app (D-24). A placeholder `css/tokens.css` stands in until the UI track's real assets arrive.
- The asset handler never serves hidden files or folders: a path segment that starts with a dot (`.gitkeep`, `.DS_Store`, `.env`), as written or percent-encoded, answers 404 whether or not the file exists (D-32). The stray `.gitkeep` is removed from the asset folder, where it was being served as an empty file.
- A plain-text placeholder page at the console path ("JPA Admin console is running"). Phase 2 replaces it.
- `BasePath`: the one validated console path that all links and routes are built from. Tests prove its results pass the contract's link rule and lie under the base path (D-29).
- Tests: properties, startup failures, auto-configuration conditions, the asset-exposure checks from `docs/UI_PLAN.md` section 10 (MockMvc and a real HTTP start), host routes and host static files left alone, and the password never appearing in output. Mutation checks on the main rules are logged in `docs/logs/`.
- Decisions in `docs/DECISIONS.md`: D-30 (dependencies for this phase and the design choices behind it), D-31 (Phase 1 is tagged `v0.0.1` although UI task U0 connects at Phase 2) and D-32 (the asset handler never serves hidden files).
- Phase 2 in `docs/PLAN.md` now names the security response headers on console pages (SEC-13) and the plain-HTTP startup warning under `prod` (SEC-8).

### Changed
- The sample app no longer starts without credentials. It has none in `application.yml` on purpose. Build the jar with `mvn -pl sample-app -am package -DskipTests`, set `JPA_ADMIN_USERNAME` and `JPA_ADMIN_PASSWORD` (12 or more characters), and run `java -jar sample-app/target/sample-app-0.1.0-SNAPSHOT.jar`; the PowerShell and bash versions are under "Run the demo" in `CLAUDE.md`. `mvn -pl sample-app spring-boot:run` can run stale code from an older install in `~/.m2`, so the docs no longer use it. Its tests use fake test-only values.

### Notes
- Until Phase 2 adds the security chain, the placeholder page at the console path needs no login. It shows no data.

## [0.0.0] - 2026-11-01

### Added
- Project plan, rules, requirements and UI plan (`docs/`).
- Repository setup: `.gitattributes`, `.gitignore`, Apache 2.0 `LICENSE`.
- Maven multi-module build on Spring Boot 4.1.2 and Java 17+: parent, `jpa-admin-core`, `jpa-admin-ui`, `jpa-admin-autoconfigure`, `jpa-admin-ui-preview`, `jpa-admin-starter` (published as `jpa-super-admin-spring-boot-starter`) and `sample-app`. A Maven Enforcer rule keeps the preview app out of every other module (D-19, D-22).
- Sample app with demo entities and seed data, used for development and tests only (not published).
- Preview app skeleton that refuses to start under the `prod` profile (ARCH-11). It has no pages yet.
- GitHub Actions build on Java 17 and 21 with pinned actions and read-only permissions.
- Draft view-model contract in `jpa-admin-core` (`web/view`): plain Java records for every page, with template and model attribute names fixed, and constructors that reject invalid states such as masked values, ragged table rows and inconsistent empty states (D-27).
- One link rule for every link in the view-model records: a local path under the base path, no scheme, no `//` in the path, no backslash, no whitespace or control character. The backend builds sort, page, page-size, filter, export and form links; templates only print them (D-28).
- `docs/FIXTURES.md`: the list of preview fixtures for the UI track. No fixture code yet.
- Design decisions D-1 to D-29 in `docs/DECISIONS.md`, and rule ARCH-12 (the library stays entity-agnostic).
- The sample app runs with `spring.jpa.open-in-view=false`, so a lazy-loading mistake cannot hide behind a session kept open for the whole request (D-29).

### Notes
- There is no console yet: the sample app starts, but `/admin` returns 404 until Phase 1.
