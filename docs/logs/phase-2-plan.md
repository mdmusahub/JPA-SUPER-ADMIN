# Phase 2 plan: login (backend and UI tracks in one session)

Branch `phase/2-login`, from tag `v0.0.1` (Phase 1 merged). Written 2026-11-01 before any code. Source of truth: `docs/PLAN.md` section 11 Phase 2, and task U0 in `docs/UI_PLAN.md` section 7.
No approval gate (per the session brief). I stop and ask only for: a dependency outside the pre-approved list, a public API/annotation/property name change, a frozen view-model record that cannot carry what a page needs, conflicting docs, or a security rule I cannot satisfy.
Both tracks are worked in this one session. That is a recorded exception to AI-11 (D-33), because the login page cannot be tested without the security chain.

Phase 2 "done when" (PLAN.md): redirect to login, bad login rejected, good login works, host routes untouched. A test shows the SEC-13 headers on console responses. A test shows the SEC-8 warning under `prod` over plain HTTP and none under other profiles.
U0 (UI_PLAN.md): tokens, base styles, layout, top bar, theme toggle, login page, error pages. Connected after Phase 2.

## 0. Missing input

`docs/prototype/admin-console.html` does not exist in the repository (D-13 says it should). I use the design tokens and layout in `docs/UI_PLAN.md` section 5 instead and say so in the report. The colour values, spacing and component look are my reading of that section, not a copy of the prototype.

## 1. What I verified against the 4.1.2 jars so far (CODE-8)

| Question | Answer (from `~/.m2` jars, `unzip`/`javap` on JDK 21) |
|---|---|
| What does `spring-boot-starter-security` 4.1.2 contain? | `spring-boot-starter` + `spring-boot-security` + `spring-aop`. Boot's security auto-configuration lives in `spring-boot-security` (package `org.springframework.boot.security.autoconfigure`), and that module is what brings `spring-security-config` and `spring-security-web` 7.1.2 |
| Which Boot auto-configurations act on a host just because Spring Security is on the classpath? | `ServletWebSecurityAutoConfiguration` (nested `SecurityFilterChainConfiguration`, conditional on no `SecurityFilterChain` bean, builds the default "everything authenticated, form login and basic" chain; nested `EnableWebSecurityConfiguration` builds `springSecurityFilterChain`), `SecurityFilterAutoConfiguration` (registers `springSecurityFilterChain` for every URL), `UserDetailsServiceAutoConfiguration` (conditional on no `AuthenticationManager`, `AuthenticationProvider`, `UserDetailsService` or `AuthenticationManagerResolver` bean; logs "Using generated security password: ..."), `SecurityAutoConfiguration` |
| Ordering | `ServletWebSecurityAutoConfiguration` is `after = UserDetailsServiceAutoConfiguration`; `SecurityFilterAutoConfiguration` is `after = ServletWebSecurityAutoConfiguration` |
| Maven Central | reachable; `spring-security-*:7.1.2`, `spring-boot-security:4.1.2`, `spring-boot-starter-security:4.1.2` and the test artifacts downloaded |

Still to verify before I rely on it (each becomes a log entry): `HttpSecurity` / `PathPatternRequestMatcher` / header configurer / `HttpSessionRequestCache` / `DaoAuthenticationProvider` / `CsrfToken` signatures in 7.1.2; that `FilterChainProxy` is not also registered as a plain `Filter` when I register it myself; how Boot's MockMvc support treats a `DelegatingFilterProxyRegistrationBean`; BCrypt's 72-byte limit; `ThymeleafViewResolver` behaviour.

## 2. The central design problem and how I solve it

The brief: one dedicated chain, highest precedence, matched only to `{path}/**`; a host without Spring Security gets no newly protected routes and no "generated security password" line; a host with its own chain behaves exactly as before.

With `spring-boot-starter-security` in the starter, every host that adds the starter also gets Boot's security auto-configuration. That has two consequences I will prove with a characterisation test and not take on trust:
1. A host with no security of its own sees `Using generated security password` in its log (`UserDetailsServiceAutoConfiguration`), because my chain is not one of the bean types that condition looks for, and I am told not to expose any of them.
2. A host that did ask for Spring Security but relies on Boot's default chain (no `SecurityFilterChain` of its own) would lose that default the moment my chain bean exists, because the default is `@ConditionalOnMissingBean(SecurityFilterChain)`. That silently weakens the host (SEC-4). I cannot tell this host apart from the first one at run time, because both have the same classpath.

**Decision (D-34, D-35): the starter brings the two Spring Security libraries (`spring-security-web`, `spring-security-config`, versions from the Boot BOM) and Thymeleaf's starter, but not `spring-boot-security`, Boot's auto-configuration module.** Boot's security auto-configuration is then active only if the host asked for it by adding `spring-boot-starter-security` itself. This is a subset of the pre-approved `spring-boot-starter-security` (the same two jars it delivers, minus Boot's auto-configuration), so it is not a new library. I flag it prominently in the report because it is not literally the artifact named in the brief. `spring-boot-starter-security` itself is used in **test scope** of `jpa-admin-autoconfigure` to build hosts that do have Boot security. Reverting is a pom change.

Two run-time modes, one chain bean:
- **Always:** a `SecurityFilterChain` bean at `Ordered.HIGHEST_PRECEDENCE`, `securityMatcher({path}/**)`. The authentication manager is a private `ProviderManager` (no parent, so the host's users can never log in to the console) holding one `DaoAuthenticationProvider` over a one-user lookup and a BCrypt hash made at startup. Nothing from Spring Security's extension points is exposed as a bean (no `UserDetailsService`, `AuthenticationManager`, `AuthenticationProvider`, `PasswordEncoder`, `RequestCache`, `AccessDeniedHandler` and so on), because `HttpSecurity` of the host's chains would pick them up.
- **Host has Spring Security (Boot's auto-configuration or its own `@EnableWebSecurity`):** `springSecurityFilterChain` already exists, so my chain is only added to the host's `FilterChainProxy`. My auto-configuration is ordered after Boot's `ServletWebSecurityAutoConfiguration`, so Boot decides about its default chain before my bean exists: a host that relies on the default keeps it, and my chain sits in front of it for `{path}/**` only.
- **Host has no Spring Security (the normal case):** my configuration imports `@EnableWebSecurity` itself, only when `springSecurityFilterChain` is missing, and registers the resulting filter for `{path}` and `{path}/*` only. Requests outside the console never reach Spring Security (no filter, no HTTP firewall, no generated user).

The console backs off entirely when Spring Security or Thymeleaf is missing from the classpath. An unprotected admin console is never an acceptable fallback.

## 3. Other design decisions made up front (all go into DECISIONS.md)

1. **Forms and sessions.** Form login at `{path}/login` (GET page from the controller, POST handled by the filter), `POST {path}/logout` only (explicit POST matcher), CSRF on with Spring's defaults for the whole chain, session fixation `changeSessionId` stated explicitly. No cookie or session property is touched (SEC-8). Spring Security's default `DisableEncodeUrlFilter` stays on, so no `;jsessionid` is added to links.
2. **No echo.** Failure handler is mine: redirect to `{path}/login?error`, without saving the exception (which carries the attempted credentials) in the session. Success handler is mine: target is `{path}`, or a saved request only when it is a GET whose own path lies under the console path, rebuilt as a relative path (so it cannot leave the origin). The saved-request attribute has its own name (`JPA_ADMIN_SAVED_REQUEST`) so a request saved by the host's chain is never replayed into the console and the reverse.
3. **BCrypt limit.** BCrypt cannot hash more than 72 bytes. A longer `jpa.admin.password` fails startup with a message that names the property (added to `JpaAdminProperties.validate()` with tests). Username is compared exactly (no `InMemoryUserDetailsManager`, which lower-cases).
4. **Headers (SEC-13)** on console responses only, written by the chain: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'`, and Spring's default `Cache-Control: no-cache, no-store, max-age=0, must-revalidate` on pages. **HSTS is switched off**: it applies to the whole host name, not to `/admin`, so the console must not set it (SEC-4). Assets and `theme.css` get an explicit short public cache so that the no-store default does not apply to them.
5. **`theme.css`** (SEC-15): `GET {path}/theme.css`, public, `text/css`, body is exactly `:root{--ac:#rrggbb}` from the validated property (the record re-checks the pattern). No inline style anywhere. `tokens.css` derives every other accent shade from `--ac` with `color-mix`.
6. **CSRF data for core.** `jpa-admin-core` stays free of Spring Security. It defines a small interface (`CsrfTokens`: request in, `PageChrome.Csrf` out) and `jpa-admin-autoconfigure` implements it by reading the request attribute that Spring Security's `CsrfFilter` sets. A request without a token gets an empty token (forms then fail the CSRF check, the safe outcome).
7. **Pages and errors.** `ConsoleController` (core, replaces `PlaceholderController`): `GET {path}` dashboard, `GET {path}/login`, the 403 page. `ThemeController`: `theme.css`. A `HandlerExceptionResolver` limited to the console path (it returns "not handled" for every other path, so host error handling is unchanged) renders the console's 404, 405 and 500 pages with a fixed text and logs the cause on the server. The access-denied handler (also for CSRF failures) forwards to the 403 page of the console (`AccessDeniedHandlerImpl` style); that page needs no data. Error texts are fixed constants; no exception message ever reaches a response (SEC-12).
8. **Chrome and views.** Controllers fill only `PageChrome` (`chrome`) and the page record (`view`). Login uses an empty `nav`. Dashboard is built from zero values (`Stats(0,0,0,null)`, no groups, no chart), which the record allows. No contract change is needed.
9. **SEC-8.** Under the `prod` profile a startup WARN is logged when the console is reachable over plain HTTP: no `server.ssl.*` configured (and not disabled) and no trusted forwarding (`server.forward-headers-strategy` of `native` or `framework`, or a Tomcat `remoteip` protocol header). The text names the remedy for both the direct and the reverse-proxy case. Nothing about cookies is changed.
10. **UI.** Pure Thymeleaf fragments (`~{layout :: layout(title, content)}` pattern, no layout dialect). No inline script, style or handler anywhere. Icons are inline SVG fragments (no external sprite, so the CSP needs no extra source). Login is a standalone centred card; the dashboard uses the shell; error pages use the shell without the sign-out button (the `PageChrome` record has no "signed in" flag, and an anonymous visitor can reach the 403 page). `theme.js` is the only script in `<head>`, blocking, applies `data-theme` before first paint and wires the toggle after `DOMContentLoaded`; the sidebar falls back to a stacked block when JavaScript is off. Links go through `@{...}` so a servlet context path works.
11. **Preview.** `PreviewController` at `/admin-preview/{page}?state=&chrome=`, fixtures built through the real record constructors, its own tiny asset and `theme.css` mapping (the preview does not depend on the autoconfigure module), fails under `prod` as before.

## 4. Steps (ordered; module build after each, full build at the end)

| Step | Work | Build |
|---|---|---|
| 0 | Docs: CHANGELOG `[0.0.1]` split; D-31 pointers in RULES.md GIT-2 and PLAN.md; D-30 annotation; D-33 (AI-11 exception) | none |
| 1 | Backend security: poms; characterisation test of Boot's behaviour; `JpaAdminSecurityConfiguration` (chain, local auth, headers, handlers), standalone infrastructure, CSRF interface, SEC-8 check; `BCrypt` 72-byte rule. Experiments first for the risky parts (filter registration, MockMvc) | `-pl jpa-admin-autoconfigure -am` |
| 2 | `theme.css` controller | same |
| 3 | Core pages: `ConsoleController`, chrome factory, `ErrorViews`, exception resolver; replace the placeholder; adapt Phase 1 tests deliberately | `-pl jpa-admin-core,jpa-admin-autoconfigure,sample-app -am` |
| 4 | UI U0: tokens, base, components, pages CSS, `theme.js`, `nav.js`, `layout`, `login`, `dashboard`, `error`, fragments | `-pl sample-app -am` |
| 5 | Preview app: controller, fixtures, asset mapping | `-pl jpa-admin-ui-preview -am` |
| 6 | Tests (see section 5) | module builds |
| 7 | Mutation checks (a) to (j) from the brief, one at a time, restore, verify by hash | module builds |
| 8 | Docs: PLAN.md Phase 2 checklist, UI_PLAN.md U0 status, DECISIONS.md, CHANGELOG, PROJECT_DOCS.md, FIXTURES.md (U0 rows) | full `mvn -B clean verify` |
| Manual | Build jar, run with the two properties, `curl.exe -i` checks, preview app checks, stop only my processes, ARCH-12 grep | |
| Report | `docs/logs/phase-2-report.md` and in chat | |

## 5. Tests (names say what they prove; at least one negative test per rule)

- **Isolation:** host route unchanged with the console on and off; host with its own chain (`/api/**`, HTTP Basic) keeps exactly its behaviour, including its own failure responses, and the console works beside it; host relying on Boot's default chain keeps it; a host catch-all chain at a high precedence does not capture the console; no `Using generated security password` line in a host without Spring Security; no `UserDetailsService`, `AuthenticationManager`, `AuthenticationProvider` or `PasswordEncoder` bean from us; the console's login does not accept the host's users; the host's login does not accept the console's admin.
- **Authentication:** unauthenticated `GET {path}` redirects to login; protected GETs and POSTs are blocked; assets, login and `theme.css` are public; wrong username and wrong password give identical status, location and body; correct login lands on `{path}` and the dashboard renders; session id changes after login; logout is POST + CSRF only, invalidates the session and shows the notice; GET logout does nothing; a second login after logout works.
- **CSRF:** login POST and logout POST without a token give the console 403 page with no stack trace or internal word; with a wrong token the same.
- **Open redirect:** `redirect`, `continue`, `next`, `returnTo`, `url`, `redirect_uri` parameters with external values are ignored; a saved request for another origin or for a path outside the console is never followed; a saved request under the console is.
- **Secrets:** neither the password nor its hash in any body, redirect URL, header, session attribute name list or log output (also at DEBUG for `org.springframework.security` and this project).
- **Headers:** every SEC-13 header on login, dashboard, error, assets and `theme.css` responses; none on host routes; no-store on authenticated pages; no HSTS.
- **Paths:** `/backoffice` works for everything, `/admin` then returns 404 and is not protected by us; nested path.
- **Static template rules** (in `jpa-admin-ui-preview`, which sees the UI jar): no `th:utext`, no inline script or style or `on*` handler, no `http://` or `https://` in templates or CSS, every expression reads only `chrome`, `view` or a declared local, no `#request`/`#session`/`@bean`/`T(`.
- **Rendered HTML** (login and error): labels, `aria-*`, `theme.js` blocking in `<head>`, nothing inline, no `jsessionid`.
- **Preview:** every fixture renders and shows its key content; unknown page or state gives 404; prod guard still fails startup.
- **Prod warning:** logged under `prod` over plain HTTP; absent under other profiles, with `server.ssl`, and with forwarding.
- All Phase 0 and 1 tests keep passing. Where the placeholder page used to answer 200 the test now expects the sign-in redirect and runs the same assertion again as a signed-in admin, so no check is dropped (AI-6).

## 6. Files (planned)

Created: `docs/logs/phase-2-*.md`; autoconfigure: `JpaAdminSecurityConfiguration`, `JpaAdminStandaloneSecurityConfiguration`, `ConsoleAccessDeniedHandler`, `ConsoleAuthenticationHandlers` (success and failure), `SpringSecurityCsrfTokens`, `JpaAdminTransportCheck`, tests; core: `web/ConsoleController`, `web/ConsoleChromeFactory`, `web/CsrfTokens`, `web/ConsoleSettings`, `web/ErrorViews`, `web/ConsoleExceptionResolver`, `web/ThemeController`, tests; ui: templates, fragments, four CSS files, two JS files; ui-preview: controller, fixtures, tests; sample-app tests.
Modified: poms (starter, autoconfigure, sample-app, ui-preview), `JpaAdminAutoConfiguration`, `JpaAdminProperties`, `JpaAdminAssetsConfigurer` (cache), Phase 1 tests that assert the placeholder, `docs/*`, `CHANGELOG.md`.
Deleted: `PlaceholderController` and its test; `templates/jpa-admin/.gitkeep` once real templates exist.

## 7. Things I will not do

No commit, no Phase 3 work (no scanner, no entity routes, no sidebar data), no change to the view-model records, no build tool or npm, no external URL in any template or stylesheet, no weakening of any test or rule, no global session or cookie setting.
