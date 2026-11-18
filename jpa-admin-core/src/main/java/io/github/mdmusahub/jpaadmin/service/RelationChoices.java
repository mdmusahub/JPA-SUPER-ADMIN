package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.web.view.FormView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The records a to-one relation can point at, as choices: the target's identifier and label only, ordered by label (a
 * missing label last) and then by identifier, at most {@value #BOUND} of them (D-63). One row more is read to find out
 * whether there are more; a target with more records than the bound has <em>no</em> choices at all, never a list cut short.
 *
 * <p>The filter dropdown and the form's select share this one rule, and the form checks a posted id against {@link #ids()}:
 * an id that is not a choice never reaches an entity. It selects no entity, no collection and no other attribute, and a
 * label is never a sensitive attribute (see {@code ListColumns#labelAttributeOf}).
 *
 * <p>Package-private: it is a rule, not API.
 *
 * @param options     the choices in order, with the identifier as text and the label as the cell rules write it
 * @param ids         the identifiers by their text, as the identifier's Java type
 * @param overTheBound whether the target has more than {@value #BOUND} records
 */
record RelationChoices(List<FormView.Option> options, Map<String, Object> ids, boolean overTheBound) {

    /** The most choices a relation lists. */
    static final int BOUND = ListQueryService.MAX_RELATION_OPTIONS;

    static RelationChoices read(EntityManager entityManager, Relation relation) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> select = builder.createTupleQuery();
        Root<?> target = select.from(relation.targetType());
        Path<?> id = target.get(relation.idAttribute());
        if (relation.labelAttribute() == null) {
            select.multiselect(id);
            select.orderBy(builder.asc(id));
        } else {
            Path<?> label = target.get(relation.labelAttribute());
            select.multiselect(id, label);
            select.orderBy(builder.asc(label, Nulls.LAST), builder.asc(id));
        }
        List<Tuple> found = entityManager.createQuery(select).setMaxResults(BOUND + 1).getResultList();
        if (found.size() > BOUND) {
            return new RelationChoices(List.of(), Map.of(), true);
        }
        List<FormView.Option> options = new ArrayList<>(found.size());
        Map<String, Object> ids = new HashMap<>();
        for (Tuple row : found) {
            Object value = row.get(0);
            String text = CellFormatter.id(value);
            Object label = relation.labelAttribute() == null ? null : row.get(1);
            options.add(new FormView.Option(text, CellFormatter.relation(label, value).display()));
            ids.put(text, value);
        }
        return new RelationChoices(options, ids, false);
    }
}
