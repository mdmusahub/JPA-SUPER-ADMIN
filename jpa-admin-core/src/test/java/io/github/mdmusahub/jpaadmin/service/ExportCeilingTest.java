package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.DiscoverySettings;
import io.github.mdmusahub.jpaadmin.metadata.EntityScanner;
import io.github.mdmusahub.jpaadmin.testsupport.ListTestPersistence;
import io.github.mdmusahub.jpaadmin.testsupport.RecordingStatementInspector;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.Writer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The fixed ceiling of 50,000 rows, on a real table of 50,001 records (Phase 11, D-12, ARCH-10): the default settings cut the file
 * at the ceiling, say so, read it in pages of 500 and keep nothing in memory but the page being written. The records are made
 * by one statement inside the database, so the test is fast.
 */
class ExportCeilingTest {

    private static final int RECORDS = ExportSettings.CEILING + 1;

    private static final AdminEntityRegistry REGISTRY = EntityScanner.scan(ListTestPersistence.factory().getMetamodel(),
            DiscoverySettings.defaults());
    private static final AdminEntity ARCHIVE = REGISTRY.find("archive").orElseThrow();

    @BeforeAll
    static void fill() {
        ListTestPersistence.empty();
        ListTestPersistence.inTransaction(em -> em.createNativeQuery(
                "insert into Archive (title) select concat('record ', x) from system_range(1, " + RECORDS + ")")
                .executeUpdate());
    }

    @AfterAll
    static void clear() {
        ListTestPersistence.empty();
    }

    /** Counts lines and remembers the biggest chunk it was handed, so nothing is kept. */
    private static final class Counting extends Writer {
        long lineEnds;
        int biggestChunk;

        @Override
        public void write(char[] buffer, int offset, int length) {
            biggestChunk = Math.max(biggestChunk, length);
            for (int i = offset; i < offset + length; i++) {
                if (buffer[i] == '\n') {
                    lineEnds++;
                }
            }
        }

        @Override
        public void flush() { }

        @Override
        public void close() { }
    }

    @Test
    void theDefaultCeilingCutsAFileOfMoreRecordsAtFiftyThousandRows() throws IOException {
        EntityManager entityManager = ListTestPersistence.entityManager();
        try {
            List<int[]> pages = new ArrayList<>();
            ListQueryService lists = new ListQueryService(entityManager, REGISTRY) {
                @Override
                public List<List<String>> exportRows(ExportPlan plan, int offset, int limit) {
                    pages.add(new int[] {offset, limit});
                    return super.exportRows(plan, offset, limit);
                }
            };
            RecordingStatementInspector.clear();

            ExportService.Export export = ExportService.over(lists, REGISTRY, ExportSettings.defaults(), Clock.systemUTC())
                    .open(ARCHIVE, ListQuery.defaults());
            Counting out = new Counting();
            export.writeTo(out);

            assertThat(export.matching()).isEqualTo(RECORDS);
            assertThat(export.truncated()).isTrue();
            assertThat(export.rowLimit()).isEqualTo(50_000);
            assertThat(export.rows()).isEqualTo(50_000);
            assertThat(out.lineEnds).as("the header and 50,000 rows").isEqualTo(50_001);
            assertThat(pages).as("100 pages of 500, never more than a page at a time").hasSize(100);
            assertThat(pages).allSatisfy(page -> assertThat(page[1]).isEqualTo(500));
            assertThat(pages.get(99)[0]).isEqualTo(49_500);
            assertThat(RecordingStatementInspector.selects().stream().map(sql -> sql.toLowerCase(Locale.ROOT))
                    .filter(sql -> sql.startsWith("select count"))).hasSize(1);
            assertThat(out.biggestChunk).as("rows are written one at a time").isLessThan(1000);
        } finally {
            entityManager.close();
        }
    }

    @Test
    void aSearchThatMatchesFewerThanTheCeilingIsNotTruncated() throws IOException {
        EntityManager entityManager = ListTestPersistence.entityManager();
        try {
            ListQueryService lists = ListQueryService.over(entityManager, REGISTRY);
            ListQuery lastOne = lists.parse(ARCHIVE, "record 5000", null, null, null, null);

            ExportService.Export export = ExportService.over(lists, REGISTRY, ExportSettings.defaults(), Clock.systemUTC())
                    .open(ARCHIVE, lastOne);

            assertThat(export.truncated()).isFalse();
            assertThat(export.matching()).isLessThan(50_000);
        } finally {
            entityManager.close();
        }
    }
}
