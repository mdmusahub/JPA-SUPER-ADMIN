package io.github.mdmusahub.jpaadmin.preview;

import io.github.mdmusahub.jpaadmin.service.FieldMessages;
import io.github.mdmusahub.jpaadmin.service.FormPage;
import io.github.mdmusahub.jpaadmin.web.FormViewFactory;
import io.github.mdmusahub.jpaadmin.web.SaveMessages;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.FormView.Field.InputType;
import io.github.mdmusahub.jpaadmin.web.view.FormView.Option;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The fixtures of task U5 ({@code docs/FIXTURES.md} section 7): the create and edit form in every state. Every one is built
 * by the console's real {@link FormViewFactory}, so the addresses of the form (the post address, the version in it, Cancel) and
 * the breadcrumbs are the real ones, and the sentences next to the fields and above the form are the real
 * {@link FieldMessages}. The data is invented (articles, members) and holds no real value; a masked field has no value even
 * here (SEC-1).
 */
final class FormFixtures {

    private static final String ARTICLE = "article";
    private static final String LABEL = "Article";
    private static final String PLURAL = "Articles";

    /** A fixture: the page the form shows, what is wrong with it, and the notices above it. */
    private record Fixture(FormPage page, List<String> formErrors, List<FlashMessage> notices, String entityLabel) {
    }

    private static Fixture of(FormPage page, List<String> formErrors, List<FlashMessage> notices) {
        return new Fixture(page, formErrors, notices, LABEL);
    }

    private static final Map<String, Function<String, Fixture>> FORMS = new LinkedHashMap<>();

    static {
        FORMS.put("create-empty", base -> of(page(null, null, null, standard(Map.of(), false)), List.of(), List.of()));
        FORMS.put("edit-prefilled", base -> of(page("12", "3", "Guide on onboarding 12", standard(values(), true)),
                List.of(), List.of()));
        FORMS.put("long-identity", base -> new Fixture(page("123e4567-e89b-12d3-a456-426614174000", "0",
                "A long record title ".repeat(6),
                standard(values(), true)), List.of(), List.of(), "Entity label ".repeat(4).substring(0, 40)));
        FORMS.put("validation-errors", base -> of(validationErrors(), List.of(FieldMessages.summary(5)), List.of()));
        FORMS.put("form-errors", base -> of(page("12", "3", "Guide on onboarding 12", standard(values(), true)),
                List.of(FieldMessages.stale()), List.of()));
        FORMS.put("select-options", base -> of(page(null, null, null, selects()), List.of(), List.of()));
        FORMS.put("masked-input", base -> of(page("12", "3", "Guide on onboarding 12", List.of(
                field("title", "Title", InputType.TEXT, "Guide on onboarding 12", true, null, List.of(), null),
                masked("accessKey", "Access key", false, "Leave blank to keep the current value."),
                masked("recoveryPhrase", "Recovery phrase", false, "Leave blank to keep the current value."))),
                List.of(), List.of()));
        FORMS.put("masked-input-create", base -> of(page(null, null, null, List.of(
                field("title", "Title", InputType.TEXT, null, true, null, List.of(), null),
                masked("accessKey", "Access key", true, null),
                masked("recoveryPhrase", "Recovery phrase", false, null))), List.of(), List.of()));
        FORMS.put("long-form", base -> of(page(null, null, null, longForm()), List.of(), List.of()));
        FORMS.put("duplicate", base -> of(page(null, null, null, standard(values(), false)), List.of(),
                List.of(SaveMessages.copied(LABEL, "12"))));
        FORMS.put("relation-notice", base -> of(page(null, null, null, notices()), List.of(), List.of()));
        FORMS.put("escaped", base -> of(escaped(), List.of(), List.of()));
    }

    private FormFixtures() {
    }

    static Set<String> states() {
        return FORMS.keySet();
    }

    static Optional<FormView> form(String state, String basePath) {
        Function<String, Fixture> fixture = FORMS.get(state);
        if (fixture == null) {
            return Optional.empty();
        }
        Fixture built = fixture.apply(basePath);
        return Optional.of(FormViewFactory.build(basePath, ARTICLE, built.entityLabel(), built.page(), built.formErrors()));
    }

    /** The chrome of a form page: the breadcrumbs lead back to the list and the record, and the notices are the real ones. */
    static Optional<PageChrome> chrome(String state, String basePath) {
        Function<String, Fixture> fixture = FORMS.get(state);
        if (fixture == null) {
            return Optional.empty();
        }
        Fixture built = fixture.apply(basePath);
        return Optional.of(ChromeFixtures.custom(basePath, ARTICLE,
                FormViewFactory.breadcrumbs(basePath, ARTICLE, PLURAL, built.entityLabel(), built.page()), built.notices()));
    }

    // ---- building blocks --------------------------------------------------------------------------------------------------

    private static FormPage page(String id, String version, String label, List<FormView.Field> fields) {
        return new FormPage(id, version, label, fields);
    }

    private static FormView.Field field(String key, String label, InputType type, String value, boolean required, String help,
                                        List<Option> options, String error) {
        return new FormView.Field(key, label, type, value, required, help, options, error, null);
    }

    private static FormView.Field masked(String key, String label, boolean required, String help) {
        return new FormView.Field(key, label, InputType.MASKED, null, required, help, List.of(), null, null);
    }

    private static List<Option> statuses(boolean required) {
        return List.of(new Option("", required ? "Choose..." : "None"), new Option("DRAFT", "Draft"),
                new Option("IN_REVIEW", "In review"), new Option("PUBLISHED", "Published"), new Option("ARCHIVED", "Archived"));
    }

    private static List<Option> members(int count, boolean required) {
        List<Option> options = new ArrayList<>();
        options.add(new Option("", required ? "Choose..." : "None"));
        for (int i = 1; i <= count; i++) {
            options.add(new Option(String.valueOf(i), "Member " + i));
        }
        return options;
    }

    private static Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("title", "Guide on onboarding 12");
        values.put("body", "First line.\nSecond line, with <b>markup</b> shown as text.");
        values.put("views", "1240");
        values.put("rating", "4.5");
        values.put("featured", "true");
        values.put("publishedOn", "2026-10-15");
        values.put("updatedAt", "2026-11-17T10:30:15");
        values.put("status", "PUBLISHED");
        values.put("author", "7");
        return values;
    }

    /** One field per input type except upload: the create form and the edit form of an article. */
    private static List<FormView.Field> standard(Map<String, String> v, boolean editing) {
        return List.of(
                field("title", "Title", InputType.TEXT, v.get("title"), true, "Shown in the list and on the page.", List.of(), null),
                field("body", "Body", InputType.TEXTAREA, v.get("body"), false, null, List.of(), null),
                field("views", "Views", InputType.NUMBER, v.get("views"), true, null, List.of(), null),
                field("rating", "Rating", InputType.NUMBER, v.get("rating"), false, "A number with one decimal point.", List.of(), null),
                field("featured", "Featured", InputType.BOOLEAN, v.get("featured"), false, null, List.of(), null),
                field("publishedOn", "Published on", InputType.DATE, v.get("publishedOn"), false, null, List.of(), null),
                field("updatedAt", "Updated at", InputType.DATETIME, v.get("updatedAt"), false, "Time in UTC.", List.of(), null),
                field("status", "Status", InputType.SELECT, v.get("status"), true, null, statuses(true), null),
                field("author", "Author", InputType.SELECT, v.get("author"), true, null, members(12, true), null),
                masked("accessKey", "Access key", !editing, editing ? "Leave blank to keep the current value." : null));
    }

    private static FormPage validationErrors() {
        Map<String, String> typed = new LinkedHashMap<>(values());
        typed.put("title", "");
        typed.put("views", "twelve");
        typed.put("publishedOn", "2026-02-30");
        typed.put("status", "SHIPPED");
        typed.put("author", "9999");
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("title", FieldMessages.required());
        errors.put("views", FieldMessages.wholeNumber());
        errors.put("publishedOn", FieldMessages.date());
        errors.put("status", FieldMessages.chooseListed());
        errors.put("author", FieldMessages.chooseExisting());
        return page(null, null, null, standard(Map.of(), false)).reshown(typed::get, errors);
    }

    private static List<FormView.Field> selects() {
        List<Option> authors = members(50, false);
        return List.of(
                field("status", "Status (4 choices and a blank)", InputType.SELECT, "IN_REVIEW", false, null, statuses(false), null),
                field("statusRequired", "Status, required", InputType.SELECT, null, true, null, statuses(true), null),
                field("author", "Author (a relation with 50 choices)", InputType.SELECT, "7", false, null, authors, null),
                field("reviewer", "Reviewer, none chosen", InputType.SELECT, null, false, null, authors, null));
    }

    private static List<FormView.Field> notices() {
        return List.of(
                field("title", "Title", InputType.TEXT, null, true, null, List.of(), null),
                field("author", "Author", InputType.SELECT, null, false, FieldMessages.tooManyChoicesNotice(200), List.of(), null),
                field("publisher", "Publisher", InputType.SELECT, null, true, FieldMessages.noChoicesNotice(), List.of(), null));
    }

    private static List<FormView.Field> longForm() {
        List<FormView.Field> fields = new ArrayList<>();
        String longLabel = "A rather long label that keeps going to check how the form grid wraps it on a phone ";
        String longHelp = "Help text that is long on purpose, so that it wraps over several lines and the layout can be judged. "
                + "AVeryLongWordWithoutAnySpacesAtAllToSeeWhetherItBreaksInsteadOfOverflowingTheScreen" + "x".repeat(40);
        for (int i = 1; i <= 30; i++) {
            InputType type = switch (i % 6) {
                case 0 -> InputType.NUMBER;
                case 1 -> InputType.TEXT;
                case 2 -> InputType.DATE;
                case 3 -> InputType.TEXTAREA;
                case 4 -> InputType.BOOLEAN;
                default -> InputType.TEXT;
            };
            fields.add(field("field" + i, i % 3 == 0 ? longLabel + i : "Field " + i, type, null, i % 4 == 0,
                    i % 5 == 0 ? longHelp : null, List.of(), null));
        }
        return fields;
    }

    /** Values and labels that look like markup: the form prints them as text, escaped (SEC-9). */
    private static FormPage escaped() {
        return page("<b>1</b>", "0", "<script>alert(1)</script>", List.of(
                field("title", "Title <i>x</i>", InputType.TEXT, "\"onfocus=\"alert(1)\" <script>x</script>", true,
                        "Help with <b>markup</b> & an ampersand", List.of(), null),
                field("body", "Body", InputType.TEXTAREA, "</textarea><script>alert(1)</script>", false, null, List.of(), "A <b>sentence</b> with markup."),
                field("status", "Status", InputType.SELECT, "B", false, null,
                        List.of(new Option("", "None"), new Option("A", "<b>A</b>"), new Option("B", "\"B\" & 'B'")), null)));
    }
}
