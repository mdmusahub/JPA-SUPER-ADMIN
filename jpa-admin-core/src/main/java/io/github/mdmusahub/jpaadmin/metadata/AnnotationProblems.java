package io.github.mdmusahub.jpaadmin.metadata;

import io.github.mdmusahub.jpaadmin.annotation.AdminResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The startup check of the annotations of Phase 13 (D-120 to D-122): {@code @AdminResource(plural)}, {@code @AdminBadge} and
 * {@code @AdminLookup}. It reads the registry and answers, in one pass, every place where the host wrote something the console
 * cannot use, so the operator fixes them all at once, in the same way the upload check does (UPL-8). Any problem stops startup.
 *
 * <p>A sentence names the entity class and the field, and says what is wrong and what to write instead. It never repeats what
 * the host wrote in an annotation (an entry, a property name, a tone): the host's text is not echoed anywhere, so an attempt
 * to smuggle a colour or a style into a message gets nothing back. Only supported entities and visible fields are checked: an
 * entity or field the console does not show cannot show a wrong badge.
 *
 * <p>A pure function of the registry, tested without a running application. No entity name is hard-coded (ARCH-12).
 *
 * <p>Internal, not stable API. Public because the auto-configuration module runs it.
 */
public final class AnnotationProblems {

    private AnnotationProblems() {
    }

    /**
     * The problems found, one sentence each, in entity and field order. Empty when every annotation is usable.
     *
     * @param registry the console's entities
     */
    public static List<String> of(AdminEntityRegistry registry) {
        List<String> problems = new ArrayList<>();
        for (AdminEntity entity : registry.all()) {
            plural(entity, problems);
            for (AdminField field : entity.visibleFields()) {
                String where = entity.className() + "." + field.name();
                if (field.badge() != null && !field.sensitive()) {
                    badge(field, where, problems);
                }
                if (field.lookup() != null) {
                    lookup(field, where, registry, problems);
                }
            }
        }
        return List.copyOf(problems);
    }

    private static void plural(AdminEntity entity, List<String> problems) {
        AdminResource resource = entity.type().getAnnotation(AdminResource.class);
        if (resource != null && !resource.plural().isBlank() && !AdminEntity.acceptablePlural(resource.plural())) {
            problems.add("@AdminResource(plural) on " + entity.className() + " is not usable: write a plural name of at most "
                    + AdminEntity.MAX_PLURAL_LENGTH + " characters with no control character, for example plural = \"People\", "
                    + "or leave the option out to let the console guess it.");
        }
    }

    private static void badge(AdminField field, String where, List<String> problems) {
        boolean isEnum = field.kind() == AdminField.Kind.ENUM;
        boolean isString = field.kind() == AdminField.Kind.TEXT && field.javaType() == String.class;
        if (!isEnum && !isString) {
            problems.add("@AdminBadge on " + where + " cannot be used: it applies to an enum or a String field only.");
            return;
        }
        for (String problem : field.badge().problems()) {
            problems.add("@AdminBadge on " + where + ": " + problem + ".");
        }
        if (isEnum) {
            List<String> values = field.badge().values();
            int unknown = (int) values.stream().filter(value -> !field.enumValues().contains(value)).count();
            if (unknown > 0) {
                problems.add("@AdminBadge on " + where + " lists " + unknown + (unknown == 1 ? " value" : " values")
                        + " that " + (unknown == 1 ? "is" : "are") + " not a constant of the enum; a value is the name of a"
                        + " constant, written exactly as in the enum, for example \"PUBLISHED=green\".");
            }
        }
    }

    private static void lookup(AdminField field, String where, AdminEntityRegistry registry, List<String> problems) {
        if (field.kind() != AdminField.Kind.TO_ONE) {
            problems.add("@AdminLookup on " + where + " cannot be used: it applies to a to-one relation (@ManyToOne or "
                    + "@OneToOne) only.");
            return;
        }
        if (field.targetEntity() == null) {
            return; // the related entity is not in the console: the relation shows its number only and nothing can be wrong
        }
        Optional<AdminEntity> target = registry.find(field.targetEntity());
        if (target.isPresent() && usableLabel(target.get(), field.lookup()).isEmpty()) {
            problems.add("@AdminLookup on " + where + " names a property that " + target.get().className()
                    + " does not have as a visible, non-sensitive text (String) property that is not its identifier. "
                    + "Name such a property, or leave the annotation out to let the console choose the label.");
        }
    }

    /**
     * The property of {@code target} that a lookup may use as the label of its records: a visible, non-sensitive text
     * ({@code String}) property that is not the identifier. The one rule, used here to report a wrong lookup and by the
     * services to decide whether to use it, so a sensitive property can never become a label (SEC-1).
     *
     * @param target the related entity
     * @param name   the property name the host wrote; untrusted text, matched exactly against the metadata
     */
    public static Optional<String> usableLabel(AdminEntity target, String name) {
        if (name == null) {
            return Optional.empty();
        }
        return target.visibleFields().stream()
                .filter(candidate -> candidate.name().equals(name) && candidate.kind() == AdminField.Kind.TEXT
                        && candidate.javaType() == String.class && !candidate.sensitive() && !candidate.id())
                .map(AdminField::name).findFirst();
    }
}
