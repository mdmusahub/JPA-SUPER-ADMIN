package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.Breadcrumb;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

/**
 * {@code GET {base path}/{entity}}: one entity's page.
 *
 * <p>Phase 3 discovers the entities; the table itself arrives in Phase 4. Until then a known entity answers 200
 * with the shell, the entity marked in the sidebar, and one notice that says the table is not available yet. The
 * page shows no data at all.
 *
 * <p>The name in the address is untrusted. It is looked up in the registry and nowhere else (SEC-2), it is never
 * put into the page, a log line or an error message, and an entity that is hidden, excluded or unsupported is not
 * in the registry, so it answers exactly like a name that never existed: the console's own 404 page. The two cases
 * cannot be told apart, so the console never reveals that such an entity exists.
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
@Controller
public class EntityController {

    /** The temporary template of this page. Phase 4 replaces it with the contract's {@code jpa-admin/list}. */
    public static final String TEMPLATE = "jpa-admin/entity-notice";

    /** The notice a known entity's page shows until Phase 4 builds the table. */
    public static final String TABLE_NOT_AVAILABLE_YET = "This table view is not available yet.";

    private final ConsoleChromeFactory chromeFactory;
    private final ConsoleViews views;
    private final ConsoleNavigation navigation;
    private final BasePath basePath;

    public EntityController(ConsoleChromeFactory chromeFactory, ConsoleViews views, ConsoleNavigation navigation,
                            BasePath basePath) {
        this.chromeFactory = Objects.requireNonNull(chromeFactory, "chromeFactory");
        this.views = Objects.requireNonNull(views, "views");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.basePath = Objects.requireNonNull(basePath, "basePath");
    }

    /**
     * @param entity the entity's URL name from the address; untrusted, only ever used as a key in the registry
     * @throws ResponseStatusException 404 when no such entity is in the console
     */
    @GetMapping("/{entity}")
    public ModelAndView entity(@PathVariable("entity") String entity, HttpServletRequest request) {
        AdminEntity found = navigation.find(entity)
                // No message: the exception's text would otherwise repeat what the visitor typed.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PageChrome chrome = chromeFactory.chrome(request,
                List.of(new Breadcrumb("Dashboard", basePath.value()), new Breadcrumb(found.label(), null)),
                found.urlName(),
                List.of(new FlashMessage(FlashMessage.Level.INFO, TABLE_NOT_AVAILABLE_YET)));
        return views.page(request, TEMPLATE, chrome);
    }
}
