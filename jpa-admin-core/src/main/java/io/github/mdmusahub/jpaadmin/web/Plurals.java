package io.github.mdmusahub.jpaadmin.web;

import java.util.Locale;

/**
 * The plural of an entity's label for the page heading, by the plain English rules, until the host can say it with
 * {@code @AdminResource} (a later phase). {@code Invoice} becomes {@code Invoices}, {@code Box} becomes {@code Boxes},
 * and a consonant followed by {@code y} becomes {@code ies}. Irregular plurals ({@code Person}) come out wrong, which
 * is why the host will be able to override it.
 *
 * <p>Package-private: it is a rule, not API.
 */
final class Plurals {

    private Plurals() {
    }

    static String of(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return label;
        }
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z") || lower.endsWith("ch")
                || lower.endsWith("sh")) {
            return label + "es";
        }
        if (lower.length() > 1 && lower.endsWith("y") && "aeiou".indexOf(lower.charAt(lower.length() - 2)) < 0) {
            return label.substring(0, label.length() - 1) + "ies";
        }
        return label + "s";
    }
}
