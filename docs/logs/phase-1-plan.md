# Phase 1 plan: auto-configuration (backend track)

Branch `phase/1-autoconfig`, started from `v0.0.0`. Written 2026-11-01 before any code. Source of truth: `docs/PLAN.md` section 11, Phase 1.
No approval gate (per the session brief). I stop and ask only for: an unapproved dependency, a public API/property/annotation name change, conflicting docs, or a security rule I cannot satisfy.

Phase 1 "done when" (PLAN.md):
1. The starter alone makes `/admin` respond.
2. A missing username fails startup clearly.
3. A runtime test proves `/admin/assets/**` serves assets and `/jpa-admin/assets/**` (and any path outside the console path) returns 404.
4. Controller tests assert every link a controller builds is under the base path (D-29).

## 1. What I verified against the 4.1.2 jars (CODE-8)

| Question | Answer (from `~/.m2` jars, `unzip`/`javap`) |
|---|---|
| Where do `@AutoConfiguration`, `@ConditionalOnWebApplication/Class/Property/MissingBean`, `AutoConfigurations` live? | Still `spring-boot-autoconfigure`: `org.springframework.boot.autoconfigure[.condition]` |
| Which auto-configuration packages moved in Boot 4? | `WebMvcAutoConfiguration`, `DispatcherServletAutoConfiguration`: `org.springframework.boot.webmvc.autoconfigure` (module `spring-boot-webmvc`). `HibernateJpaAutoConfiguration`: `org.springframework.boot.hibernate.autoconfigure` (`spring-boot-hibernate`). `DataJpaRepositoriesAutoConfiguration`: `org.springframework.boot.data.jpa.autoconfigure`. `DataSourceAutoConfiguration`: `org.springframework.boot.jdbc.autoconfigure`. `ValidationAutoConfiguration`: `org.springframework.boot.validation.autoconfigure`. `HttpMessageConvertersAutoConfiguration`: `org.springframework.boot.http.converter.autoconfigure`. `JacksonAutoConfiguration`: `org.springframework.boot.jackson.autoconfigure`. `TransactionAutoConfiguration`: `org.springframework.boot.transaction.autoconfigure`. Servlet extras (`HttpEncodingAutoConfiguration`, `MultipartAutoConfiguration`): `org.springframework.boot.servlet.autoconfigure`. Tomcat: `org.springframework.boot.tomcat.autoconfigure`. In 3.x all of these were under `org.springframework.boot.autoconfigure.<tech>`. |
| Registration file | Still `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (every Boot 4 module uses it) |
| Test runners | `WebApplicationContextRunner`, `ApplicationContextRunner`, `FilteredClassLoader`: `org.springframework.boot.test.context.runner` / `...test.context` (module `spring-boot-test`). `OutputCaptureExtension`: `org.springframework.boot.test.system`. `@AutoConfigureMockMvc`: `org.springframework.boot.webmvc.test.autoconfigure` (module `spring-boot-webmvc-test`, via `spring-boot-starter-webmvc-test`) |
| Failure analysis | `FailureAnalyzer`, `AbstractFailureAnalyzer`, `FailureAnalysis` unchanged in `spring-boot` (`org.springframework.boot.diagnostics`), registered in `META-INF/spring.factories` |
| MVC APIs (Spring Framework 7.0.10) | `PathMatchConfigurer.addPathPrefix(String, Predicate<Class<?>>)`, `ResourceHandlerRegistry.addResourceHandler(String...)`, `ResourceHandlerRegistration.addResourceLocations(String...)`, `WebMvcConfigurer.configurePathMatch/addResourceHandlers` all exist |
| Bean Validation on `@ConfigurationProperties` | `ConfigurationPropertiesBinder` validates only when `ConfigurationPropertiesJsr303Validator.isJsr303Present(...)`. With no provider on the classpath the constraints are skipped silently |
| Default analyzer output | `BindValidationFailureAnalyzer` prints `Value: "<rejected value>"` for each failed field |
| Unresolved placeholders | `PropertySourcesPlaceholdersResolver` builds its `PropertyPlaceholderHelper` with `ignoreUnresolvablePlaceholders=true`, so `password: ${ADMIN_PASSWORD}` with no such variable binds as the literal text (I prove this with a test) |

## 2. Design decisions made up front (all go into D-30)

1. **Explicit validation instead of Bean Validation.** `username`, `password`, `path` and `primary-color` are validated by `JpaAdminProperties.validate()` (called from `afterPropertiesSet`), not by `@Validated` constraints. Reasons: (a) constraints are skipped silently when no validation provider is on the classpath, and these are security rules (SEC-7, SEC-14, SEC-15); (b) the default analyzer echoes the rejected value, and SEC-6 forbids printing the password; (c) one exception type and one analyzer give one clear message, listing every problem at once. `spring-boot-starter-validation` was pre-approved but is **not added** (fewer dependencies, CODE-5).
2. **Unresolved placeholder guard.** A username or password that is still `${...}` after binding (an unset environment variable) is treated as missing. Without this, the PLAN's own example (`password: ${ADMIN_PASSWORD}`) would start with the password `${ADMIN_PASSWORD}`.
3. **Path prefix instead of path text in the controller.** Console controllers (package `io.github.mdmusahub.jpaadmin.web`) declare relative mappings. A `WebMvcConfigurer` adds the base path as a prefix to handlers from that package only. Host controllers are never prefixed. This keeps `core` free of the property name, and validation always runs before the mapping is built.
4. **`BasePath`** (core, `io.github.mdmusahub.jpaadmin.web`): canonical, already-validated path. It never rewrites input ("normalized" = canonical form only, everything else is rejected). Exposes `value()`, `link(relative)` and `assetsPattern()`.
5. **Where code lives.** `core`: `BasePath`, `PlaceholderController`. `autoconfigure`: properties, validation, password policy, failure analyzer, auto-configuration, the two `WebMvcConfigurer`s. Types that the auto-configuration instantiates across modules are public but documented as internal (ARCH-9 is SHOULD).
6. **Replaceable beans** (`@ConditionalOnMissingBean`): the placeholder controller and the assets configurer. Not replaceable: base path, path-prefix configurer, password guard (security).
7. **Sample app credentials.** `sample-app/src/main/resources/application.yml` gets **no** credentials (GIT-3, and the "start without the username" check needs them absent). Tests get fake, obviously-test-only values from `sample-app/src/test/resources/application.properties`. The demo is run with environment variables or arguments.
8. **Dependencies** (details in D-30): `spring-boot-starter-webmvc` (starter: real; core/autoconfigure: optional), `spring-boot-configuration-processor` (autoconfigure, optional), test scope `spring-boot-starter-test` and `spring-boot-starter-webmvc-test`. One use of an already-approved starter: `spring-boot-starter-data-jpa` in **test scope** of `jpa-admin-autoconfigure` (approved in D-19), so `jakarta.persistence.EntityManager` is on the test classpath and the "active" condition can be tested. The main code checks that class by name and has no JPA dependency.

## 3. Steps (in order; module build after each, full build at the end)

| Step | Work | Build |
|---|---|---|
| 0 | `CHANGELOG.md`: `[Unreleased]` becomes `[0.0.0] - 2026-11-01`, new empty `[Unreleased]` above. Baseline `mvn -B clean verify` and test counts | full |
| 1 | POMs (deps above). `JpaAdminProperties`, `JpaAdminConfigurationException`, `JpaAdminFailureAnalyzer` (+ `META-INF/spring.factories`), `PasswordPolicy`, `JpaAdminPasswordGuard`. Tests: binding, missing/blank, placeholder, bad path/color, toString, analyzer output, prod/non-prod password | `-pl jpa-admin-autoconfigure -am` |
| 2 | `JpaAdminAutoConfiguration` + imports file. Tests with `ApplicationContextRunner`/`WebApplicationContextRunner`: servlet active, non-web absent, disabled absent, no JPA absent, no webmvc absent, replaceable beans back off, imports file correct | `-pl jpa-admin-autoconfigure -am` |
| 3 | `BasePath` (core) + tests, including a test in package `web.view` proving results pass `Hrefs` and `PageChrome` | `-pl jpa-admin-core -am` |
| 4 | `PlaceholderController` (core) + path-prefix configurer. Plain text "JPA Admin console is running", comment "replaced in Phase 2" | `-pl jpa-admin-core,jpa-admin-autoconfigure -am` |
| 5 | `JpaAdminAssetsConfigurer`: `{path}/assets/**` to `classpath:/jpa-admin/assets/` only. One placeholder asset `jpa-admin-ui/.../jpa-admin/assets/css/tokens.css` (touches the UI folder; logged) | `-pl sample-app -am` |
| 6 | sample-app tests (MockMvc): default path 200, custom path, old path 404, assets 200, three outside-the-path 404s, traversal, host static content untouched, host controller untouched, `add-mappings=false`, disabled, missing username fails with message and no password in output | `-pl sample-app -am` |
| 7 | Mutation checks (a) username rule (b) path validation (c) asset mapping (d) prod password rule. One at a time, restore, rebuild green | module builds |
| 8 | Docs: PLAN.md (Phase 1 checklist ticked; section 5 notes), UI_PLAN.md asset item, DECISIONS.md D-30, CHANGELOG `[Unreleased]`. PROJECT_DOCS.md only if a description changed | full `mvn -B clean verify` |
| Manual | Run sample-app with the properties (expect 200), without the username (expect clear stop), stop all processes, confirm 8080/8081 free, ARCH-12 grep | |
| Report | `docs/logs/phase-1-report.md` and in chat | |

## 4. Files (planned)

Created: `docs/logs/phase-1-plan.md`, `docs/logs/phase-1-change-log.md`, `docs/logs/phase-1-report.md`; in `jpa-admin-autoconfigure`: `JpaAdminProperties`, `JpaAdminConfigurationException`, `JpaAdminFailureAnalyzer`, `PasswordPolicy`, `JpaAdminPasswordGuard`, `JpaAdminAutoConfiguration`, `JpaAdminPathPrefixConfigurer`, `JpaAdminAssetsConfigurer`, `META-INF/spring.factories`, `META-INF/spring/...AutoConfiguration.imports`, tests; in `jpa-admin-core`: `web/BasePath`, `web/PlaceholderController`, tests; `jpa-admin-ui/.../css/tokens.css`; sample-app tests and `src/test/resources/application.properties`, a test-only static file.
Modified: `CHANGELOG.md`, `pom.xml` files of core, autoconfigure, starter, sample-app, `docs/PLAN.md`, `docs/UI_PLAN.md`, `docs/DECISIONS.md`, `package-info.java` of autoconfigure, `sample-app/.../application.yml` only if needed (planned: not changed).

## 5. Things I will not do

No Spring Security, no Thymeleaf, no scanner, no properties for later phases, no commit, no Phase 2 work, no change to the view-model records, no weakening of any test.
