package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.service.FormPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Breadcrumb;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds the {@link FormView} of a create or edit form from the {@link FormPage} the form service produced, and the
 * breadcrumbs that lead back. It adds no data of its own, so nothing here can leak a value the service did not hand over; a
 * masked field arrives without a value and leaves without one (SEC-1).
 *
 * <p>The links are built here, by {@link ListLinks}, the one writer of a query string (D-29, D-74). The address a form posts to
 * is the collection (create) or the record (edit); for an entity with a {@code @Version} the edit address also carries the
 * version the form was opened with, which is how a save can tell that the form is stale. "Cancel" leads to the list (create)
 * or to the record's page (edit). <b>Nothing a form posts is ever used as an address to go to</b>: the controller redirects to
 * {@link ListLinks#afterSave} or {@link ListLinks#afterEdit}.
 *
 * <p>The preview app calls this same class with fake pages, so what the preview shows is built by the real rules.
 *
 * <p>Internal, not stable API. Public because the controller and the preview use it.
 */
public final class FormViewFactory {

    private FormViewFactory() {
    }

    /**
     * @param basePath    the console path, for example {@code /admin}
     * @param urlName     the entity's address segment
     * @param entityLabel the entity's singular label
     * @param page        the fields and the record's identity; no {@code recordId} means a create form
     * @param formErrors  sentences about the whole form (empty when there are none)
     */
    public static FormView build(String basePath, String urlName, String entityLabel, FormPage page,
                                 List<String> formErrors) {
        Objects.requireNonNull(basePath, "basePath");
        Objects.requireNonNull(page, "page");
        ListLinks links = new ListLinks(basePath, urlName);
        if (page.recordId() == null) {
            return new FormView(FormView.Mode.CREATE, "New " + entityLabel.toLowerCase(Locale.ROOT), entityLabel, null,
                    links.create(), links.to(ListQuery.defaults()), page.fields(), formErrors);
        }
        String action = links.update(page.recordId(), page.version());
        String cancel = links.detail(page.recordId(), ListQuery.defaults());
        String name = title(entityLabel, page);
        return new FormView(FormView.Mode.EDIT, "Edit " + name, entityLabel, page.recordId(),
                action != null ? action : links.create(), cancel != null ? cancel : links.to(ListQuery.defaults()),
                page.fields(), formErrors);
    }

    /** The record's label, or "Entity #id" when it has none. */
    public static String title(String entityLabel, FormPage page) {
        return page.label() != null ? page.label() : entityLabel + " #" + page.recordId();
    }

    /** Dashboard, the entity's list, then "New record" (create) or the record and "Edit" (edit). */
    public static List<Breadcrumb> breadcrumbs(String basePath, String urlName, String plural, String entityLabel,
                                               FormPage page) {
        ListLinks links = new ListLinks(basePath, urlName);
        Breadcrumb dashboard = new Breadcrumb("Dashboard", basePath);
        Breadcrumb list = new Breadcrumb(plural, links.to(ListQuery.defaults()));
        if (page.recordId() == null) {
            return List.of(dashboard, list, new Breadcrumb("New record", null));
        }
        return List.of(dashboard, list,
                new Breadcrumb(title(entityLabel, page), links.detail(page.recordId(), ListQuery.defaults())),
                new Breadcrumb("Edit", null));
    }
}
