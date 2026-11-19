# Phase 13 scope: polish (written at the end of Phase 12, nothing implemented)

Source: `docs/PLAN.md` (Phase 13 row, section 10 "UI", section 5 properties), `docs/UI_PLAN.md` (U9, section 10 checklist), `docs/DECISIONS.md` (D-57, D-60, D-61, D-115, D-117), `docs/logs/phase-12-report.md`, `docs/prototype/admin-console.html`. This file lists what the phase has to deliver and the questions to settle first. It is a summary, not a plan; the plan is written at the start of the phase and approved.

## What the phase delivers

1. **Annotations the console stood in for** (PLAN Phase 13 row, D-60): `@AdminResource` gets the options that decide the plural and the page size of an entity (today `Plurals` guesses the plural, D-57), `@AdminBadge` chooses the tone of an enum value (today a word list stands in, D-55), `@AdminLookup` chooses the label property of a relation (today the first of `name`, `title`, `label` ... is used, D-54). Each is a public API addition and needs an approved name and default.
2. **`jpa.admin.page-size`** (moved here from Phase 5, D-60): default 25, one of 10, 25, 50, 100; a bad value stops startup with a message that names the property (plain validation code, like the other properties).
3. **Accent colour** is already a validated, generated stylesheet (D-36); Phase 13 only checks it against the prototype and the contrast rules in both themes.
4. **Styling from the prototype** (`docs/prototype/admin-console.html`): the alignment pass the maintainer announced in D-38, grouped sidebar, breadcrumbs, stat cards, badges, floating bulk bar, detail grid, form grid, toasts, empty states.
5. **Shortcuts:** `/` focuses the search box on a list (not while typing in a field), and the sticky table header on long tables (U9).
6. **The manual passes** that Phases 4 to 12 listed and no session could do by hand: every page in light and dark, at phone width and desktop, with keyboard only, with a screen reader, with script off, and the saved views in a real private window. Phase 12 did them in headless Chrome only.
7. **README with screenshots** and a quick start that works in five minutes (the "Docs screenshots" row of `docs/FIXTURES.md`).

## Questions to settle before coding (each with a recommendation)

| Question | Options | Recommendation |
|---|---|---|
| The saved views script reads the validated state from the `href` of the page-size option marked `current` (D-115) | keep; add a field to `ListView` (a contract change, `D-65` style) | Add one field to `ListView`, for example `stateHref` (the list address with `q`, `filter.*`, `hide`, `sort`, `dir`, `size` and no page), checked by `Hrefs`, filled by the factory. It removes the one place where the script depends on the shape of another record. Ask first, as D-65 did |
| A "Show all columns" link, and "Clear search" that keeps the hidden columns | add two links to the record; leave as they are | Add them in the same contract change as the field above: `Columns` with `showAllHref`, and make `clearAllHref` and the "Clear search" links keep `hide`. Today both of those links return to the plain address and show every column (D-117) |
| `@AdminResource` option names for plural and page size | `plural`, `pageSize`; `labelPlural`, `defaultPageSize` | `plural` and `pageSize`: shortest, and `pageSize` mirrors the property. The annotation Javadoc and ARCH-9 note are updated |
| `@AdminBadge` shape | on an enum constant or on the field; a tone per constant | On the enum (a constant names its tone); tones are the existing six (`GREEN`, `AMBER`, `RED`, `BLUE`, `GREY`, `NONE`); the word list stays as the default |
| `@AdminLookup` | on the relation field with the label attribute name | On the relation field, one attribute name; the metadata checks that it is a visible, non-sensitive text attribute of the target, else startup logs it and the console falls back to the rule of D-54 |
| The manual passes | a checklist the maintainer runs; an automated browser test | Both: the Phase 7 `HeadlessChrome` driver already runs in the build and covers layout; the manual pass covers what it cannot judge (reading order, screen reader, a real private window, print) |
| Where the saved-views panel goes on a phone | as now (a closed button in the row of panels); in the list header | As now. The pass will say if it is hard to reach |

## Carried facts from Phase 12 that matter here

- The `hide` parameter, `ListQuery.hidden`, `HiddenColumns` and the panels are done and need no change except the record proposal above (D-112 to D-117).
- `views.js` stores `{ name, q, filters, sort, dir, pageSize, hiddenColumns }` under `jpa-admin.views.<console path>.<entity>`; changing the key or the shape would orphan every stored view, so a later change has to read the old shape too.
- The UI-12 checker now understands SpEL selection and projection (D-117); a template may use `.?[ ]` and `.![ ]` on the model's own collections.
- `SavedViewsBrowserTest` and `ListLayoutBrowserTest` run only where Chrome or Edge exists and are skipped, with the reason, otherwise. A CI that has no browser skips them.
- Not in scope for Phase 13: server-side views, shared views between administrators, per-user views (all need multiple admins, v0.4), XLSX export, Testcontainers and the release work (Phase 14).
