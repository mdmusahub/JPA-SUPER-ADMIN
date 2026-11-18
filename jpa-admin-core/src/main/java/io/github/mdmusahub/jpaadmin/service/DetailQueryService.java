package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads one record for the detail page, and the starting values of a duplicate.
 *
 * <p>One Criteria query per read, in one read-only transaction (ARCH-5): the page costs one statement however many
 * relations the entity has, because a to-one relation is a {@code LEFT JOIN} that selects the target's identifier and
 * label in the same statement (ARCH-6, the rule of the list, D-54). The query selects <em>attributes</em>, never an
 * entity, so no entity, proxy or collection exists (ARCH-3, ARCH-4), whatever the host's open-in-view setting is (D-29).
 * It never calls the filter-choice query.
 *
 * <p>What a record holds (SEC-1, SEC-2):
 * <ul>
 *   <li>A sensitive attribute is <b>not selected at all</b>; its item is {@link Cell#masked()}.</li>
 *   <li>A hidden attribute is absent. A collection, an embedded value and binary data are not selected either; the page
 *       shows the field's label with "(not shown)".</li>
 *   <li>The id comes from the address and is converted by {@link IdConverter}; text that is not an id, an id that has no
 *       record, an entity that is not in the console and an entity whose identifier is sensitive all give the same
 *       answer: nothing (the caller answers one console 404, SEC-2). The converted id is a bound parameter (SEC-3).</li>
 * </ul>
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it and the controller uses it.
 */
public class DetailQueryService {

    private final EntityManager entityManager;
    private final AdminEntityRegistry registry;
    private final UploadFiles uploads;
    private final Map<String, Plan> plans = new ConcurrentHashMap<>();

    /** Not public, for the same reason as {@code ListQueryService}: Spring's transactional proxy subclasses this class. */
    protected DetailQueryService(EntityManager entityManager, AdminEntityRegistry registry) {
        this(entityManager, registry, UploadFiles.none());
    }

    protected DetailQueryService(EntityManager entityManager, AdminEntityRegistry registry, UploadFiles uploads) {
        this.entityManager = entityManager;
        this.registry = Objects.requireNonNull(registry, "registry");
        this.uploads = Objects.requireNonNull(uploads, "uploads");
    }

    /**
     * The usual service.
     *
     * @param entityManager the console's transaction-aware entity manager
     * @param registry      the entities the console manages
     */
    public static DetailQueryService over(EntityManager entityManager, AdminEntityRegistry registry) {
        return new DetailQueryService(Objects.requireNonNull(entityManager, "entityManager"), registry);
    }

    /**
     * The service with upload support: an upload field is an image or a file link (Phase 9b, D-99).
     *
     * @param uploads what the pages need to know about uploaded files
     */
    public static DetailQueryService over(EntityManager entityManager, AdminEntityRegistry registry, UploadFiles uploads) {
        return new DetailQueryService(Objects.requireNonNull(entityManager, "entityManager"), registry, uploads);
    }

    /** A service for a console that has no entity manager factory. It finds nothing. */
    public static DetailQueryService unavailable() {
        return new DetailQueryService(null, AdminEntityRegistry.empty());
    }

    /**
     * One record, as display values.
     *
     * @param entity an entity of the registry; any other entity is answered with nothing
     * @param idText the id as it came from the address; untrusted
     * @return the record, or empty when there is none, the text is not an id, or the entity is not offered
     */
    @Transactional(readOnly = true)
    public Optional<DetailRecord> find(AdminEntity entity, String idText) {
        Optional<Object> id = idOf(entity, idText);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Plan plan = plans.computeIfAbsent(entity.urlName(), key -> plan(entity));
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> select = builder.createTupleQuery();
        Root<?> root = select.from(entity.type());

        List<Selection<?>> selections = new ArrayList<>();
        selections.add(root.get(plan.idAttribute()));
        int labelAt = -1;
        if (plan.labelAttribute() != null) {
            labelAt = selections.size();
            selections.add(root.get(plan.labelAttribute()));
        }
        int[] valueAt = new int[plan.fields().size()];
        int[] relationLabelAt = new int[plan.fields().size()];
        for (int i = 0; i < plan.fields().size(); i++) {
            Planned planned = plan.fields().get(i);
            AdminField field = planned.field();
            valueAt[i] = -1;
            relationLabelAt[i] = -1;
            if (field.sensitive() || planned.column() == null) {
                continue; // never selected: a sensitive value (SEC-1), a collection, an embedded value, binary data
            }
            if (field.id()) {
                valueAt[i] = 0;
            } else if (planned.relation() != null) {
                Join<Object, Object> target = root.join(field.name(), JoinType.LEFT);
                valueAt[i] = selections.size();
                selections.add(target.get(planned.relation().idAttribute()));
                if (planned.relation().labelAttribute() != null) {
                    relationLabelAt[i] = selections.size();
                    selections.add(target.get(planned.relation().labelAttribute()));
                }
            } else if (field.kind() != AdminField.Kind.TO_ONE) {
                valueAt[i] = selections.size();
                selections.add(root.get(field.name()));
            }
        }
        select.multiselect(selections);
        ParameterExpression<?> parameter = builder.parameter(IdConverter.parameterType(entity));
        select.where(builder.equal(root.get(plan.idAttribute()), parameter));
        TypedQuery<Tuple> typed = entityManager.createQuery(select);
        bind(typed, parameter, id.get());
        typed.setMaxResults(1);

        List<Tuple> found = typed.getResultList();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Tuple tuple = found.get(0);
        List<DetailRecord.Item> items = new ArrayList<>(plan.fields().size());
        for (int i = 0; i < plan.fields().size(); i++) {
            items.add(item(plan.fields().get(i), tuple, valueAt[i], relationLabelAt[i]));
        }
        String label = null;
        if (labelAt >= 0 && tuple.get(labelAt) != null) {
            Cell named = CellFormatter.text(tuple.get(labelAt).toString());
            label = named.type() == Cell.Type.EMPTY ? null : named.display();
        }
        return Optional.of(new DetailRecord(CellFormatter.id(tuple.get(0)), label, items));
    }

    private DetailRecord.Item item(Planned planned, Tuple tuple, int valueAt, int relationLabelAt) {
        AdminField field = planned.field();
        String label = planned.column() != null ? planned.column().label() : field.label();
        if (field.sensitive()) {
            return DetailRecord.Item.of(label, Cell.masked());
        }
        if (planned.column() == null) {
            return DetailRecord.Item.of(label, CellFormatter.notShown());
        }
        if (field.kind() == AdminField.Kind.TO_ONE) {
            if (planned.relation() == null || valueAt < 0 || tuple.get(valueAt) == null) {
                return DetailRecord.Item.of(label, Cell.empty());
            }
            Object target = tuple.get(valueAt);
            Cell cell = CellFormatter.relation(relationLabelAt < 0 ? null : tuple.get(relationLabelAt), target);
            return new DetailRecord.Item(label, cell, field.targetEntity(), CellFormatter.id(target));
        }
        Object value = valueAt < 0 ? null : tuple.get(valueAt);
        if (value == null) {
            return DetailRecord.Item.of(label, Cell.empty());
        }
        if (field.isUpload() && value instanceof String url) {
            Optional<Cell> shown = uploads.cell(url, label);
            if (shown.isPresent()) {
                return DetailRecord.Item.of(label, shown.get());
            }
        }
        Cell cell = planned.column().type() == Cell.Type.TEXT && value instanceof CharSequence text
                ? CellFormatter.detailText(text.toString())
                : CellFormatter.format(planned.column().type(), value);
        return DetailRecord.Item.of(label, cell);
    }

    /**
     * The starting values of a duplicate of one record (see {@link CopySeed}). Nothing sensitive is ever read, so nothing
     * sensitive can be copied (SEC-1).
     *
     * @param entity an entity of the registry; any other entity is answered with nothing
     * @param idText the id of the record to copy, as it came from the address; untrusted
     * @return the seed, or empty when there is no such record, the text is not an id, or the entity is not offered
     */
    @Transactional(readOnly = true)
    public Optional<CopySeed> copySeed(AdminEntity entity, String idText) {
        Optional<Object> id = idOf(entity, idText);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Plan plan = plans.computeIfAbsent(entity.urlName(), key -> plan(entity));
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> select = builder.createTupleQuery();
        Root<?> root = select.from(entity.type());

        List<Selection<?>> selections = new ArrayList<>();
        selections.add(root.get(plan.idAttribute()));
        List<Planned> copied = new ArrayList<>();
        for (Planned planned : plan.fields()) {
            if (!copyable(planned)) {
                continue;
            }
            copied.add(planned);
            if (planned.field().kind() == AdminField.Kind.TO_ONE) {
                Join<Object, Object> target = root.join(planned.field().name(), JoinType.LEFT);
                selections.add(target.get(planned.relation().idAttribute()));
            } else {
                selections.add(root.get(planned.field().name()));
            }
        }
        select.multiselect(selections);
        ParameterExpression<?> parameter = builder.parameter(IdConverter.parameterType(entity));
        select.where(builder.equal(root.get(plan.idAttribute()), parameter));
        TypedQuery<Tuple> typed = entityManager.createQuery(select);
        bind(typed, parameter, id.get());
        typed.setMaxResults(1);

        List<Tuple> found = typed.getResultList();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Tuple tuple = found.get(0);
        List<CopySeed.Value> values = new ArrayList<>();
        for (int i = 0; i < copied.size(); i++) {
            Object value = tuple.get(i + 1);
            if (value != null) {
                values.add(new CopySeed.Value(copied.get(i).field().name(), FormText.of(value)));
            }
        }
        return Optional.of(new CopySeed(CellFormatter.id(tuple.get(0)), values));
    }

    /**
     * Whether a field's value is copied into a duplicate. One place for the rule (SEC-1): the identifier and the version
     * identify this record, not the copy; a sensitive field is never read; a collection, an embedded value, binary data
     * and a type the forms cannot edit are left out; a relation is copied only when its target is in the console.
     * <b>Phase 9 adds {@code @AdminUpload} fields here</b>: a stored file must not be shared by two records, so an
     * upload field is never copied.
     */
    private static boolean copyable(Planned planned) {
        AdminField field = planned.field();
        if (field.id() || field.version() || field.sensitive() || field.hidden() || planned.column() == null) {
            return false;
        }
        if (field.isUpload()) {
            // D-73: a copy never carries a stored file, so two records never share one file (and a delete or a replace of
            // one record can never touch the other's).
            return false;
        }
        return switch (field.kind()) {
            case TEXT, NUMBER, BOOLEAN, ENUM, DATE, DATETIME -> true;
            case TO_ONE -> field.targetEntity() != null && planned.relation() != null;
            case TO_MANY, EMBEDDED, BINARY, OTHER -> false;
        };
    }

    // ---- shared ---------------------------------------------------------------------------------------------------

    /**
     * The converted id, or nothing for every case that must look the same from outside: no entity manager, an entity that
     * is not the registry's, an entity whose records have no address, and a text that is not an id (SEC-2).
     */
    private Optional<Object> idOf(AdminEntity entity, String idText) {
        Objects.requireNonNull(entity, "entity");
        if (entityManager == null || !registry.find(entity.urlName()).filter(entity::equals).isPresent()
                || !entity.addressable()) {
            return Optional.empty();
        }
        return IdConverter.convert(entity, idText);
    }

    @SuppressWarnings("unchecked")
    private static void bind(TypedQuery<?> typed, ParameterExpression<?> parameter, Object value) {
        typed.setParameter((Parameter<Object>) parameter, value);
    }

    private Plan plan(AdminEntity entity) {
        Map<String, ListColumn> columns = ListColumns.of(entity, registry).stream()
                .collect(Collectors.toMap(ListColumn::key, Function.identity()));
        List<Planned> fields = new ArrayList<>();
        for (AdminField field : entity.visibleFields()) {
            ListColumn column = columns.get(field.name());
            Relation relation = column != null && column.type() == Cell.Type.RELATION
                    ? Relation.of(entityManager, registry, field) : null;
            fields.add(new Planned(field, column, relation));
        }
        return new Plan(entity.idAttribute(), ListColumns.labelAttributeOf(entity).orElse(null), List.copyOf(fields));
    }

    /** A visible field with its column (null for a collection, an embedded value or binary data) and relation. */
    private record Planned(AdminField field, ListColumn column, Relation relation) {
    }

    private record Plan(String idAttribute, String labelAttribute, List<Planned> fields) {
    }
}
