package io.github.mdmusahub.jpaadmin.sample;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A signed-in headless browser for the Phase 13 browser tests: Chrome or Edge over the DevTools protocol (the JDK-only
 * {@link HeadlessChrome} driver, no dependency), signed in to the real demo application on a random port, with helpers for the
 * few things the tests keep doing: open an address, resize, run a script that returns a short string, and press a key like a
 * real keyboard does (trusted events, so the browser's own Tab order and focus rules apply).
 *
 * <p>Nothing here turns the content security policy off: the policy is the packaged application's own (a test that needs to
 * show it works in {@link CspBrowserTest} asks the page to break it and expects the browser to refuse).
 *
 * <p>When the machine has no Chrome or Edge, {@link #open} <b>skips</b> the calling test with the reason (never a pass).
 */
final class ConsoleBrowser implements AutoCloseable {

    static final String SKIP_REASON = "SKIPPED, not passed: no Chrome or Edge was found on this machine "
            + "(set -Dtest.chrome=<path> or CHROME_BIN to run the browser tests)";

    private final HeadlessChrome chrome;
    private final String base;

    private ConsoleBrowser(HeadlessChrome chrome, String base) {
        this.chrome = chrome;
        this.base = base;
    }

    /** Whether a browser exists on this machine. */
    static boolean available() {
        return HeadlessChrome.find().isPresent();
    }

    /** Starts the browser and signs in. Skips the test (with the reason) when there is no browser. */
    static ConsoleBrowser open(int port) throws Exception {
        Path browser = HeadlessChrome.find().orElse(null);
        assumeTrue(browser != null, SKIP_REASON);
        HeadlessChrome chrome = HeadlessChrome.start(browser);
        try {
            String base = "http://localhost:" + port + "/admin";
            chrome.command("Emulation.setFocusEmulationEnabled", "{\"enabled\":true}");
            chrome.setViewport(1280, 900, false);
            chrome.open(base + "/login");
            chrome.runAndWaitForLoad("document.querySelector('#username').value = '" + MockBrowser.USER
                    + "'; document.querySelector('#password').value = '" + MockBrowser.PASSWORD
                    + "'; document.querySelector('form').submit(); 'ok'");
            return new ConsoleBrowser(chrome, base);
        } catch (Exception | Error failure) {
            chrome.close();
            throw failure;
        }
    }

    HeadlessChrome chrome() {
        return chrome;
    }

    /** The console's address, for example {@code http://localhost:1234/admin}. */
    String base() {
        return base;
    }

    /** Opens a page of the console (the path starts with a slash, or is empty for the dashboard). */
    void go(String path) throws Exception {
        chrome.open(base + path);
    }

    /** Window size and colour scheme. */
    void viewport(int width, int height, boolean dark) throws Exception {
        chrome.setViewport(width, height, dark);
    }

    /** Runs an expression that returns a string. */
    String js(String expression) throws Exception {
        return chrome.evaluate(expression);
    }

    /** Runs a script that starts a navigation and waits for the next page. */
    void jsAndWait(String script) throws Exception {
        chrome.runAndWaitForLoad(script);
    }

    /** A key press: down, then up, with the browser's own handling (Tab moves focus, "/" reaches the page's key handlers). */
    void press(String key) throws Exception {
        String[] data = switch (key) {
            case "Tab" -> new String[] {"Tab", "Tab", "9", null};
            case "Enter" -> new String[] {"Enter", "Enter", "13", "\\r"};
            case "Escape" -> new String[] {"Escape", "Escape", "27", null};
            case "/" -> new String[] {"/", "Slash", "191", "/"};
            case " " -> new String[] {" ", "Space", "32", " "};
            default -> throw new IllegalArgumentException("no such key in this helper: " + key);
        };
        String text = data[3] == null ? "" : ",\"text\":\"" + data[3] + "\"";
        chrome.command("Input.dispatchKeyEvent", "{\"type\":\"" + (data[3] == null ? "rawKeyDown" : "keyDown") + "\",\"key\":\""
                + data[0] + "\",\"code\":\"" + data[1] + "\",\"windowsVirtualKeyCode\":" + data[2] + text + "}");
        chrome.command("Input.dispatchKeyEvent", "{\"type\":\"keyUp\",\"key\":\"" + data[0] + "\",\"code\":\"" + data[1]
                + "\",\"windowsVirtualKeyCode\":" + data[2] + "}");
    }

    /** Shift+Tab. */
    void pressShiftTab() throws Exception {
        chrome.command("Input.dispatchKeyEvent", "{\"type\":\"rawKeyDown\",\"key\":\"Tab\",\"code\":\"Tab\","
                + "\"windowsVirtualKeyCode\":9,\"modifiers\":8}");
        chrome.command("Input.dispatchKeyEvent", "{\"type\":\"keyUp\",\"key\":\"Tab\",\"code\":\"Tab\","
                + "\"windowsVirtualKeyCode\":9,\"modifiers\":8}");
    }

    /** A short description of the focused element: tag, id, and its accessible text. */
    String focused() throws Exception {
        return js("(function () { var e = document.activeElement; if (!e) { return 'none'; } var t = e.getAttribute('aria-label') "
                + "|| (e.textContent || '').trim().replace(/\\s+/g, ' ').slice(0, 40) || e.getAttribute('name') || e.id || ''; "
                + "return e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + ' [' + t + ']'; })()");
    }

    /** Puts a file in a file input chosen by a selector, as a visitor choosing it in the file dialog would. */
    void chooseFile(String selector, Path file) throws Exception {
        chrome.command("DOM.enable", "{}");
        String root = chrome.command("DOM.getDocument", "{\"depth\":0}");
        String rootId = first(root, "\"nodeId\":(\\d+)");
        String found = chrome.command("DOM.querySelector", "{\"nodeId\":" + rootId + ",\"selector\":\"" + selector + "\"}");
        String nodeId = first(found, "\"nodeId\":(\\d+)");
        chrome.command("DOM.setFileInputFiles", "{\"nodeId\":" + nodeId + ",\"files\":[\""
                + file.toAbsolutePath().toString().replace("\\", "/") + "\"]}");
    }

    private static String first(String text, String regex) {
        Matcher match = Pattern.compile(regex).matcher(text);
        if (!match.find()) {
            throw new IllegalStateException("The browser answered something unexpected: " + text);
        }
        return match.group(1);
    }

    @Override
    public void close() {
        chrome.close();
    }
}
