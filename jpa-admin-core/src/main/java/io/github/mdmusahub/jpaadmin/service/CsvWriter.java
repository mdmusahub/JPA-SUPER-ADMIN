package io.github.mdmusahub.jpaadmin.service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Writes CSV by hand (RFC 4180), with no library (D-105). Knows nothing about entities: it turns lists of strings into
 * lines.
 *
 * <p>The rules, in the order they are applied to one cell:
 * <ol>
 *   <li>{@code null} and the empty string are an empty cell, with no quotes.</li>
 *   <li>Control characters other than tab, carriage return and line feed are dropped (a NUL or an escape sequence in a text
 *       column would break or confuse the consumer). This runs <em>first</em>, so a control character in front of an
 *       {@code =} cannot hide it from the next rule.</li>
 *   <li><b>Formula guard (SEC-10):</b> a cell whose first character is {@code =}, {@code +}, {@code -}, {@code @}, a tab or a
 *       carriage return gets a single quote in front of it, so a spreadsheet shows the text instead of running it. Numbers are
 *       not exempt: a negative number arrives as text, {@code '-5} (D-105).</li>
 *   <li><b>Quoting:</b> a cell that holds a comma, a double quote, a carriage return or a line feed is wrapped in double
 *       quotes and its double quotes are doubled. This runs <em>after</em> the guard, so the quote in front of a guarded cell
 *       is part of the text and a guarded cell with a comma is still quoted as a whole.</li>
 * </ol>
 * Rows end with a carriage return and a line feed.
 *
 * <p>Package-private: it is a rule, not API.
 */
final class CsvWriter {

    /** The line ending between rows (RFC 4180). */
    static final String LINE_END = "\r\n";

    /** The UTF-8 byte order mark as a character; written through a UTF-8 writer it becomes the bytes EF BB BF. */
    static final char BYTE_ORDER_MARK = '﻿';

    private final Writer out;

    CsvWriter(Writer out) {
        this.out = out;
    }

    /** Writes the byte order mark. Call it before the first row, and only when the settings ask for it. */
    void byteOrderMark() throws IOException {
        out.write(BYTE_ORDER_MARK);
    }

    /** Writes one row: the cells separated by commas, then the line ending. */
    void row(List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(cell(cells.get(i)));
        }
        out.write(LINE_END);
    }

    /** Writes everything that was handed over so far. */
    void flush() throws IOException {
        out.flush();
    }

    /** One cell as it appears in the file: cleaned, guarded against formulas, quoted when it has to be. */
    static String cell(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = withoutControls(raw);
        if (text.isEmpty()) {
            return "";
        }
        if (startsAFormula(text.charAt(0))) {
            text = "'" + text;
        }
        return needsQuotes(text) ? quoted(text) : text;
    }

    /** Whether a spreadsheet could take a cell that starts with {@code first} for a formula (OWASP's list, D-105). */
    static boolean startsAFormula(char first) {
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }

    private static boolean needsQuotes(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ',' || c == '"' || c == '\r' || c == '\n') {
                return true;
            }
        }
        return false;
    }

    private static String quoted(String text) {
        StringBuilder quoted = new StringBuilder(text.length() + 8).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted.append('"');
            }
            quoted.append(c);
        }
        return quoted.append('"').toString();
    }

    private static String withoutControls(String raw) {
        boolean clean = true;
        for (int i = 0; i < raw.length() && clean; i++) {
            clean = !isDropped(raw.charAt(i));
        }
        if (clean) {
            return raw;
        }
        StringBuilder kept = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!isDropped(c)) {
                kept.append(c);
            }
        }
        return kept.toString();
    }

    private static boolean isDropped(char c) {
        return Character.isISOControl(c) && c != '\t' && c != '\r' && c != '\n';
    }
}
