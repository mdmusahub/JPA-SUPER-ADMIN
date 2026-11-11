package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.mdmusahub.jpaadmin.web.ErrorViews;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task U0 in the preview: every fixture renders without an exception and shows its key content, in every chrome
 * variant, and each rendered page obeys the UI rules (labels, aria-label on icon buttons, a blocking theme.js in
 * {@code <head>}, nothing inline, no external address).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PreviewPagesTest {

    @Autowired
    private MockMvc mvc;

    private String render(String page, String state, String chrome) throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/admin-preview/" + page).param("state", state)
                .param("chrome", chrome)).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).startsWith("text/html");
        return response.getContentAsString();
    }

    private String render(String page, String state) throws Exception {
        return render(page, state, "default");
    }

    // ---- every combination --------------------------------------------------------------------------------------

    static Stream<Arguments> everyFixtureInEveryChromeVariant() {
        List<Arguments> all = new ArrayList<>();
        for (Map.Entry<String, Set<String>> page : PageFixtures.catalog().entrySet()) {
            for (String state : page.getValue()) {
                for (String chrome : ChromeFixtures.names()) {
                    all.add(Arguments.of(page.getKey(), state, chrome));
                }
            }
        }
        return all.stream();
    }

    @ParameterizedTest(name = "{0} / {1} / {2}")
    @MethodSource("everyFixtureInEveryChromeVariant")
    void everyFixtureRendersWithoutAnExceptionAndObeysTheUiRules(String page, String state, String chrome) throws Exception {
        String html = render(page, state, chrome);

        assertThat(html).startsWith("<!DOCTYPE html>").contains("</html>");
        assertThat(HtmlRules.renderedViolations(html)).isEmpty();
    }

    @Test
    void theCatalogHoldsTheFixturesOfFixturesMd() {
        assertThat(PageFixtures.catalog().get("login")).containsExactly("normal", "bad-credentials", "logged-out", "long-title");
        assertThat(PageFixtures.catalog().get("dashboard")).containsExactly("minimal", "normal", "no-chart",
                "empty-database", "count-unavailable", "no-entities", "many-groups", "read-only-entities");
        assertThat(PageFixtures.catalog().get("list")).contains("normal", "middle-page", "last-page", "empty-no-records",
                "empty-no-matches", "sorted-asc", "sorted-desc", "masked-column", "long-text", "many-columns");
        assertThat(PageFixtures.catalog()).doesNotContainKey("entity");
        assertThat(PageFixtures.catalog().get("error")).contains("not-found", "forbidden", "server-error", "bad-request", "long-hint");
        assertThat(ChromeFixtures.names()).containsExactly("default", "empty-sidebar", "no-active-entity", "deep-breadcrumbs",
                "flash-all-levels", "theme-dark", "theme-light", "theme-system", "custom-base-path", "long-sidebar",
                "no-user", "member-active");
    }

    // ---- sign-in page -----------------------------------------------------------------------------------------------

    @Test
    void theSignInPageHasLabelledFieldsAFocusedFirstFieldAndTheCsrfToken() throws Exception {
        String html = render("login", "normal");

        assertThat(html).contains("<h1>Sign in</h1>")
                .contains("<label for=\"username\">Username</label>", "<label for=\"password\">Password</label>")
                .contains("id=\"username\"", "name=\"username\"", "autocomplete=\"username\"", "autofocus")
                .contains("id=\"password\"", "name=\"password\"", "type=\"password\"", "autocomplete=\"current-password\"")
                .contains("<input type=\"hidden\" name=\"_csrf\" value=\"preview-fake-token-not-a-secret\">")
                .contains("method=\"post\"", "action=\"/admin-preview/login\"", "<button type=\"submit\" class=\"btn btn-primary btn-block\">Sign in</button>")
                .contains("<title>Sign in | JPA Admin</title>")
                .doesNotContain("role=\"alert\"", "signed out", "aria-invalid");
    }

    @Test
    void theSignInPageIsNotInsideTheShell() throws Exception {
        String html = render("login", "normal");

        assertThat(html).contains("class=\"auth\"").doesNotContain("class=\"sidebar\"", "class=\"topbar\"", "Sign out");
    }

    @Test
    void aFailedSignInShowsOneGenericMessageLinkedToBothFields() throws Exception {
        String html = render("login", "bad-credentials");

        assertThat(html).contains(ErrorViews.SIGN_IN_FAILED).contains("id=\"sign-in-error\"", "role=\"alert\"");
        assertThat(count(html, "aria-describedby=\"sign-in-error\"")).isEqualTo(2);
        assertThat(count(html, "aria-invalid=\"true\"")).isEqualTo(2);
        // The message never says which field was wrong.
        assertThat(ErrorViews.SIGN_IN_FAILED.toLowerCase()).contains("username or password").doesNotContain("unknown user");
    }

    @Test
    void aSignOutNoticeIsShownAboveTheForm() throws Exception {
        String html = render("login", "logged-out");

        assertThat(html).contains("You have been signed out.", "role=\"status\"").doesNotContain("role=\"alert\"");
        assertThat(html.indexOf("You have been signed out.")).isLessThan(html.indexOf("<form"));
    }

    @Test
    void aVeryLongTitleIsShownInFullAndEscaped() throws Exception {
        String title = "Back office of the Example Company Regional Operations and Customer Support Team";
        String html = render("login", "long-title");

        assertThat(html).contains("<title>Sign in | " + title + "</title>").contains(">" + title + "<");
    }

    // ---- dashboard --------------------------------------------------------------------------------------------------

    @Test
    void theMinimalDashboardShowsTheShellAndAWelcomeState() throws Exception {
        String html = render("dashboard", "minimal", "no-active-entity");

        assertThat(html).contains("<title>Dashboard | JPA Admin</title>")
                .contains("<a class=\"skip-link\" href=\"#main\">Skip to content</a>")
                .contains("<h1 class=\"page-title\">Dashboard</h1>", "Welcome to <span>JPA Admin</span>",
                        "The console lists every JPA entity it finds in this application")
                .contains("<main id=\"main\" class=\"content\" tabindex=\"-1\">")
                .contains("aria-label=\"Open navigation\"", "data-nav-toggle", "aria-controls=\"sidebar\"", "aria-expanded=\"false\"")
                .contains("aria-label=\"Close navigation\"", "aria-label=\"Change theme\"", "data-theme-toggle")
                .contains("<script src=\"/admin-preview/assets/js/nav.js\" defer></script>");
    }

    @Test
    void theDashboardSignOutIsAPostWithTheCsrfTokenAndNotALink() throws Exception {
        String html = render("dashboard", "minimal");

        Matcher form = Pattern.compile("(?s)<form method=\"post\" action=\"/admin-preview/logout\">(.*?)</form>").matcher(html);
        assertThat(form.find()).isTrue();
        assertThat(form.group(1)).contains("name=\"_csrf\" value=\"preview-fake-token-not-a-secret\"", "<span>Sign out</span>");
        assertThat(html).doesNotContain("href=\"/admin-preview/logout\"");
    }

    // ---- error pages ------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("errorPages")
    void anErrorPageShowsItsStatusHeadingAndHintAndWayBackAndNoSignOut(String state, int status, String heading) throws Exception {
        String html = render("error", state);

        assertThat(html).contains("<p class=\"error-status\">" + status + "</p>", "<h1>" + heading + "</h1>")
                .contains("<title>" + heading + " | JPA Admin</title>")
                .contains("<a class=\"btn btn-primary\" href=\"/admin-preview\">Go to dashboard</a>")
                .doesNotContain("Sign out", "/logout");
        assertThat(html).contains(ErrorViews.forStatus(status).hint());
    }

    static Stream<Arguments> errorPages() {
        return Stream.of(
                Arguments.of("not-found", 404, "Page not found"),
                Arguments.of("forbidden", 403, "Access denied"),
                Arguments.of("server-error", 500, "Something went wrong"),
                Arguments.of("bad-request", 400, "Bad request"),
                Arguments.of("method-not-allowed", 405, "Action not allowed"));
    }

    @Test
    void anErrorPageNeverShowsAnExceptionSqlOrAStackTrace() throws Exception {
        for (String state : PageFixtures.catalog().get("error")) {
            assertThat(render("error", state).toLowerCase())
                    .doesNotContain("exception", "stack trace", "sql", "select ", "org.springframework", "java.lang", "at io.github");
        }
    }

    @Test
    void aVeryLongHintWrapsInsteadOfBeingCutOff() throws Exception {
        String html = render("error", "long-hint");

        assertThat(html).contains("AVeryLongWordWithoutAnySpacesToSeeWhetherItBreaks" + "x".repeat(80));
    }

    // ---- chrome variants --------------------------------------------------------------------------------------------

    @Test
    void theDefaultChromeListsGroupsAndEntitiesWithCountsAndMarksTheActiveOne() throws Exception {
        String html = render("dashboard", "minimal", "default");

        assertThat(html).contains("<span>Content</span>", "<span>People</span>", "<span>Billing</span>")
                .contains("href=\"/admin-preview/article\" aria-current=\"page\"")
                .contains("<span>Articles</span>", "<span class=\"nav-count\">1,204</span>")
                .contains("href=\"/admin-preview/invoice\"", "href=\"/admin-preview/member\"")
                .doesNotContain("Your entities will be listed here.");
        assertThat(count(html, "class=\"nav-link\"")).as("eight entities and the dashboard").isEqualTo(9);
        assertThat(count(html, "aria-current=\"page\"")).as("the active entity and the current breadcrumb").isEqualTo(2);
        assertThat(html).contains("<a href=\"/admin-preview\">Dashboard</a>", "<span aria-current=\"page\">Articles</span>");
    }

    @Test
    void anEmptySidebarSaysThatEntitiesWillAppear() throws Exception {
        String html = render("dashboard", "minimal", "empty-sidebar");

        assertThat(html).contains("Your entities will be listed here.").doesNotContain("nav-group-title");
    }

    @Test
    void withoutAnActiveEntityNoEntityIsMarkedCurrent() throws Exception {
        String html = render("dashboard", "minimal", "no-active-entity");

        assertThat(html).doesNotContain("href=\"/admin-preview/article\" aria-current");
        assertThat(html).contains("<a class=\"nav-link\" href=\"/admin-preview\" aria-current=\"page\">");
    }

    @Test
    void deepBreadcrumbsAllAppearInOrderWithTheLastOneAsTheCurrentPage() throws Exception {
        String html = render("dashboard", "minimal", "deep-breadcrumbs");

        assertThat(html).contains("Content management and editorial workflow",
                "Articles waiting for a second review before publication",
                "An article with a very long title that goes on and on");
        assertThat(html).contains("<span aria-current=\"page\">Edit the article and its translations</span>");
        assertThat(count(crumbs(html), "<li>")).as("five steps in the trail").isEqualTo(5);
    }

    @Test
    void flashMessagesOfEveryLevelAreShownAndErrorsAreAnnouncedAtOnce() throws Exception {
        String html = render("dashboard", "minimal", "flash-all-levels");

        assertThat(html).contains("toast toast-success", "toast toast-info", "toast toast-warning", "toast toast-error")
                .contains("The article was saved.", "The record could not be deleted because other records refer to it.");
        assertThat(count(html, "role=\"alert\"")).as("warning and error").isEqualTo(2);
        assertThat(count(html, "role=\"status\"")).as("success and the two infos").isEqualTo(3);
        assertThat(html).contains("AVeryLongWordWithoutAnySpacesAtAllToSeeWhetherItBreaksInsteadOfOverflowingTheScreen");
    }

    @Test
    void theInitialThemeIsCarriedOnTheHtmlElementForThemeJsToApplyBeforeFirstPaint() throws Exception {
        assertThat(htmlElement(render("dashboard", "minimal", "theme-dark"))).isEqualTo("<html lang=\"en\" data-theme-default=\"dark\">");
        assertThat(htmlElement(render("dashboard", "minimal", "theme-light"))).isEqualTo("<html lang=\"en\" data-theme-default=\"light\">");
        assertThat(htmlElement(render("dashboard", "minimal", "theme-system"))).isEqualTo("<html lang=\"en\" data-theme-default=\"system\">");
        assertThat(htmlElement(render("login", "normal", "theme-dark"))).isEqualTo("<html lang=\"en\" data-theme-default=\"dark\">");
        assertThat(htmlElement(render("error", "not-found", "theme-light"))).isEqualTo("<html lang=\"en\" data-theme-default=\"light\">");
    }

    /** The opening {@code <html ...>} tag with its whitespace normalised, so the test does not depend on line breaks in the source. */
    private static String htmlElement(String html) {
        Matcher tag = Pattern.compile("(?s)<html\\b[^>]*>").matcher(html);
        assertThat(tag.find()).isTrue();
        return tag.group().replaceAll("\\s+", " ");
    }

    @Test
    void aCustomBasePathMakesEveryLinkAndAssetAddressStartWithIt() throws Exception {
        for (String page : List.of("dashboard/minimal", "login/normal", "error/not-found")) {
            String[] parts = page.split("/");
            String html = render(parts[0], parts[1], "custom-base-path");

            List<String> addresses = new ArrayList<>();
            Matcher matcher = Pattern.compile("\\b(?:href|src|action)=\"([^\"]*)\"").matcher(html);
            while (matcher.find()) {
                addresses.add(matcher.group(1));
            }
            assertThat(addresses).as(page).isNotEmpty().allSatisfy(address -> {
                if (!address.equals("#main") && !address.equals("data:,")) {
                    assertThat(address).startsWith("/preview-alt");
                }
            });
            assertThat(html).contains("/preview-alt/assets/css/tokens.css", "/preview-alt/theme.css", "/preview-alt/assets/js/theme.js");
        }
    }

    // ---- unknown names ----------------------------------------------------------------------------------------------

    @Test
    void anUnknownPageStateOrChromeAnswers404WithTheValidNames() throws Exception {
        String page = mvc.perform(get("/admin-preview/nope")).andExpect(status().isNotFound()).andReturn()
                .getResponse().getContentAsString();
        String state = mvc.perform(get("/admin-preview/login").param("state", "nope")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String chrome = mvc.perform(get("/admin-preview/login").param("chrome", "nope")).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Valid pages").contains("login", "dashboard", "error");
        assertThat(state).contains("Valid states").contains("bad-credentials");
        assertThat(chrome).contains("Valid chrome variants").contains("flash-all-levels");
    }

    @Test
    void theIndexPageLinksEveryFixture() throws Exception {
        String html = mvc.perform(get("/admin-preview")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("/admin-preview/login?state=bad-credentials", "/admin-preview/error?state=server-error",
                "/admin-preview/dashboard?state=minimal&amp;chrome=flash-all-levels");
    }

    // ---- U1: the sidebar --------------------------------------------------------------------------------------------

    @Test
    void theSidebarHasTheBrandWithItsSubtitleAndALabelledSearchBox() throws Exception {
        String html = render("dashboard", "minimal", "default");

        assertThat(html).contains("<span class=\"brand-name\">JPA Admin</span>",
                "<span class=\"brand-subtitle\">Administration</span>")
                .contains("<label class=\"nav-search\" for=\"nav-search-input\">",
                        "<span class=\"visually-hidden\">Search entities</span>",
                        "id=\"nav-search-input\"", "type=\"search\"", "data-nav-search");
    }

    @Test
    void anEmptySidebarHasNoSearchBoxBecauseThereWouldBeNothingToSearch() throws Exception {
        String html = render("dashboard", "minimal", "empty-sidebar");

        assertThat(html).doesNotContain("data-nav-search").contains("Your entities will be listed here.");
    }

    @Test
    void everyGroupIsADetailsElementThatIsOpenAndCarriesItsNameForTheScript() throws Exception {
        String html = render("dashboard", "minimal", "default");

        assertThat(count(html, "<details class=\"nav-group\" open")).isEqualTo(3);
        assertThat(html).contains("data-nav-group=\"Content\"", "data-nav-group=\"People\"",
                "data-nav-group=\"Billing\"", "<summary class=\"nav-group-title\">", "icon icon-chevron");
        assertThat(count(html, "data-nav-item=")).as("one per entity, for the search").isEqualTo(8);
    }

    @Test
    void anEntityItemCarriesExactlyOneIcon() throws Exception {
        String html = render("dashboard", "minimal", "default");

        Matcher items = Pattern.compile("(?s)<li data-nav-item=\"[^\"]*\">(.*?)</li>").matcher(html);
        int found = 0;
        while (items.find()) {
            found++;
            assertThat(count(items.group(1), "<svg")).as("one icon, not the whole sprite").isEqualTo(1);
        }
        assertThat(found).isEqualTo(8);
    }

    @Test
    void countsAreFormattedWithThousandsSeparatorsAndAnUnreadableCountSaysSo() throws Exception {
        String html = render("dashboard", "minimal", "long-sidebar");

        assertThat(html).contains("<span class=\"nav-count\">1,000,000</span>",
                "<span class=\"nav-count\">987,654</span>", "<span class=\"nav-count\">0</span>")
                .contains("<span class=\"nav-count nav-count-missing\"", "unavailable");
        assertThat(count(html, "class=\"nav-link\"")).as("sixty entities and the dashboard").isEqualTo(61);
    }

    @Test
    void aLongSidebarKeepsLongAndNonLatinNamesInFull() throws Exception {
        String html = render("dashboard", "minimal", "long-sidebar");

        assertThat(html).contains("Scheduled newsletter delivery attempts and their bounce reports",
                "\u00dcr\u00fcn kategorileri", "\u041f\u043b\u0430\u0442\u0451\u0436\u043d\u044b\u0435 \u043f\u043e\u0440\u0443\u0447\u0435\u043d\u0438\u044f",
                "\u914d\u9001\u5148\u4f4f\u6240", "\u03a0\u03b1\u03c1\u03b1\u03b3\u03b3\u03b5\u03bb\u03af\u03b5\u03c2 \u03c0\u03b5\u03bb\u03b1\u03c4\u03ce\u03bd");
        assertThat(count(html, "<details class=\"nav-group\" open")).isEqualTo(8);
    }

    // ---- U1: the user chip ------------------------------------------------------------------------------------------

    @Test
    void theUserChipShowsTheSignedInNameAndSignsOutWithAPostAndTheToken() throws Exception {
        String html = render("dashboard", "minimal", "default");

        Matcher chip = Pattern.compile("(?s)<div class=\"user-chip\">(.*?)</div>").matcher(html);
        assertThat(chip.find()).isTrue();
        assertThat(chip.group(1)).contains("<span class=\"avatar\" aria-hidden=\"true\">A</span>",
                "<span class=\"user-name\">admin</span>",
                "<form method=\"post\" action=\"/admin-preview/logout\">",
                "name=\"_csrf\" value=\"preview-fake-token-not-a-secret\"", "<span>Sign out</span>");
        assertThat(html).doesNotContain("href=\"/admin-preview/logout\"");
    }

    @Test
    void thereIsNoUserChipWhenNobodyIsSignedIn() throws Exception {
        assertThat(render("dashboard", "minimal", "no-user")).doesNotContain("user-chip", "Sign out");
    }

    @Test
    void thereIsOneSignOutOnThePageAndItIsInTheSidebar() throws Exception {
        String html = render("dashboard", "minimal", "default");

        assertThat(count(html, "Sign out")).as("one sign-out on the page, in the chip").isEqualTo(1);
        assertThat(html.indexOf("Sign out")).isLessThan(html.indexOf("class=\"topbar\""));
    }

    // ---- U1: the dashboard ------------------------------------------------------------------------------------------

    @Test
    void theDashboardShowsFourStatCardsAndOneCardPerGroup() throws Exception {
        String html = render("dashboard", "normal", "no-active-entity");

        assertThat(count(html, "class=\"card stat-card\"")).isEqualTo(4);
        assertThat(html).contains(">Total records</h2>", "<p class=\"stat-value\">12,745</p>", "Across 8 entities",
                        ">Entities</h2>", "Discovered from the JPA model", ">Groups</h2>", "Sections in the sidebar",
                        ">Largest table</h2>", "Comments", "5,430 records")
                .doesNotContain("Welcome to");
        assertThat(count(html, "class=\"card group-card\"")).isEqualTo(3);
        assertThat(html).contains("<li class=\"entity-row\">", "href=\"/admin-preview/article\"", "1,204 records");
    }

    @Test
    void theDashboardOfAnEmptyDatabaseShowsZerosAndNoLargestTable() throws Exception {
        String html = render("dashboard", "empty-database", "no-active-entity");

        assertThat(html).contains("<p class=\"stat-value\">0</p>",
                "<p class=\"stat-value stat-value-text\">None</p>", "No record has been counted yet", "0 records");
    }

    @Test
    void aCountTheConsoleCouldNotReadSaysSoOnTheDashboardToo() throws Exception {
        String html = render("dashboard", "count-unavailable", "no-active-entity");

        assertThat(html).contains("Count unavailable").contains("5,430 records");
    }

    @Test
    void aConsoleWithoutEntitiesExplainsHowEntitiesAreFoundAndWhyOneCouldBeMissing() throws Exception {
        String html = render("dashboard", "no-entities", "empty-sidebar");

        assertThat(html).contains("Welcome to", "discovered from the JPA model", "<code>@AdminHidden</code>",
                        "<code>jpa.admin.excluded-entities</code>", "composite primary key")
                .doesNotContain("stat-card", "group-card");
    }

    @Test
    void theDashboardSurvivesSixtyEntitiesInEightGroups() throws Exception {
        String html = render("dashboard", "many-groups", "long-sidebar");

        assertThat(count(html, "class=\"card group-card\"")).isEqualTo(8);
        assertThat(count(html, "class=\"entity-row\"")).isEqualTo(60);
        assertThat(html).contains("<p class=\"stat-value\">3,456,789</p>", "Across 60 entities");
    }

    @Test
    void noDashboardCardOffersAnActionYetBecauseNothingCanBeDoneWithARecordSoFar() throws Exception {
        String html = render("dashboard", "normal", "default");

        assertThat(html).doesNotContain("New record", "Delete record", "Export");
    }

    private static String crumbs(String html) {
        Matcher trail = Pattern.compile("(?s)<ol class=\"crumbs\">(.*?)</ol>").matcher(html);
        assertThat(trail.find()).isTrue();
        return trail.group(1);
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
