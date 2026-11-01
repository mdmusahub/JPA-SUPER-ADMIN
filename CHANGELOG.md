# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
