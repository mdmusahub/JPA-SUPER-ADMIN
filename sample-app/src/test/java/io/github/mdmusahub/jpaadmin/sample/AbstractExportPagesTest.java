package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.sample.domain.Category;
import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Phase 11 acceptance in the real demo application: sign in, open a list, take its CSV, with the real security chain (so a
 * missing session and a missing CSRF token are really refused), the real transaction proxies and the real database. The
 * demo has 25 products, 12 customers (with a password column) and 30 orders. Two subclasses run every test with
 * {@code spring.jpa.open-in-view} on and off (D-29). The demo entity names appear here because this is the sample application
 * (ARCH-12).
 */
abstract class AbstractExportPagesTest {

    private static final Pattern FILE_NAME = Pattern.compile("attachment; filename=\"([a-z0-9-]+)-(\\d{4}-\\d{2}-\\d{2})\\.csv\"");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private MockBrowser browser;

    @BeforeEach
    void signIn() throws Exception {
        browser = MockBrowser.signedIn(mvc);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private MockHttpServletResponse export(String entity, String query) throws Exception {
        return browser.get("/admin/" + entity + "/export.csv" + query);
    }

    private static String body(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private List<List<String>> table(String entity, String query) throws Exception {
        MockHttpServletResponse response = export(entity, query);
        assertThat(response.getStatus()).as(entity + query).isEqualTo(200);
        return parse(body(response));
    }

    private static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\r') {
                continue;
            } else if (c == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        return rows;
    }

    private static List<String> column(List<List<String>> table, String header) {
        int at = table.get(0).indexOf(header);
        assertThat(at).as("a column headed '%s' in %s", header, table.get(0)).isGreaterThanOrEqualTo(0);
        return table.stream().skip(1).map(row -> row.get(at)).toList();
    }

    // ---- the file --------------------------------------------------------------------------------------------------------

    @Test
    void theProductExportIsADownloadWithTheSafeHeadersAndAFileNameMadeOfTheEntityAndTheDate() throws Exception {
        MockHttpServletResponse response = export("product", "");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("text/csv").containsIgnoringCase("charset=UTF-8");
        var name = FILE_NAME.matcher(response.getHeader("Content-Disposition"));
        assertThat(name.matches()).as(response.getHeader("Content-Disposition")).isTrue();
        assertThat(name.group(1)).isEqualTo("product");
        assertThat(LocalDate.parse(name.group(2))).isBetween(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertThat(response.getHeaders("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getHeader("X-Export-Truncated")).isNull();
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void theProductExportHasAHeaderAndTwentyFiveRowsWithPlainValues() throws Exception {
        List<List<String>> table = table("product", "");

        assertThat(table.get(0)).contains("ID", "Name", "Sku", "Price", "Stock", "Status", "Featured", "Category", "Created at")
                .noneMatch(label -> label.toLowerCase(Locale.ROOT).contains("tag"));
        assertThat(table).hasSize(1 + 25);
        assertThat(table).allSatisfy(row -> assertThat(row).hasSameSizeAs(table.get(0)));
        assertThat(column(table, "Price")).allSatisfy(price -> assertThat(price).matches("\\d+\\.\\d{2}"));
        assertThat(column(table, "Featured")).containsOnly("true", "false");
        assertThat(column(table, "Status")).allSatisfy(status -> assertThat(status).matches("[A-Z_]+"));
        assertThat(column(table, "Created at")).allSatisfy(at -> assertThat(at).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
        assertThat(column(table, "ID")).doesNotHaveDuplicates();
    }

    @Test
    void aRelationIsExportedAsTheLabelTheListShows() throws Exception {
        List<String> categories = column(table("category", ""), "Name");

        assertThat(categories).hasSize(6);
        assertThat(column(table("product", ""), "Category")).isNotEmpty().allSatisfy(label -> assertThat(categories).contains(label));
    }

    @Test
    void theCustomerExportNeverHasThePasswordColumnOrItsValue() throws Exception {
        MockHttpServletResponse response = export("customer", "");
        List<List<String>> table = parse(body(response));

        assertThat(table).hasSize(1 + 12);
        assertThat(table.get(0)).contains("ID", "Full name", "Email", "Active").noneMatch(label -> label.toLowerCase(Locale.ROOT)
                .contains("password"));
        assertThat(body(response)).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER).doesNotContain(MockBrowser.PASSWORD);
        assertThat(body(response).toLowerCase(Locale.ROOT)).doesNotContain("password");
    }

    @Test
    void theCustomerExportWithEverySortAndSearchTryingToReachThePasswordStillLeaksNothing() throws Exception {
        for (String query : List.of("?sort=password&dir=desc", "?q=placeholder", "?q=demo-placeholder-secret",
                "?filter.password=demo", "?sort=password")) {
            MockHttpServletResponse response = export("customer", query);

            assertThat(response.getStatus()).as(query).isEqualTo(200);
            assertThat(body(response)).as(query).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER);
        }
        assertThat(table("customer", "?q=demo-placeholder-secret")).as("a search never looks in the password").hasSize(1);
    }

    // ---- search, filter, sort -----------------------------------------------------------------------------------------

    @Test
    void theSearchIsRespected() throws Exception {
        List<List<String>> all = table("product", "");
        String sku = column(all, "Sku").get(7);

        List<List<String>> found = table("product", "?q=" + sku.toLowerCase(Locale.ROOT));

        assertThat(found).hasSize(2);
        assertThat(column(found, "Sku")).containsExactly(sku);
    }

    @Test
    void theSortIsRespectedInBothDirections() throws Exception {
        List<BigDecimal> ascending = column(table("product", "?sort=price&dir=asc"), "Price").stream().map(BigDecimal::new).toList();
        List<BigDecimal> descending = column(table("product", "?sort=price&dir=desc"), "Price").stream().map(BigDecimal::new).toList();

        assertThat(ascending).hasSize(25).isSorted();
        assertThat(descending).hasSize(25).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(table("product", "?sort=category&dir=asc")).as("a sort by a relation's label").hasSize(26);
    }

    @Test
    void aFilterIsRespectedAndCombinesWithTheSort() throws Exception {
        List<String> statuses = column(table("product", ""), "Status");
        String one = statuses.get(0);
        long expected = statuses.stream().filter(one::equals).count();

        List<List<String>> filtered = table("product", "?filter.status=" + one + "&sort=price&dir=desc");

        assertThat(filtered).hasSize(1 + (int) expected);
        assertThat(column(filtered, "Status")).containsOnly(one);
        assertThat(column(filtered, "Price").stream().map(BigDecimal::new).toList()).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void pageAndSizeMeanNothingToTheFile() throws Exception {
        assertThat(table("product", "?page=2&size=10")).hasSize(26);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?sort=nope", "?filter.nope=1", "?dir=sideways", "?q=%", "?q=_", "?size=999&page=-4"})
    void awkwardOrUnknownParametersNeverFailTheExport(String query) throws Exception {
        assertThat(export("product", query).getStatus()).isEqualTo(200);
    }

    // ---- the guard (SEC-10), a record that holds a formula ----------------------------------------------------------

    @Test
    void aFormulaStoredInARecordIsExportedGuardedAndIsRestoredAfterwards() throws Exception {
        String original = setCategoryName(1L, "=HYPERLINK(\"http://x\",\"y\")");
        try {
            MockHttpServletResponse response = export("category", "");

            assertThat(column(parse(body(response)), "Name")).contains("'=HYPERLINK(\"http://x\",\"y\")");
            assertThat(body(response)).doesNotContain(",=HYPERLINK").doesNotContain("\n=HYPERLINK");
        } finally {
            setCategoryName(1L, original);
        }
    }

    @Test
    void aStoredFileAddressIsExportedAsTextAndNothingMore() throws Exception {
        String address = "/admin/files/default/categories/2026/11/0123456789abcdef0123456789abcdef.png";
        setCategoryImage(1L, address);
        try {
            List<List<String>> table = table("category", "");

            assertThat(column(table, "Image")).contains(address);
        } finally {
            setCategoryImage(1L, null);
        }
    }

    private String setCategoryName(Long id, String name) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            Category category = em.find(Category.class, id);
            String before = category.getName();
            category.setName(name);
            em.getTransaction().commit();
            return before;
        } finally {
            em.close();
        }
    }

    private void setCategoryImage(Long id, String address) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            em.find(Category.class, id).setImage(address);
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    // ---- who may ask, and what is refused --------------------------------------------------------------------------------

    @Test
    void withoutASessionTheExportRedirectsToTheSignInPageAndShowsNoData() throws Exception {
        MockHttpServletResponse response = mvc.perform(MockMvcRequestBuilders.get("/admin/product/export.csv")).andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).endsWith("/admin/login");
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void anUnknownAndAHiddenEntityAreOneAndTheSameNotFound() throws Exception {
        MockHttpServletResponse unknown = export("nothing-like-this", "");
        MockHttpServletResponse hidden = export("internalauditentry", "");

        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat(hidden.getStatus()).isEqualTo(404);
        assertThat(body(hidden)).isEqualTo(body(unknown));
        assertThat(hidden.getContentType()).isEqualTo(unknown.getContentType());
        assertThat(hidden.getHeader("Content-Disposition")).isNull();
    }

    @Test
    void aPostIsRefusedWithoutTheTokenAndWithOneItIsNotAnExport() throws Exception {
        String token = MockBrowser.csrf(browser.get("/admin/product/new").getContentAsString());

        MockHttpServletResponse withoutToken = browser.post("/admin/product/export.csv");
        MockHttpServletResponse withToken = browser.post("/admin/product/export.csv", "_csrf", token);

        assertThat(withoutToken.getStatus()).isEqualTo(403);
        assertThat(withToken.getStatus()).isBetween(400, 499);
        assertThat(withToken.getContentType()).doesNotContain("csv");
        assertThat(withToken.getHeader("Content-Disposition")).isNull();
    }

    // ---- the list page's button and what the export costs -----------------------------------------------------------

    @Test
    void theListOffersTheLinkWithTheCurrentSearchSortAndFiltersAndNoPageOrSize() throws Exception {
        String plain = browser.get("/admin/product").getContentAsString();
        String carried = browser.getUri("/admin/product?q=bulk%20item&sort=price&dir=desc&filter.featured=true&page=1&size=10")
                .getContentAsString();

        assertThat(plain).contains("href=\"/admin/product/export.csv\"", "<span>Export CSV</span>",
                "aria-label=\"Export CSV of the matching records\"");
        assertThat(carried).contains("href=\"/admin/product/export.csv?q=bulk%20item&amp;filter.featured=true&amp;sort=price&amp;dir=desc\"");
    }

    @Test
    void everyEntityOfTheConsoleOffersTheExportOnItsList() throws Exception {
        for (String entity : List.of("category", "customer", "order", "product", "tag")) {
            assertThat(browser.get("/admin/" + entity).getContentAsString()).as(entity)
                    .contains("href=\"/admin/" + entity + "/export.csv");
        }
    }

    @Test
    void anExportCostsACountAndOnePageStatementsAndLoadsNoEntity() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        for (String entity : List.of("product", "order", "customer", "category", "tag")) {
            export(entity, ""); // the first request fills nothing the export needs, but keeps the comparison honest
            statistics.clear();

            MockHttpServletResponse response = export(entity, "?sort=id&dir=desc");

            assertThat(response.getStatus()).as(entity).isEqualTo(200);
            long statements = statistics.getPrepareStatementCount();
            assertThat(statements).as(entity + ": a count, one list of choices per filterable relation, and one page (all rows fit in one)")
                    .isBetween(2L, 4L);
            assertThat(statistics.getEntityLoadCount()).as(entity + ": no entity is ever loaded (ARCH-3)").isZero();
            assertThat(statistics.getCollectionLoadCount()).as(entity + ": no collection is ever loaded (ARCH-4)").isZero();
        }
    }
}
