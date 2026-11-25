package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.client.ClientCompat;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IPriceDataProvider;
import net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager;
import net.kroia.stockmarket.minecraft.block.StockMarketBlocks;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.stockmarket.networking.packet.MarketChangedPacket;
import net.kroia.stockmarket.screen.ManagementScreen;
import net.kroia.stockmarket.screen.TradeScreen;
import net.kroia.stockmarket.screen.custom.DisplayChartScreen;
import net.kroia.stockmarket.screen.custom.DisplayConfigScreen;
import net.kroia.stockmarket.screen.uiElements.trading_panel.InterMarketTradingPanel;
import net.kroia.stockmarket.screen.uiElements.trading_panel.TradingPanel;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.stockmarket.marketmanager.AsyncMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;
import net.kroia.testkit.gui.Gui;
import net.kroia.testkit.gui.GuiNode;
import net.kroia.testkit.gui.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * TK-3: drives the real trade screen (and, SM-362, the management screen's market grid) on the game client with
 * TestKit's {@link Gui} (a
 * {@link net.kroia.testkit.TestSuite.Side#CLIENT} suite: runs on the client's test thread after it joined, before
 * the server suites). Registered on the client only (DevBootstrap), so a dedicated server never loads it.
 * <p>
 * The trade screen is built from ModUtilities GuiElements, which TestKit sees only through ModUtilities'
 * {@link net.kroia.testkit.gui.GuiLocator}. Without it the screen shows no elements to the driver and every test
 * skips with "needs the ModUtilities GuiLocator".
 */
public class TradeScreenGuiTestSuite extends TestSuite {

    /** Client ticks to wait for the screen to open and for its market data to arrive. */
    private static final int OPEN_TICKS = 100, DATA_TICKS = 20;
    /** Wait for one server round trip (admin flag, ItemID, market create/delete). */
    private static final long WAIT_MS = 10_000;
    /** Item of the temporary market the +10 and management tests use (also used by CrossServerTradeScenarioSuite, never at the same time). */
    private static final ItemStack ITEM = new ItemStack(Items.DEAD_BUSH);

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.GUI;
    }

    @Override
    public Side side() {
        return Side.CLIENT;
    }

    @Override
    public void registerTests() {
        addTest("layout_full_hd_scale_2_and_3", this::test_layout);
        addTest("layout_market_closed", this::test_layoutMarketClosed); // SM-367
        addTest("chart_texts_do_not_overlap", this::test_chartTexts); // SM-367
        addTest("plus10_button_raises_quantity", this::test_plus10);
        addTest("management_no_duplicate_markets", this::test_management_no_duplicate_markets);
        addTest("display_chart_configure_button", this::test_display_chart_configure_button); // SM-360
        addTest("display_sneak_use_opens_setup", this::test_display_sneak_use_opens_setup); // SM-360
    }

    /**
     * SM-360: the full-screen display chart has a "Configure Display" button (top right, layout clean at Full HD
     * @2 and @3) that opens the display setup with the chart's market preselected. The screen is opened at a
     * position far above the player with no display there, so nothing is written back to a display.
     */
    private TestResult test_display_chart_configure_button() {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null) return skip("not in a world");
        return withTemporaryMarket(client, id -> {
            BlockPos pos = Gui.onClient(() -> Minecraft.getInstance().player.blockPosition().above(200));
            Gui.onClient(() -> ClientCompat.setScreen(new DisplayChartScreen(pos, id, null, pos.asLong(), null)));
            Gui.waitForScreen(DisplayChartScreen.class, OPEN_TICKS);
            Gui.waitTicks(1); // first layout
            if (Gui.findAll(n -> n.handle() instanceof GuiElement).isEmpty())
                return skip("needs the ModUtilities GuiLocator (no GuiElement nodes on the display chart screen)");
            List<Layout.Problem> scale2 = Gui.layoutProblems(1920, 1080, 2);
            TestResult r = assertTrue("1920x1080 @2: " + scale2, scale2.isEmpty());
            if (!r.passed()) return r;
            List<Layout.Problem> scale3 = Gui.layoutProblems(1920, 1080, 3);
            r = assertTrue("1920x1080 @3: " + scale3, scale3.isEmpty());
            if (!r.passed()) return r;

            String title = Gui.onClient(() -> Component.translatable("gui.stockmarket.display_config_screen.title").getString());
            List<GuiNode> buttons = Gui.findAll(n -> n.visible() && n.handle() instanceof Button && title.equals(n.text()));
            r = assertEquals("visible '" + title + "' buttons on the chart screen", 1, buttons.size());
            if (!r.passed()) return r;
            Gui.click(buttons.get(0));
            Gui.waitForScreen(DisplayConfigScreen.class, OPEN_TICKS);
            return assertSetupPreselects(id);
        });
    }

    /**
     * SM-360: on a configured display, right-click opens the chart and sneak + right-click (vanilla only lets an
     * empty-handed sneak reach the block) opens the setup with the display's market preselected. Calls the
     * display block's use directly with a stand-in player whose sneak flag is set. Needs the integrated server
     * (sp) to place the display next to the player.
     */
    private TestResult test_display_sneak_use_opens_setup() {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null) return skip("not in a world");
        MinecraftServer server = Gui.onClient(() -> Minecraft.getInstance().getSingleplayerServer());
        if (server == null) return skip("needs the integrated server (singleplayer) to place a display");
        return withTemporaryMarket(client, id -> {
            BlockPos pos = Gui.onClient(() -> Minecraft.getInstance().player.blockPosition().above(3));
            BlockState display = StockMarketBlocks.STOCKMARKET_DISPLAY_BLOCK.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
            BlockState before = await(server.submit(() -> server.overworld().getBlockState(pos)), WAIT_MS);
            try {
                await(server.submit(() -> {
                    ServerLevel level = server.overworld();
                    level.setBlock(pos, display, Block.UPDATE_ALL);
                    if (level.getBlockEntity(pos) instanceof StockMarketDisplayBlockEntity d)
                        d.setConfig(StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART, id);
                }), WAIT_MS);
                if (!Gui.waitUntil(() -> Minecraft.getInstance().level != null
                        && Minecraft.getInstance().level.getBlockEntity(pos) instanceof StockMarketDisplayBlockEntity d
                        && d.isActive() && id.equals(d.getSelectedItemID()), OPEN_TICKS))
                    return fail("the configured display at " + pos + " did not reach the client");

                Gui.onClient(() -> useDisplay(pos, true));
                Gui.waitForScreen(DisplayConfigScreen.class, DATA_TICKS);
                TestResult r = assertSetupPreselects(id);
                if (!r.passed()) return r;
                Gui.close();

                Gui.onClient(() -> useDisplay(pos, false));
                Gui.waitForScreen(DisplayChartScreen.class, DATA_TICKS);
                return pass("sneak + right-click opened the setup, right-click the chart");
            } finally {
                Gui.close();
                await(server.submit(() -> { server.overworld().setBlock(pos, before, Block.UPDATE_ALL); }), WAIT_MS);
            }
        });
    }

    /** Right-clicks the display at {@code pos} with empty hands as a stand-in player; call on the client thread. */
    private static void useDisplay(BlockPos pos, boolean sneaking) {
        Minecraft mc = Minecraft.getInstance();
        RemotePlayer player = new RemotePlayer(mc.level, mc.player.getGameProfile());
        player.setShiftKeyDown(sneaking);
        mc.level.getBlockState(pos).useWithoutItem(mc.level, player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
    }

    /** The open display setup screen shows {@code id} as the selected market. */
    private TestResult assertSetupPreselects(ItemID id) {
        String selected = Gui.onClient(() -> Component.translatable("gui.stockmarket.display_config_screen.selected",
                id.getStack().getHoverName().getString()).getString());
        return assertTrue("setup screen shows '" + selected + "'",
                !Gui.findAll(n -> n.visible() && "Label".equals(n.type()) && selected.equals(n.text())).isEmpty());
    }

    /** Opens the trade screen on the client thread (its constructor touches client state) and waits for it. */
    private static @Nullable String open() {
        Gui.onClient(() -> TradeScreen.openScreen());
        Gui.waitForScreen(TradeScreen.class, OPEN_TICKS);
        Gui.waitTicks(DATA_TICKS);
        if (Gui.findAll(n -> n.handle() instanceof GuiElement).isEmpty())
            return "needs the ModUtilities GuiLocator (no GuiElement nodes on the trade screen)";
        return null;
    }

    /**
     * No element outside the screen, no overlapping siblings and no clipped text, at Full HD (1920x1080) with GUI
     * scale 2 and with GUI scale 3. Full HD is the smallest window size the layout is checked at.
     */
    private TestResult test_layout() {
        try {
            String skip = open();
            if (skip != null) return skip(skip);
            List<Layout.Problem> scale2 = Gui.layoutProblems(1920, 1080, 2);
            TestResult r = assertTrue("1920x1080 @2: " + scale2, scale2.isEmpty());
            if (!r.passed()) return r;
            List<Layout.Problem> scale3 = Gui.layoutProblems(1920, 1080, 3);
            return assertTrue("1920x1080 @3: " + scale3, scale3.isEmpty());
        } finally {
            Gui.close();
        }
    }

    /**
     * SM-367: with the market closed, the "Market Closed" label has its own row above the trading panel (it used
     * to cover the panel's tab row), so the layout check finds nothing at 1920x1080 with GUI scale 2 and 3. The
     * temporary market is closed with {@code /stockmarket close} (master only, see {@link #withTemporaryMarket}).
     */
    private TestResult test_layoutMarketClosed() {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null) return skip("not in a world");
        return withTemporaryMarket(client, id -> {
            Gui.sendCommand("stockmarket close " + id.getShort());
            Gui.onClient(() -> TradeScreen.openScreen(id));
            Gui.waitForScreen(TradeScreen.class, OPEN_TICKS);
            if (Gui.findAll(n -> n.handle() instanceof GuiElement).isEmpty())
                return skip("needs the ModUtilities GuiLocator (no GuiElement nodes on the trade screen)");
            String closed = Gui.onClient(() -> Component.translatable("gui.stockmarket.trade_screen.market_closed").getString());
            boolean shown = false;
            for (int t = 0; t < OPEN_TICKS && !shown; t++) { // Gui.findAll belongs on the test thread
                Gui.waitTicks(1);
                shown = !Gui.findAll(n -> n.visible() && closed.equals(n.text())).isEmpty();
            }
            if (!shown) return fail("no '" + closed + "' label within " + OPEN_TICKS + " ticks of closing market " + id
                    + "; trading area: " + describeTradingArea());
            List<Layout.Problem> scale2 = Gui.layoutProblems(1920, 1080, 2);
            TestResult r = assertTrue("market closed, 1920x1080 @2: " + scale2, scale2.isEmpty());
            if (!r.passed()) return r;
            List<Layout.Problem> scale3 = Gui.layoutProblems(1920, 1080, 3);
            return assertTrue("market closed, 1920x1080 @3: " + scale3, scale3.isEmpty());
        });
    }

    /**
     * SM-367: the chart's own texts (price labels, current-price badge, time and date labels) never overlap and
     * stay inside the chart. The layout check cannot see them (the chart paints them itself), so the chart reports
     * the boxes it drew ({@link CandlestickChart#getDrawnTextBoxes()}). Runs on fixed candles (no market needed) at
     * the trade screen's chart size and at the news mini chart's size, with GUI scale 2 and 3. The current price
     * sits just under a grid line, so the badge must push that line's label aside, and the shown candles cross
     * midnight, so a date row is drawn.
     */
    private TestResult test_chartTexts() {
        int oldScale = Gui.guiScale();
        try {
            for (int scale : new int[]{2, 3}) {
                Gui.setGuiScale(scale);
                for (boolean mini : new boolean[]{false, true}) {
                    ChartScreen screen = Gui.onClient(() -> new ChartScreen(mini, 157.95));
                    Gui.open(screen);
                    Gui.waitTicks(10);
                    String problem = Gui.onClient(() -> textBoxProblem(screen.chart));
                    if (problem != null)
                        return fail("GUI scale " + scale + (mini ? ", mini chart: " : ", trade chart: ") + problem);
                }
            }
            return pass("no overlapping chart texts at GUI scale 2 and 3");
        } finally {
            Gui.close();
            Gui.setGuiScale(oldScale);
        }
    }

    /** @return why the chart's drawn texts are wrong (too few, outside the chart, overlapping), or null */
    private static @Nullable String textBoxProblem(CandlestickChart chart) {
        List<Rectangle> boxes = chart.getDrawnTextBoxes();
        if (boxes.size() < 5) return "only " + boxes.size() + " chart texts drawn";
        for (int i = 0; i < boxes.size(); i++) {
            Rectangle a = boxes.get(i);
            if (a.x < 0 || a.y < 0 || a.x + a.width > chart.getWidth() || a.y + a.height > chart.getHeight())
                return "text " + box(a) + " outside the " + chart.getWidth() + "x" + chart.getHeight() + " chart";
            for (int j = i + 1; j < boxes.size(); j++) {
                Rectangle b = boxes.get(j);
                if (a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height)
                    return "texts " + box(a) + " and " + box(b) + " overlap";
            }
        }
        return null;
    }

    private static String box(Rectangle r) {
        return "[" + r.x + "," + r.y + " " + r.width + "x" + r.height + "]";
    }

    /**
     * A screen with one chart on fixed 5-minute candles (18:00 to 04:00 local time, prices 150-160).
     * {@code mini} gives the chart the news mini chart's size, otherwise the trade screen's.
     */
    static final class ChartScreen extends StockMarketGuiScreen {
        final CandlestickChart chart = new CandlestickChart();
        private final boolean mini;

        /** @param currentPrice the live price (close of the newest candle) */
        ChartScreen(boolean mini, double currentPrice) {
            super(Component.literal("Chart test"));
            this.mini = mini;
            addElement(chart);
            long start = LocalDate.of(2026, 11, 24).atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            List<PriceHistoryData.Candle> candles = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                long open = 15500 + Math.round(450 * Math.sin(i / 9.0));
                long close = 15500 + Math.round(450 * Math.sin((i + 1) / 9.0));
                candles.add(new PriceHistoryData.Candle(start + i * 300_000L, open,
                        Math.max(open, close) + 20, Math.min(open, close) - 20, 10 + i % 7));
            }
            PriceHistoryData data = new PriceHistoryData(ItemID.INVALID_ID, 100, candles, Math.round(currentPrice * 100));
            String key = "test:chart:" + System.identityHashCode(data); // own viewport, nothing restored from an earlier run
            chart.setPriceDataProvider(new IPriceDataProvider() {
                @Override public PriceHistoryData getPriceHistoryData(long candleTimeDelta) { return data; }
                @Override public double getCurrentMarketRealPrice() { return data.getCurrentMarketRealPrice(); }
                @Override public ItemID getItemID() { return ItemID.INVALID_ID; }
                @Override public String getViewportKey() { return key; }
            });
        }

        @Override
        protected void updateLayout(net.kroia.modutilities.gui.Gui gui) {
            int w = getWidth() - 8, h = getHeight() - 8;
            if (mini) chart.setBounds(4, 4, Math.max(160, w / 4), 90); // NewsPluginGuiElement: height max(90, 30%)
            else chart.setBounds(4, 4, w * 3 / 4 - w / 10, h * 2 / 3); // TradeScreen.updateLayout
        }
    }

    /**
     * Clicking the shown order panel's "+10" button raises the quantity in that panel's amount box by 10.
     * <p>
     * The order panels are hidden while no market is shown (an --only run starts in a world without markets), so the
     * test runs on a temporary market for {@link #ITEM} ({@link #withTemporaryMarket}).
     * <p>
     * On an untrusted slave the order panels are hidden for players (T-123) and the master answers no admin-flag
     * read, so nothing is clicked or awaited there: the test checks the hidden state instead
     * ({@link #assertUntrustedSlaveHidesPlus10()}). On a trusted slave the test skips (see
     * {@link #withTemporaryMarket}).
     */
    private TestResult test_plus10() {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null) return skip("not in a world");
        if (Gui.onClient(StockMarketGuiElement::isUntrustedSlave))
            return assertUntrustedSlaveHidesPlus10();
        return withTemporaryMarket(client, id -> {
            Gui.onClient(() -> TradeScreen.openScreen(id)); // preselects the market
            Gui.waitForScreen(TradeScreen.class, OPEN_TICKS);
            if (Gui.findAll(n -> n.handle() instanceof GuiElement).isEmpty())
                return skip("needs the ModUtilities GuiLocator (no GuiElement nodes on the trade screen)");
            // the order panels show once the market's trading status (price stream) says it is open
            GuiNode[] found = null;
            for (int t = 0; t < OPEN_TICKS && (found = findPlus10()) == null; t++)
                Gui.waitTicks(1);
            if (found == null) return fail("no shown '+10' button with an amount box in its panel within " + OPEN_TICKS
                    + " ticks of opening market " + id + "; trading area: " + describeTradingArea());
            GuiNode button = found[0];
            TextBox box = (TextBox) found[1].handle();
            double before = Gui.onClient(box::getDouble);
            Gui.click(button);
            double after = Gui.onClient(box::getDouble);
            return assertEquals("amount after +10 (was " + before + ")", before + 10, after);
        });
    }

    /**
     * SM-362: overlapping market-list refreshes list each market only once in the Management screen's Overview grid.
     * One "Create selected" starts one refresh per created market (ADDED broadcast) plus one from the Create tab, so
     * answers arrive while other requests are still in flight; the old grid appended a full copy of the list per
     * answer. The test opens the screen with a temporary market ({@link #withTemporaryMarket}), fires two ADDED
     * refreshes back to back on the client thread and checks that the market's icon is in the grid exactly once.
     */
    private TestResult test_management_no_duplicate_markets() {
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null) return skip("not in a world");
        return withTemporaryMarket(client, id -> {
            Gui.onClient(ManagementScreen::openScreen);
            ManagementScreen screen = Gui.waitForScreen(ManagementScreen.class, OPEN_TICKS);
            if (Gui.findAll(n -> n.handle() instanceof GuiElement).isEmpty())
                return skip("needs the ModUtilities GuiLocator (no GuiElement nodes on the management screen)");
            // the grid fills once the screen's own market-list request is answered
            for (int t = 0; t < OPEN_TICKS && marketIcons() == 0; t++)
                Gui.waitTicks(1);
            TestResult r = assertEquals("grid icons of market " + id + " after opening", 1, marketIcons());
            if (!r.passed()) return r;
            Gui.onClient(() -> {
                screen.onMarketsChanged(MarketChangedPacket.Kind.ADDED, null);
                screen.onMarketsChanged(MarketChangedPacket.Kind.ADDED, null);
            });
            Gui.waitTicks(DATA_TICKS); // both answers arrive
            return assertEquals("grid icons of market " + id + " after two overlapping refreshes", 1, marketIcons());
        });
    }

    /**
     * @return how many icons of the Management screen's Overview market grid show {@link #ITEM}. Counts only
     * {@code MarketItemView}s: the Create tab's preset grid holds other ItemViews.
     */
    private static int marketIcons() {
        List<GuiNode> icons = Gui.findAll(n -> "MarketItemView".equals(n.type()));
        return Gui.onClient(() -> (int) icons.stream()
                .filter(n -> ((ItemView) n.handle()).getItemStack().is(ITEM.getItem())).count());
    }

    /**
     * Runs {@code body} with a temporary market for {@link #ITEM}, created the way the management screen does: the
     * player gets the StockMarket admin flag with {@code /stockmarket op} (the autotest player is an op; master only)
     * and the create request goes out from the client. Before {@code body} runs, the client has the market cached.
     * In {@code finally}, also when the body fails, the open screen is closed, the market deleted and the admin flag
     * reset.
     * <p>
     * Skips on a slave before any admin read: {@code /stockmarket op} is master-only, so neither the admin flag nor
     * the market can be set there. The slave is detected from the join-synced client setting
     * ({@link StockMarketGuiElement#isMasterServer()}).
     *
     * @param client the client instances (the player is in a world)
     * @param body   the test; gets the market's ItemID, its result is returned
     */
    private TestResult withTemporaryMarket(StockMarketModBackend.ClientInstances client, Function<ItemID, TestResult> body) {
        // join-synced client setting, no server round trip; /stockmarket op answers command_master_only on a slave
        if (!Gui.onClient(StockMarketGuiElement::isMasterServer))
            return skip("/stockmarket op is master-only: no admin flag or temporary market on a slave");
        IAsyncMarketManager async = AsyncMarketManager.createClientManager(); // admin flag + exists checks
        UUID self = Gui.onClient(() -> Minecraft.getInstance().player.getUUID());
        boolean wasAdmin = Boolean.TRUE.equals(await(Gui.onClient(() -> async.isStockmarketAdminAsync(self)), WAIT_MS));
        ItemID market = null;
        try {
            if (!wasAdmin) {
                Gui.sendCommand("stockmarket op");
                boolean admin = false;
                for (int i = 0; i < 20 && !admin; i++) { // the command runs on the server: poll its effect
                    Gui.waitTicks(5);
                    admin = Boolean.TRUE.equals(await(Gui.onClient(() -> async.isStockmarketAdminAsync(self)), WAIT_MS));
                }
                if (!admin)
                    return skip("no StockMarket admin flag after /stockmarket op (a slave refuses it: master only)");
            }
            market = await(Gui.onClient(() -> ItemID.getOrRegisterFromItemStackClientSide(ITEM)), WAIT_MS);
            ItemID id = market;
            // a run aborted before its cleanup leaves the market behind: delete it so the create below succeeds
            if (Boolean.TRUE.equals(await(Gui.onClient(() -> async.marketExistsAsync(id)), WAIT_MS)))
                await(Gui.onClient(() -> client.MARKET_MANAGER.requestDeleteMarket(id)), WAIT_MS);
            if (!Boolean.TRUE.equals(await(Gui.onClient(() -> client.MARKET_MANAGER.requestCreateMarket(id)), WAIT_MS)))
                return fail("temporary market for " + id + " not created");
            // the create normally caches the market; if its factor read failed, the market list request loads it
            if (!Gui.waitUntil(() -> client.MARKET_MANAGER.getMarket(id) != null, DATA_TICKS)) {
                Gui.onClient(() -> { client.MARKET_MANAGER.requestMarkets(); });
                if (!Gui.waitUntil(() -> client.MARKET_MANAGER.getMarket(id) != null, OPEN_TICKS))
                    return fail("market " + id + " created but never reached the client's market list");
            }
            return body.apply(id);
        } finally {
            Gui.close();
            ItemID id = market;
            if (id != null) {
                try {
                    await(Gui.onClient(() -> client.MARKET_MANAGER.requestDeleteMarket(id)), WAIT_MS);
                } catch (RuntimeException ignored) {
                    // best effort; the admin flag below is still reset
                }
            }
            if (!wasAdmin)
                Gui.sendCommand("stockmarket deop");
        }
    }

    /**
     * Untrusted slave: the trade screen shows the untrusted banner and no visible "+10" button. Opens the screen
     * without a preselected market (no market can be created here); the panels are disabled at construction
     * regardless of the market.
     */
    private TestResult assertUntrustedSlaveHidesPlus10() {
        try {
            String skip = open();
            if (skip != null) return skip(skip);
            String banner = Gui.onClient(() -> Component.translatable("gui.stockmarket.untrusted_slave.banner_line1").getString());
            TestResult r = assertTrue("untrusted-slave banner shown; trading area: " + describeTradingArea(),
                    !Gui.findAll(n -> n.visible() && "Label".equals(n.type()) && banner.equals(n.text())).isEmpty());
            if (!r.passed()) return r;
            return assertNull("no visible '+10' button on an untrusted slave", findPlus10());
        } finally {
            Gui.close();
        }
    }

    /** Diagnostic: subtrees of the trading panels and the closed/maintenance banner, capped at ~3000 chars. */
    static String describeTradingArea() { // also used by ScreenTradeClientSteps
        StringBuilder sb = new StringBuilder();
        for (GuiNode n : Gui.findAll(n -> n.handle() instanceof TradingPanel || n.handle() instanceof InterMarketTradingPanel
                || ("Label".equals(n.type()) && (n.text().contains("Closed") || n.text().contains("Maintenance"))))) {
            describe(n, 0, sb);
            sb.append(" | ");
        }
        if (sb.isEmpty()) sb.append("none found; roots: ").append(Gui.nodes());
        return sb.length() > 3000 ? sb.substring(0, 3000) + "..." : sb.toString();
    }

    private static void describe(GuiNode n, int depth, StringBuilder sb) {
        sb.append(n.type());
        if (!n.text().isEmpty()) sb.append(" '").append(n.text()).append("'");
        sb.append(" v=").append(n.visible() ? 1 : 0)
                .append(" [").append(n.x()).append(',').append(n.y()).append(' ').append(n.width()).append('x').append(n.height()).append(']');
        if (depth >= 5 || n.children().isEmpty()) return;
        sb.append(" {");
        for (GuiNode c : n.children()) {
            sb.append(' ');
            describe(c, depth + 1, sb);
        }
        sb.append(" }");
    }

    /** @return the first shown "+10" button and the amount box of the same panel, or null */
    private static GuiNode @Nullable [] findPlus10() {
        for (GuiNode root : Gui.nodes()) {
            GuiNode[] found = findPlus10(root);
            if (found != null) return found;
        }
        return null;
    }

    /** Depth first over shown nodes only: a hidden panel's buttons do not count. */
    private static GuiNode @Nullable [] findPlus10(GuiNode panel) {
        if (!panel.visible()) return null;
        for (GuiNode child : panel.children()) {
            if (child.visible() && child.handle() instanceof Button && "+10".equals(child.text())) {
                GuiNode[] box = {null};
                panel.forEach(n -> {
                    if (box[0] == null && n.visible() && n.handle() instanceof TextBox) box[0] = n;
                });
                if (box[0] != null) return new GuiNode[]{child, box[0]};
            }
        }
        for (GuiNode child : panel.children()) {
            GuiNode[] found = findPlus10(child);
            if (found != null) return found;
        }
        return null;
    }
}
