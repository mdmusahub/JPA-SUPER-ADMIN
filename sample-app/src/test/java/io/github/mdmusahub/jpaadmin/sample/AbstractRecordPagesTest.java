package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.sample.domain.Category;
import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import io.github.mdmusahub.jpaadmin.sample.domain.Product;
import io.github.mdmusahub.jpaadmin.service.CountService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
 * Phase 7 acceptance in the real demo application, through the real flow: sign in, open a record, delete it, delete
 * several, with the real security chain (so a missing CSRF token is really refused), the real Spring transaction proxy
 * (so a foreign key failure really rolls back) and the real database. Two subclasses run every test with
 * {@code spring.jpa.open-in-view} on and off (D-29).
 *
 * <p>Every test that writes works on rows of its own (names and SKUs that start with a test prefix) and removes them again,
 * so no other test sees them. The demo entity names appear here because this is the sample application (ARCH-12).
 */
abstract class AbstractRecordPagesTest {

    private static final String SKU = "TEST-REC-";
    private static final String NAME = "Disposable ";
    private static final Pattern DELETE_FORM = Pattern.compile("(?s)<form class=\"confirm-panel\"[^>]*?\saction=\"([^\"]+)\"");
    private static final Pattern BULK_FORM = Pattern.compile("(?s)<form class=\"bulk-form\"[^>]*?\saction=\"([^\"]+)\"");
    private static final Pattern ADDRESS = Pattern.compile("\\b(?:href|src|action)=\"([^\"]*)\"");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CountService counts;

    private MockBrowser browser;

    @BeforeEach
    void signIn() throws Exception {
        browser = MockBrowser.signedIn(mvc);
    }

    @AfterEach
    void removeTheRowsAgain() {
        inTransaction(() -> {
            entityManager.createQuery("delete from Product p where p.sku like :prefix").setParameter("prefix", SKU + "%")
                    .executeUpdate();
            entityManager.createQuery("delete from Category c where c.name like :prefix")
                    .setParameter("prefix", NAME + "%").executeUpdate();
        });
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private Category electronics() {
        return entityManager.createQuery("select c from Category c where c.name = 'Electronics'", Category.class)
                .getSingleResult();
    }

    /** A product of its own, made inside a transaction of its own. Returns its id. */
    private long newProduct(String name, String skuSuffix) {
        long[] id = new long[1];
        inTransaction(() -> {
            Product product = new Product(name, SKU + skuSuffix, new BigDecimal("9.99"), electronics());
            entityManager.persist(product);
            entityManager.flush();
            id[0] = product.getId();
        });
        return id[0];
    }

    private long newCategory(String name) {
        long[] id = new long[1];
        inTransaction(() -> {
            Category category = new Category(name, "made by a test");
            entityManager.persist(category);
            entityManager.flush();
            id[0] = category.getId();
        });
        return id[0];
    }

    private long firstId(String entity) {
        return entityManager.createQuery("select e.id from " + entity + " e order by e.id", Long.class).setMaxResults(1)
                .getSingleResult();
    }

    private boolean exists(Class<?> type, long id) {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager.find(type, id) != null);
    }

    private long count(Class<?> type) {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager
                .createQuery("select count(e) from " + type.getSimpleName() + " e", Long.class).getSingleResult());
    }

    private String page(String path) throws Exception {
        MockHttpServletResponse response = browser.getUri(path);
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return response.getContentAsString();
    }

    private static String main(String html) {
        Matcher main = Pattern.compile("(?s)<main\\b.*?</main>").matcher(html);
        assertThat(main.find()).isTrue();
        return main.group();
    }

    private static String unescape(String attribute) {
        return attribute.replace("&amp;", "&");
    }

    private static String formAction(Pattern form, String html) {
        Matcher matcher = form.matcher(html);
        assertThat(matcher.find()).as("a form that matches " + form).isTrue();
        return unescape(matcher.group(1));
    }

    private String csrf(String html) {
        return MockBrowser.csrf(html);
    }

    // ---- the detail page -----------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"Product", "Category", "Customer", "Order", "Tag"})
    void everyEntityHasADetailPageThatShowsItsRecord(String entity) throws Exception {
        String urlName = entity.toLowerCase();
        long id = firstId(entity);

        String html = page("/admin/" + urlName + "/" + id);

        assertThat(html).contains("<dl class=\"detail-grid\">", "<dt>ID</dt>", "<p class=\"muted detail-kicker\">" + entity + " #" + id + "</p>");
        assertThat(html).contains("aria-label=\"Breadcrumb\"", "href=\"/admin/" + urlName + "\"");
        assertThat(html).contains("Delete record", "action=\"/admin/" + urlName + "/" + id + "/delete\"");
        // Since Phase 8 the record can be edited and duplicated, so both links lead to a form.
        assertThat(html).contains("Edit record", "href=\"/admin/" + urlName + "/" + id + "/edit\"", "Duplicate record",
                "href=\"/admin/" + urlName + "/new?copyFrom=" + id + "\"");
    }

    @Test
    void aProductsRelationLinksToItsCategoryAndItsTagsAreNotRendered() throws Exception {
        long id = firstId("Product");
        long categoryId = entityManager.createQuery("select p.category.id from Product p where p.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();

        String html = main(page("/admin/product/" + id));

        assertThat(html).contains("<dt>Category</dt>").containsPattern("<a class=\"cell-link\" href=\"/admin/category/" + categoryId + "\">[^<]+</a>");
        assertThat(html).contains("<dt>Tags</dt>").contains("(not shown)");
    }

    @Test
    void aCustomersPasswordIsMaskedAndItsValueIsInNoPartOfThePage() throws Exception {
        long id = firstId("Customer");

        String html = page("/admin/customer/" + id);

        assertThat(html).contains("<dt>Password</dt>", "class=\"cell-masked\"", "Hidden value");
        assertThat(html).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER, MockBrowser.PASSWORD);
    }

    @Test
    void anOrdersCustomerIsALinkToTheCustomer() throws Exception {
        long orderId = firstId("Order");
        long customerId = entityManager.createQuery("select o.customer.id from Order o where o.id = :id", Long.class)
                .setParameter("id", orderId).getSingleResult();

        assertThat(main(page("/admin/order/" + orderId))).contains("href=\"/admin/customer/" + customerId + "\"");
    }

    @Test
    void theDetailPageCostsOneStatementAndLoadsNoEntityAndNoCollection() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        for (String path : List.of("/admin/product/" + firstId("Product"), "/admin/order/" + firstId("Order"),
                "/admin/customer/" + firstId("Customer"), "/admin/category/" + firstId("Category"))) {
            // The sidebar's counts are cached, one entry per entity, for 5 seconds on the real clock, and that cache is shared by every
            // test of this class. The request below only refreshes the entries that have already expired, so an entry that is almost
            // old enough can expire between the two requests and the measured one pays one extra count for it (1 + 5 = 6 was seen).
            // Forget them first: the request after this fills every entry fresh, and the measured request follows at once.
            // The cache is NOT cleared before the measured request: its counts must still come from the cache, that is what is measured.
            counts.clear();
            page(path); // the first request fills the sidebar's count cache
            statistics.clear();

            page(path);

            assertThat(statistics.getPrepareStatementCount()).as(path + ": one read of the record, none for filter choices").isEqualTo(1);
            assertThat(statistics.getEntityLoadCount()).as(path + ": no entity (ARCH-3)").isZero();
            assertThat(statistics.getCollectionLoadCount()).as(path + ": no collection (ARCH-4)").isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"999999", "abc", "1%20or%201=1", "-1", "007", "1.0", "99999999999999999999", "%3Cscript%3E"})
    void anUnknownIdAndATextThatIsNotAnIdAnswerTheSameNotFoundAsAHiddenEntity(String id) throws Exception {
        MockHttpServletResponse unknown = browser.getUri("/admin/product/999999");
        MockHttpServletResponse tried = browser.getUri("/admin/product/" + id);
        MockHttpServletResponse hidden = browser.getUri("/admin/internalauditentry/1");
        MockHttpServletResponse nothing = browser.getUri("/admin/nosuchthing/1");

        assertThat(tried.getStatus()).isEqualTo(unknown.getStatus());
        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat(hidden.getStatus()).isEqualTo(404);
        assertThat(nothing.getStatus()).isEqualTo(404);
        assertThat(hidden.getContentAsString()).isEqualTo(unknown.getContentAsString());
        assertThat(nothing.getContentAsString()).isEqualTo(unknown.getContentAsString());
        assertThat(tried.getContentAsString()).isEqualTo(unknown.getContentAsString());
        assertThat(unknown.getContentAsString()).contains("Page not found").doesNotContain("999999", "internalauditentry", "nosuchthing");
    }

    @ParameterizedTest
    @ValueSource(strings = {"%00", "..%2Fx", "a%2Fb", "%5Cx", "..", "%2e%2e"})
    void anIdWithAnEncodedSlashOrNulIsRefusedByTheFirewallBeforeTheConsoleIsAskedAndNothingIsReachable(String id) throws Exception {
        long keep = newProduct(NAME + "firewall", "FIREWALL");

        MockHttpServletResponse response = browser.getUri("/admin/product/" + id);
        MockHttpServletResponse deleted = browser.postUri("/admin/product/" + id + "/delete", "_csrf", csrf(page("/admin/product/" + keep)));

        assertThat(response.getStatus()).as("Spring Security's HTTP firewall, not a console page").isEqualTo(400);
        assertThat(deleted.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).doesNotContain("Delete record");
        assertThat(exists(Product.class, keep)).isTrue();
    }

    @Test
    void everyLinkOfADetailPageLiesUnderTheBasePath() throws Exception {
        long id = firstId("Product");
        List<String> addresses = new ArrayList<>();
        Matcher matcher = ADDRESS.matcher(page("/admin/product/" + id));
        while (matcher.find()) {
            addresses.add(unescape(matcher.group(1)));
        }

        assertThat(addresses).isNotEmpty().allSatisfy(address -> {
            assertThat(address).doesNotContain("//").doesNotContain("javascript:");
            assertThat(address.startsWith("/admin") || address.equals("#main") || address.equals("data:,")).as(address).isTrue();
        });
    }

    @Test
    void theRowLinkCarriesTheListStateAndTheDetailPageLeadsBackToIt() throws Exception {
        long id = newProduct(NAME + "state", "STATE");
        String list = page("/admin/product?q=disposable%20state&sort=name&dir=desc&size=10");
        Matcher link = Pattern.compile("href=\"(/admin/product/" + id + "[^\"]*)\"").matcher(list);
        assertThat(link.find()).isTrue();
        String rowLink = unescape(link.group(1));
        assertThat(rowLink).isEqualTo("/admin/product/" + id + "?q=disposable%20state&sort=name&dir=desc&size=10");

        String detail = page(rowLink);

        String state = "q=disposable%20state&amp;sort=name&amp;dir=desc&amp;size=10";
        assertThat(detail).contains("href=\"/admin/product?" + state + "\"")
                .contains("action=\"/admin/product/" + id + "/delete?" + state + "\"");
    }

    // ---- links on the list ---------------------------------------------------------------------------------------------------------

    @Test
    void theListLinksEachRowAndEachRelationAndTheLinkIdIsTheFilterDropdownsId() throws Exception {
        long id = newProduct(NAME + "links", "LINKS");
        long categoryId = electronics().getId();

        String html = page("/admin/product?q=disposable%20links");

        assertThat(html).contains("<a class=\"cell-link cell-number\" href=\"/admin/product/" + id + "?q=disposable%20links\">" + id + "</a>");
        assertThat(html).contains("<a class=\"cell-link\" href=\"/admin/category/" + categoryId + "\">Electronics</a>");
        assertThat(html).containsPattern("<option value=\"" + categoryId + "\"[^>]*>Electronics</option>");
    }

    @Test
    void aCustomerListHasNoRelationLinksButItsRowsLinkToTheRecord() throws Exception {
        String html = page("/admin/customer");

        assertThat(html).contains("<a class=\"cell-link cell-number\" href=\"/admin/customer/");
        assertThat(html).contains("class=\"cell-masked\"");
    }

    @Test
    void theListHasACheckboxPerRowInABulkFormOfItsOwnAndNeverInsideTheGetForm() throws Exception {
        newProduct(NAME + "boxes", "BOXES");

        String html = page("/admin/product?q=disposable");

        assertThat(html).contains("data-bulk-form", "data-select-all", "Delete selected");
        Matcher boxes = Pattern.compile("type=\"checkbox\" name=\"ids\"").matcher(html);
        int count = 0;
        while (boxes.find()) {
            count++;
        }
        assertThat(count).isEqualTo(1);
        assertThat(formAction(BULK_FORM, html)).isEqualTo("/admin/product/bulk-delete?q=disposable");
        int get = html.indexOf("<form class=\"list-toolbar\"");
        assertThat(html.indexOf("</form>", get)).isLessThan(html.indexOf("<form class=\"bulk-form\""));
    }

    // ---- delete one record --------------------------------------------------------------------------------------------------------------

    @Test
    void postingADeleteWithoutTheCsrfTokenIsRefusedAndNothingIsDeleted() throws Exception {
        long id = newProduct(NAME + "csrf", "CSRF");

        assertThat(browser.post("/admin/product/" + id + "/delete").getStatus()).isEqualTo(403);
        assertThat(browser.post("/admin/product/bulk-delete", "ids", String.valueOf(id)).getStatus()).isEqualTo(403);
        assertThat(browser.post("/admin/product/" + id + "/delete", "_csrf", "not-the-token").getStatus()).isEqualTo(403);

        assertThat(exists(Product.class, id)).isTrue();
    }

    @Test
    void aGetNeverDeletesAnything() throws Exception {
        long id = newProduct(NAME + "get", "GET");

        MockHttpServletResponse response = browser.get("/admin/product/" + id + "/delete");

        assertThat(response.getStatus()).isIn(404, 405);
        assertThat(browser.get("/admin/product/bulk-delete?ids=" + id).getStatus()).isIn(404, 405);
        assertThat(exists(Product.class, id)).isTrue();
        page("/admin/product/" + id);
        assertThat(exists(Product.class, id)).isTrue();
    }

    @Test
    void deletingWithTheTokenRemovesTheRecordAndReturnsToTheListWithAMessage() throws Exception {
        long id = newProduct(NAME + "<b>x</b>", "DELETE");
        String detail = page("/admin/product/" + id);
        String action = formAction(DELETE_FORM, detail);
        assertThat(action).isEqualTo("/admin/product/" + id + "/delete");

        MockHttpServletResponse response = browser.postUri(action, "_csrf", csrf(detail));

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/admin/product");
        assertThat(exists(Product.class, id)).isFalse();
        String list = page("/admin/product");
        assertThat(list).contains("role=\"status\"", "Deleted product \u201CDisposable &lt;b&gt;x&lt;/b&gt;\u201D.");
        assertThat(list).doesNotContain("Disposable <b>x</b>");
        assertThat(page("/admin/product")).as("shown once").doesNotContain("Deleted product");
    }

    @Test
    void deletingKeepsTheSearchSortAndSizeAndStartsAgainOnTheFirstPage() throws Exception {
        long id = newProduct(NAME + "state", "STATE2");
        String detail = page("/admin/product/" + id + "?q=disposable&sort=name&dir=desc&page=3&size=10");
        String action = formAction(DELETE_FORM, detail);

        MockHttpServletResponse response = browser.postUri(action, "_csrf", csrf(detail));

        assertThat(response.getHeader("Location")).isEqualTo("/admin/product?q=disposable&sort=name&dir=desc&size=10");
    }

    @Test
    void theReturnAddressIsNeverTakenFromTheRequest() throws Exception {
        long id = newProduct(NAME + "return", "RETURN");
        String token = csrf(page("/admin/product/" + id));

        MockHttpServletResponse response = browser.postUri("/admin/product/" + id + "/delete", "_csrf", token,
                "returnTo", "https://evil.example/", "next", "//evil.example", "redirect", "https://evil.example/");

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/admin/product");
    }

    @Test
    void aCategoryThatProductsPointAtIsKeptAndTheMessageHoldsNoSql() throws Exception {
        long id = electronics().getId();
        long before = count(Product.class);
        String detail = page("/admin/category/" + id + "?q=elec&size=10");

        MockHttpServletResponse response = browser.postUri(formAction(DELETE_FORM, detail), "_csrf", csrf(detail));

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/admin/category/" + id + "?q=elec&size=10");
        assertThat(exists(Category.class, id)).as("rolled back: the row is still there").isTrue();
        assertThat(count(Product.class)).isEqualTo(before);
        String after = page(response.getHeader("Location"));
        assertThat(after).contains("role=\"alert\"", "The category was not deleted. Other records still depend on it");
        // The CSRF token is random text and may hold any three letters, so it is left out of the search.
        assertThat(after.replaceAll("<input[^>]*>", "").toLowerCase()).doesNotContain("constraint", "violation", "sql", "foreign key", "category_id", "public.", "exception",
                "jdbc", "hibernate");
    }

    @Test
    void aTagUsedByAProductAndACustomerWithOrdersAreKeptToo() throws Exception {
        long tagId = entityManager.createQuery("select t.id from Product p join p.tags t order by t.id", Long.class)
                .setMaxResults(1).getSingleResult();
        long customerId = entityManager.createQuery("select o.customer.id from Order o order by o.id", Long.class)
                .setMaxResults(1).getSingleResult();
        for (String path : List.of("/admin/tag/" + tagId, "/admin/customer/" + customerId)) {
            String detail = page(path);

            MockHttpServletResponse response = browser.postUri(formAction(DELETE_FORM, detail), "_csrf", csrf(detail));

            assertThat(response.getStatus()).as(path).isEqualTo(303);
            assertThat(page(response.getHeader("Location"))).as(path).contains("role=\"alert\"", "was not deleted");
        }
        assertThat(count(Customer.class)).isEqualTo(12);
    }

    @Test
    void aHiddenOrUnknownEntityCannotBeDeletedEvenWithAValidToken() throws Exception {
        long id = newProduct(NAME + "hidden", "HIDDEN");
        String token = csrf(page("/admin/product/" + id));

        assertThat(browser.postUri("/admin/internalauditentry/1/delete", "_csrf", token).getStatus()).isEqualTo(404);
        assertThat(browser.postUri("/admin/internalauditentry/bulk-delete", "_csrf", token, "ids", "1").getStatus()).isEqualTo(404);
        assertThat(browser.postUri("/admin/nosuchthing/1/delete", "_csrf", token).getStatus()).isEqualTo(404);
        assertThat(browser.postUri("/admin/product/999999/delete", "_csrf", token).getStatus()).isEqualTo(404);
        assertThat(browser.postUri("/admin/product/abc/delete", "_csrf", token).getStatus()).isEqualTo(404);
    }

    // ---- bulk delete ------------------------------------------------------------------------------------------------------------------------

    @Test
    void bulkDeleteRemovesTheSelectedRecordsAndSaysHowManyWereDeletedAndHowManyWereNot() throws Exception {
        long a = newProduct(NAME + "a", "BULK-A");
        long b = newProduct(NAME + "b", "BULK-B");
        long keep = newProduct(NAME + "keep", "BULK-KEEP");
        String list = page("/admin/product?q=disposable&size=10");
        String action = formAction(BULK_FORM, list);

        MockHttpServletResponse response = browser.postUri(action, "_csrf", csrf(list), "ids", String.valueOf(a),
                "ids", String.valueOf(b), "ids", "987654321", "ids", "abc");

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/admin/product?q=disposable&size=10");
        assertThat(exists(Product.class, a)).isFalse();
        assertThat(exists(Product.class, b)).isFalse();
        assertThat(exists(Product.class, keep)).isTrue();
        String after = page(response.getHeader("Location"));
        assertThat(after).contains("role=\"alert\"", "Deleted 2 records.", "2 selected records were skipped")
                .doesNotContain("abc", "987654321");
    }

    @Test
    void bulkDeleteOverTheCapIsRefusedWithAMessageAndNothingIsDeleted() throws Exception {
        long id = newProduct(NAME + "cap", "CAP");
        String list = page("/admin/product?q=disposable");
        List<String> fields = new ArrayList<>(List.of("_csrf", csrf(list), "ids", String.valueOf(id)));
        for (int i = 0; i < 100; i++) {
            fields.add("ids");
            fields.add(String.valueOf(8_000_000 + i));
        }

        MockHttpServletResponse response = browser.postUri(formAction(BULK_FORM, list), fields.toArray(String[]::new));

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/admin/product?q=disposable");
        assertThat(exists(Product.class, id)).isTrue();
        assertThat(page(response.getHeader("Location"))).contains("role=\"alert\"",
                "You can delete at most 100 records at a time, and nothing was deleted.");
    }

    @Test
    void exactlyTheCapIsAccepted() throws Exception {
        String list = page("/admin/product");
        List<String> fields = new ArrayList<>(List.of("_csrf", csrf(list)));
        for (int i = 0; i < 100; i++) {
            fields.add("ids");
            fields.add(String.valueOf(8_100_000 + i));
        }

        MockHttpServletResponse response = browser.postUri(formAction(BULK_FORM, list), fields.toArray(String[]::new));

        assertThat(page(response.getHeader("Location"))).contains("No products were deleted.", "100 selected records were skipped");
    }

    @Test
    void oneRecordThatCannotGoKeepsEverySelectedRecordToo() throws Exception {
        long spare = newCategory(NAME + "spare");
        long inUse = electronics().getId();
        String list = page("/admin/category");

        MockHttpServletResponse response = browser.postUri(formAction(BULK_FORM, list), "_csrf", csrf(list), "ids",
                String.valueOf(spare), "ids", String.valueOf(inUse));

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(exists(Category.class, spare)).as("all or nothing: the spare category stays too").isTrue();
        assertThat(exists(Category.class, inUse)).isTrue();
        String after = page(response.getHeader("Location"));
        assertThat(after).contains("role=\"alert\"", "Nothing was deleted. At least one of the 2 selected records");
        assertThat(after.replaceAll("<input[^>]*>", "").toLowerCase()).doesNotContain("constraint", "sql", "category_id", "exception");
    }

    @Test
    void bulkDeleteWithNothingSelectedSaysSoAndKeepsThePage() throws Exception {
        String list = page("/admin/product?size=10");

        MockHttpServletResponse response = browser.postUri(formAction(BULK_FORM, list), "_csrf", csrf(list));

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/admin/product?size=10");
        assertThat(page(response.getHeader("Location"))).contains("No records were selected.");
    }

    @Test
    void aCategoryNobodyUsesCanBeDeletedFromItsDetailPage() throws Exception {
        long id = newCategory(NAME + "alone");
        String detail = page("/admin/category/" + id);

        MockHttpServletResponse response = browser.postUri(formAction(DELETE_FORM, detail), "_csrf", csrf(detail));

        assertThat(response.getHeader("Location")).isEqualTo("/admin/category");
        assertThat(exists(Category.class, id)).isFalse();
        assertThat(page("/admin/category")).contains("Deleted category \u201CDisposable alone\u201D.");
    }
}
