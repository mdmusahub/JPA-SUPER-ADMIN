# Phase 3 report: discovery (both tracks, one session)

Branch `phase/3-discovery`, from tag `v0.0.2`. Nothing is committed. Phase 4 is not started. Every file and command
is in `docs/logs/phase-3-change-log.md` (entries 1 to 63, plus the mutation table); the plan is
`docs/logs/phase-3-plan.md`. The one-session exception to AI-11 is D-43.

**Result:** `mvn -B clean verify` passes for all 7 modules, **BUILD SUCCESS in 1:27**, **2510 tests** (2164
before, +346), 0 failures, 0 errors, 0 skipped. `host-check` passes (5 tests). All 11 mutations caught and restored
byte-identically. The real jar behaves as specified over real HTTP.

## Environment note

Maven is not on this machine's PATH and there was no local Maven repository. I used the Maven bundled with IntelliJ
(`C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin`, Maven 3.9.16, Temurin
17.0.20.1). The first build downloaded the dependency tree. The README and CLAUDE.md commands assume `mvn` is on
the PATH; that holds on CI.

## Step 0

- Tag `v0.0.2` exists: `CHANGELOG.md` `[Unreleased]` became `[0.0.2] - 2026-11-10`, with a fresh `[Unreleased]`.
- **D-42 lists** three files with the word `Order` as Spring's `@Order`: `JpaAdminSecurityConfiguration.java`
  (main) and the tests `HostIsolationTest.java` and `JpaAdminSecurityModesTest.java`, seven lines in all. The
  check found exactly those seven, and the allow-list (`docs/name-check-allow-list.txt`) holds one entry per line:
  1. `JpaAdminSecurityConfiguration.java`: `import org.springframework.core.annotation.Order;`
  2. `JpaAdminSecurityConfiguration.java`: `@Order(Ordered.HIGHEST_PRECEDENCE)` (the chain's precedence)
  3. `HostIsolationTest.java`: `import org.springframework.core.annotation.Order;`
  4. `HostIsolationTest.java`: `@Order(1)` (a test host's chain)
  5. `JpaAdminSecurityModesTest.java`: `import org.springframework.core.annotation.Order;`
  6. `JpaAdminSecurityModesTest.java`: `@Order(5)` (a test host's chain)
  7. `JpaAdminSecurityModesTest.java`: `assertThat(method.getAnnotation(Order.class).value())...` (reads the precedence)
- One **new** hit appeared during the phase: `Product` in a Javadoc example in my `Names.java`. The check caught it
  and I renamed the example (`invoiceLine`). No new allow-list entry was needed.

## 1. Files

From `git status --short` (40 tracked files changed, +1271/-119 per `git diff --stat`, plus the new files below).

**Modified:** `.github/workflows/ci.yml`, `CHANGELOG.md`, `docs/DECISIONS.md`, `docs/FIXTURES.md`, `docs/PLAN.md`,
`docs/PROJECT_DOCS.md`, `docs/UI_PLAN.md`; `jpa-admin-autoconfigure/pom.xml`, `JpaAdminAutoConfiguration.java`,
`JpaAdminProperties.java`, tests `JpaAdminAutoConfigurationTest.java`, `JpaAdminPropertiesTest.java`;
`jpa-admin-core/pom.xml`, `web/ConsoleChromeFactory.java`, `web/ConsoleController.java`, `web/ConsoleViews.java`,
`web/view/DashboardView.java`, `web/view/PageChrome.java`, tests `ConsolePagesTest.java`, `view/BasePathHrefRuleTest.java`,
`view/Samples.java`, `view/ViewHrefRuleTest.java`, `view/ViewInvariantsTest.java`, `view/ViewNullRejectionTest.java`;
`jpa-admin-ui-preview` `ChromeFixtures.java`, `PageFixtures.java`, `PreviewController.java`, `PreviewPagesTest.java`;
`jpa-admin-ui` `css/components.css`, `css/pages.css`, `js/nav.js`, `dashboard.html`, `fragments/icons.html`,
`fragments/sidebar.html`, `fragments/topbar.html`, `layout.html`; `sample-app` tests `ConsoleAtNestedPathTest.java`,
`ConsoleOwnThymeleafTest.java`, `ConsoleRenderedPagesTest.java`, `ConsoleSignInTest.java`.

**Created:** `docs/logs/phase-3-plan.md`, `phase-3-change-log.md`, `phase-3-report.md`, `docs/name-check-allow-list.txt`;
`jpa-admin-core/.../annotation/` (`AdminHidden`, `AdminSensitive`, `package-info`), `.../metadata/` (`AdminEntity`,
`AdminField`, `AdminEntityRegistry`, `DiscoverySettings`, `EntityScanner`, `Names`, `package-info`), `.../service/`
(`CountService`, `package-info`), `web/ConsoleNavigation.java`, `web/EntityController.java`; core tests
`metadata/` (`EntityScannerTest`, `NamesTest`, `testmodel/**`), `service/CountServiceTest`, `testsupport/TestPersistence`,
`web/ConsoleEntityPagesTest`, `src/test/resources/META-INF/persistence.xml`; autoconfigure tests
`DiscoveryAutoConfigurationTest`, `testmodel/Gizmo`; `jpa-admin-ui/.../entity-notice.html`; ui-preview tests
`NameCheck`, `NameCheckTest`; `sample-app/.../domain/InternalAuditEntry.java`, tests `ConsoleDiscoversEntitiesTest`,
`ConsoleHidesEntitiesTest`; `host-check/**` (pom, README, app, three entities, data loader, test, configs).

**Not mine:** `docs/prototype/` is untracked; you added it during the session. I read it and did not change it.

**Deleted:** none (the temporary `metadata/TestEntityManagerFactory.java` and `arch/` folder I created mid-phase
were folded into `testsupport/TestPersistence` and moved to ui-preview before the end).

## 2. Test counts

| Module | Before | After | New |
|---|---|---|---|
| jpa-admin-core | 1379 | 1505 | +126 |
| jpa-admin-autoconfigure | 438 | 446 | +8 |
| jpa-admin-ui-preview | 177 | 365 | +188 (parameterized matrix grew with new fixtures, +42 name check) |
| sample-app | 170 | 194 | +24 |
| **Total** | **2164** | **2510** | **+346** |
| host-check (outside reactor) | — | 5 | +5 |

Full `mvn -B clean verify`: BUILD SUCCESS, 1:27 min, 0 failures, 0 errors, 0 skipped, Enforcer rule passed.
`mvn -B -DskipTests install` then `mvn -B -f host-check/pom.xml verify`: BUILD SUCCESS, 5 tests.

## 3. Mutation checks

Each: break one line, run the targeted test (BUILD FAILURE = caught), restore from backup, compare MD5. All
restored files match the originals (MD5 in the change log).

| | Mutation | Caught by | Result |
|---|---|---|---|
| a | ignore `excluded-entities` | `EntityScannerTest` | 7 failures |
| b | ignore `@AdminHidden` | `EntityScannerTest` | 4 failures |
| c | case-sensitive sensitive matching | `NamesTest` | 6 failures |
| d | no TTL (count every request) | `CountServiceTest` | 4 failures |
| e | hidden entity found by URL | `EntityScannerTest` | 11 failures |
| f | hidden entity in the sidebar | `ConsoleHidesEntitiesTest` (sample-app) | 5 failures |
| g | no collision handling | `EntityScannerTest` | 4 failures |
| h | name check matches substrings | `NameCheckTest` | 14 failures |
| i | name check ignores stale entries | `NameCheckTest` | 1 failure |
| j | unsupported entity in the sidebar | `EntityScannerTest` | 30 errors |
| k | echo the entity path parameter | `ConsoleEntityPagesTest` | 7 failures |

(e) and (f) break the same line: a hidden entity is simply absent from the registry, one gate for both the URL and
the sidebar. The two rows show that a URL test and a sidebar test each catch it. (j) shows as errors because the
registry constructor refuses an unsupported entity, so the scan throws.

## 4. Decisions, deviations, host behaviour

**Decisions** (D-43 to D-49): one session on both tracks; the contract change; the URL-name rule; the 5-second TTL;
the temporary entity page; one primary `EntityManagerFactory`; the dependencies with reasons.

**Contract change (approved):** `PageChrome.user` (nullable) and `NavItem.count` / `EntityCard.count` as `Long`
(`null` = unavailable). All call sites and fixtures updated on both sides.

**Dependencies (approved where outside the list):** core `jakarta.persistence-api` and `spring-tx` (provided);
autoconfigure `jakarta.persistence-api` and `spring-orm` (provided, approved by you); test scope data-jpa + H2 in
core, H2 in autoconfigure. **Nothing new in the starter.**

**Deviations and choices to review:**
- **URL names are the lower-cased simple name** (`widgetpart`, not `widget-part`), as the brief literally says and
  as Django does. Camel case is not split.
- **Labels are humanized singular** (`Http log entry`), not plural. Plurals belong to `@AdminResource` later.
- **Annotation targets:** `@AdminHidden` also allows `METHOD`, and `@AdminSensitive` allows `FIELD` and `METHOD`, so
  property-access entities (annotations on getters) work. That is a slightly wider target set than "class or field".
- **`@AdminHidden` on an identifier is ignored** with a startup WARN (records are addressed by id).
- **The sign-out moved** from the top bar into the sidebar's user chip, so the page has one sign-out. The user chip
  shows only when the layout says the visitor is signed in and `chrome.user` is set, so error pages still show none.
- **The signed-in name** comes from `HttpServletRequest.getUserPrincipal()` (Spring Security's servlet API
  integration), so core still has no Spring Security dependency.
- **`ConsoleViews` got an overload without a `view` attribute** for the temporary entity page, which has no record.
  Its heading is read from the last breadcrumb.
- **`InternalAuditEntry`** (`@AdminHidden`, never seeded) was added to the demo app, to prove hiding end to end.
  The demo still shows exactly five entities.
- **`ConsoleRenderedPagesTest`'s label check** was a literal `<label for="...">` string, so a label with a class
  failed it. It now uses the same regex as the preview's `HtmlRules`. An unlabelled input still fails it.
- **The name check lives in `jpa-admin-ui-preview`** (next to `HtmlRules`), because it has to contain the forbidden
  names and must not be one of the modules it scans.
- **Open-in-view:** the sample app's existing `OpenInViewEnabledTest`/`OpenInViewDisabledTest` cover both settings,
  and the sample app runs with it off. I did not add a separate console-rendering test under `open-in-view=true`.
  The count service needs no open session (it runs in its own read-only transaction), so I judged that enough.
  Say if you want an explicit page test with it on.

**Two real bugs found by the tests, both fixed:**
1. Thymeleaf runs `th:replace` before `th:if`, so the `entity-icon` fragment rendered every icon. Fixed by putting
   them on separate elements.
2. CGLIB could not make the `@Transactional` proxy for `CountService` while its only constructor was private, so
   the sample app did not start. The constructor is now `protected`.

**Host-application behaviour to know:**
- **New queries:** each console page runs at most one `select count(*)` per visible entity every 5 seconds. A
  repeated page within the window runs none. On very large tables a count is not free; nothing is counted outside
  console pages.
- **Startup:** the scanner reads the metamodel once and logs one INFO line ("found N entities") and, only if
  needed, one WARN (unsupported entities, ignored `@AdminHidden` on an id, or several factories with none primary).
- **A failing count** logs a WARN with the cause's message and no stack trace, up to once per 5-second window per
  entity while the problem lasts.
- **Several `EntityManagerFactory` beans with no `@Primary`:** the console lists no entities; the host starts
  normally.

## 5. Not done, manual checks, contract

**Not done:** I could not drive a real browser in this session, so these visual checks are yours (the HTML is
tested, the look is not):
1. Sidebar search: type part of a name, the list filters, groups with no match hide, "No entity matches your
   search" shows; Escape clears it.
2. Group collapse: click a group header, it closes; reload, it stays closed; works with `localStorage` blocked.
3. Dark theme: toggle to dark, check the stat cards, group cards, user chip, avatar and the "unavailable" count.
4. Phone width (about 390px): the drawer opens, the search box and chip fit, the stat cards stack.
5. JavaScript off: the search box is hidden, groups still open and close, every link works.
6. Compare with `docs/prototype/admin-console.html`: brand subtitle, group headers with chevrons, counts at the
   right, user chip at the bottom.

Preview URLs for those: `/admin-preview/dashboard?state=normal&chrome=long-sidebar`,
`/admin-preview/dashboard?state=count-unavailable`, `/admin-preview/entity?state=not-available&chrome=entity-active`.

**No further contract change needed.**

## 6. Suggested commits (PowerShell-safe, never `git add .`)

```powershell
# 1. Contract change
git add jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/view/PageChrome.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/view/DashboardView.java jpa-admin-core/src/test/java/io/github/mdmusahub/jpaadmin/web/view
git commit -m "feat: add signed-in user and nullable counts to the view contract (D-44)"

# 2. Backend: annotations, metadata, counts, controllers, auto-configuration
git add jpa-admin-core/pom.xml jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/annotation jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/metadata jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/service jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ConsoleNavigation.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/EntityController.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ConsoleChromeFactory.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ConsoleController.java jpa-admin-core/src/main/java/io/github/mdmusahub/jpaadmin/web/ConsoleViews.java jpa-admin-autoconfigure/pom.xml jpa-admin-autoconfigure/src/main
git commit -m "feat: discover entities, count records and wire the console to them (Phase 3)"

# 3. Backend tests
git add jpa-admin-core/src/test jpa-admin-autoconfigure/src/test sample-app/src/main/java/io/github/mdmusahub/jpaadmin/sample/domain/InternalAuditEntry.java sample-app/src/test
git commit -m "test: cover the scanner, counts, entity pages and hiding end to end"

# 4. UI task U1 and preview
git add jpa-admin-ui jpa-admin-ui-preview/src/main jpa-admin-ui-preview/src/test/java/io/github/mdmusahub/jpaadmin/preview/PreviewPagesTest.java
git commit -m "feat: add sidebar search, groups, counts, user chip and dashboard cards (U1)"

# 5. ARCH-12 name check
git add docs/name-check-allow-list.txt jpa-admin-ui-preview/src/test/java/io/github/mdmusahub/jpaadmin/preview/NameCheck.java jpa-admin-ui-preview/src/test/java/io/github/mdmusahub/jpaadmin/preview/NameCheckTest.java
git commit -m "test: fail the build on demo entity names in library modules (ARCH-12)"

# 6. host-check and CI
git add host-check/pom.xml host-check/README.md host-check/src .github/workflows/ci.yml
git commit -m "test: add host-check project with different entities and run it in CI"

# 7. Docs and logs (add docs/prototype only if you want it in this phase's commits)
git add CHANGELOG.md docs/DECISIONS.md docs/FIXTURES.md docs/PLAN.md docs/PROJECT_DOCS.md docs/UI_PLAN.md docs/logs/phase-3-plan.md docs/logs/phase-3-change-log.md docs/logs/phase-3-report.md
git commit -m "docs: record Phase 3 decisions, update plan and add phase logs"
```

Each commit message ends with your attribution line if you use one. After review: merge and tag `v0.0.3`.
