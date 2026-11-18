package io.github.mdmusahub.jpaadmin.service;

import java.util.Locale;

/**
 * The words of the errors next to a form field and of the messages above a form. Each one says what went wrong and how to
 * fix it (UI-6). They are fixed sentences: none holds a value the visitor typed, a constraint's class name, a table
 * name, SQL or an exception text (SEC-1, SEC-12). The only numbers are limits that come from the metadata or the
 * constraint (a length, a minimum).
 *
 * <p>Internal, not stable API.
 */
public final class FieldMessages {

    /** The most characters any text field accepts, whatever the column allows. */
    public static final int MAX_TEXT_LENGTH = 100_000;

    private FieldMessages() {
    }

    /** A required field was left empty. */
    public static String required() {
        return "Enter a value. This field is required.";
    }

    /** A text longer than the limit. */
    public static String tooLong(int limit) {
        return "Use at most " + group(limit) + " characters.";
    }

    /** A text with a control character. */
    public static String badCharacters() {
        return "Remove the characters that are not text, such as control characters, and try again.";
    }

    /** Not a whole number. */
    public static String wholeNumber() {
        return "Enter a whole number, using digits only, for example 42.";
    }

    /** A whole number outside the type's range. */
    public static String outOfRange(String min, String max) {
        return "Enter a whole number from " + min + " to " + max + ".";
    }

    /** Not a decimal number. */
    public static String number() {
        return "Enter a number, using digits and one decimal point, for example 19.99.";
    }

    /** Not yes or no. */
    public static String yesNo() {
        return "Choose Yes or No.";
    }

    /** Not a date. */
    public static String date() {
        return "Enter a date that exists, as year-month-day, for example 2026-11-17.";
    }

    /** Not a date with a time. */
    public static String dateTime() {
        return "Enter a date and time that exist, as year-month-day and hour:minute, for example 2026-11-17T10:30.";
    }

    /** Not one of an enum's constants. */
    public static String chooseListed() {
        return "Choose one of the listed values.";
    }

    /** Not a single character. */
    public static String singleCharacter() {
        return "Enter exactly one character.";
    }

    /** Not a UUID. */
    public static String uuid() {
        return "Enter an identifier in the form 123e4567-e89b-12d3-a456-426614174000.";
    }

    /** An upload field whose posted address is not a file the console stored for that field (Phase 9b, D-97). */
    public static String notOurFile() {
        return "The file is not one this console uploaded. Upload it again.";
    }

    /** A relation whose chosen record is not one of the choices. */
    public static String chooseExisting() {
        return "Choose one of the listed records. The record you picked is not available any more.";
    }

    /** A relation that would have more than the bound of choices. */
    public static String tooManyChoices(String label, int bound) {
        return "The " + label.toLowerCase(Locale.ROOT) + " cannot be set here: it has more than " + bound
                + " records to choose from, which this version of the console cannot list. It keeps its current value.";
    }

    /** The help text of a relation field that is shown as a notice because it has too many choices. */
    public static String tooManyChoicesNotice(int bound) {
        return "This field has more than " + bound + " records to choose from, so it cannot be set here yet. "
                + "It keeps its current value.";
    }

    /** The help text of a relation field that has no record to choose yet. */
    public static String noChoicesNotice() {
        return "There are no records to choose from yet. Create one first, then come back.";
    }

    /** A constraint of the host's mapping, or the database, refused the record, and no field can be named. */
    public static String refused() {
        return "The record was not saved because the database or the entity's rules refused it. Check the values and "
                + "try again.";
    }

    /** A constraint that names no kind of its own. */
    public static String notAccepted() {
        return "This value is not accepted. Check it and try again.";
    }

    /** The record changed after the form was opened. */
    public static String stale() {
        return "This record was changed by someone else after you opened the form, so your changes were not saved. "
                + "Open the record again to see the latest values, then make your changes again.";
    }

    /** The field is not part of what the console can change. */
    public static String cannotBeSet() {
        return "This field cannot be set here.";
    }

    /** "2 fields need your attention." for the summary. */
    public static String summary(int fields) {
        return fields == 1 ? "1 field needs your attention. It is marked below."
                : fields + " fields need your attention. They are marked below.";
    }

    /** Digits with a comma between thousands, the same on every machine. */
    private static String group(int number) {
        String digits = Integer.toString(number);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                grouped.append(',');
            }
            grouped.append(digits.charAt(i));
        }
        return grouped.toString();
    }
}
