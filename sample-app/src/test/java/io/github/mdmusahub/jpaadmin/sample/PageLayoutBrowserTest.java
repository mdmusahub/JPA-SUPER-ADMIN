package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The layout of every kind of page in a real browser (headless Chrome or Edge, the JDK-only driver), at a desktop width (1280) and
 * a phone width (390), in both colour schemes, against the real demo application with its data (Phase 13, U9). It covers the
 * dashboard, the list of every entity, the detail page, the create and edit forms (one with an upload field) and the not-found
 * page, and the list with each of its three panels open (Filters, Columns and Saved views) and with the export button.
 *
 * <p>What it asserts for every page: the page never scrolls sideways; the table scrolls only inside its own box and the box fits the
 * screen; a table is not squeezed (no scrollbar of its own at desktop width); the document is no taller than its content plus a
 * stated margin ({@link #BOTTOM_SLACK} pixels); an open panel and the export button stay inside the screen; with a row selected the
 * pager is still in view. The width of 390 pixels is checked on every page, never left out: a test that ignored it would let the
 * phone layout break unseen (mutation f).
 *
 * <p>When the machine has no Chrome or Edge the test is <b>skipped</b>, with the reason, and is never reported as passed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PageLayoutBrowserTest {

    /** How much taller than its content the document may be: the page's own bottom padding plus a little. */
    static final int BOTTOM_SLACK = 120;

    /** The two widths: a desktop and a phone. Both are always checked (mutation f removes the phone). */
    static final int[] WIDTHS = {1280, 390};

    private static final List<String> LISTS = List.of("/category", "/customer", "/order", "/product", "/tag");

    private static final List<String> OTHER_PAGES = List.of("", "/product/1", "/product/1/edit", "/product/new", "/category/1/edit",
            "/customer/1/edit", "/order/1", "/nothing-here");

    private static final String MEASURE = """
            (function () {
              var d = document.documentElement, r = function (e) { return e.getBoundingClientRect(); };
              var ts = document.querySelector('.table-scroll'), main = document.querySelector('main');
              var out = ['docW=' + d.scrollWidth, 'bodyW=' + document.body.scrollWidth, 'winW=' + innerWidth,
                'docH=' + d.scrollHeight, 'winH=' + innerHeight, 'mainBottom=' + Math.round(r(main).bottom + window.scrollY)];
              if (ts) {
                out.push('tsScrollW=' + ts.scrollWidth, 'tsClientW=' + ts.clientWidth, 'tsLeft=' + Math.round(r(ts).left),
                  'tsRight=' + Math.round(r(ts).right));
              }
              var wide = [];
              document.querySelectorAll('body *').forEach(function (e) {
                if (wide.length < 3 && !e.closest('#sidebar') && !e.closest('.table-scroll') && !e.closest('.skip-link')
                    && !e.closest('.visually-hidden') && getComputedStyle(e).position !== 'fixed') {
                  var b = r(e);
                  if (b.width > 0 && b.right > innerWidth + 1) { wide.push(e.tagName.toLowerCase() + '.' + String(e.className).split(' ')[0]); }
                }
              });
              out.push('wide=' + wide.join('|'));
              return out.join(';');
            })()
            """;

    private static final String OPEN_PANELS = "document.querySelectorAll('details.filter-panel').forEach(function (p) { p.open = true; }); 'ok'";

    private static final String MEASURE_PANELS_AND_EXPORT = """
            (function () {
              var out = [];
              document.querySelectorAll('details.filter-panel').forEach(function (p, i) {
                var b = p.getBoundingClientRect();
                out.push('panel' + i + '=' + Math.round(b.left) + '/' + Math.round(b.right));
              });
              var x = document.querySelector('a.export-link');
              if (x) { var b = x.getBoundingClientRect(); out.push('export=' + Math.round(b.left) + '/' + Math.round(b.right)
                + '/' + (b.width > 0) + '/' + x.getAttribute('aria-label') + '/' + x.textContent.trim()); }
              return out.join(';');
            })()
            """;

    private static final String SELECT_ONE_AND_SCROLL = """
            (function () {
              var box = document.querySelector('[data-select-row]');
              if (!box) { return 'norows=true'; }
              box.click();
              var d = document.documentElement; window.scrollTo(0, d.scrollHeight);
              var foot = document.querySelector('.table-footer'), bar = document.querySelector('[data-bulk-bar]');
              var f = foot.getBoundingClientRect(), b = bar.getBoundingClientRect();
              var over = !(b.bottom <= f.top || b.top >= f.bottom || b.right <= f.left || b.left >= f.right);
              return ['barShown=' + (getComputedStyle(bar).display !== 'none'), 'footInView=' + (f.top >= 0 && f.bottom <= innerHeight),
                'barOverFoot=' + over, 'docW=' + d.scrollWidth, 'winW=' + innerWidth].join(';');
            })()
            """;

    @LocalServerPort
    private int port;

    /** The window widths the browser really reported while measuring: the proof that both widths were applied and checked. */
    private static final java.util.Set<Integer> WIDTHS_SEEN = new java.util.TreeSet<>();

    private static Map<String, String> parse(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : text.split(";")) {
            int at = pair.indexOf('=');
            if (at > 0) {
                values.put(pair.substring(0, at), pair.substring(at + 1));
            }
        }
        return values;
    }

    private static int number(Map<String, String> values, String key) {
        return Integer.parseInt(values.get(key).replace("px", ""));
    }

    @Test
    void everyKindOfPageFitsItsScreenInBothThemesAtBothWidths() throws Exception {
        List<String> problems = new ArrayList<>();
        int measured = 0;
        WIDTHS_SEEN.clear();
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            for (boolean dark : new boolean[] {false, true}) {
                for (int width : WIDTHS) {
                    browser.viewport(width, width > 600 ? 900 : 844, dark);
                    for (String path : OTHER_PAGES) {
                        browser.go(path);
                        measured++;
                        page(where(path, width, dark), width, parse(browser.js(MEASURE)), problems);
                    }
                    for (String path : LISTS) {
                        browser.go(path);
                        measured++;
                        String where = where(path, width, dark);
                        page(where, width, parse(browser.js(MEASURE)), problems);
                        panels(where, width, browser, problems);
                        selection(where, browser, problems);
                    }
                }
            }
        }

        System.out.println("LAYOUT pages measured: " + measured + " (widths " + java.util.Arrays.toString(WIDTHS) + ")");
        assertThat(measured).as("both widths and both themes of every page were measured")
                .isEqualTo(2 * WIDTHS.length * (OTHER_PAGES.size() + LISTS.size()));
        assertThat(WIDTHS_SEEN).as("the browser measured both a desktop and a phone width (a test that left the phone out would let that layout break unseen)")
                .containsExactlyInAnyOrder(1280, 390);
        assertThat(problems).as("layout problems found in the browser").isEmpty();
    }

    private static String where(String path, int width, boolean dark) {
        return (path.isEmpty() ? "dashboard" : path) + " @" + width + (dark ? " dark" : " light");
    }

    private static void page(String where, int width, Map<String, String> m, List<String> problems) {
        WIDTHS_SEEN.add(number(m, "winW"));
        if (number(m, "winW") != width) {
            problems.add(where + ": the window is " + m.get("winW") + " wide, not the " + width + " this check is for");
        }
        if (number(m, "docW") > number(m, "winW") || number(m, "bodyW") > number(m, "winW")) {
            problems.add(where + ": the page scrolls sideways (document " + m.get("docW") + ", body " + m.get("bodyW")
                    + ", window " + m.get("winW") + "; wide: " + m.get("wide") + ")");
        }
        if (m.containsKey("tsScrollW")) {
            if (width >= 1000 && number(m, "tsScrollW") > number(m, "tsClientW") + 1) {
                problems.add(where + ": the table has a scrollbar of its own at desktop width (" + m.get("tsScrollW") + " > "
                        + m.get("tsClientW") + ")");
            }
            if (number(m, "tsRight") > number(m, "winW") + 1 || number(m, "tsLeft") < -1) {
                problems.add(where + ": the table's box is not inside the screen (" + m.get("tsLeft") + " to " + m.get("tsRight") + ")");
            }
        }
        int tail = number(m, "docH") - number(m, "mainBottom");
        if (number(m, "docH") > number(m, "winH") && tail > BOTTOM_SLACK) {
            problems.add(where + ": the document is " + tail + "px taller than its content (limit " + BOTTOM_SLACK + ")");
        }
    }

    private static void panels(String where, int width, ConsoleBrowser browser, List<String> problems) throws Exception {
        String before = browser.js(MEASURE_PANELS_AND_EXPORT);
        browser.js(OPEN_PANELS);
        Map<String, String> m = parse(browser.js(MEASURE));
        page(where + " with every panel open", width, m, problems);
        for (Map.Entry<String, String> panel : parse(browser.js(MEASURE_PANELS_AND_EXPORT)).entrySet()) {
            if (panel.getKey().startsWith("panel")) {
                String[] edges = panel.getValue().split("/");
                if (Integer.parseInt(edges[1]) > width + 1 || Integer.parseInt(edges[0]) < -1) {
                    problems.add(where + ": an open panel leaves the screen (" + panel.getKey() + " " + panel.getValue() + ")");
                }
            }
        }
        assertThat(before).as(where + ": the three panels are on the page").contains("panel0=", "panel1=", "panel2=");
        Map<String, String> closed = parse(before);
        if (closed.containsKey("export")) {
            String[] export = closed.get("export").split("/", 5);
            if (Integer.parseInt(export[1]) > width + 1 || Integer.parseInt(export[0]) < -1 || !"true".equals(export[2])) {
                problems.add(where + ": the export button is not inside the screen (" + closed.get("export") + ")");
            }
            if (!export[3].contains(export[4]) || export[4].isEmpty()) {
                problems.add(where + ": the export button's name does not contain its visible words (" + closed.get("export") + ")");
            }
        } else if (where.startsWith("/product") || where.startsWith("/order") || where.startsWith("/customer")) {
            problems.add(where + ": no export button on a list that offers export");
        }
    }

    private static void selection(String where, ConsoleBrowser browser, List<String> problems) throws Exception {
        Map<String, String> s = parse(browser.js(SELECT_ONE_AND_SCROLL));
        if (s.containsKey("norows")) {
            return;
        }
        String selected = where + " with one row selected";
        if (!"true".equals(s.get("barShown"))) {
            problems.add(selected + ": the bulk bar is not shown");
        }
        if (!"true".equals(s.get("footInView"))) {
            problems.add(selected + ": the pager is not in view at the end of the page");
        }
        if (!"false".equals(s.get("barOverFoot"))) {
            problems.add(selected + ": the bulk bar covers the pager");
        }
        if (number(s, "docW") > number(s, "winW")) {
            problems.add(selected + ": the page scrolls sideways");
        }
    }
}
