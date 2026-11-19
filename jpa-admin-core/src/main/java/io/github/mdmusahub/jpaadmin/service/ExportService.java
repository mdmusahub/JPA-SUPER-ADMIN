package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import java.io.IOException;
import java.io.Writer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The CSV export of one entity's list (Phase 11, D-105 to D-110).
 *
 * <p><b>The same pipeline as the list</b> (ARCH-10): the search, the filters and the sort are checked and run by
 * {@link ListQueryService}, so an unknown, hidden or sensitive name is dropped exactly as on the list page (SEC-2) and every
 * value is a bound parameter (SEC-3). This class decides only what the list does not: whether an export is allowed, how many
 * rows, and how they are written.
 *
 * <p><b>SEC-11 is enforced here</b>, not only by hiding the button: an entity that is not the registry's is
 * {@link RecordNotFoundException} (the console's 404); a visible entity whose {@code @AdminResource(exportable)} is false,
 * or any entity while {@code jpa.admin.export-enabled} is false, is {@link ActionRefusedException} (403). A forged request
 * for a link the page never showed stops here.
 *
 * <p><b>Memory and cost:</b> records are read in pages of {@value #PAGE_SIZE} (a read-only transaction per page, ARCH-5), never
 * all at once (ARCH-10), at most the cap in all. A file costs one count, the one small statement per filterable relation that
 * the list also pays, and one statement per page. No entity is loaded and no statement is run per row (ARCH-6).
 *
 * <p><b>The cap</b> is {@code min(jpa.admin.export-max-rows, 50000)}. When more records match, the file holds the first rows
 * in the list's order and {@link Export#truncated()} says so before the first byte (a header needs that).
 *
 * <p><b>Failure:</b> {@link #open} reads the first page, so a failure there happens before any header is set and is a normal
 * console error. A failure after the first byte is logged once with the entity key and the number of rows written (never a
 * value) and re-thrown, so the connection is cut and half a file is not taken for a whole one.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it and the controller uses it.
 */
public final class ExportService {

    /** Rows read per statement. */
    public static final int PAGE_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final ListQueryService lists;
    private final AdminEntityRegistry registry;
    private final ExportSettings settings;
    private final Clock clock;
    private final int pageSize;

    private ExportService(ListQueryService lists, AdminEntityRegistry registry, ExportSettings settings, Clock clock,
                          int pageSize) {
        this.lists = Objects.requireNonNull(lists, "lists");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
        this.pageSize = pageSize;
    }

    /**
     * The usual service.
     *
     * @param lists    the list's query service (a transactional proxy in the application)
     * @param registry the entities the console manages
     * @param settings what the properties say
     * @param clock    decides the date in the file name
     */
    public static ExportService over(ListQueryService lists, AdminEntityRegistry registry, ExportSettings settings,
                                     Clock clock) {
        return new ExportService(lists, registry, settings, clock, PAGE_SIZE);
    }

    /** As {@link #over} with a smaller page, so a test can prove that a table larger than one page is read in pages. */
    static ExportService withPageSize(ListQueryService lists, AdminEntityRegistry registry, ExportSettings settings,
                                      Clock clock, int pageSize) {
        return new ExportService(lists, registry, settings, clock, pageSize);
    }

    /** A service that offers nothing and refuses everything: a console with no entity manager, or a test. */
    public static ExportService none() {
        return new ExportService(ListQueryService.unavailable(), AdminEntityRegistry.empty(), ExportSettings.off(),
                Clock.systemUTC(), PAGE_SIZE);
    }

    /** The most rows one file holds. */
    public int cap() {
        return settings.cap();
    }

    /**
     * Whether the list of {@code entity} offers the export button: the export is switched on, the entity may be exported, and
     * it has at least one column that can go in a file.
     *
     * @param entity an entity of the registry
     */
    public boolean offers(AdminEntity entity) {
        return settings.enabled() && entity.canExport() && !ListColumns.of(entity, registry).stream()
                .allMatch(column -> column.type() == Cell.Type.MASKED);
    }

    /**
     * Starts an export: checks the switches, reads the first page and returns the file ready to be written.
     *
     * @param entity an entity of the registry
     * @param query  the checked list query (search, filters, sort); its page and size are ignored
     * @throws RecordNotFoundException if the entity is not the registry's
     * @throws ActionRefusedException  if the export is switched off, for all entities or for this one, or the entity has
     *                                 no column that can go in a file
     */
    public Export open(AdminEntity entity, ListQuery query) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(query, "query");
        if (registry.find(entity.urlName()).filter(entity::equals).isEmpty()) {
            throw new RecordNotFoundException();
        }
        if (!offers(entity)) {
            throw new ActionRefusedException();
        }
        ExportPlan plan = lists.exportPlan(entity, query);
        long toWrite = Math.min(plan.total(), cap());
        List<List<String>> first = toWrite == 0 ? List.of() : lists.exportRows(plan, 0, (int) Math.min(pageSize, toWrite));
        String fileName = entity.urlName() + "-" + LocalDate.now(clock) + ".csv";
        return new Export(plan, fileName, toWrite, plan.total() > cap(), first);
    }

    /**
     * One export, ready to be written: the file name, whether it is cut at the cap, and the rows. Write it once.
     *
     * <p>Internal, not stable API.
     */
    public final class Export {

        private final ExportPlan plan;
        private final String fileName;
        private final long rows;
        private final boolean truncated;
        private final List<List<String>> first;

        private Export(ExportPlan plan, String fileName, long rows, boolean truncated, List<List<String>> first) {
            this.plan = plan;
            this.fileName = fileName;
            this.rows = rows;
            this.truncated = truncated;
            this.first = first;
        }

        /** {@code <entity-url-name>-<yyyy-MM-dd>.csv}: ASCII only, no text a visitor typed. */
        public String fileName() {
            return fileName;
        }

        /** Whether more records match than the file holds. */
        public boolean truncated() {
            return truncated;
        }

        /** The cap the file was cut at; meaningful when {@link #truncated()}. */
        public int rowLimit() {
            return cap();
        }

        /** How many records match the search and the filters, over all pages. */
        public long matching() {
            return plan.total();
        }

        /** How many data rows the file holds: the matching records, up to the cap. */
        public long rows() {
            return rows;
        }

        /**
         * Writes the header and the rows, page by page, and flushes. The caller supplies the destination; the controller
         * passes a UTF-8 writer over the servlet output stream.
         *
         * @throws IOException if the destination fails (a client that went away, for instance)
         */
        public void writeTo(Writer out) throws IOException {
            CsvWriter csv = new CsvWriter(out);
            long written = 0;
            try {
                if (settings.bom()) {
                    csv.byteOrderMark();
                }
                csv.row(plan.columns().stream().map(ListColumn::label).toList());
                List<List<String>> page = first;
                int requested = (int) Math.min(pageSize, rows);
                long offset = 0;
                while (true) {
                    // A page never holds more than was asked for, and what was asked for never goes past the cap.
                    for (List<String> row : page) {
                        csv.row(row);
                        written++;
                    }
                    offset += page.size();
                    if (written >= rows || page.size() < requested) {
                        break; // done, or a short page: the table shrank while the file was written
                    }
                    requested = (int) Math.min(pageSize, rows - written);
                    page = lists.exportRows(plan, (int) offset, requested);
                }
                csv.flush();
            } catch (RuntimeException failure) {
                // The class name only: a database message can quote a value (SEC-1, SEC-12).
                log.error("The CSV export of '{}' stopped after {} rows because a page could not be read ({}).",
                        plan.entity().urlName(), written, failure.getClass().getSimpleName());
                throw failure;
            }
        }
    }
}
