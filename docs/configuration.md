# Configuration

Every property has a default except the two that are required. A wrong value stops startup with one report that names the property (it never repeats a secret).

## All properties

| Property | Default | Notes |
|---|---|---|
| `jpa.admin.enabled` | `true` | Master switch |
| `jpa.admin.path` | `/admin` | Starts with `/`, then letters, digits, `-` and `_` in each segment, segments separated by single slashes, at most 100 characters. Cannot be `/`, no trailing slash (SEC-14). A bad value stops startup |
| `jpa.admin.username` | none | Required. Missing, blank, or an unset `${...}` variable stops startup with a message naming the property |
| `jpa.admin.password` | none | Required, BCrypt-hashed at startup, so at most 72 bytes (BCrypt's limit; longer stops startup). Missing, blank, or an unset `${...}` variable stops startup. Under `prod`, under 12 characters or a common password also stops startup (section 8). Never logged or printed |
| `jpa.admin.title` | `JPA Admin` | Header and login title |
| `jpa.admin.primary-color` | `#0d9f6e` | Validated as `#rrggbb` only. A bad value stops startup |
| `jpa.admin.page-size` | `25` | Rows per page in every list when the address says no size. One of `10`, `25`, `50`, `100` (the sizes the page-size links offer); any other number stops startup with a message that names the property, lists the allowed values and does not repeat the value |
| `jpa.admin.export-enabled` | `true` | Global CSV export switch. When false no list has the button and every export address of an entity in the console answers 403 |
| `jpa.admin.export-max-rows` | `50000` | May lower the cap, never raise it: 50,000 is a fixed internal ceiling. A value below 1 or above 50000 stops startup with a message that names the property and does not repeat the value |
| `jpa.admin.export-bom` | `false` | Start every CSV file with a UTF-8 byte order mark, for an Excel that would read it in the local code page |
| `jpa.admin.sensitive-fields` | `password, secret, token, apikey` | Names always masked. Match is case-insensitive, ignores `_` and `-`, and is a substring match (`userPassword`, `api_key` match). `@AdminSensitive` adds to this |
| `jpa.admin.excluded-entities` | empty | Entity names to hide |
| `jpa.admin.storage.default.type` | `local` | `local` only in v0.1 (`s3` planned for v0.2). Any other type stops startup |
| `jpa.admin.storage.default.directory` | `./uploads` | Local storage root. Relative paths start at the working directory. Created and probed at startup when an upload field uses the storage; refused inside the web root |
| `jpa.admin.storage.default.max-size` | `5MB` | Largest file of the storage. More than 0 and at most 100MB (the global ceiling). A field may lower it, never raise it |
| `jpa.admin.storage.default.public-path` | none | If set, files are also served without a session at this path (`/files`). Off by default. Not the console path, not inside it, not containing it |
| `jpa.admin.storage.<name>.*` | none | Another storage, with the same four properties. `<name>` is a lower-case word; a field names it with `@AdminUpload(storage = "name")`. The storage `default` exists without any property |
| `jpa.admin.upload.signing-key` | random per start | Signs upload tickets (see Uploads). At least 32 characters. Unset: a random key per start and a startup WARN that restarts invalidate open forms. Set explicitly when running several instances |

Startup fails fast, with one list of the offending fields and the reason, when any `@AdminUpload` field refers to a storage that is not configured, a storage has a type other than `local`, or the folder of a storage that a field uses cannot be created or written to. The defaults alone pass. The host's multipart settings (`spring.servlet.multipart.*`, 1MB per file and 10MB per request in Boot 4.1) are the host's: the console never changes them and its limits apply inside them.

## Details and examples

Only `jpa.admin.username` and `jpa.admin.password` are required. An environment variable that is not set counts as missing: `password: ${ADMIN_PASSWORD}` with no such variable stops startup. `jpa.admin.path` must start with `/` and have no trailing slash, and `jpa.admin.primary-color` must be written `#rrggbb`. When several properties are wrong, one startup report lists every one of them and names each property.

**`jpa.admin.excluded-entities`** (list, empty by default). Entity class names the console must never show. An entry matches by simple class name or by fully qualified class name, ignoring case (`AuditEntry`, `com.example.AuditEntry`, `auditentry` all match the same class). A hidden entity is not in the sidebar, not on the dashboard, and its address returns the same 404 as an address that was never an entity. Example: `jpa.admin.excluded-entities[0]=AuditEntry`.

**`jpa.admin.sensitive-fields`** (list, default `password, secret, token, apikey`). Field names whose values are always masked. The match ignores case, `_` and `-`, and is a substring match, so `password` also covers `userPassword` and `api_key` matches `apikey`. Because it is a substring match it masks a little too much on purpose: a field called `tokenizer` contains `token` and is masked. Narrow the list if that is not wanted. `@AdminSensitive` adds to this list and is never removed from it. Example: `jpa.admin.sensitive-fields[0]=password`.

**`jpa.admin.page-size`**. How many rows a list shows when its address says no size: one of `10`, `25`, `50` or `100`, the sizes the page-size links of every list offer. Any other number stops startup with a message that names the property, lists the four sizes, says to leave the property out for 25 and does not repeat the value you wrote; a value that is not a number fails binding and names the property too. It applies to every list, to a size in an address that is not on offer (that falls back to this default, not to 25) and to the saved views script. The links of a list leave `size` out exactly when it equals this default, so with `jpa.admin.page-size=50` the address of a list is the plain one for 50 rows and says `size=25` for 25. The CSV export is not paged and ignores it. A page size per entity is not offered.

```yaml
jpa:
  admin:
    page-size: 50
```

**Export properties**. All optional. `jpa.admin.export-enabled` (default `true`): `false` removes the export button and makes every export address answer 403. `jpa.admin.export-max-rows` (default `50000`): the most rows in one CSV file; it can only lower the cap, because 50,000 is a fixed ceiling, and a value below 1 or above 50000 stops startup with a message that names the property and does not repeat the value. `jpa.admin.export-bom` (default `false`): start each file with a UTF-8 byte order mark, for an Excel that reads it in the wrong character set. See "CSV export" under "Using the console".

**Upload properties**. All optional; the storage `default` exists without any of them.

| Property | Default | Meaning |
|---|---|---|
| `jpa.admin.storage.<name>.type` | `local` | The kind of storage. Only `local` (a folder on this server's disk) exists in v0.1; any other type stops startup. S3 is planned for v0.2 |
| `jpa.admin.storage.<name>.directory` | `./uploads` | The folder. A relative path starts at the working directory. Created, and checked for write access, at startup when an upload field uses the storage. Must be outside the web root (not under `src/main/resources`, `src/main/webapp`, `target/classes` or the servlet context's folder), or startup stops |
| `jpa.admin.storage.<name>.max-size` | `5MB` | The largest file of the storage: more than 0 and at most 100MB. A field's `maxSize` may lower it, never raise it |
| `jpa.admin.storage.<name>.public-path` | none | A path such as `/files`. When set, the storage's files are also served to everyone, without a session, at `{public-path}/{key}`, and a file's stored address is the public one. Off by default. Not the console path, not inside it, not containing it, not shared with another storage |
| `jpa.admin.upload.signing-key` | random per start | Signs the upload tickets. At least 32 characters; an unset `${...}` variable stops startup. Unset: a random key is made at every start and a WARN says that a restart invalidates the forms that are open. Set it when you run more than one instance |

`<name>` is a lower-case word of letters, digits and `-` (at most 32 characters). A field uses a storage other than `default` with `@AdminUpload(storage = "name")`. Every wrong value stops startup with one report that names the property and never prints the value. Example:

```yaml
jpa:
  admin:
    storage:
      default:
        directory: /var/lib/shop/uploads
        max-size: 8MB
    upload:
      signing-key: ${UPLOAD_SIGNING_KEY}      # 32 characters or more
spring:
  servlet:
    multipart:
      max-file-size: 8MB                      # the host's own limits, see "File uploads"
      max-request-size: 9MB
```
