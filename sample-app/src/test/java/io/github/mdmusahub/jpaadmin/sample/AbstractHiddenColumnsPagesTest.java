package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 12 acceptance in the real demo application: the {@code hide} parameter through the real security chain, the real
 * transaction proxies and the real database. A hidden column is absent from the page and from the CSV export, a sensitive
 * column can never be hidden and never leaks, a request that would hide every column is ignored and says so, and hiding costs
 * no extra statement. Two subclasses run every test with {@code spring.jpa.open-in-view} on and off (D-29). The demo entity names
 * appear here because this is the sample application (ARCH-12).
 */
abstract class AbstractHiddenColumnsPagesTest {

    private static final Pattern HEADER = Pattern.compile("(?s)<th[^>]*scope=\"col\"[^>]*>(.*?)</th>");

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

    private String page(String path) throws Exception {
        MockHttpServletResponse response = browser.get(path);
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> headers(String html) {
        String table = html.substring(html.indexOf("<table"));
        List<String> found = new ArrayList<>();
        Matcher th = HEADER.matcher(table.substring(0, table.indexOf("</thead>")));
        while (th.find()) {
            String text = th.group(1).replaceAll("(?s)<[^>]*>", "").replaceAll("\\s+", " ").strip();
            if (!text.startsWith("Select all")) { // the checkbox column of bulk delete is not a data column
                found.add(text);
            }
        }
        return found;
    }

    private String csvHeader(String path) throws Exception {
        MockHttpServletResponse response = browser.get(path);
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        return body.substring(0, body.indexOf("\r\n"));
    }

    private String csv(String path) throws Exception {
        MockHttpServletResponse response = browser.get(path);
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private long statements(String path) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        page(path);
        return statistics.getPrepareStatementCount();
    }

    /** The first value of the column of the product export that has this header. */
    private String firstValue(String entity, String header) throws Exception {
        String[] lines = csv("/admin/" + entity + "/export.csv").split("\r\n");
        List<String> names = List.of(lines[0].split(","));
        int at = names.indexOf(header);
        assertThat(at).as(header + " in " + names).isGreaterThanOrEqualTo(0);
        return lines[1].split(",")[at];
    }

    // ---- the page ---------------------------------------------------------------------------------------------------

    @Test
    void hidingTheSkuTakesItOutOfTheProductListAndKeepsTheOtherColumns() throws Exception {
        String plain = page("/admin/product");
        String hidden = page("/admin/product?hide=sku");

        assertThat(headers(plain)).contains("Sku", "Name", "Price");
        assertThat(headers(hidden)).doesNotContain("Sku").contains("ID", "Name", "Price", "Category", "Status");
        String sku = firstValue("product", "Sku");
        assertThat(plain).contains(sku);
        assertThat(hidden).doesNotContain(">" + sku + "<");
        assertThat(hidden).contains("name=\"hide\"").containsPattern("value=\"sku\"[^>]*checked");
    }

    @Test
    void severalColumnsAndTheirOrderInTheAddressDoNotMatter() throws Exception {
        List<String> a = headers(page("/admin/product?hide=stock&hide=sku&hide=name"));
        List<String> b = headers(page("/admin/product?hide=name&hide=stock&hide=sku"));

        assertThat(a).isEqualTo(b).doesNotContain("Stock", "Sku", "Name");
    }

    @Test
    void theLinksOfAHiddenPageKeepTheHiddenColumnsAndTheSizeLinksStartAgainAtTheFirstPage() throws Exception {
        String html = page("/admin/product?hide=sku&size=10&page=1");

        assertThat(html).contains("/admin/product/export.csv?hide=sku");
        assertThat(html).contains("href=\"/admin/product?hide=sku&amp;page=2&amp;size=10\"");
        assertThat(html).contains("href=\"/admin/product?hide=sku&amp;size=50\"");
        assertThat(html).doesNotContain("size=50&amp;page");
        assertThat(html).contains("data-views-state=\"/admin/product?hide=sku&amp;size=10\"");
    }

    @Test
    void aRowLinkAndTheWayBackFromTheDetailPageKeepTheHiddenColumns() throws Exception {
        String html = page("/admin/product?hide=sku");
        Matcher link = Pattern.compile("href=\"(/admin/product/\\d+\\?hide=sku)\"").matcher(html);
        assertThat(link.find()).as("a row link that carries the hidden columns").isTrue();

        String detail = page(link.group(1).replace("&amp;", "&"));

        assertThat(detail).contains("href=\"/admin/product?hide=sku\"");
    }

    @Test
    void aBulkDeleteThatDeletesNothingReturnsToTheSameListWithTheSameHiddenColumns() throws Exception {
        String list = page("/admin/product?hide=sku&hide=stock&size=10");
        Matcher action = Pattern.compile("data-bulk-form[^>]*action=\"([^\"]+)\"").matcher(list);
        assertThat(action.find()).as("the bulk form").isTrue();
        assertThat(action.group(1).replace("&amp;", "&")).isEqualTo("/admin/product/bulk-delete?hide=sku&hide=stock&size=10");

        MockHttpServletResponse response = browser.postUri(action.group(1).replace("&amp;", "&"), "_csrf", MockBrowser.csrf(list));

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/admin/product?hide=sku&hide=stock&size=10");
    }

    @Test
    void whenTheIdentifierIsHiddenTheRowLinkMovesToTheFirstTextCell() throws Exception {
        String html = page("/admin/product?hide=id");

        assertThat(headers(html)).doesNotContain("ID");
        assertThat(html).containsPattern("<a class=\"cell-link[^\"]*\" href=\"/admin/product/\\d+\\?hide=id\"");
    }

    @Test
    void aColumnThatIsHiddenCanStillBeSortedBy() throws Exception {
        String html = page("/admin/product?hide=price&sort=price&dir=desc");

        assertThat(headers(html)).doesNotContain("Price");
        assertThat(html).contains("sort=price&amp;dir=desc");
    }

    @Test
    void filtersAndSearchStillWorkOnAHiddenColumn() throws Exception {
        String all = page("/admin/product");
        String filtered = page("/admin/product?hide=status&filter.status=PUBLISHED");

        assertThat(filtered).containsPattern("filter-count[^>]*>\\s*1\\s*<");
        assertThat(headers(filtered)).doesNotContain("Status");
        assertThat(filtered.length()).isLessThan(all.length() + 20_000);
        assertThat(page("/admin/product?hide=name&q=zzzz-nothing")).contains("No matching records");
    }

    @Test
    void aRequestThatHidesEveryColumnIsIgnoredAndThePageSaysSo() throws Exception {
        String html = page("/admin/product?hide=id&hide=category&hide=createdAt&hide=featured&hide=name&hide=price&hide=sku"
                + "&hide=status&hide=stock");

        assertThat(html).contains("Every column was hidden, so all columns are shown. A list needs at least one column.");
        assertThat(headers(html)).contains("ID", "Name", "Sku", "Price", "Stock");
        assertThat(html).doesNotContainPattern("name=\"hide\"[^>]*checked");
    }

    @Test
    void hidingEveryColumnButOneIsAllowedAndHasNoNotice() throws Exception {
        String html = page("/admin/product?hide=id&hide=category&hide=createdAt&hide=featured&hide=name&hide=price&hide=sku"
                + "&hide=status");

        assertThat(headers(html)).containsExactly("Stock");
        assertThat(html).doesNotContain("Every column was hidden");
    }

    @Test
    void aHostileHideRequestNeverBreaksThePage() throws Exception {
        StringBuilder query = new StringBuilder("?");
        for (int i = 0; i < 300; i++) {
            query.append("hide=junk").append(i).append('&');
        }
        query.append("hide=").append("x".repeat(5_000)).append("&hide=weight%2Cstock&hide=%00&hide=%3Cscript%3E");

        String html = page("/admin/product" + query);

        assertThat(headers(html)).contains("ID", "Name", "Sku");
        assertThat(html).doesNotContain("Every column was hidden").doesNotContain("<script>alert");
    }

    @Test
    void hidingCostsNoExtraStatement() throws Exception {
        long plain = statements("/admin/product");
        long hidden = statements("/admin/product?hide=category&hide=sku&hide=price");

        assertThat(hidden).as("the same statements, with fewer joins and a shorter select list").isLessThanOrEqualTo(plain);
    }

    // ---- sensitive columns ------------------------------------------------------------------------------------------

    @Test
    void aSensitiveColumnCanNeverBeHiddenAndNothingRevealsIt() throws Exception {
        String password = secret("select c.password from Customer c order by c.id");
        String plain = page("/admin/customer");
        String asked = page("/admin/customer?hide=password");

        assertThat(headers(asked)).as("the masked column is still on the list, as dots").isEqualTo(headers(plain));
        assertThat(asked).doesNotContain(password).doesNotContain("hide=password");
        assertThat(asked).doesNotContainPattern("value=\"password\"");
        assertThat(asked).doesNotContain("Every column was hidden");
        assertThat(asked).contains("cell-masked");
    }

    @Test
    void theHideRequestForASensitiveNameIsTheSameAnswerAsForANameThatDoesNotExist() throws Exception {
        assertThat(headers(page("/admin/customer?hide=password"))).isEqualTo(headers(page("/admin/customer?hide=nothing")));
        assertThat(page("/admin/customer?hide=password").length()).isEqualTo(page("/admin/customer?hide=nothing").length());
    }

    @Test
    void aSavedViewScriptIsHandedNoSensitiveKey() throws Exception {
        String html = page("/admin/customer?hide=fullName");

        Matcher columns = Pattern.compile("data-views-columns=\"([^\"]*)\"").matcher(html);
        assertThat(columns.find()).isTrue();
        assertThat(List.of(columns.group(1).split(" "))).doesNotContain("password").contains("id", "email");
        assertThat(headers(html)).doesNotContain("Full name");
    }

    @Test
    void hidingASensitiveColumnBesideAValidOneHidesOnlyTheValidOne() throws Exception {
        List<String> headers = headers(page("/admin/customer?hide=password&hide=email"));

        assertThat(headers).doesNotContain("Email").contains("Password");
    }

    // ---- the export follows the list --------------------------------------------------------------------------------

    @Test
    void theExportHeaderHasNoHiddenColumn() throws Exception {
        assertThat(csvHeader("/admin/product/export.csv")).contains("Sku");

        String header = csvHeader("/admin/product/export.csv?hide=sku");

        assertThat(header).doesNotContain("Sku").contains("ID", "Name", "Price");
        String body = csv("/admin/product/export.csv?hide=sku");
        assertThat(body.split("\r\n")).as("a header and the 25 products").hasSize(26);
        assertThat(body).doesNotContain(firstValue("product", "Sku") + ",");
    }

    @Test
    void theExportFollowsTheSameColumnsAsTheList() throws Exception {
        String query = "hide=price&hide=stock&hide=category";
        List<String> listed = headers(page("/admin/product?" + query));

        List<String> exported = List.of(csvHeader("/admin/product/export.csv?" + query).split(","));

        assertThat(exported).containsExactlyElementsOf(listed);
    }

    @Test
    void theCustomerExportNeverHasAPasswordColumnOrValueWhateverIsHidden() throws Exception {
        String password = secret("select c.password from Customer c order by c.id");

        for (String query : List.of("", "?hide=password", "?hide=email&hide=password", "?hide=fullName")) {
            String body = csv("/admin/customer/export.csv" + query);
            assertThat(body.split("\r\n")[0]).as(query).doesNotContain("Password");
            assertThat(body).as(query).doesNotContain(password);
        }
        assertThat(csvHeader("/admin/customer/export.csv?hide=email")).doesNotContain("Email");
    }

    @Test
    void theExportOfARequestThatHidesEveryColumnHasEveryColumn() throws Exception {
        String everything = "?hide=id&hide=category&hide=createdAt&hide=featured&hide=name&hide=price&hide=sku&hide=status&hide=stock";

        assertThat(csvHeader("/admin/product/export.csv" + everything)).isEqualTo(csvHeader("/admin/product/export.csv"));
    }

    @Test
    void anUnknownEntityWithAHideIsTheSameNotFoundAsWithout() throws Exception {
        assertThat(browser.get("/admin/nothing-here?hide=a").getStatus()).isEqualTo(404);
        assertThat(browser.get("/admin/nothing-here/export.csv?hide=a").getStatus()).isEqualTo(404);
    }

    @Test
    void aVisitorWithNoSessionIsSentToTheSignInPageNotToAList() throws Exception {
        MockHttpServletResponse response = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/product?hide=sku")).andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).endsWith("/admin/login");
    }

    private String secret(String jpql) {
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            return em.createQuery(jpql, String.class).setMaxResults(1).getSingleResult();
        }
    }
}
