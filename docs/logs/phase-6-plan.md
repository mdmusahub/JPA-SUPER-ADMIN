# Phase 6 plan: Filters

Branch `phase/6-filters`, from `main` at tag `v0.0.5`. One session on both tracks (the same recorded exception to AI-11 as
Phases 2 to 5, D-33, D-43, D-50, D-61; a new entry D-64 records it for Phase 6). Plan written first, then built in the
ordered steps below without waiting for approval (the brief says so). I stop and ask only for the five conditions in the
brief. Nothing is committed.

## What the code already has (read, not assumed)

* `ListView.Filters(definitions, chips, clearAllHref)`, `Definition(key, label, kind, options, value, from, to)`,
  `Chip(key, label, display, removeHref)` and `FormView.Option(value, label)` exist and are frozen. `ListView.Column`
  already has a `filterable` flag. `chrome.flash` (a list of `FlashMessage`, levels SUCCESS/INFO/WARNING/ERROR) is the
  existing notice area (the Phase 5 cut-search notice uses it).
* `ListViewFactory` builds an empty `Filters`. `ListLinks` builds the other list links and is the only class that writes
  a query string. `ListQuery` is the checked request (search, sort, dir, page, size). `ListQueryService` runs a count and a
  page with the Criteria API, with the search as a bound parameter. `ListColumns` decides columns, `labelAttributeOf` the
  label of a relation target.
* Test model for core: `Parcel` (text, large text, decimal, int, boolean, enum, date, date-time, four relations: `depot`
  labelled by name, `courier` labelled by call sign, `crate` with no label, `vault` with a hidden target; sensitive fields
  by name and by annotation; a hidden field). Sample app: `Product` (category), `Order` (customer, date-time), `Customer`
  (sensitive `password`).

## Can the frozen records carry the filter UI? Yes. No contract change is proposed.

* Ranges: `Definition.from`/`to` carry min/max or from/to. The control kind says which (`NUMBER_RANGE` uses
  `filter.<key>.min|max`, `DATE_RANGE` uses `filter.<key>.from|to`); the template derives the field names from key and kind.
* Options: `FormView.Option` carries enum constants, yes/no, and relation ids with labels.
* Ignored-filter messages: `chrome.flash` (as in Phase 5).
* A relation filter that cannot be offered (target over the bound) is reported in `chrome.flash` too.

## Rules (to be recorded as D-62, D-63)

URL: `filter.<key>` for text, enum, boolean and relation; `filter.<key>.min` / `.max` for numbers; `filter.<key>.from` /
`.to` for dates. A filter parameter is any parameter whose name starts with `filter.`.

A field is **filterable** only when it is visible, not sensitive and not the identifier, and is one of: String text; a
number; a boolean; an enum; a date or date-time of a supported type (`LocalDate`, `LocalDateTime`, `Instant`,
`OffsetDateTime`, `ZonedDateTime`, `java.sql.Date`, `java.util.Date`, `java.sql.Timestamp`); a to-one relation whose target
is in the console and has a label (the sort rule of D-60). Never: hidden or sensitive fields, to-many, embedded, binary,
unknown types, a relation to a hidden target.

| Filter | Rule |
|---|---|
| text | control characters become spaces, trimmed, cut to 100 characters (the cut is reported); case-insensitive contains; `lower(attr) like :p escape '!'`, `%` `_` `!` literal, bound parameter |
| enum | exactly one constant name (case-sensitive) from the enum |
| boolean | `true` or `false` (any letter case); `any` or blank = no filter |
| relation | exactly one target id taken from the dropdown's choices; any other value is "unknown target id" |
| number range | `min` and/or `max`, `-?digits[.digits]` (at most 30+30 digits), no exponent/plus/comma; compared in the field's own type; integer fields round a fractional bound inwards; a bound outside the type's range is ignored; `min > max` ignores the whole filter |
| date range | `from` and/or `to`, `yyyy-MM-dd` strict (no 30 February), years 1000 to 9998; date-times: `from` is the start of that day, `to` includes the whole day (UTC for instants, like the display); `from > to` ignores the whole filter |

Common: a blank value is "not set" and is silent (an HTML form sends blank inputs). Anything else invalid is ignored and
reported by label and reason, never by echoing what was typed (escaped text only). Repeated parameter: first value used,
reported. Unknown, hidden, sensitive, wrong-suffix names: ignored with one generic message that names nothing (so a
sensitive name is indistinguishable from an unknown one). **Cap: 10 applied filters** (a range with both bounds is one
filter); the filters beyond the 10th, taken in column order, are ignored and reported. Filters combine with AND, with the
search, the sort and paging. Links (`to`, chip remove, clear all, sort, page, size) are built by `ListLinks`; order
`q`, `filter.*` (column order), `sort`, `dir`, `page`, `size`. **Any change of the filter set starts again at page 0.**

Relation dropdown: `select id, label from Target order by label nulls last, id` limited to **201 rows** (one extra
statement per filterable relation column on each list page; no entity, no join, only the label and id attributes; the label
attribute is never sensitive and the target is never hidden). 200 is the bound. If a target has more, the filter is **not
offered** (no truncated list, no guess): the page says so in a notice and a `filter.<key>` for it is ignored. This is the
runtime reading of "do not silently truncate"; the sample app and host-check have at most 12 records per target, so the
brief's STOP condition does not occur during this phase. I will flag the runtime choice in the report for confirmation.

## Files

Backend (core): new `service/ActiveFilter`, `FilterControl`, `FilterNote`, `FilterParse`, `FilterField` (package-private),
`ListFilters` (package-private rules and parser), `FilterPredicates` (package-private predicates); changed `ListQuery`
(filters component, `withFilters`, `withoutFilter`, `withoutFilters`, cap constant), `ListPage` (controls and notes),
`ListQueryService` (options, resolve, predicates in count and page), `web/ListLinks`, `web/ListViewFactory`,
`web/EntityController`. No change to `web/view` records.

UI: `list.html` (one GET form with search and the filter panel, chips, clear all), new fragments `filter-panel.html` and
`filter-chips.html`, `pages.css` additions, new `assets/js/filters.js` (progressive enhancement: leave out empty controls so
the address stays short), `head`/`layout` script tag.

Preview: `ListFixtures` (+ `ChromeFixtures` for the ignored notice) with the fixtures of step 5; tests.

Sample app and host-check: filter tests (open-in-view on and off). Docs: PLAN, UI_PLAN, FIXTURES, PROJECT_DOCS, DECISIONS,
CHANGELOG, `docs/logs/phase-6-*.md`, `docs/logs/phase-7-scope.md`.

## Steps (each ends with `mvn -pl <module> -am clean verify`)

0. Housekeeping (v0.0.5 exists, no "not available yet" text in code, ports free), baseline counts.
1. Filter model: `ActiveFilter`, `ListFilters` (fields and parser), `FilterParse`, `FilterNote`; `ListQuery` filters and the cap.
2. Query: `FilterPredicates`, options query, relation resolve, count and page use the same predicates; statement counts.
3. Controller and view model: `ListLinks`, `ListViewFactory` (definitions, chips, clear all, empty hint, `filterable`), `EntityController`.
4. UI U3: templates, CSS, `filters.js`.
5. Preview fixtures and tests.
6. Tests (core, sample-app, host-check, preview).
7. Mutation checks (a) to (i), one at a time, MD5 restore.
8. Docs.
9. Final checks and the demo run with `curl.exe`.

Tests per rule are listed in the change log as they are written. The Phase 7 scope note is written after the code.
