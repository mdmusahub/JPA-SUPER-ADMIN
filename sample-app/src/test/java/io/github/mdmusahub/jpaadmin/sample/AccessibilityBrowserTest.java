package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The structure that assistive technology relies on, checked in a real browser on every kind of page (Phase 13, U9, UI-4): a page
 * language, exactly one top-level heading, landmarks that are named when there are several, unique ids, and an accessible name
 * for every link, button, form control, summary, image and table that a visitor can reach (from {@code aria-label},
 * {@code aria-labelledby}, an associated {@code label}, the visible text, the image text or the title, in that order, as the
 * accessible name computation does). A control the browser would announce as just "button" fails here.
 *
 * <p>It does not replace a screen reader pass: what is said, in which order, and how it feels are manual items in the Phase 13
 * report.
 *
 * <p>When the machine has no Chrome or Edge the test is <b>skipped</b>, with the reason, and is never reported as passed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccessibilityBrowserTest {

    private static final List<String> PAGES = List.of("", "/product", "/product/1", "/product/1/edit", "/product/new", "/customer",
            "/order", "/category", "/category/1/edit", "/tag", "/nothing-here");

    /** {@code unnamed=...;ids=...;h1=...;lang=...;navs=...;mains=...}: only what is wrong is listed, at most a few entries each. */
    private static final String AUDIT = """
            (function () {
              function text(e) { return (e.textContent || '').replace(/\\s+/g, ' ').trim(); }
              function byId(id) { var x = document.getElementById(id); return x ? text(x) : ''; }
              function name(e) {
                var v = e.getAttribute('aria-label');
                if (v && v.trim()) { return v.trim(); }
                v = e.getAttribute('aria-labelledby');
                if (v) { var t = v.split(' ').map(byId).join(' ').trim(); if (t) { return t; } }
                if (e.labels && e.labels.length) { var l = Array.prototype.map.call(e.labels, text).join(' ').trim(); if (l) { return l; } }
                if (e.tagName === 'INPUT' && ['submit', 'button', 'reset'].indexOf(e.type) >= 0 && e.value) { return e.value; }
                var t2 = text(e);
                if (t2) { return t2; }
                var img = e.querySelector('img[alt]');
                if (img && img.getAttribute('alt').trim()) { return img.getAttribute('alt'); }
                var svgTitle = e.querySelector('title');
                if (svgTitle && text(svgTitle)) { return text(svgTitle); }
                return (e.getAttribute('title') || '').trim();
              }
              function shown(e) {
                if (e.closest('template, [hidden]')) { return false; }
                var cs = getComputedStyle(e);
                return cs.display !== 'none' && cs.visibility !== 'hidden';
              }
              var unnamed = [];
              document.querySelectorAll('a[href], button, input:not([type=hidden]), select, textarea, summary, [role=button], [role=link]').forEach(function (e) {
                if (!shown(e)) { return; }
                if (name(e) === '' && unnamed.length < 5) {
                  unnamed.push(e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + '.' + String(e.className).split(' ')[0]);
                }
              });
              document.querySelectorAll('img').forEach(function (e) {
                if (shown(e) && !e.hasAttribute('alt') && unnamed.length < 5) { unnamed.push('img-without-alt'); }
              });
              document.querySelectorAll('table').forEach(function (e) {
                if (shown(e) && !e.querySelector('caption') && !e.getAttribute('aria-label') && !e.getAttribute('aria-labelledby')
                    && unnamed.length < 5) { unnamed.push('table-without-name'); }
              });
              var seen = {}, dup = [];
              document.querySelectorAll('[id]').forEach(function (e) { if (seen[e.id] && dup.length < 5) { dup.push(e.id); } seen[e.id] = true; });
              var navs = [], unlabelled = 0;
              document.querySelectorAll('nav, aside, [role=navigation], [role=region], [role=search]').forEach(function (e) {
                if (shown(e) && !e.getAttribute('aria-label') && !e.getAttribute('aria-labelledby')) { unlabelled++; }
              });
              var landmarksOfAKind = document.querySelectorAll('nav').length;
              return ['unnamed=' + unnamed.join(','), 'ids=' + dup.join(','), 'h1=' + document.querySelectorAll('h1').length,
                'lang=' + document.documentElement.lang, 'unlabelled=' + unlabelled, 'navs=' + landmarksOfAKind,
                'mains=' + document.querySelectorAll('main').length, 'title=' + (document.title ? 'yes' : 'no')].join(';');
            })()
            """;

    @LocalServerPort
    private int port;

    @Test
    void everyPageHasALanguageOneMainOneTopHeadingUniqueIdsAndANameForEveryControl() throws Exception {
        List<String> problems = new ArrayList<>();
        try (ConsoleBrowser browser = ConsoleBrowser.open(port)) {
            for (boolean dark : new boolean[] {false, true}) {
                for (int width : new int[] {1280, 390}) {
                    browser.viewport(width, width > 600 ? 900 : 844, dark);
                    for (String path : PAGES) {
                        browser.go(path);
                        String answer = browser.js(AUDIT);
                        String where = (path.isEmpty() ? "dashboard" : path) + " @" + width + (dark ? " dark" : " light");
                        if (!answer.startsWith("unnamed=;ids=;h1=1;lang=en;unlabelled=0;")) {
                            problems.add(where + ": " + answer);
                        }
                        if (!answer.contains(";mains=1;") || !answer.endsWith("title=yes")) {
                            problems.add(where + ": expected one main landmark and a title: " + answer);
                        }
                    }
                }
            }
        }

        assertThat(problems).as("accessibility structure problems found in the browser").isEmpty();
    }
}
