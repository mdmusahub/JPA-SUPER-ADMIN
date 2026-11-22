# Troubleshooting

## Maven: "Could not find artifact ...springboot-super-admin:jar:0.1.0"

The starter is a pom-only artifact. Add `<type>pom</type>` to the dependency:

```xml
<dependency>
    <groupId>io.github.mdmusahub</groupId>
    <artifactId>springboot-super-admin</artifactId>
    <version>0.1.0</version>
    <type>pom</type>
</dependency>
```

## The application does not start

**"... jpa.admin.username ..." or "... jpa.admin.password ..." is missing.** Both are required. A value that is blank, or an environment variable that is not set (`password: ${ADMIN_PASSWORD}` with no such variable), counts as missing. The message names the property.

**The password is refused under the `prod` profile.** Under `prod` the password must have at least 12 characters and must not be a common one. Choose a longer one.

**Startup stops and lists upload fields.** Read the list: it names each field and the reason. The usual causes are a storage that is not configured, a storage type other than `local`, a folder that cannot be created or written to or that lies inside the web root, and an `accept` type the console cannot recognise (only PNG, JPEG, GIF, WebP and PDF can be).

**Startup stops and names a property.** Every wrong value (`jpa.admin.path`, `jpa.admin.primary-color`, `jpa.admin.page-size`, `jpa.admin.export-max-rows`, the storage and signing-key properties) stops startup with one report that names the property. It never repeats a secret.

## The console does not show up

**404 at `/admin`, or a 500 with the log line "did not pass through the console's security chain".** Most often a servlet path prefix: `spring.mvc.servlet.path` must be unset in 0.1. The log line says why.

**An entity is missing from the sidebar.** It is hidden (`@AdminHidden`, `jpa.admin.excluded-entities`), it has a composite primary key (`@EmbeddedId` or `@IdClass`, unsupported in 0.1: the console logs one warning at startup), or your application has several `EntityManagerFactory` beans and none is marked `@Primary` (the console then lists nothing and logs a warning rather than guess).

**404 for an entity that exists.** An entity that is hidden answers exactly like one that does not exist.

## Pages and lists

**A search fails with the error page on one entity.** An entity with a `@Lob String` attribute cannot be searched on any database in 0.1. The list without a search works. Keep such an attribute out of the entities you search, or map the text without `@Lob`.

**A field I did not mark is masked.** The sensitive-field match is a substring match: `tokenizer` contains `token`. Narrow `jpa.admin.sensitive-fields`.

**A date looks one day off, or a time shows another hour.** A date and time stored with an offset is shown in UTC on PostgreSQL and MySQL, and with its offset on H2. Days in the chart follow the server's time zone (or your `Clock` bean), not the viewer's. Set the JVM's zone at start (`-Duser.timezone=...`) and do not change it while the application runs.

**The PostgreSQL connection is refused with `invalid value for parameter "TimeZone"`.** The JVM reports a legacy zone name such as `Asia/Calcutta`, which recent PostgreSQL images no longer know. Start with `-Duser.timezone=Asia/Kolkata` (the canonical name).

**The page is slow on a large table.** See "Large tables" under [Deployment](deployment.md) and the measurements on [Limitations and roadmap](limitations-and-roadmap.md).

## Saving and uploading

**"Stale" message when saving.** Another change was saved after the form was opened (entities with a `@Version`). Open the record again.

**A delete is refused.** Other records still depend on the record. The page says so in plain words; delete or change those first.

**Upload: "larger than this server accepts" (413).** Raise your application's `spring.servlet.multipart.max-file-size` and `max-request-size`; Spring Boot's defaults are 1MB and 10MB, below the console's 5MB default.

**Upload: "no longer valid" (403).** The form was open for more than 10 minutes, the application restarted without a configured `jpa.admin.upload.signing-key`, or the request reached another instance with another key. Open the form again, and set the key when you run several instances.

**Upload: "the file has page or script markup hidden inside it".** The file contains text such as `<script` or `<html`, which the console refuses in any file. Export a clean copy.

**Public files ask for a login.** A storage's `public-path` is protected by your own security rules: permit the path in your configuration.

## Still stuck?

Search the [issues](https://github.com/mdmusahub/JPA-SUPER-ADMIN/issues), and if yours is new, open one with the **Bug report** form: it asks for the versions and the smallest example that shows the problem. Never paste a real password, key or customer record.
