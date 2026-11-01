# CLAUDE.md (UI track)

Applies when you work in `jpa-admin-ui/` or `jpa-admin-ui-preview/`. The root `CLAUDE.md`, `docs/PLAN.md`, `docs/RULES.md` and `docs/UI_PLAN.md` still apply.

## Your track

You are the **UI track**. You build Thymeleaf templates, CSS, JavaScript and the preview fixtures, so every page can be designed and checked without the real backend.

Visual reference: `docs/prototype/admin-console.html`. Design system and component list: `docs/UI_PLAN.md` section 5.

## You own

- `jpa-admin-ui/src/main/resources/templates/jpa-admin/**`
- `jpa-admin-ui/src/main/resources/jpa-admin/assets/**` (not under `META-INF/resources`, so the host app never serves it at a second URL)
- `jpa-admin-ui-preview/**` (preview controller and fixtures)

## You must not edit

- Any Java under `jpa-admin-core`, `jpa-admin-autoconfigure`, `jpa-admin-starter` or `sample-app`. If a page needs data that does not exist, propose a contract change instead.
- The view-model records in `jpa-admin-core/.../web/view/**`, unless the change is approved and logged in `docs/DECISIONS.md` (contract change rule, `docs/UI_PLAN.md` section 8).

## Rules for this track

- Templates read only the view-model records listed in `docs/UI_PLAN.md` section 3. Never call a service, a repository or anything outside the model.
- Escape all values. Never use `th:utext` on stored data (SEC-9). Never place a sensitive value in HTML, attributes or JavaScript data (SEC-1).
- No CDN, no remote fonts, no remote images, no build step, no npm (UI-2, CODE-6). Plain JavaScript, one small file per feature.
- Links and forms must work first. JavaScript only enhances them.
- Table state (search, filters, sort, page) lives in the URL (UI-10). `localStorage` use is wrapped in try/catch and optional (UI-11).
- Light and dark themes via CSS variables, theme applied before first paint by a blocking `theme.js` in `<head>` (no inline scripts, handlers or styles, UI-13), reduced motion respected, visible focus, labelled icon buttons (UI-3, UI-4, UI-8).
- Every page gets preview fixtures: normal, empty, long text, error, and the states listed in `docs/UI_PLAN.md` section 6.
- Copy follows the style in `docs/UI_PLAN.md` section 5.

## Build and verify (only your modules)

```
mvn -pl jpa-admin-ui-preview -am spring-boot:run     # then open http://localhost:8080/admin-preview/list?state=normal
mvn -pl jpa-admin-ui-preview -am clean verify
```

Check each page in light and dark, at phone width and desktop, with keyboard only. Run the checklist in `docs/UI_PLAN.md` section 10. Run the full `mvn clean verify` only when the backend session is idle.

## Commit

Commit by path: `git add jpa-admin-ui jpa-admin-ui-preview`. Never `git add .` while the backend session has changes.

## Starting a task

State "UI track" in your first line, restate the U-task from `docs/UI_PLAN.md` section 7, propose a plan (templates, assets, fixtures, states), and wait for approval.
