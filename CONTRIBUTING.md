# Contributing to JPA Super Admin

Thank you for taking the time. A bug report, a question, a documentation fix or a patch are all welcome. This page says how to get set up, what a good change looks like and what happens to it.

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md). To report a security problem, do **not** open an issue: follow [SECURITY.md](SECURITY.md).

## Before you start

- **Look at the scope.** The plan is in [docs/PLAN.md](docs/PLAN.md) and the roadmap is in the documentation (Limitations and roadmap). Version 0.1 deliberately has one administrator, no audit log and no S3 storage; those are planned for later versions. A feature that is out of scope for 0.1 may be a good idea and still not be accepted yet.
- **Open an issue first** for anything larger than a small fix, so you do not spend a weekend on something that cannot be merged. Say what you want to do, and why.
- **Read the rules.** [docs/RULES.md](docs/RULES.md) lists the engineering, security, UI and testing rules. Each has an id (for example `SEC-3`) that a review will quote. The short version: never concatenate user input into a query, never show a sensitive value anywhere, keep the public API (annotations and properties) small, no new dependency without a recorded reason.

## Set up

You need **JDK 17** (the continuous integration build runs on 17 and 21) and **Maven 3.6.3 or newer** (what Spring Boot 4.1 asks for). Nothing else is needed to compile.

```
git clone https://github.com/mdmusahub/JPA-SUPER-ADMIN.git
cd JPA-SUPER-ADMIN
mvn -B clean verify
```

The full build takes about 20 minutes, because the demo application starts a fresh JVM for each test class. While you work on one module, build only that module and the ones it needs:

```
mvn -B -pl jpa-admin-core test            # core only
mvn -B -pl sample-app -am test            # the demo application, with its dependencies
```

Two kinds of test need something on your machine and are **skipped with their reason** when it is missing, never reported as passed:

- the **database tests** run the same scenarios on H2, PostgreSQL and MySQL; PostgreSQL and MySQL run in containers and need **Docker**;
- the **browser tests** drive a real headless **Chrome or Edge** (or set `-Dtest.chrome=<path>` or `CHROME_BIN`).

The continuous integration build fails instead of skipping (`JPA_ADMIN_REQUIRE_ENGINES=true` and `JPA_ADMIN_REQUIRE_BROWSER=true` make your local build do the same). Run the demo to see your change working:

```
mvn -pl sample-app -am package -DskipTests
JPA_ADMIN_USERNAME=admin JPA_ADMIN_PASSWORD='a password of at least 12 characters' java -jar sample-app/target/sample-app-0.1.0.jar
```

Then open <http://localhost:8080/admin>. Do not use `mvn -pl sample-app spring-boot:run` without `-am`: it can run stale jars from your local Maven repository.

## The layout

| Folder | What it is |
|---|---|
| `jpa-admin-core` | Metadata, services, controllers and the view-model records |
| `jpa-admin-autoconfigure` | Properties, the console's security chain and the auto-configuration |
| `jpa-admin-ui` | Thymeleaf templates, CSS and JavaScript only (no build step, no npm) |
| `jpa-admin-starter` | An empty pom, published as `springboot-super-admin`: the one dependency users add |
| `sample-app` | The demo shop and the integration and browser tests (not published) |
| `jpa-admin-ui-preview` | A preview app that draws every page from fixtures (not published) |
| `host-check` | A second, unrelated host application that adds only the starter (not part of the build) |

## What a good change looks like

- **One purpose.** A bug fix, a feature or a refactor, not all three.
- **A test in the same change** (TEST-1), and for anything that touches security a test that shows the bad input is refused (TEST-2). A bug fix starts with a test that fails.
- **A documentation and `CHANGELOG.md` entry** for anything a user can see (DOC-1). A new property or annotation option is documented with its default (DOC-2).
- **`mvn -B clean verify` passes**, and so does `mvn -B -f host-check/pom.xml verify` after `mvn -B -DskipTests install`.
- **Verified APIs.** Spring Boot 4 moved many packages; check an API against the Spring Boot 4.1 documentation or the library's own source and do not rely on a Boot 3 example (CODE-8).
- **No new dependency** unless you explain in the pull request why Spring Boot's own libraries cannot do the job (CODE-5).

Commit messages are short and in the imperative mood ("Mask sensitive columns in the export"). Open the pull request against `main` and fill in the template; it repeats the checklist above. A maintainer will review it, may ask for changes, and merges it when the build is green.

## Reporting a bug

Use the **Bug report** form. The most useful reports say which version of the starter, Spring Boot and Java you use, which database, and the smallest entity and request that show the problem. Never paste a real password, a key or a customer record.

## Questions

Open an issue (the **Feature request** form is fine for a question) or, if discussions are enabled for the repository, start a discussion. There is no chat.
