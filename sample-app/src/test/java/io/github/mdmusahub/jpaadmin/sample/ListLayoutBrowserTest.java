package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The layout of every entity's list page in a real browser (headless Chrome or Edge, over the DevTools protocol, with
 * nothing but the JDK): at a desktop width and at a phone width, in both colour schemes, with the demo data present.
 *
 * <p>It exists because of a defect that tests of the markup could not see (found by eye in Phase 7): the checkbox column and
 * the bulk bar made the ID column far too wide, a scrollbar appeared inside a two-column table, the page grew a tall empty
 * area at the bottom, and the floating bar covered the last row and the pager. What it asserts, for all five sample entities:
 * <ul>
 *   <li>the page never scrolls sideways;</li>
 *   <li>at the desktop width the table fits its box (no scrollbar inside it), and at a phone width it may scroll only
 *       inside its own box, which itself fits the screen;</li>
 *   <li>the checkbox column is narrow and the identifier column is not stretched;</li>
 *   <li>a document taller than the window is not taller than its content plus a stated margin ({@link #BOTTOM_SLACK} pixels,
 *       {@link #BAR_ROOM} while the bulk bar is shown); a shorter one is exactly as tall as the window;</li>
 *   <li>with one row selected, the bulk bar does not overlap the last row or the pager, and the pager is in view when the
 *       page is scrolled to its end.</li>
 * </ul>
 *
 * <p>When the machine has no Chrome or Edge the test is <b>skipped</b>, with the reason, and is never reported as passed.
 * Set {@code -Dtest.chrome=path} or {@code CHROME_BIN} to point at a browser.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ListLayoutBrowserTest {

    /** How much taller than its content the document may be: the page's own bottom padding plus a little. */
    static final int BOTTOM_SLACK = 100;

    /** While the bulk bar is shown the page keeps room for it, so the limit is larger. */
    static final int BAR_ROOM = 160;

    /** {@code null} is the page's own font stack; the others are forced to test the layout against wider and narrower text. */
    private static final List<String> FONTS = java.util.Arrays.asList(null, "Verdana", "Tahoma", "Arial");

    private static final List<String> ENTITIES = List.of("tag", "category", "customer", "order", "product");

    private static final String MEASURE = """
            (function () {
              var d = document.documentElement, r = function (e) { return e.getBoundingClientRect(); };
              var ts = document.querySelector('.table-scroll'), t = document.querySelector('.data-table');
              var main = document.querySelector('main'), foot = document.querySelector('.table-footer');
              var cols = [].map.call(document.querySelectorAll('.data-table thead th'), function (e) {
                return Math.round(r(e).width); }).join('/');
              return ['docH=' + d.scrollHeight, 'winH=' + innerHeight, 'docW=' + d.scrollWidth, 'winW=' + innerWidth,
                'bodyW=' + document.body.scrollWidth, 'tsScrollW=' + ts.scrollWidth, 'tsClientW=' + ts.clientWidth,
                'tableW=' + Math.round(r(t).width), 'mainPadBottom=' + getComputedStyle(main).paddingBottom,
                'footBottom=' + Math.round(r(foot).bottom + window.scrollY), 'cols=' + cols].join(';');
            })()
            """;

    private static final String SELECT_ONE = """
            (function () {
              var box = document.querySelector('[data-select-row]'); box.click();
              var d = document.documentElement; window.scrollTo(0, d.scrollHeight);
              var bar = document.querySelector('[data-bulk-bar]'), foot = document.querySelector('.table-footer');
              var last = document.querySelector('.data-table tbody tr:last-child');
              var b = bar.getBoundingClientRect(), f = foot.getBoundingClientRect(), l = last.getBoundingClientRect();
              var over = function (a, c) { return !(a.bottom <= c.top || a.top >= c.bottom || a.right <= c.left || a.left >= c.right); };
              var hit = function (rc) { var e = document.elementFromPoint(rc.left + rc.width / 2, rc.top + rc.height / 2);
                return !!e && bar.contains(e); };
              var docH = d.scrollHeight, footAbs = Math.round(f.bottom + window.scrollY);
              var confirm = document.querySelector('[data-bulk-confirm]'); confirm.open = true;
              var docHOpen = d.scrollHeight; confirm.open = false;
              return ['docH=' + docH, 'docHOpen=' + docHOpen, 'footAbs=' + footAbs, 'barShown=' + (getComputedStyle(bar).display !== 'none'), 'barTop=' + Math.round(b.top),
                'footTop=' + Math.round(f.top), 'footBottom=' + Math.round(f.bottom), 'winH=' + innerHeight,
                'overlapFoot=' + over(b, f), 'overlapLast=' + over(b, l), 'footCovered=' + hit(f), 'lastCovered=' + hit(l),
                'footInView=' + (f.top >= 0 && f.bottom <= innerHeight), 'docW=' + d.scrollWidth, 'winW=' + innerWidth].join(';');
            })()
            """;

    @LocalServerPort
    private int port;

    /** The fonts the browser really draws the table text with, and the font-family the page asked for. */
    private static String fonts(HeadlessChrome chrome) throws Exception {
        chrome.command("DOM.enable", "{}");
        chrome.command("CSS.enable", "{}");
        String root = chrome.command("DOM.getDocument", "{\"depth\":0}");
        String rootId = root.replaceAll("(?s).*\"nodeId\":(\\d+).*", "$1");
        String cell = chrome.command("DOM.querySelector", "{\"nodeId\":" + rootId + ",\"selector\":\".data-table tbody td:nth-child(5)\"}");
        String cellId = cell.replaceAll("(?s).*\"nodeId\":(\\d+).*", "$1");
        String platform = chrome.command("CSS.getPlatformFontsForNode", "{\"nodeId\":" + cellId + "}");
        java.util.regex.Matcher family = java.util.regex.Pattern.compile("\"familyName\":\"([^\"]+)\"").matcher(platform);
        List<String> names = new ArrayList<>();
        while (family.find()) {
            names.add(family.group(1));
        }
        return "requested=" + chrome.evaluate("getComputedStyle(document.body).fontFamily") + " drawn=" + names;
    }

    private static Map<String, String> parse(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : text.split(";")) {
            int at = pair.indexOf('=');
            values.put(pair.substring(0, at), pair.substring(at + 1));
        }
        return values;
    }

    private static int number(Map<String, String> values, String key) {
        return Integer.parseInt(values.get(key).replace("px", ""));
    }

    @Test
    void everyListFitsItsScreenHasNoStrayScrollbarOrEmptyTailAndTheBarCoversNothing() throws Exception {
        Path browser = BrowserPolicy.require(); // a skip with the reason, or a failure when CI requires a browser
        List<String> problems = new ArrayList<>();
        try (HeadlessChrome chrome = HeadlessChrome.start(browser)) {
            String base = "http://localhost:" + port + "/admin";
            chrome.setViewport(1280, 900, true);
            chrome.open(base + "/login");
            chrome.runAndWaitForLoad("document.querySelector('#username').value = '" + MockBrowser.USER
                    + "'; document.querySelector('#password').value = '" + MockBrowser.PASSWORD
                    + "'; document.querySelector('form').submit(); 'ok'");
            // The machine's own font first (both schemes), then a wide, a medium and a narrow font forced on the page, because the
            // widths of text differ from one platform to the next and CI is not this machine.
            // The console's policy (rightly) refuses an inline style; the test turns it off for itself to force a font.
            chrome.command("Page.setBypassCSP", "{\"enabled\":true}");
            for (String font : FONTS) {
            for (boolean dark : font == null ? new boolean[] {true, false} : new boolean[] {true}) {
                for (int[] screen : new int[][] {{1280, 900}, {390, 844}}) {
                    chrome.setViewport(screen[0], screen[1], dark);
                    for (String entity : ENTITIES) {
                        chrome.open(base + "/" + entity);
                        if (font != null) {
                            chrome.evaluate("(function () { var s = document.createElement('style'); s.textContent = 'body { --font: \"" + font
                                    + "\", sans-serif; }'; document.head.appendChild(s); return 'ok'; })()");
                        }
                        String where = entity + " @" + screen[0] + (dark ? " dark" : " light") + (font == null ? "" : " font " + font);
                        Map<String, String> m = parse(chrome.evaluate(MEASURE));
                        System.out.println("LAYOUT " + where + " " + m);
                        if (entity.equals("product")) {
                            System.out.println("LAYOUT fonts " + where + " " + fonts(chrome));
                        }
                        check(where, screen[0], m, problems);
                        Map<String, String> s = parse(chrome.evaluate(SELECT_ONE));
                        System.out.println("LAYOUT " + where + " one row selected " + s);
                        checkSelected(where, s, problems);
                    }
                }
            }
            }
        }

        assertThat(problems).as("layout problems found in the browser").isEmpty();
    }

    private static void check(String where, int width, Map<String, String> m, List<String> problems) {
        if (number(m, "docW") > number(m, "winW") || number(m, "bodyW") > number(m, "winW")) {
            problems.add(where + ": the page scrolls sideways (document " + m.get("docW") + ", body " + m.get("bodyW")
                    + ", window " + m.get("winW") + ")");
        }
        if (width >= 1000 && number(m, "tsScrollW") > number(m, "tsClientW") + 1) {
            problems.add(where + ": the table has a scrollbar of its own at desktop width (" + m.get("tsScrollW") + " > "
                    + m.get("tsClientW") + ")");
        }
        if (number(m, "tsClientW") > number(m, "winW")) {
            problems.add(where + ": the table's box is wider than the screen");
        }
        String[] columns = m.get("cols").split("/");
        if (Integer.parseInt(columns[0]) > 64) {
            problems.add(where + ": the checkbox column is " + columns[0] + "px wide (limit 64)");
        }
        if (width >= 1000 && Integer.parseInt(columns[1]) > 110) {
            problems.add(where + ": the ID column is " + columns[1] + "px wide at desktop width (limit 110)");
        }
        int extra = number(m, "docH") - number(m, "footBottom");
        // A short page is as tall as the window (the shell fills it); only a page taller than the window can have an empty tail.
        if (number(m, "docH") > number(m, "winH") && extra > BOTTOM_SLACK) {
            problems.add(where + ": the document is " + extra + "px taller than its content (limit " + BOTTOM_SLACK + ")");
        }
    }

    private static void checkSelected(String where, Map<String, String> s, List<String> problems) {
        String selected = where + " with one row selected";
        if (!"true".equals(s.get("barShown"))) {
            problems.add(selected + ": the bulk bar is not shown");
        }
        if (!"false".equals(s.get("overlapFoot")) || !"false".equals(s.get("footCovered"))) {
            problems.add(selected + ": the bulk bar covers the pager (bar top " + s.get("barTop") + ", pager "
                    + s.get("footTop") + " to " + s.get("footBottom") + ")");
        }
        if (!"false".equals(s.get("overlapLast")) || !"false".equals(s.get("lastCovered"))) {
            problems.add(selected + ": the bulk bar covers the last row");
        }
        if (!"true".equals(s.get("footInView"))) {
            problems.add(selected + ": the pager is not in view at the end of the page");
        }
        if (number(s, "docW") > number(s, "winW")) {
            problems.add(selected + ": the page scrolls sideways");
        }
        int tail = number(s, "docH") - number(s, "footAbs");
        if (number(s, "docH") > number(s, "winH") && tail > BAR_ROOM) {
            problems.add(selected + ": the document is " + tail + "px taller than its content (limit " + BAR_ROOM + ", the room kept for the bar)");
        }
        if (number(s, "docHOpen") > number(s, "docH")) {
            problems.add(selected + ": opening the question makes the page taller (" + s.get("docH") + " to " + s.get("docHOpen") + ")");
        }
    }
}
