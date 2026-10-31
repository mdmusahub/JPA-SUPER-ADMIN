# DECISIONS.md

Design and project decisions with date and reason (DOC-4). Newest at the bottom.

## 2026-10-31

| # | Decision | Reason |
|---|---|---|
| D-1 | Published artifactId is `jpa-super-admin-spring-boot-starter`. The module folder stays `jpa-admin-starter`. groupId is `io.github.mdmusahub`. | The docs already used this artifactId. A descriptive published name helps users, and a short folder name keeps the repo simple. |
| D-2 | Parent artifactId is `jpa-super-admin`. | Matches the root folder name in `PLAN.md`. |
| D-3 | **Superseded by D-16.** ~~Minimum Spring Boot is 3.2. Build on the latest stable 3.x patch. Confirm the exact version with the maintainer before pinning.~~ | 3.2 is the earliest release with the features and Hibernate 6 behaviour we rely on. Pinning needs approval (AI-8). Latest 3.x seen on Maven Central on this date: 3.5.16. |
| D-4 | S3 storage provider moves from optional v0.1 to v0.2. The storage SPI still ships in v0.1. | Reduces v0.1 scope. The SPI keeps S3 an addition, not a rewrite. |
| D-5 | Uploads are private by default (authenticated serving only). | Safer default. Matches UPL-7. |
| D-6 | The default console path stays `/admin`. | Familiar and documented. Hardening is covered by the Security page. |
| D-7 | Under the `prod` profile, startup fails if the password is under 12 characters or is a common password. Otherwise a warning is logged. | Stops weak credentials on exposed consoles without blocking local development. |
| D-8 | The console does not change global session cookie settings. It relies on Spring Boot defaults, documents that hosts should set `Secure` and `SameSite`, and logs a startup warning on plain HTTP under `prod`. SEC-8 reworded. | Session cookie settings are global and would change the host app, which breaks SEC-4. |
| D-9 | Image thumbnails are the original image scaled by CSS. No server-side resizing. | Keeps resizing out of v0.1 scope. |
| D-10 | Upload tickets: a signed token of about 10 minutes, issued with the form page, bound to entity and field, carrying max size and expiry. Validated on `POST /admin/uploads` in addition to CSRF. `jpa.admin.upload.signing-key` signs them. File serving uses session auth only in v0.1. | Limits what a stolen or replayed upload request can do. Session auth is enough for serving in v0.1. |
| D-11 | Sensitive field matching is case-insensitive, ignores `_` and `-`, and is a substring match against configured names, plus `@AdminSensitive`. | Catches `userPassword`, `api_key` and similar names that exact matching would miss. Favours masking too much over too little. |
| D-12 | Export cap is a fixed internal ceiling of 50,000 rows. `jpa.admin.export-max-rows` may lower it, never raise it. | Protects memory and the database. |
| D-13 | The HTML prototype lives at `docs/prototype/admin-console.html`. It is reference only and not shipped in the jar. | Keeps the visual reference in the repo without affecting the artifact. |
| D-14 | `DECISIONS.md` and `CHANGELOG.md` are created in Phase 0. This file is created early to record these decisions. | Decisions should be recorded when made. |
| D-15 | `PROJECT_DOCS.md` is added to the AI-1 reading list. | Consistency with `CLAUDE.md`. |
| D-16 | v0.1 targets Spring Boot 4.1.x (latest stable patch, 4.1.2 on Maven Central at this date) with Java 17 minimum. Spring Boot 3.x is not supported in v0.1. A 3.5 compatibility build can be reconsidered if users ask. Supersedes D-3. The exact version is confirmed with the maintainer before pinning. | All Spring Boot 3.x lines are out of OSS support (3.5 ended 2026-06-30). Boot 4.0 OSS support ends 2026-12-31. Boot 4.1 was released 2026-06-30 with OSS support to 2027-07-31. Per the Boot 4.1.2 BOM: Spring Framework 7.0.10, Spring Security 7.1.2, Hibernate ORM 7.4.13, Jakarta Persistence 3.2, Servlet 6.1, Thymeleaf 3.1.5, Spring Data 2026.0.2. |
| D-17 | New rule CODE-8: verify Spring Boot, Spring Security, Spring Data JPA, Hibernate and Thymeleaf APIs and auto-configuration packages against the official 4.1 docs or source, not memory. | Boot 4 reorganised modules and packages, so 3.x knowledge may be wrong. |
| D-18 | `.gitattributes` enforcing LF for text files is added in Phase 0 (rule GIT-5). | Avoids CRLF churn on Windows. |
