package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.service.ActionRefusedException;
import io.github.mdmusahub.jpaadmin.service.BulkDeleteIds;
import io.github.mdmusahub.jpaadmin.service.DeleteService;
import io.github.mdmusahub.jpaadmin.service.DetailQueryService;
import io.github.mdmusahub.jpaadmin.service.DetailRecord;
import io.github.mdmusahub.jpaadmin.service.FilterParse;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.service.RecordInUseException;
import io.github.mdmusahub.jpaadmin.service.RecordNotFoundException;
import io.github.mdmusahub.jpaadmin.service.TooManyRecordsException;
import io.github.mdmusahub.jpaadmin.web.view.DetailView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

/**
 * The routes of one record: its detail page, deleting it and deleting several. The forms (create, edit, duplicate) are
 * {@link FormController}'s.
 *
 * <pre>
 * GET  {base}/{entity}/{id}              the detail page
 * POST {base}/{entity}/{id}/delete       delete one record
 * POST {base}/{entity}/bulk-delete       delete the records whose ids are in the {@code ids} parameter
 * </pre>
 *
 * <p>The controller is thin (ARCH-2): it looks the entity up, asks a service, builds a record with a factory, or redirects. It
 * touches no entity manager, no entity and no sensitive value (ARCH-3, SEC-1).
 *
 * <p><b>Same answer, always</b> (SEC-2): an entity that is hidden, excluded, unsupported or unknown, an id that is not the
 * shape of an id, and an id that has no record all answer
 * the console's own 404, with no message and nothing from the request in it. An entity that is in the console but is
 * read-only or not deletable answers 403 (SEC-11, enforced in {@link DeleteService}, not here).
 *
 * <p><b>The list state travels in the address.</b> The delete forms post to an address that carries the list state the visitor
 * came from (search, filters, sort, page, size); the controller reads it with the same parser as the list page and builds the
 * way back with {@link ListLinks}. Nothing is taken from the request as a redirect target (D-65). A delete that removed
 * something returns to page 0 of that list, because its content changed (the rule of D-62); a delete that removed nothing
 * returns to the same page; the list clamps a page past the end (D-51).
 *
 * <p>Every POST needs the console's CSRF token; the security chain refuses a request without it before it gets here (SEC-5).
 * A GET changes nothing.
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
@Controller
public class RecordController {

    /**
     * The {@code {entity}} segment of the routes that GET a second segment. It excludes {@code assets} (and takes only what an
     * entity address can hold, D-45), because {@code {base}/assets/{file}} belongs to the console's asset handler and a
     * controller mapping would otherwise win over it. Everything else that does not match is the console's 404 all the same.
     */
    private static final String ENTITY = "/{entity:(?!assets$)[a-z0-9-]+}";

    private final ConsoleChromeFactory chromeFactory;
    private final ConsoleViews views;
    private final ConsoleNavigation navigation;
    private final ListQueryService lists;
    private final DetailQueryService details;
    private final DeleteService deletes;
    private final BasePath basePath;

    public RecordController(ConsoleChromeFactory chromeFactory, ConsoleViews views, ConsoleNavigation navigation,
                            ListQueryService lists, DetailQueryService details, DeleteService deletes,
                            BasePath basePath) {
        this.chromeFactory = Objects.requireNonNull(chromeFactory, "chromeFactory");
        this.views = Objects.requireNonNull(views, "views");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.lists = Objects.requireNonNull(lists, "lists");
        this.details = Objects.requireNonNull(details, "details");
        this.deletes = Objects.requireNonNull(deletes, "deletes");
        this.basePath = Objects.requireNonNull(basePath, "basePath");
    }

    // ---- detail ---------------------------------------------------------------------------------------------------

    /**
     * @param entity the entity's URL name from the address; untrusted, only ever a key in the registry
     * @param id     the record's id from the address; untrusted, converted to the id's type by the service
     * @throws ResponseStatusException 404 for every case described on the class
     */
    @GetMapping(ENTITY + "/{id}")
    public ModelAndView detail(@PathVariable("entity") String entity, @PathVariable("id") String id,
                               HttpServletRequest request) {
        AdminEntity found = entity(entity);
        DetailRecord record = details.find(found, id).orElseThrow(RecordController::notFound);
        ListQuery state = listState(found, request);
        DetailView view = DetailViewFactory.build(basePath.value(), found.urlName(), found.label(), record, state,
                new DetailViewFactory.Offers(found.canUpdate(), found.canCreate(), found.canDelete()));
        PageChrome chrome = chromeFactory.chrome(request, DetailViewFactory.breadcrumbs(basePath.value(),
                found.urlName(), Plurals.of(found.label()), view.title(), state), found.urlName(), List.of());
        return views.page(request, DetailView.TEMPLATE, chrome, view);
    }

    // ---- delete ---------------------------------------------------------------------------------------------------

    /**
     * Deletes one record and goes back to the list. A record the database will not let go (a foreign key still points at
     * it) stays, and the visitor is shown its page with a message that says why, without any SQL (SEC-12).
     */
    @PostMapping("/{entity}/{id}/delete")
    public ModelAndView delete(@PathVariable("entity") String entity, @PathVariable("id") String id,
                               HttpServletRequest request) {
        AdminEntity found = entity(entity);
        ListQuery state = listState(found, request);
        ListLinks links = new ListLinks(basePath.value(), found.urlName());
        try {
            DeleteService.Deleted deleted = deletes.delete(found, id);
            ConsoleFlash.add(request, DeleteMessages.deleted(found.label(), deleted.label()));
            return ConsoleRedirect.to(basePath, links.afterDelete(state, true));
        } catch (RecordInUseException refused) {
            ConsoleFlash.add(request, DeleteMessages.inUse(found.label()));
            String back = links.detail(id, state);
            return ConsoleRedirect.to(basePath, back != null ? back : links.afterDelete(state, false));
        } catch (RecordNotFoundException missing) {
            throw notFound();
        } catch (ActionRefusedException refused) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Deletes the selected records, all or none, and goes back to the list. The ids are the values of the {@code ids}
     * parameter, at most {@value DeleteService#MAX_BULK_DELETE}.
     */
    @PostMapping("/{entity}/bulk-delete")
    public ModelAndView bulkDelete(@PathVariable("entity") String entity,
                                   @RequestParam(name = "ids", required = false) String[] ids,
                                   HttpServletRequest request) {
        AdminEntity found = entity(entity);
        ListQuery state = listState(found, request);
        ListLinks links = new ListLinks(basePath.value(), found.urlName());
        String plural = Plurals.of(found.label());
        try {
            BulkDeleteIds selected = BulkDeleteIds.parse(ids);
            if (selected.size() == 0) {
                // Still check the entity's flags first: a forged request must not learn anything from this answer.
                deletes.deleteMany(found, selected);
                ConsoleFlash.add(request, DeleteMessages.nothingSelected());
                return ConsoleRedirect.to(basePath, links.afterDelete(state, false));
            }
            DeleteService.BulkResult result = deletes.deleteMany(found, selected);
            ConsoleFlash.add(request, DeleteMessages.bulkDeleted(plural, result.deleted(), result.notDeleted()));
            return ConsoleRedirect.to(basePath, links.afterDelete(state, result.deleted() > 0));
        } catch (TooManyRecordsException tooMany) {
            ConsoleFlash.add(request, DeleteMessages.tooMany());
            return ConsoleRedirect.to(basePath, links.afterDelete(state, false));
        } catch (RecordInUseException refused) {
            ConsoleFlash.add(request, DeleteMessages.bulkInUse(ids == null ? 0 : ids.length));
            return ConsoleRedirect.to(basePath, links.afterDelete(state, false));
        } catch (RecordNotFoundException missing) {
            throw notFound();
        } catch (ActionRefusedException refused) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    // ---- shared ---------------------------------------------------------------------------------------------------

    private AdminEntity entity(String urlName) {
        return navigation.find(urlName).orElseThrow(RecordController::notFound);
    }

    /** The one 404: no message, so the exception's text can never repeat what the visitor typed. */
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /** The list state of the address, read with the list page's own parser (SEC-2): nothing but a checked query comes out. */
    private ListQuery listState(AdminEntity entity, HttpServletRequest request) {
        FilterParse filters = lists.parseFilters(entity, EntityController.filterParameters(request));
        return lists.parse(entity, request.getParameter("q"), request.getParameter("sort"),
                request.getParameter("dir"), request.getParameter("page"), request.getParameter("size"))
                .withParsedFilters(filters.filters());
    }
}
