package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.BankSystemModBackend;
import net.kroia.banksystem.data.table.record.BalanceHistoryRecord;
import net.kroia.banksystem.minecraft.entity.custom.BankSystemDisplayBlockEntity;
import net.kroia.banksystem.minecraft.entity.custom.MoneyStockpileBlockEntity;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.client.ClientCompat;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.minecraft.compat.StockMarketWebInterface;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.stockmarket.screen.custom.DisplayChartScreen;
import net.kroia.stockmarket.screen.widgets.DisplayCandlestickChart;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.gui.Gui;
import net.kroia.testkit.gui.Screenshots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.awt.Rectangle;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * SM-361: the in-world and outside-the-game pictures of the documentation site (Docs/planning/images.md section 1,
 * "StockMarket and shared"): the home corner, the terminal family, the Stock Market Displays, a news popup, a
 * villager's trade screen and the web interface. Runs on the market world of {@link DocsScreenshotSuite} (its
 * fixture is built here when this suite runs alone: {@code --only stockmarket/sm_docs_shots/DocsWorldShotsSuite}).
 * <p>
 * The scene is the user's home corner, {@code Docs/planning/structures/home-hero.nbt}, placed at its origin
 * (6, -60, -13) of the cell's superflat world: display blocks store their main block by absolute coordinates. The
 * file is taken from {@code -Dstockmarket.docs.structure} or found in a parent folder of the game dir (the mods
 * workspace). The camera spots start from the table of images.md and are tuned on the pictures. The web picture needs Firefox
 * ({@code -Dstockmarket.docs.firefox}, default its Windows install path); without it that shot is skipped.
 */
public class DocsWorldShotsSuite extends DocsScreenshotSuite {

    private static final String STRUCTURE = "Docs/planning/structures/home-hero.nbt";
    private static final String STRUCTURE_PROPERTY = "stockmarket.docs.structure";
    private static final String FIREFOX_PROPERTY = "stockmarket.docs.firefox";
    /** The news popup is shot with the HUD on (it is part of the HUD). */
    private static final Screenshots.Scene HUD = new Screenshots.Scene(SCENE.windowWidth(), SCENE.windowHeight(), SCENE.guiScale(), SCENE.dayTime(), false);
    /** News event started for the popup: copper, a market no other docs event touches. */
    private static final String TOAST_NEWS = "copper_seam_discovery";

    /**
     * The main blocks of the Stock Market Displays and their charts: the 3x2 one diamond, the three 2x1 ones top to
     * bottom. The 2x1 ones show markets no running news event moves: gold and iron (the builder's choice) end in the
     * fixture events' jump, which squeezes the rest of their candles into a flat line.
     */
    private static final Map<BlockPos, Item> CHARTS = Map.of(
            new BlockPos(22, -57, 5), Items.DIAMOND,
            new BlockPos(18, -56, 5), Items.LAPIS_LAZULI,
            new BlockPos(18, -57, 5), Items.REDSTONE,
            new BlockPos(18, -58, 5), Items.EMERALD);

    /** Whether the home corner is placed and filled; the corner shots skip without it. */
    private static @Nullable String cornerProblem = "home corner not placed yet";

    @Override
    public void registerTests() {
        addTest("fixture", this::test_fixture);
        addTest("home_corner", this::test_homeCorner);
        // camera spots of images.md, tuned on the pictures (2026-11-24). A narrow view from further back lets the corner
        // fill the picture: with FOV 70 the displays and the terminal row only fit with most of the frame sky and floor.
        addTest("home_hero", () -> withFov(40, () -> corner("shared/home-hero.png", SCENE, 27.0, -58.52, 1.0, 69.5f, -4, null)));
        // the Stock Market Displays only: the Bank Displays further west (x 11-16) stay out of the picture
        addTest("display_in_world", () -> withFov(30, () -> corner("stockmarket/display-in-world.png", SCENE, 23.99, -58.82, -2.37, 17, -1, this::stockMarketBlock)));
        addTest("terminal_family", () -> corner("shared/terminal-family.png", SCENE, 11.5, -59, -2, 90, 14, this::terminalRow));
        addTest("news_toast", () -> {
            try {
                // the top-right corner around the popup (320x64 pixels at the right edge)
                return corner("stockmarket/news-toast.png", HUD, 11.5, -59, -2, 90, 14, this::newsToast,
                        () -> new Rectangle(HUD.windowWidth() - 360, 0, 360, 100));
            } finally {
                Gui.onClient(() -> Minecraft.getInstance().gameRenderer.setRenderHand(true));
            }
        });
        addTest("villager_trade", () -> {
            try {
                return corner("stockmarket/villager-trade.png", SCENE, 20.5, -59, -7.5, 0, 8, this::villagerTrade, DocsWorldShotsSuite::tradeWindow);
            } finally {
                Gui.onClient(() -> jeiOverlay(true));
            }
        });
        addTest("web_interface", this::test_webInterface);
    }

    // ---------------------------------------------------------------- home corner

    /** Places the home corner, fills its displays and Money Stockpile and writes a week of Savings' balance history. */
    private TestResult test_homeCorner() {
        if (fixture == null) return skip(String.valueOf(fixtureProblem));
        Path source = findStructure();
        if (source == null) return skip(cornerProblem = "no " + STRUCTURE + " in a parent folder of the game dir (or -D" + STRUCTURE_PROPERTY + ")");
        MinecraftServer server = Gui.onClient(() -> Minecraft.getInstance().getSingleplayerServer());
        try {
            Path target = server.getWorldPath(LevelResource.GENERATED_DIR).resolve("docs/structures/home-hero.nbt");
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return fail(cornerProblem = "structure not copied: " + e);
        }
        // the cell's client renders 2 chunks; the corner and the sky behind it need more
        Gui.onClient(() -> Minecraft.getInstance().options.renderDistance().set(8));
        // placed and filled while the player is far away, so the client first loads the corner already filled: its
        // displays keep the chart view (price range) they first got, the one of the builder's world
        for (String command : List.of("gamemode creative", "gamerule sendCommandFeedback false", "gamerule doMobSpawning false",
                "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set " + SCENE.dayTime(), "weather clear",
                "tp @s 16 -59 -600", "forceload add 0 -16 31 15")) {
            Gui.sendCommand(command);
            Gui.waitTicks(2);
        }
        Gui.waitTicks(60);
        Gui.sendCommand("place template docs:home-hero 6 -60 -13");
        Gui.waitTicks(20);
        Gui.sendCommand("kill @e[type=!minecraft:player,type=!minecraft:item_frame]"); // the flat world's animals, no mobs in the pictures

        String problem = writeBalanceHistory();
        if (problem == null) problem = await(server.submit(() -> fillCorner(server.overworld())), WAIT_MS);
        Gui.sendCommand("tp @s 16 -59 -4");
        Gui.waitTicks(5);
        Gui.sendCommand("forceload remove 0 -16 31 15");
        if (problem != null) return fail(cornerProblem = problem);
        Gui.waitTicks(200); // chunks around the corner, display contents, chart data
        problem = fitDisplayCharts();
        if (problem != null) return fail(cornerProblem = problem);
        cornerProblem = null;
        return pass("placed from " + source);
    }

    /**
     * Fits each Stock Market Display's chart to its newest candles the way a player does: opens its chart screen
     * (what a right-click on the display opens), presses Space (vertical fit over the visible candles) and closes
     * it, which stores the view in the block. The first fit of a fresh display can leave its candles in a thin band.
     *
     * @return null, or what is missing
     */
    private static @Nullable String fitDisplayCharts() {
        for (BlockPos pos : CHARTS.keySet()) {
            boolean opened = Gui.onClient(() -> {
                if (!(Minecraft.getInstance().level.getBlockEntity(pos) instanceof StockMarketDisplayBlockEntity display)
                        || display.getSelectedItemID() == null)
                    return false;
                CompoundTag view = display.getChart() instanceof DisplayCandlestickChart chart ? chart.getViewportState() : null;
                ClientCompat.setScreen(new DisplayChartScreen(pos, display.getSelectedItemID(), display.getSecondItemID(), pos.asLong(), view));
                return true;
            });
            if (!opened) return "no configured Stock Market Display at " + pos + " on the client";
            Gui.waitForScreen(DisplayChartScreen.class, 40);
            Gui.waitTicks(DATA_TICKS); // the candles arrive
            Gui.key(GLFW.GLFW_KEY_SPACE);
            Gui.waitTicks(5);
            Gui.close();
            Gui.waitTicks(5);
        }
        return null;
    }

    /** The structure file: the system property, else {@link #STRUCTURE} below a parent folder of the game dir. */
    private static @Nullable Path findStructure() {
        String property = System.getProperty(STRUCTURE_PROPERTY);
        if (property != null) return Files.isRegularFile(Paths.get(property)) ? Paths.get(property) : null;
        Path dir = Gui.onClient(() -> Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath());
        for (Path p = dir; p != null; p = p.getParent())
            if (Files.isRegularFile(p.resolve(STRUCTURE))) return p.resolve(STRUCTURE);
        return null;
    }

    /**
     * Server thread: points the displays at this world's markets and Savings, and refills the Money Stockpile (the
     * structure stores the builder's world's item numbers). Positions: images.md "The home corner".
     *
     * @return null, or what is missing
     */
    private static @Nullable String fillCorner(ServerLevel level) {
        if (!"stockmarket:stock_market_block".equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(7, -59, -3)).getBlock()).toString()))
            return "the home corner was not placed (no Stock Market Block at 7 -59 -3)";
        for (Map.Entry<BlockPos, Item> chart : CHARTS.entrySet()) {
            if (!(level.getBlockEntity(chart.getKey()) instanceof StockMarketDisplayBlockEntity display))
                return "no Stock Market Display at " + chart.getKey();
            display.setConfig(StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART, fixture.market(chart.getValue()));
            // the stored chart view fits the builder's prices, and a market change keeps it (SM-364): drop it
            display.applyViewport(null);
        }
        // the main blocks of the 2x1 and the 2x2 Bank Display
        for (BlockPos pos : List.of(new BlockPos(12, -57, 5), new BlockPos(15, -57, 5))) {
            if (!(level.getBlockEntity(pos) instanceof BankSystemDisplayBlockEntity display))
                return "no Bank Display at " + pos;
            display.setConfig(BankSystemDisplayBlockEntity.DisplayType.BALANCE_HISTORY, fixture.account());
        }
        if (!(level.getBlockEntity(new BlockPos(12, -58, 5)) instanceof MoneyStockpileBlockEntity pile))
            return "no Money Stockpile at 12 -58 5";
        pile.getItems().clear();
        List<ItemStack> money = BankSystemItems.getMoneyItems(); // coins first, then the notes by value
        for (int i = 0; i < money.size(); i++)
            pile.addItems(money.get(i).copyWithCount(i < 6 ? 1 : i < 12 ? 2 : 5));
        return null;
    }

    /** A week of invented balance history of Savings (money, diamond, iron every two hours) for the Bank Displays. */
    private @Nullable String writeBalanceHistory() {
        var manager = BankSystemModBackend.getBalanceHistoryManager();
        if (manager == null) return "no balance history manager";
        long scale = await(BankSystemMod.getAPI().getServerBankManager().getAsync().getItemFractionScaleFactorAsync(), WAIT_MS);
        long now = System.currentTimeMillis(), step = TimeUnit.HOURS.toMillis(2);
        int points = 7 * 12;
        Random random = new Random(7);
        List<BalanceHistoryRecord> records = new ArrayList<>();
        double cash = 8_200, diamonds = 40, iron = 96;
        for (int i = 0; i <= points; i++) {
            long time = now - (points - i) * step;
            cash += (12_450 - 8_200) / (double) points + random.nextGaussian() * 90;
            if (i % 9 == 0) diamonds += 3;
            if (i % 14 == 0) iron -= 5;
            records.add(new BalanceHistoryRecord(fixture.account(), fixture.money().getShort(), Math.round(cash) * scale, 0, time));
            records.add(new BalanceHistoryRecord(fixture.account(), fixture.market(Items.DIAMOND).getShort(), Math.round(Math.min(diamonds, 64)) * scale, 0, time));
            records.add(new BalanceHistoryRecord(fixture.account(), fixture.market(Items.IRON_INGOT).getShort(), Math.round(Math.max(iron, 64)) * scale, 0, time));
        }
        await(manager.save(records), 60_000);
        return null;
    }

    // ---------------------------------------------------------------- corner shots

    /**
     * A shot from a camera spot of images.md: feet position, yaw, pitch. The player flies, so spots above the floor
     * hold. {@code prepare} (may be null) sets the scene up after the teleport.
     */
    private TestResult corner(String file, Screenshots.Scene scene, double x, double y, double z, float yaw, float pitch,
                              @Nullable Supplier<String> prepare) {
        return corner(file, scene, x, y, z, yaw, pitch, prepare, null);
    }

    /** As above, the picture cut to {@code crop} (pixels) when not null. */
    private TestResult corner(String file, Screenshots.Scene scene, double x, double y, double z, float yaw, float pitch,
                              @Nullable Supplier<String> prepare, @Nullable Supplier<Rectangle> crop) {
        if (cornerProblem != null) return skip(cornerProblem);
        return shot(file, scene, () -> {
            Gui.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
            Gui.onClient(() -> {
                var player = Minecraft.getInstance().player;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            });
            Gui.waitTicks(40);
            return prepare == null ? null : prepare.get();
        }, crop);
    }

    /**
     * Runs {@code shot} with a vertical field of view of {@code fov} degrees (30..110) and without FOV effects (flying
     * widens it by 10 %), then restores both: a narrow view lets the corner fill the picture from further away.
     */
    private static TestResult withFov(int fov, Supplier<TestResult> shot) {
        Options options = Gui.onClient(() -> Minecraft.getInstance().options);
        int oldFov = Gui.onClient(() -> options.fov().get());
        double oldEffects = Gui.onClient(() -> options.fovEffectScale().get());
        Gui.onClient(() -> {
            options.fov().set(fov);
            options.fovEffectScale().set(0.0);
        });
        try {
            return shot.get();
        } finally {
            Gui.onClient(() -> {
                options.fov().set(oldFov);
                options.fovEffectScale().set(oldEffects);
            });
        }
    }

    /** A Stock Market Block under the 3x2 display, so the display picture shows where the markets are traded. */
    private @Nullable String stockMarketBlock() {
        Gui.sendCommand("setblock 21 -59 5 stockmarket:stock_market_block[facing=north]");
        Gui.waitTicks(20);
        return null;
    }

    /** The open trade screen's window plus a small margin, in pixels (vanilla MerchantScreen: 276x166 GUI pixels, centred). */
    private static Rectangle tradeWindow() {
        return Gui.onClient(() -> {
            Minecraft mc = Minecraft.getInstance();
            int scale = (int) mc.getWindow().getGuiScale(), margin = 2;
            int left = (mc.screen.width - 276) / 2 - margin, top = (mc.screen.height - 166) / 2 - margin;
            return new Rectangle(left * scale, top * scale, (276 + 2 * margin) * scale, (166 + 2 * margin) * scale);
        });
    }

    /** The Terminal Block and the ATM join the Stock Market Block and the Bank Terminal Block on the west wall. */
    private @Nullable String terminalRow() {
        Gui.sendCommand("setblock 7 -59 -4 banksystem:terminal_block[facing=east]");
        Gui.sendCommand("setblock 7 -59 -1 banksystem:atm_block[facing=east]"); // in place of the Share Stamper
        Gui.waitTicks(20);
        return null;
    }

    /** News popups on, one news event published: its popup is in the top-right corner for five seconds. */
    private @Nullable String newsToast() {
        String problem = terminalRow();
        if (problem != null) return problem;
        Gui.onClient(() -> {
            PlayerPreferences prefs = StockMarketGuiElement.getPlayerPreferences();
            prefs.setNewsToastEnabled(true);
            StockMarketGuiElement.updatePlayerPreferences(prefs);
            Minecraft.getInstance().gameRenderer.setRenderHand(false); // HUD on, but no empty hand in front of the blocks
        });
        Gui.waitTicks(20);
        Gui.sendCommand("stockmarket news trigger " + TOAST_NEWS);
        Gui.waitTicks(5);
        Gui.sendCommand("stockmarket news skipphase " + TOAST_NEWS); // published now: the popup shows
        Gui.waitTicks(20);
        Gui.onClient(() -> Minecraft.getInstance().gui.getChat().clearMessages(false));
        return null;
    }

    /** A toolsmith whose trades are listed items, so they are priced in money; its trade screen opened. */
    private @Nullable String villagerTrade() {
        StockMarketModBackend.ServerInstances b = StockMarketModBackend.getServerInstances();
        MinecraftServer server = Gui.onClient(() -> Minecraft.getInstance().getSingleplayerServer());
        await(server.submit(() -> b.VILLAGER_TRADE_MANAGER.recomputeTable()), WAIT_MS); // the markets are newer than the price table
        String offers = Stream.of(offer("coal", 15, "emerald", 1), offer("iron_ingot", 4, "emerald", 1),
                offer("gold_ingot", 3, "emerald", 1), offer("diamond", 1, "emerald", 1),
                offer("emerald", 1, "redstone", 4), offer("emerald", 1, "lapis_lazuli", 2)).reduce((a, c) -> a + "," + c).orElseThrow();
        Gui.sendCommand("time set " + SCENE.dayTime()); // daylight behind the window, also after the evening shot of the full topic
        Gui.sendCommand("kill @e[type=minecraft:villager]");
        Gui.sendCommand("summon minecraft:villager 20.5 -59 -5.0 {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Silent:1b,Rotation:[180f,0f],"
                + "VillagerData:{profession:\"minecraft:toolsmith\",level:4,type:\"minecraft:plains\"},Offers:{Recipes:[" + offers + "]}}");
        Gui.waitTicks(20);
        boolean found = Gui.onClient(() -> {
            Minecraft mc = Minecraft.getInstance();
            var villager = mc.level.getEntitiesOfClass(Villager.class, mc.player.getBoundingBox().inflate(6)).stream()
                    .min(Comparator.comparingDouble(v -> v.distanceTo(mc.player))).orElse(null);
            if (villager != null) mc.gameMode.interact(mc.player, villager, InteractionHand.MAIN_HAND);
            return villager != null;
        });
        if (!found) return "no villager next to the player";
        Gui.waitForScreen(MerchantScreen.class, 100);
        Gui.onClient(() -> jeiOverlay(false));
        Gui.waitTicks(20);
        return null;
    }

    /**
     * Shows or hides JEI's item list and bookmarks beside container screens. JEI's API has no switch for them: this
     * flips its internal toggles (the Ctrl+O key, kept in memory only) by reflection. Client thread.
     */
    private static void jeiOverlay(boolean on) {
        try {
            Object state = Class.forName("mezz.jei.common.Internal").getMethod("getClientToggleState").invoke(null);
            Class<?> type = state.getClass();
            if ((boolean) type.getMethod("isOverlayEnabled").invoke(state) != on)
                type.getMethod("toggleOverlayEnabled").invoke(state);
            if ((boolean) type.getMethod("isBookmarkOverlayEnabled").invoke(state) != on)
                type.getMethod("toggleBookmarkEnabled").invoke(state);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // ponytail: JEI internals; without JEI there is no list, after a JEI rename the list stays in the picture
        }
    }

    /** One vanilla trade in summon NBT: the villager takes {@code buy}, gives {@code sell}. */
    private static String offer(String buy, int buyCount, String sell, int sellCount) {
        return "{buy:{id:\"minecraft:" + buy + "\",count:" + buyCount + "},sell:{id:\"minecraft:" + sell + "\",count:" + sellCount
                + "},maxUses:12,uses:0,xp:10,priceMultiplier:0.05f,rewardExp:1b}";
    }

    // ---------------------------------------------------------------- web interface

    /** The web interface on a free port, diamond selected, shot by headless Firefox at 1920x1080. */
    private TestResult test_webInterface() {
        if (fixture == null) return skip(String.valueOf(fixtureProblem));
        Path firefox = Paths.get(System.getProperty(FIREFOX_PROPERTY, "C:/Program Files/Mozilla Firefox/firefox.exe"));
        if (!Files.isRegularFile(firefox)) return skip("no Firefox at " + firefox + " (-D" + FIREFOX_PROPERTY + ")");
        StockMarketModBackend.ServerInstances b = StockMarketModBackend.getServerInstances();
        StockMarketModSettings.Integrations settings = b.SERVER_SETTINGS.INTEGRATIONS;
        boolean enabledBefore = Boolean.TRUE.equals(settings.WEB_INTERFACE_ENABLED.get());
        int portBefore = settings.WEB_INTERFACE_PORT.get();
        try {
            writeRecentTrades(b);
            int port = freePort();
            settings.WEB_INTERFACE_ENABLED.set(true);
            settings.WEB_INTERFACE_PORT.set(port);
            StockMarketWebInterface.apply(b);
            if (StockMarketWebInterface.getRunningPort() != port) return fail("web interface not running on " + port);
            String selectDiamond = """
                    new Promise(resolve => {
                      const start = Date.now();
                      const tick = () => {
                        const li = document.querySelector('li[title="minecraft:diamond"]');
                        if (li) { li.click(); resolve(true); }
                        else if (Date.now() - start > 20000) resolve(false);
                        else setTimeout(tick, 200);
                      };
                      tick();
                    })""";
            byte[] png = firefoxShot(firefox, "http://127.0.0.1:" + port + "/", selectDiamond, 5_000);
            String root = System.getProperty(Screenshots.ROOT_PROPERTY);
            Path base = root != null ? Paths.get(root)
                    : Gui.onClient(() -> Minecraft.getInstance().gameDirectory.toPath()).resolve("screenshots").resolve("testkit");
            Path file = base.resolve("stockmarket/web-interface.png").toAbsolutePath().normalize();
            Files.createDirectories(file.getParent());
            Files.write(file, png);
            return pass(file.toString());
        } catch (Exception e) {
            return fail("web-interface.png: " + e);
        } finally {
            settings.WEB_INTERFACE_ENABLED.set(enabledBefore);
            settings.WEB_INTERFACE_PORT.set(portBefore);
            StockMarketWebInterface.apply(b);
        }
    }

    /** Invented trades of the last half hour on diamond for the "recent trades" list (no player shows there). */
    private void writeRecentTrades(StockMarketModBackend.ServerInstances b) {
        ItemID diamond = fixture.market(Items.DIAMOND);
        long price = await(await(b.MARKET_MANAGER.getAsync().getMarketAsync(diamond), WAIT_MS).getCurrentMarketPriceAsync(), WAIT_MS);
        long now = System.currentTimeMillis();
        Random random = new Random(11);
        List<OrderRecordStruct> trades = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            long amount = (random.nextInt(16) + 1) * (random.nextBoolean() ? 1L : -1L);
            long tradePrice = Math.round(price * (1 + random.nextGaussian() * 0.004));
            UUID trader = UUID.nameUUIDFromBytes(("docs-trader-" + i % 3).getBytes()); // invented, never shown
            trades.add(new OrderRecordStruct(diamond.getShort(), 0, trader, 0,
                    net.kroia.stockmarket.stockmarket.marketmanager.MarketManager.convertToRawAmountStatic(amount), tradePrice,
                    now - (12 - i) * 150_000L));
        }
        await(b.ORDER_RECORD_MANAGER.save(trades), WAIT_MS);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Opens {@code url} in headless Firefox at 1920x1080 over WebDriver BiDi, runs {@code script} (a JavaScript
     * expression giving a promise of true), waits {@code settleMs} and returns the PNG. Only the Firefox started
     * here (own profile, {@code --no-remote}) and its child processes are ended afterwards.
     */
    static byte[] firefoxShot(Path firefox, String url, String script, long settleMs) throws Exception {
        int port = freePort();
        Path profile = Files.createTempDirectory("sm-docs-firefox");
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
            String context = bidi.call("browsingContext.create", json("type", "tab")).get("context").getAsString();
            bidi.call("browsingContext.setViewport", json("context", context, "viewport", json("width", 1920, "height", 1080)));
            bidi.call("browsingContext.navigate", json("context", context, "url", url, "wait", "complete"));
            JsonObject done = bidi.call("script.evaluate", json("expression", script, "target", json("context", context), "awaitPromise", true));
            if (!"success".equals(done.get("type").getAsString()) || !done.getAsJsonObject("result").get("value").getAsBoolean())
                throw new IllegalStateException("page script did not succeed: " + done);
            Thread.sleep(settleMs);
            String data = bidi.call("browsingContext.captureScreenshot", json("context", context)).get("data").getAsString();
            bidi.call("session.end", new JsonObject());
            return Base64.getDecoder().decode(data);
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
