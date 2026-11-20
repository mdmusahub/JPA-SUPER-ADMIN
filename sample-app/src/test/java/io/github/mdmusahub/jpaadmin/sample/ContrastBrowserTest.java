package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The contrast of every piece of visible text in a real browser, in both colour schemes, on the pages a visitor uses (Phase 13, U9):
 * the colour the browser really draws the text with against the colour it really draws behind it (the background of the nearest
 * ancestor that has one, composited with any transparency), the way WCAG 2.2 success criterion 1.4.3 measures it. Normal text must
 * reach 4.5 to 1, large text (24px, or 18.66px and bold) 3 to 1; the placeholder of a search box counts as text; the focus ring
 * must reach 3 to 1 against the page (criterion 1.4.11). Disabled controls are exempt, as the criterion says.
 *
 * <p>It measures what the browser computes, so a token that changes in one theme and not the other, or a tint that sinks a badge's
 * text, fails here. It cannot judge text on a background image or a gradient (the console has none), text drawn inside an image, the
 * hover state, or anything the visitor's own browser settings change.
 *
 * <p>When the machine has no Chrome or Edge the test is <b>skipped</b>, with the reason, and is never reported as passed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContrastBrowserTest {

    static final double NORMAL = 4.5;
    static final double LARGE = 3.0;

    private static final List<String> PAGES = List.of("", "/product", "/product/1", "/product/1/edit", "/product/new", "/customer",
            "/order", "/category/1/edit", "/nothing-here");

    private static final String OPEN_PANELS = "document.querySelectorAll('details').forEach(function (p) { p.open = true; }); 'ok'";

    /** Returns {@code n=...;min=...;fails=...} where each failure is {@code tag.class|text|foreground|background|ratio|needed}. */
    private static final String MEASURE = """
            (function () {
              function parse(c) {
                var m = /^rgba?\\(([^)]+)\\)$/.exec(c);
                if (m) { var p = m[1].split(/[ ,\\/]+/).filter(Boolean).map(parseFloat); return [p[0], p[1], p[2], p.length > 3 ? p[3] : 1]; }
                m = /^color\\(srgb ([^)]+)\\)$/.exec(c);
                if (m) { var q = m[1].split(/[ \\/]+/).filter(Boolean).map(parseFloat); return [q[0] * 255, q[1] * 255, q[2] * 255, q.length > 3 ? q[3] : 1]; }
                return null;
              }
              function over(top, under) {
                var a = top[3];
                return [top[0] * a + under[0] * (1 - a), top[1] * a + under[1] * (1 - a), top[2] * a + under[2] * (1 - a), 1];
              }
              function lum(c) {
                var f = c.slice(0, 3).map(function (v) { v = v / 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); });
                return 0.2126 * f[0] + 0.7152 * f[1] + 0.0722 * f[2];
              }
              function ratio(a, b) {
                var x = lum(a), y = lum(b);
                return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
              }
              function background(el) {
                var layers = [];
                for (var e = el; e; e = e.parentElement) {
                  var cs = getComputedStyle(e);
                  if (cs.backgroundImage !== 'none') { return null; }
                  var bg = parse(cs.backgroundColor);
                  if (bg && bg[3] > 0) { layers.push(bg); if (bg[3] >= 1) { break; } }
                }
                var base = [255, 255, 255, 1];
                for (var i = layers.length - 1; i >= 0; i--) { base = over(layers[i], base); }
                return base;
              }
              function describe(el) {
                return el.tagName.toLowerCase() + '.' + String(el.className && el.className.baseVal !== undefined ? el.className.baseVal : el.className).split(' ')[0];
              }
              var n = 0, min = 99, fails = [];
              function check(el, fg, text, size, weight) {
                var bg = background(el);
                if (!fg || !bg) { return; }
                var f = fg[3] < 1 ? over(fg, bg) : fg;
                var large = size >= 24 || (size >= 18.66 && weight >= 700);
                var need = large ? 3 : 4.5;
                var r = ratio(f, bg);
                n++;
                if (r < min) { min = r; }
                if (r < need && fails.length < 6) {
                  fails.push([describe(el), text.slice(0, 24), f.map(Math.round).join(','), bg.map(Math.round).join(','), r.toFixed(2), need].join('|'));
                }
              }
              document.querySelectorAll('body *').forEach(function (el) {
                if (['SCRIPT', 'STYLE', 'TEMPLATE', 'NOSCRIPT'].indexOf(el.tagName) >= 0 || el.closest('template, [hidden], .visually-hidden, .skip-link')) { return; }
                var cs = getComputedStyle(el);
                if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) { return; }
                if (el.matches(':disabled') || el.getAttribute('aria-disabled') === 'true') { return; }
                var rect = el.getBoundingClientRect();
                if (rect.width === 0 || rect.height === 0) { return; }
                var own = '';
                el.childNodes.forEach(function (node) { if (node.nodeType === 3) { own += node.nodeValue; } });
                own = own.replace(/\\s+/g, ' ').trim();
                if (own !== '') {
                  var svg = el instanceof SVGElement;
                  check(el, parse(svg ? cs.fill : cs.color), own, parseFloat(cs.fontSize), parseInt(cs.fontWeight, 10));
                }
                if ((el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') && el.getAttribute('placeholder') && el.value === '') {
                  var ph = getComputedStyle(el, '::placeholder');
                  check(el, parse(ph.color), 'placeholder', parseFloat(cs.fontSize), 400);
                }
              });
              // The focus ring, against the page: an outline set through the style object (allowed by the policy) resolves the token.
              var probe = document.createElement('span');
              probe.style.outlineColor = 'var(--focus-ring)';
              document.body.appendChild(probe);
              var ring = parse(getComputedStyle(probe).outlineColor);
              var page = background(document.body);
              var ringRatio = ring && page ? ratio(ring, page) : 0;
              probe.remove();
              return 'n=' + n + ';min=' + min.toFixed(2) + ';ring=' + ringRatio.toFixed(2) + ';fails=' + fails.join(' # ');
            })()
            """;

    @LocalServerPort
    private int port;

    @Test
    void everyVisibleTextReachesItsContrastInBothThemesAndTheFocusRingIsVisible() throws Exception {
        List<String> problems = new ArrayList<>();
        double lowest = 99;
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            for (boolean dark : new boolean[] {false, true}) {
                browser.viewport(1280, 900, dark);
                for (String path : PAGES) {
                    browser.go(path);
                    for (boolean open : new boolean[] {false, true}) {
                        if (open) {
                            browser.js(OPEN_PANELS);
                        }
                        String answer = browser.js(MEASURE);
                        String where = (path.isEmpty() ? "dashboard" : path) + (dark ? " dark" : " light") + (open ? " panels open" : "");
                        System.out.println("CONTRAST " + where + " " + answer.replaceAll(";fails=.*", ""));
                        assertThat(answer).as(where).startsWith("n=");
                        int texts = Integer.parseInt(answer.substring(2, answer.indexOf(';')));
                        double min = Double.parseDouble(answer.replaceAll(".*;min=([0-9.]+);.*", "$1"));
                        double ring = Double.parseDouble(answer.replaceAll(".*;ring=([0-9.]+);.*", "$1"));
                        String fails = answer.substring(answer.indexOf("fails=") + 6);
                        lowest = Math.min(lowest, min);
                        assertThat(texts).as(where + ": the measurement found text").isGreaterThan(5);
                        if (!fails.isEmpty()) {
                            problems.add(where + ": " + fails);
                        }
                        if (ring < 3.0) {
                            problems.add(where + ": the focus ring reaches only " + ring + " to 1 against the page");
                        }
                    }
                }
            }
        }
        System.out.println("CONTRAST lowest text ratio over every page, theme and open panel: " + lowest);
        assertThat(lowest).isGreaterThanOrEqualTo(LARGE);
        assertThat(problems).as("text below its contrast, as the browser draws it").isEmpty();
    }
}
