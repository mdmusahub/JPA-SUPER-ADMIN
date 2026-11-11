package io.github.mdmusahub.jpaadmin.testsupport;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Records every SQL statement Hibernate prepares for the list-test persistence unit, so a test can prove which columns
 * a page selects, how many statements it costs, and that the search text is a bound parameter and not part of the SQL.
 * Hibernate creates the instance from its class name (see {@code persistence.xml}); the statements are kept in a static
 * list because that instance is not reachable from a test.
 */
public final class RecordingStatementInspector implements StatementInspector {

    private static final long serialVersionUID = 1L;
    private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }

    /** Forgets everything recorded so far. */
    public static void clear() {
        STATEMENTS.clear();
    }

    /** The statements prepared since the last {@link #clear()}, in order. */
    public static List<String> statements() {
        return List.copyOf(STATEMENTS);
    }

    /** Only the {@code select} statements. */
    public static List<String> selects() {
        return STATEMENTS.stream().filter(sql -> sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("select"))
                .toList();
    }
}
