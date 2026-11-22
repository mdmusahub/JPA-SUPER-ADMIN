# Phase 14a: two decisions for you (options only, nothing implemented)

Both items are listed in the Phase 14a brief as "do not implement, write the options". Nothing in the product changed for them. Each has a recommendation and what I need from you.

---

## Item 7. A relation to a table with more than 200 records

### What happens today (Phase 8, D-81; Phase 6, D-63)

- **A form** shows a to-one relation as a `select` of the target's records, labelled and ordered by label, **at most 200**. A target with more records is a **notice** instead of a select ("it keeps its current value"). A save that would change that field is refused for that field only, with a message; the stored value is kept otherwise.
- **What that means in practice:** a relation that is **required** (`optional = false`) to a target with more than 200 records **cannot be set when creating a record**, so **such a record cannot be created in the console at all**. An optional one is created with no value, and an existing value cannot be changed.
- **A list filter** on such a relation is not offered ("The ... filter is not available: it would have more than 200 choices"); the relation column still shows and sorts by the target's label.
- Cost today, measured in this phase (`phase-14a-performance.md`): the three small filter-choice statements of a list page cost about 3 to 6 ms of the 20 ms of a first page on PostgreSQL.

### The options

| | A. Searchable picker | B. Id box | C. Lazy list |
|---|---|---|---|
| **What the visitor sees** | A text box that suggests matching records as they type (by label); the chosen record is shown by its label | A plain box where the visitor types the target's id; after saving, the label is shown | The same `select`, but its options are fetched 200 at a time (when it gets focus, and a "Load more" at the end) |
| **New route** | Yes: a read-only `GET {path}/{entity}/relation-options?field=&q=&page=`, session required, answers at most 20 labels and ids | **No** | Yes: the same route, paged, without `q` |
| **New script** | Yes: one plain file, about 150 lines, a combobox (the hardest widget to make accessible: roles, `aria-activedescendant`, keyboard, screen reader) | **No** | Yes: about 60 lines |
| **Works with script off** | **No**, so it needs option B as its fallback | **Yes** | Partly: the first 200 only, which is today |
| **Contract change (frozen records)** | **Yes**: `FormView.Field` needs the picker address and a new input kind | **Probably**: the field needs a plain text input kind for a relation (today a relation is a `select`) | **Yes** (a paging address) |
| **Server rules to add** | The route accepts only a field of the entity from the metadata, a visible, non-sensitive target in the console, a bound `like` parameter, a hard limit, labels only (SEC-1, SEC-2, SEC-3, SEC-11); the write path then accepts any id that exists, not only a listed one | The write path accepts an id and checks it with one `find` (exists, target in the console, not hidden); everything else is as today | As A for the route; the write path as A |
| **Query cost on the target** | `lower(label) like '%q%'`: a scan of the target, 25 to 30 ms at 200,000 rows on PostgreSQL (measured), more at a million; an index (`pg_trgm`) makes it milliseconds | One lookup by primary key | One page of 200 by label: milliseconds, grows with the offset |
| **Largest target it is good for** | Any size | Any size, if the visitor knows the id | A few thousand records; beyond that nobody scrolls |
| **Tests to add** | About 40: the route (every refusal), the write path, a real-browser test of the keyboard and the listbox, the no-script path, preview fixtures | About 15: the write path, a missing, hidden, sensitive or foreign id, the message, the form with an id left blank | About 25 |
| **Rough size** | The largest: backend, UI, accessibility pass, manual screen reader check | **The smallest** | Medium |
| **Main risk** | A new, security-relevant read route and an accessible combobox | A visitor who does not know the id is stuck (a link to the target's list would help) | Looks like a fix and is not one for large tables |

### Recommendation

**B for v0.1, A for v0.2.** B removes the "cannot create such a record" defect with one small change and no new route or script, and it works with script off like every other form control. A is the right long-term control and deserves its own phase (the accessibility work is the cost, not the query). C is not recommended.

### What I need from you

1. Which option, or none (keep the limit as documented in `docs/PROJECT_DOCS.md`).
2. If B or A: **approval of the `FormView` contract change** (a new input kind for a relation), recorded in `docs/DECISIONS.md` as the contract-change rule requires.
3. Whether the same applies to the list filter on such a relation (A would serve both; B would not).

---

## Item 8. The border of an input is below 3 to 1

### What was measured (a real Chrome 157 on this machine, headless, the packaged console, the default accent)

The colour the browser really draws for the border of each control, against the colour it really draws behind it and in the control (Phase 13 measured the first two rows from the tokens; this run measured the rest in the browser, on `/product/new`, `/product`, the dashboard and `/category/1/edit`, in both themes, with every panel open):

| Control (class) | Where | Border | Light theme | Dark theme |
|---|---|---|---:|---:|
| Text, number, date inputs, selects (`.input`) | every field of every create, edit and duplicate form; the 8 inputs of the filter panel; the login form; the uploader; the views panel | `--line-strong` | **1.52** (against the white panel) | **1.67** (against the dark panel) |
| Sidebar search (`.nav-search`) | every page | `--line-strong` | 1.52 | 1.67 |
| List search box (`.search-field`) | every list | `--line-strong` | **1.41** against the page, 1.52 against its own fill | 1.81 against the page, 1.67 against its fill |
| Range switch (`.range-switch`) | the dashboard chart | `--line-strong` | 1.52 against the panel, **1.45** against its own fill | 1.67, **1.56** |
| Upload drop zone (`.upload-drop`, dashed) | the upload field of a form | `--line-strong` | 1.52 against the panel, **1.45** against its fill | 1.67, **1.56** |

WCAG 2.2 success criterion 1.4.11 (non-text contrast) asks **3 to 1** for what identifies a control. Every field has a visible label, so a control is still identifiable by its label, which is why D-124 left it. The same token is also used by the filter chips and the `.btn` base rule; the buttons and chips were not measured here because their first match in the browser had a transparent border (a quiet variant), so I do not state a number for them.

The tokens (`tokens.css`): light `--line-strong: #cdd2d9` on `--panel: #fff` and `--bg: #f5f6f8`; dark `--line-strong: #38404c` on `--panel: #161a21` and `--bg: #0e1116`.

### The options

| | What changes | Contrast it reaches | Effect on forms and pages |
|---|---|---|---|
| **1. Leave it** | Nothing | 1.52 and 1.67 | None. The limit stays documented (D-124, the accessibility statement) |
| **2. A new token for control borders** (`--control-line`), used by `.input`, `.nav-search`, `.search-field`, `.range-switch`, `.upload-drop` and bordered buttons; `--line-strong` stays for decoration (table rules, cards, the bulk bar) | Light `color-mix(in srgb, var(--line-strong), #000 32%)` = **#8b8f94**; dark `color-mix(in srgb, var(--line-strong), #fff 23%)` = **#666c75** | Light **3.25** on the panel, **3.01** on the page; dark **3.30** on the panel, **3.57** on the page | **Every field of every form gets a visibly darker outline** (the biggest visual change of any option), the same on the list's search and filter boxes, the sidebar search and the login page. The hover border (`--muted`, #5a6472 in the light theme) stays darker than the new base border (#8b8f94), so hovering is still visible, but the step is smaller (contrast 3.25 to 6.00 instead of 1.52 to 6.00). The preview fixtures and the 52 layout measurements are unaffected (colour only); a new browser assertion for 1.4.11 would be added with the change |
| **3. Reuse `--muted` as the border** | `.input { border-color: var(--muted) }` and friends | Light 6.00 on the panel, 5.55 on the page; dark 6.92, 7.50 | Passes with room to spare, but the look is **heavy**: dark grey outlines on every field. The hover state (today `--muted`) would need a new colour, or it disappears |
| **4. A thicker border** (2px) | `border-width: 2px` | **No change in contrast** | Not a fix for 1.4.11 (the criterion is about the colour ratio); only useful together with 2. Shifts every control by 1px unless the padding is adjusted |
| **5. A fill instead of a line** (`--panel-alt` background) | Background only | 1.05 (light) and 1.04 (dark) against the panel | Does not reach 3 to 1; not an option on its own |
| **6. A bottom line only** (an underlined field) | `border: 0; border-bottom: 1px solid var(--control-line)` | As option 2 for the line that remains | Changes the whole look of the console and of the prototype it follows (`docs/prototype/admin-console.html`); a design decision, not an accessibility fix |

All numbers are WCAG 2.x relative-luminance contrast ratios; options 2 and 3 were computed from the token values and the first two rows of the table were confirmed in the browser. **No colour was changed.**

### Recommendation

**Option 2**, if you want the criterion met: one new token, the smallest visible change that reaches 3 to 1 with the console's own hue, nothing else touched. If you prefer the current look, **option 1** is honest as long as the limit stays in the accessibility statement. I would not do 3, 4, 5 or 6.

### What I need from you

1. Option 1 or 2 (or another).
2. If 2: approval to touch the UI track's CSS (a one-token change plus the selectors above) and to add a browser assertion for the boundary contrast of the five control kinds in both themes. It is a small change but it changes the look of every form, so it was not made here.
3. Whether the placeholder and the dashed upload zone count as "control boundaries" for you (the table treats them as such).
