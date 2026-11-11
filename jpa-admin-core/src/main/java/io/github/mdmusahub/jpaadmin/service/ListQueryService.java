package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * {@code String} attributes, with {@code %} and {@code _} taken literally. The sort always ends with the identifier
 * (ascending) so that paging is stable when the sort column has equal values.
 *
 * <p>A page past the end is clamped to the last page; the {@linkplain ListPage#query() effective query} says which
 * page was read. Anything the metadata does not allow in a query (a sort key, for instance) is dropped here as well,
 * not only where the request is parsed.
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it and the controller uses it.
 */
public class ListQueryService {

    /** The character that escapes {@code %} and {@code _} in the search pattern. Not a backslash, which some databases treat specially. */
    private static final char ESCAPE = '!';

    private static final List<String> PREFERRED_LABELS = List.of("name", "title", "label", "fullname", "displayname",
            "username", "email", "code");

    private final EntityManager entityManager;
    private final AdminEntityRegistry registry;
    private final Map<String, Plan> plans = new ConcurrentHashMap<>();

    /** Not public, for the same reason as {@code CountService}: Spring's transactional proxy subclasses this class. */
    protected ListQueryService(EntityManager entityManager, AdminEntityRegistry registry) {
        this.entityManager = entityManager;
        this.registry = Objects.requireNonNull(registry, "registry");
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
        long total = count(builder, entity, plan, checked);
        int lastPage = total == 0 ? 0 : (int) Math.min((total - 1) / checked.size(), Integer.MAX_VALUE);
        ListQuery effective = checked.withPage(Math.min(checked.page(), lastPage));
        List<ListView.Row> rows = total == 0 ? List.of() : rows(builder, entity, plan, effective);
        return new ListPage(plan.listColumns(), rows, total, effective);
    }

    // ---- the two queries ------------------------------------------------------------------------------------

    private long count(CriteriaBuilder builder, AdminEntity entity, Plan plan, ListQuery query) {
        CriteriaQuery<Long> count = builder.createQuery(Long.class);
        Root<?> root = count.from(entity.type());
        count.select(builder.count(root));
        Search search = Search.of(builder, root, plan.searchable(), query.search());
        if (search != null) {
            count.where(search.predicate());
        }
        TypedQuery<Long> typed = entityManager.createQuery(count);
        bind(typed, search);
        Long total = typed.getSingleResult();
        return total == null ? 0 : total;
    }

    private List<ListView.Row> rows(CriteriaBuilder builder, AdminEntity entity, Plan plan, ListQuery query) {
        CriteriaQuery<Tuple> select = builder.createTupleQuery();
        Root<?> root = select.from(entity.type());

        // Selection 0 is the identifier. Every other selection is remembered by position, per column.
        List<Selection<?>> selections = new ArrayList<>();
        selections.add(root.get(plan.idAttribute()));
        int[] valueAt = new int[plan.columns().size()];
        int[] labelAt = new int[plan.columns().size()];
        for (int i = 0; i < plan.columns().size(); i++) {
            PlannedColumn column = plan.columns().get(i);
            valueAt[i] = -1;
            labelAt[i] = -1;
            if (column.masked()) {
                continue; // a sensitive attribute is never selected (SEC-1)
            }
            if (column.relation() != null) {
                Join<Object, Object> target = root.join(column.field().name(), JoinType.LEFT);
                valueAt[i] = selections.size();
                selections.add(target.get(column.relation().idAttribute()));
                if (column.relation().labelAttribute() != null) {
                    labelAt[i] = selections.size();
                    selections.add(target.get(column.relation().labelAttribute()));
                }
            } else if (column.field().kind() != AdminField.Kind.TO_ONE) {
                valueAt[i] = selections.size();
                selections.add(root.get(column.field().name()));
            }
        }
        select.multiselect(selections);

        Search search = Search.of(builder, root, plan.searchable(), query.search());
        if (search != null) {
            select.where(search.predicate());
        }
        orderBy(builder, select, root, plan, query);

        TypedQuery<Tuple> typed = entityManager.createQuery(select);
        bind(typed, search);
        typed.setFirstResult((int) Math.min((long) query.page() * query.size(), Integer.MAX_VALUE));
        typed.setMaxResults(query.size());

        List<ListView.Row> rows = new ArrayList<>();
        for (Tuple tuple : typed.getResultList()) {
            List<Cell> cells = new ArrayList<>(plan.columns().size());
            for (int i = 0; i < plan.columns().size(); i++) {
                cells.add(cell(plan.columns().get(i), tuple, valueAt[i], labelAt[i]));
            }
            rows.add(new ListView.Row(CellFormatter.id(tuple.get(0)), cells));
        }
        return rows;
    }

    private static Cell cell(PlannedColumn column, Tuple tuple, int valueAt, int labelAt) {
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
        return CellFormatter.format(column.column().type(), value);
    }

    /** Adds the sort, then the identifier ascending, so equal values never make the pages shift (stable paging). */
    private static void orderBy(CriteriaBuilder builder, CriteriaQuery<Tuple> select, Root<?> root, Plan plan,
                                ListQuery query) {
        var byId = builder.asc(root.get(plan.idAttribute()));
        if (query.sort() == null || plan.idAttribute().equals(query.sort())) {
            // Sorted by the identifier itself, or not sorted: the identifier alone is already a total order.
            select.orderBy(query.sort() != null && ListQuery.DESC.equals(query.dir())
                    ? builder.desc(root.get(plan.idAttribute())) : byId);
            return;
        }
        Path<?> sorted = root.get(query.sort());
        select.orderBy(ListQuery.DESC.equals(query.dir()) ? builder.desc(sorted) : builder.asc(sorted), byId);
    }

    private static Search bind(TypedQuery<?> typed, Search search) {
        if (search != null && search.parameter() != null) {
            typed.setParameter(search.parameter(), search.pattern());
        }
        return search;
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
                any.add(builder.like(builder.lower(root.<String>get(name)), parameter, ESCAPE));
            }
            return new Search(builder.or(any), parameter, "%" + escape(text.toLowerCase(Locale.ROOT)) + "%");
        }

        private static String escape(String text) {
            StringBuilder escaped = new StringBuilder(text.length() + 8);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ESCAPE || c == '%' || c == '_') {
                    escaped.append(ESCAPE);
                }
                escaped.append(c);
            }
            return escaped.toString();
        }
    }

    // ---- what an entity's list is made of ---------------------------------------------------------------------

    private Plan plan(AdminEntity entity) {
        List<ListColumn> listColumns = ListColumns.of(entity);
        List<PlannedColumn> columns = new ArrayList<>();
        List<String> searchable = new ArrayList<>();
        for (ListColumn column : listColumns) {
            AdminField field = entity.visibleField(column.key()).orElseThrow();
            Relation relation = column.type() == Cell.Type.RELATION ? relationOf(field) : null;
            columns.add(new PlannedColumn(column, field, relation));
            if (column.type() == Cell.Type.TEXT && field.kind() == AdminField.Kind.TEXT
                    && field.javaType() == String.class && !field.sensitive()) {
                searchable.add(field.name());
            }
        }
        return new Plan(entity.idAttribute(), List.copyOf(columns), List.copyOf(searchable));
    }

    /**
     * The identifier and the label of a to-one relation's target. {@code null} when the target has no single
     * identifier attribute to read; the cell is then empty rather than wrong.
     */
    private Relation relationOf(AdminField field) {
        EntityType<?> target;
        try {
            target = entityManager.getMetamodel().entity(field.javaType());
        } catch (IllegalArgumentException notAnEntity) {
            return null;
        }
        if (!target.hasSingleIdAttribute()) {
            return null;
        }
        String idAttribute = null;
        for (SingularAttribute<?, ?> attribute : target.getSingularAttributes()) {
            if (attribute.isId()) {
                idAttribute = attribute.getName();
            }
        }
        if (idAttribute == null) {
            return null;
        }
        String label = registry.find(field.targetEntity()).flatMap(ListQueryService::labelAttributeOf).orElse(null);
        return new Relation(idAttribute, label);
    }

    /** The attribute that names a record of {@code target} on screen, or empty when it has no usable text attribute. */
    static Optional<String> labelAttributeOf(AdminEntity target) {
        List<AdminField> texts = target.visibleFields().stream()
                .filter(field -> field.kind() == AdminField.Kind.TEXT && field.javaType() == String.class
                        && !field.sensitive() && !field.id())
                .toList();
        for (String preferred : PREFERRED_LABELS) {
            for (AdminField field : texts) {
                if (field.name().equalsIgnoreCase(preferred)) {
                    return Optional.of(field.name());
                }
            }
        }
        return texts.stream().map(AdminField::name).findFirst();
    }

    private record Relation(String idAttribute, String labelAttribute) {
    }

    private record PlannedColumn(ListColumn column, AdminField field, Relation relation) {

        boolean masked() {
            return column.type() == Cell.Type.MASKED;
        }
    }

    /** Everything about an entity's list that does not depend on a request. Built once per entity. */
    private record Plan(String idAttribute, List<PlannedColumn> columns, List<String> searchable) {

        List<ListColumn> listColumns() {
            return columns.stream().map(PlannedColumn::column).toList();
        }

        /** The query with any sort the entity cannot do removed (SEC-2), so only known names reach a query. */
        ListQuery checked(ListQuery query) {
            if (query.sort() == null) {
                return query;
            }
            boolean sortable = columns.stream().anyMatch(column -> column.column().sortable()
                    && column.column().key().equals(query.sort()));
            return sortable ? query : new ListQuery(query.search(), null, null, query.page(), query.size());
        }
    }
}
