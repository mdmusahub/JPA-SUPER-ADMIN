package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * What the pages do when a person uses them, in a real browser (headless Chrome or Edge, the JDK-only driver, real trusted key
 * events), against the real demo application (Phase 13, U9): the chart's range links, the export button, the saved views panel
 * (delete asks first), the column control and its "Show all columns" and "Clear search" links, the uploader's states that a browser
 * can be driven into, the unsaved-changes guard of the form, the "/" shortcut, the skip links, the keyboard focus order with a visible
 * focus ring on every stop, and the drawer's focus trap on a phone.
 *
 * <p>What no automated browser can judge, and is a manual item in the Phase 13 report: dragging a file onto the drop zone, the
 * look of the real file dialog, a screen reader's speech, a real private window, a physical keyboard and phone, and print.
 *
 * <p>When the machine has no Chrome or Edge each test is <b>skipped</b>, with the reason, and is never reported as passed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsoleBehaviourBrowserTest {

    @LocalServerPort
    private int port;

    @TempDir
    Path temp;

    /** Polls until the expression equals the wanted text, at most about ten seconds (a real upload is asynchronous). */
    private static String waitFor(ConsoleBrowser browser, String expression, String wanted) throws Exception {
        String last = "";
        for (int i = 0; i < 100; i++) {
            last = browser.js(expression);
            if (wanted.equals(last)) {
                return last;
            }
            Thread.sleep(100);
        }
        return last;
    }

    // ---- the chart's range links ----------------------------------------------------------------------------------

    @Test
    void theRangeLinksReloadTheDashboardWithTheOtherRangeAndMarkTheCurrentOne() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("");
            assertThat(browser.js("Array.prototype.map.call(document.querySelectorAll('.range-link'), function (a) { return "
                    + "a.textContent.trim() + (a.getAttribute('aria-current') ? '*' : ''); }).join(',')"))
                    .isEqualTo("7 days,30 days*,90 days");

            browser.jsAndWait("document.querySelectorAll('.range-link')[0].click(); 'ok'");

            assertThat(browser.js("location.pathname + location.search")).isEqualTo("/admin?range=7");
            assertThat(browser.js("document.querySelector('.range-link[aria-current]').textContent.trim()")).isEqualTo("7 days");
            assertThat(browser.js("document.querySelector('.chart-sub').textContent.trim()")).isEqualTo("Last 7 days, per day");
            assertThat(browser.js("String(document.querySelectorAll('.chart-table tbody tr').length)")).isEqualTo("7");

            browser.jsAndWait("document.querySelectorAll('.range-link')[2].click(); 'ok'");

            assertThat(browser.js("location.search")).isEqualTo("?range=90");
            assertThat(browser.js("document.querySelector('.chart-sub').textContent.trim()")).isEqualTo("Last 90 days, per day");
        }
    }

    // ---- the export button ------------------------------------------------------------------------------------------

    @Test
    void theExportButtonLinksToTheFileOfTheCurrentListAndTheFileHasNoHiddenColumn() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("/product?hide=sku&sort=price&dir=desc");

            assertThat(browser.js("document.querySelector('a.export-link').getAttribute('href')"))
                    .isEqualTo("/admin/product/export.csv?hide=sku&sort=price&dir=desc");
            assertThat(browser.js("document.querySelector('a.export-link').getAttribute('aria-label')"))
                    .contains("Export CSV");
            String file = browser.js("(function () { var x = new XMLHttpRequest(); x.open('GET', document.querySelector('a.export-link')"
                    + ".getAttribute('href'), false); x.send(); return x.status + '|' + x.getResponseHeader('Content-Type') + '|' "
                    + "+ x.getResponseHeader('Content-Disposition') + '|' + x.responseText.split('\\r\\n')[0] + '|' "
                    + "+ x.responseText.trim().split('\\r\\n').length; })()");

            String[] parts = file.split("\\|");
            assertThat(parts[0]).isEqualTo("200");
            assertThat(parts[1]).startsWith("text/csv");
            assertThat(parts[2]).startsWith("attachment").contains("product-");
            assertThat(parts[3]).isEqualTo("ID,Category,Created at,Featured,Name,Price,Status,Stock").as("the hidden Sku column is not in the file");
            assertThat(parts[4]).as("a header and the 25 products").isEqualTo("26");
        }
    }

    // ---- the saved views panel --------------------------------------------------------------------------------------

    @Test
    void aSavedViewIsAppliedRenamedAndDeletedOnlyAfterTheQuestionIsAnswered() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("/product");
            browser.js("localStorage.clear(); 'ok'");
            browser.go("/product?sort=price&dir=asc&size=10");
            browser.js("document.querySelector('[data-views-name]').value = 'Mine'; document.querySelector('[data-views-save]').click(); 'ok'");

            assertThat(browser.js("document.querySelector('[data-views-apply]').textContent")).isEqualTo("Mine");
            assertThat(browser.js("document.querySelector('[data-views-apply]').getAttribute('href')"))
                    .isEqualTo("/admin/product?sort=price&dir=asc&size=10");

            browser.go("/product");
            browser.js("document.querySelector('details[data-views]').open = true; 'ok'");
            browser.jsAndWait("document.querySelector('[data-views-apply]').click(); 'ok'");
            assertThat(browser.js("location.pathname + location.search")).as("applying a view is a plain link").isEqualTo(
                    "/admin/product?sort=price&dir=asc&size=10");

            browser.js("document.querySelector('details[data-views]').open = true; 'ok'");
            browser.js("document.querySelector('details.views-rename > summary').click(); 'ok'");
            assertThat(browser.js("String(document.querySelector('[data-views-rename-name]').offsetParent !== null)")).isEqualTo("true");
            browser.js("document.querySelector('[data-views-rename-name]').value = 'Cheapest'; "
                    + "document.querySelector('[data-views-rename]').click(); 'ok'");
            assertThat(browser.js("document.querySelector('[data-views-apply]').textContent")).isEqualTo("Cheapest");
            assertThat(browser.js("document.querySelector('[data-views-status]').textContent")).contains("Renamed");

            assertThat(browser.js("String(document.querySelector('[data-views-delete]').checkVisibility() === false)"))
                    .as("the confirm button cannot be pressed before the question is open").isEqualTo("true");
            browser.js("document.querySelector('details.views-delete > summary').click(); 'ok'");
            assertThat(browser.js("document.querySelector('[data-views-delete-question]').textContent")).contains("Cheapest")
                    .contains("cannot be undone");
            browser.js("document.querySelector('details.views-delete > summary').click(); 'ok'"); // closing the question is "no"
            assertThat(browser.js("String(document.querySelectorAll('[data-views-apply]').length)")).as("closing the question keeps it")
                    .isEqualTo("1");

            browser.js("document.querySelector('details.views-delete > summary').click(); 'ok'");
            browser.js("document.querySelector('[data-views-delete]').click(); 'ok'");
            assertThat(browser.js("String(document.querySelectorAll('[data-views-apply]').length)")).isEqualTo("0");
            assertThat(browser.js("document.querySelector('[data-views-status]').textContent")).contains("Deleted");
            assertThat(browser.js("String(localStorage.getItem('jpa-admin.views./admin/product'))")).isEqualTo("[]");
        }
    }

    // ---- the column control, show all columns and clear search ------------------------------------------------------

    @Test
    void theColumnPanelHidesColumnsShowAllBringsThemBackAndClearSearchKeepsTheChoice() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("/product");
            assertThat(browser.js("String(document.body.textContent.indexOf('Show all columns') >= 0)")).isEqualTo("false");

            browser.js("document.querySelector('details.column-panel').open = true; "
                    + "document.querySelector('input[name=hide][value=sku]').click(); "
                    + "document.querySelector('input[name=hide][value=stock]').click(); 'ok'");
            browser.jsAndWait("document.querySelector('details.column-panel button[type=submit]').click(); 'ok'");

            assertThat(browser.js("location.search")).contains("hide=sku").contains("hide=stock").doesNotContain("page=");
            assertThat(browser.js("Array.prototype.map.call(document.querySelectorAll('th[scope=col]'), function (th) { return "
                    + "th.textContent.trim(); }).join(',')")).doesNotContain("Sku").doesNotContain("Stock").contains("Price");
            assertThat(browser.js("Array.prototype.find.call(document.querySelectorAll('a'), function (a) { return "
                    + "a.textContent.trim() === 'Show all columns'; }).getAttribute('href')")).isEqualTo("/admin/product");

            browser.go("/product?q=zzzzzz&hide=sku&sort=price&dir=asc");
            browser.jsAndWait("Array.prototype.find.call(document.querySelectorAll('a'), function (a) { return "
                    + "a.textContent.trim() === 'Clear search'; }).click(); 'ok'");
            assertThat(browser.js("location.pathname + location.search")).as("Clear search keeps the hidden columns")
                    .isEqualTo("/admin/product?hide=sku");

            browser.jsAndWait("Array.prototype.find.call(document.querySelectorAll('a'), function (a) { return "
                    + "a.textContent.trim() === 'Show all columns'; }).click(); 'ok'");
            assertThat(browser.js("location.pathname + location.search")).isEqualTo("/admin/product");
            assertThat(browser.js("Array.prototype.map.call(document.querySelectorAll('th[scope=col]'), function (th) { return "
                    + "th.textContent.trim(); }).join(',')")).contains("Sku").contains("Stock");
        }
    }

    // ---- the uploader -----------------------------------------------------------------------------------------------

    private Path png(String name) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        Path file = temp.resolve(name);
        ImageIO.write(image, "png", file.toFile());
        return file;
    }

    private static String state(ConsoleBrowser browser) throws Exception {
        return browser.js("document.querySelector('[data-upload]').getAttribute('data-state')");
    }

    @Test
    void theUploaderRefusesAFileOfTheWrongTypeAndAnOversizedOneInThePageWithAPlainSentence() throws Exception {
        Path text = temp.resolve("notes.txt");
        Files.writeString(text, "plain text, not a picture");
        Path big = temp.resolve("big.png");
        Files.write(big, new byte[3 * 1024 * 1024]);
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("/category/1/edit");
            assertThat(state(browser)).isEqualTo("idle");

            browser.chooseFile("[data-upload-input]", text);
            assertThat(waitFor(browser, "document.querySelector('[data-upload]').getAttribute('data-state')", "error")).isEqualTo("error");
            assertThat(browser.js("document.querySelector('[data-upload-status]').textContent")).isNotBlank().doesNotContain("Exception")
                    .doesNotContain("undefined");

            browser.chooseFile("[data-upload-input]", big);
            assertThat(waitFor(browser, "document.querySelector('[data-upload-status]').textContent.indexOf('MB') >= 0 ? 'mb' : ''", "mb"))
                    .isEqualTo("mb");
            assertThat(state(browser)).isEqualTo("error");
        }
    }

    @Test
    void aGoodPictureIsUploadedShownAndCountsAsAChangeOfTheForm() throws Exception {
        Path picture = png("small.png");
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("/category/1/edit");

            browser.chooseFile("[data-upload-input]", picture);

            assertThat(waitFor(browser, "document.querySelector('[data-upload]').getAttribute('data-state')", "done")).isEqualTo("done");
            assertThat(browser.js("document.querySelector('[data-upload-status]').textContent")).contains("small.png").contains("is uploaded");
            assertThat(browser.js("document.querySelector('input[name=image]').value")).startsWith("/admin/files/default/");
            assertThat(browser.js("String(document.querySelector('[data-upload-actions]').hidden)")).isEqualTo("false");
            assertThat(browser.js("String(document.querySelector('.upload-preview') !== null)")).isEqualTo("true");
            assertThat(browser.js("(function () { var e = new Event('beforeunload', {cancelable: true}); window.dispatchEvent(e); "
                    + "return String(e.defaultPrevented); })()")).as("a finished upload is an unsaved change").isEqualTo("true");
        }
    }

    // ---- the unsaved-changes guard ----------------------------------------------------------------------------------

    private static final String LEAVE = "(function () { var e = new Event('beforeunload', {cancelable: true}); window.dispatchEvent(e); "
            + "return String(e.defaultPrevented); })()";

    @Test
    void theFormWarnsAboutLeavingOnlyAfterAChangeAndCancelGoesStraightBackWhenNothingChanged() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("/product/1/edit");
            assertThat(browser.js(LEAVE)).as("nothing changed, nothing to warn about").isEqualTo("false");

            browser.jsAndWait("document.querySelector('[data-form-cancel] > summary').click(); 'ok'");
            assertThat(browser.js("location.pathname")).as("a direct Cancel").startsWith("/admin/product").doesNotContain("edit");

            browser.go("/product/1/edit");
            browser.js("var f = document.querySelector('input[name=name]'); f.value = 'Changed'; "
                    + "f.dispatchEvent(new Event('input', {bubbles: true})); 'ok'");
            assertThat(browser.js(LEAVE)).as("a changed form warns").isEqualTo("true");

            browser.js("document.querySelector('[data-form-cancel] > summary').click(); 'ok'");
            assertThat(browser.js("location.pathname")).as("with a change, Cancel asks first").endsWith("/edit");
            assertThat(browser.js("String(document.querySelector('[data-form-cancel]').open)")).isEqualTo("true");
        }
    }

    // ---- the "/" shortcut and the skip links ------------------------------------------------------------------------

    @Test
    void theSlashKeyFocusesTheSearchBoxButNeverWhileTypingInAField() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("/product");
            assertThat(browser.js("document.activeElement.tagName")).isEqualTo("BODY");

            browser.press("/");

            assertThat(browser.js("document.activeElement.id")).isEqualTo("list-search");
            assertThat(browser.js("document.querySelector('#list-search').value")).as("the key itself is not typed").isEmpty();

            browser.js("document.querySelector('details.filter-panel').open = true; document.querySelector('input[name=\"filter.name\"]').focus(); 'ok'");
            browser.press("/");

            assertThat(browser.js("document.activeElement.name")).as("typing in a field is left alone").isEqualTo("filter.name");
            assertThat(browser.js("document.activeElement.value")).as("and the slash is typed there").isEqualTo("/");
        }
    }

    @Test
    void theSlashKeyGoesToTheSidebarSearchOnADesktopPageWithNoSearchBoxAndDoesNothingOnAPhone() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("");
            browser.press("/");
            assertThat(browser.js("String(document.activeElement.hasAttribute('data-nav-search'))")).isEqualTo("true");

            browser.viewport(390, 844, false);
            browser.go("");
            browser.press("/");
            assertThat(browser.js("document.activeElement.tagName")).as("the sidebar is a closed drawer on a phone").isEqualTo("BODY");
        }
    }

    @Test
    void theSkipLinksAreTheFirstTwoStopsAndTheSearchOneMovesFocusToTheSearchBox() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.go("/product");

            browser.press("Tab");
            assertThat(browser.focused()).isEqualTo("a [Skip to content]");
            browser.press("Tab");
            assertThat(browser.focused()).isEqualTo("a [Skip to search]");

            browser.press("Enter");

            assertThat(browser.js("document.activeElement.id")).isEqualTo("list-search");
        }
    }

    // ---- keyboard focus order ---------------------------------------------------------------------------------------

    private static final String RING = "(function () { var e = document.activeElement; var cs = getComputedStyle(e); "
            + "var ring = cs.outlineStyle !== 'none' && parseFloat(cs.outlineWidth) >= 2; "
            + "var shadow = cs.boxShadow !== 'none'; "
            + "var box = e.closest('.search-field, .nav-search'); "
            + "var within = box !== null && getComputedStyle(box).outlineStyle !== 'none'; "
            + "var summary = e.tagName === 'SUMMARY' && (ring || shadow); "
            + "return (ring || shadow || within || summary) ? 'true' : ('false:' + cs.outlineStyle + '/' + cs.outlineWidth + '/focus-visible=' + e.matches(':focus-visible')); })()";

    @Test
    void everyStopOfTheTabOrderIsVisibleHasAFocusRingAndTheOrderIsSkipLinksThenSidebarThenPage() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            // The whole of the pages a person tabs through: the list (toolbar, table, pager), the detail page and both kinds of form.
            for (String path : List.of("/product/1", "/product/1/edit", "/category/1/edit", "/customer", "/")) {
                List<String> stops = new ArrayList<>();
                browser.go(path.equals("/") ? "" : path);
                int rings = tabThroughEverything(browser, stops);
                System.out.println("FOCUS " + path + " stops=" + stops.size() + " withoutRing=" + rings);
                assertThat(stops.size()).as(path + ": the page has stops").isGreaterThan(5);
                assertThat(rings).as(path + ": stops without a visible focus ring").isZero();
            }
            browser.go("/product");
            List<String> stops = new ArrayList<>();
            int rings = tabThroughEverything(browser, stops);
            System.out.println("FOCUS /product stops=" + stops.size() + " withoutRing=" + rings);
            assertThat(rings).as("/product: stops without a visible focus ring").isZero();
            stops = stops.subList(0, Math.min(stops.size(), 40));
            assertThat(stops.subList(0, 2)).containsExactly("a [Skip to content]", "a [Skip to search]");
            int dashboardLink = indexOf(stops, "Dashboard");
            int listSearch = indexOf(stops, "#list-search");
            assertThat(dashboardLink).as("the sidebar is reached: " + stops).isGreaterThanOrEqualTo(2);
            assertThat(listSearch).as("the list search is reached: " + stops).isGreaterThanOrEqualTo(0);
            assertThat(dashboardLink).as("the sidebar comes before the page: " + stops).isLessThan(listSearch);
        }
    }

    /**
     * Presses Tab until the focus leaves the page (the browser's own interface, shown here as no active element or the body) or 300
     * times, records every stop in {@code stops} and returns how many stops showed no focus ring or were not on screen.
     */
    private static int tabThroughEverything(ConsoleBrowser browser, List<String> stops) throws Exception {
        int without = 0;
        for (int i = 0; i < 300; i++) {
            browser.press("Tab");
            if ("BODY".equals(browser.js("document.activeElement.tagName"))) {
                break;
            }
            String stop = browser.focused();
            stops.add(stop);
            boolean onScreen = "true".equals(browser.js("String(document.activeElement.offsetParent !== null || getComputedStyle("
                    + "document.activeElement).position === 'fixed' || document.activeElement.checkVisibility())"));
            if (!onScreen || !"true".equals(browser.js(RING))) {
                without++;
                System.out.println("FOCUS no ring or off screen: " + stop + " -> " + browser.js(RING));
            }
        }
        return without;
    }

    private static int indexOf(List<String> stops, String part) {
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).contains(part)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void theDrawerKeepsTheTabKeyInsideItAndGivesFocusBackWhenItCloses() throws Exception {
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            browser.viewport(390, 844, false);
            browser.go("/product");
            browser.js("document.querySelector('[data-nav-toggle]').focus(); document.querySelector('[data-nav-toggle]').click(); 'ok'");
            assertThat(browser.js("document.querySelector('[data-shell]').getAttribute('data-nav-open')")).isEqualTo("true");
            assertThat(browser.js("String(document.querySelector('#sidebar').contains(document.activeElement))")).isEqualTo("true");

            for (int i = 0; i < 25; i++) {
                browser.press("Tab");
                assertThat(browser.js("String(document.querySelector('#sidebar').contains(document.activeElement))"))
                        .as("Tab number " + (i + 1) + " stays in the drawer: " + browser.focused()).isEqualTo("true");
            }
            for (int i = 0; i < 25; i++) {
                browser.pressShiftTab();
                assertThat(browser.js("String(document.querySelector('#sidebar').contains(document.activeElement))"))
                        .as("Shift+Tab number " + (i + 1) + " stays in the drawer: " + browser.focused()).isEqualTo("true");
            }

            browser.press("Escape");

            assertThat(browser.js("document.querySelector('[data-shell]').getAttribute('data-nav-open')")).isEqualTo("false");
            assertThat(browser.js("String(document.activeElement.hasAttribute('data-nav-toggle'))")).as("focus returns to the button")
                    .isEqualTo("true");
        }
    }
}
