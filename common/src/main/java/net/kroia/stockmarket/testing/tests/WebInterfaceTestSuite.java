package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.PermissionCompat;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.data.filter.EqualityFilter;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.minecraft.compat.StockMarketWebInterface;
import net.kroia.stockmarket.networking.request.ModSettingsRequest;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;
import net.kroia.stockmarket.util.StockMarketPermissions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * SM-347: the read-only web interface ({@link StockMarketWebInterface}).
 * <p>
 * Offline (no game): port validation and a clean failed start on an occupied port. Master: page + JSON served, no
 * player UUID in the trades, no path traversal, POST refused, the server stops again; a settings change moves the
 * port; the admin SET path rejects a reserved port. Slave: never starts. Every test restores the web settings and
 * re-applies them in {@code finally}.
 */
public class WebInterfaceTestSuite extends TestSuite {

    private static final long WAIT_MS = 10_000;

    private static StockMarketModBackend.ServerInstances backend;

    /** @param instances this server's instances (master or slave), set by DevBootstrap */
    public static void setBackend(StockMarketModBackend.ServerInstances instances) {
        backend = instances;
    }

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.WEB;
    }

    @Override
    public boolean needsGame() {
        return false; // the game tests skip without a backend
    }

    @Override
    public void registerTests() {
        addTest("port_validation_rejects_reserved", this::test_portValidation);
        addTest("occupied_port_rejected_start_fails_cleanly", this::test_occupiedPort);
        addTest("icon_path_validation", this::test_iconPathValidation);
        addTest("serves_page_and_json", this::test_servesPageAndJson);
        addTest("settings_change_moves_port", this::test_settingsChangeMovesPort);
        addTest("mod_settings_set_rejects_game_port", this::test_setRejectsReservedPort);
        addTest("slave_never_starts", this::test_slaveNeverStarts);
    }

    private TestResult test_portValidation() {
        int[][] rejected = {{0, -1, -1}, {80, -1, -1}, {70000, -1, -1}, {25565, 25565, 25575}, {25575, 25565, 25575}};
        for (int[] c : rejected) {
            TestResult r = assertNotNull("port " + c[0] + " rejected", StockMarketWebInterface.validatePort(c[0], c[1], c[2]));
            if (!r.passed()) return r;
        }
        TestResult r = assertNull("8090 accepted", StockMarketWebInterface.validatePort(8090, 25565, 25575));
        if (!r.passed()) return r;
        return assertNull("any port when there is no game/link port", StockMarketWebInterface.validatePort(25565, -1, -1));
    }

    /** Only plain {@code namespace:path} item names map to texture paths; anything that could leave the folder is refused. */
    private TestResult test_iconPathValidation() {
        for (String bad : new String[]{null, "", "../x", "minecraft:../x", "minecraft:a/b", "minecraft:.hidden",
                "Minecraft:Stone", "minecraft:stone%2f", "stone", "minecraft:stone:x"}) {
            TestResult r = assertNull("refused: " + bad, StockMarketWebInterface.iconResourcePaths(bad));
            if (!r.passed()) return r;
        }
        return assertEquals("item texture, then block texture",
                java.util.List.of("assets/minecraft/textures/item/stone.png", "assets/minecraft/textures/block/stone.png"),
                StockMarketWebInterface.iconResourcePaths("minecraft:stone"));
    }

    private TestResult test_occupiedPort() {
        if (StockMarketWebInterface.getRunningPort() != -1)
            return skip("the web interface is running on this server");
        try (ServerSocket held = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            int port = held.getLocalPort();
            TestResult r = assertNotNull("checkPort reports the occupied port", StockMarketWebInterface.checkPort(port));
            if (!r.passed()) return r;
            r = assertFalse("start fails", StockMarketWebInterface.start(port, true));
            if (!r.passed()) return r;
            return assertEquals("stays off", -1, StockMarketWebInterface.getRunningPort());
        } catch (Exception e) {
            return fail("unexpected exception: " + e);
        }
    }

    private TestResult test_servesPageAndJson() {
        if (!isMaster())
            return skip("master only");
        StockMarketModSettings.Integrations s = backend.SERVER_SETTINGS.INTEGRATIONS;
        boolean enabledBefore = Boolean.TRUE.equals(s.WEB_INTERFACE_ENABLED.get());
        int portBefore = s.WEB_INTERFACE_PORT.get();
        TestFixture fixture = new TestFixture("WebInterface");
        fixture.setBackend(backend);
        short marketShort = -1;
        int port = -1;
        try {
            port = freePort();
            ItemID marketID = fixture.createMarket("web").getItemID();
            marketShort = marketID.getShort();
            UUID player = UUID.randomUUID();
            backend.ORDER_RECORD_MANAGER.save(new OrderRecordStruct(marketShort, 1, player, 0, 100, 100, System.currentTimeMillis()));

            s.WEB_INTERFACE_ENABLED.set(true);
            s.WEB_INTERFACE_PORT.set(port);
            StockMarketWebInterface.apply(backend);
            TestResult r = assertEquals("running on the port", port, StockMarketWebInterface.getRunningPort());
            if (!r.passed()) return r;

            HttpResponse<String> page = get(port, "/");
            r = assertEquals("/ status", 200, page.statusCode());
            if (!r.passed()) return r;
            r = assertTrue("/ is html", page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
            if (!r.passed()) return r;
            r = assertEquals("chart library served", 200, get(port, "/vendor/lightweight-charts.js").statusCode());
            if (!r.passed()) return r;

            HttpResponse<String> markets = get(port, "/api/markets");
            r = assertTrue("markets lists the fixture market: " + markets.body(),
                    markets.statusCode() == 200 && markets.body().contains("\"id\":" + marketShort + ","));
            if (!r.passed()) return r;
            r = assertTrue("markets carry a category", markets.body().contains("\"category\":\"money\""));
            if (!r.passed()) return r;
            r = assertEquals("icon traversal refused", 400, get(port, "/api/icon?item=minecraft:..%2F..%2Fx").statusCode());
            if (!r.passed()) return r;
            r = assertEquals("BankSystem's own item icon is served", 200, get(port, "/api/icon?item=banksystem:stamped_share").statusCode());
            if (!r.passed()) return r;
            HttpResponse<String> history = get(port, "/api/history?market=" + marketShort + "&tf=" + firstTimeframe(markets.body()) + "&max=50");
            r = assertTrue("history has candles: " + history.body(), history.statusCode() == 200 && history.body().contains("\"candles\":["));
            if (!r.passed()) return r;
            HttpResponse<String> book = get(port, "/api/book?market=" + marketShort);
            r = assertTrue("book has levels and orders: " + book.body(),
                    book.statusCode() == 200 && book.body().contains("\"levels\":[") && book.body().contains("\"orders\":["));
            if (!r.passed()) return r;
            HttpResponse<String> trades = get(port, "/api/trades?market=" + marketShort);
            r = assertTrue("trades lists the saved order: " + trades.body(), trades.statusCode() == 200 && trades.body().contains("\"side\":\"buy\""));
            if (!r.passed()) return r;
            r = assertFalse("no player UUID in the trades", trades.body().contains(player.toString()));
            if (!r.passed()) return r;
            r = assertEquals("unknown market", 404, get(port, "/api/book?market=-2").statusCode());
            if (!r.passed()) return r;
            r = assertEquals("path traversal", 404, get(port, "/../build.gradle").statusCode());
            if (!r.passed()) return r;
            HttpResponse<String> post = await(HttpClient.newHttpClient().sendAsync(HttpRequest.newBuilder(uri(port, "/api/markets"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()), WAIT_MS);
            r = assertEquals("POST refused", 405, post.statusCode());
            if (!r.passed()) return r;

            s.WEB_INTERFACE_ENABLED.set(false);
            StockMarketWebInterface.apply(backend);
            return assertFalse("connection refused after disable", reachable(port));
        } catch (Exception e) {
            return fail("unexpected exception: " + e);
        } finally {
            s.WEB_INTERFACE_ENABLED.set(enabledBefore);
            s.WEB_INTERFACE_PORT.set(portBefore);
            StockMarketWebInterface.apply(backend);
            if (marketShort != -1)
                backend.ORDER_RECORD_MANAGER.removeHistory(Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.of(new EqualityFilter(marketShort)));
            fixture.cleanup();
        }
    }

    private TestResult test_settingsChangeMovesPort() {
        if (!isMaster())
            return skip("master only");
        StockMarketModSettings.Integrations s = backend.SERVER_SETTINGS.INTEGRATIONS;
        boolean enabledBefore = Boolean.TRUE.equals(s.WEB_INTERFACE_ENABLED.get());
        int portBefore = s.WEB_INTERFACE_PORT.get();
        try {
            int a = freePort(), b = freePort();
            s.WEB_INTERFACE_ENABLED.set(true);
            s.WEB_INTERFACE_PORT.set(a);
            StockMarketWebInterface.apply(backend);
            TestResult r = assertTrue("reachable on the first port", reachable(a));
            if (!r.passed()) return r;
            s.WEB_INTERFACE_PORT.set(b);
            StockMarketWebInterface.apply(backend);
            r = assertEquals("moved", b, StockMarketWebInterface.getRunningPort());
            if (!r.passed()) return r;
            r = assertTrue("reachable on the new port", reachable(b));
            if (!r.passed()) return r;
            return assertFalse("old port closed", reachable(a));
        } catch (Exception e) {
            return fail("unexpected exception: " + e);
        } finally {
            s.WEB_INTERFACE_ENABLED.set(enabledBefore);
            s.WEB_INTERFACE_PORT.set(portBefore);
            StockMarketWebInterface.apply(backend);
        }
    }

    /** The admin SET path refuses the game port (or, without one as in singleplayer, port 80) and keeps the old values. */
    private TestResult test_setRejectsReservedPort() {
        if (!isMaster())
            return skip("master only");
        MinecraftServer server = getServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty())
            return skip("no player online");
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        if (!PermissionCompat.hasPermission(player, StockMarketPermissions.FALLBACK_LEVEL))
            return skip("the player has no operator level 2 (cheats off)");
        UUID uuid = player.getUUID();
        boolean wasAdmin = backend.MARKET_MANAGER.getSync().isStockmarketAdmin(uuid);
        StockMarketModSettings.Integrations s = backend.SERVER_SETTINGS.INTEGRATIONS;
        boolean enabledBefore = Boolean.TRUE.equals(s.WEB_INTERFACE_ENABLED.get());
        int portBefore = s.WEB_INTERFACE_PORT.get();
        int runningBefore = StockMarketWebInterface.getRunningPort();
        int bad = server.getPort() > 0 ? server.getPort() : 80;
        try {
            if (!wasAdmin)
                backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(uuid, true);
            String json = "{\"Integrations\":{\"WEB_INTERFACE_ENABLED\":true,\"WEB_INTERFACE_PORT\":" + bad + "}}";
            ModSettingsRequest.OutputData out = backend.NETWORKING.MOD_SETTINGS_REQUEST.handleOnMasterServer(
                    new ModSettingsRequest.InputData(ModSettingsRequest.Action.SET, json), "", uuid).join();
            TestResult r = assertTrue("SET reports the rejection: " + out.message(), out.message().contains("rejected"));
            if (!r.passed()) return r;
            r = assertEquals("enabled restored", enabledBefore, Boolean.TRUE.equals(s.WEB_INTERFACE_ENABLED.get()));
            if (!r.passed()) return r;
            r = assertEquals("port restored", portBefore, (int) s.WEB_INTERFACE_PORT.get());
            if (!r.passed()) return r;
            return assertEquals("running state unchanged", runningBefore, StockMarketWebInterface.getRunningPort());
        } finally {
            s.WEB_INTERFACE_ENABLED.set(enabledBefore);
            s.WEB_INTERFACE_PORT.set(portBefore);
            StockMarketWebInterface.apply(backend);
            if (!wasAdmin)
                backend.MARKET_MANAGER.getSync().setStockmarketAdminMode(uuid, false);
        }
    }

    private TestResult test_slaveNeverStarts() {
        if (backend == null || backend.MARKET_MANAGER == null || backend.MARKET_MANAGER.isMaster())
            return skip("slave only");
        StockMarketModSettings.Integrations s = backend.SERVER_SETTINGS.INTEGRATIONS;
        boolean enabledBefore = Boolean.TRUE.equals(s.WEB_INTERFACE_ENABLED.get());
        try {
            s.WEB_INTERFACE_ENABLED.set(true);
            StockMarketWebInterface.apply(backend);
            return assertEquals("not running on a slave", -1, StockMarketWebInterface.getRunningPort());
        } finally {
            s.WEB_INTERFACE_ENABLED.set(enabledBefore);
            StockMarketWebInterface.apply(backend);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isMaster() {
        return backend != null && backend.MARKET_MANAGER != null && backend.MARKET_MANAGER.isMaster();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static URI uri(int port, String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    /** GET through the HTTP client; waits with {@link #await}, so the server thread keeps running the API's tasks. */
    private HttpResponse<String> get(int port, String path) {
        return await(HttpClient.newHttpClient().sendAsync(HttpRequest.newBuilder(uri(port, path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()), WAIT_MS);
    }

    private boolean reachable(int port) {
        try {
            get(port, "/");
            return true;
        } catch (RuntimeException e) {
            return false; // connection refused
        }
    }

    /** The first offered timeframe from a /api/markets body. */
    private static long firstTimeframe(String marketsBody) {
        int i = marketsBody.indexOf("\"timeframes\":[") + "\"timeframes\":[".length();
        int end = marketsBody.indexOf(',', i);
        return Long.parseLong(marketsBody.substring(i, end).replace("]", "").trim());
    }
}
