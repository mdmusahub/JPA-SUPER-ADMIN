package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.service.ExportService;
import io.github.mdmusahub.jpaadmin.service.FilterNote;
import io.github.mdmusahub.jpaadmin.service.FilterParse;
import io.github.mdmusahub.jpaadmin.service.HiddenParse;
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
 * <p>Columns are hidden with the repeated {@code hide} parameter, read by {@link ListQueryService#parseHidden}: only the key of
 * a visible, non-sensitive column counts, anything else is ignored without a message, and a request that would hide every
 * column is ignored as a whole and the page says so (Phase 12, D-112).
 *
 * <p>Filters are read from the {@code filter.*} parameters by {@link ListQueryService#parseFilters}; a filter that is
 * not acceptable is ignored, never an error, and the page says which one and why (in the console's notices, plain text
 * that is escaped on output). The cut-search notice and the filter notices share the same area.
 *
 * <p>The list offers bulk delete (row checkboxes and the bulk bar) exactly when the entity can be deleted
 * ({@link AdminEntity#canDelete}, from {@code @AdminResource}); every row's identifier links to its detail page and a
 * relation cell links to the related record (D-65, D-70). "New record" is offered exactly when the entity can be created in
 * (Phase 8, SEC-11), and the CSV export exactly when {@link ExportService#offers} says so (Phase 11): the export is switched on,
 * the entity is exportable and it has a column to export. When the export would be cut at the cap the page says so in a notice
 * (D-110). An action that is not offered is absent, not disabled, so no button leads nowhere.
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
@Controller
public class EntityController {

    /** The repeated parameter that names a column to hide (Phase 12, D-112). */
    public static final String HIDE_PARAMETER = "hide";

    private final ConsoleChromeFactory chromeFactory;
    private final ConsoleViews views;
    private final ConsoleNavigation navigation;
    private final ListQueryService lists;
    private final BasePath basePath;
    private final ExportService exports;

    /** A list page that offers no export (the console with the export switched off, or a test). */
    public EntityController(ConsoleChromeFactory chromeFactory, ConsoleViews views, ConsoleNavigation navigation,
                            ListQueryService lists, BasePath basePath) {
        this(chromeFactory, views, navigation, lists, basePath, ExportService.none());
    }

    /** @param exports decides whether the list offers the CSV export and what its cap is (Phase 11) */
    public EntityController(ConsoleChromeFactory chromeFactory, ConsoleViews views, ConsoleNavigation navigation,
                            ListQueryService lists, BasePath basePath, ExportService exports) {
        this.chromeFactory = Objects.requireNonNull(chromeFactory, "chromeFactory");
        this.views = Objects.requireNonNull(views, "views");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.lists = Objects.requireNonNull(lists, "lists");
        this.basePath = Objects.requireNonNull(basePath, "basePath");
        this.exports = Objects.requireNonNull(exports, "exports");
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
        HiddenParse hidden = lists.parseHidden(found, hideParameters(request));
        ListQuery query = lists.parse(found, q, sort, dir, page, size).withParsedFilters(filters.filters())
                .withParsedHidden(hidden.hidden());
        ListPage listed = lists.list(found, query);
        ListView.EntityInfo info = new ListView.EntityInfo(found.label(), Plurals.of(found.label()), found.icon());
        boolean export = exports.offers(found);
        ListView view = ListViewFactory.build(basePath.value(), found.urlName(), info, listed,
                new ListView.Actions(found.canCreate(), false, false, null, null),
                found.canDelete(), export);
        List<PageChrome.FlashMessage> notices = notices(q, filters, listed, hidden);
        if (export && listed.total() > exports.cap()) {
            // The file would be cut: say so on the page, before the download and after it (D-110).
            notices.add(ListViewFactory.exportLimitNotice(listed.total(), exports.cap()));
        }
        PageChrome chrome = chromeFactory.chrome(request,
                List.of(new Breadcrumb("Dashboard", basePath.value()), new Breadcrumb(info.plural(), null)),
                found.urlName(), notices);
        return views.page(request, ListView.TEMPLATE, chrome, view);
    }

    /** The notice about a request that hid every column, the cut-search notice and the filter notices, in that order. */
    private static List<PageChrome.FlashMessage> notices(String q, FilterParse filters, ListPage listed,
                                                          HiddenParse hidden) {
        List<PageChrome.FlashMessage> notices = new ArrayList<>();
        if (hidden.everyColumnHidden()) {
            notices.add(ListViewFactory.everyColumnHiddenNotice());
        }
        if (ListQuery.searchWasCut(q)) {
            notices.add(ListViewFactory.searchCutNotice());
        }
        List<FilterNote> notes = new ArrayList<>(filters.notes());
        notes.addAll(listed.notes());
        notices.addAll(ListViewFactory.filterNotices(notes));
        return notices;
    }

    /** The values of the repeated {@code hide} parameter, as sent (untrusted: the list parser checks every one). */
    static List<String> hideParameters(HttpServletRequest request) {
        String[] values = request.getParameterValues(HIDE_PARAMETER);
        return values == null ? List.of() : List.of(values);
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
