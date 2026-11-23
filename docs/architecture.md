# Architecture

A map for people who want to read or change the code. The rules the code follows are in the repository's [RULES.md](https://github.com/mdmusahub/JPA-SUPER-ADMIN/blob/main/docs/RULES.md), each with an id (`SEC-3`, `ARCH-2`, ...) that a review quotes; the design decisions and their reasons are in [DECISIONS.md](https://github.com/mdmusahub/JPA-SUPER-ADMIN/blob/main/docs/DECISIONS.md).

## Modules

| Folder | Artifact | Published | What it holds |
|---|---|---|---|
| (root) | `jpa-super-admin` | yes | The parent pom: Spring Boot 4.1.2 as its parent, the release profile, the Enforcer rule |
| `jpa-admin-core` | `jpa-admin-core` | yes | Metadata, services, controllers and the view-model records. No templates |
| `jpa-admin-autoconfigure` | `jpa-admin-autoconfigure` | yes | The properties, the console's security chain, the auto-configuration, the asset handler |
| `jpa-admin-ui` | `jpa-admin-ui` | yes | Thymeleaf templates, CSS and JavaScript only: a resource jar with no logic and no build step |
| `jpa-admin-starter` | `springboot-super-admin` | yes | An empty pom: the one dependency users add. It depends on the three above |
| `sample-app` | `sample-app` | **no** | The demo shop, the integration tests and the real-browser tests |
| `jpa-admin-ui-preview` | `jpa-admin-ui-preview` | **no** | An app that draws every page from fixtures; never a dependency of any other module |
| `host-check` | (not in the build) | **no** | A second, unrelated host application that adds only the starter, built against the installed release |

Rules that the build checks: the preview app can never be a dependency of another module (a Maven Enforcer rule); the library modules contain no demo entity name and no reference to `sample-app` (a name check that reads every file); and the console's configuration never changes the host's own beans or security (a host-isolation test).

## The pieces

| Piece | Responsibility |
|---|---|
| `JpaAdminProperties` (autoconfigure) | The validated `jpa.admin.*` configuration |
| `JpaAdminAutoConfiguration` | Registers the console's beans only for a servlet web application that has JPA, and backs off cleanly otherwise |
| `JpaAdminSecurityConfiguration` | The dedicated security chain and the one in-memory BCrypt administrator |
| `EntityScanner` (core) | Reads the JPA metamodel once at startup and builds immutable metadata (`AdminEntity`, `AdminField`) |
| `AdminEntityRegistry` | Looks an entity up by its address name |
| `ListQueryService`, `DetailQueryService`, `FormQueryService` | The read side: list, count, detail and form data, with the Criteria API and bound parameters, in read-only transactions |
| `RecordWriteService`, `DeleteService` | Create, update, duplicate, delete and bulk delete, each in one transaction |
| `ValueConverter`, `CellFormatter` | Text from a form to a typed value, and a value to display text with masking |
| `ExportService`, `CsvWriter` | Streams the CSV from the same validated query as the list, in pages, with the formula guard |
| `StatsService`, `CountService` | The record counts and the "Records created" series, cached for five seconds |
| `StorageProvider` (SPI), `LocalStorageProvider` | Where uploaded files live; replaceable with a bean of your own |
| `UploadController`, `UploadTicketService`, `UploadService` | Ticket-checked uploads and safe file serving |
| `EntityController`, `RecordController`, `FormController`, `ExportController`, `StatsController`, `ConsoleController` | Thin controllers: they resolve the entity, call a service and hand a view-model record to a template |
| the `web.view` records | The only data a template can read (`ListView`, `FormView`, `DetailView`, ...). They are public because the preview app needs them, and they are **internal, not stable API** |

Controllers never touch the `EntityManager`; templates never call a service, a repository or an entity; and no raw entity or collection is ever put in a model.

## A list request

The browser asks for `/admin/product?q=&filter.status=Published&sort=price&dir=desc&page=0`. The console's chain checks the session and the CSRF rules. The controller resolves the entity (a 404 for an unknown or hidden one). `ListQueryService` validates every parameter against the metadata, runs the query and a count with the Criteria API, and the view factory turns the rows into display values, masking sensitive ones. Thymeleaf renders the page from the view record. The CSV export reuses the same validated query.

## The user interface

Templates live in `jpa-admin-ui` under `templates/jpa-admin/` and share one layout. Styles and scripts are static files under `jpa-admin/assets/` and are served only by the console's own resource handler, at `/admin/assets/**`. There are no inline scripts and no inline styles, which is what lets the Content-Security-Policy be strict. Every page works without JavaScript; script only enhances.
