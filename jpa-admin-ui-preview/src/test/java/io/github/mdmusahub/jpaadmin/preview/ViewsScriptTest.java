package io.github.mdmusahub.jpaadmin.preview;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * {@code views.js} read as text (D-116): the build has no JavaScript engine, so the rules that must never change are checked
 * on the source, comments removed. A saved view lives only in {@code localStorage}, in the browser; the file must never write
 * a cookie, send a request, build markup from text, navigate on its own, or touch anything that looks like a credential.
 * The behaviour of the cleaning rules is checked in a real browser (the Phase 12 report lists the cases).
 */
class ViewsScriptTest {

    private static final String RAW = read();
    /** The code without block comments and line comments. The file has no {@code //} inside a string or a pattern. */
    private static final String CODE = RAW.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)(?<![:\\\\])//.*$", "");

    private static String read() {
        try {
            return new String(new PathMatchingResourcePatternResolver().getResource("classpath:jpa-admin/assets/js/views.js")
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- what the file must never do ---------------------------------------------------------------------------------------

    @Test
    void itNeverWritesACookieOrTouchesAnotherStore() {
        assertThat(CODE.toLowerCase(Locale.ROOT)).doesNotContain("cookie", "sessionstorage", "indexeddb", "caches.", "serviceworker");
    }

    @Test
    void itNeverSendsAnythingAnywhere() {
        assertThat(CODE).doesNotContain("fetch(", "XMLHttpRequest", "sendBeacon", "WebSocket", "EventSource", "new Image", "import(",
                "importScripts", ".submit(", "FormData", "navigator.");
    }

    @Test
    void itNeverNavigatesByItselfBecauseApplyingAViewIsAPlainLink() {
        assertThat(CODE).doesNotContain("location", "window.open", "history.", ".href =", ".href=", "document.URL");
    }

    @Test
    void itNeverBuildsMarkupFromText() {
        assertThat(CODE).doesNotContain("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "createContextualFragment",
                "DOMParser", "eval(", "new Function", "setTimeout(\"", "setInterval(\"");
        assertThat(CODE).contains("textContent").contains("setAttribute('href'");
    }

    @Test
    void itNeverMentionsAnythingThatLooksLikeACredential() {
        assertThat(CODE.toLowerCase(Locale.ROOT)).doesNotContain("password", "token", "ticket", "csrf", "secret", "session", "authorization");
    }

    @Test
    void theSourceIsPlainAsciiSoNoEditorOrToolCanChangeWhatARegularExpressionMeans() {
        assertThat(RAW.chars().filter(c -> c > 126 || (c < 32 && c != '\n')).count()).isZero();
    }

    @Test
    void itIsPlainBrowserScriptWithNoModuleSystemAndNoBuildStep() {
        assertThat(CODE).doesNotContain("import ", "require(", "export ", "=>", "let ", "const ", "async ", "await ");
        assertThat(CODE).startsWith("\n(function () {\n  'use strict';");
    }

    // ---- the storage ----------------------------------------------------------------------------------------------------------

    @Test
    void everyStorageAccessSitsInsideATryWithACatch() {
        Matcher access = Pattern.compile("window\\.localStorage\\.(getItem|setItem)").matcher(CODE);
        int found = 0;
        while (access.find()) {
            found++;
            String before = CODE.substring(Math.max(0, access.start() - 60), access.start());
            String after = CODE.substring(access.end(), Math.min(CODE.length(), access.end() + 160));
            assertThat(before).as("before access %d", found).contains("try {");
            assertThat(after).as("after access %d", found).contains("} catch (e) {");
        }
        assertThat(found).as("one read and one write, nothing else").isEqualTo(2);
        assertThat(CODE.split("localStorage", -1).length - 1).isEqualTo(2);
    }

    @Test
    void itStoresOnlyTheViewJsonUnderOneKeyOfItsOwn() {
        assertThat(CODE).contains("var KEY = 'jpa-admin.views.' + base;", "JSON.stringify(views)", "getItem(KEY)", "setItem(KEY, ");
        assertThat(CODE.split("setItem\\(", -1).length - 1).isEqualTo(1);
        assertThat(CODE).doesNotContain("removeItem", "localStorage.clear", "localStorage[");
    }

    @Test
    void aStoredViewIsReadInsideATryAndNeverTrusted() {
        assertThat(CODE).contains("parsed = JSON.parse(raw);").contains("Array.isArray(parsed)");
        assertThat(CODE.indexOf("JSON.parse")).isGreaterThan(CODE.indexOf("try {"));
    }

    // ---- the limits ------------------------------------------------------------------------------------------------------------

    @Test
    void theLimitsAreTheDocumentedOnes() {
        assertThat(CODE).contains("var MAX_VIEWS = 20;", "var MAX_NAME = 60;", "var MAX_TEXT = 100;", "var MAX_FILTERS = 10;",
                "var DEFAULT_SIZE = 25;", "var PAGE_SIZES = [10, 25, 50, 100];");
    }

    @Test
    void everyStoredViewIsCheckedAgainstWhatThePageOffersAndTheCapIsEnforcedTwice() {
        assertThat(CODE).contains("columns.indexOf(value[i]) < 0", "filterKeys.indexOf(filterBase(key)) < 0",
                "columns.indexOf(sort) < 0", "PAGE_SIZES.indexOf(size) < 0", "value.length >= columns.length",
                "key.indexOf('filter.') !== 0");
        assertThat(CODE).as("on reading, and on saving").contains("views.length < MAX_VIEWS ? clean(parsed[i]) : null")
                .contains("at < 0 && views.length >= MAX_VIEWS");
        assertThat(CODE).contains("name.length >= 1 && plainText(name, MAX_NAME)");
    }

    @Test
    void aNameCannotHoldControlOrBidirectionalOverrideCharacters() {
        assertThat(CODE).contains("var CONTROL = /[\\x00-\\x1f\\x7f-\\x9f]/;",
                "(code >= 0x202a && code <= 0x202e) || (code >= 0x2066 && code <= 0x2069)", "!CONTROL.test(value) && !reorders(value)");
    }

    // ---- the links ------------------------------------------------------------------------------------------------------------

    @Test
    void theEncoderEscapesEverythingTheServersLinkWriterEscapes() {
        assertThat(CODE).contains("encodeURIComponent(value).replace(/[!~*'()]/g,",
                "'%' + c.charCodeAt(0).toString(16).toUpperCase()");
        // Java: letters, digits and - _ . stay; encodeURIComponent also leaves ! ~ * ' ( ), which the replace escapes.
    }

    @Test
    void everyValueInALinkGoesThroughTheEncoder() {
        List<String> concatenations = new ArrayList<>();
        Matcher parts = Pattern.compile("parts\\.push\\(([^;]*)\\);").matcher(CODE);
        while (parts.find()) {
            concatenations.add(parts.group(1));
        }
        assertThat(concatenations).isNotEmpty();
        for (String push : concatenations) {
            if (push.contains("view.pageSize")) {
                continue; // a number from the list 10, 25, 50, 100, checked before it gets here
            }
            assertThat(push).as(push).contains("enc(");
        }
    }

    @Test
    void aLinkNeverCarriesAPage() {
        assertThat(CODE).doesNotContain("page=", "'page'", "\"page\"", "page:");
    }

    @Test
    void theBaseOfALinkMustBeALocalPathUnderTheConsole() {
        assertThat(CODE).contains("/^\\/[A-Za-z0-9_-]+(?:\\/[A-Za-z0-9_-]+)*$/.test(base)");
        assertThat(CODE).contains("saveButton.disabled = true;");
    }

    @Test
    void theSavedStateIsTheServersEffectiveStateNotTheRawAddress() {
        assertThat(CODE).contains("panel.getAttribute('data-views-state')");
        assertThat(CODE).doesNotContain("window.location", "document.location", "location.search");
    }
}
