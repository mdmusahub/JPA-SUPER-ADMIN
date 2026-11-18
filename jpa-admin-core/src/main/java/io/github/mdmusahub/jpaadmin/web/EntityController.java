package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.service.FilterNote;
import io.github.mdmusahub.jpaadmin.service.FilterParse;
import io.github.mdmusahub.jpaadmin.service.ListPage;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.service.ListQueryService;
import io.github.mdmusahub.jpaadmin.web.view.ListView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Breadcrumb;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

/**
 * {@code GET {base path}/{entity}}: one entity's list page, with search ({@code q}), sort ({@code sort}, {@code dir}),
 * paging ({@code page}, {@code size}) and the links between them.
 *
 * <p>The controller is thin (ARCH-2): it looks the entity up, hands the five raw parameters to
 * {@link ListQuery#parse}, asks {@link ListQueryService} for the page, and builds the {@link ListView} with
 * {@link ListViewFactory}. It touches no entity manager, no entity and no sensitive value (ARCH-3, SEC-1).
 *
 * <p>The name in the address is untrusted. It is looked up in the registry and nowhere else (SEC-2), it is never
 * put into the page, a log line or an error message, and an entity that is hidden, excluded or unsupported is not
 * in the registry, so it answers exactly like a name that never existed: the console's own 404 page. The two cases
 * cannot be told apart, so the console never reveals that such an entity exists.
 *
 * <p>The other parameters are untrusted as well, and never cause an error: a page that is not a number or is below
 * 0 is page 0, a page past the end is the last page, a size that is not on offer is the default, a search longer
 * than the limit is cut (and the page says so in a notice), and a sort the entity cannot do is ignored (see
 * {@link ListQuery}). The page always renders.
 *
 * <p>Filters are read from the {@code filter.*} parameters by {@link ListQueryService#parseFilters}; a filter that is
 * not acceptable is ignored, never an error, and the page says which one and why (in the console's notices, plain text
 * that is escaped on output). The cut-search notice and the filter notices share the same area.
 *
 * <p>The list offers bulk delete (row checkboxes and the bulk bar) exactly when the entity can be deleted
 * ({@link AdminEntity#canDelete}, from {@code @AdminResource}); every row's identifier links to its detail page and a
 * relation cell links to the related record (D-65, D-70). "New record" is offered exactly when the entity can be created in (Phase 8, SEC-11) and CSV export arrives with
 * Phase 11. An action that is not offered is absent, not disabled, so no button leads nowhere.
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
@Controller
public class EntityController {

    private final ConsoleChromeFactory chromeFactory;
    private final ConsoleViews views;
    private final ConsoleNavigation navigation;
    private final ListQueryService lists;
    private final BasePath basePath;

    public EntityController(ConsoleChromeFactory chromeFactory, ConsoleViews views, ConsoleNavigation navigation,
                            ListQueryService lists, BasePath basePath) {
        this.chromeFactory = Objects.requireNonNull(chromeFactory, "chromeFactory");
        this.views = Objects.requireNonNull(views, "views");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.lists = Objects.requireNonNull(lists, "lists");
        this.basePath = Objects.requireNonNull(basePath, "basePath");
    }

    /**
     * @param entity the entity's URL name from the address; untrusted, only ever used as a key in the registry
     * @param q      search text; untrusted
     * @param sort   key of the column to sort by; untrusted
     * @param dir    {@code asc} or {@code desc}; untrusted
     * @param page   page number, zero-based; untrusted
     * @param size   rows per page; untrusted
     * @throws ResponseStatusException 404 when no such entity is in the console
     */
    @GetMapping("/{entity}")
    public ModelAndView entity(@PathVariable("entity") String entity,
                               @RequestParam(name = "q", required = false) String q,
                               @RequestParam(name = "sort", required = false) String sort,
                               @RequestParam(name = "dir", required = false) String dir,
                               @RequestParam(name = "page", required = false) String page,
                               @RequestParam(name = "size", required = false) String size,
                               HttpServletRequest request) {
        AdminEntity found = navigation.find(entity)
                // No message: the exception's text would otherwise repeat what the visitor typed.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        FilterParse filters = lists.parseFilters(found, filterParameters(request));
        ListQuery query = lists.parse(found, q, sort, dir, page, size).withParsedFilters(filters.filters());
        ListPage listed = lists.list(found, query);
        ListView.EntityInfo info = new ListView.EntityInfo(found.label(), Plurals.of(found.label()), found.icon());
        ListView view = ListViewFactory.build(basePath.value(), found.urlName(), info, listed,
                new ListView.Actions(found.canCreate(), false, false, null, null),
                found.canDelete());
        PageChrome chrome = chromeFactory.chrome(request,
                List.of(new Breadcrumb("Dashboard", basePath.value()), new Breadcrumb(info.plural(), null)),
                found.urlName(), notices(q, filters, listed));
        return views.page(request, ListView.TEMPLATE, chrome, view);
    }

    /** The cut-search notice and what to tell the visitor about the filters, in that order. */
    private static List<PageChrome.FlashMessage> notices(String q, FilterParse filters, ListPage listed) {
        List<PageChrome.FlashMessage> notices = new ArrayList<>();
        if (ListQuery.searchWasCut(q)) {
            notices.add(ListViewFactory.searchCutNotice());
        }
        List<FilterNote> notes = new ArrayList<>(filters.notes());
        notes.addAll(listed.notes());
        notices.addAll(ListViewFactory.filterNotices(notes));
        return notices;
    }

    /** The request's {@code filter.*} parameters only; everything else is none of the filter code's business. */
    static Map<String, List<String>> filterParameters(HttpServletRequest request) {
        Map<String, List<String>> filters = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            if (name.startsWith("filter.")) {
                filters.put(name, List.of(values));
            }
        });
        return filters;
    }
}
