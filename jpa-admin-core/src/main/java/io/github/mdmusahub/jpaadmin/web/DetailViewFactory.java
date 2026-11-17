package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.service.DetailRecord;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.web.view.Cell;
import io.github.mdmusahub.jpaadmin.web.view.DetailView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Breadcrumb;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the {@link DetailView} of one record from the display values the detail service produced, and the breadcrumbs
 * that lead back to the list. It adds no data of its own, so nothing here can leak a value the service did not hand over.
 *
 * <p>The links are built here, by {@link ListLinks}, the one writer of a query string (D-29, D-65):
 * <ul>
 *   <li>a relation field links to the related record's detail page exactly when the target is in the console and the
 *       relation is set (D-54); a hidden or unsupported target keeps its {@code #id} text and gets no link;</li>
 *   <li>the breadcrumb for the list carries the list state the page was opened with (search, filters, sort, page, size),
 *       and the delete form's address carries it too, so the redirect after a delete returns to that list.</li>
 * </ul>
 *
 * <p>The preview app calls this same class with fake records, so what the preview shows is built by the real rules.
 *
 * <p>Internal, not stable API. Public because the controller and the preview use it.
 */
public final class DetailViewFactory {

    private DetailViewFactory() {
    }

    /**
     * What the page offers. {@code canEdit} and {@code canDuplicate} are printed as fixed routes by the template; the
     * delete address is built here.
     *
     * @param canEdit      whether "Edit" is offered
     * @param canDuplicate whether "Duplicate" is offered
     * @param canDelete    whether "Delete" is offered
     */
    public record Offers(boolean canEdit, boolean canDuplicate, boolean canDelete) {
    }

    /**
     * @param basePath    the console path, for example {@code /admin}
     * @param urlName     the entity's address segment
     * @param entityLabel the entity's singular label
     * @param record      the record, as display values
     * @param state       the list state the page was opened with (defaults when it was opened without one)
     * @param offers      what the page offers
     */
    public static DetailView build(String basePath, String urlName, String entityLabel, DetailRecord record,
                                   ListQuery state, Offers offers) {
        Objects.requireNonNull(basePath, "basePath");
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(offers, "offers");
        ListLinks links = new ListLinks(basePath, urlName);
        List<DetailView.Field> fields = new ArrayList<>(record.items().size());
        for (DetailRecord.Item item : record.items()) {
            fields.add(new DetailView.Field(item.label(), linked(basePath, item)));
        }
        String deleteHref = offers.canDelete() ? links.delete(record.id(), state) : null;
        return new DetailView(title(entityLabel, record), entityLabel, record.id(), fields, List.of(),
                new DetailView.Actions(offers.canEdit(), offers.canDuplicate(), deleteHref != null, deleteHref));
    }

    /** The record's label, or "Entity #id" when it has none. */
    public static String title(String entityLabel, DetailRecord record) {
        return record.label() != null ? record.label() : entityLabel + " #" + record.id();
    }

    /** Dashboard, the entity's list (with the list state of this visit), then the record. */
    public static List<Breadcrumb> breadcrumbs(String basePath, String urlName, String plural, String title,
                                               ListQuery state) {
        return List.of(new Breadcrumb("Dashboard", basePath),
                new Breadcrumb(plural, new ListLinks(basePath, urlName).to(state)), new Breadcrumb(title, null));
    }

    private static Cell linked(String basePath, DetailRecord.Item item) {
        Cell cell = item.cell();
        if (item.targetEntity() == null || item.targetId() == null || cell.type() != Cell.Type.RELATION) {
            return cell;
        }
        String href = new ListLinks(basePath, item.targetEntity()).detail(item.targetId(), ListQuery.defaults());
        return href == null ? cell : new Cell(cell.type(), cell.display(), cell.tone(), href, cell.title());
    }
}
