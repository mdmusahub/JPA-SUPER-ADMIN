package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.sample.domain.Category;
import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import io.github.mdmusahub.jpaadmin.sample.domain.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
 * Phase 4 acceptance in the real demo application, through the real flow: sign in, open the list pages, search, sort,
 * page. Two subclasses run every test with {@code spring.jpa.open-in-view} on and off (D-29), because the console must
 * read its rows inside its own read-only transaction and not lean on a session that the host keeps open.
 *
 * <p>The seed has 25 products, 12 customers, 30 orders, 6 categories and 10 tags. For paging and search, the tests add
 * rows of their own (30 more products, one named like a phone) and remove them again, so no other test sees them.
 * The demo entity names appear here because this is the sample application, not a library module (ARCH-12).
 */
abstract class AbstractListPagesTest {

    private static final String BULK_SKU_PREFIX = "TEST-BULK-";
    private static final int BULK_PRODUCTS = 30;
    private static final Pattern ROW = Pattern.compile("(?s)<tr>(.*?)</tr>");
    private static final Pattern CELL = Pattern.compile("(?s)<td\\b[^>]*>(.*?)</td>");
    private static final Pattern ADDRESS = Pattern.compile("\\b(?:href|src|action)=\"([^\"]*)\"");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockBrowser browser;

    @BeforeEach
    void signInAndAddRows() throws Exception {
        browser = MockBrowser.signedIn(mvc);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Category books = entityManager.createQuery("select c from Category c where c.name = 'Electronics'",
                    Category.class).getSingleResult();
            for (int i = 1; i <= BULK_PRODUCTS; i++) {
                entityManager.persist(new Product("Bulk item " + String.format("%02d", i),
                        BULK_SKU_PREFIX + String.format("%02d", i), BigDecimal.valueOf(1000 + i).add(new BigDecimal("0.50")), books));
            }
            entityManager.persist(new Product("iPhone 16 Pro", BULK_SKU_PREFIX + "PHONE", new BigDecimal("1199.00"), books));
        });
    }

    @AfterEach
    void removeTheRowsAgain() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createQuery("delete from Product p where p.sku like :prefix")
                        .setParameter("prefix", BULK_SKU_PREFIX + "%").executeUpdate());
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    /** Adds one product (removed again after the test) whose name holds the characters a link must survive. */
    private void addProduct(String name, String skuSuffix) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Category electronics = entityManager.createQuery("select c from Category c where c.name = 'Electronics'",
                    Category.class).getSingleResult();
            entityManager.persist(new Product(name, BULK_SKU_PREFIX + skuSuffix, new BigDecimal("9.99"), electronics));
        });
    }

    private String page(String path) throws Exception {
        MockHttpServletResponse response = browser.getUri(path);
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return response.getContentAsString();
    }

    /** The text of every cell of every table row, in order. Tags are removed and entities are left as they are. */
    protected static List<List<String>> table(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher body = Pattern.compile("(?s)<tbody>(.*?)</tbody>").matcher(html);
        if (!body.find()) {
            return rows;
        }
        Matcher row = ROW.matcher(body.group(1));
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = CELL.matcher(row.group(1));
            while (cell.find()) {
                cells.add(cell.group(1).replaceAll("(?s)<[^>]*>", "").strip());
            }
            rows.add(cells);
        }
        return rows;
    }

    private static List<String> column(String html, int index) {
        return table(html).stream().map(row -> row.get(index)).toList();
    }

    private static List<String> headers(String html) {
        List<String> labels = new ArrayList<>();
        Matcher header = Pattern.compile("(?s)<th\\b[^>]*scope=\"col\"[^>]*>(.*?)</th>").matcher(html);
        while (header.find()) {
            labels.add(header.group(1).replaceAll("(?s)<[^>]*>", "").replaceAll("\\s+", " ").strip());
        }
        return labels;
    }

    private static String flat(String html) {
        return html.replaceAll("\\s+", " ");
    }

    // ---- the page ---------------------------------------------------------------------------------------------

    @Test
    void theProductListHasTheDefaultTwentyFiveRowsOfFiftySixAndTheRightColumns() throws Exception {
        String html = page("/admin/product");

        assertThat(table(html)).hasSize(25);
        assertThat(html).contains("<h1 class=\"page-title\">Products</h1>", "56 records", "Showing 1–25 of 56",
                "Page 1 of 3", "<title>Products | JPA Admin</title>");
        assertThat(headers(html)).containsExactly("ID", "Category", "Created at", "Featured", "Name", "Price", "Sku",
                "Status", "Stock");
        assertThat(html).contains("href=\"/admin/product\" aria-current=\"page\"");
        assertThat(html).doesNotContain("Tags").as("a collection is never a column (ARCH-4)");
    }

    @Test
    void pageTwoAndPageThreeAreTheOtherRowsAndTheLastOneHoldsTheRest() throws Exception {
        List<String> first = column(page("/admin/product"), 0);
        List<String> second = column(page("/admin/product?page=1"), 0);
        String third = page("/admin/product?page=2");

        assertThat(second).hasSize(25).doesNotContainAnyElementsOf(first);
        assertThat(column(third, 0)).hasSize(6).doesNotContainAnyElementsOf(first).doesNotContainAnyElementsOf(second);
        assertThat(third).contains("Showing 51–56 of 56", "Page 3 of 3");
        assertThat(third).doesNotContain("aria-label=\"Next page\"", "aria-label=\"Last page\"");
    }

    @Test
    void pagesOfTenEndWithAPartialPageAndAPagePastTheEndIsTheLastPage() throws Exception {
        String last = page("/admin/product?size=10&page=5");

        assertThat(table(last)).hasSize(6);
        assertThat(table(page("/admin/product?size=10&page=99"))).isEqualTo(table(last));
        assertThat(page("/admin/product?size=10&page=99")).contains("Page 6 of 6", "Showing 51–56 of 56");
        assertThat(table(page("/admin/product?size=10"))).hasSize(10);
        assertThat(table(page("/admin/product?size=100"))).hasSize(56);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=abc", "page=-1", "page=99999999999999999999", "page=", "size=7", "size=abc", "size=-5",
            "size=1000000", "sort=nosuch", "sort=password", "sort=category", "sort=tags&dir=asc", "dir=sideways", "dir=desc",
            "sort=name&dir=%27%3B--", "q=%25", "q=_", "q=%27", "q=%22", "q=http%3A%2F%2Fx", "q=%5C", "q=%00", "q=%F0%9F%98%80",
            "q=%3Cscript%3Ealert(1)%3C%2Fscript%3E", "q=x&q=y&page=1&page=2", "unknown=1", "page=1&size=10&sort=name&dir=desc&q=a"})
    void badOrOddParametersNeverBreakThePage(String query) throws Exception {
        String html = page("/admin/product?" + query);

        assertThat(html).contains("<h1 class=\"page-title\">Products</h1>").doesNotContain("Something went wrong");
    }

    @Test
    void aVeryLongQueryStringStillRenders() throws Exception {
        String html = page("/admin/product?q=" + "a".repeat(3000) + "&page=" + "9".repeat(500) + "&size=" + "9".repeat(500));

        assertThat(html).contains("No matching records");
        assertThat(html).contains("value=\"" + "a".repeat(100) + "\"").doesNotContain("a".repeat(101));
    }

    @Test
    void noListPageCarriesTheRemovedNoticeOrAMessageAtAll() throws Exception {
        for (String entity : List.of("category", "customer", "order", "product", "tag")) {
            String html = page("/admin/" + entity);

            assertThat(html).as(entity).doesNotContain("not available yet", "This table view");
            assertThat(html).as(entity + ": the page raises no flash message of its own").doesNotContain("class=\"toasts\"");
        }
    }

    // ---- search -----------------------------------------------------------------------------------------------

    @Test
    void aSearchFindsTheRowsThatMatchWhateverTheCase() throws Exception {
        for (String q : List.of("iphone", "IPHONE", "iPhone", "  iphone  ")) {
            String html = page("/admin/product?q=" + q.replace(" ", "%20"));

            assertThat(column(html, 4)).as(q).containsExactly("iPhone 16 Pro");
            assertThat(html).contains("1 record<").contains("value=\"" + q.strip() + "\"");
        }
        assertThat(column(page("/admin/product?q=phone"), 4)).containsExactlyInAnyOrder("Headphones", "iPhone 16 Pro",
                "Phone stand");
    }

    @Test
    void aSearchWithNoMatchShowsTheEmptyStateWithAWayBack() throws Exception {
        String html = page("/admin/product?q=zzzz");

        assertThat(html).contains("No matching records", "No products match your search", "Clear search")
                .doesNotContain("<table");
    }

    @Test
    void searchTextThatLooksLikeSyntaxIsMatchedLiterally() throws Exception {
        assertThat(page("/admin/product?q=%25")).as("a percent sign is not a wildcard").contains("No matching records");
        assertThat(page("/admin/product?q=_")).as("an underscore is not a wildcard").contains("No matching records");
        assertThat(page("/admin/product?q=http%3A%2F%2Fx")).contains("No matching records")
                .contains("value=\"http://x\"");
        assertThat(page("/admin/product?q=%27")).contains("No matching records");
        assertThat(page("/admin/product?q=x%27%20or%20%271%27%3D%271")).contains("No matching records");
    }

    @Test
    void theSearchIsKeptWhenSortingAndPaging() throws Exception {
        String html = page("/admin/product?q=bulk%20item&size=10");

        assertThat(html).contains("30 records", "Page 1 of 3");
        assertThat(flat(html)).contains("href=\"/admin/product?q=bulk%20item&amp;page=1&amp;size=10\"")
                .contains("href=\"/admin/product?q=bulk%20item&amp;sort=name&amp;dir=asc&amp;size=10\"");
    }

    // ---- sort -------------------------------------------------------------------------------------------------

    private static List<BigDecimal> prices(String html) {
        return column(html, 5).stream().map(BigDecimal::new).toList();
    }

    @Test
    void sortingByPriceDescendingPutsTheHighestFirstAndAscendingTheLowest() throws Exception {
        List<BigDecimal> descending = prices(page("/admin/product?sort=price&dir=desc&size=100"));
        List<BigDecimal> ascending = prices(page("/admin/product?sort=price&dir=asc&size=100"));

        assertThat(descending).hasSize(56).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(descending.get(0)).isEqualByComparingTo("1199.00");
        assertThat(ascending).hasSize(56).isSorted();
        assertThat(ascending.get(0)).isLessThan(new BigDecimal("10"));
    }

    @Test
    void theSortedHeaderSaysSoAndTheOthersDoNot() throws Exception {
        String html = page("/admin/product?sort=price&dir=desc");

        assertThat(flat(html)).contains("aria-sort=\"descending\"");
        assertThat(html.split("aria-sort=\"descending\"", -1).length - 1).isEqualTo(1);
        assertThat(html).doesNotContain("aria-sort=\"ascending\"");
        assertThat(flat(html)).contains("href=\"/admin/product?sort=price&amp;dir=asc\"");
    }

    @Test
    void pagingWhileSortedByAColumnWithEqualValuesNeverRepeatsOrDropsARow() throws Exception {
        // "featured" has only two values over 56 rows. Without the identifier as a tie-breaker the database may order
        // equal rows differently on each page.
        List<String> all = new ArrayList<>();
        for (int page = 0; page < 6; page++) {
            all.addAll(column(page("/admin/product?sort=featured&dir=desc&size=10&page=" + page), 0));
        }

        assertThat(all).hasSize(56).doesNotHaveDuplicates();
    }

    @Test
    void anUnsortableKeyIsIgnoredAndNeverReachesTheQuery() throws Exception {
        for (String key : List.of("password", "category", "tags", "nosuch", "' or 1=1 --")) {
            String html = page("/admin/product?sort=" + key.replace(" ", "%20").replace("'", "%27") + "&dir=desc");

            assertThat(html).as(key).doesNotContain("aria-sort=\"ascending\"", "aria-sort=\"descending\"");
            assertThat(column(html, 0).stream().map(Long::valueOf).toList()).as(key).isSorted();
        }
    }

    // ---- relations and collections ----------------------------------------------------------------------------

    @Test
    void aRelationColumnShowsTheLabelOfTheRelatedRecordAndNoLink() throws Exception {
        String products = page("/admin/product?q=iphone");
        String orders = page("/admin/order?size=100");

        assertThat(column(products, 1)).containsExactly("Electronics");
        assertThat(column(orders, 2)).hasSize(30).allSatisfy(customer -> assertThat(customer).isNotBlank().doesNotStartWith("#"));
        assertThat(column(orders, 2)).contains("Ava Stone", "Ben Carter");
        assertThat(orders).doesNotContain("class=\"cell-link\"");
        assertThat(headers(products)).doesNotContain("Tags");
    }

    @Test
    void everyDemoEntityListsItsRows() throws Exception {
        assertThat(table(page("/admin/category"))).hasSize(6);
        assertThat(table(page("/admin/tag"))).hasSize(10);
        assertThat(table(page("/admin/customer"))).hasSize(12);
        assertThat(table(page("/admin/order"))).hasSize(25);
        assertThat(page("/admin/order?page=1")).contains("Showing 26–30 of 30");
        assertThat(page("/admin/tag")).contains("<h1 class=\"page-title\">Tags</h1>");
        assertThat(page("/admin/category")).contains("<h1 class=\"page-title\">Categories</h1>");
    }

    // ---- sensitive values -------------------------------------------------------------------------------------

    @Test
    void thePasswordColumnIsMaskedInEveryRowAndTheValueIsNowhere() throws Exception {
        String html = page("/admin/customer");

        assertThat(headers(html)).contains("Password");
        assertThat(table(html)).hasSize(12).allSatisfy(row -> assertThat(String.join("|", row)).contains("••••••••Hidden value"));
        assertThat(html.split("class=\"cell-masked\"", -1).length - 1).isEqualTo(12);
        assertThat(html).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER).doesNotContain("demo-placeholder");
    }

    @Test
    void thePasswordColumnCannotBeSortedOrSearchedAndItsValueIsNeverInALink() throws Exception {
        for (String path : List.of("/admin/customer?sort=password&dir=asc", "/admin/customer?sort=password",
                "/admin/customer?q=demo-placeholder", "/admin/customer?q=placeholder-secret",
                "/admin/customer?q=" + Customer.DEMO_PASSWORD_PLACEHOLDER)) {
            MockHttpServletResponse response = browser.getUri(path);
            String html = response.getContentAsString();

            assertThat(response.getStatus()).as(path).isEqualTo(200);
            assertThat(html).as(path).doesNotContain("sort=password");
            assertThat(html).as(path + " (aside from what was typed)").doesNotContain("class=\"cell-text\">demo");
            assertThat(html).as(path).doesNotContain("aria-sort=\"ascending\"", "aria-sort=\"descending\"");
        }
        assertThat(page("/admin/customer?q=demo-placeholder")).contains("No matching records");
        assertThat(page("/admin/customer?size=10&sort=email&dir=desc")).as("a valid sort works next to it")
                .contains("aria-sort=\"descending\"");
        assertThat(page("/admin/customer?q=ava")).contains("1 record<");
    }

    @Test
    void theSensitiveValueIsInNoResponseOfAnyListPage() throws Exception {
        for (String entity : List.of("category", "customer", "order", "product", "tag")) {
            for (String query : List.of("", "?size=100", "?sort=id&dir=desc", "?q=a", "?page=99", "?sort=password")) {
                String html = browser.getUri("/admin/" + entity + query).getContentAsString();

                assertThat(html).as(entity + query).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER)
                        .doesNotContain(MockBrowser.PASSWORD);
            }
        }
    }

    // ---- unknown and hidden entities --------------------------------------------------------------------------

    @Test
    void aHiddenEntityIsTheSameNotFoundAsOneThatDoesNotExist() throws Exception {
        MockHttpServletResponse hidden = browser.getUri("/admin/internalauditentry?q=a&page=2");
        MockHttpServletResponse unknown = browser.getUri("/admin/there-is-no-such-entity?q=a&page=2");

        assertThat(hidden.getStatus()).isEqualTo(404);
        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat(content(hidden.getContentAsString())).isEqualTo(content(unknown.getContentAsString()));
        assertThat(content(hidden.getContentAsString())).doesNotContain("internalauditentry", "there-is-no-such-entity");
    }

    private static String content(String html) {
        Matcher main = Pattern.compile("(?s)<main\\b[^>]*>(.*?)</main>").matcher(html);
        assertThat(main.find()).isTrue();
        return main.group(1);
    }

    // ---- links ------------------------------------------------------------------------------------------------

    @Test
    void everyLinkLiesUnderTheConsolePathAndNothingTypedIsInAnAddressUnencoded() throws Exception {
        addProduct("Odd a b/c'd\"e&f 100%_x", "ODD");
        String html = page("/admin/product?q=a%20b%2Fc%27d%22e%26f%20100%25_x&size=10&sort=name&dir=asc");

        assertThat(table(html)).as("the odd row is found: the box holds its name literally").hasSize(1);
        Matcher addresses = ADDRESS.matcher(html);
        int checked = 0;
        while (addresses.find()) {
            String address = addresses.group(1).replace("&amp;", "&");
            if (address.equals("#main") || address.equals("data:,")) {
                continue;
            }
            checked++;
            assertThat(address).as(address).startsWith("/admin");
            assertThat(address).doesNotContain(" ", "'", "\"", "<", ">", "\\", "://", "//");
            assertThat(address).matches("[A-Za-z0-9/._%=&?-]*");
        }
        assertThat(checked).isGreaterThan(15);
        Matcher q = Pattern.compile("href=\"(/admin/product\\?q=[^\"]*)\"").matcher(html);
        assertThat(q.find()).isTrue();
        String decoded = URLDecoder.decode(q.group(1).replace("&amp;", "&"), StandardCharsets.UTF_8);
        assertThat(decoded).contains("q=a b/c'd\"e&f 100%_x");
        // The value is escaped where it is printed as text, so it cannot end an attribute or start a tag.
        assertThat(html).contains("Odd a b/c&#39;d&quot;e&amp;f 100%_x").doesNotContain("c'd\"e&f");
    }

    @Test
    void aSearchForAnAddressStaysDataAndIsNotALink() throws Exception {
        addProduct("See http://evil.example/x now", "URL");
        String html = page("/admin/product?q=http%3A%2F%2Fevil.example%2Fx&size=10");

        assertThat(table(html)).hasSize(1);
        Matcher addresses = ADDRESS.matcher(html);
        while (addresses.find()) {
            assertThat(addresses.group(1)).doesNotStartWith("http").doesNotContain("evil.example/x");
        }
        assertThat(html).contains("q=http%3A%2F%2Fevil.example%2Fx").contains("value=\"http://evil.example/x\"");
        assertThat(html).as("the text in the cell is text, not a link").contains("See http://evil.example/x now")
                .doesNotContain("<a href=\"http");
    }

    @Test
    void theSearchFormIsAGetFormWithoutACsrfTokenSoNoTokenCanEndUpInAnAddress() throws Exception {
        String html = page("/admin/product?q=a&sort=name&dir=desc&size=10");
        Matcher form = Pattern.compile("(?s)<form class=\"list-toolbar\".*?</form>").matcher(html);

        assertThat(form.find()).isTrue();
        assertThat(form.group()).contains("method=\"get\"").doesNotContain("_csrf");
        // The session's token is in the POST sign-out form only. It is never part of an address.
        String token = MockBrowser.csrf(html);
        Matcher addresses = ADDRESS.matcher(html);
        while (addresses.find()) {
            assertThat(addresses.group(1)).doesNotContain(token).doesNotContain("_csrf");
        }
    }

    @Test
    void theSidebarLinksOnTheListPageStillWork() throws Exception {
        String html = page("/admin/product?size=10&sort=name");
        Matcher links = Pattern.compile("<a class=\"nav-link\" href=\"(/admin[^\"]*)\"").matcher(html);
        List<String> checked = new ArrayList<>();
        while (links.find()) {
            checked.add(links.group(1));
            assertThat(browser.getUri(links.group(1)).getStatus()).as(links.group(1)).isEqualTo(200);
        }

        assertThat(checked).contains("/admin", "/admin/category", "/admin/customer", "/admin/order", "/admin/product",
                "/admin/tag");
    }

    @Test
    void thePagesOfTheListAreLinkedBothWays() throws Exception {
        String middle = page("/admin/product?page=1");

        for (String label : List.of("First page", "Previous page", "Next page", "Last page")) {
            assertThat(middle).contains("aria-label=\"" + label + "\"");
        }
        assertThat(table(page("/admin/product?page=0"))).isEqualTo(table(page("/admin/product")));
    }

    // ---- cost -------------------------------------------------------------------------------------------------

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }

    @Test
    void aListPageCostsTwoStatementsWhateverTheRowsAndLoadsNoEntityAndNoCollection() throws Exception {
        Statistics statistics = statistics();
        for (String path : List.of("/admin/order?size=100", "/admin/product?size=100", "/admin/customer?size=100",
                "/admin/product?q=bulk%20item&sort=price&dir=desc&page=1&size=10")) {
            page(path); // the first request fills the sidebar's count cache
            statistics.clear();

            page(path);

            assertThat(statistics.getPrepareStatementCount()).as(path + ": a count and a page, nothing per row (ARCH-6)")
                    .isEqualTo(2);
            assertThat(statistics.getEntityLoadCount()).as(path + ": no entity is ever loaded (ARCH-3)").isZero();
            assertThat(statistics.getCollectionLoadCount()).as(path + ": no collection is ever read (ARCH-4)").isZero();
        }
    }
}
