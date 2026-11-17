package io.github.mdmusahub.jpaadmin.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns checked {@link ActiveFilter}s into Criteria predicates (SEC-3). No query text is ever built from a value: every
 * value is a bound parameter ({@link Bound}), and the only names that reach the query are attribute names that
 * {@link ListFilters} gave out. The count query and the page query call this with their own root, so both see the same
 * rows.
 *
 * <ul>
 *   <li>text: {@code lower(attribute) like :p escape '!'} with {@code %}, {@code _} and {@code !} taken literally;</li>
 *   <li>enum, boolean: equals the constant or the value;</li>
 *   <li>relation: the target's identifier equals the choice, read from the foreign key column with no join;</li>
 *   <li>number: {@code >= min} and/or {@code <= max}, compared in the field's own type;</li>
 *   <li>date: {@code >= start of the first day} and/or {@code <} the start of the day after the last (a plain date
 *       field: {@code <= last day}); an instant counts days in UTC, like the cells do.</li>
 * </ul>
 *
 * <p>Package-private: it is a rule, not API.
 */
final class FilterPredicates {

    /** The character that escapes {@code %} and {@code _} in a pattern. Not a backslash, which some databases treat specially. */
    static final char ESCAPE = '!';

    private FilterPredicates() {
    }

    /** A parameter and the value to bind to it. */
    record Bound(ParameterExpression<?> parameter, Object value) {
    }

    /** What one filter needs besides itself: the field, and for a relation the target's identifier attribute and the chosen id. */
    record Spec(ActiveFilter filter, FilterField field, String relationIdAttribute, Object relationId) {
    }

    /** The conditions and the values to bind. */
    record Built(List<Predicate> predicates, List<Bound> bindings) {
    }

    static Built build(CriteriaBuilder builder, Root<?> root, List<Spec> specs) {
        List<Predicate> predicates = new ArrayList<>();
        List<Bound> bindings = new ArrayList<>();
        for (Spec spec : specs) {
            switch (spec.filter().kind()) {
                case TEXT -> text(builder, root, spec, predicates, bindings);
                case ENUM -> enumeration(builder, root, spec, predicates, bindings);
                case BOOLEAN -> {
                    ParameterExpression<Boolean> p = builder.parameter(Boolean.class);
                    predicates.add(builder.equal(root.get(spec.field().key()), p));
                    bindings.add(new Bound(p, Boolean.valueOf(spec.filter().value())));
                }
                case RELATION -> relation(builder, root, spec, predicates, bindings);
                case NUMBER_RANGE -> numbers(builder, root, spec, predicates, bindings);
                case DATE_RANGE -> dates(builder, root, spec, predicates, bindings);
            }
        }
        return new Built(predicates, bindings);
    }

    /** {@code %text%} with the characters that mean something to {@code like} escaped, lower-cased. */
    static String containsPattern(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder escaped = new StringBuilder(lower.length() + 8).append('%');
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == ESCAPE || c == '%' || c == '_') {
                escaped.append(ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.append('%').toString();
    }

    private static void text(CriteriaBuilder builder, Root<?> root, Spec spec, List<Predicate> predicates,
                             List<Bound> bindings) {
        ParameterExpression<String> p = builder.parameter(String.class);
        predicates.add(builder.like(builder.lower(root.<String>get(spec.field().key())), p, ESCAPE));
        bindings.add(new Bound(p, containsPattern(spec.filter().value())));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void enumeration(CriteriaBuilder builder, Root<?> root, Spec spec, List<Predicate> predicates,
                                    List<Bound> bindings) {
        Class<Object> type = (Class<Object>) spec.field().javaType();
        Object constant = null;
        for (Object candidate : type.getEnumConstants()) {
            if (((Enum) candidate).name().equals(spec.filter().value())) {
                constant = candidate;
            }
        }
        if (constant == null) {
            throw new IllegalStateException("the filter value is not a constant of the enum"); // checked before; never a request error
        }
        ParameterExpression<Object> p = builder.parameter(type);
        predicates.add(builder.equal(root.get(spec.field().key()), p));
        bindings.add(new Bound(p, constant));
    }

    @SuppressWarnings("unchecked")
    private static void relation(CriteriaBuilder builder, Root<?> root, Spec spec, List<Predicate> predicates,
                                 List<Bound> bindings) {
        Path<Object> id = root.get(spec.field().key()).get(spec.relationIdAttribute());
        ParameterExpression<Object> p = builder.parameter((Class<Object>) spec.relationId().getClass());
        predicates.add(builder.equal(id, p));
        bindings.add(new Bound(p, spec.relationId()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void numbers(CriteriaBuilder builder, Root<?> root, Spec spec, List<Predicate> predicates,
                                List<Bound> bindings) {
        Class type = boxed(spec.field().javaType());
        Expression<Comparable> path = root.get(spec.field().key());
        if (spec.filter().from() != null) {
            ParameterExpression<Comparable> p = builder.parameter(type);
            predicates.add(builder.greaterThanOrEqualTo(path, p));
            bindings.add(new Bound(p, number(type, spec.filter().from())));
        }
        if (spec.filter().to() != null) {
            ParameterExpression<Comparable> p = builder.parameter(type);
            predicates.add(builder.lessThanOrEqualTo(path, p));
            bindings.add(new Bound(p, number(type, spec.filter().to())));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dates(CriteriaBuilder builder, Root<?> root, Spec spec, List<Predicate> predicates,
                              List<Bound> bindings) {
        Class type = spec.field().javaType();
        Expression<Comparable> path = root.get(spec.field().key());
        if (spec.filter().from() != null) {
            ParameterExpression<Comparable> p = builder.parameter(type);
            predicates.add(builder.greaterThanOrEqualTo(path, p));
            bindings.add(new Bound(p, startOf(type, LocalDate.parse(spec.filter().from()))));
        }
        if (spec.filter().to() != null) {
            LocalDate last = LocalDate.parse(spec.filter().to());
            ParameterExpression<Comparable> p = builder.parameter(type);
            if (dateOnly(type)) {
                predicates.add(builder.lessThanOrEqualTo(path, p));
                bindings.add(new Bound(p, startOf(type, last)));
            } else {
                predicates.add(builder.lessThan(path, p)); // the whole last day is included
                bindings.add(new Bound(p, startOf(type, last.plusDays(1))));
            }
        }
    }

    private static boolean dateOnly(Class<?> type) {
        return type == LocalDate.class || type == java.sql.Date.class;
    }

    /** The first moment of {@code day} in the field's own type; UTC for anything that is an instant. */
    private static Comparable<?> startOf(Class<?> type, LocalDate day) {
        if (type == LocalDate.class) {
            return day;
        }
        if (type == java.sql.Date.class) {
            return java.sql.Date.valueOf(day);
        }
        if (type == LocalDateTime.class) {
            return day.atStartOfDay();
        }
        Instant instant = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        if (type == Instant.class) {
            return instant;
        }
        if (type == OffsetDateTime.class) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        if (type == ZonedDateTime.class) {
            return instant.atZone(ZoneOffset.UTC);
        }
        if (type == java.sql.Timestamp.class) {
            return java.sql.Timestamp.from(instant);
        }
        if (type == java.util.Date.class) {
            return java.util.Date.from(instant);
        }
        throw new IllegalStateException("a date filter on an unsupported type"); // ListFilters never offers one
    }

    private static Comparable<?> number(Class<?> type, String canonical) {
        BigDecimal value = new BigDecimal(canonical);
        if (type == Integer.class) {
            return value.intValueExact();
        }
        if (type == Long.class) {
            return value.longValueExact();
        }
        if (type == Short.class) {
            return value.shortValueExact();
        }
        if (type == Byte.class) {
            return value.byteValueExact();
        }
        if (type == BigInteger.class) {
            return value.toBigIntegerExact();
        }
        if (type == Double.class) {
            return value.doubleValue();
        }
        if (type == Float.class) {
            return value.floatValue();
        }
        return value;
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        return type;
    }
}
