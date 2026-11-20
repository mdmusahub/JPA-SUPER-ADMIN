package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads one page of an entity's records for the list page.
 *
 * <p>Two Criteria queries per page, whatever the number of rows or relation columns: a {@code count} and a page. No
 * query is ever built from text (SEC-3): the search text travels as a bound parameter, and the only names that reach
 * the query are attribute names the metadata gave out. Both run in one read-only transaction (ARCH-5), so the count
 * and the rows agree, and nothing is lazily loaded after it ends.
 *
 * <p>What a row holds (ARCH-3, ARCH-4, SEC-1):
 * <ul>
 *   <li>The query selects <em>attributes</em>, never entities, so no entity, proxy or collection is created. Only
 *       the identifier and the columns the page shows are selected.</li>
 *   <li>A sensitive attribute is <b>not selected at all</b>: its cell is {@link Cell#masked()}, built without a value.
 *       It can be neither searched nor sorted.</li>
 *   <li>To-many relations, embedded values and binary data are neither selected nor shown.</li>
 *   <li>A to-one relation is a {@code LEFT JOIN} that selects the target's identifier and its label attribute in the
 *       same query, so a page of any size costs no extra query (ARCH-6). The label is the first of the target's
 *       visible, non-sensitive text attributes named {@code name}, {@code title}, {@code label} and so on, else the
 *       first such text attribute, else {@code #id}. A target that is hidden from the console shows {@code #id}
 *       only.</li>
 * </ul>
 *
 * <p>The search ({@link ListQuery}) is a case-insensitive "contains" over the entity's visible, non-sensitive
 * {@code String} attributes, with {@code %} and {@code _} taken literally. The sort puts missing values last in both
 * directions, on every database, and always ends with the identifier (ascending) so that paging is stable when the
 * sort column has equal values. A relation column with a label is sorted by that label, through the same join that
 * shows it, so sorting costs no extra join and no extra statement (D-60).
 *
 * <p>Filters ({@link ActiveFilter}) are further conditions of the same two queries, all bound parameters
 * ({@link FilterPredicates}). A relation filter lists its target's records as choices, so each filterable relation costs
 * one more small statement per page (the target's identifier and label only, at most {@value #MAX_RELATION_OPTIONS} + 1
 * rows, ordered by label); a target with more records than that gets no filter and the page says so, so a list is
 * never silently cut short. The choice in the address must be one of those records, or the filter is ignored.
 *
 * <p>A page past the end is clamped to the last page; the {@linkplain ListPage#query() effective query} says which
 * page was read. Anything the metadata does not allow in a query (a sort key, for instance) is dropped here as well,
 * not only where the request is parsed.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it and the controller uses it.
 */
public class ListQueryService {

    /** The most choices a relation filter lists. A target with more records gets no filter (never a cut-short list). */
    public static final int MAX_RELATION_OPTIONS = 200;

    private final EntityManager entityManager;
    private final AdminEntityRegistry registry;
    private final UploadFiles uploads;
    private final int defaultPageSize;
    private final Map<String, Plan> plans = new ConcurrentHashMap<>();

    /** Not public, for the same reason as {@code CountService}: Spring's transactional proxy subclasses this class. */
    protected ListQueryService(EntityManager entityManager, AdminEntityRegistry registry) {
        this(entityManager, registry, UploadFiles.none());
    }

    protected ListQueryService(EntityManager entityManager, AdminEntityRegistry registry, UploadFiles uploads) {
        this(entityManager, registry, uploads, ListQuery.DEFAULT_SIZE);
    }

    protected ListQueryService(EntityManager entityManager, AdminEntityRegistry registry, UploadFiles uploads,
                               int defaultPageSize) {
        this.entityManager = entityManager;
        this.registry = Objects.requireNonNull(registry, "registry");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        if (!ListQuery.PAGE_SIZES.contains(defaultPageSize)) {
            throw new IllegalArgumentException("defaultPageSize must be one of " + ListQuery.PAGE_SIZES);
        }
        this.defaultPageSize = defaultPageSize;
    }

    /**
     * The usual service.
     *
     * @param entityManager the console's transaction-aware entity manager
     * @param registry      the entities the console manages (the labels of relations come from it)
     */
    public static ListQueryService over(EntityManager entityManager, AdminEntityRegistry registry) {
        return new ListQueryService(Objects.requireNonNull(entityManager, "entityManager"), registry);
    }

    /**
     * The service with upload support: the cell of an upload field is an image or a file link (Phase 9b, D-99).
     *
     * @param uploads what the lists need to know about uploaded files
     */
    public static ListQueryService over(EntityManager entityManager, AdminEntityRegistry registry, UploadFiles uploads) {
        return new ListQueryService(Objects.requireNonNull(entityManager, "entityManager"), registry, uploads);
    }

    /**
     * The service with upload support and a page size of the host's choosing (Phase 13, D-119): the size a list has when the
     * address says none, or says one that is not on offer.
     *
     * @param defaultPageSize one of {@link ListQuery#PAGE_SIZES}; the auto-configuration has already checked the property
     * @throws IllegalArgumentException if it is not one of them
     */
    public static ListQueryService over(EntityManager entityManager, AdminEntityRegistry registry, UploadFiles uploads,
                                        int defaultPageSize) {
        return new ListQueryService(Objects.requireNonNull(entityManager, "entityManager"), registry, uploads,
                defaultPageSize);
    }

    /** The page size a list has when the address says none: {@code jpa.admin.page-size}, 25 unless the host set it. */
    public int defaultPageSize() {
        return defaultPageSize;
    }

    /** A service for a console that has no entity manager factory. It lists nothing; no entity can be asked for. */
    public static ListQueryService unavailable() {
        return new ListQueryService(null, AdminEntityRegistry.empty());
    }

    /**
     * One page of {@code entity}.
     *
     * @param entity an entity from the registry
     * @param query  the checked query
     * @throws IllegalStateException if the console has no entity manager
     */
    @Transactional(readOnly = true)
    public ListPage list(AdminEntity entity, ListQuery query) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(query, "query");
        if (entityManager == null) {
            throw new IllegalStateException("The console has no entity manager to read records with.");
        }
        Plan plan = plans.computeIfAbsent(entity.urlName(), key -> plan(entity));
        ListQuery checked = plan.checked(query);

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        Filtering filtering = filtering(builder, plan, checked);
        checked = filtering.query();
        long total = count(builder, entity, plan, checked, filtering.specs());
        int lastPage = total == 0 ? 0 : (int) Math.min((total - 1) / checked.size(), Integer.MAX_VALUE);
        ListQuery effective = checked.withPage(Math.min(checked.page(), lastPage));
        List<List<String>> relationIds = new ArrayList<>();
        List<ListView.Row> rows = total == 0 ? List.of()
                : rows(builder, entity, plan, effective, filtering.specs(), relationIds);
        return new ListPage(plan.listColumns(), rows, total, effective, plan.searchedLabels(), filtering.controls(),
                filtering.notes(), relationIds);
    }

    /**
     * Checks the five request parameters of {@code entity}'s list page against this console's metadata (see
     * {@link ListQuery#parse}). Never throws.
     *
     * @param entity an entity from the registry
     */
    public ListQuery parse(AdminEntity entity, String q, String sort, String dir, String page, String size) {
        return ListQuery.parse(entity, registry, q, sort, dir, page, size, defaultPageSize);
    }

    /**
     * Reads the {@code filter.*} parameters of a request against this entity's metadata (see {@link ListFilters}).
     * Never throws; whatever is not acceptable is ignored and described in a note.
     *
     * @param entity an entity from the registry
     * @param params the request's parameters; only names that start with {@code filter.} are read
     */
    public FilterParse parseFilters(AdminEntity entity, Map<String, List<String>> params) {
        return ListFilters.parse(entity, registry, params);
    }

    /**
     * Reads the {@code hide} parameters of a request against this entity's columns (see {@link HiddenColumns}): only the key
     * of a visible, non-sensitive column is accepted, in column order, and the last column that can stay is never hidden.
     * Never throws; whatever is not acceptable is ignored without a message, so a sensitive name cannot be told from an
     * unknown one (SEC-1, SEC-2).
     *
     * @param entity an entity from the registry
     * @param values the values of the repeated {@code hide} parameter, or {@code null}
     */
    public HiddenParse parseHidden(AdminEntity entity, List<String> values) {
        return HiddenColumns.parse(ListColumns.of(entity, registry), values);
    }

    // ---- the two queries ------------------------------------------------------------------------------------

    private long count(CriteriaBuilder builder, AdminEntity entity, Plan plan, ListQuery query,
                       List<FilterPredicates.Spec> specs) {
        CriteriaQuery<Long> count = builder.createQuery(Long.class);
        Root<?> root = count.from(entity.type());
        count.select(builder.count(root));
        FilterPredicates.Built conditions = conditions(builder, root, plan, query, specs);
        if (!conditions.predicates().isEmpty()) {
            count.where(conditions.predicates().toArray(new Predicate[0]));
        }
        TypedQuery<Long> typed = entityManager.createQuery(count);
        bind(typed, conditions.bindings());
        Long total = typed.getSingleResult();
        return total == null ? 0 : total;
    }

    /**
     * @param relationIds filled with one entry per row: for each column the identifier of the related record as text, or
     *                    {@code null} when the column is not a relation or the relation is empty. The target's
     *                    identifier is already one of the selections, so a link costs no extra statement (D-54).
     */
    private List<ListView.Row> rows(CriteriaBuilder builder, AdminEntity entity, Plan plan, ListQuery query,
                                    List<FilterPredicates.Spec> specs, List<List<String>> relationIds) {
        CriteriaQuery<Tuple> select = builder.createTupleQuery();
        Root<?> root = select.from(entity.type());

        // Selection 0 is the identifier. Every other selection is remembered by position, per column.
        Projection projection = project(root, plan, true, new HashSet<>(query.hidden()), query.sort());
        int[] valueAt = projection.valueAt();
        int[] labelAt = projection.labelAt();
        select.multiselect(projection.selections());

        FilterPredicates.Built conditions = conditions(builder, root, plan, query, specs);
        if (!conditions.predicates().isEmpty()) {
            select.where(conditions.predicates().toArray(new Predicate[0]));
        }
        orderBy(builder, select, root, plan, query, projection.labelPaths());

        TypedQuery<Tuple> typed = entityManager.createQuery(select);
        bind(typed, conditions.bindings());
        typed.setFirstResult((int) Math.min((long) query.page() * query.size(), Integer.MAX_VALUE));
        typed.setMaxResults(query.size());

        List<ListView.Row> rows = new ArrayList<>();
        for (Tuple tuple : typed.getResultList()) {
            List<Cell> cells = new ArrayList<>(plan.columns().size());
            List<String> targets = new ArrayList<>(plan.columns().size());
            for (int i = 0; i < plan.columns().size(); i++) {
                PlannedColumn column = plan.columns().get(i);
                cells.add(cell(column, tuple, valueAt[i], labelAt[i]));
                Object target = column.relation() != null && valueAt[i] >= 0 ? tuple.get(valueAt[i]) : null;
                targets.add(target == null ? null : CellFormatter.id(target));
            }
            rows.add(new ListView.Row(CellFormatter.id(tuple.get(0)), cells));
            relationIds.add(targets);
        }
        return rows;
    }

    /**
     * What a query selects for the columns of a plan, and where each column's value (and a relation's label) sits in a row of
     * the result. The list and the export share it, so both select the same attributes the same way: a sensitive attribute is
     * never selected (SEC-1), a to-one relation is one {@code LEFT JOIN} that also selects the label (ARCH-6), and nothing
     * else (no entity, no collection, no embedded or binary value) is touched (ARCH-3, ARCH-4).
     *
     * @param labelPaths the label of each relation column, from the join the column already makes: a sort by that column
     *                   orders by this path, so sorting adds neither a join nor a statement (D-60)
     */
    private record Projection(List<Selection<?>> selections, int[] valueAt, int[] labelAt, Map<String, Path<?>> labelPaths) {
    }

    /**
     * @param selectId whether selection 0 is the identifier. The list needs it for the row; the export selects the
     *                 identifier only when it is an exported column, so an identifier that is a sensitive field is never read.
     * @param hidden   the keys of the columns the page does not show (Phase 12, D-113): such a column is neither selected nor
     *                 joined, so hiding relation columns lowers the cost of a page. The one exception is a hidden relation
     *                 column that is the sort key: it keeps its join for the {@code ORDER BY} only, nothing of it is selected
     * @param sortKey  the key the query is sorted by, or {@code null}
     */
    private static Projection project(Root<?> root, Plan plan, boolean selectId, Set<String> hidden, String sortKey) {
        List<Selection<?>> selections = new ArrayList<>();
        if (selectId) {
            selections.add(root.get(plan.idAttribute()));
        }
        int[] valueAt = new int[plan.columns().size()];
        int[] labelAt = new int[plan.columns().size()];
        Map<String, Path<?>> labelPaths = new HashMap<>();
        for (int i = 0; i < plan.columns().size(); i++) {
            PlannedColumn column = plan.columns().get(i);
            valueAt[i] = -1;
            labelAt[i] = -1;
            if (column.masked()) {
                continue; // a sensitive attribute is never selected (SEC-1)
            }
            if (hidden.contains(column.column().key())) {
                if (column.relation() != null && column.relation().labelAttribute() != null
                        && column.column().key().equals(sortKey)) {
                    // Sorted by but not shown: the join stays for the ORDER BY, and nothing is selected from it.
                    labelPaths.put(column.column().key(), root.join(column.field().name(), JoinType.LEFT)
                            .get(column.relation().labelAttribute()));
                }
                continue; // a hidden column is not selected and not joined (D-113)
            }
            if (column.relation() != null) {
                Join<Object, Object> target = root.join(column.field().name(), JoinType.LEFT);
                valueAt[i] = selections.size();
                selections.add(target.get(column.relation().idAttribute()));
                if (column.relation().labelAttribute() != null) {
                    labelAt[i] = selections.size();
                    Path<?> label = target.get(column.relation().labelAttribute());
                    selections.add(label);
                    labelPaths.put(column.column().key(), label);
                }
            } else if (column.field().kind() != AdminField.Kind.TO_ONE) {
                valueAt[i] = selections.size();
                selections.add(root.get(column.field().name()));
            }
        }
        return new Projection(selections, valueAt, labelAt, labelPaths);
    }

    // ---- the export: the same pipeline, read in pages ----------------------------------------------------------

    /**
     * Prepares an export of {@code entity}: checks the query exactly as the list does (an unknown, hidden or sensitive sort
     * key, filter or relation choice is dropped, SEC-2), resolves the filters, and counts the records that match. Reads in a
     * read-only transaction of its own (ARCH-5). The columns are the list's, minus the masked ones and minus the hidden ones
     * (Phase 12): a sensitive or hidden column is not in the header, the rows or the SQL.
     *
     * <p>This method does not check the export switches (SEC-11); {@link ExportService#open} does, and is the only caller
     * outside tests.
     *
     * @param entity an entity from the registry
     * @param query  the checked query; its page and size are ignored
     * @throws IllegalStateException if the console has no entity manager
     */
    @Transactional(readOnly = true)
    public ExportPlan exportPlan(AdminEntity entity, ListQuery query) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(query, "query");
        requireEntityManager();
        Plan plan = plans.computeIfAbsent(entity.urlName(), key -> plan(entity));
        ListQuery checked = plan.checked(query);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        Filtering filtering = filtering(builder, plan, checked);
        checked = filtering.query();
        long total = count(builder, entity, plan, checked, filtering.specs());
        List<String> hidden = checked.hidden();
        List<ListColumn> columns = plan.columns().stream()
                .filter(column -> !column.masked() && !hidden.contains(column.column().key()))
                .map(PlannedColumn::column).toList();
        return new ExportPlan(entity, columns, total, checked, filtering.specs());
    }

    /**
     * One page of an export, as cell text, one entry per exported column ({@code null} is an empty cell). The same
     * selection, conditions, bound parameters and order as the list's page, cut with {@code setFirstResult} and
     * {@code setMaxResults}; a read-only transaction of its own per call (ARCH-5), so no connection is held while a slow
     * client takes the bytes.
     *
     * @param plan   from {@link #exportPlan}
     * @param offset rows to skip, at least 0
     * @param limit  rows to read, at least 1
     */
    @Transactional(readOnly = true)
    public List<List<String>> exportRows(ExportPlan plan, int offset, int limit) {
        Objects.requireNonNull(plan, "plan");
        if (offset < 0 || limit < 1) {
            throw new IllegalArgumentException("offset must not be negative and limit must be at least 1");
        }
        requireEntityManager();
        AdminEntity entity = plan.entity();
        Plan listPlan = plans.computeIfAbsent(entity.urlName(), key -> plan(entity));
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> select = builder.createTupleQuery();
        Root<?> root = select.from(entity.type());
        Set<String> hidden = new HashSet<>(plan.query().hidden());
        Projection projection = project(root, listPlan, false, hidden, plan.query().sort());
        select.multiselect(projection.selections());
        FilterPredicates.Built conditions = conditions(builder, root, listPlan, plan.query(), plan.specs());
        if (!conditions.predicates().isEmpty()) {
            select.where(conditions.predicates().toArray(new Predicate[0]));
        }
        orderBy(builder, select, root, listPlan, plan.query(), projection.labelPaths());
        TypedQuery<Tuple> typed = entityManager.createQuery(select);
        bind(typed, conditions.bindings());
        typed.setFirstResult(offset);
        typed.setMaxResults(limit);

        List<List<String>> rows = new ArrayList<>();
        for (Tuple tuple : typed.getResultList()) {
            List<String> cells = new ArrayList<>(plan.columns().size());
            for (int i = 0; i < listPlan.columns().size(); i++) {
                PlannedColumn column = listPlan.columns().get(i);
                if (column.masked() || hidden.contains(column.column().key())) {
                    continue; // not in the header, so not in the row
                }
                int valueAt = projection.valueAt()[i];
                int labelAt = projection.labelAt()[i];
                if (valueAt < 0) {
                    cells.add(null);
                } else if (column.relation() != null) {
                    cells.add(ExportValues.relation(labelAt < 0 ? null : tuple.get(labelAt), tuple.get(valueAt)));
                } else {
                    // An upload field exports its stored address text, like any other text (D-105).
                    cells.add(ExportValues.text(column.column().type(), tuple.get(valueAt)));
                }
            }
            rows.add(cells);
        }
        return rows;
    }

    private void requireEntityManager() {
        if (entityManager == null) {
            throw new IllegalStateException("The console has no entity manager to read records with.");
        }
    }

    private Cell cell(PlannedColumn column, Tuple tuple, int valueAt, int labelAt) {
        if (column.masked()) {
            return Cell.masked();
        }
        if (valueAt < 0) {
            return Cell.empty();
        }
        Object value = tuple.get(valueAt);
        if (column.relation() != null) {
            return CellFormatter.relation(labelAt < 0 ? null : tuple.get(labelAt), value);
        }
        if (column.field().isUpload() && value instanceof String url) {
            // The stored address of an upload field: an image, a file link or a note, or ordinary escaped text when it is not
            // one of the console's own addresses (never a link or an image source then).
            Optional<Cell> shown = uploads.cell(url, column.column().label());
            if (shown.isPresent()) {
                return shown.get();
            }
        }
        return CellFormatter.format(column.column().type(), value, column.field());
    }

    /**
     * Adds the sort, then the identifier ascending, so equal values never make the pages shift (stable paging).
     *
     * <p>One rule for missing values, the same on every database (D-60): nulls come last in both directions
     * ({@link Nulls#LAST}). Without it H2 and MySQL put nulls first in an ascending sort and PostgreSQL puts them last.
     * A relation column is ordered by its target's label, through the join the select already has.
     */
    private static void orderBy(CriteriaBuilder builder, CriteriaQuery<Tuple> select, Root<?> root, Plan plan,
                                ListQuery query, Map<String, Path<?>> labelPaths) {
        var byId = builder.asc(root.get(plan.idAttribute()));
        if (query.sort() == null || plan.idAttribute().equals(query.sort())) {
            // Sorted by the identifier itself, or not sorted: the identifier alone is already a total order, and it
            // is never null, so no null rule is needed.
            select.orderBy(query.sort() != null && ListQuery.DESC.equals(query.dir())
                    ? builder.desc(root.get(plan.idAttribute())) : byId);
            return;
        }
        Path<?> sorted = labelPaths.containsKey(query.sort()) ? labelPaths.get(query.sort()) : root.get(query.sort());
        select.orderBy(ListQuery.DESC.equals(query.dir()) ? builder.desc(sorted, Nulls.LAST)
                : builder.asc(sorted, Nulls.LAST), byId);
    }

    /** The search and the filters as one list of conditions (all must hold) and the values to bind. */
    private static FilterPredicates.Built conditions(CriteriaBuilder builder, Root<?> root, Plan plan, ListQuery query,
                                                     List<FilterPredicates.Spec> specs) {
        FilterPredicates.Built filters = FilterPredicates.build(builder, root, specs);
        List<Predicate> predicates = new ArrayList<>();
        List<FilterPredicates.Bound> bindings = new ArrayList<>();
        Search search = Search.of(builder, root, plan.searchable(), query.search());
        if (search != null) {
            predicates.add(search.predicate());
            if (search.parameter() != null) {
                bindings.add(new FilterPredicates.Bound(search.parameter(), search.pattern()));
            }
        }
        predicates.addAll(filters.predicates());
        bindings.addAll(filters.bindings());
        return new FilterPredicates.Built(predicates, bindings);
    }

    @SuppressWarnings("unchecked")
    private static void bind(TypedQuery<?> typed, List<FilterPredicates.Bound> bindings) {
        for (FilterPredicates.Bound bound : bindings) {
            typed.setParameter((Parameter<Object>) bound.parameter(), bound.value());
        }
    }

    // ---- the search -----------------------------------------------------------------------------------------

    /**
     * The search condition: any searchable attribute, lower-cased, contains the lower-cased text. The text is a bound
     * parameter and {@code %}, {@code _} and the escape character are escaped, so what is typed is always taken
     * literally (SEC-3). An entity with nothing searchable matches nothing, honestly, instead of everything.
     */
    private record Search(Predicate predicate, ParameterExpression<String> parameter, String pattern) {

        static Search of(CriteriaBuilder builder, Root<?> root, List<String> searchable, String text) {
            if (text.isEmpty()) {
                return null;
            }
            if (searchable.isEmpty()) {
                return new Search(builder.disjunction(), null, null);
            }
            ParameterExpression<String> parameter = builder.parameter(String.class);
            List<Predicate> any = new ArrayList<>();
            for (String name : searchable) {
                any.add(builder.like(builder.lower(root.<String>get(name)), parameter, FilterPredicates.ESCAPE));
            }
            return new Search(builder.or(any), parameter, FilterPredicates.containsPattern(text));
        }
    }

    // ---- filters: controls, choices, and the conditions ---------------------------------------------------

    private record Filtering(ListQuery query, List<FilterPredicates.Spec> specs, List<FilterControl> controls,
                             List<FilterNote> notes) {
    }


    /**
     * The controls on offer and the filters that stay in force. A relation choice is checked against the target's
     * records (the same list the dropdown shows), so an id that is not one of them is ignored and never reaches a query.
     */
    private Filtering filtering(CriteriaBuilder builder, Plan plan, ListQuery query) {
        Map<String, ActiveFilter> active = new LinkedHashMap<>();
        query.filters().forEach(filter -> active.put(filter.key(), filter));
        List<FilterControl> controls = new ArrayList<>();
        List<FilterNote> notes = new ArrayList<>();
        List<ActiveFilter> kept = new ArrayList<>();
        List<FilterPredicates.Spec> specs = new ArrayList<>();
        for (FilterPlan planned : plan.filters()) {
            FilterField field = planned.field();
            ActiveFilter filter = active.get(field.key());
            Object relationId = null;
            switch (field.kind()) {
                case ENUM -> controls.add(new FilterControl(field.key(), field.label(), field.kind(), field.enumValues().stream()
                        .map(constant -> new FormView.Option(constant, CellFormatter.humanize(constant))).toList()));
                case BOOLEAN -> controls.add(new FilterControl(field.key(), field.label(), field.kind(),
                        List.of(new FormView.Option("true", "Yes"), new FormView.Option("false", "No"))));
                case RELATION -> {
                    RelationChoices choices = choices(builder, planned.relation());
                    if (choices.overTheBound()) {
                        notes.add(new FilterNote("The " + field.label() + " filter is not available: it would have more than "
                                + MAX_RELATION_OPTIONS + " choices.", false));
                        continue;
                    }
                    controls.add(new FilterControl(field.key(), field.label(), field.kind(), choices.options()));
                    if (filter != null) {
                        relationId = choices.ids().get(filter.value());
                        if (relationId == null) {
                            notes.add(new FilterNote("The " + field.label()
                                    + " filter was ignored: that choice is not available.", false));
                            filter = null;
                        }
                    }
                }
                case TEXT, NUMBER_RANGE, DATE_RANGE ->
                        controls.add(new FilterControl(field.key(), field.label(), field.kind(), List.of()));
            }
            if (filter != null) {
                kept.add(filter);
                specs.add(new FilterPredicates.Spec(filter, field,
                        planned.relation() == null ? null : planned.relation().idAttribute(), relationId));
            }
        }
        ListQuery effective = new ListQuery(query.search(), query.sort(), query.dir(), query.page(), query.size(), kept,
                query.hidden(), query.defaultSize());
        return new Filtering(effective, specs, controls, notes);
    }

    /** The target's records as choices (see {@link RelationChoices}): at most {@value #MAX_RELATION_OPTIONS}. */
    private RelationChoices choices(CriteriaBuilder builder, Relation relation) {
        return RelationChoices.read(entityManager, relation);
    }

    // ---- what an entity's list is made of ---------------------------------------------------------------------

    private Plan plan(AdminEntity entity) {
        List<ListColumn> listColumns = ListColumns.of(entity, registry);
        List<PlannedColumn> columns = new ArrayList<>();
        List<String> searchable = new ArrayList<>();
        List<String> searchedLabels = new ArrayList<>();
        for (ListColumn listed : listColumns) {
            ListColumn column = listed;
            AdminField field = entity.visibleField(column.key()).orElseThrow();
            Relation relation = column.type() == Cell.Type.RELATION ? Relation.of(entityManager, registry, field) : null;
            if (column.type() == Cell.Type.RELATION && column.sortable()
                    && (relation == null || relation.labelAttribute() == null)) {
                // The metadata said the target has a label but the query cannot join to it: not sortable, never guessed.
                column = new ListColumn(column.key(), column.label(), column.type(), false, column.align(),
                        column.targetEntity(), column.identifier());
            }
            columns.add(new PlannedColumn(column, field, relation));
            // A String shown as a badge (@AdminBadge) is an enum column but is still text that a search looks in.
            if ((column.type() == Cell.Type.TEXT || column.type() == Cell.Type.ENUM) && field.kind() == AdminField.Kind.TEXT
                    && field.javaType() == String.class && !field.sensitive()) {
                searchable.add(field.name());
                searchedLabels.add(column.label());
            }
        }
        return new Plan(entity.idAttribute(), List.copyOf(columns), List.copyOf(searchable),
                List.copyOf(searchedLabels), filterPlans(entity));
    }

    /** The fields of {@code entity} that may be filtered, and for a relation the target to read its choices from. */
    private List<FilterPlan> filterPlans(AdminEntity entity) {
        List<FilterPlan> plans = new ArrayList<>();
        for (FilterField field : ListFilters.fields(entity, registry)) {
            Relation relation = null;
            if (field.kind() == ListView.Filters.Definition.Kind.RELATION) {
                relation = Relation.of(entityManager, registry, entity.visibleField(field.key()).orElseThrow());
                if (relation == null || relation.labelAttribute() == null) {
                    continue; // nothing to list the choices from: no filter, never a guess
                }
            }
            plans.add(new FilterPlan(field, relation));
        }
        return List.copyOf(plans);
    }

    private record FilterPlan(FilterField field, Relation relation) {
    }

    private record PlannedColumn(ListColumn column, AdminField field, Relation relation) {

        boolean masked() {
            return column.type() == Cell.Type.MASKED;
        }
    }

    /** Everything about an entity's list that does not depend on a request. Built once per entity. */
    private record Plan(String idAttribute, List<PlannedColumn> columns, List<String> searchable,
                        List<String> searchedLabels, List<FilterPlan> filters) {

        List<ListColumn> listColumns() {
            return columns.stream().map(PlannedColumn::column).toList();
        }

        /**
         * The query with any sort, filter or hidden column the entity cannot do removed (SEC-2), so only known names reach a
         * query. The filters come out in column order, at most {@value ListQuery#MAX_FILTERS}; the hidden columns come out in
         * column order and never include a masked column or the last column that can stay.
         */
        ListQuery checked(ListQuery query) {
            boolean sortable = query.sort() != null && columns.stream().anyMatch(column -> column.column().sortable()
                    && column.column().key().equals(query.sort()));
            List<ActiveFilter> allowed = new ArrayList<>();
            for (FilterPlan plan : filters) {
                for (ActiveFilter filter : query.filters()) {
                    if (filter.key().equals(plan.field().key()) && filter.kind() == plan.field().kind()
                            && allowed.size() < ListQuery.MAX_FILTERS) {
                        allowed.add(filter);
                    }
                }
            }
            return new ListQuery(query.search(), sortable ? query.sort() : null, sortable ? query.dir() : null,
                    query.page(), query.size(), allowed, HiddenColumns.checked(listColumns(), query.hidden()),
                    query.defaultSize());
        }
    }
}
