package io.github.mdmusahub.jpaadmin.web;

import io.github.mdmusahub.jpaadmin.metadata.AdminEntity;
import io.github.mdmusahub.jpaadmin.service.ActionRefusedException;
import io.github.mdmusahub.jpaadmin.service.CopySeed;
import io.github.mdmusahub.jpaadmin.service.DetailQueryService;
import io.github.mdmusahub.jpaadmin.service.FieldMessages;
import io.github.mdmusahub.jpaadmin.service.FormPage;
import io.github.mdmusahub.jpaadmin.service.FormQueryService;
import io.github.mdmusahub.jpaadmin.service.InvalidRecordException;
import io.github.mdmusahub.jpaadmin.service.ListQuery;
import io.github.mdmusahub.jpaadmin.service.RecordNotFoundException;
import io.github.mdmusahub.jpaadmin.service.RecordWriteService;
import io.github.mdmusahub.jpaadmin.service.UploadFiles;
import io.github.mdmusahub.jpaadmin.web.view.FormView;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome;
import io.github.mdmusahub.jpaadmin.web.view.PageChrome.FlashMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

/**
 * The create and edit forms and their saves.
 *
 * <pre>
 * GET  {base}/{entity}/new                  an empty create form (the entity's own defaults)
 * GET  {base}/{entity}/new?copyFrom={id}    the create form of a duplicate, filled from the record's copyable values
 * POST {base}/{entity}                      create
 * GET  {base}/{entity}/{id}/edit            the edit form
 * POST {base}/{entity}/{id}                 update
 * </pre>
 *
 * <p>The controller is thin (ARCH-2): it looks the entity up, asks a service, builds a record with a factory, or redirects. It
 * touches no entity manager, no entity and no sensitive value (ARCH-3, SEC-1). A GET changes nothing: the forms are read-only
 * pages until the POST (SEC-5 covers the POST: the security chain refuses one without the CSRF token before it gets here).
 *
 * <p><b>Same answers as the other record routes</b> (SEC-2, SEC-11): an entity that is hidden, excluded, unsupported or unknown,
 * an id that is not the shape of an id or has no record, and a missing, bad or unknown {@code copyFrom} are the console's one
 * 404, with no message. An entity that is in the console but is read-only, or not creatable (for a create) or not updatable
 * (for an edit), is 403; the services refuse it even when the page never showed the button.
 *
 * <p><b>A refused save shows the form again</b> (status 422) with the values the visitor typed, except a secret, which is never
 * shown back (SEC-1), and a plain sentence next to each field that was wrong. Nothing of the exception reaches the page. The
 * version the form was opened with is kept in the address, so a stale form stays stale. <b>A saved record redirects</b> (303)
 * to a link {@link ListLinks} built; nothing a form posts is ever used as an address to go to (D-65, mutation check i).
 *
 * <p>Internal, not stable API. Public only so the auto-configuration module can create it.
 */
@Controller
public class FormController {

    /** The {@code {entity}} segment, as in {@code RecordController}: it excludes {@code assets} (D-74). */
    private static final String ENTITY = "/{entity:(?!assets$)[a-z0-9-]+}";

    /** The status of a form that was shown again because the values were not acceptable. */
    private static final int UNPROCESSABLE = 422;

    private final ConsoleChromeFactory chromeFactory;
    private final ConsoleViews views;
    private final ConsoleNavigation navigation;
    private final FormQueryService forms;
    private final DetailQueryService details;
    private final RecordWriteService writes;
    private final BasePath basePath;
    private final UploadFiles uploads;

    public FormController(ConsoleChromeFactory chromeFactory, ConsoleViews views, ConsoleNavigation navigation,
                          FormQueryService forms, DetailQueryService details, RecordWriteService writes,
                          BasePath basePath) {
        this(chromeFactory, views, navigation, forms, details, writes, basePath, UploadFiles.none());
    }

    /** With upload support: a file sent with the plain form is shown as the field's value when the page is opened again. */
    public FormController(ConsoleChromeFactory chromeFactory, ConsoleViews views, ConsoleNavigation navigation,
                          FormQueryService forms, DetailQueryService details, RecordWriteService writes,
                          BasePath basePath, UploadFiles uploads) {
        this.uploads = Objects.requireNonNull(uploads, "uploads");
        this.chromeFactory = Objects.requireNonNull(chromeFactory, "chromeFactory");
        this.views = Objects.requireNonNull(views, "views");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.forms = Objects.requireNonNull(forms, "forms");
        this.details = Objects.requireNonNull(details, "details");
        this.writes = Objects.requireNonNull(writes, "writes");
        this.basePath = Objects.requireNonNull(basePath, "basePath");
    }

    // ---- create ----------------------------------------------------------------------------------------------------

    /**
     * The create form, or the create form of a duplicate when {@code copyFrom} names a record.
     *
     * @param copyFrom the id of the record to copy; untrusted. Absent means a plain "New record"
     */
    @GetMapping(ENTITY + "/new")
    public ModelAndView newRecord(@PathVariable("entity") String entity,
                                  @RequestParam(name = "copyFrom", required = false) String copyFrom,
                                  HttpServletRequest request) {
        AdminEntity found = entity(entity);
        try {
            forms.requireCreatable(found); // the flags first: a forbidden entity is 403 whatever copyFrom says
            if (copyFrom == null) {
                return show(request, null, found, withUploaded(request, found, "", forms.blank(found)), List.of(), List.of());
            }
            CopySeed seed = details.copySeed(found, copyFrom).orElseThrow(FormController::notFound);
            return show(request, null, found, withUploaded(request, found, "", forms.copyOf(found, seed)), List.of(),
                    List.of(SaveMessages.copied(found.label(), seed.sourceId())));
        } catch (RecordNotFoundException missing) {
            throw notFound();
        } catch (ActionRefusedException refused) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    /** Creates a record from the posted values and goes to the list; a refused save shows the form again. */
    @PostMapping("/{entity:(?!assets$)[a-z0-9-]+}")
    public ModelAndView create(@PathVariable("entity") String entity, HttpServletRequest request,
                               HttpServletResponse response) {
        AdminEntity found = entity(entity);
        ListLinks links = new ListLinks(basePath.value(), found.urlName());
        try {
            RecordWriteService.Saved saved = writes.create(found, request::getParameter);
            ConsoleFlash.add(request, SaveMessages.created(found.label(), saved.label()));
            return ConsoleRedirect.to(basePath, links.afterSave(ListQuery.defaults()));
        } catch (InvalidRecordException invalid) {
            response.setStatus(UNPROCESSABLE);
            FormPage again = forms.blank(found).reshown(request::getParameter, invalid.fieldErrors());
            return show(request, null, found, again, formErrors(invalid), List.of());
        } catch (RecordNotFoundException missing) {
            throw notFound();
        } catch (ActionRefusedException refused) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    // ---- edit ------------------------------------------------------------------------------------------------------

    /** The edit form of one record. */
    @GetMapping(ENTITY + "/{id}/edit")
    public ModelAndView edit(@PathVariable("entity") String entity, @PathVariable("id") String id,
                             HttpServletRequest request) {
        AdminEntity found = entity(entity);
        try {
            FormPage page = forms.record(found, id).orElseThrow(FormController::notFound);
            return show(request, id, found, withUploaded(request, found, id, page), List.of(), List.of());
        } catch (RecordNotFoundException missing) {
            throw notFound();
        } catch (ActionRefusedException refused) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Saves the posted values into the record and goes to its page; a refused save shows the form again. The identifier is the
     * one in the address: a posted {@code id} is not an editable name, so it is never read.
     */
    @PostMapping(ENTITY + "/{id}")
    public ModelAndView update(@PathVariable("entity") String entity, @PathVariable("id") String id,
                               HttpServletRequest request, HttpServletResponse response) {
        AdminEntity found = entity(entity);
        ListLinks links = new ListLinks(basePath.value(), found.urlName());
        String version = request.getParameter(ListLinks.VERSION_PARAMETER);
        try {
            RecordWriteService.Saved saved = writes.update(found, id, version, request::getParameter);
            ConsoleFlash.add(request, SaveMessages.updated(found.label(), saved.label()));
            return ConsoleRedirect.to(basePath, links.afterEdit(saved.id()));
        } catch (InvalidRecordException invalid) {
            response.setStatus(UNPROCESSABLE);
            FormPage current = forms.record(found, id).orElseThrow(FormController::notFound);
            // The version of the request, not the stored one: a form that was stale must stay stale until it is opened again.
            FormPage again = new FormPage(current.recordId(), version, current.label(), current.fields())
                    .reshown(request::getParameter, invalid.fieldErrors());
            return show(request, id, found, again, formErrors(invalid), List.of());
        } catch (RecordNotFoundException missing) {
            throw notFound();
        } catch (ActionRefusedException refused) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    /**
     * The sentences above a form that was refused: one line that counts the fields that need attention (the template lists
     * them, each as a link to its input), then the sentences about the whole form.
     */
    private static List<String> formErrors(InvalidRecordException invalid) {
        if (invalid.fieldErrors().isEmpty()) {
            return invalid.formErrors();
        }
        List<String> errors = new ArrayList<>();
        errors.add(FieldMessages.summary(invalid.fieldErrors().size()));
        errors.addAll(invalid.formErrors());
        return errors;
    }

    // ---- shared ----------------------------------------------------------------------------------------------------

    /**
 * Puts the file the visitor just sent with the plain form (option A, D-95) into the form as the upload field's value. An
 * entry is used once, only for the same entity, field and record, and only if the field's own storage still confirms the
 * address; anything else is dropped, so a session entry can never put an address in a field that the storage would refuse.
 * Nothing is saved: the visitor saves the record when they are ready.
 */
private FormPage withUploaded(HttpServletRequest request, AdminEntity found, String record, FormPage page) {
    List<FormView.Field> shown = new ArrayList<>(page.fields().size());
    boolean changed = false;
    for (FormView.Field field : page.fields()) {
        FormView.Field next = field;
        if (field.inputType() == FormView.Field.InputType.UPLOAD) {
            Optional<PendingUploads.Entry> pending = PendingUploads.take(request, found.urlName(), field.key(), record);
            if (pending.isPresent() && uploads.accepts(found.urlName(), field.key(), pending.get().url())) {
                next = new FormView.Field(field.key(), field.label(), field.inputType(), pending.get().url(),
                        field.required(), field.help(), field.options(), field.error(), field.uploadTicket());
                changed = true;
            }
        }
        shown.add(next);
    }
    return changed ? new FormPage(page.recordId(), page.version(), page.label(), shown) : page;
}

private ModelAndView show(HttpServletRequest request, String id, AdminEntity found, FormPage page,
                              List<String> formErrors, List<FlashMessage> notices) {
        FormView view = FormViewFactory.build(basePath.value(), found.urlName(), found.label(), page, formErrors);
        PageChrome chrome = chromeFactory.chrome(request, FormViewFactory.breadcrumbs(basePath.value(), found.urlName(),
                found.plural(), found.label(), page), found.urlName(), notices);
        return views.page(request, FormView.TEMPLATE, chrome, view);
    }

    private AdminEntity entity(String urlName) {
        return navigation.find(urlName).orElseThrow(FormController::notFound);
    }

    /** The one 404: no message, so the exception's text can never repeat what the visitor typed. */
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
