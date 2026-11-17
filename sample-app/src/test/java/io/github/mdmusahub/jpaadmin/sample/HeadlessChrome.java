package io.github.mdmusahub.jpaadmin.sample;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A very small browser driver for tests: it starts the machine's Chrome (or Edge) in headless mode and talks to it over the
 * DevTools protocol with nothing but the JDK ({@code java.net.http}), so the project gets no new dependency (AI-8, CODE-5).
 * It is test support only. It can open a page, set the window size and the colour scheme, run a script and read a value.
 *
 * <p>No browser, no test: {@link #find()} says whether there is one, and a test that needs it is skipped with the reason
 * when there is none (never reported as passed). A temporary profile folder is used and removed, and only the process this
 * class started is stopped; the user's own browser is never touched.
 */
final class HeadlessChrome implements AutoCloseable {

    private static final Duration WAIT = Duration.ofSeconds(30);

    private final Process process;
    private final Path profile;
    private final WebSocket socket;
    private final AtomicInteger ids = new AtomicInteger();
    private final ConcurrentHashMap<Integer, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    private volatile CountDownLatch loaded = new CountDownLatch(1);

    private HeadlessChrome(Process process, Path profile, WebSocket socket) {
        this.process = process;
        this.profile = profile;
        this.socket = socket;
    }

    /** The browser to use: {@code -Dtest.chrome=path}, then {@code CHROME_BIN}, then the usual places. */
    static Optional<Path> find() {
        List<String> candidates = Stream.of(System.getProperty("test.chrome"), System.getenv("CHROME_BIN"),
                "C:/Program Files/Google/Chrome/Application/chrome.exe",
                "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
                "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
                "C:/Program Files/Microsoft/Edge/Application/msedge.exe", "/usr/bin/google-chrome", "/usr/bin/chromium",
                "/usr/bin/chromium-browser", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
                .filter(path -> path != null && !path.isBlank()).toList();
        return candidates.stream().map(Path::of).filter(Files::isExecutable).findFirst();
    }

    static HeadlessChrome start(Path browser) throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        Path profile = Files.createTempDirectory("jpa-admin-chrome-");
        Process process = new ProcessBuilder(browser.toString(), "--headless=new", "--disable-gpu", "--no-first-run",
                "--no-default-browser-check", "--remote-debugging-port=" + port, "--user-data-dir=" + profile,
                "--remote-allow-origins=*", "about:blank").redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            HttpClient http = HttpClient.newHttpClient();
            String debuggerUrl = null;
            long until = System.nanoTime() + WAIT.toNanos();
            while (debuggerUrl == null && System.nanoTime() < until) {
                try {
                    String body = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list")).build(),
                            HttpResponse.BodyHandlers.ofString()).body();
                    Matcher matcher = Pattern.compile("\"type\":\\s*\"page\"[^}]*?\"webSocketDebuggerUrl\":\\s*\"([^\"]+)\"|"
                            + "\"webSocketDebuggerUrl\":\\s*\"([^\"]+)\"[^}]*?\"type\":\\s*\"page\"").matcher(body);
                    if (matcher.find()) {
                        debuggerUrl = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                    }
                } catch (IOException notYet) {
                    Thread.sleep(200);
                }
                if (debuggerUrl == null) {
                    Thread.sleep(200);
                }
            }
            if (debuggerUrl == null) {
                throw new IllegalStateException("The browser did not offer a DevTools page within " + WAIT.toSeconds() + " seconds.");
            }
            HeadlessChrome[] holder = new HeadlessChrome[1];
            WebSocket socket = http.newWebSocketBuilder().connectTimeout(WAIT)
                    .buildAsync(URI.create(debuggerUrl), new Listener(holder)).get(30, TimeUnit.SECONDS);
            HeadlessChrome chrome = new HeadlessChrome(process, profile, socket);
            holder[0] = chrome;
            chrome.send("Page.enable", "{}");
            chrome.send("Runtime.enable", "{}");
            return chrome;
        } catch (Exception failure) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            throw failure;
        }
    }

    // ---- what a test can do -------------------------------------------------------------------------------------------

    /** Window size and colour scheme, as a desktop browser ({@code dark} follows the operating system's setting). */
    void setViewport(int width, int height, boolean dark) throws Exception {
        send("Emulation.setDeviceMetricsOverride", "{\"width\":" + width + ",\"height\":" + height
                + ",\"deviceScaleFactor\":1,\"mobile\":false}");
        send("Emulation.setEmulatedMedia", "{\"features\":[{\"name\":\"prefers-color-scheme\",\"value\":\""
                + (dark ? "dark" : "light") + "\"}]}");
    }

    /** Opens the address and waits until the page has loaded. */
    void open(String url) throws Exception {
        loaded = new CountDownLatch(1);
        send("Page.navigate", "{\"url\":\"" + url + "\"}");
        waitForLoad();
    }

    /** Runs a script that starts a navigation (a form submit) and waits for the next page to load. */
    void runAndWaitForLoad(String script) throws Exception {
        loaded = new CountDownLatch(1);
        evaluate(script);
        waitForLoad();
    }

    /** Runs an expression and returns its value as text (the expression should return a string). */
    String evaluate(String expression) throws Exception {
        String response = send("Runtime.evaluate", "{\"expression\":" + quote(expression) + ",\"returnByValue\":true}");
        if (response.contains("\"exceptionDetails\"")) {
            throw new IllegalStateException("The page's script failed: " + response);
        }
        Matcher value = Pattern.compile("\"result\":\\{\"type\":\"string\",\"value\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(response);
        return value.find() ? value.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : "";
    }

    private void waitForLoad() throws Exception {
        if (!loaded.await(WAIT.toSeconds(), TimeUnit.SECONDS)) {
            throw new IllegalStateException("The page did not finish loading within " + WAIT.toSeconds() + " seconds.");
        }
    }

    private String send(String method, String params) throws Exception {
        int id = ids.incrementAndGet();
        CompletableFuture<String> answer = new CompletableFuture<>();
        pending.put(id, answer);
        socket.sendText("{\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + params + "}", true).get(WAIT.toSeconds(),
                TimeUnit.SECONDS);
        return answer.get(WAIT.toSeconds(), TimeUnit.SECONDS);
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return out.append('"').toString();
    }

    @Override
    public void close() {
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        } catch (RuntimeException ignored) {
            // The process is stopped next anyway.
        }
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(10, TimeUnit.SECONDS);
            Thread.sleep(500); // Windows releases the profile folder a moment after the process ends
            try (Stream<Path> files = Files.walk(profile)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        } catch (IOException | InterruptedException ignored) {
            // A temporary folder that stays behind is harmless.
        }
    }

    /** Reads whole messages: the answer to a command, and the "page loaded" event. */
    private static final class Listener implements WebSocket.Listener {

        private static final Pattern ID = Pattern.compile("^\\{\"id\":(\\d+)");
        private final HeadlessChrome[] owner;
        private final StringBuilder message = new StringBuilder();

        Listener(HeadlessChrome[] owner) {
            this.owner = owner;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                String text = message.toString();
                message.setLength(0);
                HeadlessChrome chrome = owner[0];
                if (chrome != null) {
                    Matcher id = ID.matcher(text);
                    if (id.find()) {
                        CompletableFuture<String> waiting = chrome.pending.remove(Integer.parseInt(id.group(1)));
                        if (waiting != null) {
                            waiting.complete(text);
                        }
                    } else if (text.contains("\"method\":\"Page.loadEventFired\"")) {
                        chrome.loaded.countDown();
                    }
                }
            }
            webSocket.request(1);
            return null;
        }
    }
}
