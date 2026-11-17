package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 6 acceptance in the real demo application, through the real flow: sign in, open the list pages with
 * {@code filter.*} parameters, and check the rows against what the database itself says. Two subclasses run every test
 * with {@code spring.jpa.open-in-view} on and off (D-29). The expected counts come from JPQL written in the test, not
 * from the page. The demo entity names appear here because this is the sample application, not a library module
 * (ARCH-12).
 */
abstract class AbstractFilterPagesTest {

    private static final Pattern COUNT = Pattern.compile("list-count\">([\\d,]+) records?<");
    private static final Pattern ROW = Pattern.compile("(?s)<tr>(.*?)</tr>");
    private static final Pattern CELL = Pattern.compile("(?s)<td\\b[^>]*>(.*?)</td>");
    private static final Pattern ADDRESS = Pattern.compile("\\b(?:href|src|action)=\"([^\"]*)\"");
    private static final String UNKNOWN_FILTER = "1 filter in the address does not match any filter of this table and was ignored.";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private MockBrowser browser;

    @BeforeEach
    void signIn() throws Exception {
        browser = MockBrowser.signedIn(mvc);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private String page(String path) throws Exception {
        MockHttpServletResponse response = browser.getUri(path);
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private static int total(String html) {
        Matcher count = COUNT.matcher(html);
        assertThat(count.find()).as("a record count").isTrue();
        return Integer.parseInt(count.group(1).replace(",", ""));
    }

    private static List<List<String>> table(String html) {
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

    private static String flat(String html) {
        return html.replaceAll("\\s+", " ");
    }

    private long count(String jpql, Object... parameters) {
        var query = entityManager.createQuery(jpql, Long.class);
        for (int i = 0; i < parameters.length; i++) {
            query.setParameter(i + 1, parameters[i]);
        }
        return query.getSingleResult();
    }

    private static String chips(String html) {
        int from = html.indexOf("<div class=\"filter-chips\"");
        return from < 0 ? "" : flat(html.substring(from, html.indexOf("</ul>", from)));
    }

    private static List<String> notices(String html) {
        List<String> found = new ArrayList<>();
        Matcher message = Pattern.compile("(?s)<div class=\"toast [^\"]*\"[^>]*>.*?<span>(.*?)</span>\\s*</div>").matcher(html);
        while (message.find()) {
            found.add(message.group(1).replace("&#39;", "'").strip());
        }
        return found;
    }

    private long categoryId(String name) {
        return entityManager.createQuery("select c.id from Category c where c.name = :name", Long.class)
                .setParameter("name", name).getSingleResult();
    }

    // ---- the panel --------------------------------------------------------------------------------------------

    @Test
    void everyEntityOffersExactlyTheFiltersOfItsFilterableFields() throws Exception {
        assertThat(controls(page("/admin/product"))).containsExactly("filter.category", "filter.createdAt.from", "filter.createdAt.to",
                "filter.featured", "filter.name", "filter.price.min", "filter.price.max", "filter.sku", "filter.status", "filter.stock.min",
                "filter.stock.max");
        assertThat(controls(page("/admin/order"))).containsExactly("filter.createdAt.from", "filter.createdAt.to", "filter.customer",
                "filter.note", "filter.status", "filter.total.min", "filter.total.max");
        assertThat(controls(page("/admin/customer"))).containsExactly("filter.active", "filter.createdAt.from", "filter.createdAt.to",
                "filter.email", "filter.fullName");
        assertThat(controls(page("/admin/category"))).containsExactly("filter.createdAt.from", "filter.createdAt.to", "filter.description",
                "filter.name");
        assertThat(controls(page("/admin/tag"))).containsExactly("filter.name");
    }

    private static List<String> controls(String html) {
        int from = html.indexOf("<details class=\"filter-panel\"");
        assertThat(from).isGreaterThanOrEqualTo(0);
        String panel = html.substring(from, html.indexOf("</details>", from));
        List<String> names = new ArrayList<>();
        Matcher name = Pattern.compile("<(?:input|select)\\b[^>]*\\bname=\"(filter\\.[^\"]*)\"").matcher(panel);
        while (name.find()) {
            names.add(name.group(1));
        }
        return names;
    }

    @Test
    void thePasswordHasNoFilterTheIdentifierAndTheCollectionNeither() throws Exception {
        for (String entity : List.of("customer", "product")) {
            String html = page("/admin/" + entity);

            assertThat(controls(html)).noneMatch(name -> name.toLowerCase().contains("password") || name.equals("filter.id")
                    || name.contains("tags"));
        }
        assertThat(page("/admin/customer")).contains(">Password<").doesNotContain("filter.password");
    }

    @Test
    void aRelationDropdownListsTheLabelsOfTheTargetAndNothingElse() throws Exception {
        String products = page("/admin/product");
        String orders = page("/admin/order");

        assertThat(optionLabels(products, "filter-category")).containsExactly("Books", "Electronics", "Garden", "Home", "Sports", "Toys");
        assertThat(optionLabels(orders, "filter-customer")).hasSize(12).contains("Ava Stone", "Liam Gray")
                .noneMatch(label -> label.contains("@") || label.contains(Customer.DEMO_PASSWORD_PLACEHOLDER));
        assertThat(orders).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER);
        assertThat(products).doesNotContain("audit", "Audit");
    }

    private static List<String> optionLabels(String html, String selectId) {
        int from = html.indexOf("id=\"" + selectId + "\"");
        assertThat(from).as(selectId).isGreaterThanOrEqualTo(0);
        String select = html.substring(from, html.indexOf("</select>", from));
        List<String> labels = new ArrayList<>();
        Matcher option = Pattern.compile("<option value=\"([^\"]*)\"[^>]*>(.*?)</option>").matcher(select);
        while (option.find()) {
            if (!option.group(1).isEmpty()) {
                labels.add(option.group(2));
            }
        }
        return labels;
    }

    // ---- each type --------------------------------------------------------------------------------------------

    @Test
    void filteringProductsByEachCategoryGivesExactlyThatCategorysProducts() throws Exception {
        for (String name : List.of("Books", "Electronics", "Garden", "Home", "Toys", "Sports")) {
            String html = page("/admin/product?filter.category=" + categoryId(name) + "&size=100");

            assertThat(total(html)).as(name).isEqualTo(count("select count(p) from Product p where p.category.name = ?1", name)).isPositive();
            assertThat(column(html, 1)).as(name).isNotEmpty().containsOnly(name);
            assertThat(chips(html)).as(name).contains("<span class=\"chip-label\">Category:</span>", "<span class=\"chip-value\">" + name + "</span>");
            assertThat(notices(html)).as(name).isEmpty();
        }
    }

    @Test
    void aPriceRangeKeepsOnlyTheProductsInsideItAndEitherBoundMayBeLeftOut() throws Exception {
        String both = page("/admin/product?filter.price.min=20&filter.price.max=60&size=100");
        String minOnly = page("/admin/product?filter.price.min=60.5&size=100");
        String maxOnly = page("/admin/product?filter.price.max=10&size=100");

        assertThat(total(both)).isEqualTo(count("select count(p) from Product p where p.price >= ?1 and p.price <= ?2",
                new BigDecimal("20"), new BigDecimal("60"))).isPositive();
        assertThat(column(both, 5).stream().map(BigDecimal::new)).allSatisfy(price -> assertThat(price)
                .isBetween(new BigDecimal("20"), new BigDecimal("60")));
        assertThat(total(minOnly)).isEqualTo(count("select count(p) from Product p where p.price >= ?1", new BigDecimal("60.5")));
        assertThat(total(maxOnly)).isEqualTo(count("select count(p) from Product p where p.price <= ?1", new BigDecimal("10")));
        assertThat(chips(both)).contains("20 to 60");
        assertThat(chips(minOnly)).contains("at least 60.5");
    }

    @Test
    void anEnumABooleanAndATextFilterWork() throws Exception {
        assertThat(total(page("/admin/product?filter.status=PUBLISHED&size=100")))
                .isEqualTo(count("select count(p) from Product p where p.status = io.github.mdmusahub.jpaadmin.sample.domain.ProductStatus.PUBLISHED"));
        assertThat(total(page("/admin/product?filter.featured=true&size=100"))).isEqualTo(count("select count(p) from Product p where p.featured = true"));
        assertThat(total(page("/admin/product?filter.featured=false&size=100"))).isEqualTo(count("select count(p) from Product p where p.featured = false"));
        assertThat(total(page("/admin/product?filter.name=PHONE&size=100")))
                .isEqualTo(count("select count(p) from Product p where lower(p.name) like '%phone%'")).isPositive();
        assertThat(total(page("/admin/customer?filter.active=false"))).isEqualTo(count("select count(c) from Customer c where c.active = false")).isEqualTo(1);
        assertThat(total(page("/admin/tag?filter.name=sale"))).isEqualTo(1);
    }

    @Test
    void aDateRangeOnOrdersKeepsTheWholeLastDay() throws Exception {
        LocalDate from = LocalDate.now().minusDays(20);
        LocalDate to = LocalDate.now().minusDays(5);

        String html = page("/admin/order?size=100&filter.createdAt.from=" + from + "&filter.createdAt.to=" + to);

        long expected = count("select count(o) from Order o where o.createdAt >= ?1 and o.createdAt < ?2", from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());
        assertThat(expected).isPositive();
        assertThat(total(html)).isEqualTo((int) expected);
        assertThat(chips(html)).contains(from + " to " + to);
        assertThat(notices(html)).isEmpty();
        assertThat(total(page("/admin/order?filter.createdAt.from=" + to.plusYears(10)))).isZero();
    }

    @Test
    void anOrderStatusAndACustomerFilterCombineAndTheTotalIsConsistentWithTheRows() throws Exception {
        long ava = count("select c.id from Customer c where c.fullName = 'Ava Stone'");

        String html = page("/admin/order?filter.customer=" + ava + "&filter.status=NEW&size=100");

        assertThat(total(html)).isEqualTo(count("select count(o) from Order o where o.customer.id = ?1 and o.status = io.github.mdmusahub.jpaadmin.sample.domain.OrderStatus.NEW", ava));
        assertThat(table(html)).hasSize(total(html));
    }

    // ---- combining ----------------------------------------------------------------------------------------------

    @Test
    void filtersCombineWithTheSearchTheSortAndThePagingAndEveryLinkKeepsThem() throws Exception {
        long books = categoryId("Books");
        String path = "/admin/product?filter.category=" + books + "&filter.status=PUBLISHED&filter.price.min=5&sort=price&dir=desc&size=10";
        String first = page(path);
        int expected = (int) count("select count(p) from Product p where p.category.id = ?1 and p.status = io.github.mdmusahub.jpaadmin.sample.domain.ProductStatus.PUBLISHED and p.price >= ?2", books, new BigDecimal("5"));

        assertThat(total(first)).isEqualTo(expected);
        List<BigDecimal> prices = column(first, 5).stream().map(BigDecimal::new).toList();
        assertThat(prices).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        // The sort headers, the page-size links and the pager keep the filters (the chips' remove links and "Clear all"
        // are meant to lack them, and are checked in the next tests).
        Matcher links = Pattern.compile("<a class=\"(?:sort-link|page-size|icon-button)\" href=\"([^\"]*)\"").matcher(first);
        int filtered = 0;
        while (links.find()) {
            String link = links.group(1).replace("&amp;", "&");
            filtered++;
            assertThat(link).as(link).contains("filter.category=" + books, "filter.status=PUBLISHED", "filter.price.min=5");
        }
        assertThat(filtered).isGreaterThan(8);
        String withSearch = page(path + "&q=a");
        assertThat(total(withSearch)).isLessThanOrEqualTo(expected);
        assertThat(withSearch).contains("name=\"q\"");
    }

    @Test
    void aFilterChipsRemoveLinkKeepsTheOtherFiltersTheSearchTheSortAndTheSizeAndDropsThePage() throws Exception {
        String html = page("/admin/product?q=a&filter.status=PUBLISHED&filter.featured=false&sort=price&dir=asc&size=10&page=1");
        List<String> removes = new ArrayList<>();
        Matcher remove = Pattern.compile("<a class=\"chip-remove\" href=\"([^\"]*)\"").matcher(html);
        while (remove.find()) {
            removes.add(remove.group(1).replace("&amp;", "&"));
        }

        assertThat(removes).hasSize(2);
        assertThat(removes).anyMatch(link -> link.contains("filter.featured=false") && !link.contains("filter.status"));
        assertThat(removes).anyMatch(link -> link.contains("filter.status=PUBLISHED") && !link.contains("filter.featured"));
        assertThat(removes).allSatisfy(link -> assertThat(link).contains("q=a", "sort=price", "dir=asc", "size=10").doesNotContain("page="));
        assertThat(flat(html)).contains(">Clear all filters</a>");
        assertThat(flat(html)).contains("href=\"/admin/product?q=a&amp;sort=price&amp;dir=asc&amp;size=10\">Clear all filters</a>");
    }

    @Test
    void aPageLinkKeepsItsPageAndAFilterChangeStartsAtTheFirstPage() throws Exception {
        String first = page("/admin/product?filter.status=PUBLISHED&size=10");
        String second = page("/admin/product?filter.status=PUBLISHED&size=10&page=1");

        assertThat(first).contains("Page 1 of 2", "href=\"/admin/product?filter.status=PUBLISHED&amp;page=1&amp;size=10\"");
        assertThat(second).contains("Page 2 of 2", "Showing 11");
        assertThat(second).contains("href=\"/admin/product?filter.status=PUBLISHED&amp;size=10\"").as("the way back to the first page");
        assertThat(page("/admin/product?filter.status=PUBLISHED&size=10&page=99")).as("a page past the end is the last page")
                .contains("Page 2 of 2");
    }

    // ---- what is ignored ------------------------------------------------------------------------------------------

    @Test
    void aFromAfterAToIsIgnoredAndTheNoticeSaysWhichFilterAndWhy() throws Exception {
        String html = page("/admin/order?filter.createdAt.from=2026-11-10&filter.createdAt.to=2026-11-01");

        assertThat(total(html)).isEqualTo(30);
        assertThat(notices(html)).containsExactly("The Created at filter was ignored: the 'from' date is after the 'to' date.");
        assertThat(html).contains("role=\"alert\"");
        assertThat(chips(html)).isEmpty();
    }

    @Test
    void everyInvalidValueIsIgnoredWithItsOwnNoticeAndTheListIsStillThere() throws Exception {
        String html = page("/admin/product?filter.price.min=abc&filter.price.max=20&filter.status=NOPE&filter.featured=maybe"
                + "&filter.createdAt.from=2026-02-30&filter.category=999999&size=100");

        assertThat(notices(html)).containsExactlyInAnyOrder(
                "The Price filter's minimum was ignored: it is not a number. Use digits, with a point for decimals.",
                "The Status filter was ignored: that is not one of its values.",
                "The Featured filter was ignored: use true, false or any.",
                "The Created at filter's 'from' date was ignored: it is not a valid date. Use yyyy-MM-dd, for example 2026-11-30.",
                "The Category filter was ignored: that choice is not available.");
        assertThat(total(html)).isEqualTo((int) count("select count(p) from Product p where p.price <= ?1", new BigDecimal("20")));
        assertThat(html).doesNotContain("NOPE", "maybe", "999999", "2026-02-30");
    }

    @Test
    void aPasswordFilterIsIgnoredShowsNoValueAndLooksLikeAnyUnknownName() throws Exception {
        for (String name : List.of("filter.password", "filter.password.min", "filter.password.max", "filter.PASSWORD",
                "filter.Password", "filter.pass_word", "filter.nosuch")) {
            String html = page("/admin/customer?" + name + "=" + Customer.DEMO_PASSWORD_PLACEHOLDER);

            assertThat(total(html)).as(name).isEqualTo(12);
            assertThat(notices(html)).as(name).containsExactly(UNKNOWN_FILTER);
            assertThat(html).as(name).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER).doesNotContain("demo-placeholder");
            assertThat(chips(html)).as(name).isEmpty();
            assertThat(html).as(name).doesNotContain("name=\"filter.password");
        }
    }

    @Test
    void aFilterOnAHiddenEntityIsTheSameNotFoundAsOneThatDoesNotExist() throws Exception {
        MockHttpServletResponse hidden = browser.getUri("/admin/internalauditentry?filter.action=x");
        MockHttpServletResponse unknown = browser.getUri("/admin/there-is-no-such-entity?filter.action=x");

        assertThat(hidden.getStatus()).isEqualTo(404);
        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat(hidden.getContentAsString().replaceAll("\\s+", " ")).isEqualTo(unknown.getContentAsString().replaceAll("\\s+", " "));
    }

    @Test
    void aHiddenFieldOrTheCollectionCannotBeFiltered() throws Exception {
        for (String name : List.of("filter.tags", "filter.id", "filter.internalCode")) {
            String html = page("/admin/product?" + name + "=1");

            assertThat(total(html)).as(name).isEqualTo(25);
            assertThat(notices(html)).as(name).containsExactly(UNKNOWN_FILTER);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"%3Cscript%3Ealert(1)%3C%2Fscript%3E", "%27%20or%201%3D1%20--", "%00", "a%26b%3Dc%23d", "%F0%9F%98%80", "%25", "_",
            "http%3A%2F%2Fx", "%5C", "%22"})
    void hostileFilterTextNeverBreaksThePageNorReachesItUnescaped(String value) throws Exception {
        String html = page("/admin/product?filter.name=" + value + "&filter.sku=" + value + "&filter.price.min=" + value
                + "&filter.status=" + value + "&filter.category=" + value);

        assertThat(html).contains("<h1 class=\"page-title\">Products</h1>").doesNotContain("<script>alert(1)");
        Matcher addresses = ADDRESS.matcher(html);
        while (addresses.find()) {
            String address = addresses.group(1).replace("&amp;", "&");
            if (address.startsWith("/admin")) {
                assertThat(address).doesNotContain(" ", "'", "\"", "<", ">", "\\", "://");
            }
        }
    }

    @Test
    void aTextFilterShownInTheChipAndTheControlIsEscaped() throws Exception {
        String html = page("/admin/product?filter.name=%3Cb%3Ebold%3C%2Fb%3E%20%26%20%22q%22");

        assertThat(html).doesNotContain("<b>bold</b>");
        assertThat(chips(html)).contains("&lt;b&gt;bold&lt;/b&gt; &amp; &quot;q&quot;");
        assertThat(html).contains("value=\"&lt;b&gt;bold&lt;/b&gt; &amp; &quot;q&quot;\"");
        Matcher remove = Pattern.compile("<a class=\"chip-remove\" href=\"([^\"]*)\"").matcher(html);
        assertThat(remove.find()).isTrue();
        assertThat(remove.group(1)).isEqualTo("/admin/product");
        assertThat(URLDecoder.decode("%3Cb%3E", StandardCharsets.UTF_8)).isEqualTo("<b>");
    }

    // ---- cost -----------------------------------------------------------------------------------------------------

    @Test
    void aFilteredPageCostsACountAPageAndOneSmallStatementPerRelationFilterAndLoadsNoEntity() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        long books = categoryId("Books");
        for (String path : List.of("/admin/product?filter.category=" + books + "&size=100",
                "/admin/product?filter.price.min=10&filter.price.max=90&filter.status=PUBLISHED&filter.featured=false&filter.name=a&sort=price&dir=desc",
                "/admin/order?filter.createdAt.from=2026-01-01&filter.status=NEW&filter.total.min=1",
                "/admin/customer?filter.active=true&filter.fullName=a", "/admin/tag?filter.name=e")) {
            page(path); // fills the sidebar's count cache
            statistics.clear();

            page(path);

            assertThat(statistics.getPrepareStatementCount()).as(path)
                    .isEqualTo(path.startsWith("/admin/product") || path.startsWith("/admin/order") ? 3 : 2);
            assertThat(statistics.getEntityLoadCount()).as(path + ": no entity is ever loaded (ARCH-3)").isZero();
            assertThat(statistics.getCollectionLoadCount()).as(path + ": no collection is ever read (ARCH-4)").isZero();
        }
    }

    @Test
    void theCategoryEntityKeepsItsOwnFilterWorking() throws Exception {
        assertThat(total(page("/admin/category?filter.name=oo"))).isEqualTo(count("select count(c) from Category c where lower(c.name) like '%oo%'"));
    }
}
