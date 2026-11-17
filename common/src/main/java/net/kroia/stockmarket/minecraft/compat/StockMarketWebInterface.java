package net.kroia.stockmarket.minecraft.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.multi_server.MultiServerManager;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.filter.EqualityFilter;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.networking.request.OrderbookVolumeRequest;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * SM-347: read-only web interface (charts, order book, open orders, recent trades) on the master / singleplayer.
 * <p>
 * A JDK {@link HttpServer} serves a fixed set of static files from {@code assets/stockmarket/web/} and a small JSON
 * API under {@code /api/}. Gated live by {@code Integrations.WEB_INTERFACE_ENABLED} / {@code WEB_INTERFACE_PORT}
 * ({@link #apply}); slaves never start it. Binds to all interfaces on a dedicated server and to loopback only in
 * singleplayer. No login and no writes: the API only returns public market data, never player UUIDs, account
 * numbers or order IDs.
 * <p>
 * Threading: market state belongs to the server thread. HTTP threads never touch it; every API call hands its work
 * to the server thread ({@code server.submit}) and waits at most {@link #SERVER_WAIT_SECONDS}. Responses are cached
 * per URI for {@link #CACHE_TTL_MS}, so many viewers polling the same URI cost one server-thread call per second.
 */
public final class StockMarketWebInterface {

    /** Wall-clock timeframes the page may request (ms): 1m, 5m, 15m, 1h, 4h, 1D. */
    static final long[] WALL_TIMEFRAMES = {60_000L, 300_000L, 900_000L, 3_600_000L, 14_400_000L, 86_400_000L};
    /** Game-time timeframes in in-game hours: 1h, 4h, 1D, 1W. */
    static final long[] GAME_TIMEFRAME_HOURS = {1, 4, 24, 168};

    private static final long CACHE_TTL_MS = 1000;
    private static final int CACHE_MAX_ENTRIES = 256;
    private static final long SERVER_WAIT_SECONDS = 10;
    private static final int MAX_CANDLES = 1000;
    private static final int DEFAULT_CANDLES = 500;
    private static final int MAX_TRADES = 100;
    private static final int DEFAULT_TRADES = 50;
    private static final int BOOK_CHUNKS = 50;
    private static final double BOOK_RANGE = 0.25; // depth levels over price +-25 %

    /** Whitelist: URL path -> {resource file below assets/stockmarket/web/, content type}. Nothing else is served. */
    private static final Map<String, String[]> STATIC_FILES = Map.of(
            "/", new String[]{"index.html", "text/html; charset=utf-8"},
            "/index.html", new String[]{"index.html", "text/html; charset=utf-8"},
            "/app.js", new String[]{"app.js", "text/javascript; charset=utf-8"},
            "/vendor/lightweight-charts.js", new String[]{"vendor/lightweight-charts.standalone.production.js", "text/javascript; charset=utf-8"},
            "/vendor/LICENSE-lightweight-charts.txt", new String[]{"vendor/LICENSE-lightweight-charts.txt", "text/plain; charset=utf-8"});
    private static final Map<String, byte[]> STATIC_BYTES = new ConcurrentHashMap<>();

    private record Cached(long at, byte[] body) {}
    // ponytail: per-URI cache with a 1 s TTL, cleared when full; switch to a per-tick snapshot if many viewers
    // request many different URIs.
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private static HttpServer httpServer;
    private static ExecutorService executor;
    private static volatile int runningPort = -1;
    /** The master's instances while running; read by the HTTP threads, used only on the server thread. */
    private static volatile StockMarketModBackend.ServerInstances backend;

    private StockMarketWebInterface() {}

    /** An API error with its HTTP status and a message for {@code {"error":..}}. */
    private static final class ApiError extends RuntimeException {
        final int status;
        ApiError(int status, String message) { super(message, null, false, false); this.status = status; }
    }

    /**
     * Starts, stops or moves the web interface to match the settings. On iff {@code b} is the master's (or
     * singleplayer's) instances and {@code WEB_INTERFACE_ENABLED} is on. Never throws; a failed start logs one
     * warning and leaves the interface off.
     *
     * @param b the server instances, or null (stops the interface)
     */
    public static synchronized void apply(StockMarketModBackend.ServerInstances b) {
        boolean want = b != null && b.MARKET_MANAGER != null && b.MARKET_MANAGER.isMaster() && b.SERVER_SETTINGS != null
                && Boolean.TRUE.equals(b.SERVER_SETTINGS.INTEGRATIONS.WEB_INTERFACE_ENABLED.get());
        if (!want) {
            stop();
            return;
        }
        Integer port = b.SERVER_SETTINGS.INTEGRATIONS.WEB_INTERFACE_PORT.get();
        int p = port == null ? b.SERVER_SETTINGS.INTEGRATIONS.WEB_INTERFACE_PORT.getDefaultValue() : port;
        backend = b;
        if (p == runningPort)
            return;
        start(p, loopbackOnly());
    }

    /** Stops the HTTP server and its threads (no-op when off). The dispatcher thread is not a daemon, so this must run on server stop. */
    public static synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            StockMarketMod.LOGGER.info("[StockMarket Web] stopped (port {})", runningPort);
        }
        if (executor != null)
            executor.shutdownNow();
        httpServer = null;
        executor = null;
        runningPort = -1;
        CACHE.clear();
    }

    /**
     * Starts the HTTP server on {@code port}, stopping a running one first. Never throws.
     *
     * @param port         TCP port
     * @param loopbackOnly true = bind 127.0.0.1 only (singleplayer), false = all interfaces
     * @return true if it is running now
     */
    public static synchronized boolean start(int port, boolean loopbackOnly) {
        stop();
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(loopbackOnly ? InetAddress.getLoopbackAddress() : null, port), 0);
            ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "StockMarket-Web");
                t.setDaemon(true);
                return t;
            });
            server.createContext("/api/", StockMarketWebInterface::handleApi);
            server.createContext("/", StockMarketWebInterface::handleStatic);
            server.setExecutor(pool);
            server.start();
            httpServer = server;
            executor = pool;
            runningPort = port;
            StockMarketMod.LOGGER.info("[StockMarket Web] serving on {}:{}", loopbackOnly ? "127.0.0.1" : "0.0.0.0", port);
            return true;
        } catch (IOException | LinkageError | RuntimeException e) {
            if (server != null)
                server.stop(0);
            StockMarketMod.LOGGER.warn("[StockMarket Web] could not bind port {}: {}; web interface stays off", port, e.toString());
            return false;
        }
    }

    /** @return the port the interface runs on, or -1 when off */
    public static int getRunningPort() {
        return runningPort;
    }

    /**
     * Pure range / conflict check of a web interface port.
     *
     * @param port     the wanted port
     * @param gamePort the Minecraft server port, -1 = none
     * @param linkPort the multi-server link port, -1 = none
     * @return null if allowed, else the reason
     */
    public static @Nullable String validatePort(int port, int gamePort, int linkPort) {
        if (port < 1024 || port > 65535)
            return "must be between 1024 and 65535";
        if (gamePort > 0 && port == gamePort)
            return "is the game port";
        if (linkPort > 0 && port == linkPort)
            return "is the multi-server link port";
        return null;
    }

    /**
     * Checks a port before it is saved: {@link #validatePort} against this server's game port and multi-server link
     * port, then a test bind on the address the interface would use. The port the interface already runs on passes
     * the bind test.
     *
     * @param port the wanted port
     * @return null if usable, else the reason
     */
    public static @Nullable String checkPort(int port) {
        MinecraftServer server = UtilitiesPlatform.getServer();
        int gamePort = server == null ? -1 : server.getPort();
        int linkPort;
        try {
            linkPort = MultiServerManager.getMasterPort();
        } catch (RuntimeException | LinkageError e) {
            linkPort = -1; // no multi-server setup
        }
        String reason = validatePort(port, gamePort, linkPort);
        if (reason != null || port == getRunningPort())
            return reason;
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(loopbackOnly() ? InetAddress.getLoopbackAddress() : null, port));
        } catch (IOException e) {
            return "already in use or not allowed (" + e.getMessage() + ")";
        }
        return null;
    }

    /** Loopback only in singleplayer (and offline, without a server), all interfaces on a dedicated server. */
    private static boolean loopbackOnly() {
        MinecraftServer server = UtilitiesPlatform.getServer();
        return server == null || !server.isDedicatedServer();
    }

    // ------------------------------------------------------------------ HTTP handlers

    private static void handleStatic(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                send(ex, 405, "text/plain; charset=utf-8", "Method not allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String[] file = STATIC_FILES.get(ex.getRequestURI().getPath());
            byte[] body = file == null ? null : staticBytes(file[0]);
            if (body == null) {
                send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            send(ex, 200, file[1], "HEAD".equals(method) ? null : body);
        } finally {
            ex.close();
        }
    }

    /** Loads a whitelisted static file once from the jar. */
    private static byte[] staticBytes(String file) {
        return STATIC_BYTES.computeIfAbsent(file, f -> {
            try (InputStream in = StockMarketWebInterface.class.getResourceAsStream("/assets/" + StockMarketMod.MOD_ID + "/web/" + f)) {
                return in == null ? null : in.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        });
    }

    private static void handleApi(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equals(ex.getRequestMethod()))
                throw new ApiError(405, "method not allowed");
            String path = ex.getRequestURI().getPath();
            String rawQuery = ex.getRequestURI().getRawQuery();
            String key = path + "?" + rawQuery;
            long now = System.currentTimeMillis();
            Cached cached = CACHE.get(key);
            byte[] body;
            if (cached != null && now - cached.at < CACHE_TTL_MS) {
                body = cached.body;
            } else {
                Map<String, String> q = parseQuery(rawQuery);
                JsonObject json = switch (path) {
                    case "/api/markets" -> onServer(StockMarketWebInterface::markets);
                    case "/api/history" -> history(q);
                    case "/api/book" -> {
                        short market = marketParam(q);
                        yield onServer(b -> book(b, market));
                    }
                    case "/api/trades" -> trades(q);
                    default -> throw new ApiError(404, "unknown endpoint");
                };
                body = json.toString().getBytes(StandardCharsets.UTF_8);
                if (CACHE.size() > CACHE_MAX_ENTRIES)
                    CACHE.clear();
                CACHE.put(key, new Cached(now, body));
            }
            send(ex, 200, "application/json; charset=utf-8", body);
        } catch (Exception e) {
            Throwable cause = e;
            while ((cause instanceof java.util.concurrent.ExecutionException || cause instanceof java.util.concurrent.CompletionException)
                    && cause.getCause() != null)
                cause = cause.getCause();
            int status;
            String message;
            if (cause instanceof ApiError api) {
                status = api.status;
                message = api.getMessage();
            } else if (cause instanceof TimeoutException || cause instanceof IllegalStateException) {
                status = 503;
                message = "server busy or stopping";
            } else {
                status = 500;
                message = "internal error";
                StockMarketMod.LOGGER.warn("[StockMarket Web] {} failed: {}", ex.getRequestURI().getPath(), cause.toString());
            }
            JsonObject err = new JsonObject();
            err.addProperty("error", message);
            send(ex, status, "application/json; charset=utf-8", err.toString().getBytes(StandardCharsets.UTF_8));
        } finally {
            ex.close();
        }
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (!ex.getResponseHeaders().containsKey("Cache-Control"))
            ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body == null ? -1 : body.length);
        if (body != null)
            ex.getResponseBody().write(body);
    }

    /**
     * Runs {@code work} on the server thread and waits for its (possibly asynchronous) result.
     *
     * @throws IllegalStateException when the interface or the server is gone (answered with 503)
     */
    private static <T> T onServer(Function<StockMarketModBackend.ServerInstances, ?> work) throws Exception {
        StockMarketModBackend.ServerInstances b = backend;
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (b == null || server == null || runningPort == -1)
            throw new IllegalStateException("not running");
        CompletableFuture<Object> result = server.submit(() -> work.apply(b))
                .thenCompose(r -> r instanceof CompletableFuture<?> f ? f.thenApply(v -> (Object) v) : CompletableFuture.completedFuture(r));
        @SuppressWarnings("unchecked")
        T value = (T) result.get(SERVER_WAIT_SECONDS, TimeUnit.SECONDS);
        return value;
    }

    // ------------------------------------------------------------------ endpoints

    /** Server thread: market list with prices and the live candle. */
    private static JsonObject markets(StockMarketModBackend.ServerInstances b) {
        ServerMarketManager smm = b.MARKET_MANAGER.getServerMarketManager();
        if (smm == null)
            throw new IllegalStateException("no market manager");
        ItemID currency = smm.getTradingCurrencyID();
        JsonObject o = new JsonObject();
        o.addProperty("serverTime", System.currentTimeMillis());
        boolean gameTime = smm.isCandleUseGameTime();
        o.addProperty("gameTime", gameTime);
        o.addProperty("candleMs", gameTime ? PriceHistoryData.GAME_HOUR_MS : b.SERVER_SETTINGS.MARKET.CANDLE_TIME.get());
        JsonObject cur = new JsonObject();
        cur.addProperty("id", currency.getName());
        cur.addProperty("name", itemName(currency));
        o.add("currency", cur);
        JsonArray tfs = new JsonArray();
        for (long tf : timeframes(gameTime))
            tfs.add(tf);
        o.add("timeframes", tfs);
        JsonArray list = new JsonArray();
        for (ItemID id : smm.getAvailableMarketIDs()) {
            ServerMarket market = smm.getServerMarket(id);
            if (market == null || id.equals(currency))
                continue;
            MarketPriceStruct candle = market.getCurrentMarketPriceStruct();
            JsonObject m = new JsonObject();
            m.addProperty("id", id.getShort());
            m.addProperty("itemId", id.getName());
            m.addProperty("name", itemName(id));
            m.addProperty("price", real(market.getCurrentMarketPrice()));
            m.addProperty("state", market.getTradingState().name());
            m.addProperty("candleStart", candle.time());
            m.addProperty("candleVolume", candle.tradedVolume());
            list.add(m);
        }
        o.add("markets", list);
        return o;
    }

    /** Candles of one market; close[i] = open[i+1], the newest close is the current price (as in the in-game chart). */
    private static JsonObject history(Map<String, String> q) throws Exception {
        short market = marketParam(q);
        long tf = longParam(q, "tf", -1);
        int max = (int) Math.max(1, Math.min(MAX_CANDLES, longParam(q, "max", DEFAULT_CANDLES)));
        PriceHistoryData data = onServer(b -> {
            ServerMarketManager smm = b.MARKET_MANAGER.getServerMarketManager();
            ItemID id = new ItemID(market);
            if (smm == null || smm.getServerMarket(id) == null)
                throw new ApiError(404, "unknown market");
            boolean gameTime = smm.isCandleUseGameTime();
            boolean offered = false;
            for (long t : timeframes(gameTime))
                offered |= t == tf;
            if (!offered)
                throw new ApiError(400, "tf must be one of the offered timeframes");
            return b.NETWORKING.MARKET_PRICE_HISTORY_REQUEST.handleOnMasterServer(
                    new MarketPriceHistoryRequest.InputData(id, tf, -1, Long.MAX_VALUE, max, gameTime), "", null);
        });
        JsonObject o = new JsonObject();
        o.addProperty("market", market);
        o.addProperty("tf", tf);
        o.addProperty("truncated", data.isTruncated());
        JsonArray arr = new JsonArray();
        List<PriceHistoryData.Candle> candles = data.getCandles();
        long lastT = Long.MIN_VALUE;
        for (int i = 0; i < candles.size(); i++) {
            PriceHistoryData.Candle c = candles.get(i);
            long t = c.openTimestamp / 1000;
            if (t <= lastT)
                continue; // the chart needs strictly ascending times
            lastT = t;
            long close = i + 1 < candles.size() ? candles.get(i + 1).open : data.getCurrentMarketPrice();
            JsonObject j = new JsonObject();
            j.addProperty("t", t);
            j.addProperty("o", data.toRealPrice(c.open));
            j.addProperty("h", data.toRealPrice(Math.max(c.high, Math.max(c.open, close))));
            j.addProperty("l", data.toRealPrice(Math.min(c.low, Math.min(c.open, close))));
            j.addProperty("c", data.toRealPrice(close));
            j.addProperty("v", c.tradedVolume);
            arr.add(j);
        }
        o.add("candles", arr);
        return o;
    }

    /** Server thread: depth levels (incl. virtual volume) over price +-25 % and resting player limit orders by price. */
    private static JsonObject book(StockMarketModBackend.ServerInstances b, short marketShort) {
        ServerMarketManager smm = b.MARKET_MANAGER.getServerMarketManager();
        ItemID id = new ItemID(marketShort);
        ServerMarket market = smm == null ? null : smm.getServerMarket(id);
        if (market == null)
            throw new ApiError(404, "unknown market");
        double price = real(market.getCurrentMarketPrice());
        OrderbookVolumeRequest.OutputData depth = b.NETWORKING.ORDERBOOK_VOLUME_REQUEST.handleOnMasterServer(
                new OrderbookVolumeRequest.InputData(id, price * (1 - BOOK_RANGE), price * (1 + BOOK_RANGE), BOOK_CHUNKS), "", null).join();
        JsonObject o = new JsonObject();
        o.addProperty("market", marketShort);
        o.addProperty("price", price);
        o.addProperty("from", depth.startPrice());
        o.addProperty("to", depth.endPrice());
        JsonArray levels = new JsonArray();
        int n = depth.volumes().length;
        double step = n == 0 ? 0 : (depth.endPrice() - depth.startPrice()) / n;
        for (int i = 0; i < n; i++) {
            JsonObject l = new JsonObject();
            l.addProperty("p", depth.startPrice() + (i + 0.5) * step);
            l.addProperty("v", depth.volumes()[i]); // positive = buy, negative = sell
            levels.add(l);
        }
        o.add("levels", levels);
        // limit price -> {buy volume, sell volume, order count}; no owners, accounts or order IDs
        TreeMap<Long, long[]> byPrice = new TreeMap<>(Comparator.reverseOrder());
        for (Order order : market.getLimitOrders()) {
            if (order.isBotOrder())
                continue;
            long[] agg = byPrice.computeIfAbsent(order.getStartPrice(), k -> new long[3]);
            agg[order.isBuyOrder() ? 0 : 1] += Math.abs(order.getRemainingVolume());
            agg[2]++;
        }
        JsonArray orders = new JsonArray();
        byPrice.forEach((p, agg) -> {
            JsonObject r = new JsonObject();
            r.addProperty("p", real(p));
            r.addProperty("buy", real(agg[0]));
            r.addProperty("sell", real(agg[1]));
            r.addProperty("count", agg[2]);
            orders.add(r);
        });
        o.add("orders", orders);
        return o;
    }

    /** Finished player orders of the last 24 h, newest first, without user, account, order or group IDs. */
    private static JsonObject trades(Map<String, String> q) throws Exception {
        short market = marketParam(q);
        int limit = (int) Math.max(1, Math.min(MAX_TRADES, longParam(q, "limit", DEFAULT_TRADES)));
        List<OrderRecordStruct> records = onServer(b -> {
            ServerMarketManager smm = b.MARKET_MANAGER.getServerMarketManager();
            if (smm == null || smm.getServerMarket(new ItemID(market)) == null)
                throw new ApiError(404, "unknown market");
            long now = System.currentTimeMillis();
            return b.ORDER_RECORD_MANAGER.getHistory(Optional.of(new DateFilter(now - TimeUnit.DAYS.toMillis(1), now)),
                    Optional.empty(), Optional.empty(), Optional.of(new EqualityFilter(market)));
        });
        List<OrderRecordStruct> sorted = new ArrayList<>();
        for (OrderRecordStruct r : records)
            if (r.amount() != 0)
                sorted.add(r);
        sorted.sort(Comparator.comparingLong(StockMarketWebInterface::finishTime).reversed());
        Order.Type[] types = Order.Type.values();
        JsonArray arr = new JsonArray();
        for (OrderRecordStruct r : sorted.subList(0, Math.min(limit, sorted.size()))) {
            JsonObject t = new JsonObject();
            t.addProperty("t", finishTime(r));
            t.addProperty("side", r.amount() > 0 ? "buy" : "sell");
            t.addProperty("type", r.type() >= 0 && r.type() < types.length ? types[r.type()].name().toLowerCase(Locale.ROOT) : "unknown");
            t.addProperty("amount", real(Math.abs(r.amount())));
            t.addProperty("price", real(r.price()));
            arr.add(t);
        }
        JsonObject o = new JsonObject();
        o.addProperty("market", market);
        o.add("trades", arr);
        return o;
    }

    // ------------------------------------------------------------------ helpers

    private static long[] timeframes(boolean gameTime) {
        if (!gameTime)
            return WALL_TIMEFRAMES;
        long[] out = new long[GAME_TIMEFRAME_HOURS.length];
        for (int i = 0; i < out.length; i++)
            out[i] = GAME_TIMEFRAME_HOURS[i] * PriceHistoryData.GAME_HOUR_MS;
        return out;
    }

    private static long finishTime(OrderRecordStruct r) {
        return r.finishTime() != null ? r.finishTime() : r.time();
    }

    private static Map<String, String> parseQuery(@Nullable String raw) {
        Map<String, String> q = new HashMap<>();
        if (raw == null || raw.isEmpty())
            return q;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0)
                q.put(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return q;
    }

    private static short marketParam(Map<String, String> q) {
        try {
            return Short.parseShort(q.getOrDefault("market", ""));
        } catch (NumberFormatException e) {
            throw new ApiError(400, "market must be a market id");
        }
    }

    private static long longParam(Map<String, String> q, String name, long fallback) {
        String v = q.get(name);
        if (v == null)
            return fallback;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new ApiError(400, name + " must be a number");
        }
    }

    /** The item's display name, else its ID name (same rule as {@link StockMarketDiscord}). */
    private static String itemName(ItemID id) {
        ItemStack stack = id.getStackTemplate();
        return stack.isEmpty() ? id.getName() : stack.getHoverName().getString();
    }

    private static double real(long raw) {
        return MarketManager.convertToRealAmountStatic(raw);
    }
}
