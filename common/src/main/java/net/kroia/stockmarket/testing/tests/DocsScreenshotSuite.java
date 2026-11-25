package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.bank.BankStatus;
import net.kroia.banksystem.api.bank.IAsyncBank;
import net.kroia.banksystem.api.bankmanager.IAsyncBankManager;
import net.kroia.banksystem.banking.company.AsyncCompanyManager;
import net.kroia.banksystem.minecraft.item.BankSystemItems;
import net.kroia.banksystem.minecraft.item.custom.share.StampedShareItem;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.gui.client.ClientCompat;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.stockmarket.networking.request.CreateOrderRequest;
import net.kroia.stockmarket.networking.request.PluginSubscriptionRequest;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.screen.ManagementScreen;
import net.kroia.stockmarket.screen.NewsScreen;
import net.kroia.stockmarket.screen.TradeScreen;
import net.kroia.stockmarket.screen.custom.DisplayConfigScreen;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;
import net.kroia.testkit.gui.Gui;
import net.kroia.testkit.gui.GuiNode;
import net.kroia.testkit.gui.Layout;
import net.kroia.testkit.gui.Screenshots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * SM-358: the 22 plain StockMarket screens of the documentation site, taken as scripted screenshots
 * ({@link Screenshots#take}) on the game client of an sp cell. File names and the state each screen shows:
 * Docs/planning/images.md, section 2 "StockMarket (22)".
 * <p>
 * The first test builds an invented market world on the integrated server: ten markets with the default plugins
 * and about 33 hours of one-minute candles each (written straight into the price history, a random walk ending at
 * the live price), money and items for the player, two own limit orders on diamond, two companies with open share
 * markets, three published news events and trading hours on wheat. The other tests each open one screen and take
 * its picture; a failed shot does not stop the others.
 * <p>
 * Opt-in: the shots take minutes and resize the window, so the suite runs only when named with
 * {@code --only stockmarket/sm_docs_shots} ({@link #onDemand()}). Start the client with
 * {@code --client-name Tavrik}: the player name shows on some screens. Needs the integrated server (sp cells).
 * The in-world pictures are taken by {@link DocsWorldShotsSuite} on the same world.
 */
public class DocsScreenshotSuite extends TestSuite {

    /** The topic to name with --only. */
    static final String TOPIC = "stockmarket/sm_docs_shots";
    static final long WAIT_MS = 15_000;
    private static final long MINUTE = 60_000L;
    /** One-minute candles written per market (about 33 hours). */
    private static final int HISTORY_CANDLES = 2_000;
    /** Ticks to wait after opening a screen, so its requests are answered and charts are filled. */
    static final int DATA_TICKS = 60;
    /** The ten markets of the docs world; diamond first, the market most shots show. */
    private static final List<Item> MARKET_ITEMS = List.of(Items.DIAMOND, Items.IRON_INGOT, Items.GOLD_INGOT,
            Items.EMERALD, Items.COAL, Items.COPPER_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI, Items.WHEAT, Items.OAK_LOG);
    /** News events triggered for the newspaper (all three have a picture); the first stays running for the Active tab. */
    private static final List<String> NEWS = List.of("diamond_rush", "iron_supply_disruption", "gold_rush_rumor");
    /** Invented companies with an open share market and their initial share price. */
    private static final Map<String, Float> COMPANIES = new LinkedHashMap<>();
    static {
        COMPANIES.put("Acme Mining", 25f);
        COMPANIES.put("Brindle Timber", 8f);
    }
    /** Trading hours of wheat (in-game minutes, 08:00-18:00) and the time of day the closed shot is taken at (20:00). */
    private static final int WHEAT_OPEN = 8 * 60, WHEAT_CLOSE = 18 * 60;
    private static final long EVENING = 14_000;
    /** User decision 2026-11-24: Full HD at GUI scale 2 (960x540 GUI space); at 1280x720 buttons overlap. */
    static final Screenshots.Scene SCENE = new Screenshots.Scene(1920, 1080, 2, 6_000, true);

    /** The world built by {@link #test_fixture()}, shared with later docs suites in the same client; null until built. */
    static @Nullable Fixture fixture;
    /** Why the fixture could not be built (the shots skip with it), or null. */
    static @Nullable String fixtureProblem = "fixture not built yet";

    /** What the fixture created: the player, its personal account, the money item and the market ids. */
    record Fixture(UUID self, String name, int account, ItemID money, Map<Item, ItemID> markets, List<ItemID> companies) {
        ItemID market(Item item) {
            return markets.get(item);
        }
    }

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.DOCS_SHOTS;
    }

    @Override
    public Side side() {
        return Side.CLIENT;
    }

    /** The shots take minutes and resize the window: they run only when named with --only (also DocsWorldShotsSuite). */
    @Override
    public boolean onDemand() {
        return true;
    }

    @Override
    public void registerTests() {
        addTest("fixture", this::test_fixture);
        addTest("trading_screen", () -> shot("stockmarket/trading-screen.png", () -> tradeScreen(Items.IRON_INGOT))); // the page sells iron
        addTest("price_chart", () -> shot("stockmarket/price-chart.png", this::priceChart));
        addTest("item_item_mode", () -> shot("stockmarket/item-item-mode.png", this::itemItemMode));
        addTest("companies_tab", () -> shot("stockmarket/companies-tab.png", this::companiesTab));
        addTest("newspaper", () -> shot("stockmarket/newspaper.png", this::newspaper));
        addTest("display_config", () -> shot("stockmarket/display-config.png", this::displayConfig));
        addTest("management_overview", () -> shot("stockmarket/management-overview.png", this::managementOverview));
        addTest("market_settings", () -> shot("stockmarket/market-settings.png", this::marketSettings));
        addTest("create_markets", () -> shot("stockmarket/create-markets.png", this::createMarkets));
        addTest("presets_tab", () -> shot("stockmarket/presets-tab.png", this::presetsTab));
        addTest("mod_settings", () -> shot("stockmarket/mod-settings.png", this::modSettings));
        addTest("plugin_management", () -> shot("stockmarket/plugin-management.png", this::pluginManagementOverview));
        addTest("net_flow_settings", () -> shot("stockmarket/plugins/net-flow-settings.png", () -> pluginEntry("flow")));
        addTest("volatility_settings", () -> shot("stockmarket/plugins/volatility-settings.png", () -> pluginEntry("volatil")));
        addTest("order_book_volume_settings", () -> shot("stockmarket/plugins/order-book-volume-settings.png", () -> pluginEntry("orderbook")));
        addTest("target_price_bot_settings", () -> shot("stockmarket/plugins/target-price-bot-settings.png", this::targetPriceBotSettings));
        addTest("news_settings", () -> shot("stockmarket/plugins/news-settings.png", this::newsSettings));
        addTest("news_all_events", () -> shot("stockmarket/news/all-events.png", () -> newsTab("All events")));
        addTest("news_scheduler", () -> shot("stockmarket/news/scheduler.png", () -> newsTab("Scheduler")));
        addTest("news_event_details", () -> shot("stockmarket/news/event-details.png", this::newsEventDetails));
        addTest("news_active", this::test_newsActive);
        addTest("market_closed", this::test_marketClosed); // last: the evening time stays after it (the sky catches up slowly)
    }

    // ---------------------------------------------------------------- fixture

    /** Builds the invented market world (see the class Javadoc). Every shot skips if this fails. */
    TestResult test_fixture() {
        if (fixture != null) return pass("built by an earlier docs suite in this client");
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null || client.MARKET_MANAGER == null) return skip(fixtureProblem = "not in a world");
        MinecraftServer server = Gui.onClient(() -> Minecraft.getInstance().getSingleplayerServer());
        if (server == null) return skip(fixtureProblem = "needs the integrated server (sp cell)");
        StockMarketModBackend.ServerInstances b = StockMarketModBackend.getServerInstances();
        if (b == null || b.MARKET_MANAGER.isSlave()) return skip(fixtureProblem = "not the StockMarket master");
        try {
            // final window size first, so every screen is laid out once at the size it is shot at
            Gui.onClient(() -> Minecraft.getInstance().getWindow().setWindowed(SCENE.windowWidth(), SCENE.windowHeight()));
            Gui.setGuiScale(2);
            Gui.waitTicks(10);

            UUID self = Gui.onClient(() -> Minecraft.getInstance().player.getUUID());
            String name = Gui.onClient(() -> Minecraft.getInstance().player.getGameProfile().getName());
            if (!Boolean.TRUE.equals(await(b.MARKET_MANAGER.getAsync().setStockmarketAdminModeAsync(self, true), WAIT_MS)))
                return fail(fixtureProblem = "admin flag not set");

            // markets with history
            Map<Item, ItemID> markets = new LinkedHashMap<>();
            Map<ItemID, IAsyncMarket> asyncMarkets = new LinkedHashMap<>();
            for (Item item : MARKET_ITEMS) {
                ItemID id = await(ItemIDManager.registerItemStackServerSide(new ItemStack(item)), WAIT_MS);
                IAsyncMarket market = Boolean.TRUE.equals(await(b.MARKET_MANAGER.getAsync().marketExistsAsync(id), WAIT_MS))
                        ? await(b.MARKET_MANAGER.getAsync().getMarketAsync(id), WAIT_MS)
                        : await(onServer(server, () -> b.MARKET_MANAGER.getAsync().createMarketAsync(id)), WAIT_MS);
                if (market == null) return fail(fixtureProblem = "market " + id + " not created");
                markets.put(item, id);
                asyncMarkets.put(id, market);
            }
            if (!Boolean.TRUE.equals(await(asyncMarkets.get(markets.get(Items.WHEAT)).setTradingHoursAsync(WHEAT_OPEN, WHEAT_CLOSE), WAIT_MS)))
                return fail(fixtureProblem = "trading hours of wheat not set");

            // every market on every plugin: a fresh world's default plugins hold only its first market, and the
            // news events of iron and gold need their market on the NewsPlugin (through the admin request, as the UI does)
            ServerPluginManager pluginManager = (ServerPluginManager) b.PLUGIN_MANAGER.getSync();
            for (ServerPlugin plugin : List.copyOf(pluginManager.getPlugins().values()))
                for (ItemID id : markets.values())
                    if (!plugin.getSubscribedMarkets().contains(id)) {
                        PluginSubscriptionRequest.OutputData out = await(Gui.onClient(
                                () -> client.PLUGIN_MANAGER.requestUpdateSubscription(plugin.getInstanceID(), id, true)), WAIT_MS);
                        if (out == null || !out.success())
                            return fail(fixtureProblem = "market " + id + " not subscribed to " + plugin.getClass().getSimpleName());
                    }

            // money, items and the personal account
            IAsyncBankManager bank = BankSystemMod.getAPI().getServerBankManager().getAsync();
            long scale = await(bank.getItemFractionScaleFactorAsync(), WAIT_MS);
            ItemID money = await(b.MARKET_MANAGER.getAsync().getTradingCurrencyIDAsync(), WAIT_MS);
            Integer account = await(bank.createPersonalBankAccountGetAccountNrAsync(self), WAIT_MS);
            if (account == null || account < 0) return fail(fixtureProblem = "no personal account: " + account);
            var accountRef = await(bank.getBankAccountAsync(account), WAIT_MS);
            if (accountRef != null) accountRef.setAccountNameAsync("Savings"); // the invented account name of the docs
            String error = deposit(bank, self, money, 12_450L * scale);
            if (error == null) error = deposit(bank, self, markets.get(Items.DIAMOND), 64L * scale);
            if (error == null) error = deposit(bank, self, markets.get(Items.IRON_INGOT), 64L * scale);
            if (error != null) return fail(fixtureProblem = error);

            // companies with open share markets
            List<ItemID> companies = new ArrayList<>();
            for (Map.Entry<String, Float> c : COMPANIES.entrySet()) {
                AsyncCompanyManager.CompanyInfoOutput info = await(AsyncCompanyManager.getCompanyInfoAsync(c.getKey()), WAIT_MS);
                if (info == null || !info.present()) {
                    AsyncCompanyManager.CreateOutput out = await(AsyncCompanyManager.createCompanyAsync(c.getKey(), 10_000L, self, name), WAIT_MS);
                    if (out == null || out.resultCode() != AsyncCompanyManager.CODE_OK)
                        return fail(fixtureProblem = "company " + c.getKey() + " not created: " + (out == null ? "no answer" : "code " + out.resultCode()));
                    info = await(AsyncCompanyManager.getCompanyInfoAsync(c.getKey()), WAIT_MS);
                }
                int companyId = info.companyId();
                AsyncCompanyManager.OpenShareMarketOutput open = await(onServer(server, () -> AsyncCompanyManager.openShareMarketAsync(companyId, c.getValue(), self)), WAIT_MS);
                if (open == null || (open.status() != AsyncCompanyManager.SM_STATUS_SUCCESS && open.status() != AsyncCompanyManager.SM_STATUS_ALREADY_EXISTS))
                    return fail(fixtureProblem = "share market of " + c.getKey() + " not opened: " + (open == null ? "no answer" : open.status() + " " + open.reason()));
                ItemID share = await(ItemIDManager.registerItemStackServerSide(
                        Gui.onClient(() -> StampedShareItem.ofCompany(BankSystemItems.STAMPED_SHARE.get(), companyId))), WAIT_MS);
                companies.add(share);
                IAsyncMarket market = await(b.MARKET_MANAGER.getAsync().getMarketAsync(share), WAIT_MS);
                if (market != null) asyncMarkets.put(share, market);
            }

            // one-minute candle history, a random walk that ends at each market's live price
            long seed = 1, diamondPrice = 0;
            for (Map.Entry<ItemID, IAsyncMarket> m : asyncMarkets.entrySet()) {
                long price = await(m.getValue().getCurrentMarketPriceAsync(), WAIT_MS);
                if (m.getKey().equals(markets.get(Items.DIAMOND))) diamondPrice = price;
                if (price <= 0) return fail(fixtureProblem = "market " + m.getKey() + " has no price: " + price);
                await(b.MARKET_PRICE_HISTORY_MANAGER.save(history(m.getKey(), price, seed++)), 60_000);
            }

            // the client loads the new markets
            await(Gui.onClient(() -> client.MARKET_MANAGER.requestMarkets()), WAIT_MS);
            if (!Gui.waitUntil(() -> client.MARKET_MANAGER.getMarket(markets.get(Items.DIAMOND)) != null, 200))
                return fail(fixtureProblem = "the client has no diamond market");

            fixture = new Fixture(self, name, account, money, markets, companies);

            // two own limit orders around the diamond price (the markers on the chart) and one small market buy
            ClientMarket diamond = Gui.onClient(() -> client.MARKET_MANAGER.getMarket(markets.get(Items.DIAMOND)));
            // the server's price: the client's price arrives only with a price subscription
            double p = MarketManager.convertToRealAmountStatic(diamondPrice);
            error = order(() -> diamond.createLimitBuyOrder(account, 5, Math.floor(p * 0.985)), "limit buy");
            if (error == null) error = order(() -> diamond.createLimitSellOrder(account, -3, Math.ceil(p * 1.015)), "limit sell");
            if (error == null) error = order(() -> diamond.createMarketBuyOrder(account, 2), "market buy");
            if (error != null) return fail(fixtureProblem = error);

            // three published news events
            for (String id : NEWS) {
                Gui.sendCommand("stockmarket news trigger " + id);
                Gui.waitTicks(5);
                Gui.sendCommand("stockmarket news skipphase " + id); // announce now instead of after the random delay
                Gui.waitTicks(5);
            }
            Gui.waitTicks(100); // let the news toasts fade before the first shot

            fixtureProblem = null;
            return pass(markets.size() + " markets, " + companies.size() + " company markets, diamond at " + p
                    + ", account " + account + ", player " + name);
        } catch (RuntimeException e) {
            fixture = null;
            return fail(fixtureProblem = "fixture: " + e);
        }
    }

    /**
     * Starts {@code call} on the server thread: on the master the async API runs on the calling thread, and a market
     * created from the test thread raced the server tick's loop over the markets (ConcurrentModificationException).
     */
    static <T> CompletableFuture<T> onServer(MinecraftServer server, Supplier<CompletableFuture<T>> call) {
        return server.submit(call).thenCompose(f -> f);
    }

    /** Deposits {@code amount} raw units of {@code item} into the player's personal account; returns an error or null. */
    @Nullable String deposit(IAsyncBankManager bank, UUID self, ItemID item, long amount) {
        IAsyncBank b = await(bank.getOrCreatePersonalBankAsync(self, item), WAIT_MS);
        if (b == null) return "no bank for " + item;
        BankStatus status = await(b.depositAsync(amount), WAIT_MS);
        return status == BankStatus.SUCCESS ? null : "deposit of " + amount + " " + item + " failed: " + status;
    }

    /** Places one order from the client, retrying while the trade cooldown refuses it; returns an error or null. */
    @Nullable String order(Supplier<CompletableFuture<CreateOrderRequest.OutputData>> place, String what) {
        for (int i = 0; i < 10; i++) {
            CreateOrderRequest.OutputData out = await(Gui.onClient(place::get), WAIT_MS);
            if (out != null && out.status == CreateOrderRequest.Status.TRADING_TOO_FAST) {
                Gui.waitTicks(20);
                continue;
            }
            Gui.waitTicks(10);
            return out != null && out.status == CreateOrderRequest.Status.CREATED ? null
                    : what + " refused: " + (out == null ? "no answer" : out.status);
        }
        return what + " refused: trade cooldown";
    }

    /**
     * {@link #HISTORY_CANDLES} one-minute candles before the current minute: a random walk with slow swings,
     * built backwards from {@code endPrice} so the last candle closes at the live price (close = next open).
     */
    private static List<MarketPriceStruct> history(ItemID id, long endPrice, long seed) {
        Random r = new Random(seed);
        int n = HISTORY_CANDLES;
        double[] open = new double[n + 1];
        open[n] = endPrice;
        double phase = r.nextDouble() * Math.PI * 2;
        for (int i = n - 1; i >= 0; i--) {
            double swing = 0.0009 * Math.sin(i / 70.0 + phase) + 0.0005 * Math.sin(i / 23.0 + 2 * phase);
            open[i] = open[i + 1] / Math.exp(swing + r.nextGaussian() * 0.0025);
        }
        long end = (System.currentTimeMillis() / MINUTE - 1) * MINUTE;
        List<MarketPriceStruct> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double o = open[i], c = open[i + 1];
            double high = Math.max(o, c) * (1 + Math.abs(r.nextGaussian()) * 0.0015);
            double low = Math.min(o, c) * (1 - Math.abs(r.nextGaussian()) * 0.0015);
            float volume = (float) (4 + 60 * r.nextDouble() * r.nextDouble());
            rows.add(new MarketPriceStruct(id.getShort(), Math.max(1, Math.round(o)), Math.max(1, Math.round(low)),
                    Math.max(1, Math.round(high)), end - (long) (n - 1 - i) * MINUTE, volume));
        }
        return rows;
    }

    // ---------------------------------------------------------------- shots

    /**
     * Runs {@code prepare} (opens the screen and brings it into the listed state), takes the picture and closes the
     * screen. {@code prepare} returns null when ready or a reason to fail.
     */
    TestResult shot(String file, Supplier<String> prepare) {
        return shot(file, SCENE, prepare);
    }

    TestResult shot(String file, Screenshots.Scene scene, Supplier<String> prepare) {
        return shot(file, scene, prepare, null);
    }

    /** As {@link #shot(String, Screenshots.Scene, Supplier)}, cut to {@code crop} (pixels, asked after {@code prepare}) when not null. */
    TestResult shot(String file, Screenshots.Scene scene, Supplier<String> prepare, @Nullable Supplier<Rectangle> crop) {
        if (fixture == null) return skip(String.valueOf(fixtureProblem));
        try {
            String problem = prepare.get();
            if (problem != null) return fail(problem);
            // in the world (no screen) the mouse turns the camera: it is left alone there
            boolean screen = Gui.onClient(() -> Minecraft.getInstance().screen != null);
            if (screen) Gui.hover(-100, -100); // no hover highlight or tooltip in the picture
            List<Layout.Problem> layout = screen ? Gui.layoutProblems() : List.of();
            Rectangle area = crop == null ? null : crop.get();
            Path path = Screenshots.take(file, scene, area);
            if (!Files.isRegularFile(path)) return fail("no screenshot at " + path);
            // layout problems are reported, not failed: the picture is checked by eye (overlapping decorations are normal)
            return pass(path + "; layout problems " + layout.size() + (layout.isEmpty() ? ""
                    : ": " + layout.stream().limit(6).map(Layout.Problem::message).toList()));
        } catch (RuntimeException e) {
            return fail(file + ": " + e);
        } finally {
            Gui.close();
            Gui.waitTicks(5);
        }
    }

    /** Opens {@code screen} on the client thread and waits for it and its data. */
    private static <T extends Screen> T open(Supplier<T> screen, Class<T> type) {
        Gui.onClient(() -> ClientCompat.setScreen(screen.get()));
        T s = Gui.waitForScreen(type, 100);
        Gui.waitTicks(DATA_TICKS);
        return s;
    }

    private @Nullable String tradeScreen() {
        return tradeScreen(Items.DIAMOND);
    }

    private @Nullable String tradeScreen(Item item) {
        Gui.onClient(() -> TradeScreen.openScreen(fixture.market(item)));
        Gui.waitForScreen(TradeScreen.class, 100);
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    private @Nullable String priceChart() {
        tradeScreen();
        click(n -> "Pending Orders".equals(n.text()), "Pending Orders tab", false);
        click(n -> "5m".equals(n.text()), "5m timeframe", false);
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    private @Nullable String itemItemMode() {
        Gui.onClient(() -> {
            PlayerPreferences prefs = StockMarketGuiElement.getPlayerPreferences();
            prefs.setLastPairHaveMarketID(fixture.market(Items.IRON_INGOT));
            prefs.setLastPairWantMarketID(fixture.market(Items.DIAMOND));
            StockMarketGuiElement.updatePlayerPreferences(prefs);
        });
        tradeScreen();
        Gui.click("Item / Item");
        Gui.waitTicks(DATA_TICKS);
        click(n -> "Exchange".equals(n.text()), "Exchange tab", false);
        Gui.waitTicks(10);
        return null;
    }

    private @Nullable String companiesTab() {
        tradeScreen();
        Gui.click(Gui.find(n -> n.visible() && n.text().startsWith("Companies"), "Companies button"));
        Gui.waitTicks(20);
        click(n -> shows(n, BankSystemItems.STAMPED_SHARE.get()), "company share", false);
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    private @Nullable String newspaper() {
        Gui.onClient(NewsScreen::openScreen);
        Gui.waitForScreen(NewsScreen.class, 100);
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    private @Nullable String displayConfig() {
        open(() -> new DisplayConfigScreen(BlockPos.ZERO, StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART,
                fixture.market(Items.DIAMOND), fixture.market(Items.IRON_INGOT)), DisplayConfigScreen.class); // an Item / Item pair
        return null;
    }

    private @Nullable String management() {
        Gui.onClient(ManagementScreen::openScreen);
        Gui.waitForScreen(ManagementScreen.class, 100);
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    /** The Overview tab with diamond selected, so the chart and the market panel are filled. */
    private @Nullable String managementOverview() {
        management();
        Gui.click(itemView(Items.DIAMOND));
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    private @Nullable String marketSettings() {
        managementOverview();
        Gui.click("Market Settings");
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    private @Nullable String createMarkets() {
        management();
        Gui.click("Create Markets");
        Gui.waitTicks(20);
        Gui.click("Food");
        Gui.waitTicks(20);
        List<GuiNode> items = Gui.findAll(n -> n.visible() && "SelectableItemView".equals(n.type()));
        for (int i = 0; i < Math.min(3, items.size()); i++) {
            Gui.click(items.get(i * 2)); // every other one, so the selection reads as a choice
            Gui.waitTicks(5);
        }
        Gui.waitTicks(10);
        return items.size() < 6 ? "only " + items.size() + " items in Food" : null;
    }

    private @Nullable String presetsTab() {
        management();
        Gui.click("Presets");
        Gui.waitTicks(20);
        scrollTo(Gui.find(n -> "Ore".equals(n.text()), "Ore category"));
        click(n -> "Ore".equals(n.text()), "Ore category", true);
        Gui.waitTicks(20);
        return clippedText(); // SM-365: the preset rows cut their checkbox labels
    }

    /** SM-365: the clipped texts (TEXT layout problems) on the open screen, or null when every text fits. */
    private static @Nullable String clippedText() {
        List<Layout.Problem> text = Gui.layoutProblems().stream().filter(p -> p.kind() == Layout.Kind.TEXT).toList();
        return text.isEmpty() ? null : "clipped text: " + text.stream().limit(6).map(Layout.Problem::message).toList();
    }

    private @Nullable String modSettings() {
        management();
        Gui.click("Mod Settings");
        Gui.waitTicks(DATA_TICKS);
        scrollTo(Gui.find(n -> "Market".equals(n.text()) && "Label".equals(n.type()), "Market group heading"));
        return null;
    }

    @Nullable String pluginManagement() {
        management();
        Gui.click("Manage Plugins");
        Gui.waitTicks(DATA_TICKS);
        // the order book's debug lines are on by default (for the session): unticked like a player would
        List<GuiNode> debug = Gui.findAll(n -> n.visible() && n.handle() instanceof CheckBox box && box.isChecked()
                && "Orderbook debug markers".equals(box.getText()));
        if (!debug.isEmpty()) { // only its box (the right end) takes the click
            GuiNode box = debug.get(0);
            Gui.click(box.x() + box.width() - box.height() / 2, box.centerY(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
            Gui.waitTicks(10);
        }
        return null;
    }

    /**
     * The entries show their full settings, so the five plugins never fit on one screen: the list is scrolled to its
     * end (the short entries of the plugins with their own screens) and diamond is on the chart.
     */
    private @Nullable String pluginManagementOverview() {
        pluginManagement();
        scrollTo(entry("news"));
        List<GuiNode> diamonds = Gui.findAll(n -> n.visible() && shows(n, Items.DIAMOND));
        if (!diamonds.isEmpty()) Gui.click(diamonds.get(diamonds.size() - 1));
        Gui.waitTicks(DATA_TICKS);
        return clippedText(); // SM-365: the news plugin's description ran past its entry
    }

    /** Plugin Management with the entry whose name contains {@code keyword} scrolled to the top, selected, diamond on the chart. */
    private @Nullable String pluginEntry(String keyword) {
        pluginManagement();
        GuiNode entry = entry(keyword);
        scrollTo(entry);
        entry = entry(keyword);
        GuiNode diamond = findIn(entry, n -> n.visible() && shows(n, Items.DIAMOND));
        if (diamond != null) Gui.click(diamond);
        else Gui.click(entry.x() + 6, entry.y() + 6, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    /** The plugin's own settings screen ("Open Plugin Settings" in its entry). */
    private @Nullable String pluginScreen(String keyword) {
        pluginManagement();
        GuiNode entry = entry(keyword);
        scrollTo(entry);
        GuiNode button = findIn(entry(keyword), n -> n.visible() && "Open Plugin Settings".equals(n.text()));
        if (button == null) return "no 'Open Plugin Settings' in the " + keyword + " entry";
        Gui.click(button);
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    /** The Target Price Bot screen with diamond selected, so its chart and fields are filled. */
    private @Nullable String targetPriceBotSettings() {
        String problem = pluginScreen("target");
        if (problem != null) return problem;
        click(n -> shows(n, Items.DIAMOND), "diamond in the item list", true);
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    private @Nullable String newsSettings() {
        String problem = pluginScreen("news");
        if (problem != null) return problem;
        List<GuiNode> diamonds = Gui.findAll(n -> n.visible() && shows(n, Items.DIAMOND));
        if (diamonds.isEmpty()) return "no diamond market in the per-market news settings";
        Gui.click(diamonds.get(diamonds.size() - 1)); // the per-market list is the right-hand panel
        Gui.waitTicks(20);
        // SM-365: the status line ("<n> definition(s), <m> active event(s)") was cut to the label's width before
        // the first layout. It is shortened with "…" on purpose, so the TEXT layout check cannot see it.
        GuiNode status = Gui.findAll(n -> n.visible() && n.text().contains("definition(s)")).stream().findFirst().orElse(null);
        if (status == null) return "no news status line with the definition count";
        return status.text().endsWith("…") ? "news status line cut: " + status.text() : null;
    }

    private @Nullable String newsTab(String tab) {
        String problem = newsSettings();
        if (problem != null) return problem;
        click(n -> tab.equals(n.text()), tab + " tab", true);
        Gui.waitTicks(30);
        return null;
    }

    private @Nullable String newsEventDetails() {
        String problem = newsTab("All events");
        if (problem != null) return problem;
        // the running diamond event, so the details show live values and a matching market; the row draws its
        // headline itself (no label), so it is read from the row's private field
        Predicate<GuiNode> diamondRush = n -> {
            if (!"EventDefRow".equals(n.type())) return false;
            try {
                var field = n.handle().getClass().getDeclaredField("headline");
                field.setAccessible(true);
                return String.valueOf(field.get(n.handle())).startsWith("Diamond rush");
            } catch (ReflectiveOperationException e) {
                return false;
            }
        };
        scrollTo(Gui.find(diamondRush, "the Diamond rush row"));
        GuiNode row = Gui.find(n -> n.visible() && diamondRush.test(n), "the visible Diamond rush row");
        Gui.click(row.x() + 20, row.centerY(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        Gui.waitTicks(DATA_TICKS);
        return null;
    }

    /** Wheat has trading hours 08:00-18:00: shot at 20:00 with the Market Closed banner. */
    private TestResult test_marketClosed() {
        Screenshots.Scene evening = new Screenshots.Scene(SCENE.windowWidth(), SCENE.windowHeight(), SCENE.guiScale(), EVENING, true);
        return shot("stockmarket/market-closed.png", evening, () -> {
            Gui.sendCommand("time set " + EVENING);
            Gui.onClient(() -> TradeScreen.openScreen(fixture.market(Items.WHEAT)));
            Gui.waitForScreen(TradeScreen.class, 100);
            boolean closed = false;
            for (int i = 0; i < 40 && !closed; i++) { // Gui.findAll belongs on the test thread, not inside waitUntil
                Gui.waitTicks(10);
                closed = !Gui.findAll(n -> n.visible() && "Market Closed".equals(n.text())).isEmpty();
            }
            Gui.waitTicks(DATA_TICKS);
            return closed ? null : "no Market Closed banner on wheat at 20:00";
        });
    }

    /** The Active tab with the fixture's three running events. */
    private TestResult test_newsActive() {
        return shot("stockmarket/news/active.png", () -> newsTab("Active"));
    }

    // ---------------------------------------------------------------- GUI helpers

    /**
     * The plugin entry whose name label contains {@code keyword} (case-insensitive). Plugin names have no spaces
     * (NetFlowPlugin), which keeps setting labels such as "Capacity Volatility Scale" from matching.
     */
    private static GuiNode entry(String keyword) {
        return Gui.find(n -> "PluginEntryWidget".equals(n.type())
                && findIn(n, c -> "Label".equals(c.type()) && !c.text().contains(" ") && c.text().toLowerCase().contains(keyword)) != null,
                "plugin entry '" + keyword + "'");
    }

    /** Whether {@code n} is an ItemView showing {@code item} (empty item views have no stack). */
    static boolean shows(GuiNode n, Item item) {
        return n.handle() instanceof ItemView v && v.getItemStack() != null && v.getItemStack().is(item);
    }

    /** The visible ItemView showing {@code item}. */
    static GuiNode itemView(Item item) {
        return Gui.find(n -> n.visible() && shows(n, item), "item view " + item);
    }

    /** Clicks the first visible match; throws if there is none and {@code required}. */
    static void click(Predicate<GuiNode> filter, String what, boolean required) {
        List<GuiNode> found = Gui.findAll(n -> n.visible() && filter.test(n));
        if (!found.isEmpty()) Gui.click(found.get(0));
        else if (required) throw new IllegalStateException("no visible " + what);
    }

    static @Nullable GuiNode findIn(GuiNode root, Predicate<GuiNode> filter) {
        List<GuiNode> found = new ArrayList<>();
        root.forEach(n -> {
            if (found.isEmpty() && filter.test(n)) found.add(n);
        });
        return found.isEmpty() ? null : found.get(0);
    }

    /** Scrolls the list view that holds {@code node} so the node's row is at the top. */
    static void scrollTo(GuiNode node) {
        Gui.onClient(() -> {
            GuiElement row = (GuiElement) node.handle();
            GuiElement parent = row.getParent();
            while (parent != null && !(parent instanceof ListView)) {
                if (!(parent.getParent() instanceof ListView)) row = parent; // stop below the list's scroll container
                parent = parent.getParent();
            }
            if (parent instanceof ListView list) list.setScrollOffset(row.getTop() + list.getScrollOffset());
        });
        Gui.waitTicks(5);
    }
}
