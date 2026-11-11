package io.github.mdmusahub.jpaadmin.metadata;

import java.util.List;
import java.util.Locale;

/**
 * The naming rules of the console: the address of an entity, the words shown on screen, and whether a field name
 * looks sensitive. Package-private: these are rules, not API.
 */
final class Names {

    private Names() {
    }

    /**
     * The address of an entity: the simple class name in lower case, with every character that is not a letter or
     * a digit turned into a hyphen (so an inner class {@code Shop$Item} becomes {@code shop-item}). The result
     * holds only {@code a-z}, {@code 0-9} and single hyphens, and never starts or ends with one.
     *
     * @return the address, or an empty string when the name holds nothing usable
     */
    static String urlName(String simpleName) {
        return hyphenate(simpleName.toLowerCase(Locale.ROOT));
    }

    /**
     * The fallback address used when two entity classes in different packages share a simple name: the fully
     * qualified name in lower case, with dots (and anything else that is not a letter or a digit) as hyphens. Both
     * entities get this form, so neither keeps the short address and neither overwrites the other.
     */
    static String qualifiedUrlName(String className) {
        return hyphenate(className.toLowerCase(Locale.ROOT));
    }

    private static String hyphenate(String lowerCase) {
        StringBuilder result = new StringBuilder(lowerCase.length());
        boolean pendingHyphen = false;
        for (int i = 0; i < lowerCase.length(); i++) {
            char c = lowerCase.charAt(i);
            boolean usable = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
            if (usable) {
                if (pendingHyphen && !result.isEmpty()) {
                    result.append('-');
                }
                pendingHyphen = false;
                result.append(c);
            } else {
                pendingHyphen = true;
            }
        }
        return result.toString();
    }

    /**
     * A name for the screen, made from a Java name: {@code invoiceLine} and {@code InvoiceLine} both become
     * {@code "Invoice line"}, {@code created_at} becomes {@code "Created at"}, and an acronym stays whole, so
     * {@code HTTPLog} becomes {@code "HTTP log"}.
     */
    static String label(String name) {
        List<String> words = words(name);
        if (words.isEmpty()) {
            return name;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            if (i > 0) {
                result.append(' ');
                // Only an acronym keeps its capitals in the middle of a label.
                result.append(isAcronym(word) ? word : word.toLowerCase(Locale.ROOT));
            } else {
                result.append(isAcronym(word) ? word
                        : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.toString();
    }

    private static boolean isAcronym(String word) {
        if (word.length() < 2) {
            return false;
        }
        for (int i = 0; i < word.length(); i++) {
            if (!Character.isUpperCase(word.charAt(i)) && !Character.isDigit(word.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Splits a Java name into words at case changes, underscores, hyphens and spaces. */
    private static List<String> words(String name) {
        List<String> words = new java.util.ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' || c == '-' || c == ' ' || c == '$' || c == '.') {
                flush(words, word);
                continue;
            }
            boolean startsWord = Character.isUpperCase(c) && !word.isEmpty()
                    && (!Character.isUpperCase(word.charAt(word.length() - 1))
                            || (i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1))));
            if (startsWord) {
                flush(words, word);
            }
            word.append(c);
        }
        flush(words, word);
        return words;
    }

    private static void flush(List<String> words, StringBuilder word) {
        if (!word.isEmpty()) {
            words.add(word.toString());
            word.setLength(0);
        }
    }

    /**
     * Whether a field name counts as sensitive: the comparison ignores case, {@code _} and {@code -}, and asks
     * whether the field's name <em>contains</em> one of the configured names (D-11, SEC-2). So {@code userPassword},
     * {@code api_key} and {@code API-KEY} are sensitive, and {@code passport} is not.
     *
     * <p>It masks too much rather than too little on purpose: a name such as {@code tokenizer} contains
     * {@code token} and is masked, which {@code jpa.admin.sensitive-fields} can be narrowed to avoid.
     */
    static boolean looksSensitive(String fieldName, List<String> sensitiveNames) {
        String normalized = normalize(fieldName);
        for (String sensitive : sensitiveNames) {
            String needle = normalize(sensitive);
            if (!needle.isEmpty() && normalized.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String name) {
        StringBuilder result = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '_' && c != '-') {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }
}
