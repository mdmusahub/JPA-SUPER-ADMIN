# Phase 2 report: login (both tracks, one session)

Branch `phase/2-login`, from tag `v0.0.1`. Nothing is committed. Phase 3 is not started. Every file and command is in `docs/logs/phase-2-change-log.md` (193 entries for Phase 2, and the follow-up of 2026-11-02 from entry 194); the plan I wrote first is `docs/logs/phase-2-plan.md`. The one-session exception to AI-11 is D-33.

**Result at the end of Phase 2:** `mvn -B clean verify` passed for all 7 modules, **2067 tests** (1653 before), Enforcer rule green (the follow-up below has its own result). The real jar answers as specified, and the whole sign-in flow works in real Chrome with no Content-Security-Policy violation.

## Follow-up of 2026-11-02 (after your review of this report)

You approved D-34 and D-35, kept the servlet path prefix unsupported for v0.1 (it is now under Limitations), and approved the name-check allow-list. This follow-up changes two behaviours and the docs. All on this branch, nothing committed, Phase 3 not started. Change log entries 194 to 256. The sections below this one are the Phase 2 report as written, with the statements that are no longer true corrected and marked.

**Result:** `mvn -B clean verify` passes for all 7 modules: **BUILD SUCCESS in 1 min 28 s**, the Enforcer rule passed in each module, **2164 tests** (core 1379, autoconfigure 438, ui-preview 177, sample-app 170; 2067 after Phase 2, +97), 0 failures, 0 errors, 0 skipped. All 14 follow-up mutations were caught and restored byte-identically. The build ran after you stopped your sample app (no Java process, nothing on 8080), and I did not start the app afterwards. I read the packaged `sample-app` jar without running it: it holds `thymeleaf-spring6` and not `spring-boot-thymeleaf` or `spring-boot-security`.

### F1. Signing out no longer ends a host's session (D-39)

I checked first, as you asked. In the Spring Security 7.1.2 bytecode, with `invalidateHttpSession(false)` the default logout handlers only clear the current authentication and remove the console-named attributes from the console's own repositories (`JPA_ADMIN_*`), so it could be done safely and I went on.

- Spring's handlers now run with `invalidateHttpSession(false)` and `clearAuthentication(true)`. A new `ConsoleLogoutHandler` removes every session attribute whose name starts with `JPA_ADMIN_` (also future console attributes) and invalidates the session **only if no attribute is left**.
- Tests: `ConsoleLogoutHandlerTest` (15), and in `ConsoleInsideHostsTest` four tests that replace the old one: the host login survives console sign-out in the same session; a console-only session is invalidated; any other state keeps the session; a GET changes nothing and the CSRF token is still required. Over real Tomcat (`ConsoleRealHttpTest`): the session id stays, no new cookie is sent, the host's cart survives, and a console-only session ends. Also a MockMvc test in the sample app.
- **One honest limit:** "is the session empty" and "invalidate it" are two servlet calls and the API has no atomic version. A host request in the same session at that very instant could lose state it added in between. The window is microseconds wide and the effect is lost host state, never a disclosure. It is in the Javadoc and in D-39.

### F2. The console has its own Thymeleaf (D-40)

- `ConsoleThymeleafViews` creates one template engine, one template resolver and one view resolver and keeps them in private fields. **None is a bean**, because Boot's Thymeleaf auto-configuration collects every `ITemplateResolver` bean into its engine and backs off for an `ISpringTemplateEngine` bean. The template resolver reads only `jpa-admin/*` from `classpath:/templates/`; the view resolver answers only to `jpa-admin/*`, ignores `redirect:` and `forward:`, and buffers the page until it has rendered completely.
- Pages carry the **view, not its name** (`ConsoleViews` in core, `ConsoleViewFactory` as the seam so core still has no Thymeleaf). Spring MVC renders such a view directly and asks no view resolver, so a host resolver of any order or technology cannot take a console page. The request's locale is passed because a `ThymeleafView` needs one (found in the bytecode, which changed my first plan).
- Nothing is read from the host: not `spring.thymeleaf.*`, not its template resolvers, dialects, engines, view resolvers or message source.
- **Starter dependency (amends D-34):** the starter now brings `org.thymeleaf:thymeleaf-spring6` and **no longer `spring-boot-starter-thymeleaf`**. That starter's `spring-boot-thymeleaf` gave a host without Thymeleaf a view resolver, an engine and `spring.thymeleaf.*` settings, so keeping it would not have met "must not change the host". `jpa-admin-autoconfigure` has the library as `optional`; the console still backs off without it.
- Tests with real auto-configurations (`ConsoleViewIsolationTest`, 38; `ConsoleThymeleafViewsTest`, 34; `ConsoleOwnThymeleafTest`, 2). Hosts: **(a)** no template engine, with and without Boot's security; **(b)** the host's own Thymeleaf with another prefix, suffix, encoding, content type and view names that even exclude `jpa-admin/*`, Thymeleaf switched off, and the host's own resolver, dialect, engine and view-resolver beans; **(c)** a highest-priority view resolver that claims every name (also beside Boot's security and beside the host's Thymeleaf); **(d)** a JSP-style resolver and a custom resolver standing in for FreeMarker or Mustache. For all of them: the console's pages are identical to the plainest host; the host's own views render exactly the same with the console off and on (including a `th:action` form); the console adds no bean of a view-technology type; the host's Thymeleaf objects are configured as before; and the catch-all resolver is never asked about a console name (it is asked about the host's own, as the control). The default test hosts no longer include Boot's Thymeleaf, so every older console test also proves the console works without it.
- **Consequences you should know:** console templates are cached and not reloaded at run time (UI work uses the preview app, which keeps Boot's Thymeleaf with `spring.thymeleaf.cache=false` and does not use this code). In a host without Spring Security, the `@EnableWebSecurity` that the console imports (D-35) registers Spring Security's bean `requestDataValueProcessor`; it adds a hidden field only to requests that passed the console's CSRF filter, and the host-form test shows the host's pages are identical. I did not change any template, so the pages' HTML is unchanged by design, but I did **not** repeat the Chrome checks of Phase 2 after this change.

### F3. Prototype, decisions, docs

- `docs/prototype/admin-console.html` was still not in the repository when I looked at the start and at the end of this follow-up. I did not create or change it. D-38 and `docs/UI_PLAN.md` now say you are adding it and will ask for a UI alignment pass after your commit.
- New decisions: **D-39** sign-out, **D-40** private Thymeleaf, **D-41** servlet path prefix unsupported in v0.1, **D-42** the name-check allow-list rule (path and reason per entry, stale entries fail). D-34 and D-35 are marked approved, D-34's dependency list and D-35's sign-out sentence are amended, D-38's prototype sentence is corrected.
- Updated: `docs/PROJECT_DOCS.md` (US-2, installation, security, troubleshooting, limitations), `CHANGELOG.md` `[Unreleased]`, `docs/UI_PLAN.md`, `docs/PLAN.md` (the Phase 3 name-check row points to D-42; three follow-up boxes in the Phase 2 checklist).
- **Correction to this report:** the ARCH-12 grep has **seven lines in three files** (`JpaAdminSecurityConfiguration` main, `HostIsolationTest`, `JpaAdminSecurityModesTest`), not "six hits in a main file and three tests". D-42 lists the three paths.

### F4. Mutation checks of the follow-up

Same method as before, except that each run builds the modules and runs only the test classes that should catch the break (it never uses `clean`). The file is restored from its saved bytes and the MD5 is compared before and after (all identical, checked again at the end).

| | Mutation | Where | Failing tests | Caught by |
|---|---|---|---|---|
| k | sign-out always ends the whole session | `ConsoleLogoutHandler` | 11 | `ConsoleInsideHostsTest`, `ConsoleLogoutHandlerTest` |
| l | sign-out never ends a console-only session | `ConsoleLogoutHandler` | 4 | `ConsoleInsideHostsTest`, `ConsoleLogoutHandlerTest` |
| m | sign-out removes every session attribute, the host's too | `ConsoleLogoutHandler` | 11 | `ConsoleInsideHostsTest`, `ConsoleLogoutHandlerTest` |
| n | Spring's own handler ends the whole session (`invalidateHttpSession(true)`) | `JpaAdminSecurityConfiguration` | 2 | `ConsoleInsideHostsTest` |
| o | pages carry the template name, so the host's view resolvers decide | core `ConsoleViews` | 29 | `ConsoleInsideHostsTest`, `ConsolePagesTest` |
| p | the console's engine is exposed as a bean | `JpaAdminAutoConfiguration` | 15 | `ConsoleViewIsolationTest` |
| q | the console's view resolver is exposed as a bean | `JpaAdminAutoConfiguration` | 12 | `ConsoleViewIsolationTest` |
| r | the engine may resolve any template, not only `jpa-admin/*` | `ConsoleThymeleafViews` | 2 | `ConsoleThymeleafViewsTest` |
| s | the console reads the host's `spring.thymeleaf.prefix` | `ConsoleThymeleafViews` | 5 | `ConsoleThymeleafViewsTest`, `ConsoleViewIsolationTest` |
| t | a fixed locale instead of the request's | core `ConsoleViews` | 2 | `ConsolePagesTest`, `ConsoleViewIsolationTest` |
| u | `redirect:` and `forward:` names are honoured | `ConsoleThymeleafViews` | 1 | `ConsoleThymeleafViewsTest` |
| v | the factory accepts any name the resolver pattern lets through | `ConsoleThymeleafViews` | 15 | `ConsoleThymeleafViewsTest` |
| w | the host's template resolvers are added to the console's engine | `ConsoleThymeleafViews` | 1 | `ConsoleThymeleafViewsTest` |
| x | the starter brings Boot's Thymeleaf starter again | starter `pom.xml` | 2 | `ConsoleOwnThymeleafTest` |

### F5. Tests and files

| Module | After Phase 2 | After follow-up | New |
|---|---|---|---|
| `jpa-admin-core` | 1377 | 1379 | +2 (`ConsolePagesTest` 49 to 51) |
| `jpa-admin-autoconfigure` | 348 | 438 | +90 (`ConsoleLogoutHandlerTest` 15, `ConsoleThymeleafViewsTest` 34, `ConsoleViewIsolationTest` 38, `ConsoleInsideHostsTest` 7 to 10) |
| `jpa-admin-ui-preview` | 177 | 177 | 0 |
| `sample-app` | 165 | 170 | +5 (`ConsoleSignInTest` +1, `ConsoleRealHttpTest` +2, `ConsoleOwnThymeleafTest` 2) |
| **Total** | **2067** | **2164** | **+97** |

Final `mvn -B clean verify`: **BUILD SUCCESS**, 1 min 28 s, 0 failures, 0 errors, 0 skipped. git now sees 100 paths: 27 modified, 3 deleted, 70 new (60 at the end of Phase 2 including this report, plus the 10 new files of the follow-up); `git diff --stat` for tracked files: 30 files, 725 insertions, 176 deletions. The only ignored paths are the `target/` build folders.

- **New, main:** autoconfigure `ConsoleLogoutHandler`, `ConsoleThymeleafViews`; core `ConsoleViewFactory`, `ConsoleViews`.
- **Changed, main:** autoconfigure `JpaAdminSecurityConfiguration`, `JpaAdminAutoConfiguration`, `package-info`; core `ConsoleController`, `ConsoleExceptionResolver`, `web/package-info`; poms of the starter and autoconfigure.
- **New, tests:** `ConsoleLogoutHandlerTest`, `ConsoleThymeleafViewsTest`, `ConsoleViewIsolationTest`, sample-app `ConsoleOwnThymeleafTest`; test resources `host-views/home.page` and `templates/host-only.html`.
- **Changed, tests:** core `ConsolePagesTest`; autoconfigure `ConsoleTestHosts` (new `withHostThymeleaf()`), `ConsoleInsideHostsTest`, `JpaAdminAutoConfigurationTest`; sample-app `host/HostProbeController`, `ConsoleSignInTest`, `ConsoleRealHttpTest`.
- **Docs:** `docs/DECISIONS.md`, `docs/PROJECT_DOCS.md`, `docs/UI_PLAN.md`, `docs/PLAN.md`, `CHANGELOG.md`, this report and the change log.

### F6. Needs your attention

1. **The D-34 amendment above.** D-34 was approved with `spring-boot-starter-thymeleaf` in the list; D-40 replaces it with the library alone. It is a pom change if you want it back, and the host problems come back with it (a test fails then: mutation x).
2. **Your sample app.** Java processes 20992 and 13516 (`java -jar sample-app/target/sample-app-0.1.0-SNAPSHOT.jar`, started 00:35 on 8080) were not mine, and they held the jar, so I did not stop them. You stopped them and the final build ran after that. I did not start the app afterwards, as you asked. `sample-app/target/sample-app-0.1.0-SNAPSHOT.jar` is now the new code; start it yourself to see the changes (the credentials come from `JPA_ADMIN_USERNAME` and `JPA_ADMIN_PASSWORD`, as in `CLAUDE.md`).
3. The UI alignment pass against the prototype waits for your commit, as you said.

## Read this first (the Phase 2 report as written; the follow-up above corrects it where marked)

1. **Dependency deviation (D-34).** You pre-approved `spring-boot-starter-security`. The starter brings `spring-security-web` and `spring-security-config` (the two libraries that starter delivers) and **not** that starter. Reason, checked in the 4.1.2 jars and by tests: that starter also brings Boot's security auto-configuration, which (a) protects every route of a host that never asked for Spring Security, or logs `Using generated security password` even when the console's chain exists, because it only backs off for `UserDetailsService`/`AuthenticationManager`/`AuthenticationProvider` beans that you told me not to expose; and (b) would silently remove the default protection of a host that relies on Boot's default chain, as soon as the console adds a chain bean. The two cases cannot be told apart at run time. **You approved this deviation on 2026-11-02.** `spring-boot-starter-security` is used in test scope to build hosts that have it. If you want the exact artifact, it is a pom change, and the two problems come back (a test records Boot's behaviour). I did not stop to ask because the requirement you wrote cannot be met otherwise.
2. **The prototype `docs/prototype/admin-console.html` did not exist in the repository.** The look follows `docs/UI_PLAN.md` section 5 (my reading of it, not a copy). (Update 2026-11-02: you are adding the file and will ask for a UI alignment pass after your commit; it was still absent when the follow-up ended, and I did not create it.)
3. **Three real defects were found by the tests and fixed** (not by the brief): the admin could sign in only once per start (the stored user's hash was erased after the first success); a bare `?error` or `?logout` marker was not seen (Spring MVC passes an empty value as `null`); and an attempted username appeared in the DEBUG log (`Failed to find user '...'` from Spring Security).
4. **Host behaviour you should know** (all tested unless stated): see section 4.
5. **A process you did not mention** (PID 10064/13004, `java -jar sample-app/...jar`, started 21:55) held `sample-app/target/...jar` and port 8080 and made `clean` fail at the start. I did not stop it (not mine). It was gone before my final build. (Update 2026-11-02: another one, PIDs 20992 and 13516, is running now; see F6.)

## 1. Files (git status: 27 modified, 3 deleted, 59 new, plus this report; nothing ignored)

`git diff --stat` for tracked files: 30 files, 662 insertions, 173 deletions.

**Deleted (3):** `PlaceholderController.java`, `PlaceholderControllerTest.java` (replaced by design), `jpa-admin-ui/.../templates/jpa-admin/.gitkeep`.

**Modified (27):** `CHANGELOG.md`; `docs/` DECISIONS, FIXTURES, PLAN, PROJECT_DOCS, RULES, UI_PLAN; poms of core, autoconfigure, starter, ui-preview, sample-app; autoconfigure main `JpaAdminAssetsConfigurer`, `JpaAdminAutoConfiguration`, `JpaAdminProperties`, `package-info`; core `web/package-info`; `tokens.css` (the placeholder replaced); autoconfigure tests `JpaAdminAutoConfigurationTest`, `JpaAdminPathPrefixConfigurerTest`, `JpaAdminPropertiesTest`; sample-app tests `ConsoleAtCustomPathTest`, `ConsoleAtDefaultPathTest`, `ConsoleAtNestedPathTest`, `ConsoleDisabledTest`, `ConsoleStartupTest`, `HostRoutesUntouchedTest`. (The Phase 1 tests were rewritten because the placeholder page is gone; every old check is kept, run signed out and signed in.)

**New, backend (main):** autoconfigure `JpaAdminSecurityConfiguration`, `JpaAdminStandaloneSecurityConfiguration`, `ConsoleChainGuard`, `ConsoleAuthenticationSuccessHandler`, `SpringSecurityCsrfTokens`, `JpaAdminTransportCheck`; core `ConsoleController`, `ConsoleChromeFactory`, `ConsoleExceptionResolver`, `ConsoleSettings`, `CsrfTokens`, `ErrorViews`, `ThemeController`.
**New, UI (main):** `css/base.css`, `components.css`, `pages.css`; `js/theme.js`, `nav.js`; templates `layout`, `login`, `dashboard`, `error` and fragments `breadcrumbs`, `head`, `icons`, `sidebar`, `theme-toggle`, `toast`, `topbar`; preview `ChromeFixtures`, `PageFixtures`, `PreviewController`, `PreviewWebConfig`, `application.properties`, `templates/preview/index.html`.
**New, tests:** autoconfigure `ConsoleAuthenticationSuccessHandlerTest`, `ConsoleChainGuardTest`, `ConsoleInsideHostsTest`, `ConsoleTestHosts`, `HostIsolationTest`, `JpaAdminSecurityModesTest`, `JpaAdminTransportCheckTest`, `MockBrowser`, `SpringSecurityCsrfTokensTest`; core `ConsolePagesTest`, `org/springframework/security/fake/FakeAccessDeniedException`; preview `HtmlRules`, `HtmlRulesTest`, `PreviewAssetsTest`, `PreviewPagesTest`, `TemplateRulesTest`; sample-app `ConsoleRealHttpTest`, `ConsoleRenderedPagesTest`, `ConsoleSecurityTest`, `ConsoleSignInTest`, `MockBrowser`, `host/HostProbeController`.
**New, docs:** `docs/logs/phase-2-plan.md`, `phase-2-change-log.md`, `phase-2-report.md`.

Not touched: the view-model records, `CLAUDE.md` files, `jpa-admin-ui-preview` prod guard, `NoDotfilesResourceResolver`.
Files outside the repository (scratchpad, not committed): the Chrome DevTools scripts I used for browser checks (Node 24; the project itself still has no Node), the mutation driver, screenshots.

## 2. Test counts

| Module | Before | After | New |
|---|---|---|---|
| `jpa-admin-core` | 1332 | 1377 | +45 (`ConsolePagesTest` 49, minus the 4 placeholder tests) |
| `jpa-admin-autoconfigure` | 223 | 348 | +125 (`HostIsolationTest` 15, `ConsoleInsideHostsTest` 7, `ConsoleAuthenticationSuccessHandlerTest` 48, `JpaAdminTransportCheckTest` 31, `JpaAdminSecurityModesTest` 9, `ConsoleChainGuardTest` 5, `SpringSecurityCsrfTokensTest` 3, +4 properties, +3 auto-configuration) |
| `jpa-admin-ui-preview` | 4 | 177 | +173 (`PreviewPagesTest` 123, `HtmlRulesTest` 25, `PreviewAssetsTest` 18, `TemplateRulesTest` 7) |
| `sample-app` | 94 | 165 | +71 (`ConsoleSignInTest` 35, `ConsoleSecurityTest` 21, `ConsoleRealHttpTest` 5, `ConsoleRenderedPagesTest` 3, startup +3, path tests adapted) |
| **Total** | **1653** | **2067** | **+414** |

Final `mvn -B clean verify`: **BUILD SUCCESS**, 1 min 11 s, 0 failures, 0 errors, 0 skipped.

## 3. Mutation checks

Each applied alone to the real source, whole reactor run with failures ignored, file restored from its saved bytes, MD5 compared before and after (all identical), and the final `clean verify` was green afterwards.

| | Mutation | Failing tests | Caught by |
|---|---|---|---|
| a | removed the `{path}/**` matcher | 19 | `HostIsolationTest`, `ConsoleInsideHostsTest`, `ConsoleChainGuardTest` |
| b | disabled CSRF | 65 | all three path tests, `ConsoleInsideHostsTest` |
| c | GET logout allowed | 2 | `ConsoleSignInTest` |
| d | wrong-username answer differs from wrong-password answer | 3 | `ConsoleSignInTest`, `ConsoleInsideHostsTest` |
| e | removed the permit rule for `{path}/assets/**` | 23 | `ConsoleSecurityTest`, `ConsoleSignInTest`, `ConsoleStartupTest`, the Phase 1 asset tests, the three path tests |
| f | dropped one SEC-13 header (Referrer-Policy) | 3 | `ConsoleSecurityTest` |
| g | `th:utext` in a template | 1 | `TemplateRulesTest` (and the rule checker has its own tests) |
| h | honoured an external `redirect` parameter | 3 | `ConsoleAuthenticationSuccessHandlerTest`, `ConsoleSecurityTest` |
| i | lowered the chain order | 16 | `HostIsolationTest` (catch-all host chain), `ConsoleInsideHostsTest`, `ConsoleChainGuardTest` |
| j | exposed a global `UserDetailsService` bean | 7 | `HostIsolationTest`, `ConsoleInsideHostsTest` |

## 4. Decisions, deviations, and host-application behaviour

Decisions are D-33 to D-42 in `docs/DECISIONS.md` (D-39 to D-42 are from the follow-up). Deviations from the brief:

- **D-34 as above** (two libraries instead of the named starter).
- **Authority, not role prefix:** the admin has authority `ROLE_JPA_ADMIN` and the rule uses `hasAuthority`, so a host's role-prefix setting cannot lock the admin out.
- **Added beyond the list, each tested:** a fail-closed guard (a request that did not pass the chain gets 500 and one log line, e.g. a host that ignores the console path); a 72-byte password limit with a startup message (BCrypt cannot hash more); console-only names in the shared session (`JPA_ADMIN_*`); a stand-in user for unknown names (no username in the log, equal work).
- **Error pages use the shell without a sign-out button**, because `PageChrome` has no "signed in" flag. No contract change was needed.
- **Fixtures:** added `dashboard/minimal` and `error/method-not-allowed` to `docs/FIXTURES.md`.

What changes for a host that adds the starter:
- **No Spring Security in the host:** Spring Security's libraries are on the classpath but nothing is active except for `{path}` and `{path}/*`. Host routes see no filter, no HTTP firewall (so `//x` or `;` paths still work), no generated user, no headers. Proven by running the same requests with the console off and on, in MockMvc and over real Tomcat.
- **Host with its own chain, or relying on Boot's default chain, or with a catch-all chain:** unchanged, same proof. The console's chain is first in the host's chain list and matches only `{path}/**`.
- **Shared browser session:** the console no longer reads or writes the host's login, CSRF token or saved request, and vice versa. **Signing out of the console no longer ends the whole HTTP session (changed 2026-11-02, D-39):** it removes only the console's own attributes and ends the session only if nothing else is in it, so a host login in the same browser stays. (The Phase 2 version ended the whole session, as the brief asked.)
- **Thymeleaf (changed 2026-11-02, D-40):** the Phase 2 version brought `spring-boot-starter-thymeleaf`, which activated Boot's Thymeleaf in a host without it and made the console depend on the host's `spring.thymeleaf.*` and view resolvers. That is gone: the console renders with its own private engine, and the host's Thymeleaf and view technology are neither used nor changed (see F2).
- **No HSTS** from the console (it would apply to the host's whole host name). Console-only CSP, `no-store`, and so on.
- **Servlet path prefix** (`spring.mvc.servlet.path`) is not supported in v0.1 (approved, D-41): the console answers 500 and logs why. Listed in PROJECT_DOCS limitations and troubleshooting.
- **Plain HTTP under `prod`:** one WARN line; no cookie or session setting is touched. The session cookie in a real run is `JSESSIONID=...; Path=/; HttpOnly`, identical in attributes to a host route's cookie (tested).
- **Logging:** a 500 inside the console logs its stack trace at ERROR on the server (path without query). Exception messages never reach a response.

## 5. Not done, what you must check, contract changes

- **No contract change needed** (records untouched). The dashboard is built from zero values, which the record allows.
- Not done: merge to `main` and the `v0.0.2` tag (the PLAN checklist item is left unticked, your step); commits.
- **Browser coverage:** I checked Chrome only, headless, by script: light and dark, 1280px and 390px, drawer open/close, Escape and focus return, theme cycle and persistence, blocked `localStorage`, JavaScript off, keyboard order and focus ring, reduced motion, contrast (every pair at least 4.5:1, lowest 5.16), no horizontal overflow, pages contact only their own origin. **Please look yourself at:** (1) both themes in a real Chrome and in Firefox and Safari (the stylesheets use `color-mix`, supported since 2023), (2) the phone drawer with a finger, (3) keyboard-only sign-in including the focus ring on the button, (4) a wrong password message, (5) the sign-out button and the 404 page while signed in, (6) the look against the prototype if you have it.
- The `ARCH-12` grep found whole-word hits for `Order`, all Spring's `@Order` annotation. (Corrected 2026-11-02: seven lines in three files, one main and two test files, not six; the allow-list rule is approved and the three entries are in D-42, for the Phase 3 check.)
- `CLAUDE.md` (UI track) still says to open `/admin-preview/list?state=normal`; that page arrives with U2. The index at `/admin-preview` lists what exists now.
- `CLAUDE.md` for the UI track and RULES.md are unchanged except the D-31 pointer in GIT-2.

## 6. Suggested commits (PowerShell-safe, by path; nothing runs `git add .`)

I verified the **final tree**, not each commit in isolation: the backend commit alone does not compile its old tests (they are in commit 3), as in Phase 1.

**1. Backend**
```
git add jpa-admin-core/pom.xml jpa-admin-core/src/main jpa-admin-autoconfigure/pom.xml jpa-admin-autoconfigure/src/main jpa-admin-starter/pom.xml
git commit
```
```
feat: add the console's sign-in chain and pages (Phase 2)

One security chain for the console path only, highest precedence, local
BCrypt admin with no exposed authentication beans, form login, POST-only
sign-out that ends only the console's part of the session, CSRF, SEC-13
headers and a strict CSP, no open redirect, a fail-closed guard, a
startup warning for plain HTTP under prod, and theme.css. The console
renders with its own private Thymeleaf engine. The host's own security
and view technology are never touched; the console keeps its session
data under its own names. Replaces the Phase 1 placeholder.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
```

**2. UI (task U0)**
```
git add jpa-admin-ui jpa-admin-ui-preview/pom.xml jpa-admin-ui-preview/src/main
git commit
```
```
feat: add the U0 templates, styles, scripts and preview fixtures

Layout shell, sign-in, dashboard and error pages, light and dark tokens,
theme.js (blocking, in head) and nav.js, and preview fixtures for the
pages and nine chrome variants. No inline script or style, no external
address.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
```

**3. Tests**
```
git add jpa-admin-core/src/test jpa-admin-autoconfigure/src/test jpa-admin-ui-preview/src/test sample-app
git commit
```
```
test: prove host isolation, sign-in rules, headers and the UI rules

Same requests with the console off and on for four kinds of host, session
isolation, open redirect, CSRF, secrets in responses and TRACE logs,
real-HTTP checks, the UI rules as code with tests of the checker, and
every preview fixture. Phase 1 tests rewritten to keep every check.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
```

**4. Docs and logs**
```
git add CHANGELOG.md docs
git commit
```
```
docs: record Phase 2 decisions, checklist, U0 status and logs

D-33 to D-42, the Phase 2 checklist, U0 status, fixture rows, security
and limitation notes, the 0.0.1 changelog split, and the phase logs.

Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
```
