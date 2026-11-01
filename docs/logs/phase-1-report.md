# Phase 1 report: auto-configuration (backend track)

Branch `phase/1-autoconfig`, from tag `v0.0.0`. Nothing is committed. Phase 2 is not started. Detail for every file and command is in `docs/logs/phase-1-change-log.md` (167 entries); the plan I wrote first is `docs/logs/phase-1-plan.md`.

**Result:** `mvn -B clean verify` passes for all modules, 1595 tests (1259 before). A real start of the sample app with the two properties answers `GET /admin` with 200; without the username it stops with exit code 1 and a message naming `jpa.admin.username`.

## CODE-8: what changed in Spring Boot 4 (checked in the 4.1.2 jars, not from memory)

Boot 4 moved the technology auto-configurations out of `spring-boot-autoconfigure` into one module and package per technology. Only the infrastructure stayed.

| Class | Boot 3.x package | Boot 4.1.2 package (module) |
|---|---|---|
| `WebMvcAutoConfiguration`, `DispatcherServletAutoConfiguration` | `...autoconfigure.web.servlet` | `org.springframework.boot.webmvc.autoconfigure` (`spring-boot-webmvc`) |
| `HibernateJpaAutoConfiguration` | `...autoconfigure.orm.jpa` | `org.springframework.boot.hibernate.autoconfigure` (`spring-boot-hibernate`) |
| `JpaRepositoriesAutoConfiguration`, renamed `DataJpaRepositoriesAutoConfiguration` in 4.x | `...autoconfigure.data.jpa` | `org.springframework.boot.data.jpa.autoconfigure` (`spring-boot-data-jpa`) |
| `DataSourceAutoConfiguration` | `...autoconfigure.jdbc` | `org.springframework.boot.jdbc.autoconfigure` (`spring-boot-jdbc`) |
| `ValidationAutoConfiguration` | `...autoconfigure.validation` | `org.springframework.boot.validation.autoconfigure` (`spring-boot-validation`) |
| `HttpMessageConvertersAutoConfiguration` | `...autoconfigure.http` | `org.springframework.boot.http.converter.autoconfigure` |
| `JacksonAutoConfiguration` | `...autoconfigure.jackson` | `org.springframework.boot.jackson.autoconfigure` |
| `TransactionAutoConfiguration` | `...autoconfigure.transaction` | `org.springframework.boot.transaction.autoconfigure` |
| `HttpEncodingAutoConfiguration`, `MultipartAutoConfiguration` | `...autoconfigure.web.servlet` | `org.springframework.boot.servlet.autoconfigure` |
| Embedded Tomcat for servlet apps | `ServletWebServerFactoryAutoConfiguration` in `...autoconfigure.web.servlet` | `TomcatServletWebServerAutoConfiguration` in `org.springframework.boot.tomcat.autoconfigure.servlet` (`spring-boot-tomcat`). No class named `ServletWebServerFactoryAutoConfiguration` exists in any 4.1.2 module I scanned |

Unchanged: `@AutoConfiguration`, `AutoConfigurations`, `@ConditionalOnWebApplication/Class/Property/MissingBean` (all `org.springframework.boot.autoconfigure[.condition]`), the registration file `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, `FailureAnalyzer` and `AbstractFailureAnalyzer` (`org.springframework.boot.diagnostics`, registered in `spring.factories`).
Test support also moved: `@AutoConfigureMockMvc` is `org.springframework.boot.webmvc.test.autoconfigure` (artifact `spring-boot-starter-webmvc-test`, no longer in `starter-test`). `WebApplicationContextRunner`, `FilteredClassLoader`, `OutputCaptureExtension` stayed in `spring-boot-test`.
My code needs none of the moved classes, because the console checks Spring MVC by `WebMvcConfigurer` and JPA by class name.

## 1. Files created, modified, deleted

From `git status --short -uall` and `git diff --stat`. Deleted: none. None of the new files is git-ignored.

Modified (10), `git diff --stat`: 95 insertions, 9 deletions.

```
 CHANGELOG.md                                       | 18 +++++++++++++
 docs/DECISIONS.md                                  |  1 +
 docs/PLAN.md                                       | 18 ++++++++++---
 docs/PROJECT_DOCS.md                               |  6 ++---
 docs/UI_PLAN.md                                    |  2 +-
 jpa-admin-autoconfigure/pom.xml                    | 31 ++++++++++++++++++++++
 .../jpaadmin/autoconfigure/package-info.java       |  2 +-
 jpa-admin-core/pom.xml                             | 11 ++++++++
 jpa-admin-starter/pom.xml                          |  9 +++++++
 sample-app/pom.xml                                 |  6 +++++
```

Created (37, counting the three log files; 36 before this report existed):

| Area | Files |
|---|---|
| `jpa-admin-core` main (3) | `web/BasePath.java`, `web/PlaceholderController.java`, `web/package-info.java` |
| `jpa-admin-core` tests (3) | `web/BasePathTest.java`, `web/PlaceholderControllerTest.java`, `web/view/BasePathHrefRuleTest.java` |
| `jpa-admin-autoconfigure` main (8 Java + 2 resources) | `JpaAdminProperties`, `JpaAdminAutoConfiguration`, `JpaAdminConfigurationException`, `JpaAdminFailureAnalyzer`, `JpaAdminPasswordGuard`, `JpaAdminPathPrefixConfigurer`, `JpaAdminAssetsConfigurer`, `PasswordPolicy`; `META-INF/spring.factories`, `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `jpa-admin-autoconfigure` tests (8) | `JpaAdminPropertiesTest`, `JpaAdminPropertiesBindingTest`, `JpaAdminAutoConfigurationTest`, `AutoConfigurationRegistrationTest`, `JpaAdminFailureAnalyzerTest`, `JpaAdminPasswordGuardTest`, `JpaAdminPathPrefixConfigurerTest`, `PasswordPolicyTest` |
| `jpa-admin-ui` (1, **UI track's folder**) | `src/main/resources/jpa-admin/assets/css/tokens.css` (a comment only, as asked) |
| `sample-app` tests (7 Java + 2 resources) | `ConsoleAtDefaultPathTest`, `ConsoleAtCustomPathTest`, `ConsoleAtNestedPathTest`, `ConsoleDisabledTest`, `HostRoutesUntouchedTest`, `ConsoleAssetsWithoutHostStaticTest`, `ConsoleStartupTest`; `src/test/resources/application.properties` (fake credentials), `src/test/resources/static/host-marker.txt` |
| Logs (2 so far, plus this report) | `docs/logs/phase-1-plan.md`, `docs/logs/phase-1-change-log.md` |

Not touched: `sample-app/src/main/resources/application.yml`, the view-model records, `jpa-admin-ui-preview`, `CLAUDE.md`, `docs/RULES.md`, the templates.

## 2. Test counts and build

| Module | Before | After | New |
|---|---|---|---|
| `jpa-admin-core` | 1245 | 1332 | +87 (`BasePathTest` 65, `BasePathHrefRuleTest` 18, `PlaceholderControllerTest` 4) |
| `jpa-admin-autoconfigure` | 0 | 179 | +179 (`JpaAdminPropertiesTest` 89, `PasswordPolicyTest` 20, `JpaAdminAutoConfigurationTest` 18, `JpaAdminPasswordGuardTest` 15, `JpaAdminPropertiesBindingTest` 15, `JpaAdminPathPrefixConfigurerTest` 14, `AutoConfigurationRegistrationTest` 4, `JpaAdminFailureAnalyzerTest` 4) |
| `jpa-admin-ui-preview` | 4 | 4 | 0 |
| `sample-app` | 10 | 80 | +70 (`ConsoleAtDefaultPathTest` 31, `ConsoleAtCustomPathTest` 13, `ConsoleStartupTest` 11, `ConsoleAtNestedPathTest` 7, `HostRoutesUntouchedTest` 4, `ConsoleDisabledTest` 3, `ConsoleAssetsWithoutHostStaticTest` 1) |
| **Total** | **1259** | **1595** | **+336** |

Parameterized cases count one each. Final `mvn -B clean verify` (run after the last edit): **BUILD SUCCESS**, 33.8 s, all 7 modules, 0 failures, 0 errors, 0 skipped. The Enforcer rule for the preview module passed.

## 3. Mutation checks

Each mutation was applied alone, run with `-Dmaven.test.failure.ignore=true` (so every module runs), restored, checked byte-identical with `md5sum`, and followed by a green build (1332 / 179 / 80). Counts are failed test invocations.

| Mutation | Result | Failures (core / autoconfigure / sample-app) | Caught by |
|---|---|---|---|
| (a) username-required check removed | detected | 0 / 15 / 3 | `JpaAdminPropertiesTest`, `JpaAdminPropertiesBindingTest`, `JpaAdminAutoConfigurationTest`, `ConsoleStartupTest` |
| (b) path rule accepts any `/...` | detected | 25 / 23 / 1 | `BasePathTest`, the three autoconfigure property/auto-config tests, `ConsoleStartupTest` |
| (c1) assets handler also maps `/**` | detected | 0 / 0 / 4 | `ConsoleAtDefaultPathTest` (also: it hid the host's static file), `ConsoleAtCustomPathTest`, `ConsoleStartupTest` |
| (c2) asset also copied under `META-INF/resources` | detected | 0 / 0 / 4 | default, custom and nested path tests, `ConsoleStartupTest` |
| (c3) handler also reads `classpath:/static/` | detected | 0 / 0 / 1 | `ConsoleAtDefaultPathTest.theHostsOwnStaticContent...` (the only test for this variant) |
| (d1) `prod` never refuses a weak password | detected | 0 / 6 / 1 | `JpaAdminPasswordGuardTest`, `JpaAdminAutoConfigurationTest`, `ConsoleStartupTest` |
| (d2) minimum length 12 changed to 6 | detected | 0 / 21 / 2 | `PasswordPolicyTest`, `JpaAdminPasswordGuardTest`, `JpaAdminAutoConfigurationTest`, `ConsoleStartupTest` |
| (e) extra: `@Lazy(false)` removed from the guard | detected | 0 / 2 / 0 | the two lazy-initialization tests |
| (f) extra: prefix predicate matches every controller | detected | 0 / 2 / 3 | `JpaAdminPathPrefixConfigurerTest`, `HostRoutesUntouchedTest` |

(c), (d), (e), (f) go beyond the four you asked for. (c) has three variants because "serves the default location" can mean a handler mistake or a file-placement mistake (the one D-24 is about).

One process problem during this step: restoring (c1) with an empty replacement made the Edit tool also delete a line terminator, so the file was not identical. `md5sum -c` caught it before the next mutation, I fixed it, and from then on restored by replacing whole lines with non-empty text.

## 4. Decisions and deviations (all recorded in D-30)

Deviations from your brief, each with the reason:

1. **`spring-boot-starter-validation` is not added.** You pre-approved it; I did not use it. The binder only validates when a JSR-303 provider is present (`isJsr303Present`), so a host that excludes it would silently lose the username/password/path/colour rules, and the default analyzer prints the rejected value, which SEC-6 forbids for the password. `JpaAdminProperties.validate()` is plain code that always runs, lists every wrong property in one report, and never repeats the password. If you want Bean Validation anyway, say so; it is a contained change.
2. **`spring-boot-starter-data-jpa` in test scope of `jpa-admin-autoconfigure`.** Not on your list, but D-19 already approves it. It only puts `jakarta.persistence.EntityManager` on the test classpath so "active when JPA is present" can be tested. Main code checks the class by name. If you read "nothing else" strictly, this is the one to veto.
3. **Step order:** `BasePath` (your step 3) was built before the properties (step 1) because the properties' path check reuses its rule.
4. **PLAN.md had no checkboxes to tick.** I added a "Phase 1 checklist" under the phase table (all ticked except merge/tag) and updated the property notes in section 5.

Additions you did not ask for, all small and tested:

5. **Unresolved placeholder = missing.** Spring's binder leaves `${ADMIN_PASSWORD}` as literal text when the variable is not set (proved by a test), so the PLAN's own example would have started with that text as the password. Now it stops startup.
6. **Console path prefix instead of path text in the controller.** The placeholder controller declares an empty `@GetMapping`; a `WebMvcConfigurer` adds the base path to controllers in `io.github.mdmusahub.jpaadmin.web` only. Host routes are not touched (tested, including a host route at `/`). Phase 2 controllers should follow this.
7. **Failure analyzer** (clean "APPLICATION FAILED TO START" report) and **configuration metadata** for IDE completion.
8. **`@Lazy(false)` on the password guard.** With `spring.main.lazy-initialization=true` nothing would create it and the credential checks would be skipped.
9. **Path rule details you did not specify:** each segment starts with a letter or digit and continues with letters, digits, `-`, `_`; maximum 100 characters; multi-segment paths such as `/tools/admin` are allowed.
10. **Common-password list:** about 90 entries written by me from general knowledge, not copied from a project (DOC-5). The `prod` check matches the profile name `prod` exactly (case-sensitive, like Spring profiles).
11. **Public types.** `BasePath`, `PlaceholderController`, `JpaAdminAutoConfiguration`, `JpaAdminProperties`, `JpaAdminAssetsConfigurer` are public with an "internal" Javadoc, because the auto-configuration lives in another module (ARCH-9 is SHOULD). The assets handler and the controller are replaceable by a host bean; the base path, prefix and password guard are not.
12. **Sample app credentials:** `application.yml` has none; tests use fake values from `src/test/resources/application.properties`.
13. **ARCH-12 cleanup:** my own tests had `Product`/`/product` and the string `...jpaadmin.sample` in library modules; I renamed them (`Items`, `/item`, `...jpaadmin.other`). The final grep shows no hits.

## 5. Not done, and what you must check

Not done (not in Phase 1 or not asked):
- No merge, no tag, no commit. GIT-2 says a phase is tagged when connected, and UI task U0 connects after Phase 2 (`docs/UI_PLAN.md` section 7). Whether Phase 1 gets `v0.0.1` now is your call; the checklist item is left unticked.
- SEC-8's "warn on plain HTTP under `prod`" and SEC-13's security headers are not in the Phase 1 table; the placeholder page has no headers beyond Spring's defaults and **needs no login until Phase 2** (it returns one fixed sentence, no data).
- BCrypt hashing and the security chain are Phase 2.
- No browser check is needed (no HTML yet). The only asset is a CSS comment.

Check or decide:
1. **`CLAUDE.md` says `mvn -pl sample-app spring-boot:run`.** That command resolves the other modules from `~/.m2`, which still holds the Phase 0 installs (a 2,638-byte autoconfigure jar). I ran it: the app starts but `/admin` is **404**, because it is the old code. It also now needs credentials. To run current code: `mvn -pl sample-app -am package -DskipTests`, then `JPA_ADMIN_USERNAME=admin JPA_ADMIN_PASSWORD=<12+ characters> java -jar sample-app/target/sample-app-0.1.0-SNAPSHOT.jar` (PowerShell: set `$env:...` first). I verified this with the jar from `mvn clean verify`. I did not run `mvn install` (it would change your `~/.m2`) and did not edit `CLAUDE.md`; both are your call.
2. **Decide on deviations 1 and 2** above.
3. `ConsoleStartupTest` starts the real application 11 times (about 5 s in total) on random ports; if you prefer faster tests, that class is the one to trim.
4. Test credentials in `sample-app/src/test/resources/application.properties` are fake but are committed text (GIT-3); say if you want them generated at runtime instead.
5. The existing `.gitkeep` in `jpa-admin/assets/` will be served at `/admin/assets/.gitkeep` (an empty file). I did not test this; the UI track can delete it once real assets exist.
6. During manual verification one blanket "stop all `java.exe`" command was **denied** by the permission system. It was right to: it could have hit unrelated Java processes. I listed read-only instead, identified the three processes I had started by command line and port owner, and stopped only those. Verified afterwards: no Java processes, nothing listening on 8080 or 8081. (Two background tasks were reported as "failed, exit 127" after I force-stopped them; the app logs show no errors.)

## 6. Suggested commits, in order

Commit 1 was built on its own in a scratch copy of the repository (everything except the commit 2 and 3 files): `mvn -B clean verify` passed with the original 1245 + 4 + 10 tests. Commit 2 adds only tests and the sample-app test dependency, so after it the tree equals what the final full build verified, minus the docs. The fake test credentials ride with the code commit because the existing sample tests need them once the console exists. Commit by path (the UI track owns `jpa-admin-ui`; the one file there is the placeholder you asked for).

**1. Code**

```
git add jpa-admin-core/pom.xml jpa-admin-core/src/main jpa-admin-autoconfigure/pom.xml jpa-admin-autoconfigure/src/main jpa-admin-starter/pom.xml jpa-admin-ui/src/main/resources/jpa-admin/assets/css/tokens.css sample-app/src/test/resources/application.properties
git commit
```
```
feat: add auto-configuration, startup checks and asset handler (Phase 1)

Register JpaAdminAutoConfiguration for servlet web apps with Spring MVC and
JPA, switchable with jpa.admin.enabled. Validate jpa.admin.* at startup with
a report that names each wrong property: username and password required
(an unset ${...} variable counts as missing), strict path, #rrggbb colour.
Under the prod profile a weak password stops startup; elsewhere it warns. The
password is never printed.

Add BasePath, a placeholder page at the console path, and a resource handler
for {path}/assets/** that serves classpath:/jpa-admin/assets/ and nothing
else. A path prefix applies only to console controllers, never to host routes.

The sample app's tests get fake test-only credentials; its application.yml
stays free of them.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
```

**2. Tests**

```
git add jpa-admin-core/src/test jpa-admin-autoconfigure/src/test sample-app/pom.xml sample-app/src/test
git commit
```
```
test: cover properties, auto-configuration, asset exposure and startup output

Properties and startup failures (missing, blank, unresolved, bad path and
colour, prod and non-prod weak passwords, no password in any output),
auto-configuration conditions and back-off, BasePath against the contract's
link rule, and sample-app tests: console and assets at default, custom and
nested paths, paths outside the console path return 404, traversal attempts
never return a file, host routes and static files are untouched, and real
application starts on a random port.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
```

**3. Docs and logs**

```
git add CHANGELOG.md docs
git commit
```
```
docs: record Phase 1 decisions, update plan and changelog, add phase logs

Add D-30 (dependencies and design decisions), the Phase 1 checklist and
property notes in PLAN.md, the ticked asset-exposure item in UI_PLAN.md,
PROJECT_DOCS updates, the [0.0.0] release split and [Unreleased] entries in
CHANGELOG.md, and docs/logs with the plan, change log and report.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
```

## 7. Follow-up (2026-11-01, after this report)

Made after the report, in the working tree, **not committed**. Every change is in `phase-1-change-log.md`, entries 124 onward.

- **Run commands.** `CLAUDE.md` and `jpa-admin-core/CLAUDE.md` now build the jar with `mvn -pl sample-app -am package -DskipTests`, set `JPA_ADMIN_USERNAME` and `JPA_ADMIN_PASSWORD`, and run the jar, in PowerShell and in bash; both versions were run for real. They also say that `spring-boot:run` can run stale code from old `~/.m2` installs. This resolves section 5, item 1.
- **`.gitkeep`.** It was served (reproduced: 200) and is deleted. Deleting alone left every other dotfile served, so the asset handler now refuses any hidden file or folder (D-32), tested by `DotfilesAreNeverServedTest` and `NoDotfilesResourceResolverTest`. This resolves section 5, item 5.
- **Plan and decisions.** Phase 2 in `docs/PLAN.md` now names SEC-8 and SEC-13 (these were listed above as not done in Phase 1). D-31 records that Phase 1 is tagged `v0.0.1`; D-32 records the hidden-file rule.
- **Build.** `mvn -B clean verify` passes: core 1332, autoconfigure 223, ui-preview 4, sample-app 94, 1653 tests in total.
- **Commits.** This checkout shows no Phase 1 commits (`HEAD` is still `60b63aa`). The commit grouping in section 6 should therefore still apply (the isolated build of commit 1 in section 6 was done before this follow-up and was not repeated for the new files), with these additions: the code commit also needs the deletion (`git add -A jpa-admin-ui/src/main/resources/jpa-admin/assets`), the docs commit also needs `CLAUDE.md` and `jpa-admin-core/CLAUDE.md`, and the new tests and fixtures are covered by the existing test paths.
