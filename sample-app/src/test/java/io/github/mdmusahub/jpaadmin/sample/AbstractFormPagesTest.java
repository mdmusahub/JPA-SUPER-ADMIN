package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.sample.domain.Category;
import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import io.github.mdmusahub.jpaadmin.sample.domain.Order;
import io.github.mdmusahub.jpaadmin.sample.domain.Product;
import io.github.mdmusahub.jpaadmin.sample.domain.Tag;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 8 acceptance in the real demo application, through the real flow: sign in, open a form, post it, with the real
 * security chain (so a missing CSRF token is really refused), the real Spring transaction proxy (so a refused record really
 * rolls back), the real Hibernate Validator (the sample entities use Bean Validation, D-19) and the real database. Two
 * subclasses run every test with {@code spring.jpa.open-in-view} on and off (D-29).
 *
 * <p>Every test that writes works on rows of its own (names, SKUs and e-mail addresses that start with a test prefix) and
 * removes them again, so no other test sees them. The demo entity names appear here because this is the sample application
 * (ARCH-12).
 */
abstract class AbstractFormPagesTest {

    private static final String SKU = "TEST-FORM-";
    private static final String NAME = "Disposable form ";
    private static final String MAIL = "form-test-";
    private static final String NOTE = "TEST-FORM";
    private static final String TYPED_SECRET = "needle-typed-secret-4711";
    private static final String CREATED_AT = "2026-01-01T10:00";
    private static final Pattern FORM_ACTION = Pattern.compile("(?s)<form class=\"card form-card\"[^>]*?\\saction=\"([^\"]+)\"");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockBrowser browser;

    @BeforeEach
    void signIn() throws Exception {
        browser = MockBrowser.signedIn(mvc);
    }

    @AfterEach
    void removeTheRowsAgain() {
        inTransaction(() -> {
            entityManager.createQuery("delete from Order o where o.note = :note").setParameter("note", NOTE).executeUpdate();
            entityManager.createQuery("delete from Product p where p.sku like :prefix").setParameter("prefix", SKU + "%")
                    .executeUpdate();
            entityManager.createQuery("delete from Customer c where c.email like :prefix").setParameter("prefix", MAIL + "%")
                    .executeUpdate();
            entityManager.createQuery("delete from Category c where c.name like :prefix").setParameter("prefix", NAME + "%")
                    .executeUpdate();
            entityManager.createQuery("delete from Tag t where t.name like :prefix").setParameter("prefix", NAME + "%")
                    .executeUpdate();
        });
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private long count(Class<?> type) {
        return inTransaction(() -> entityManager.createQuery("select count(e) from " + type.getSimpleName() + " e", Long.class)
                .getSingleResult());
    }

    private long electronics() {
        return entityManager.createQuery("select c.id from Category c where c.name = 'Electronics'", Long.class)
                .getSingleResult();
    }

    private String page(String path) throws Exception {
        MockHttpServletResponse response = browser.getUri(path);
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return response.getContentAsString();
    }

    private String token() throws Exception {
        return MockBrowser.csrf(page("/admin/category/new"));
    }

    /** The post address of the form on a page, as the browser would send to it. */
    private static String actionOf(String html) {
        Matcher form = FORM_ACTION.matcher(html);
        assertThat(form.find()).as("a form on the page").isTrue();
        return form.group(1).replace("&amp;", "&");
    }

    private static String[] fields(Map<String, String> values, String token) {
        List<String> all = new ArrayList<>();
        values.forEach((name, value) -> {
            all.add(name);
            all.add(value);
        });
        all.add("_csrf");
        all.add(token);
        return all.toArray(String[]::new);
    }

    private MockHttpServletResponse create(String entity, Map<String, String> values) throws Exception {
        return browser.postUri("/admin/" + entity, fields(values, token()));
    }

    private static Map<String, String> product(String suffix, long category) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", NAME + "product " + suffix);
        values.put("sku", SKU + suffix);
        values.put("price", "19.99");
        values.put("stock", "5");
        values.put("status", "DRAFT");
        values.put("featured", "false");
        values.put("category", String.valueOf(category));
        values.put("createdAt", CREATED_AT);
        return values;
    }

    private static Map<String, String> customer(String suffix) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("fullName", NAME + "customer " + suffix);
        values.put("email", MAIL + suffix + "@example.test");
        values.put("password", TYPED_SECRET);
        values.put("active", "true");
        values.put("createdAt", CREATED_AT);
        return values;
    }

    private Long productId(String suffix) {
        return inTransaction(() -> entityManager.createQuery("select p.id from Product p where p.sku = :sku", Long.class)
                .setParameter("sku", SKU + suffix).getSingleResult());
    }

    private Long customerId(String suffix) {
        return inTransaction(() -> entityManager.createQuery("select c.id from Customer c where c.email = :email", Long.class)
                .setParameter("email", MAIL + suffix + "@example.test").getSingleResult());
    }

    private String storedPassword(Long id) {
        return inTransaction(() -> entityManager.find(Customer.class, id).getPassword());
    }

    private static String main(String html) {
        Matcher main = Pattern.compile("(?s)<main\\b.*?</main>").matcher(html);
        assertThat(main.find()).isTrue();
        return main.group();
    }

    /** The page's markup with the form controls removed (a random CSRF token can contain any three letters). */
    private static String text(String html) {
        return html.replaceAll("(?s)<input\\b[^>]*>", "");
    }

    // ---- every entity can be created and edited ----------------------------------------------------------------------------

    @Test
    void aCategoryCanBeCreatedAndEdited() throws Exception {
        long before = count(Category.class);
        assertThat(page("/admin/category/new")).contains("<h1 class=\"page-title\">New category</h1>");

        MockHttpServletResponse created = create("category", Map.of("name", NAME + "category", "description", "made by a test",
                "createdAt", CREATED_AT));

        assertThat(created.getStatus()).isEqualTo(303);
        assertThat(created.getHeader("Location")).isEqualTo("/admin/category");
        assertThat(count(Category.class)).isEqualTo(before + 1);
        assertThat(text(page("/admin/category"))).contains("Created category “" + NAME + "category”.");
        assertThat(text(page("/admin/category"))).as("the message is shown once").doesNotContain("Created category");

        Long id = inTransaction(() -> entityManager.createQuery("select c.id from Category c where c.name = :n", Long.class)
                .setParameter("n", NAME + "category").getSingleResult());
        String edit = page("/admin/category/" + id + "/edit");
        assertThat(edit).contains("value=\"" + NAME + "category\"");
        MockHttpServletResponse saved = browser.postUri(actionOf(edit), "name", NAME + "renamed", "description", "",
                "createdAt", CREATED_AT, "_csrf", MockBrowser.csrf(edit));
        assertThat(saved.getStatus()).isEqualTo(303);
        assertThat(saved.getHeader("Location")).isEqualTo("/admin/category/" + id);
        assertThat(inTransaction(() -> entityManager.find(Category.class, id).getName())).isEqualTo(NAME + "renamed");
        assertThat(inTransaction(() -> entityManager.find(Category.class, id).getDescription())).isNull();
        assertThat(text(page("/admin/category/" + id))).contains("Saved the changes to category “" + NAME + "renamed”.");
    }

    @Test
    void aTagCanBeCreatedAndEdited() throws Exception {
        assertThat(create("tag", Map.of("name", NAME + "tag")).getStatus()).isEqualTo(303);
        Long id = inTransaction(() -> entityManager.createQuery("select t.id from Tag t where t.name = :n", Long.class)
                .setParameter("n", NAME + "tag").getSingleResult());

        String edit = page("/admin/tag/" + id + "/edit");
        browser.postUri(actionOf(edit), "name", NAME + "tag two", "_csrf", MockBrowser.csrf(edit));

        assertThat(inTransaction(() -> entityManager.find(Tag.class, id).getName())).isEqualTo(NAME + "tag two");
    }

    @Test
    void aCustomerCanBeCreatedAndEditedAndItsSecretIsNeverShown() throws Exception {
        MockHttpServletResponse created = create("customer", customer("a"));

        assertThat(created.getStatus()).isEqualTo(303);
        Long id = customerId("a");
        assertThat(storedPassword(id)).isEqualTo(TYPED_SECRET); // the console stores exactly what it is given
        String edit = page("/admin/customer/" + id + "/edit");
        assertThat(edit).contains("type=\"password\"").contains("Leave blank to keep the current value.");
        assertThat(edit).doesNotContain(TYPED_SECRET);
        Matcher password = Pattern.compile("(?is)<input\\b[^>]*type=\"password\"[^>]*>").matcher(edit);
        assertThat(password.find()).isTrue();
        assertThat(password.group()).doesNotContain("value=");

        browser.postUri(actionOf(edit), "fullName", NAME + "customer renamed", "email", MAIL + "a@example.test", "password", "",
                "active", "true", "createdAt", CREATED_AT, "_csrf", MockBrowser.csrf(edit));

        assertThat(inTransaction(() -> entityManager.find(Customer.class, id).getFullName())).isEqualTo(NAME + "customer renamed");
        assertThat(storedPassword(id)).as("a blank password keeps the stored one").isEqualTo(TYPED_SECRET);
    }

    @Test
    void aProductCanBeCreatedWithItsRelationEnumNumberAndDatesAndEdited() throws Exception {
        MockHttpServletResponse created = create("product", product("p1", electronics()));

        assertThat(created.getStatus()).isEqualTo(303);
        Long id = productId("p1");
        Product stored = inTransaction(() -> {
            Product found = entityManager.find(Product.class, id);
            found.getCategory().getName();
            return found;
        });
        assertThat(stored.getPrice()).isEqualByComparingTo(new BigDecimal("19.99"));
        assertThat(stored.getStock()).isEqualTo(5);
        assertThat(stored.getStatus().name()).isEqualTo("DRAFT");
        assertThat(stored.getCategory().getName()).isEqualTo("Electronics");
        assertThat(stored.getCreatedAt().toString()).isEqualTo(CREATED_AT);

        String edit = page("/admin/product/" + id + "/edit");
        assertThat(edit).contains("value=\"19.99\"").contains("<option value=\"DRAFT\" selected=\"selected\">Draft</option>")
                .contains("<option value=\"" + electronics() + "\" selected=\"selected\">Electronics</option>");
        Map<String, String> changed = product("p1", electronics());
        changed.put("price", "24.50");
        changed.put("status", "PUBLISHED");
        changed.put("featured", "true");
        MockHttpServletResponse saved = browser.postUri(actionOf(edit), fields(changed, MockBrowser.csrf(edit)));
        assertThat(saved.getStatus()).isEqualTo(303);
        Product updated = inTransaction(() -> entityManager.find(Product.class, id));
        assertThat(updated.getPrice()).isEqualByComparingTo(new BigDecimal("24.50"));
        assertThat(updated.getStatus().name()).isEqualTo("PUBLISHED");
        assertThat(updated.isFeatured()).isTrue();
    }

    @Test
    void anOrderCanBeCreatedAndEditedWithAnOptionalNoteLeftEmpty() throws Exception {
        long customer = inTransaction(() -> entityManager.createQuery("select min(c.id) from Customer c", Long.class)
                .getSingleResult());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("customer", String.valueOf(customer));
        values.put("status", "NEW");
        values.put("total", "10.00");
        values.put("note", NOTE);
        values.put("createdAt", CREATED_AT);

        assertThat(create("order", values).getStatus()).isEqualTo(303);
        Long id = inTransaction(() -> entityManager.createQuery("select o.id from Order o where o.note = :n", Long.class)
                .setParameter("n", NOTE).getSingleResult());

        String edit = page("/admin/order/" + id + "/edit");
        values.put("status", "PAID");
        assertThat(browser.postUri(actionOf(edit), fields(values, MockBrowser.csrf(edit))).getStatus()).isEqualTo(303);
        assertThat(inTransaction(() -> entityManager.find(Order.class, id).getStatus().name())).isEqualTo("PAID");
    }

    // ---- invalid input never saves ------------------------------------------------------------------------------------------

    @Test
    void aRequiredFieldLeftEmptyNeverSavesAndTheErrorIsNextToTheField() throws Exception {
        long before = count(Product.class);
        Map<String, String> values = product("empty", electronics());
        values.put("name", "");

        MockHttpServletResponse response = create("product", values);

        assertThat(response.getStatus()).isEqualTo(422);
        String html = response.getContentAsString();
        assertThat(html).contains("id=\"e-name\"").contains("Enter a value. This field is required.")
                .contains("aria-describedby=\"e-name\"").contains("aria-invalid=\"true\"");
        assertThat(html).contains("<a href=\"#f-name\">");
        assertThat(count(Product.class)).isEqualTo(before);
    }

    @Test
    void textInANumberFieldAnImpossibleDateAnUnknownEnumAndAnUnknownRelationNeverSaveAndNeverFail() throws Exception {
        long before = count(Product.class);
        Map<String, String> values = product("bad", electronics());
        values.put("price", "abc");
        values.put("stock", "many");
        values.put("createdAt", "2026-02-30T10:00");
        values.put("status", "SHIPPED");
        values.put("category", "999999");

        MockHttpServletResponse response = create("product", values);

        assertThat(response.getStatus()).isEqualTo(422);
        String html = response.getContentAsString();
        for (String field : List.of("price", "stock", "createdAt", "status", "category")) {
            assertThat(html).as(field).contains("id=\"e-" + field + "\"");
        }
        assertThat(html).contains("value=\"abc\"").contains("value=\"many\""); // what was typed is kept
        assertThat(text(html).toLowerCase()).doesNotContain("exception", "sql", "hibernate", "constraint", "stack");
        assertThat(count(Product.class)).isEqualTo(before);
    }

    @Test
    void beanValidationOfTheHostsEntityIsShownAsPlainSentencesPerField() throws Exception {
        long before = count(Product.class);
        Map<String, String> values = product("neg", electronics());
        values.put("price", "-5.00"); // @DecimalMin("0.00")
        values.put("stock", "-3"); // @Min(0)

        MockHttpServletResponse response = create("product", values);

        assertThat(response.getStatus()).isEqualTo(422);
        String html = response.getContentAsString();
        assertThat(html).contains("Enter a number that is at least 0.00.").contains("Enter a number that is at least 0.");
        assertThat(html).doesNotContain("DecimalMin").doesNotContain("jakarta.validation").doesNotContain("must be greater than")
                .doesNotContain("io.github.mdmusahub.jpaadmin.sample");
        assertThat(count(Product.class)).isEqualTo(before);
    }

    @Test
    void aTextTooLongForItsColumnIsRefusedWithTheLimit() throws Exception {
        Map<String, String> values = product("long", electronics());
        values.put("name", "x".repeat(121));

        MockHttpServletResponse response = create("product", values);

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString()).contains("Use at most 120 characters.");
    }

    @Test
    void aCustomerWithABadEmailOrAShortPasswordIsRefusedAndTheTypedSecretIsNeverShownBack() throws Exception {
        long before = count(Customer.class);
        Map<String, String> values = customer("bad");
        values.put("email", "not-an-email");
        values.put("password", "short");

        MockHttpServletResponse response = create("customer", values);

        assertThat(response.getStatus()).isEqualTo(422);
        String html = response.getContentAsString();
        assertThat(html).contains("valid email address").contains("Use between 8 and 100 characters.");
        assertThat(html).doesNotContain("short</").doesNotContain("value=\"short\"");
        Matcher password = Pattern.compile("(?is)<input\\b[^>]*type=\"password\"[^>]*>").matcher(html);
        assertThat(password.find()).isTrue();
        assertThat(password.group()).doesNotContain("value=");
        assertThat(count(Customer.class)).isEqualTo(before);
    }

    @Test
    void aTypedSecretIsNotInTheRefusedPageEvenWhenAnotherFieldIsWrong() throws Exception {
        Map<String, String> values = customer("bad2");
        values.put("email", "not-an-email");

        MockHttpServletResponse response = create("customer", values);

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString()).doesNotContain(TYPED_SECRET);
        assertThat(response.getHeaderNames()).noneMatch(name -> String.valueOf(response.getHeader(name)).contains(TYPED_SECRET));
    }

    @Test
    void aValueTheDatabaseRefusesIsAFormErrorWithNoSqlAndSavesNothing() throws Exception {
        assertThat(create("product", product("dup", electronics())).getStatus()).isEqualTo(303);
        long before = count(Product.class);

        MockHttpServletResponse response = create("product", product("dup", electronics())); // the SKU is unique

        assertThat(response.getStatus()).isEqualTo(422);
        String html = response.getContentAsString();
        assertThat(html).contains("The record was not saved because the database or the entity&#39;s rules refused it");
        assertThat(text(html).toLowerCase()).doesNotContain("unique", "sku_", "insert into", "constraint", "hibernate");
        assertThat(count(Product.class)).isEqualTo(before);
    }

    // ---- secrets -----------------------------------------------------------------------------------------------------------------

    @Test
    void aBlankSecretKeepsTheStoredOneAndATypedSecretReplacesItAndAppearsNowhere() throws Exception {
        create("customer", customer("s"));
        Long id = customerId("s");
        String edit = page("/admin/customer/" + id + "/edit");
        Map<String, String> same = customer("s");
        same.put("password", "");

        MockHttpServletResponse kept = browser.postUri(actionOf(edit), fields(same, MockBrowser.csrf(edit)));
        assertThat(kept.getStatus()).isEqualTo(303);
        assertThat(storedPassword(id)).isEqualTo(TYPED_SECRET);

        Map<String, String> replaced = customer("s");
        replaced.put("password", "a-brand-new-password");
        MockHttpServletResponse changed = browser.postUri(actionOf(edit), fields(replaced, MockBrowser.csrf(edit)));
        assertThat(changed.getStatus()).isEqualTo(303);
        assertThat(storedPassword(id)).isEqualTo("a-brand-new-password");

        // Nowhere the visitor can look: not the redirect, not the message, not the record's page, not the edit page again.
        assertThat(changed.getHeader("Location")).doesNotContain("a-brand-new-password");
        String detail = page("/admin/customer/" + id);
        assertThat(detail).doesNotContain("a-brand-new-password").doesNotContain(TYPED_SECRET);
        assertThat(page("/admin/customer/" + id + "/edit")).doesNotContain("a-brand-new-password");
        assertThat(page("/admin/customer")).doesNotContain("a-brand-new-password");
    }

    @Test
    void theSeededCustomersEditFormHasTheMaskedMarkerAndNoSecret() throws Exception {
        String edit = page("/admin/customer/1/edit");

        assertThat(edit).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER);
        assertThat(edit).contains("type=\"password\"").contains("Leave blank to keep the current value.");
    }

    // ---- mass assignment ------------------------------------------------------------------------------------------------------

    @Test
    void aPostedIdAHiddenOrUnknownNameAndACollectionAreIgnoredAndNeverStored() throws Exception {
        Map<String, String> values = product("mass", electronics());
        values.put("id", "999999");
        values.put("tags", "1");
        values.put("unknownField", "x");
        values.put("class", "java.lang.Object");
        values.put("version", "5");

        assertThat(create("product", values).getStatus()).isEqualTo(303);

        Long id = productId("mass");
        assertThat(id).isNotEqualTo(999999L);
        assertThat(inTransaction(() -> entityManager.find(Product.class, 999999L))).isNull();
        assertThat(inTransaction(() -> entityManager.find(Product.class, id).getTags().size())).isZero();
    }

    @Test
    void anUpdateNeverChangesTheIdentifierWhateverIsPosted() throws Exception {
        create("product", product("ident", electronics()));
        Long id = productId("ident");
        String edit = page("/admin/product/" + id + "/edit");
        Map<String, String> values = product("ident", electronics());
        values.put("id", "1");
        values.put("name", NAME + "product ident two");

        browser.postUri(actionOf(edit), fields(values, MockBrowser.csrf(edit)));

        assertThat(inTransaction(() -> entityManager.find(Product.class, id).getName())).isEqualTo(NAME + "product ident two");
        assertThat(inTransaction(() -> entityManager.find(Product.class, 1L).getName())).isNotEqualTo(NAME + "product ident two");
    }

    @ParameterizedTest
    @ValueSource(strings = {"next", "redirect", "returnTo", "url", "back", "continue", "location"})
    void theRedirectAfterASaveIsNeverTakenFromTheForm(String name) throws Exception {
        Map<String, String> values = product("redir-" + name, electronics());
        values.put(name, "https://evil.example/steal");

        MockHttpServletResponse response = create("product", values);

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/admin/product");
    }

    // ---- CSRF -------------------------------------------------------------------------------------------------------------------

    @Test
    void everyPostNeedsTheCsrfToken() throws Exception {
        long before = count(Product.class);
        Map<String, String> values = product("csrf", electronics());
        List<String> targets = List.of("/admin/product", "/admin/product/1", "/admin/category", "/admin/customer/1");

        for (String target : targets) {
            String[] noToken = fields(values, "x");
            String[] withoutField = java.util.Arrays.copyOf(noToken, noToken.length - 2);
            assertThat(browser.postUri(target, withoutField).getStatus()).as(target + " without a token").isEqualTo(403);
            assertThat(browser.postUri(target, noToken).getStatus()).as(target + " with a wrong token").isEqualTo(403);
        }

        assertThat(count(Product.class)).isEqualTo(before);
    }

    // ---- a GET changes nothing -----------------------------------------------------------------------------------------------

    @Test
    void openingAFormChangesNothing() throws Exception {
        long products = count(Product.class);
        long customers = count(Customer.class);
        long categories = count(Category.class);
        long orders = count(Order.class);
        String before = inTransaction(() -> entityManager.find(Customer.class, 1L).getPassword() + entityManager
                .find(Customer.class, 1L).getFullName());

        for (String entity : List.of("category", "tag", "customer", "product", "order")) {
            page("/admin/" + entity + "/new");
            page("/admin/" + entity + "/new?copyFrom=1");
            page("/admin/" + entity + "/1/edit");
        }

        assertThat(count(Product.class)).isEqualTo(products);
        assertThat(count(Customer.class)).isEqualTo(customers);
        assertThat(count(Category.class)).isEqualTo(categories);
        assertThat(count(Order.class)).isEqualTo(orders);
        assertThat(inTransaction(() -> entityManager.find(Customer.class, 1L).getPassword() + entityManager
                .find(Customer.class, 1L).getFullName())).isEqualTo(before);
    }

    // ---- 404 sameness and 403 ------------------------------------------------------------------------------------------------

    @Test
    void anUnknownIdANonNumericIdAHiddenEntityAndAnUnknownEntityAreTheSameConsoleNotFound() throws Exception {
        String token = token();
        List<MockHttpServletResponse> responses = new ArrayList<>();
        for (String path : List.of("/admin/product/99999/edit", "/admin/product/abc/edit", "/admin/internalauditentry/1/edit",
                "/admin/nosuchthing/1/edit", "/admin/internalauditentry/new", "/admin/nosuchthing/new",
                "/admin/product/new?copyFrom=99999", "/admin/product/new?copyFrom=abc")) {
            MockHttpServletResponse response = browser.getUri(path);
            assertThat(response.getStatus()).as(path).isEqualTo(404);
            responses.add(response);
        }
        for (String path : List.of("/admin/product/99999", "/admin/product/abc", "/admin/internalauditentry/1",
                "/admin/nosuchthing/1", "/admin/internalauditentry", "/admin/nosuchthing")) {
            MockHttpServletResponse response = browser.postUri(path, "name", "x", "_csrf", token);
            assertThat(response.getStatus()).as("POST " + path).isEqualTo(404);
            responses.add(response);
        }
        String reference = responses.get(0).getContentAsString();
        for (MockHttpServletResponse response : responses) {
            assertThat(response.getContentAsString().replaceAll("name=\"_csrf\"[^>]*>", "")
                    .replaceAll("value=\"[^\"]*\"", "")).isEqualTo(reference.replaceAll("name=\"_csrf\"[^>]*>", "")
                    .replaceAll("value=\"[^\"]*\"", ""));
        }
    }

    // ---- duplicate ---------------------------------------------------------------------------------------------------------------

    @Test
    void theDetailPageOffersEditAndDuplicateAndTheListOffersNewRecord() throws Exception {
        String detail = page("/admin/product/1");
        assertThat(detail).contains("href=\"/admin/product/1/edit\"").contains("Edit record")
                .contains("href=\"/admin/product/new?copyFrom=1\"").contains("Duplicate record");
        assertThat(page("/admin/product")).contains("href=\"/admin/product/new\"").contains("New record");
    }

    @Test
    void aDuplicateStartsWithTheCopyableValuesOnly() throws Exception {
        String html = page("/admin/product/new?copyFrom=1");

        assertThat(html).contains("<h1 class=\"page-title\">New product</h1>").contains("This form starts from a copy of product #1.");
        String form = main(html);
        assertThat(actionOf(html)).isEqualTo("/admin/product");
        assertThat(form).doesNotContain("jpa-admin-version").doesNotContain("name=\"id\"").doesNotContain("name=\"tags\"");
        String name = inTransaction(() -> entityManager.find(Product.class, 1L).getName());
        assertThat(form).contains("value=\"" + name.replace("&", "&amp;") + "\"");
    }

    @Test
    void aDuplicateOfACustomerCopiesNoSecretAndTheCopyIsANewRecord() throws Exception {
        String html = page("/admin/customer/new?copyFrom=1");

        assertThat(html).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER);
        Matcher password = Pattern.compile("(?is)<input\\b[^>]*type=\"password\"[^>]*>").matcher(html);
        assertThat(password.find()).isTrue();
        assertThat(password.group()).doesNotContain("value=").contains("required=\"required\"");

        Map<String, String> copy = customer("copy");
        assertThat(browser.postUri(actionOf(html), fields(copy, MockBrowser.csrf(html))).getStatus()).isEqualTo(303);
        assertThat(customerId("copy")).isNotEqualTo(1L);
    }

    // ---- a relation with more than 200 records -----------------------------------------------------------------------------

    @Test
    void aRelationWithMoreThan200RecordsIsANoticeAndAChangeToItIsRefusedForThatFieldOnly() throws Exception {
        inTransaction(() -> {
            for (int i = 0; i < 205; i++) {
                entityManager.persist(new Category(NAME + "bulk " + i, "bulk"));
            }
        });
        try {
            String html = page("/admin/product/new");
            assertThat(html).doesNotContain("id=\"f-category\"").contains("class=\"field-notice\"")
                    .contains("This field has more than 200 records to choose from");

            MockHttpServletResponse response = create("product", product("many", electronics()));

            assertThat(response.getStatus()).isEqualTo(422);
            String refused = response.getContentAsString();
            assertThat(refused).contains("id=\"e-category\"").contains("cannot be set here");
            assertThat(refused).doesNotContain("id=\"e-name\"").doesNotContain("id=\"e-price\"");
        } finally {
            inTransaction(() -> entityManager.createQuery("delete from Category c where c.name like :prefix")
                    .setParameter("prefix", NAME + "bulk %").executeUpdate());
        }
    }

}
