package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 3 acceptance in the real demo application: a signed-in admin sees every demo entity in the sidebar with the
 * count the seed data really has, the dashboard adds them up, one entity's page answers 200 with the notice, and an
 * address that is not an entity answers the console's own 404.
 *
 * <p>The demo entity names appear here because this is the sample application, not a library module (ARCH-12). The
 * counts come from {@code DemoDataLoader}'s constants, so the test says what the data says and not a number
 * somebody typed twice.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConsoleDiscoversEntitiesTest {

    /** The demo entities, their address and the number of rows the loader seeds. */
    private static final Map<String, Integer> EXPECTED = Map.of(
            "category", DemoDataLoader.CATEGORY_COUNT,
            "customer", DemoDataLoader.CUSTOMER_COUNT,
            "order", DemoDataLoader.ORDER_COUNT,
            "product", DemoDataLoader.PRODUCT_COUNT,
            "tag", DemoDataLoader.TAG_COUNT);

    @Autowired
    private MockMvc mvc;

    private String dashboard() throws Exception {
        MockHttpServletResponse response = MockBrowser.signedIn(mvc).get("/admin");
        assertThat(response.getStatus()).isEqualTo(200);
        return response.getContentAsString();
    }

    private static List<String> sidebarEntities(String html) {
        Matcher items = Pattern.compile("<li data-nav-item=\"([^\"]+)\">").matcher(html);
        List<String> labels = new java.util.ArrayList<>();
        while (items.find()) {
            labels.add(items.group(1));
        }
        return labels;
    }

    // ---- the sidebar ------------------------------------------------------------------------------------------

    @Test
    void theSidebarListsExactlyTheFiveDemoEntitiesWithTheirCounts() throws Exception {
        String html = dashboard();

        assertThat(sidebarEntities(html)).containsExactly("Category", "Customer", "Order", "Product", "Tag");
        EXPECTED.forEach((entity, count) -> assertThat(html)
                .as(entity).contains("href=\"/admin/" + entity + "\"")
                .contains("<span class=\"nav-count\">" + count + "</span>"));
        assertThat(html).contains("<span class=\"brand-subtitle\">Administration</span>",
                "<span class=\"user-name\">" + MockBrowser.USER + "</span>");
        assertThat(html).doesNotContain("unavailable");
    }

    @Test
    void theDashboardAddsUpEveryEntityAndNamesTheLargestTable() throws Exception {
        String html = dashboard();
        int total = EXPECTED.values().stream().mapToInt(Integer::intValue).sum();

        assertThat(html).contains("<p class=\"stat-value\">" + total + "</p>",
                "Across " + EXPECTED.size() + " entities",
                "<p class=\"stat-value\">" + EXPECTED.size() + "</p>",
                "<p class=\"stat-value stat-value-text\">Order</p>",
                DemoDataLoader.ORDER_COUNT + " records");
        assertThat(html).contains("class=\"card group-card\"", ">Entities</h2>");
    }

    @Test
    void everyLinkOnTheDashboardLiesUnderTheConsolePath() throws Exception {
        String html = dashboard();

        Matcher addresses = Pattern.compile("\\b(?:href|src|action)=\"([^\"]*)\"").matcher(html);
        int found = 0;
        while (addresses.find()) {
            String address = addresses.group(1);
            found++;
            if (!address.equals("#main") && !address.equals("data:,")) {
                assertThat(address).as(address).startsWith("/admin");
            }
        }
        assertThat(found).isGreaterThan(10);
    }

    // ---- one entity's page -----------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"category", "customer", "order", "product", "tag"})
    void everyDemoEntityHasAPageThatSaysTheTableIsNotAvailableYet(String entity) throws Exception {
        MockHttpServletResponse response = MockBrowser.signedIn(mvc).get("/admin/" + entity);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString())
                .contains("This table view is not available yet.")
                .contains("href=\"/admin/" + entity + "\" aria-current=\"page\"")
                .contains("<a class=\"btn\" href=\"/admin\">Go to dashboard</a>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"nothing", "orders", "Product", "PRODUCT", "product2", "demodataloader",
            "orderstatus", "productstatus"})
    void anAddressThatIsNotAnEntityGetsTheConsolesOwnNotFoundPage(String address) throws Exception {
        MockHttpServletResponse response = MockBrowser.signedIn(mvc).get("/admin/" + address);

        String html = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(html).contains("<p class=\"error-status\">404</p>", "Page not found");
        // The page the console builds says nothing about what was typed. (The shell around it still lists the real
        // entities, which is why only the page's own content is read here.)
        assertThat(content(html)).as(address).doesNotContain(address);
        assertThat(html).doesNotContain("/admin/" + address);
    }

    /** The page's own content, without the shell around it. */
    private static String content(String html) {
        Matcher main = Pattern.compile("(?s)<main\\b[^>]*>(.*?)</main>").matcher(html);
        assertThat(main.find()).isTrue();
        return main.group(1);
    }

    @Test
    void anEnumIsNotAnEntityAndNeitherIsAnythingElseOnTheClasspath() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);

        assertThat(browser.get("/admin/orderstatus").getStatus()).isEqualTo(404);
        assertThat(browser.get("/admin/string").getStatus()).isEqualTo(404);
        assertThat(sidebarEntities(browser.get("/admin").getContentAsString()))
                .doesNotContain("Order status", "Product status");
    }

    // ---- sensitive values ------------------------------------------------------------------------------------

    @Test
    void theSeededPasswordValueIsInNoResponseAnywhereInTheConsole() throws Exception {
        MockBrowser browser = MockBrowser.signedIn(mvc);

        for (String path : List.of("/admin", "/admin/customer", "/admin/product", "/admin/login", "/admin/nothing")) {
            String body = browser.get(path).getContentAsString();
            assertThat(body).as(path).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER)
                    .doesNotContain(MockBrowser.PASSWORD);
        }
    }

    @Test
    void theConsoleNeverShowsAFieldNameOfASensitiveColumnEither() throws Exception {
        String customers = MockBrowser.signedIn(mvc).get("/admin/customer").getContentAsString();

        assertThat(customers).doesNotContain("password", "Password");
    }
}
