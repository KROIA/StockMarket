package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.PermissionCompat;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.data.filter.EqualityFilter;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.minecraft.compat.StockMarketWebInterface;
import net.kroia.stockmarket.minecraft.compat.WebIconTextures;
import net.kroia.stockmarket.minecraft.item.StockMarketItems;
import net.kroia.stockmarket.networking.request.ModSettingsRequest;
import net.kroia.stockmarket.testing.HeadlessFirefox;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;
import net.kroia.stockmarket.util.StockMarketPermissions;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * SM-347: the read-only web interface ({@link StockMarketWebInterface}).
 * <p>
 * Offline (no game): port validation and a clean failed start on an occupied port. Master: page + JSON served, no
 * player UUID in the trades, no path traversal, POST refused, the server stops again; a settings change moves the
 * port; the admin SET path rejects a reserved port. Slave: never starts. Every test restores the web settings and
 * re-applies them in {@code finally}.
 * <p>
 * SM-363 (no internet needed): texture lookup through item models in a generated pack, the download's metadata,
 * size and SHA-1 checks (against a local HTTP server), icons over HTTP, and market names with variant details.
 * SM-405: the market list's sort keys follow the creative inventory order.
 * <p>
 * In headless Firefox (skipped without Firefox): SM-363, the market list's icons and variant names as shown;
 * SM-383, the chart's per-market view, also after a page reload.
 */
public class WebInterfaceTestSuite extends TestSuite {

    private static final long WAIT_MS = 10_000;

    private static StockMarketModBackend.ServerInstances backend;

    /** @param instances this server's instances (master or slave), set by DevBootstrap */
    public static void setBackend(StockMarketModBackend.ServerInstances instances) {
        backend = instances;
    }

    /** "Download item textures" before the suite, restored by {@link #teardown()}; null = not changed (no backend). */
    private @Nullable Boolean downloadBefore;

    /**
     * SM-404: the download is on by default, so the tests that turn the web interface on would fetch the 27 MB client
     * from Mojang on a dedicated master. It stays off for the whole suite; the download tests use a local HTTP server.
     */
    @Override
    public void setup() {
        if (backend == null || backend.SERVER_SETTINGS == null)
            return;
        downloadBefore = backend.SERVER_SETTINGS.INTEGRATIONS.WEB_INTERFACE_DOWNLOAD_TEXTURES.get();
        backend.SERVER_SETTINGS.INTEGRATIONS.WEB_INTERFACE_DOWNLOAD_TEXTURES.set(false);
    }

    @Override
    public void teardown() {
        if (downloadBefore != null && backend != null && backend.SERVER_SETTINGS != null)
            backend.SERVER_SETTINGS.INTEGRATIONS.WEB_INTERFACE_DOWNLOAD_TEXTURES.set(downloadBefore);
        downloadBefore = null;
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
        addTest("texture_path_validation", this::test_texturePathValidation);
        addTest("drop_in_pack_icons", this::test_dropInPackIcons);
        addTest("download_metadata_checks", this::test_downloadMetadataChecks);
        addTest("download_checks_size_and_sha1", this::test_downloadChecksSizeAndSha1);
        addTest("market_names_show_variants", this::test_marketNamesShowVariants);
        addTest("markets_sorted_like_creative_tabs", this::test_marketsSortedLikeCreativeTabs);
        addTest("icons_served_over_http", this::test_iconsServedOverHttp);
        addTest("chart_view_per_market_in_browser", this::test_chartViewPerMarket);
        addTest("icons_and_names_in_browser", this::test_iconsAndNamesInBrowser);
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

    // ------------------------------------------------------------------ SM-363: names

    /**
     * SM-363: an enchanted book shows its enchantment, the normal and the long Swiftness potion differ (their
     * durations), and markets with the same item name (two scratch paper markets) get their market number.
     */
    private TestResult test_marketNamesShowVariants() {
        if (!isMaster())
            return skip("master only");
        MinecraftServer server = getServer();
        if (server == null)
            return skip("no server");
        StockMarketModSettings.Integrations s = backend.SERVER_SETTINGS.INTEGRATIONS;
        boolean enabledBefore = Boolean.TRUE.equals(s.WEB_INTERFACE_ENABLED.get());
        int portBefore = s.WEB_INTERFACE_PORT.get();
        TestFixture fixture = new TestFixture("WebNames");
        fixture.setBackend(backend);
        try {
            short bookId = fixture.createMarket("book", sharpnessBook(server)).getItemID().getShort();
            short swift = fixture.createMarket("swift", PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS)).getItemID().getShort();
            short longSwift = fixture.createMarket("long_swift", PotionContents.createItemStack(Items.POTION, Potions.LONG_SWIFTNESS)).getItemID().getShort();
            short paperA = fixture.createMarket("paper_a").getItemID().getShort();
            short paperB = fixture.createMarket("paper_b").getItemID().getShort();

            int port = freePort();
            s.WEB_INTERFACE_ENABLED.set(true);
            s.WEB_INTERFACE_PORT.set(port);
            StockMarketWebInterface.apply(backend);
            HttpResponse<String> response = get(port, "/api/markets");
            TestResult r = assertEquals("markets status", 200, response.statusCode());
            if (!r.passed()) return r;
            Map<Integer, String> names = new HashMap<>();
            for (JsonElement m : JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("markets"))
                names.put(m.getAsJsonObject().get("id").getAsInt(), m.getAsJsonObject().get("name").getAsString());
            String sharpnessV = sharpnessName(server);
            r = assertTrue("book name shows " + sharpnessV + ": " + names.get((int) bookId), String.valueOf(names.get((int) bookId)).contains(sharpnessV));
            if (!r.passed()) return r;
            r = assertTrue("swiftness and long swiftness differ: " + names.get((int) swift) + " / " + names.get((int) longSwift),
                    names.get((int) swift) != null && !names.get((int) swift).equals(names.get((int) longSwift)));
            if (!r.passed()) return r;
            r = assertTrue("scratch paper gets its market number: " + names.get((int) paperA), String.valueOf(names.get((int) paperA)).endsWith(" #" + paperA));
            if (!r.passed()) return r;
            r = assertTrue("second scratch paper too: " + names.get((int) paperB), String.valueOf(names.get((int) paperB)).endsWith(" #" + paperB));
            if (!r.passed()) return r;
            return assertEquals("all market names differ", names.size(), new HashSet<>(names.values()).size());
        } catch (Exception e) {
            return fail("unexpected exception: " + e);
        } finally {
            s.WEB_INTERFACE_ENABLED.set(enabledBefore);
            s.WEB_INTERFACE_PORT.set(portBefore);
            StockMarketWebInterface.apply(backend);
            fixture.cleanup();
        }
    }

    /** @return an enchanted book holding Sharpness V */
    private static ItemStack sharpnessBook(MinecraftServer server) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(sharpness(server), 5);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        return book;
    }

    /** @return "Sharpness V" in the server's language, what the book's market name has to show */
    private static String sharpnessName(MinecraftServer server) {
        return Enchantment.getFullname(sharpness(server), 5).getString();
    }

    private static Holder<Enchantment> sharpness(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }

    // ------------------------------------------------------------------ SM-405: sort order

    /**
     * SM-405: {@code /api/markets} gives each market its creative inventory position ({@code sort}). In 1.21.1 the
     * building blocks tab lists oak planks directly followed by oak stairs, then (after the other oak blocks) spruce
     * planks; bread is in the food tab and diamond in the ingredients tab after it. So the keys rise in that order,
     * and no other market's key lies between oak planks and oak stairs.
     */
    private TestResult test_marketsSortedLikeCreativeTabs() {
        if (!isMaster())
            return skip("master only");
        StockMarketModSettings.Integrations s = backend.SERVER_SETTINGS.INTEGRATIONS;
        boolean enabledBefore = Boolean.TRUE.equals(s.WEB_INTERFACE_ENABLED.get());
        int portBefore = s.WEB_INTERFACE_PORT.get();
        TestFixture fixture = new TestFixture("WebSort");
        fixture.setBackend(backend);
        try {
            Item[] items = {Items.OAK_PLANKS, Items.OAK_STAIRS, Items.SPRUCE_PLANKS, Items.BREAD, Items.DIAMOND};
            int[] ids = new int[items.length];
            for (int i = 0; i < items.length; i++)
                ids[i] = fixture.createMarket("sort_" + i, new ItemStack(items[i])).getItemID().getShort();

            int port = freePort();
            s.WEB_INTERFACE_ENABLED.set(true);
            s.WEB_INTERFACE_PORT.set(port);
            StockMarketWebInterface.apply(backend);
            HttpResponse<String> response = get(port, "/api/markets");
            TestResult r = assertEquals("markets status", 200, response.statusCode());
            if (!r.passed()) return r;
            Map<Integer, Integer> sort = new HashMap<>();
            for (JsonElement e : JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("markets")) {
                JsonObject m = e.getAsJsonObject();
                if (m.has("sort"))
                    sort.put(m.get("id").getAsInt(), m.get("sort").getAsInt());
            }
            for (int i = 0; i < items.length; i++) {
                r = assertTrue(items[i] + " has a sort key: " + response.body(), sort.containsKey(ids[i]));
                if (!r.passed()) return r;
            }
            for (int i = 1; i < items.length; i++) {
                int before = sort.get(ids[i - 1]), after = sort.get(ids[i]);
                r = assertTrue(items[i - 1] + " (" + before + ") before " + items[i] + " (" + after + ")", before < after);
                if (!r.passed()) return r;
            }
            int planks = sort.get(ids[0]), stairs = sort.get(ids[1]);
            return assertTrue("no other item between oak planks and oak stairs: " + sort.values(),
                    sort.values().stream().noneMatch(k -> k > planks && k < stairs));
        } catch (Exception e) {
            return fail("unexpected exception: " + e);
        } finally {
            s.WEB_INTERFACE_ENABLED.set(enabledBefore);
            s.WEB_INTERFACE_PORT.set(portBefore);
            StockMarketWebInterface.apply(backend);
            fixture.cleanup();
        }
    }

    /**
     * Checks the market list in the browser. {@code %s}: a JSON array of {id, has (text the row's name must
     * contain, optional), icon (true: the row must show the item's picture, false: the letter badge)}. Resolves to
     * "ok: ..." or the reason it failed.
     */
    private static final String ICONS_SCRIPT = """
            (async () => {
              const want = %s;
              const sleep = ms => new Promise(r => setTimeout(r, ms));
              const until = async (cond, ms) => { const end = Date.now() + ms; while (Date.now() < end && !cond()) await sleep(100); return cond(); };
              const markets = (await (await fetch('api/markets')).json()).markets;
              const label = id => { const m = markets.find(x => x.id === id); return m ? (m.name || m.itemId) : null; };
              const row = id => [...document.querySelectorAll('#markets li')].find(li => li.querySelector('.name').textContent === label(id));
              if (!await until(() => want.every(w => row(w.id)), 15000))
                return 'not in the market list: ' + want.filter(w => !row(w.id)).map(w => `${w.id} (${label(w.id)})`).join(', ');
              // what the row's icon box shows: the item's picture once loaded, else the letter badge
              const shows = id => { const box = row(id).querySelector('.icon'), img = box.querySelector('img');
                return img ? (img.complete && img.naturalWidth > 0 ? 'icon' : 'loading') : box.textContent ? 'badge' : 'empty'; };
              const seen = [];
              for (const w of want) {
                const name = row(w.id).querySelector('.name').textContent, expected = w.icon ? 'icon' : 'badge';
                if (w.has && !name.includes(w.has)) return `market ${w.id} reads '${name}', without '${w.has}'`;
                if (!await until(() => shows(w.id) === expected, 10000)) return `'${name}' shows ${shows(w.id)}, expected ${expected}`;
                seen.push(`${name}: ${expected}`);
              }
              const names = want.map(w => row(w.id).querySelector('.name').textContent);
              if (new Set(names).size !== names.length) return 'two markets read the same: ' + names.join(' / ');
              return 'ok: ' + seen.join(', ');
            })()""";

    /**
     * SM-363 as the user sees it, in headless Firefox. In the market list, the Sharpness V book shows its
     * enchantment and the normal and long Swiftness potions read differently. Each row shows the item's picture
     * where the server has its texture: the mod's own items always, vanilla items in singleplayer; on a dedicated
     * server a vanilla item shows its picture only with a texture pack or the opt-in download, else the letter
     * badge. Skips without Firefox ({@link HeadlessFirefox#PROPERTY}) and on slaves (no web interface there).
     */
    private TestResult test_iconsAndNamesInBrowser() {
        if (!isMaster())
            return skip("master only");
        Path firefox = HeadlessFirefox.find();
        if (firefox == null)
            return skip("no Firefox (-D" + HeadlessFirefox.PROPERTY + ")");
        MinecraftServer server = getServer();
        if (server == null)
            return skip("no server");
        StockMarketModSettings.Integrations s = backend.SERVER_SETTINGS.INTEGRATIONS;
        boolean enabledBefore = Boolean.TRUE.equals(s.WEB_INTERFACE_ENABLED.get());
        int portBefore = s.WEB_INTERFACE_PORT.get();
        TestFixture fixture = new TestFixture("WebIconsBrowser");
        fixture.setBackend(backend);
        try {
            boolean singleplayer = !server.isDedicatedServer();
            JsonArray want = new JsonArray();
            expectRow(want, fixture.createMarket("book", sharpnessBook(server)), sharpnessName(server), singleplayer);
            expectRow(want, fixture.createMarket("swift", PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS)), null, singleplayer);
            expectRow(want, fixture.createMarket("long_swift", PotionContents.createItemStack(Items.POTION, Potions.LONG_SWIFTNESS)), null, singleplayer);
            expectRow(want, fixture.createMarket("newspaper", new ItemStack(StockMarketItems.NEWSPAPER.get())), null, true);
            int port = freePort();
            s.WEB_INTERFACE_ENABLED.set(true);
            s.WEB_INTERFACE_PORT.set(port);
            StockMarketWebInterface.apply(backend);
            TestResult r = assertEquals("running on the port", port, StockMarketWebInterface.getRunningPort());
            if (!r.passed()) return r;
            String result = runInBrowser(firefox, port, String.format(ICONS_SCRIPT, want)).get(0);
            return result.startsWith("ok") ? pass(result) : fail(result);
        } catch (Exception e) {
            return fail("unexpected exception: " + e);
        } finally {
            s.WEB_INTERFACE_ENABLED.set(enabledBefore);
            s.WEB_INTERFACE_PORT.set(portBefore);
            StockMarketWebInterface.apply(backend);
            fixture.cleanup();
        }
    }

    /**
     * Adds what the market list must show for {@code market} to {@code want} ({@link #ICONS_SCRIPT}).
     *
     * @param has  text its name must contain, or null
     * @param icon true if its row must show the item's picture; false: the picture if the server has the texture,
     *             else the letter badge
     */
    private static void expectRow(JsonArray want, IServerMarket market, @Nullable String has, boolean icon) {
        JsonObject row = new JsonObject();
        row.addProperty("id", market.getItemID().getShort());
        if (has != null) row.addProperty("has", has);
        row.addProperty("icon", icon || WebIconTextures.findIcon(market.getItemID().getName()) != null);
        want.add(row);
    }

    // ------------------------------------------------------------------ SM-363: icons

    /** Texture references from model files map to texture PNG paths only; anything that could leave the folder is refused. */
    private TestResult test_texturePathValidation() {
        for (String bad : new String[]{null, "", "../x", "minecraft:../x", "minecraft:a/../b", "minecraft:a//b", "minecraft:.x/y",
                "..:x", ".minecraft:x", "#layer0", "Minecraft:block/stone", "minecraft:/abs", "c:/x", "minecraft:a\\b", "minecraft:a/b/"}) {
            TestResult r = assertNull("refused: " + bad, WebIconTextures.textureResourcePath(bad));
            if (!r.passed()) return r;
        }
        TestResult r = assertEquals("namespaced", "assets/minecraft/textures/block/stone.png", WebIconTextures.textureResourcePath("minecraft:block/stone"));
        if (!r.passed()) return r;
        r = assertEquals("no namespace = minecraft", "assets/minecraft/textures/block/stone.png", WebIconTextures.textureResourcePath("block/stone"));
        if (!r.passed()) return r;
        return assertEquals("sub folders", "assets/banksystem/textures/item/money/money.png", WebIconTextures.textureResourcePath("banksystem:item/money/money"));
    }

    /**
     * A generated pack in a scratch folder: a plain item texture, a texture found through a model chain with a
     * #reference, and refused cases (a model pointing out of the folder, a model loop, a "PNG" that is not one).
     */
    private TestResult test_dropInPackIcons() {
        Path dir = null;
        try {
            dir = writeTestPack();
            WebIconTextures.usePacks(dir);
            TestResult r = assertTrue("plain item texture", Arrays.equals(PNG_A, WebIconTextures.findIcon("smtest:plain")));
            if (!r.passed()) return r;
            r = assertTrue("texture through the model and its parent", Arrays.equals(PNG_B, WebIconTextures.findIcon("smtest:modelled")));
            if (!r.passed()) return r;
            r = assertTrue("#reference resolved", Arrays.equals(PNG_A, WebIconTextures.findIcon("smtest:referenced")));
            if (!r.passed()) return r;
            r = assertNull("model pointing out of the folder refused", WebIconTextures.findIcon("smtest:escape"));
            if (!r.passed()) return r;
            r = assertNull("model loop ends", WebIconTextures.findIcon("smtest:loop"));
            if (!r.passed()) return r;
            r = assertNull("not a PNG", WebIconTextures.findIcon("smtest:fake"));
            if (!r.passed()) return r;
            return assertNull("bad item name", WebIconTextures.findIcon("smtest:../plain"));
        } catch (IOException e) {
            return fail("could not write the test pack: " + e);
        } finally {
            WebIconTextures.reopenPacks();
            deleteTree(dir);
        }
    }

    /**
     * Over HTTP: a pack texture and a mod texture found through its item model (banksystem:money ->
     * item/money/money.png) are served as PNG; the refused pack entries answer 404, a bad name 400. In singleplayer
     * also a vanilla block item through its model (oak stairs -> planks).
     */
    private TestResult test_iconsServedOverHttp() {
        if (!isMaster())
            return skip("master only");
        MinecraftServer server = getServer();
        StockMarketModSettings.Integrations s = backend.SERVER_SETTINGS.INTEGRATIONS;
        boolean enabledBefore = Boolean.TRUE.equals(s.WEB_INTERFACE_ENABLED.get());
        int portBefore = s.WEB_INTERFACE_PORT.get();
        Path dir = null;
        try {
            int port = freePort();
            s.WEB_INTERFACE_ENABLED.set(true);
            s.WEB_INTERFACE_PORT.set(port);
            StockMarketWebInterface.apply(backend);
            dir = writeTestPack();
            WebIconTextures.usePacks(dir); // after the start, which opened the config folder's packs
            HttpResponse<byte[]> icon = getBytes(port, "/api/icon?item=smtest:plain");
            TestResult r = assertTrue("pack texture served: " + icon.statusCode(), icon.statusCode() == 200 && Arrays.equals(PNG_A, icon.body()));
            if (!r.passed()) return r;
            r = assertEquals("served as PNG", "image/png", icon.headers().firstValue("Content-Type").orElse(""));
            if (!r.passed()) return r;
            r = assertEquals("BankSystem money through its item model", 200, getBytes(port, "/api/icon?item=banksystem:money").statusCode());
            if (!r.passed()) return r;
            r = assertEquals("model escaping the folder", 404, getBytes(port, "/api/icon?item=smtest:escape").statusCode());
            if (!r.passed()) return r;
            r = assertEquals("not a PNG", 404, getBytes(port, "/api/icon?item=smtest:fake").statusCode());
            if (!r.passed()) return r;
            r = assertEquals("traversal in the item name", 400, getBytes(port, "/api/icon?item=smtest:..%2F..%2Fplain").statusCode());
            if (!r.passed()) return r;
            if (server != null && !server.isDedicatedServer()) {
                r = assertEquals("singleplayer: oak stairs through the block model", 200, getBytes(port, "/api/icon?item=minecraft:oak_stairs").statusCode());
                if (!r.passed()) return r;
            }
            return pass("icons served");
        } catch (Exception e) {
            return fail("unexpected exception: " + e);
        } finally {
            s.WEB_INTERFACE_ENABLED.set(enabledBefore);
            s.WEB_INTERFACE_PORT.set(portBefore);
            StockMarketWebInterface.apply(backend);
            WebIconTextures.reopenPacks();
            deleteTree(dir);
        }
    }

    // ------------------------------------------------------------------ SM-383: chart view in a real browser

    /**
     * Drives the page in headless Firefox: what a user does by clicking the market list, the chart object read
     * through the page's {@code window.smChart} handle. The script resolves to "ok: ..." or the reason it failed.
     * {@code %1$d} / {@code %2$d}: market ids A (low prices) and B (100x higher).
     */
    private static final String CHART_VIEW_SCRIPT = """
            (async () => {
              const A = %1$d, B = %2$d, chart = window.smChart;
              if (!chart) return 'no window.smChart on the page';
              const sleep = ms => new Promise(r => setTimeout(r, ms));
              const until = async (cond, ms) => { const end = Date.now() + ms; while (Date.now() < end && !cond()) await sleep(100); return cond(); };
              const markets = await (await fetch('api/markets')).json();
              const tf = markets.timeframes[Math.min(1, markets.timeframes.length - 1)]; // the page's default timeframe
              const range = async id => {
                const d = await (await fetch(`api/history?market=${id}&tf=${tf}&max=500`)).json();
                if (d.candles.length < 20) throw new Error(`market ${id} has only ${d.candles.length} candles`);
                return { lo: Math.min(...d.candles.map(c => c.l)), hi: Math.max(...d.candles.map(c => c.h)) };
              };
              const ra = await range(A), rb = await range(B);
              if (ra.hi >= rb.lo) return `price ranges overlap: A ${ra.lo}..${ra.hi}, B ${rb.lo}..${rb.hi}`;
              const row = id => {
                const m = markets.markets.find(x => x.id === id);
                return m && [...document.querySelectorAll('#markets li')].find(li => li.querySelector('.name').textContent === (m.name || m.itemId));
              };
              const scale = () => chart.priceScale('right');
              const vis = () => scale().getVisibleRange();
              const bars = () => chart.timeScale().getVisibleLogicalRange();
              const fmt = r => r ? `${r.from}..${r.to}` : 'none';
              const near = (x, y, tol) => Math.abs(x - y) <= tol;
              // centred on a market: all its candles in view and not much more (autoScale adds ~45 %% margins)
              const centred = r => { const v = vis(); return !!v && v.from <= r.lo && v.to >= r.hi && v.to - v.from <= 3 * (r.hi - r.lo); };

              if (!await until(() => row(A) && row(B), 15000)) return 'market A or B not in the list';
              row(A).click();
              if (!await until(() => centred(ra), 10000)) return `A not in view: visible ${fmt(vis())}, A ${ra.lo}..${ra.hi}`;
              // what a drag leaves behind: the price axis on an odd range (autoScale off) and the time axis zoomed in
              const span = ra.hi - ra.lo, odd = { from: ra.lo - 0.37 * span, to: ra.hi + 1.91 * span };
              scale().setVisibleRange(odd);
              chart.timeScale().setVisibleLogicalRange({ from: 3.5, to: 13.5 });
              await sleep(300);
              const zoomPrice = vis(), zoomBars = bars();
              if (scale().options().autoScale || !near(zoomPrice.from, odd.from, span * 0.01) || !near(zoomPrice.to, odd.to, span * 0.01))
                return `zoom not applied: ${fmt(zoomPrice)}, wanted ${fmt(odd)}, autoScale ${scale().options().autoScale}`;

              row(B).click();
              if (!await until(() => centred(rb), 10000))
                return `B not recentred: visible ${fmt(vis())}, B ${rb.lo}..${rb.hi}, autoScale ${scale().options().autoScale}`;

              row(A).click();
              const back = () => { const p = vis(), t = bars(); return !!p && !!t && near(p.from, zoomPrice.from, span * 0.01)
                && near(p.to, zoomPrice.to, span * 0.01) && near(t.from, zoomBars.from, 0.5) && near(t.to, zoomBars.to, 0.5); };
              if (!await until(back, 10000))
                return `A's zoom not back: price ${fmt(vis())}, wanted ${fmt(zoomPrice)}; bars ${fmt(bars())}, wanted ${fmt(zoomBars)}`;
              sessionStorage.setItem('smtest.zoom', JSON.stringify({ price: zoomPrice, bars: zoomBars, span })); // for the reload step
              return `ok: B centred on ${rb.lo}..${rb.hi}; A back at price ${fmt(zoomPrice)}, bars ${fmt(zoomBars)}`;
            })()""";

    /**
     * Second step of the SM-383 browser check, on the page loaded again: a click on market A brings back the zoom the
     * first step left it with (the page keeps each market's view in the browser). {@code %1$d}: market A's id.
     */
    private static final String CHART_RELOAD_SCRIPT = """
            (async () => {
              const A = %1$d, chart = window.smChart;
              if (!chart) return 'no window.smChart on the page after the reload';
              const want = JSON.parse(sessionStorage.getItem('smtest.zoom') || 'null');
              if (!want) return 'the first step left no zoom to compare with';
              const sleep = ms => new Promise(r => setTimeout(r, ms));
              const until = async (cond, ms) => { const end = Date.now() + ms; while (Date.now() < end && !cond()) await sleep(100); return cond(); };
              const m = (await (await fetch('api/markets')).json()).markets.find(x => x.id === A);
              const row = () => m && [...document.querySelectorAll('#markets li')].find(li => li.querySelector('.name').textContent === (m.name || m.itemId));
              if (!await until(() => row(), 15000)) return 'market A not in the list after the reload';
              row().click();
              const vis = () => chart.priceScale('right').getVisibleRange(), bars = () => chart.timeScale().getVisibleLogicalRange();
              const fmt = r => r ? `${r.from}..${r.to}` : 'none';
              const near = (x, y, tol) => Math.abs(x - y) <= tol;
              const back = () => { const p = vis(), t = bars(); return !!p && !!t && near(p.from, want.price.from, want.span * 0.01)
                && near(p.to, want.price.to, want.span * 0.01) && near(t.from, want.bars.from, 0.5) && near(t.to, want.bars.to, 0.5); };
              if (!await until(back, 10000))
                return `A's zoom lost over the reload: price ${fmt(vis())}, wanted ${fmt(want.price)}; bars ${fmt(bars())}, wanted ${fmt(want.bars)}`;
              return `ok: A's zoom kept over the reload (price ${fmt(want.price)}, bars ${fmt(want.bars)})`;
            })()""";

    /**
     * SM-383 in a real browser: two markets with far-apart prices and four hours of history each. A zoomed market A,
     * then a click on B: the price axis is recentred on B. A click back on A: A's price and time range are back, also
     * after the page is loaded again (the page keeps the views in the browser). Skips without Firefox ({@link HeadlessFirefox#PROPERTY}) and on slaves (no web interface there).
     */
    private TestResult test_chartViewPerMarket() {
        if (!isMaster())
            return skip("master only");
        Path firefox = HeadlessFirefox.find();
        if (firefox == null)
            return skip("no Firefox (-D" + HeadlessFirefox.PROPERTY + ")");
        StockMarketModSettings.Integrations s = backend.SERVER_SETTINGS.INTEGRATIONS;
        boolean enabledBefore = Boolean.TRUE.equals(s.WEB_INTERFACE_ENABLED.get());
        int portBefore = s.WEB_INTERFACE_PORT.get();
        TestFixture fixture = new TestFixture("WebChartView");
        fixture.setBackend(backend);
        List<Short> ids = new ArrayList<>();
        try {
            long price = 10_000;
            for (String name : new String[]{"chart_a", "chart_b"}) {
                IServerMarket market = fixture.createMarket(name);
                short id = market.getItemID().getShort();
                ids.add(id);
                market.test_setCurrentMarketPrice(price); // the newest candle closes at the live price
                market.getCurrentMarketPriceStructAndReset(); // the live candle forgets the creation price (its low/high)
                await(backend.MARKET_PRICE_HISTORY_MANAGER.save(chartHistory(id, price)), WAIT_MS);
                price *= 100;
            }
            int port = freePort();
            s.WEB_INTERFACE_ENABLED.set(true);
            s.WEB_INTERFACE_PORT.set(port);
            StockMarketWebInterface.apply(backend);
            TestResult r = assertEquals("running on the port", port, StockMarketWebInterface.getRunningPort());
            if (!r.passed()) return r;
            List<String> results = runInBrowser(firefox, port, String.format(CHART_VIEW_SCRIPT, ids.get(0), ids.get(1)),
                    String.format(CHART_RELOAD_SCRIPT, ids.get(0)));
            for (String result : results)
                if (!result.startsWith("ok")) return fail(result);
            return pass(String.join("; ", results));
        } catch (Exception e) {
            return fail("unexpected exception: " + e);
        } finally {
            s.WEB_INTERFACE_ENABLED.set(enabledBefore);
            s.WEB_INTERFACE_PORT.set(portBefore);
            StockMarketWebInterface.apply(backend);
            for (short id : ids)
                backend.MARKET_PRICE_HISTORY_MANAGER.removeHistory(Optional.empty(), Optional.of(new EqualityFilter(id)));
            fixture.cleanup();
        }
    }

    /**
     * Runs {@code scripts} in headless Firefox on the web page at {@code port} ({@link HeadlessFirefox#evaluate}: the
     * page is loaded again before each script after the first) and returns their values. Off the server thread: the
     * page's API calls are answered while {@link #await} runs the server's tasks.
     */
    private List<String> runInBrowser(Path firefox, int port, String... scripts) {
        CompletableFuture<List<String>> run = CompletableFuture.supplyAsync(() -> {
            try {
                return HeadlessFirefox.evaluate(firefox, "http://127.0.0.1:" + port + "/", scripts);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
        return await(run, 150_000);
    }

    /** 240 one-minute candles up to the last full minute, a zigzag of +-5 % around {@code price} (raw), oldest first. */
    private static List<MarketPriceStruct> chartHistory(short id, long price) {
        long minute = 60_000L, end = (System.currentTimeMillis() / minute - 1) * minute;
        List<MarketPriceStruct> rows = new ArrayList<>();
        for (int i = 0; i < 240; i++) {
            long open = price + price * (i % 20 - 10) / 200;
            rows.add(new MarketPriceStruct(id, open, open - price / 100, open + price / 100, end - (239L - i) * minute, 1f));
        }
        return rows;
    }

    // ------------------------------------------------------------------ SM-363: texture download

    /** Only https URLs on Mojang hosts with a 40-digit SHA-1 and a sane size are accepted from the manifest files. */
    private TestResult test_downloadMetadataChecks() {
        String sha = "0123456789abcdef0123456789abcdef01234567";
        JsonObject manifest = JsonParser.parseString("{\"versions\":["
                + "{\"id\":\"1.21.1\",\"url\":\"https://piston-meta.mojang.com/v1/packages/" + sha + "/1.21.1.json\",\"sha1\":\"" + sha.toUpperCase() + "\"},"
                + "{\"id\":\"plain_http\",\"url\":\"http://piston-meta.mojang.com/x.json\",\"sha1\":\"" + sha + "\"},"
                + "{\"id\":\"other_host\",\"url\":\"https://mojang.com.example.org/x.json\",\"sha1\":\"" + sha + "\"},"
                + "{\"id\":\"lookalike\",\"url\":\"https://evilmojang.com/x.json\",\"sha1\":\"" + sha + "\"},"
                + "{\"id\":\"bad_sha\",\"url\":\"https://piston-meta.mojang.com/x.json\",\"sha1\":\"xyz\"}]}").getAsJsonObject();
        WebIconTextures.Download v = WebIconTextures.versionJson(manifest, "1.21.1");
        TestResult r = assertTrue("1.21.1 found: " + v, v != null && v.url().endsWith("/1.21.1.json") && v.sha1().equals(sha));
        if (!r.passed()) return r;
        for (String id : new String[]{"plain_http", "other_host", "lookalike", "bad_sha", "9.9.9"}) {
            r = assertNull("refused: " + id, WebIconTextures.versionJson(manifest, id));
            if (!r.passed()) return r;
        }
        String client = "{\"downloads\":{\"client\":{\"sha1\":\"" + sha + "\",\"size\":%d,\"url\":\"%s\"}}}";
        WebIconTextures.Download jar = WebIconTextures.clientJar(JsonParser.parseString(String.format(client, 26836906, "https://piston-data.mojang.com/v1/objects/" + sha + "/client.jar")).getAsJsonObject());
        r = assertTrue("client jar accepted: " + jar, jar != null && jar.size() == 26836906);
        if (!r.passed()) return r;
        r = assertNull("empty size refused", WebIconTextures.clientJar(JsonParser.parseString(String.format(client, 0, "https://piston-data.mojang.com/c.jar")).getAsJsonObject()));
        if (!r.passed()) return r;
        r = assertNull("oversized refused", WebIconTextures.clientJar(JsonParser.parseString(String.format(client, 1L << 40, "https://piston-data.mojang.com/c.jar")).getAsJsonObject()));
        if (!r.passed()) return r;
        return assertNull("non-Mojang host refused", WebIconTextures.clientJar(JsonParser.parseString(String.format(client, 100, "https://example.org/c.jar")).getAsJsonObject()));
    }

    /**
     * The jar download against a local HTTP server (no internet): the right size and SHA-1 pass and the file matches;
     * a wrong SHA-1, a smaller announced size and an HTTP error fail.
     */
    private TestResult test_downloadChecksSizeAndSha1() {
        byte[] body = new byte[100_000];
        new Random(5).nextBytes(body);
        HttpServer local = null;
        Path dir = null;
        try {
            local = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            local.createContext("/client.jar", ex -> {
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            local.start();
            String url = "http://127.0.0.1:" + local.getAddress().getPort();
            dir = Files.createTempDirectory("sm-web-download");
            Path file = dir.resolve("client.jar");
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(body));
            HttpClient http = HttpClient.newHttpClient();
            WebIconTextures.downloadTo(http, new WebIconTextures.Download(url + "/client.jar", sha, body.length), file);
            TestResult r = assertEquals("stored file has the SHA-1", sha, WebIconTextures.sha1(file));
            if (!r.passed()) return r;
            for (WebIconTextures.Download bad : List.of(
                    new WebIconTextures.Download(url + "/client.jar", "0".repeat(40), body.length),
                    new WebIconTextures.Download(url + "/client.jar", sha, body.length - 1),
                    new WebIconTextures.Download(url + "/missing.jar", sha, body.length))) {
                try {
                    WebIconTextures.downloadTo(http, bad, dir.resolve("bad.jar"));
                    return fail("accepted: " + bad);
                } catch (IOException expected) {
                    // refused
                }
            }
            return pass("size and SHA-1 checked");
        } catch (Exception e) {
            return fail("unexpected exception: " + e);
        } finally {
            if (local != null)
                local.stop(0);
            deleteTree(dir);
        }
    }

    // ------------------------------------------------------------------ SM-363 helpers

    /** Two different "PNGs": the PNG signature plus one byte, enough for the signature check. */
    private static final byte[] PNG_A = fakePng('A');
    private static final byte[] PNG_B = fakePng('B');

    private static byte[] fakePng(char c) {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', (byte) c};
    }

    /** Writes a scratch folder with one pack zip (namespace smtest) for the icon tests. */
    private static Path writeTestPack() throws IOException {
        Path dir = Files.createTempDirectory("sm-web-pack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(dir.resolve("test-pack.zip")))) {
            entry(zip, "assets/smtest/textures/item/plain.png", PNG_A);
            entry(zip, "assets/smtest/textures/item/sub/deep.png", PNG_B);
            entry(zip, "assets/smtest/models/item/modelled.json", "{\"parent\":\"smtest:item/base\"}");
            entry(zip, "assets/smtest/models/item/base.json", "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"smtest:item/sub/deep\"}}");
            entry(zip, "assets/smtest/models/item/referenced.json", "{\"textures\":{\"layer0\":\"#side\",\"side\":\"smtest:item/plain\"}}");
            entry(zip, "assets/smtest/models/item/escape.json", "{\"textures\":{\"layer0\":\"smtest:../../../secret\"}}");
            entry(zip, "secret.png", PNG_A);
            entry(zip, "assets/smtest/models/item/loop.json", "{\"parent\":\"smtest:item/loop\"}");
            entry(zip, "assets/smtest/textures/item/fake.png", "not a png");
        }
        return dir;
    }

    private static void entry(ZipOutputStream zip, String name, String text) throws IOException {
        entry(zip, name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static void deleteTree(@Nullable Path dir) {
        if (dir == null)
            return;
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // a temp folder
        }
    }

    private HttpResponse<byte[]> getBytes(int port, String path) {
        return await(HttpClient.newHttpClient().sendAsync(HttpRequest.newBuilder(uri(port, path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()), WAIT_MS);
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
