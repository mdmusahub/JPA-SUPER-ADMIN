# JPA Super Admin

An open-source admin console for Spring Boot. Add one dependency, set two properties, open `/admin`, and manage every JPA entity of your application, much like Django Admin. Entities are discovered automatically; there is nothing to register and no front-end build.

**Status: in development (v0.1).** The features below are built phase by phase (release candidates are tagged `v0.1.0-rc.<phase>`); the first release and its Maven coordinates come with Phase 14.

![The product list in the light theme](docs/images/list-light.png)

|  |  |
|---|---|
| ![The dashboard in the dark theme](docs/images/dashboard-dark.png) | ![A record in the light theme](docs/images/detail-light.png) |
| ![The product list on a phone](docs/images/list-phone-dark.png) | |

## What you get

- A dashboard with record counts and a "Records created" chart, and a grouped sidebar of every entity.
- Lists with search, sorting, paging, per-field filters, column visibility, saved views (kept in your browser) and CSV export.
- Detail pages, create, edit, duplicate, delete and bulk delete, with validation errors next to the fields and an unsaved-changes guard.
- File and image uploads with a local storage provider and a storage interface for your own.
- Sensitive fields (passwords, tokens, keys) are masked everywhere: in lists, forms, exports, logs and errors.
- A separate sign-in with one administrator, CSRF protection on every change, and a security chain that never touches your own application's rules.
- Light and dark theme, a phone layout, keyboard use (`/` jumps to the search box) and a measured contrast of at least 4.5 to 1 for text.

## Quick start (about five minutes)

Requirements: Java 17 or newer, a Spring Boot 4.1 application with Spring Data JPA.

1. **Add the starter.** Until the first release is published, install it from this repository (`mvn -DskipTests install`) and use the version `0.1.0-SNAPSHOT`:

   ```xml
   <dependency>
       <groupId>io.github.mdmusahub</groupId>
       <artifactId>springboot-super-admin</artifactId>
       <version>0.1.0-SNAPSHOT</version>
   </dependency>
   ```

2. **Set the two required properties.** The password is hashed with BCrypt at startup and never logged; use at least 12 characters (under the `prod` profile a weak password stops startup):

   ```yaml
   jpa:
     admin:
       username: admin
       password: ${ADMIN_PASSWORD}
   ```

3. **Start your application and open `http://localhost:8080/admin`.** Sign in and manage your entities. A missing username or password stops startup with a message that says what to add.

To see it with demo data first, build and run the sample application (do not use `spring-boot:run` without `-am`, it can run stale jars):

```powershell
mvn -pl sample-app -am package -DskipTests
$env:JPA_ADMIN_USERNAME = 'admin'
$env:JPA_ADMIN_PASSWORD = '<12 or more characters>'
java -jar sample-app/target/sample-app-0.1.0-SNAPSHOT.jar
```

## Configuration at a glance

| Property | Default | Meaning |
|---|---|---|
| `jpa.admin.username`, `jpa.admin.password` | none | Required |
| `jpa.admin.path` | `/admin` | Where the console lives |
| `jpa.admin.title` | `JPA Admin` | Header and sign-in title |
| `jpa.admin.primary-color` | `#0d9f6e` | Accent colour, `#rrggbb` |
| `jpa.admin.page-size` | `25` | Rows per page: 10, 25, 50 or 100 |
| `jpa.admin.excluded-entities` | none | Entities to hide |
| `jpa.admin.sensitive-fields` | `password, secret, token, apikey` | Names that are always masked |
| `jpa.admin.export-enabled`, `export-max-rows`, `export-bom` | `true`, `50000`, `false` | CSV export |
| `jpa.admin.storage.*`, `jpa.admin.upload.signing-key` | local `./uploads`, 5MB | File uploads |

Every wrong value stops startup with a message that names the property. The full reference is in `docs/PROJECT_DOCS.md` (section B4).

## Make it fit your entities

All annotations are optional (package `io.github.mdmusahub.jpaadmin.annotation`):

```java
@Entity
@AdminResource(plural = "People", deletable = false)   // headings say "People"; records cannot be deleted
public class Person {

    @Enumerated(EnumType.STRING)
    @AdminBadge({"ACTIVE=green", "PENDING=amber", "BANNED=red"})   // badge colours: green, amber, red, grey
    private PersonStatus status;

    @ManyToOne
    @AdminLookup("email")                                // show the related record by its email
    private Company company;

    @AdminSensitive                                      // masked everywhere
    private String recoveryCode;

    @AdminUpload(folder = "avatars", maxSize = "2MB")    // a file field
    private String avatar;
}
```

`@AdminHidden` removes an entity or a field from the console completely. See `docs/PROJECT_DOCS.md` (section B9) for every option and its default.

## Documentation

- `docs/PLAN.md`: scope, architecture, routes and phases. `docs/RULES.md`: the engineering and security rules. `docs/PROJECT_DOCS.md`: requirements and user documentation. `docs/UI_PLAN.md`: how the interface is built. `docs/DECISIONS.md`: every design decision with its reason. `CHANGELOG.md`: what changed.
- Build everything and run all tests: `mvn clean verify`. The real-browser tests use a Chrome or Edge on the machine (or `-Dtest.chrome=<path>`) and are skipped, with the reason, when there is none.

## Security notes

The console is a high-value target: serve it over HTTPS, set `Secure` and `SameSite` on your session cookie yourself (the console never changes global cookie settings), and put it behind a VPN or an IP allow-list when it faces the internet. Login rate limiting is planned for v0.2.

## About the author

I'm Muhammad Musa, a Java and Spring Boot developer who likes building tools that save other developers time.

**Tech I work with:** Java · Spring Boot · Spring Security · JPA/Hibernate · Maven · PostgreSQL · MySQL

**Get in touch:** <your email or LinkedIn>
