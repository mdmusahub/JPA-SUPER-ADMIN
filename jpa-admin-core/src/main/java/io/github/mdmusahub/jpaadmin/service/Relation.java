package io.github.mdmusahub.jpaadmin.service;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntityRegistry;
import io.github.mdmusahub.jpaadmin.metadata.AdminField;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;

/**
 * The identifier and the label of a to-one relation's target: what a query has to select to show the related record by
 * its label (the list, the filter choices and the detail page share this one rule, D-54).
 *
 * <p>Package-private: it is a rule, not API.
 *
 * @param idAttribute    the target's identifier attribute
 * @param labelAttribute the target's label attribute, or {@code null} when it has none (the relation then shows {@code #id})
 * @param targetType     the target's entity class
 */
record Relation(String idAttribute, String labelAttribute, Class<?> targetType) {

    /**
     * The relation of {@code field}, or {@code null} when the target has no single identifier attribute to read; the
     * cell is then empty rather than wrong.
     */
    static Relation of(EntityManager entityManager, AdminEntityRegistry registry, AdminField field) {
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
        String label = registry.find(field.targetEntity()).flatMap(ListColumns::labelAttributeOf).orElse(null);
        return new Relation(idAttribute, label, target.getJavaType());
    }
}
