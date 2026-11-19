package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mdmusahub.jpaadmin.sample.domain.Customer;
import io.github.mdmusahub.jpaadmin.service.CountService;
import io.github.mdmusahub.jpaadmin.service.StatsService;
import jakarta.persistence.EntityManagerFactory;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 10 acceptance in the real demo application, through the real flow: sign in, open the dashboard, ask the JSON
 * endpoint, with the real security chain (so a missing session and a missing CSRF token are really refused), the real
 * transaction proxies and the real database. The clock is fixed at noon UTC on 2026-11-10 ({@link FixedClockConfiguration}),
 * so the seed is known to the day:
 * <ul>
 *   <li>orders: 30, one on each of the last 30 days (offsets {@code (i * 11) % 30} are all different);</li>
 *   <li>products: 25, one on each of the last 25 days;</li>
 *   <li>customers: 12, on offsets 1, 3, 5 ... 23;</li>
 *   <li>categories: 6, on offsets 0, 5, 10, 15, 20, 25;</li>
 *   <li>tags: no creation field, so not in the chart.</li>
 * </ul>
 * Two subclasses run every test with {@code spring.jpa.open-in-view} on and off (D-29). The demo entity names appear here
 * because this is the sample application (ARCH-12).
 */
abstract class AbstractChartPagesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> NAMES = List.of("category", "customer", "order", "product");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private StatsService stats;

    @Autowired
    private CountService counts;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private MockBrowser browser;

    @BeforeEach
    void signInAndForgetTheCaches() throws Exception {
        browser = MockBrowser.signedIn(mvc);
        stats.clear();
        counts.clear();
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private JsonNode json(String query) throws Exception {
        MockHttpServletResponse response = browser.get("/admin/api/stats" + query);
        assertThat(response.getStatus()).isEqualTo(200);
        return JSON.readTree(response.getContentAsString());
    }

    private static int indexOf(JsonNode json, String name) {
        for (int i = 0; i < json.get("entities").size(); i++) {
            if (json.get("entities").get(i).get("name").asString().equals(name)) {
                return i;
            }
        }
        throw new AssertionError("not in the chart: " + name);
    }

    private static long total(JsonNode json, String name) {
        return json.get("entities").get(indexOf(json, name)).get("total").longValue();
    }

    private static List<Long> column(JsonNode json, String name) {
        int i = indexOf(json, name);
        List<Long> counts = new ArrayList<>();
        json.get("days").forEach(day -> counts.add(day.get("counts").get(i).longValue()));
        return counts;
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private static String card(String html) {
        Matcher card = Pattern.compile("(?s)<section class=\"card chart-card\".*?</section>").matcher(html);
        assertThat(card.find()).as("the chart card is on the page").isTrue();
        return card.group();
    }

    private static int count(String text, String needle) {
        int found = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            found++;
        }
        return found;
    }

    // ---- the dashboard ----------------------------------------------------------------------------------------------

    @Test
    void theDashboardShowsTheChartCardWithTheFourEntitiesThatHaveACreationFieldAndNotTag() throws Exception {
        MockHttpServletResponse response = browser.get("/admin");
        String html = response.getContentAsString();
        String card = card(html);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(card).contains(">Records created<", "Last 30 days, per day");
        for (String label : List.of("Category", "Customer", "Order", "Product")) {
            assertThat(card).contains("<span class=\"chart-legend-name\">" + label + "</span>");
        }
        assertThat(card).doesNotContain("<span class=\"chart-legend-name\">Tag</span>");
        assertThat(card.replaceAll("\\s+", " ")).contains("Not in the chart: <span>Tag</span>. They have no creation date field.");
        assertThat(count(card, "chart-legend-item")).isEqualTo(4);
        assertThat(count(card, "<g class=\"chart-bar\">")).isEqualTo(30);
    }

    @Test
    void theRangeLinksLieUnderTheBasePathAndOnlyTheCurrentOneIsMarked() throws Exception {
        String card = card(browser.get("/admin?range=90").getContentAsString());

        assertThat(card).contains("href=\"/admin?range=7\"", "href=\"/admin?range=30\"")
                .contains("is-current\" href=\"/admin?range=90\" aria-current=\"true\"");
        assertThat(count(card, "aria-current")).isEqualTo(1);
        Matcher links = Pattern.compile("href=\"([^\"]*)\"").matcher(card);
        int seen = 0;
        while (links.find()) {
            seen++;
            assertThat(links.group(1)).startsWith("/admin");
        }
        assertThat(seen).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 30, 90})
    void eachRangeDrawsOneBarPerDay(int range) throws Exception {
        String card = card(browser.get("/admin?range=" + range).getContentAsString());

        assertThat(count(card, "<g class=\"chart-bar\">")).isEqualTo(range);
        assertThat(card).contains("viewBox=\"0 0 " + range * 10 + " 1000\"", "Last " + range + " days, per day");
    }

    @ParameterizedTest
    @ValueSource(strings = {"45", "0", "abc", "", "7;30", "%3Cscript%3E", "9999999999999"})
    void aRangeThatIsNotOfferedFallsBackToThirtyAndThePageSaysSo(String range) throws Exception {
        String card = card(browser.getUri("/admin?range=" + range).getContentAsString());

        assertThat(card).contains("Last 30 days, per day", "That range is not available, so the last 30 days are shown.")
                .contains("is-current\" href=\"/admin?range=30\" aria-current=\"true\"");
        assertThat(card).doesNotContain("script");
    }

    @Test
    void noRangeIsThirtyDaysWithoutANote() throws Exception {
        String card = card(browser.get("/admin").getContentAsString());

        assertThat(card).doesNotContain("That range is not available").doesNotContain("chart-notes");
    }

    @Test
    void theCardHasNoNestedFormNothingInlineAndNoScriptOfItsOwn() throws Exception {
        String html = browser.get("/admin").getContentAsString();
        String card = card(html);

        assertThat(card).doesNotContain("<form").doesNotContain(" style=").doesNotContain("<style")
                .doesNotContain("<script").doesNotContainPattern("(?i)\\son[a-z]+=");
        assertThat(count(html, "<form")).as("only the sign-out form of the page").isEqualTo(count(html, "</form>"));
        assertThat(card).contains("aria-label=\"Chart range\"", "aria-label=\"Entities in the chart\"",
                "aria-label=\"Daily counts table\"", "aria-labelledby=\"chart-title\"");
    }

    @Test
    void theChartNeverShowsASensitiveValueOrAHiddenEntity() throws Exception {
        String page = browser.get("/admin").getContentAsString();
        String body = browser.get("/admin/api/stats").getContentAsString();

        for (String text : List.of(card(page), body)) {
            assertThat(text).doesNotContain(Customer.DEMO_PASSWORD_PLACEHOLDER).doesNotContain(MockBrowser.PASSWORD)
                    .doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("internalaudit")
                    .doesNotContainIgnoringCase("happenedat");
        }
    }

    // ---- the numbers ------------------------------------------------------------------------------------------------

    @Test
    void theSevenThirtyAndNinetyDayTotalsMatchTheKnownSeed() throws Exception {
        JsonNode seven = json("?range=7");
        JsonNode thirty = json("?range=30");
        JsonNode ninety = json("?range=90");

        assertThat(List.of(total(seven, "category"), total(seven, "customer"), total(seven, "order"),
                total(seven, "product"))).containsExactly(2L, 3L, 7L, 7L);
        assertThat(List.of(total(thirty, "category"), total(thirty, "customer"), total(thirty, "order"),
                total(thirty, "product"))).containsExactly(6L, 12L, 30L, 25L);
        assertThat(List.of(total(ninety, "category"), total(ninety, "customer"), total(ninety, "order"),
                total(ninety, "product"))).containsExactly(6L, 12L, 30L, 25L);
    }

    @Test
    void theOrdersAreOnePerDayOverTheLastThirtyDays() throws Exception {
        JsonNode thirty = json("?range=30");
        JsonNode ninety = json("?range=90");

        assertThat(column(thirty, "order")).hasSize(30).containsOnly(1L);
        List<Long> orders = column(ninety, "order");
        assertThat(orders.subList(0, 60)).containsOnly(0L);
        assertThat(orders.subList(60, 90)).containsOnly(1L);
        assertThat(thirty.get("days").get(29).get("date").asString()).isEqualTo(TODAY.toString());
        assertThat(thirty.get("days").get(0).get("date").asString()).isEqualTo(TODAY.minusDays(29).toString());
    }

    @Test
    void theCategoriesAreOnTheirSeedDays() throws Exception {
        List<Long> categories = column(json("?range=30"), "category");

        for (int offset = 0; offset < 30; offset++) {
            assertThat(categories.get(29 - offset)).as("offset " + offset).isEqualTo(offset % 5 == 0 ? 1L : 0L);
        }
    }

    @Test
    void theCustomersAreOnTheirSeedDays() throws Exception {
        List<Long> customers = column(json("?range=30"), "customer");

        for (int offset = 0; offset < 30; offset++) {
            boolean seeded = offset % 2 == 1 && offset <= 23;
            assertThat(customers.get(29 - offset)).as("offset " + offset).isEqualTo(seeded ? 1L : 0L);
        }
    }

    @Test
    void thePageAndTheEndpointAgreeOnTheTotals() throws Exception {
        String card = card(browser.get("/admin?range=30").getContentAsString());

        assertThat(card).contains("<span class=\"chart-legend-total\">30 created</span>")
                .contains("<span class=\"chart-legend-total\">25 created</span>")
                .contains("<span class=\"chart-legend-total\">12 created</span>")
                .contains("<span class=\"chart-legend-total\">6 created</span>");
        assertThat(card).contains("<th scope=\"row\">Total</th>");
    }

    // ---- the endpoint -----------------------------------------------------------------------------------------------

    @Test
    void theEndpointAnswersJsonWithTheShapeOfTheRecord() throws Exception {
        MockHttpServletResponse response = browser.get("/admin/api/stats?range=7");
        JsonNode json = JSON.readTree(response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("range", "entities", "days", "notes");
        assertThat(json.get("range").longValue()).isEqualTo(7);
        assertThat(json.get("days").size()).isEqualTo(7);
        List<String> names = new ArrayList<>();
        json.get("entities").forEach(entity -> {
            names.add(entity.get("name").asString());
            assertThat(entity.propertyNames()).containsExactlyInAnyOrder("name", "label", "available", "total", "capped");
            assertThat(entity.get("available").booleanValue()).isTrue();
            assertThat(entity.get("capped").booleanValue()).isFalse();
        });
        assertThat(names).containsExactlyElementsOf(NAMES);
        assertThat(json.get("notes").size()).isZero();
    }

    @Test
    void theEndpointAnswersTheFallbackForARangeThatIsNotOffered() throws Exception {
        for (String query : List.of("?range=45", "?range=abc", "?range=", "?range=7&range=30", "?range=-1")) {
            JsonNode json = json(query);

            assertThat(json.get("range").longValue()).as(query).isEqualTo(30);
            assertThat(json.get("days").size()).isEqualTo(30);
            assertThat(json.get("notes").size()).isEqualTo(1);
            assertThat(json.get("notes").get(0).asString()).contains("not available").contains("30 days");
        }
    }

    @Test
    void theEndpointNeedsASession() throws Exception {
        MockHttpServletResponse response = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/api/stats?range=7"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).endsWith("/admin/login");
        assertThat(response.getContentAsString()).doesNotContain("entities").doesNotContain("category");
    }

    @Test
    void theEndpointAnswersOnlyGet() throws Exception {
        String token = MockBrowser.csrf(browser.get("/admin").getContentAsString());

        // The console's existing update route (POST {entity}/{id}) claims the path first, finds no entity "api" and answers the
        // console's own 404: a refusal that changes nothing and tells nothing (the same page as for any unknown entity).
        MockHttpServletResponse withToken = browser.post("/admin/api/stats", "_csrf", token);
        assertThat(withToken.getStatus()).as("POST with a token").isEqualTo(404);
        assertThat(withToken.getContentType()).startsWith("text/html");
        assertThat(withToken.getContentAsString()).doesNotContain("\"entities\"");
        assertThat(browser.post("/admin/api/stats").getStatus()).as("POST without a token").isEqualTo(403);
        for (var builder : List.of(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/admin/api/stats"),
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/admin/api/stats"),
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/admin/api/stats"))) {
            builder.param("_csrf", token);
            assertThat(browser.perform(builder).getStatus()).isEqualTo(405);
        }
    }

    @Test
    void theApiPrefixIsAnExactRouteAndNotAnEntityOrARecord() throws Exception {
        assertThat(browser.get("/admin/api/stats").getContentType()).startsWith("application/json");
        MockHttpServletResponse other = browser.get("/admin/api/other");
        MockHttpServletResponse prefix = browser.get("/admin/api");

        assertThat(other.getStatus()).isEqualTo(404);
        assertThat(prefix.getStatus()).isEqualTo(404);
        assertThat(other.getContentType()).startsWith("text/html");
        assertThat(other.getContentAsString()).doesNotContain("\"entities\"").doesNotContain("\"days\"");
    }

    // ---- the cost ---------------------------------------------------------------------------------------------------

    @Test
    void oneLoadCostsOneQueryPerChartEntityAndARepeatCostsNone() throws Exception {
        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);

        long before = statistics.getPrepareStatementCount();
        browser.get("/admin/api/stats?range=30");
        long first = statistics.getPrepareStatementCount() - before;

        before = statistics.getPrepareStatementCount();
        browser.get("/admin/api/stats?range=30");
        browser.get("/admin/api/stats?range=7");
        browser.get("/admin/api/stats?range=90");
        long repeats = statistics.getPrepareStatementCount() - before;

        assertThat(first).as("category, customer, order and product; tag and the hidden entity are not read").isEqualTo(4);
        assertThat(repeats).as("the repeats and the other ranges are served from the 5-second cache").isZero();
    }

    @Test
    void theDashboardAddsNoChartQueryAfterTheEndpointAndTheCountsAreSharedWithTheSidebar() throws Exception {
        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        browser.get("/admin/api/stats");

        long before = statistics.getPrepareStatementCount();
        browser.get("/admin?range=90");
        long page = statistics.getPrepareStatementCount() - before;

        before = statistics.getPrepareStatementCount();
        browser.get("/admin?range=7");
        long again = statistics.getPrepareStatementCount() - before;

        assertThat(page).as("five counts for the sidebar and the stat cards, once, and no chart query").isEqualTo(5);
        assertThat(again).isZero();
    }

    @Test
    void aColdDashboardCostsTheFiveCountsAndTheFourChartQueriesAndNothingMore() throws Exception {
        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);

        long before = statistics.getPrepareStatementCount();
        browser.get("/admin");

        assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(5 + 4);
    }
}
