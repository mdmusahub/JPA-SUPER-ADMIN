package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.web.view.ListView.Filters.Definition.Kind;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rules for filters, from the metadata alone (it never sees a row): which fields may be filtered, and how the
 * {@code filter.*} parameters of a request are read. Everything in the address is untrusted (SEC-2): a parameter is
 * accepted only when its name is a filterable field of this entity, and its value is parsed and written back in a
 * canonical form ({@link ActiveFilter}), so no raw text from the address reaches a query. It never fails; whatever is not
 * acceptable is ignored and reported in a {@link FilterNote}.
 *
 * <p><b>Which fields.</b> A field is filterable only when it is visible, not sensitive and not the identifier, and is one
 * of: a {@code String}; a number; a boolean; an enum; a date or date-time of a supported type; a to-one relation whose
 * target is in the console and has a label (the sort rule, D-60). Hidden fields are not in
 * {@link AdminEntity#visibleFields()} at all. A to-many relation, an embedded value, binary data and a type with no
 * filter are never filterable.
 *
 * <p><b>Parameters.</b> {@code filter.<key>} for text, enum, boolean and relation filters; {@code filter.<key>.min} and
 * {@code .max} for numbers; {@code filter.<key>.from} and {@code .to} for dates. A blank value means "not set" and is
 * silent, because an HTML form sends blank inputs. A parameter that names nothing filterable (unknown, hidden, sensitive,
 * wrong suffix) is only counted, in one message that names nothing. At most {@value ListQuery#MAX_FILTERS} filters are
 * applied, in column order; a range with both bounds is one filter.
 *
 * <p>Package-private: it is a rule, not API.
 */
final class ListFilters {

    /** The prefix of every filter parameter. */
    static final String PREFIX = "filter.";

    static final String MIN = "min";
    static final String MAX = "max";
    static final String FROM = "from";
    static final String TO = "to";

    /** First and last year accepted in a date filter: the days around them must exist in every database. */
    static final int FIRST_YEAR = 1000;
    static final int LAST_YEAR = 9998;

    private static final int MAX_RAW_LENGTH = 64;
    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,30}(\\.\\d{1,30})?");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private static final Set<Class<?>> INTEGRAL = Set.of(Integer.class, int.class, Long.class, long.class, Short.class,
            short.class, Byte.class, byte.class, BigInteger.class);
    private static final Set<Class<?>> NUMBERS = Set.of(Integer.class, int.class, Long.class, long.class, Short.class,
            short.class, Byte.class, byte.class, BigInteger.class, BigDecimal.class, Double.class, double.class,
            Float.class, float.class);
    private static final Set<Class<?>> DATES = Set.of(LocalDate.class, java.sql.Date.class, java.time.LocalDateTime.class,
            java.time.Instant.class, java.time.OffsetDateTime.class, java.time.ZonedDateTime.class,
            java.util.Date.class, java.sql.Timestamp.class);

    private ListFilters() {
    }

    // ---- which fields ---------------------------------------------------------------------------------------

    /**
     * The fields of {@code entity} that may be filtered, in column order.
     *
     * @param registry the entities the console manages; a relation is filterable only if its target is in it and has
     *                 a label
     */
    static List<FilterField> fields(AdminEntity entity, AdminEntityRegistry registry) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(registry, "registry");
        List<FilterField> fields = new ArrayList<>();
        for (AdminField field : entity.visibleFields()) {
            if (field.id() || field.sensitive()) {
                continue;
            }
            Kind kind = kindOf(field, registry);
            if (kind != null) {
                fields.add(new FilterField(field.name(), field.label(), kind, field.javaType(), field.enumValues(),
                        field.targetEntity()));
            }
        }
        return List.copyOf(fields);
    }

    private static Kind kindOf(AdminField field, AdminEntityRegistry registry) {
        return switch (field.kind()) {
            case TEXT -> field.javaType() == String.class ? Kind.TEXT : null;
            case NUMBER -> NUMBERS.contains(field.javaType()) ? Kind.NUMBER_RANGE : null;
            case BOOLEAN -> Kind.BOOLEAN;
            case ENUM -> field.enumValues().isEmpty() ? null : Kind.ENUM;
            case DATE, DATETIME -> DATES.contains(field.javaType()) ? Kind.DATE_RANGE : null;
            case TO_ONE -> ListColumns.targetHasLabel(field, registry) ? Kind.RELATION : null;
            case TO_MANY, EMBEDDED, BINARY, OTHER -> null;
        };
    }

    // ---- reading the request --------------------------------------------------------------------------------

    /**
     * Reads the {@code filter.*} parameters. Never throws.
     *
     * @param params the request's parameters (name to values); only names that start with {@value #PREFIX} are looked
     *               at, so the caller may pass them all
     */
    static FilterParse parse(AdminEntity entity, AdminEntityRegistry registry, Map<String, List<String>> params) {
        Objects.requireNonNull(params, "params");
        List<FilterField> fields = fields(entity, registry);
        Map<String, FilterField> byKey = new HashMap<>();
        for (FilterField field : fields) {
            byKey.put(field.key(), field);
        }

        Map<String, Map<String, String>> received = new HashMap<>();
        Set<String> repeated = new java.util.HashSet<>();
        int unknown = 0;
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String name = entry.getKey();
            List<String> values = entry.getValue() == null ? List.of() : entry.getValue();
            if (name == null || !name.startsWith(PREFIX) || values.stream().allMatch(v -> v == null || v.isBlank())) {
                continue; // not a filter parameter, or nothing was asked for
            }
            String rest = name.substring(PREFIX.length());
            int dot = rest.indexOf('.');
            String key = dot < 0 ? rest : rest.substring(0, dot);
            String suffix = dot < 0 ? "" : rest.substring(dot + 1);
            FilterField field = byKey.get(key);
            if (field == null || (dot >= 0 && suffix.isEmpty()) || !suffixesOf(field.kind()).contains(suffix)) {
                unknown++;
                continue;
            }
            received.computeIfAbsent(key, k -> new HashMap<>()).put(suffix, values.get(0) == null ? "" : values.get(0));
            if (values.size() > 1) {
                repeated.add(key);
            }
        }

        List<ActiveFilter> filters = new ArrayList<>();
        List<FilterNote> notes = new ArrayList<>();
        int droppedByCap = 0;
        for (FilterField field : fields) {
            Map<String, String> given = received.get(field.key());
            if (given == null) {
                continue;
            }
            List<FilterNote> local = new ArrayList<>();
            ActiveFilter filter = read(field, given, local);
            if (filter != null && repeated.contains(field.key())) {
                local.add(new FilterNote("The " + field.label() + " filter was given more than once, so only the first "
                        + "value was used.", true));
            }
            if (filter != null && filters.size() >= ListQuery.MAX_FILTERS) {
                droppedByCap++;
                continue;
            }
            notes.addAll(local);
            if (filter != null) {
                filters.add(filter);
            }
        }
        if (droppedByCap > 0) {
            notes.add(new FilterNote("Only " + ListQuery.MAX_FILTERS + " filters can be used at once, so " + droppedByCap
                    + (droppedByCap == 1 ? " more was" : " more were") + " ignored.", false));
        }
        if (unknown > 0) {
            notes.add(new FilterNote(unknown == 1
                    ? "1 filter in the address does not match any filter of this table and was ignored."
                    : unknown + " filters in the address do not match any filter of this table and were ignored.",
                    false));
        }
        return new FilterParse(filters, notes);
    }

    /** The parameter name suffixes a kind accepts: none for single values, two bounds for a range. */
    static Set<String> suffixesOf(Kind kind) {
        return switch (kind) {
            case NUMBER_RANGE -> Set.of(MIN, MAX);
            case DATE_RANGE -> Set.of(FROM, TO);
            case TEXT, ENUM, BOOLEAN, RELATION -> Set.of("");
        };
    }

    private static ActiveFilter read(FilterField field, Map<String, String> given, List<FilterNote> notes) {
        return switch (field.kind()) {
            case TEXT -> text(field, given.get(""), notes);
            case ENUM -> enumeration(field, given.get(""), notes);
            case BOOLEAN -> bool(field, given.get(""), notes);
            case RELATION -> relation(field, given.get(""), notes);
            case NUMBER_RANGE -> numbers(field, given.get(MIN), given.get(MAX), notes);
            case DATE_RANGE -> dates(field, given.get(FROM), given.get(TO), notes);
        };
    }

    // ---- one kind each --------------------------------------------------------------------------------------

    private static ActiveFilter text(FilterField field, String raw, List<FilterNote> notes) {
        if (raw == null) {
            return null;
        }
        String text = ListQuery.normalizeSearch(raw);
        if (text.isEmpty()) {
            return null;
        }
        if (ListQuery.searchWasCut(raw)) {
            notes.add(new FilterNote("The " + field.label() + " filter was cut to " + ListQuery.MAX_SEARCH_LENGTH
                    + " characters.", true));
        }
        return ActiveFilter.of(field.key(), Kind.TEXT, text);
    }

    private static ActiveFilter enumeration(FilterField field, String raw, List<FilterNote> notes) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (!field.enumValues().contains(value)) {
            notes.add(ignored(field, "that is not one of its values."));
            return null;
        }
        return ActiveFilter.of(field.key(), Kind.ENUM, value);
    }

    private static ActiveFilter bool(FilterField field, String raw, List<FilterNote> notes) {
        String value = raw == null ? "" : raw.strip().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty() || value.equals("any")) {
            return null;
        }
        if (!value.equals("true") && !value.equals("false")) {
            notes.add(ignored(field, "use true, false or any."));
            return null;
        }
        return ActiveFilter.of(field.key(), Kind.BOOLEAN, value);
    }

    /** Only the shape is checked here; whether the id exists is checked against the target's records when the page is read. */
    private static ActiveFilter relation(FilterField field, String raw, List<FilterNote> notes) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > ListQuery.MAX_SEARCH_LENGTH || value.chars().anyMatch(Character::isISOControl)) {
            notes.add(ignored(field, "that is not one of the choices."));
            return null;
        }
        return ActiveFilter.of(field.key(), Kind.RELATION, value);
    }

    private static ActiveFilter numbers(FilterField field, String rawMin, String rawMax, List<FilterNote> notes) {
        BigDecimal min = number(field, "minimum", rawMin, notes);
        BigDecimal max = number(field, "maximum", rawMax, notes);
        if (min != null && max != null && min.compareTo(max) > 0) {
            notes.add(ignored(field, "the minimum is greater than the maximum."));
            return null;
        }
        String from = effective(field, "minimum", min, RoundingMode.CEILING, notes);
        String to = effective(field, "maximum", max, RoundingMode.FLOOR, notes);
        return from == null && to == null ? null : ActiveFilter.range(field.key(), Kind.NUMBER_RANGE, from, to);
    }

    /** The bound as a number, {@code null} when it is not given (silent) or not a number (reported). */
    private static BigDecimal number(FilterField field, String which, String raw, List<FilterNote> notes) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > MAX_RAW_LENGTH || !NUMBER.matcher(value).matches()) {
            notes.add(ignoredBound(field, which, "it is not a number. Use digits, with a point for decimals."));
            return null;
        }
        return new BigDecimal(value);
    }

    /**
     * The bound as the field's own type will compare it, in plain text. A whole-number field rounds a fractional bound
     * inwards (a minimum up, a maximum down), which keeps exactly the rows the visitor asked for. A bound that the
     * field's type cannot hold is ignored, not guessed.
     */
    private static String effective(FilterField field, String which, BigDecimal bound, RoundingMode inwards,
                                    List<FilterNote> notes) {
        if (bound == null) {
            return null;
        }
        BigDecimal value = INTEGRAL.contains(field.javaType()) ? bound.setScale(0, inwards) : bound;
        if (!fits(field.javaType(), value)) {
            notes.add(ignoredBound(field, which, "it is too large or too small for this field."));
            return null;
        }
        return value.toPlainString();
    }

    private static boolean fits(Class<?> type, BigDecimal value) {
        if (type == Integer.class || type == int.class) {
            return within(value, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        if (type == Long.class || type == long.class) {
            return within(value, Long.MIN_VALUE, Long.MAX_VALUE);
        }
        if (type == Short.class || type == short.class) {
            return within(value, Short.MIN_VALUE, Short.MAX_VALUE);
        }
        if (type == Byte.class || type == byte.class) {
            return within(value, Byte.MIN_VALUE, Byte.MAX_VALUE);
        }
        return true;
    }

    private static boolean within(BigDecimal value, long low, long high) {
        return value.compareTo(BigDecimal.valueOf(low)) >= 0 && value.compareTo(BigDecimal.valueOf(high)) <= 0;
    }

    private static ActiveFilter dates(FilterField field, String rawFrom, String rawTo, List<FilterNote> notes) {
        LocalDate from = date(field, "'from' date", rawFrom, notes);
        LocalDate to = date(field, "'to' date", rawTo, notes);
        if (from != null && to != null && from.isAfter(to)) {
            notes.add(ignored(field, "the 'from' date is after the 'to' date."));
            return null;
        }
        return from == null && to == null ? null
                : ActiveFilter.range(field.key(), Kind.DATE_RANGE, from == null ? null : from.toString(),
                        to == null ? null : to.toString());
    }

    /** The day, {@code null} when it is not given (silent) or not a real day between the first and last year (reported). */
    private static LocalDate date(FilterField field, String which, String raw, List<FilterNote> notes) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        LocalDate day = null;
        if (value.length() <= MAX_RAW_LENGTH && DATE.matcher(value).matches()) {
            try {
                day = LocalDate.parse(value);
            } catch (DateTimeException notADay) {
                day = null; // for example 2026-02-30
            }
        }
        if (day == null || day.getYear() < FIRST_YEAR || day.getYear() > LAST_YEAR) {
            notes.add(ignoredBound(field, which, "it is not a valid date. Use yyyy-MM-dd, for example 2026-11-30."));
            return null;
        }
        return day;
    }

    private static FilterNote ignored(FilterField field, String reason) {
        return new FilterNote("The " + field.label() + " filter was ignored: " + reason, false);
    }

    private static FilterNote ignoredBound(FilterField field, String which, String reason) {
        return new FilterNote("The " + field.label() + " filter's " + which + " was ignored: " + reason, false);
    }
}
