package net.kroia.stockmarket.testing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Headless Firefox driven over WebDriver BiDi, for the web interface's browser test and its docs picture. Plain Java
 * (JDK HTTP client + Gson, no Minecraft API), so it compiles on every port. Each call starts its own Firefox (own
 * temp profile, {@code --no-remote}, US locale) at 1920x1080, and only that Firefox and its child processes are
 * ended afterwards. Calls block: never run them on the server thread while the page talks to that server.
 */
public final class HeadlessFirefox {

    /** System property with the Firefox executable; default its Windows install path. */
    public static final String PROPERTY = "stockmarket.firefox";

    private HeadlessFirefox() {
    }

    /** @return the Firefox executable from {@link #PROPERTY} (default its Windows install path), or null if it isn't there */
    public static @Nullable Path find() {
        Path firefox = Paths.get(System.getProperty(PROPERTY, "C:/Program Files/Mozilla Firefox/firefox.exe"));
        return Files.isRegularFile(firefox) ? firefox : null;
    }

    /**
     * Opens {@code url}, runs {@code script} (a JavaScript expression giving a promise of true), waits
     * {@code settleMs} and returns the PNG of the page.
     *
     * @throws IllegalStateException if the script fails or does not give true
     */
    public static byte[] screenshot(Path firefox, String url, String script, long settleMs) throws Exception {
        return withPage(firefox, url, (bidi, context) -> {
            JsonObject done = evaluate(bidi, context, script);
            if (!"success".equals(done.get("type").getAsString()) || !done.getAsJsonObject("result").get("value").getAsBoolean())
                throw new IllegalStateException("page script did not succeed: " + done);
            Thread.sleep(settleMs);
            String data = bidi.call("browsingContext.captureScreenshot", json("context", context)).get("data").getAsString();
            return Base64.getDecoder().decode(data);
        });
    }

    /**
     * Opens {@code url} and runs the {@code scripts} one after another (JavaScript expressions; a promise is awaited,
     * at most 60 s each). Before each script after the first the page is loaded again, like a reload: it starts
     * fresh with what it stored in the browser.
     *
     * @return each script's value as text, in order
     * @throws IllegalStateException if a script throws or its promise rejects
     */
    public static List<String> evaluate(Path firefox, String url, String... scripts) throws Exception {
        return withPage(firefox, url, (bidi, context) -> {
            List<String> values = new ArrayList<>();
            for (int i = 0; i < scripts.length; i++) {
                if (i > 0) navigate(bidi, context, url);
                JsonObject done = evaluate(bidi, context, scripts[i]);
                if (!"success".equals(done.get("type").getAsString()))
                    throw new IllegalStateException("page script " + (i + 1) + " failed: " + done);
                JsonElement value = done.getAsJsonObject("result").get("value");
                values.add(value == null ? "" : value.isJsonPrimitive() ? value.getAsString() : value.toString());
            }
            return values;
        });
    }

    /** @return a port that was free a moment ago */
    public static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** What to do with the open page: {@code context} is its browsing context id. */
    private interface PageAction<T> {
        T run(Bidi bidi, String context) throws Exception;
    }

    /** Loads {@code url} in the page and waits until it has loaded. */
    private static void navigate(Bidi bidi, String context, String url) throws Exception {
        bidi.call("browsingContext.navigate", json("context", context, "url", url, "wait", "complete"));
    }

    private static JsonObject evaluate(Bidi bidi, String context, String script) throws Exception {
        return bidi.call("script.evaluate", json("expression", script, "target", json("context", context), "awaitPromise", true));
    }

    /** Opens a new tab and returns its browsing context id. */
    private static String createTab(Bidi bidi) throws Exception {
        // SM-400: right after session.new, Firefox may still be replacing its startup context and discard the call
        for (int attempt = 1; ; attempt++) {
            try {
                return bidi.call("browsingContext.create", json("type", "tab")).get("context").getAsString();
            } catch (IOException e) {
                String msg = String.valueOf(e.getMessage());
                if (attempt >= 3 || !(msg.contains("no such frame") || msg.contains("DiscardedBrowsingContext"))) throw e;
                Thread.sleep(500);
            }
        }
    }

    private static <T> T withPage(Path firefox, String url, PageAction<T> action) throws Exception {
        int port = freePort();
        Path profile = Files.createTempDirectory("sm-firefox");
        // US number and date format whatever the PC's region (the page formats with the browser's locale)
        Files.writeString(profile.resolve("user.js"), String.join("\n",
                "user_pref(\"intl.locale.requested\", \"en-US\");",
                "user_pref(\"intl.accept_languages\", \"en-US, en\");",
                "user_pref(\"intl.regional_prefs.use_os_locales\", false);",
                "user_pref(\"javascript.use_us_english_locale\", true);", ""));
        Process process = new ProcessBuilder(firefox.toString(), "--headless", "--no-remote", "--profile", profile.toString(),
                "--remote-debugging-port", String.valueOf(port))
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try (Bidi bidi = Bidi.connect(port)) {
            bidi.call("session.new", json("capabilities", new JsonObject()));
            String context = createTab(bidi);
            bidi.call("browsingContext.setViewport", json("context", context, "viewport", json("width", 1920, "height", 1080)));
            navigate(bidi, context, url);
            T result = action.run(bidi, context);
            bidi.call("session.end", new JsonObject());
            return result;
        } finally {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            try (Stream<Path> files = Files.walk(profile)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException ignored) {
                // a leftover temp profile is harmless
            }
        }
    }

    /** A JSON object from key/value pairs (String, Number, Boolean or JsonElement values). */
    private static JsonObject json(Object... pairs) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = (String) pairs[i];
            Object value = pairs[i + 1];
            if (value instanceof JsonElement e) o.add(key, e);
            else if (value instanceof Number n) o.addProperty(key, n);
            else if (value instanceof Boolean bool) o.addProperty(key, bool);
            else o.addProperty(key, String.valueOf(value));
        }
        return o;
    }

    /** A minimal WebDriver BiDi connection: numbered commands, each answered by the message with its id. */
    private static final class Bidi implements AutoCloseable, WebSocket.Listener {
        private final Map<Integer, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
        private final AtomicInteger ids = new AtomicInteger();
        private final StringBuilder message = new StringBuilder();
        private WebSocket socket;

        /** Connects to the browser started with {@code --remote-debugging-port port}, waiting up to 30 s for it. */
        static Bidi connect(int port) throws Exception {
            Bidi bidi = new Bidi();
            HttpClient http = HttpClient.newHttpClient();
            Exception last = null;
            for (int i = 0; i < 60 && bidi.socket == null; i++) {
                Thread.sleep(500);
                try {
                    bidi.socket = http.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + port + "/session"), bidi).get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    last = e; // the browser is not listening yet
                }
            }
            if (bidi.socket == null) throw new IOException("no WebDriver BiDi on port " + port, last);
            return bidi;
        }

        /** Sends a command and returns its result; throws on an error answer or after 60 s. */
        JsonObject call(String method, JsonObject params) throws Exception {
            int id = ids.incrementAndGet();
            CompletableFuture<JsonObject> answer = new CompletableFuture<>();
            pending.put(id, answer);
            socket.sendText(json("id", id, "method", method, "params", params).toString(), true).get(10, TimeUnit.SECONDS);
            JsonObject reply = answer.get(60, TimeUnit.SECONDS);
            if (reply.has("error")) throw new IOException(method + ": " + reply.get("error") + " " + reply.get("message"));
            return reply.getAsJsonObject("result");
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                JsonObject m = JsonParser.parseString(message.toString()).getAsJsonObject();
                message.setLength(0);
                if (m.has("id") && !m.get("id").isJsonNull()) {
                    CompletableFuture<JsonObject> answer = pending.remove(m.get("id").getAsInt());
                    if (answer != null) answer.complete(m);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            pending.values().forEach(f -> f.completeExceptionally(error));
        }

        @Override
        public void close() {
            if (socket != null) socket.abort();
        }
    }
}
