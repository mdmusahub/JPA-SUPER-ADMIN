package io.github.mdmusahub.jpaadmin.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Saved views and column visibility in a real browser (headless Chrome or Edge over the DevTools protocol, with nothing but the
 * JDK, the same driver as the list layout test), against the real demo application: the rules of {@code views.js} that no build
 * test can run without a JavaScript engine (D-116). Saving stores exactly the server's validated state; applying a view is a plain
 * link and restores the exact search, filters, sort, page size and hidden columns, and survives a reload; every invalid stored view
 * is dropped without a word; at most 20 views; storage that is blocked costs nothing else on the page; nothing is written to a cookie
 * or sent anywhere; markup in a name stays text; no sensitive key reaches a view.
 *
 * <p>When the machine has no Chrome or Edge the tests are <b>skipped</b>, with the reason, and never reported as passed. Set
 * {@code -Dtest.chrome=path} or {@code CHROME_BIN} to point at a browser.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SavedViewsBrowserTest {

    private static final String KEY = "jpa-admin.views./admin/product";

    /** Records every way a script could send something out; the page must never use any of them for a view. */
    private static final String SPY = "(function(){window.__calls=[];var f=window.fetch;window.fetch=function(){window.__calls.push('fetch');"
            + "return f.apply(this,arguments);};var o=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(){"
            + "window.__calls.push('xhr');return o.apply(this,arguments);};navigator.sendBeacon=function(){window.__calls.push('beacon');"
            + "return true;};var W=window.WebSocket;window.WebSocket=function(){window.__calls.push('ws');throw new Error('no');};})();";

    private static final String BLOCK_STORAGE = "Object.defineProperty(window,'localStorage',{get:function(){"
            + "throw new DOMException('blocked','SecurityError');}});";

    @LocalServerPort
    private int port;

    private HeadlessChrome signedIn(boolean spy, boolean blocked) throws Exception {
        Path browser = BrowserPolicy.require(); // a skip with the reason, or a failure when CI requires a browser
        HeadlessChrome chrome = HeadlessChrome.start(browser);
        try {
            chrome.setViewport(1280, 900, false);
            chrome.open(base() + "/login");
            chrome.runAndWaitForLoad("document.querySelector('#username').value = '" + MockBrowser.USER
                    + "'; document.querySelector('#password').value = '" + MockBrowser.PASSWORD
                    + "'; document.querySelector('form').submit(); 'ok'");
            if (spy) {
                chrome.command("Page.addScriptToEvaluateOnNewDocument", "{\"source\":\"" + SPY + "\"}");
            }
            if (blocked) {
                chrome.command("Page.addScriptToEvaluateOnNewDocument", "{\"source\":\"" + BLOCK_STORAGE + "\"}");
            }
            return chrome;
        } catch (Exception | Error failure) {
            chrome.close();
            throw failure;
        }
    }

    private String base() {
        return "http://localhost:" + port + "/admin";
    }

    private static String quote(String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static void seed(HeadlessChrome chrome, String key, String json) throws Exception {
        chrome.evaluate("localStorage.setItem(" + quote(key) + ", " + quote(json) + "); 'ok'");
    }

    private static String stored(HeadlessChrome chrome, String key) throws Exception {
        return chrome.evaluate("String(localStorage.getItem(" + quote(key) + "))");
    }

    private static String links(HeadlessChrome chrome) throws Exception {
        return chrome.evaluate("Array.prototype.map.call(document.querySelectorAll('[data-views-list] [data-views-apply]'), "
                + "function (a) { return a.textContent + ' => ' + a.getAttribute('href'); }).join(' | ')");
    }

    private static String status(HeadlessChrome chrome) throws Exception {
        return chrome.evaluate("document.querySelector('[data-views-status]').textContent");
    }

    private static void save(HeadlessChrome chrome, String name) throws Exception {
        chrome.evaluate("document.querySelector('[data-views-name]').value = " + quote(name)
                + "; document.querySelector('[data-views-save]').click(); 'ok'");
    }

    // ---- save, apply, reload ----------------------------------------------------------------------------------------

    @Test
    void savingStoresTheExactStateAndApplyingTheViewRestoresItAndSurvivesAReload() throws Exception {
        try (HeadlessChrome chrome = signedIn(false, false)) {
            chrome.open(base() + "/product");
            chrome.evaluate("localStorage.clear(); 'ok'");
            String address = "/product?q=a&hide=sku&hide=stock&sort=price&dir=desc&size=10";
            chrome.open(base() + address);

            save(chrome, "  Cheap first  ");

            String json = stored(chrome, KEY);
            assertThat(json).isEqualTo("[{\"name\":\"Cheap first\",\"q\":\"a\",\"filters\":{},\"sort\":\"price\",\"dir\":\"desc\","
                    + "\"pageSize\":10,\"hiddenColumns\":[\"sku\",\"stock\"]}]");
            assertThat(links(chrome)).isEqualTo("Cheap first => /admin/product?q=a&hide=sku&hide=stock&sort=price&dir=desc&size=10");

            chrome.open(base() + "/product");
            assertThat(chrome.evaluate("document.querySelector('.column-panel summary').textContent.trim()"))
                    .as("the plain list has nothing hidden").doesNotContain("hidden");
            chrome.runAndWaitForLoad("document.querySelector('[data-views-list] [data-views-apply]').click(); 'ok'");

            assertThat(chrome.evaluate("location.pathname + location.search"))
                    .isEqualTo("/admin/product?q=a&hide=sku&hide=stock&sort=price&dir=desc&size=10");
            assertThat(chrome.evaluate("document.querySelector('#list-search').value")).isEqualTo("a");
            assertThat(chrome.evaluate("Array.prototype.map.call(document.querySelectorAll('input[name=hide]:checked'), "
                    + "function (b) { return b.value; }).join(',')")).isEqualTo("sku,stock");
            assertThat(chrome.evaluate("Array.prototype.map.call(document.querySelectorAll('th[scope=col]'), "
                    + "function (th) { return th.textContent.trim(); }).join(',')")).doesNotContain("Sku").doesNotContain("Stock")
                    .contains("Price");
            assertThat(chrome.evaluate("document.querySelector('th[aria-sort]:not([aria-sort=none])').textContent.trim() + ' '"
                    + " + document.querySelector('th[aria-sort]:not([aria-sort=none])').getAttribute('aria-sort')"))
                    .isEqualTo("Price descending");

            chrome.open(base() + "/product?q=a&hide=sku&hide=stock&sort=price&dir=desc&size=10");
            assertThat(links(chrome)).as("the view survives a reload").startsWith("Cheap first =>");
            assertThat(stored(chrome, KEY)).isEqualTo(json);
        }
    }

    @Test
    void aNameThatLooksLikeMarkupStaysTextAndEveryAwkwardValueIsPercentEncodedInTheLink() throws Exception {
        try (HeadlessChrome chrome = signedIn(false, false)) {
            chrome.open(base() + "/product");
            seed(chrome, KEY, "[{\"name\":\"<b>x</b> & \\\"y\\\" <script>alert(1)</script>\",\"q\":\"a b/c&d=e#f'g\","
                    + "\"filters\":{},\"sort\":null,\"dir\":null,\"pageSize\":25,\"hiddenColumns\":[\"name\"]}]");
            chrome.open(base() + "/product");

            assertThat(links(chrome)).isEqualTo("<b>x</b> & \"y\" <script>alert(1)</script> => /admin/product?q=a%20b%2Fc%26d%3De%23f%27g&hide=name");
            assertThat(chrome.evaluate("String(document.querySelectorAll('[data-views-list] b, [data-views-list] script').length)"))
                    .isEqualTo("0");
        }
    }

    // ---- what is dropped --------------------------------------------------------------------------------------------

    @Test
    void everyInvalidStoredViewIsDroppedQuietlyAndTheStoreIsCleaned() throws Exception {
        try (HeadlessChrome chrome = signedIn(false, false)) {
            chrome.open(base() + "/product");
            String longName = "N".repeat(61);
            seed(chrome, KEY, "[{\"name\":\"Kept\",\"q\":\"x\",\"hiddenColumns\":[\"sku\"]},"
                    + "{\"name\":\"Unknown column\",\"hiddenColumns\":[\"nothing\"]},"
                    + "{\"name\":\"Password column\",\"hiddenColumns\":[\"password\"]},"
                    + "{\"name\":\"Unknown filter\",\"filters\":{\"filter.apiKey\":\"x\"}},"
                    + "{\"name\":\"" + longName + "\"},"
                    + "{\"name\":\"Bad direction\",\"sort\":\"price\",\"dir\":\"sideways\"},"
                    + "{\"name\":\"Direction alone\",\"sort\":null,\"dir\":\"asc\"},"
                    + "{\"name\":\"Bad page size\",\"pageSize\":30},"
                    + "\"not an object\",{\"name\":\"kept\"},{\"name\":\"\"},{\"name\":5},"
                    + "{\"name\":\"All hidden\",\"hiddenColumns\":[\"id\",\"category\",\"createdAt\",\"featured\",\"name\",\"price\","
                    + "\"sku\",\"status\",\"stock\"]}]");
            chrome.open(base() + "/product");

            assertThat(links(chrome)).isEqualTo("Kept => /admin/product?q=x&hide=sku");
            assertThat(status(chrome)).as("no notice, no error").isEmpty();
            assertThat(stored(chrome, KEY)).isEqualTo("[{\"name\":\"Kept\",\"q\":\"x\",\"filters\":{},\"sort\":null,\"dir\":null,"
                    + "\"pageSize\":25,\"hiddenColumns\":[\"sku\"]}]");
        }
    }

    @Test
    void textThatIsNotAListOfViewsIsDroppedWithoutAnError() throws Exception {
        try (HeadlessChrome chrome = signedIn(false, false)) {
            chrome.open(base() + "/product");
            for (String text : new String[] {"{this is not json", "null", "5", "\"text\"", "{\"name\":\"not an array\"}", "[[]]", ""}) {
                seed(chrome, KEY, text);
                chrome.open(base() + "/product");

                assertThat(links(chrome)).as(text).isEmpty();
                assertThat(stored(chrome, KEY)).as(text).isIn("[]", "null");
                assertThat(chrome.evaluate("String(!!document.querySelector('table'))")).as("the page still renders").isEqualTo("true");
            }
        }
    }

    @Test
    void aViewKeepsAtMostTwentyAndTheTwentyFirstIsRefused() throws Exception {
        try (HeadlessChrome chrome = signedIn(false, false)) {
            chrome.open(base() + "/product");
            StringBuilder views = new StringBuilder("[");
            for (int i = 1; i <= 22; i++) {
                views.append(i > 1 ? "," : "").append("{\"name\":\"View ").append(String.format("%02d", i)).append("\"}");
            }
            seed(chrome, KEY, views.append(']').toString());
            chrome.open(base() + "/product");

            assertThat(chrome.evaluate("String(document.querySelectorAll('[data-views-list] .views-item').length)")).isEqualTo("20");
            assertThat(chrome.evaluate("String(localStorage.getItem(" + quote(KEY) + ").indexOf('View 21') >= 0 || localStorage.getItem("
                    + quote(KEY) + ").indexOf('View 22') >= 0)")).as("the store was cut to 20").isEqualTo("false");

            save(chrome, "View 23");

            assertThat(status(chrome)).startsWith("You can keep at most 20 views");
            assertThat(chrome.evaluate("String(JSON.parse(localStorage.getItem(" + quote(KEY) + ")).length)")).isEqualTo("20");

            save(chrome, "view 07");

            assertThat(status(chrome)).startsWith("Replaced the view");
            assertThat(chrome.evaluate("String(JSON.parse(localStorage.getItem(" + quote(KEY) + ")).length)")).isEqualTo("20");
        }
    }

    @Test
    void aNameOfSixtyOneCharactersOrOfControlCharactersIsRefused() throws Exception {
        try (HeadlessChrome chrome = signedIn(false, false)) {
            chrome.open(base() + "/product");
            chrome.evaluate("localStorage.clear(); 'ok'");
            chrome.open(base() + "/product");

            save(chrome, "x".repeat(61));
            assertThat(status(chrome)).startsWith("Give the view a name of 1 to 60 characters");
            save(chrome, "tab\there");
            assertThat(status(chrome)).startsWith("Give the view a name");
            save(chrome, "   ");
            assertThat(status(chrome)).startsWith("Give the view a name");
            assertThat(stored(chrome, KEY)).isEqualTo("null");

            save(chrome, "x".repeat(60));
            assertThat(status(chrome)).startsWith("Saved the view");
        }
    }

    // ---- rename and delete ------------------------------------------------------------------------------------------

    @Test
    void renameKeepsTheViewAndRefusesAnotherViewsNameAndDeleteRemovesOnlyThatView() throws Exception {
        try (HeadlessChrome chrome = signedIn(false, false)) {
            chrome.open(base() + "/product");
            seed(chrome, KEY, "[{\"name\":\"One\"},{\"name\":\"Two\",\"hiddenColumns\":[\"sku\"]},{\"name\":\"Three\"}]");
            chrome.open(base() + "/product");

            chrome.evaluate("var li = document.querySelectorAll('[data-views-list] .views-item')[1];"
                    + "li.querySelector('[data-views-rename-name]').value = 'Two renamed'; li.querySelector('[data-views-rename]').click(); 'ok'");
            assertThat(links(chrome)).isEqualTo("One => /admin/product | Two renamed => /admin/product?hide=sku | Three => /admin/product");

            chrome.evaluate("var li = document.querySelectorAll('[data-views-list] .views-item')[1];"
                    + "li.querySelector('[data-views-rename-name]').value = 'THREE'; li.querySelector('[data-views-rename]').click(); 'ok'");
            assertThat(status(chrome)).startsWith("Another view is already called");
            assertThat(links(chrome)).contains("Two renamed");

            assertThat(chrome.evaluate("var d = document.querySelector('[data-views-list] .views-delete');"
                    + "d.tagName + ' ' + d.open + ' ' + d.querySelector('summary').getAttribute('aria-label')"))
                    .isEqualTo("DETAILS false Delete view One");
            chrome.evaluate("document.querySelectorAll('[data-views-list] .views-item')[0].querySelector('[data-views-delete]').click(); 'ok'");
            assertThat(links(chrome)).isEqualTo("Two renamed => /admin/product?hide=sku | Three => /admin/product");
        }
    }

    // ---- storage, network, cookies, keyboard -----------------------------------------------------------------------

    @Test
    void whenStorageIsBlockedThePanelSaysSoAndTheRestOfThePageStillWorks() throws Exception {
        try (HeadlessChrome chrome = signedIn(false, true)) {
            chrome.open(base() + "/product?hide=sku");

            assertThat(status(chrome)).startsWith("This browser does not allow saved views here");
            assertThat(chrome.evaluate("String(document.querySelector('[data-views-save]').disabled)")).isEqualTo("true");
            assertThat(chrome.evaluate("String(document.querySelectorAll('input[name=hide]').length > 0 && !!document.querySelector('table'))"))
                    .isEqualTo("true");
        }
    }

    @Test
    void neverWritesACookieAndNeverSendsAnything() throws Exception {
        try (HeadlessChrome chrome = signedIn(true, false)) {
            chrome.open(base() + "/product?hide=sku");
            String resources = chrome.evaluate("String(performance.getEntriesByType('resource').length)");
            String cookieBefore = chrome.evaluate("document.cookie");

            save(chrome, "Quiet view");
            chrome.evaluate("var li = document.querySelector('[data-views-list] .views-item');"
                    + "li.querySelector('[data-views-rename-name]').value = 'Quiet view 2'; li.querySelector('[data-views-rename]').click();"
                    + "document.querySelector('[data-views-list] [data-views-delete]').click(); 'ok'");

            assertThat(chrome.evaluate("String(window.__calls.length)")).as("no fetch, XHR, beacon or WebSocket").isEqualTo("0");
            assertThat(chrome.evaluate("String(performance.getEntriesByType('resource').length)")).isEqualTo(resources);
            assertThat(chrome.evaluate("document.cookie")).isEqualTo(cookieBefore);
            assertThat(chrome.evaluate("document.cookie")).doesNotContain("jpa-admin");
        }
    }

    @Test
    void enterInTheNameFieldSavesTheView() throws Exception {
        try (HeadlessChrome chrome = signedIn(false, false)) {
            chrome.open(base() + "/product");
            chrome.evaluate("localStorage.clear(); 'ok'");
            chrome.open(base() + "/product?hide=name");

            chrome.evaluate("var n = document.querySelector('[data-views-name]'); n.value = 'By Enter';"
                    + "n.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true, cancelable: true})); 'ok'");

            assertThat(links(chrome)).isEqualTo("By Enter => /admin/product?hide=name");
        }
    }

    // ---- sensitive columns ------------------------------------------------------------------------------------------

    @Test
    void aSavedViewOfACustomerListNeverHoldsTheSensitiveColumn() throws Exception {
        try (HeadlessChrome chrome = signedIn(false, false)) {
            chrome.open(base() + "/customer");
            chrome.evaluate("localStorage.clear(); 'ok'");
            chrome.open(base() + "/customer?hide=password&hide=email");

            assertThat(chrome.evaluate("document.querySelector('[data-views]').getAttribute('data-views-columns')"))
                    .doesNotContain("password").contains("email");
            save(chrome, "Customers without email");

            String json = stored(chrome, "jpa-admin.views./admin/customer");
            assertThat(json).contains("\"hiddenColumns\":[\"email\"]").doesNotContain("password");
            seed(chrome, "jpa-admin.views./admin/customer", "[{\"name\":\"Forged\",\"hiddenColumns\":[\"password\"]}]");
            chrome.open(base() + "/customer");
            assertThat(links(chrome)).as("a stored view that names the sensitive column is dropped").isEmpty();
        }
    }
}
