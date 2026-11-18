package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.FormView.Field.InputType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PersistenceUnitUtil;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and changes records. With {@link DeleteService} the only writer of the console (ARCH-5: {@code @Transactional},
 * rolled back by any runtime exception, so an invalid record leaves nothing behind).
 *
 * <p><b>SEC-11 is enforced first</b>, before a posted value is looked at: an entity that is not the registry's is answered
 * like an unknown record ({@link RecordNotFoundException}); an entity that is in the console but is read-only, not creatable
 * (for a create) or not updatable (for an update) is refused ({@link ActionRefusedException}). A forged request for a
 * button the page never showed stops here and never reaches the database.
 *
 * <p><b>Mass assignment is impossible by construction</b>: the service asks the request only for the names of
 * {@link EditableFields} (taken from the metadata), through a function, so it never sees any other name. The identifier of an
 * existing record, the version, a hidden field, a collection and an unknown name are never read, so never stored.
 *
 * <p><b>Rules of a save</b> (D-75):
 * <ul>
 *   <li>A name that was not posted means <em>empty</em> on create and <em>unchanged</em> on update.</li>
 *   <li>An update writes only the fields whose posted text differs from the stored value's form text ({@link FormText}),
 *       so a record saved without changes is not dirtied and a value the form cannot show exactly is never rewritten.</li>
 *   <li>A sensitive field is never shown back. On update a blank value keeps the stored one; a typed value replaces it, and
 *       the console stores exactly what it is given (a setter in the host's entity may hash it). On create a blank value
 *       is "required" when the field is.</li>
 *   <li>A to-one relation takes the id of one of the target's choices (at most 200, D-63) and nothing else. A target with
 *       more records has no choices: a save that would change that field is refused for that field only, with a message;
 *       the stored value is kept otherwise.</li>
 *   <li>A version that does not match the stored one (or is missing, for an entity with {@code @Version}) means the form
 *       is stale: nothing is saved and the visitor is told to reopen the record. The database's own optimistic lock
 *       ({@link OptimisticLockException}) gives the same answer for a change that raced this one.</li>
 *   <li>The persistence layer has the last word: Bean Validation (when the host has a validator, Hibernate validates at
 *       insert and update) and the database's constraints can still refuse the record. Every such failure is turned into
 *       a plain sentence per field or for the whole form ({@link ConstraintMessages}, {@link FieldMessages}); no SQL,
 *       table name, constraint name or typed value reaches the page, the redirect, the flash or the log (SEC-1, SEC-12).</li>
 * </ul>
 *
 * <p>The entities exist only inside the methods and never leave them (ARCH-3).
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it and the controller uses it.
 */
public class RecordWriteService {

    private static final Logger log = LoggerFactory.getLogger(RecordWriteService.class);

    private final EntityManager entityManager;
    private final AdminEntityRegistry registry;
    private final Map<String, List<EditableFields.Editable>> plans = new ConcurrentHashMap<>();

    /** Not public, for the same reason as {@code ListQueryService}: Spring's transactional proxy subclasses this class. */
    protected RecordWriteService(EntityManager entityManager, AdminEntityRegistry registry) {
        this.entityManager = entityManager;
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * The usual service.
     *
     * @param entityManager the console's transaction-aware entity manager
     * @param registry      the entities the console manages
     */
    public static RecordWriteService over(EntityManager entityManager, AdminEntityRegistry registry) {
        return new RecordWriteService(Objects.requireNonNull(entityManager, "entityManager"), registry);
    }

    /** A service for a console that has no entity manager factory. Every entity is unknown to it. */
    public static RecordWriteService unavailable() {
        return new RecordWriteService(null, AdminEntityRegistry.empty());
    }

    /**
     * What a save reports, for the message to the visitor.
     *
     * @param id    the record's identifier as text
     * @param label the record's label on one line, or {@code null} when the entity has none
     */
    public record Saved(String id, String label) {
    }

    // ---- create ------------------------------------------------------------------------------------------------------

    /**
     * Creates a record.
     *
     * @param entity an entity of the registry
     * @param posted the request's value for a field name, or {@code null} when it was not sent; asked only for editable names
     * @throws RecordNotFoundException  if the entity is not offered by the console
     * @throws ActionRefusedException   if the entity is read-only or not creatable
     * @throws InvalidRecordException   if a value is wrong or the record was refused; nothing was saved
     */
    @Transactional
    public Saved create(AdminEntity entity, Function<String, String> posted) {
        requireOffered(entity);
        if (!entity.canCreate()) {
            throw new ActionRefusedException(); // SEC-11: before anything else, so the database is never asked
        }
        List<EditableFields.Editable> plan = plan(entity);
        Object record = instantiate(entity);
        Work work = new Work();
        for (EditableFields.Editable editable : plan) {
            String text = posted.apply(editable.key());
            apply(entity, editable, text == null ? "" : text, record, null, work, true);
        }
        work.failIfAny();
        try {
            entityManager.persist(record);
            entityManager.flush();
        } catch (RuntimeException failure) {
            throw refusal(entity, plan, failure);
        }
        return saved(entity, record);
    }

    // ---- update ------------------------------------------------------------------------------------------------------

    /**
     * Changes a record.
     *
     * @param entity      an entity of the registry
     * @param idText      the id as it came from the address; untrusted. The identifier never changes
     * @param versionText the version the form was opened with (from the form's address), or {@code null}
     * @param posted      the request's value for a field name, or {@code null} when it was not sent; asked only for editable names
     * @throws RecordNotFoundException  if the entity is not offered, the text is not an id, or there is no such record
     * @throws ActionRefusedException   if the entity is read-only or not updatable
     * @throws InvalidRecordException   if a value is wrong, the form is stale or the record was refused; nothing was saved
     */
    @Transactional
    public Saved update(AdminEntity entity, String idText, String versionText, Function<String, String> posted) {
        requireOffered(entity);
        if (!entity.addressable()) {
            throw new RecordNotFoundException();
        }
        if (!entity.canUpdate()) {
            throw new ActionRefusedException(); // SEC-11
        }
        Object id = IdConverter.convert(entity, idText).orElseThrow(RecordNotFoundException::new);
        Object record = entityManager.find(entity.type(), id);
        if (record == null) {
            throw new RecordNotFoundException();
        }
        List<EditableFields.Editable> plan = plan(entity);
        requireCurrentVersion(entity, record, versionText);
        Work work = new Work();
        for (EditableFields.Editable editable : plan) {
            if (editable.createOnly()) {
                continue; // the identifier of an existing record never changes
            }
            String text = posted.apply(editable.key());
            if (text != null) {
                apply(entity, editable, text, record, id, work, false);
            }
        }
        work.failIfAny();
        try {
            entityManager.flush();
        } catch (RuntimeException failure) {
            throw refusal(entity, plan, failure);
        }
        return saved(entity, record);
    }

    // ---- one field -----------------------------------------------------------------------------------------------------

    private void apply(AdminEntity entity, EditableFields.Editable editable, String rawText, Object record, Object id,
                       Work work, boolean creating) {
        AdminField field = editable.field();
        if (editable.createOnly() && rawText.isBlank()) {
            work.fieldErrors.put(field.name(), FieldMessages.required()); // an identifier the application assigns is required
            return;
        }
        String text = isText(editable) ? normalizeLineBreaks(rawText) : rawText;
        if (editable.input() == InputType.MASKED && text.isBlank()
                && !creating) {
            return; // a blank secret keeps the stored one (SEC-1)
        }
        if (editable.relation() != null) {
            relation(entity, editable, text, record, work, creating);
            return;
        }
        if (!creating && editable.input() != InputType.MASKED
                && text.equals(nullToEmpty(FormText.of(Members.get(record, field.name()))))) {
            return; // unchanged: not written, so not dirtied
        }
        ValueConverter.Result result = ValueConverter.convert(field, text, editable.maxLength());
        if (result.failed()) {
            work.fieldErrors.put(field.name(), result.error());
            return;
        }
        if (result.value() == null && field.javaType().isPrimitive()) {
            work.fieldErrors.put(field.name(), FieldMessages.required());
            return;
        }
        Members.set(record, field.name(), result.value());
    }

    private void relation(AdminEntity entity, EditableFields.Editable editable, String text, Object record, Work work,
                          boolean creating) {
        AdminField field = editable.field();
        String chosen = text.strip();
        PersistenceUnitUtil util = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        if (!creating) {
            Object current = Members.get(record, field.name());
            String currentId = current == null ? "" : CellFormatter.id(util.getIdentifier(current));
            if (chosen.equals(currentId)) {
                return; // unchanged
            }
        }
        if (chosen.isEmpty()) {
            if (field.required()) {
                work.fieldErrors.put(field.name(), FieldMessages.required());
            } else {
                Members.set(record, field.name(), null);
            }
            return;
        }
        RelationChoices choices = work.choices.computeIfAbsent(field.name(),
                key -> RelationChoices.read(entityManager, editable.relation()));
        if (choices.overTheBound()) {
            work.fieldErrors.put(field.name(), FieldMessages.tooManyChoices(field.label(), RelationChoices.BOUND));
            return;
        }
        Object target = choices.ids().get(chosen);
        if (target == null) {
            work.fieldErrors.put(field.name(), FieldMessages.chooseExisting());
            return;
        }
        Members.set(record, field.name(), entityManager.getReference(editable.relation().targetType(), target));
    }

    private static boolean isText(EditableFields.Editable editable) {
        return editable.field().kind() == AdminField.Kind.TEXT && editable.field().javaType() == String.class;
    }

    /** A browser sends a line break in a text area as CR LF; the stored text uses LF. */
    private static String normalizeLineBreaks(String text) {
        return text.indexOf('\r') < 0 ? text : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    // ---- version, instance, result ---------------------------------------------------------------------------------------

    private static void requireCurrentVersion(AdminEntity entity, Object record, String versionText) {
        Optional<AdminField> version = entity.fields().stream().filter(AdminField::version).findFirst();
        if (version.isEmpty()) {
            return;
        }
        Object current = Members.get(record, version.get().name());
        if (versionText == null || current == null || !current.toString().equals(versionText)) {
            throw new InvalidRecordException(Map.of(), List.of(FieldMessages.stale()));
        }
    }

    private Object instantiate(AdminEntity entity) {
        try {
            Constructor<?> constructor = entity.type().getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException cannot) {
            log.warn("The JPA Admin console cannot create a record of '{}': the entity needs a constructor without "
                    + "arguments.", entity.urlName());
            throw new InvalidRecordException(Map.of(), List.of(FieldMessages.refused()));
        }
    }

    private Saved saved(AdminEntity entity, Object record) {
        PersistenceUnitUtil util = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        String id = CellFormatter.id(util.getIdentifier(record));
        String label = null;
        String labelAttribute = ListColumns.labelAttributeOf(entity).orElse(null);
        if (labelAttribute != null) {
            Object value = Members.get(record, labelAttribute);
            if (value != null) {
                Cell cell = CellFormatter.text(value.toString());
                label = cell.type() == Cell.Type.EMPTY ? null : cell.display();
            }
        }
        return new Saved(id, label);
    }

    // ---- what the persistence layer refused ----------------------------------------------------------------------------------

    /**
     * Turns a failure of {@code persist} or {@code flush} into an {@link InvalidRecordException}. A Bean Validation violation
     * is attached to its field when the field is editable (else it is a sentence for the whole form, which does not name the
     * property); an optimistic lock is a stale form; any other persistence failure is one sentence for the form. The log gets
     * the entity's address and the kinds of exception, never a message (the provider's message holds SQL and values).
     * Anything that is none of these is not about the data and is rethrown as it is.
     */
    private InvalidRecordException refusal(AdminEntity entity, List<EditableFields.Editable> plan, RuntimeException failure) {
        List<ConstraintMessages.Violation> violations = ConstraintMessages.violationsOf(failure);
        boolean stale = false;
        boolean persistence = false;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            stale |= current instanceof OptimisticLockException;
            persistence |= current instanceof PersistenceException;
        }
        if (violations.isEmpty() && !stale && !persistence) {
            throw failure;
        }
        log.warn("The JPA Admin console could not save a record of '{}' ({}); nothing was saved.", entity.urlName(),
                kinds(failure));
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        List<String> formErrors = new ArrayList<>();
        if (stale) {
            formErrors.add(FieldMessages.stale());
        }
        boolean unnamed = false;
        for (ConstraintMessages.Violation violation : violations) {
            boolean editable = plan.stream().anyMatch(candidate -> candidate.key().equals(violation.property()));
            if (editable) {
                fieldErrors.putIfAbsent(violation.property(), violation.message());
            } else {
                unnamed = true;
            }
        }
        if (unnamed || (!stale && violations.isEmpty())) {
            formErrors.add(FieldMessages.refused());
        }
        return new InvalidRecordException(fieldErrors, formErrors);
    }

    private static String kinds(Throwable failure) {
        List<String> names = new ArrayList<>();
        int depth = 0;
        for (Throwable current = failure; current != null && depth < 5; current = current.getCause(), depth++) {
            names.add(current.getClass().getSimpleName());
        }
        return String.join(" <- ", names);
    }

    // ---- shared ------------------------------------------------------------------------------------------------------------

    private void requireOffered(AdminEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (entityManager == null || registry.find(entity.urlName()).filter(entity::equals).isEmpty()) {
            throw new RecordNotFoundException();
        }
    }

    private List<EditableFields.Editable> plan(AdminEntity entity) {
        return plans.computeIfAbsent(entity.urlName(), key -> EditableFields.of(entity, registry, entityManager));
    }

    /** What one save collects: the errors, and the choices already read for a relation. */
    private static final class Work {
        private final Map<String, String> fieldErrors = new LinkedHashMap<>();
        private final Map<String, RelationChoices> choices = new java.util.HashMap<>();

        void failIfAny() {
            if (!fieldErrors.isEmpty()) {
                throw new InvalidRecordException(fieldErrors, List.of());
            }
        }
    }
}
