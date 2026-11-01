# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
