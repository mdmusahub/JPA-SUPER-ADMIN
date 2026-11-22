# Customizing entities

The console works without any annotation: it reads your entities from the JPA metamodel and shows them as they are. Annotations are optional and only ever **narrow or relabel** what the console does; they never open up anything that the console would otherwise hide. They live in the package `io.github.mdmusahub.jpaadmin.annotation`. The options and defaults of each are in the [annotations reference](annotations.md).

A Person entity that uses most of them:

```java
@Entity
@AdminResource(plural = "People", deletable = false)   // headings say "People"; records cannot be deleted
public class Person {

    @Id
    @GeneratedValue
    private Long id;

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

## Hide something completely

`@AdminHidden` on an **entity** removes it from the console: no sidebar entry, no dashboard card, and its address answers the same 404 as an address that was never an entity. On a **field** it is never shown, searched, sorted, filtered or exported. You can hide an entity without touching its class with the property `jpa.admin.excluded-entities`.

```java
@Entity
@AdminHidden
public class AuditEntry { ... }
```

## Mask a field

A field whose name contains `password`, `secret`, `token` or `apikey` is masked automatically (the list is `jpa.admin.sensitive-fields`; the match ignores case, `_` and `-`). `@AdminSensitive` masks any other field, whatever it is called. A masked value is shown as dots in lists and on detail pages, is not in the CSV export, is never written to a log, and on an edit form a blank box keeps the stored value.

## Make an entity read-only, or forbid one action

```java
@AdminResource(readOnly = true)                        // only shown: no create, edit or delete
@AdminResource(deletable = false)                      // everything but delete
@AdminResource(creatable = false, exportable = false)  // no create or duplicate, no CSV export
```

These are checked on the server, not only hidden in the page: a forged request is refused with 403.

## Name things

`@AdminResource(plural = "People")` sets the plural used in headings, the sidebar, the search box and the dashboard. Without it the console guesses with plain English rules (`Category` becomes `Categories`, `Person` would wrongly become `Persons`).

## Colour a status

`@AdminBadge` on an enum or a `String` field chooses the colour of the badge that shows the value. The tones are **`green`, `amber`, `red` and `grey`** and nothing else (never a colour code), so the badges follow the light and dark theme. A value you do not list is grey.

## Choose how a related record is shown

A relation to another entity is shown as the other record's label. By default that is its first text property named `name`, `title`, `label`, `fullName`, `displayName`, `username`, `email` or `code`, else its first text property, else its number (`#12`). `@AdminLookup("serial")` names the property you want instead.

## Let people upload a file

`@AdminUpload` on a `String` field makes it a file field: the field holds the address of a stored file. See [File uploads](file-uploads.md) for the storage, the checks and the limits.
