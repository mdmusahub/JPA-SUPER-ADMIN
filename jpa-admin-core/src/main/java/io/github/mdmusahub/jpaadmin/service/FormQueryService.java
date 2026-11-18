package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.FormView.Field.InputType;
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
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads what a create or edit form needs, in one read-only transaction (ARCH-5): the fields of {@link EditableFields} with
 * their inputs, the choices of a select, and the starting values.
 *
 * <p><b>The typed read of a record is one statement</b> that selects <em>attributes</em>: the identifier, the version, the
 * label, and for each editable field its value (for a to-one relation the target's identifier, through a {@code LEFT JOIN}).
 * It never selects an entity, a collection, a hidden attribute or a <b>sensitive</b> attribute (SEC-1): a masked field has
 * no value to read. So no entity or proxy exists whatever the host's open-in-view setting is (D-29). The choices of a
 * relation are one more small statement each, and a target with more than {@value RelationChoices#BOUND} records has none
 * (D-63): the field is then a notice, and a save that would change it is refused for that field only (D-75).
 *
 * <p>The starting values of a create form are the entity's own defaults, read from a fresh instance of its class (never
 * from the database); the starting values of a duplicate are the {@link CopySeed}.
 *
 * <p>SEC-11 is checked here as well as at save time: a form for something the console may not do is refused
 * ({@link ActionRefusedException}, or {@link RecordNotFoundException} for an entity the console does not offer).
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it and the controller uses it.
 */
public class FormQueryService {

    private final EntityManager entityManager;
    private final AdminEntityRegistry registry;
    private final Map<String, List<EditableFields.Editable>> plans = new ConcurrentHashMap<>();

    /** Not public, for the same reason as {@code ListQueryService}: Spring's transactional proxy subclasses this class. */
    protected FormQueryService(EntityManager entityManager, AdminEntityRegistry registry) {
        this.entityManager = entityManager;
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * The usual service.
     *
     * @param entityManager the console's transaction-aware entity manager
     * @param registry      the entities the console manages
     */
    public static FormQueryService over(EntityManager entityManager, AdminEntityRegistry registry) {
        return new FormQueryService(Objects.requireNonNull(entityManager, "entityManager"), registry);
    }

    /** A service for a console that has no entity manager factory. Every entity is unknown to it. */
    public static FormQueryService unavailable() {
        return new FormQueryService(null, AdminEntityRegistry.empty());
    }

    /**
     * The empty create form, with the entity's own defaults.
     *
     * @throws RecordNotFoundException if the entity is not offered by the console
     * @throws ActionRefusedException  if the entity may not be created in (SEC-11)
     */
    @Transactional(readOnly = true)
    public FormPage blank(AdminEntity entity) {
        requireCreatable(entity);
        List<EditableFields.Editable> plan = plan(entity);
        Map<String, String> defaults = defaults(entity, plan);
        return new FormPage(null, null, null, fields(plan, true, editable -> defaults.get(editable.key())));
    }

    /**
     * The create form of a duplicate: only the values of the {@link CopySeed} (the rule of what is copied lives in
     * {@code DetailQueryService}); every other field starts empty, a masked field always.
     *
     * @throws RecordNotFoundException if the entity is not offered by the console
     * @throws ActionRefusedException  if the entity may not be created in (SEC-11)
     */
    @Transactional(readOnly = true)
    public FormPage copyOf(AdminEntity entity, CopySeed seed) {
        requireCreatable(entity);
        Objects.requireNonNull(seed, "seed");
        Map<String, String> values = new HashMap<>();
        seed.values().forEach(value -> values.put(value.key(), value.text()));
        List<EditableFields.Editable> plan = plan(entity);
        return new FormPage(null, null, null, fields(plan, true, editable -> values.get(editable.key())));
    }

    /**
     * The edit form of one record.
     *
     * @param idText the id as it came from the address; untrusted
     * @return the form, or empty when the text is not an id, the entity is not offered or has no address, or there is no such record
     * @throws ActionRefusedException if the entity may not be changed in the console (SEC-11)
     */
    @Transactional(readOnly = true)
    public Optional<FormPage> record(AdminEntity entity, String idText) {
        requireUpdatable(entity);
        Optional<Object> id = IdConverter.convert(entity, idText);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        List<EditableFields.Editable> plan = plan(entity);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> select = builder.createTupleQuery();
        Root<?> root = select.from(entity.type());

        List<Selection<?>> selections = new ArrayList<>();
        selections.add(root.get(entity.idAttribute()));
        int versionAt = -1;
        AdminField version = entity.fields().stream().filter(AdminField::version).findFirst().orElse(null);
        if (version != null) {
            versionAt = selections.size();
            selections.add(root.get(version.name()));
        }
        int labelAt = -1;
        String labelAttribute = ListColumns.labelAttributeOf(entity).orElse(null);
        if (labelAttribute != null) {
            labelAt = selections.size();
            selections.add(root.get(labelAttribute));
        }
        int[] valueAt = new int[plan.size()];
        for (int i = 0; i < plan.size(); i++) {
            EditableFields.Editable editable = plan.get(i);
            valueAt[i] = -1;
            if (editable.field().sensitive() || editable.createOnly()) {
                continue; // never selected: a sensitive value (SEC-1), an identifier that cannot be edited
            }
            valueAt[i] = selections.size();
            if (editable.relation() != null) {
                Join<Object, Object> target = root.join(editable.key(), JoinType.LEFT);
                selections.add(target.get(editable.relation().idAttribute()));
            } else {
                selections.add(root.get(editable.key()));
            }
        }
        select.multiselect(selections);
        ParameterExpression<?> parameter = builder.parameter(IdConverter.parameterType(entity));
        select.where(builder.equal(root.get(entity.idAttribute()), parameter));
        TypedQuery<Tuple> typed = entityManager.createQuery(select);
        bind(typed, parameter, id.get());
        typed.setMaxResults(1);
        List<Tuple> found = typed.getResultList();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Tuple tuple = found.get(0);
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < plan.size(); i++) {
            if (valueAt[i] >= 0 && tuple.get(valueAt[i]) != null) {
                values.put(plan.get(i).key(), plan.get(i).relation() != null ? CellFormatter.id(tuple.get(valueAt[i]))
                        : FormText.of(tuple.get(valueAt[i])));
            }
        }
        String label = null;
        if (labelAt >= 0 && tuple.get(labelAt) != null) {
            Cell named = CellFormatter.text(tuple.get(labelAt).toString());
            label = named.type() == Cell.Type.EMPTY ? null : named.display();
        }
        String versionText = versionAt >= 0 && tuple.get(versionAt) != null ? tuple.get(versionAt).toString() : null;
        return Optional.of(new FormPage(CellFormatter.id(tuple.get(0)), versionText, label,
                fields(plan, false, editable -> values.get(editable.key()))));
    }

    // ---- the fields --------------------------------------------------------------------------------------------------

    private List<FormView.Field> fields(List<EditableFields.Editable> plan, boolean creating,
                                        Function<EditableFields.Editable, String> valueOf) {
        List<FormView.Field> fields = new ArrayList<>(plan.size());
        for (EditableFields.Editable editable : plan) {
            if (editable.createOnly() && !creating) {
                continue; // the identifier of an existing record never changes
            }
            fields.add(field(editable, creating, valueOf.apply(editable)));
        }
        return fields;
    }

    private FormView.Field field(EditableFields.Editable editable, boolean creating, String value) {
        AdminField field = editable.field();
        InputType input = editable.input();
        boolean required = switch (input) {
            case MASKED -> creating && field.required();
            case BOOLEAN -> false;
            default -> editable.createOnly() || field.required();
        };
        String help = null;
        List<FormView.Option> options = List.of();
        if (input == InputType.MASKED) {
            help = creating ? null : "Leave blank to keep the current value.";
            value = null;
        } else if (editable.createOnly()) {
            help = "Identifier. It cannot be changed after the record is created.";
        } else if (editable.relation() != null) {
            RelationChoices choices = RelationChoices.read(entityManager, editable.relation());
            if (choices.overTheBound()) {
                help = FieldMessages.tooManyChoicesNotice(RelationChoices.BOUND);
            } else if (choices.options().isEmpty() && field.required()) {
                help = FieldMessages.noChoicesNotice();
            } else {
                options = withBlank(choices.options(), field.required());
            }
        } else if (field.kind() == AdminField.Kind.ENUM) {
            List<FormView.Option> constants = field.enumValues().stream()
                    .map(constant -> new FormView.Option(constant, CellFormatter.humanize(constant))).toList();
            options = withBlank(constants, field.required());
        }
        return new FormView.Field(field.name(), field.label(), input, value, required, help, options, null, null);
    }

    /** Every select starts with a blank choice: "None" when the value is optional, "Choose..." when it is required. */
    private static List<FormView.Option> withBlank(List<FormView.Option> choices, boolean required) {
        List<FormView.Option> options = new ArrayList<>(choices.size() + 1);
        options.add(new FormView.Option("", required ? "Choose..." : "None"));
        options.addAll(choices);
        return options;
    }

    /** The entity's defaults: the form text of each editable field of a fresh instance. Nothing if it cannot be made. */
    private Map<String, String> defaults(AdminEntity entity, List<EditableFields.Editable> plan) {
        Map<String, String> defaults = new HashMap<>();
        Object fresh;
        try {
            Constructor<?> constructor = entity.type().getDeclaredConstructor();
            constructor.setAccessible(true);
            fresh = constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException cannot) {
            return defaults;
        }
        for (EditableFields.Editable editable : plan) {
            if (editable.field().sensitive() || editable.relation() != null) {
                continue;
            }
            try {
                String text = FormText.of(Members.get(fresh, editable.key()));
                if (text != null) {
                    defaults.put(editable.key(), text);
                }
            } catch (RuntimeException unreadable) {
                // No default for this field.
            }
        }
        return defaults;
    }

    // ---- shared ------------------------------------------------------------------------------------------------------

    /**
     * SEC-11 for a form that creates: the check every create form passes first, before anything is read. Public so the
     * controller can run it before it looks at a duplicate's source.
     *
     * @throws RecordNotFoundException if the entity is not offered by the console
     * @throws ActionRefusedException  if the entity may not be created in
     */
    public void requireCreatable(AdminEntity entity) {
        requireOffered(entity);
        if (!entity.canCreate()) {
            throw new ActionRefusedException();
        }
    }

    private void requireUpdatable(AdminEntity entity) {
        requireOffered(entity);
        if (!entity.addressable()) {
            throw new RecordNotFoundException();
        }
        if (!entity.canUpdate()) {
            throw new ActionRefusedException();
        }
    }

    private void requireOffered(AdminEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (entityManager == null || registry.find(entity.urlName()).filter(entity::equals).isEmpty()) {
            throw new RecordNotFoundException();
        }
    }

    private List<EditableFields.Editable> plan(AdminEntity entity) {
        return plans.computeIfAbsent(entity.urlName(), key -> EditableFields.of(entity, registry, entityManager));
    }

    @SuppressWarnings("unchecked")
    private static void bind(TypedQuery<?> typed, ParameterExpression<?> parameter, Object value) {
        typed.setParameter((Parameter<Object>) parameter, value);
    }
}
