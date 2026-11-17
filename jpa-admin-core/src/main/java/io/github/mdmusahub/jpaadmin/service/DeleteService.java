package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Parameter;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes one record, or several in one go. The only writer of Phase 7 (ARCH-5: {@code @Transactional}, rolled back on
 * any runtime exception).
 *
 * <p><b>SEC-11 is enforced here</b>, not only in the pages: an entity that is not the registry's, or whose identifier is a
 * sensitive field, is answered like an unknown record ({@link RecordNotFoundException}); an entity that is in the console
 * but is read-only or not deletable is refused ({@link ActionRefusedException}). A forged request for a button the page
 * never showed lands here and stops here.
 *
 * <p>The ids come from outside, so every one goes through {@link IdConverter} and reaches a query as a bound parameter
 * (SEC-3). The records are removed through the persistence provider ({@code EntityManager#remove}), so the host's own
 * mapping decides what follows (cascades, {@code @PreRemove}), exactly as when its code deletes the record. The entities
 * exist only inside the method and never leave it (ARCH-3).
 *
 * <p>A database that refuses the delete (a foreign key still points at the record, most often) is reported as
 * {@link RecordInUseException}: the exception rolls the transaction back, so <b>a bulk delete is all or nothing</b>. The
 * exception holds no SQL, table name or value (SEC-12).
 *
 * <p>Internal, not stable API. Public because the auto-configuration module creates it and the controller uses it.
 */
public class DeleteService {

    /** The most records one bulk delete may remove. Equal to the largest page, so the page's own checkboxes never exceed it. */
    public static final int MAX_BULK_DELETE = 100;

    private static final Logger log = LoggerFactory.getLogger(DeleteService.class);

    private final EntityManager entityManager;
    private final AdminEntityRegistry registry;

    /** Not public, for the same reason as {@code ListQueryService}: Spring's transactional proxy subclasses this class. */
    protected DeleteService(EntityManager entityManager, AdminEntityRegistry registry) {
        this.entityManager = entityManager;
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * The usual service.
     *
     * @param entityManager the console's transaction-aware entity manager
     * @param registry      the entities the console manages
     */
    public static DeleteService over(EntityManager entityManager, AdminEntityRegistry registry) {
        return new DeleteService(Objects.requireNonNull(entityManager, "entityManager"), registry);
    }

    /** A service for a console that has no entity manager factory. Every entity is unknown to it. */
    public static DeleteService unavailable() {
        return new DeleteService(null, AdminEntityRegistry.empty());
    }

    /**
     * What a delete reports: the label of the record that is gone, for the message to the visitor.
     *
     * @param label the record's label on one line, or {@code null} when the entity has none
     */
    public record Deleted(String label) {
    }

    /**
     * What a bulk delete reports.
     *
     * @param deleted    how many records were deleted
     * @param notDeleted how many of the distinct ids sent were not deleted: not an id, or no such record
     */
    public record BulkResult(int deleted, int notDeleted) {
    }

    /**
     * Deletes one record.
     *
     * @param entity an entity of the registry
     * @param idText the id as it came from the address; untrusted
     * @throws RecordNotFoundException if the entity is not offered, the text is not an id, or there is no such record
     * @throws ActionRefusedException  if the entity is read-only or not deletable
     * @throws RecordInUseException    if the database refused the delete; nothing was deleted
     */
    @Transactional
    public Deleted delete(AdminEntity entity, String idText) {
        requireDeletable(entity);
        Object id = IdConverter.convert(entity, idText).orElseThrow(RecordNotFoundException::new);
        Optional<String> label = label(entity, id);
        if (label == null) {
            throw new RecordNotFoundException();
        }
        Object record = entityManager.find(entity.type(), id);
        if (record == null) {
            throw new RecordNotFoundException();
        }
        try {
            entityManager.remove(record);
            entityManager.flush();
        } catch (PersistenceException refused) {
            throw refusal(entity, refused);
        }
        return new Deleted(label.orElse(null));
    }

    /**
     * Deletes the records whose ids were sent, in one transaction: either all of them are deleted or none. An id that is not
     * an id, or has no record, is skipped and counted; it is not a failure. The same id sent twice counts once.
     *
     * @param entity  an entity of the registry
     * @param request the ids as sent, at most {@value #MAX_BULK_DELETE}
     * @throws RecordNotFoundException if the entity is not offered
     * @throws ActionRefusedException  if the entity is read-only or not deletable
     * @throws TooManyRecordsException if more than {@value #MAX_BULK_DELETE} ids were sent (checked again here, although
     *                                 {@link BulkDeleteIds} already refuses it)
     * @throws RecordInUseException    if the database refused the delete; nothing was deleted
     */
    @Transactional
    public BulkResult deleteMany(AdminEntity entity, BulkDeleteIds request) {
        requireDeletable(entity);
        Objects.requireNonNull(request, "request");
        if (request.size() > MAX_BULK_DELETE) {
            throw new TooManyRecordsException(MAX_BULK_DELETE);
        }
        Set<Object> ids = new LinkedHashSet<>();
        Set<String> texts = new LinkedHashSet<>(request.ids());
        int invalid = 0;
        for (String text : texts) {
            Optional<Object> id = IdConverter.convert(entity, text);
            if (id.isPresent()) {
                ids.add(id.get());
            } else {
                invalid++;
            }
        }
        int distinct = ids.size() + invalid;
        if (ids.isEmpty()) {
            return new BulkResult(0, distinct);
        }
        List<Object> records = find(entity, ids);
        try {
            for (Object record : records) {
                entityManager.remove(record);
            }
            entityManager.flush();
        } catch (PersistenceException refused) {
            throw refusal(entity, refused);
        }
        return new BulkResult(records.size(), distinct - records.size());
    }

    // ---- shared ---------------------------------------------------------------------------------------------------

    /** SEC-11: the check every write passes first, before any id is looked at. */
    private void requireDeletable(AdminEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (entityManager == null || registry.find(entity.urlName()).filter(entity::equals).isEmpty()
                || !entity.addressable()) {
            throw new RecordNotFoundException();
        }
        if (!entity.canDelete()) {
            throw new ActionRefusedException();
        }
    }

    /**
     * The label of the record with this id (one line), and whether it exists: {@code null} when there is no such record,
     * an empty optional when it exists and has no label. One small statement that selects only the label attribute.
     */
    private Optional<String> label(AdminEntity entity, Object id) {
        String labelAttribute = ListColumns.labelAttributeOf(entity).orElse(null);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> select = builder.createTupleQuery();
        Root<?> root = select.from(entity.type());
        List<Selection<?>> selections = new ArrayList<>();
        selections.add(root.get(entity.idAttribute()));
        if (labelAttribute != null) {
            selections.add(root.get(labelAttribute));
        }
        select.multiselect(selections);
        ParameterExpression<?> parameter = builder.parameter(IdConverter.parameterType(entity));
        select.where(builder.equal(root.get(entity.idAttribute()), parameter));
        TypedQuery<Tuple> typed = entityManager.createQuery(select);
        bind(typed, parameter, id);
        typed.setMaxResults(1);
        List<Tuple> found = typed.getResultList();
        if (found.isEmpty()) {
            return null;
        }
        Object value = labelAttribute == null ? null : found.get(0).get(1);
        if (value == null) {
            return Optional.empty();
        }
        Cell cell = CellFormatter.text(value.toString());
        return cell.type() == Cell.Type.EMPTY ? Optional.empty() : Optional.of(cell.display());
    }

    /** The managed records with these ids, in one statement. They are used inside the caller and never leave it. */
    @SuppressWarnings("unchecked")
    private List<Object> find(AdminEntity entity, Set<Object> ids) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> select = builder.createQuery(Object.class);
        Root<Object> root = (Root<Object>) select.from(entity.type());
        select.select(root).where(root.get(entity.idAttribute()).in(ids));
        return entityManager.createQuery(select).getResultList();
    }

    @SuppressWarnings("unchecked")
    private static void bind(TypedQuery<?> typed, ParameterExpression<?> parameter, Object value) {
        typed.setParameter((Parameter<Object>) parameter, value);
    }

    /** The log gets the entity's address and the kind of failure, never the provider's message, which holds SQL. */
    private static RecordInUseException refusal(AdminEntity entity, PersistenceException refused) {
        log.warn("The database refused a delete in '{}' ({}); nothing was deleted.", entity.urlName(),
                refused.getClass().getSimpleName());
        return new RecordInUseException(refused);
    }
}
