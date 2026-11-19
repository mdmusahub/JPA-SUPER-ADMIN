package io.github.mdmusahub.jpaadmin.testsupport;

import java.util.ArrayList;
import java.util.List;

/**
 * A small RFC 4180 reader for the export tests, written separately from the writer so a test does not agree with the writer
 * by sharing its mistakes: quoted cells, doubled quotes, line breaks inside quotes, {@code \r\n} or {@code \n} between rows.
 * It does not undo the formula guard; a test that cares looks at the text.
 */
public final class Csv {

    private Csv() {
    }

    /** The rows of {@code text}. A trailing line ending does not make an extra row. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
                continue;
            }
            if (c == '"' && cell.isEmpty()) {
                quoted = true;
                any = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
                any = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                any = false;
            } else {
                cell.append(c);
                any = true;
            }
        }
        if (any || !cell.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}
