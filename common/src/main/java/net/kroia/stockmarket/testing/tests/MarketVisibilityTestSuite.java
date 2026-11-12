package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.MoneyFormat;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager;
import net.kroia.stockmarket.minecraft.compat.StockMarketPlaceholders;
import net.kroia.stockmarket.stockmarket.market.AsyncMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.AsyncMarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.ClientMarketManager;
import net.kroia.stockmarket.networking.packet.MarketChangedPacket.Kind;
import net.kroia.stockmarket.stockmarket.market.MarketSettings.TradingState;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.testing.TestFixture;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.networking.request.OrderHistoryRequest;
import net.kroia.stockmarket.networking.request.PluginListRequest;
import net.kroia.stockmarket.networking.request.TransactionHistoryRequest;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * T-237: a slave hides markets for items it cannot resolve (items whose mod only the master has).
 * <p>
 * {@link MultiServerUtils#setUnresolvableForTesting} makes a real item of this server's registry look
 * missing, so no second mod loadout is needed. Items are only looked up, never registered (an untrusted
 * slave may not register items). The fake-transport tests run on both sides, the filters are the same code
 * there; the live market list test runs on a slave and the master test on the master. T-240 adds the
 * portfolio placeholder, which a slave sums without those markets. SM-283 adds the slave's command
 * suggestions/arguments and the client screen gate (history, plugin icons); SM-309 the history and plugin list
 * results a slave side decodes.
 */
public class MarketVisibilityTestSuite extends TestSuite {

    /** Wait for a request to the master. */
    private static final long WAIT_MS = 10_000;

    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;

    /**
     * @param backend the instances of the running server, master or slave
     */
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.MARKET_VISIBILITY;
    }

    @Override
    public void registerTests() {
        addTest("async_manager_hides_unresolvable_market", this::test_asyncManagerHidesUnresolvableMarket);
        addTest("client_manager_hides_unresolvable_market_and_pair", this::test_clientManagerHidesUnresolvableMarketAndPair);
        addTest("put_order_refused_without_forwarding", this::test_putOrderRefusedWithoutForwarding);
        addTest("slave_list_hides_unresolvable_master_market", this::test_slaveListHidesUnresolvableMasterMarket);
        addTest("master_still_lists_unresolvable_market", this::test_masterStillListsUnresolvableMarket);
        addTest("slave_portfolio_excludes_unresolvable_market", this::test_slavePortfolioExcludesUnresolvableMarket);
        addTest("client_adds_created_market_without_request", this::test_clientAddsCreatedMarketWithoutRequest);
        addTest("slave_commands_hide_unresolvable_market", this::test_slaveCommandsHideUnresolvableMarket);
        addTest("client_screen_gate_hides_unresolvable_market_on_slave", this::test_clientScreenGateHidesUnresolvableMarketOnSlave);
        addTest("request_results_leave_out_unresolvable_market_on_slave", this::test_requestResultsLeaveOutUnresolvableMarketOnSlave);
    }

    /**
     * @return two items this server resolves, from the live registry (lookup only); null if it has fewer
     */
    private static @Nullable List<ItemID> twoResolvableItems() {
        List<ItemID> items = ItemIDManager.getItemIDMap().keySet().stream()
                .filter(MultiServerUtils::isMarketResolvableOnThisServer)
                .limit(2)
                .toList();
        return items.size() == 2 ? items : null;
    }

    /** The slave's market list (commands, tab completion) leaves out the hidden market and keeps the other one. */
    private TestResult test_asyncManagerHidesUnresolvableMarket() {
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID shown = items.get(0);
        ItemID hidden = items.get(1);
        AsyncMarketManager manager = AsyncMarketManager.createForTesting(input -> CompletableFuture.completedFuture(
                AsyncMarketManager.OutputData.of(input.function, List.of(shown, hidden))));
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            return assertEquals("only the resolvable market is listed", List.of(shown),
                    manager.getAvailableMarketIDsAsync().join());
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /**
     * The client gets no market object for the hidden market, so every market list leaves it out and no
     * item-for-item pair with it exists. The resolvable market is still there.
     */
    private TestResult test_clientManagerHidesUnresolvableMarketAndPair() {
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID shown = items.get(0);
        ItemID hidden = items.get(1);
        AsyncMarketManager creating = AsyncMarketManager.createForTesting(input -> CompletableFuture.completedFuture(
                AsyncMarketManager.OutputData.of(input.function, true)));
        ClientMarketManager manager = ClientMarketManager.createForTesting(creating, () -> CompletableFuture.completedFuture(1));
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            manager.requestCreateMarket(shown).join();
            manager.requestCreateMarket(hidden).join();
            TestResult r = assertTrue("resolvable market listed", manager.getAvailableMarkets().contains(shown));
            if (!r.passed()) return r;
            r = assertFalse("unresolvable market not listed", manager.getAvailableMarkets().contains(hidden));
            if (!r.passed()) return r;
            r = assertNull("no market object for it", manager.getMarket(hidden));
            if (!r.passed()) return r;
            return assertNull("no item-for-item pair with it", manager.getCrossRateMarket(shown, hidden));
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /** An order for the hidden market is refused at once and never sent; the resolvable market still sends. */
    private TestResult test_putOrderRefusedWithoutForwarding() {
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID shown = items.get(0);
        ItemID hidden = items.get(1);
        AtomicInteger sends = new AtomicInteger();
        Function<AsyncMarket.InputData, CompletableFuture<AsyncMarket.OutputData>> transport = input -> {
            sends.incrementAndGet();
            return CompletableFuture.completedFuture(AsyncMarket.OutputData.of(input.function, true));
        };
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            CompletableFuture<Boolean> refused = AsyncMarket.createForTesting(hidden, transport).putOrderAsync(order(hidden));
            TestResult r = assertTrue("answered at once", refused.isDone());
            if (!r.passed()) return r;
            r = assertEquals("refused", false, refused.join());
            if (!r.passed()) return r;
            r = assertEquals("not sent", 0, sends.get());
            if (!r.passed()) return r;
            r = assertEquals("resolvable market still sends", true,
                    AsyncMarket.createForTesting(shown, transport).putOrderAsync(order(shown)).join());
            if (!r.passed()) return r;
            return assertEquals("sent once", 1, sends.get());
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /** Slave only: the master's market list, as this slave reads it, leaves out a market this slave cannot resolve. */
    private TestResult test_slaveListHidesUnresolvableMasterMarket() {
        if (BACKEND_INSTANCES == null || !BACKEND_INSTANCES.MARKET_MANAGER.isSlave())
            return skip("slave only");
        if (!MultiServerUtils.canInteractWithStockMarket())
            return skip("not connected to the master");
        IAsyncMarketManager manager = BACKEND_INSTANCES.MARKET_MANAGER.getAsync();
        List<ItemID> listed = await(manager.getAvailableMarketIDsAsync(), WAIT_MS);
        if (listed == null || listed.isEmpty())
            return skip("the master has no market this slave can resolve");
        ItemID hidden = listed.get(0);
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            return assertFalse("market for an item this slave cannot resolve not listed",
                    await(manager.getAvailableMarketIDsAsync(), WAIT_MS).contains(hidden));
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /** Master only: the master lists a market even if its item counts as unresolvable; nothing is hidden there. */
    private TestResult test_masterStillListsUnresolvableMarket() {
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.MARKET_MANAGER.isSlave())
            return skip("master only");
        TestFixture fixture = new TestFixture("MarketVisibility");
        fixture.setBackend(BACKEND_INSTANCES);
        ItemID marketID = null;
        try {
            marketID = fixture.createMarket("hidden").getItemID();
            MultiServerUtils.setUnresolvableForTesting(marketID, true);
            TestResult r = assertTrue("MarketsRequest lists it",
                    BACKEND_INSTANCES.NETWORKING.MARKETS_REQUEST.handleOnMasterServer(0, "", null).join().contains(marketID));
            if (!r.passed()) return r;
            return assertTrue("the market manager lists it",
                    await(BACKEND_INSTANCES.MARKET_MANAGER.getAsync().getAvailableMarketIDsAsync(), WAIT_MS).contains(marketID));
        } finally {
            if (marketID != null)
                MultiServerUtils.setUnresolvableForTesting(marketID, false);
            fixture.cleanup();
        }
    }

    /**
     * T-240 / SM-247: the portfolio and open_orders placeholders the master computes keep every market; the slave's
     * copy sums them again without the market it cannot resolve (an inter-market order touching it is left out
     * too), and keeps the master's values when every market resolves.
     */
    private TestResult test_slavePortfolioExcludesUnresolvableMarket() {
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID shown = items.get(0);
        ItemID hidden = items.get(1);
        // 2 orders on the shown market, 3 on the hidden one, 1 inter-market order between both
        Map<String, Integer> orders = Map.of(
                StockMarketPlaceholders.openOrdersPartKey(shown), 2,
                StockMarketPlaceholders.openOrdersPartKey(hidden), 3,
                StockMarketPlaceholders.openOrdersPartKey(shown, hidden), 1);
        Map<String, String> master = StockMarketPlaceholders.playerValueMap(orders, Map.of(shown, 300L, hidden, 700L));
        TestResult r = assertTrue("all markets resolve: master values kept",
                StockMarketPlaceholders.withoutUnresolvableMarkets(master) == master);
        if (!r.passed()) return r;
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            r = assertEquals("master keeps both markets", MoneyFormat.formatMoneyRaw(1000), master.get("portfolio_raw"));
            if (!r.passed()) return r;
            r = assertEquals("master counts every open order", "6", master.get("open_orders"));
            if (!r.passed()) return r;
            Map<String, String> slave = StockMarketPlaceholders.withoutUnresolvableMarkets(master);
            r = assertEquals("slave open_orders without the hidden market", "2", slave.get("open_orders"));
            if (!r.passed()) return r;
            r = assertEquals("slave open_orders_raw without the hidden market", "2", slave.get("open_orders_raw"));
            if (!r.passed()) return r;
            r = assertEquals("slave raw value without the hidden market", MoneyFormat.formatMoneyRaw(300), slave.get("portfolio_raw"));
            if (!r.passed()) return r;
            return assertEquals("slave formatted value without the hidden market", MoneyFormat.formatMoney(300), slave.get("portfolio"));
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /**
     * SM-304/SM-310: the MarketChangedPacket handler path ({@code onMarketChanged}) lists a market created on the
     * server (ADDED) without a markets request and tells the open screen once; a repeat is a no-op, and a market
     * this side cannot resolve is skipped without a screen refresh. CLOSED/MAINTENANCE_ON/OFF/OPENED then update
     * the ClientMarket's trading state (no price stream running) and REMOVED drops it, each with one screen notice.
     */
    private TestResult test_clientAddsCreatedMarketWithoutRequest() {
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID shown = items.get(0);
        ItemID hidden = items.get(1);
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger notified = new AtomicInteger();
        AsyncMarketManager transport = AsyncMarketManager.createForTesting(input -> {
            sends.incrementAndGet();
            return CompletableFuture.completedFuture(AsyncMarketManager.OutputData.of(input.function, true));
        });
        ClientMarketManager manager = ClientMarketManager.createForTesting(transport,
                () -> CompletableFuture.completedFuture(1), notified::incrementAndGet);
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            manager.onMarketChanged(shown, Kind.ADDED);
            TestResult r = assertTrue("created market listed", manager.getAvailableMarkets().contains(shown));
            if (!r.passed()) return r;
            r = assertEquals("open screen told once", 1, notified.get());
            if (!r.passed()) return r;
            manager.onMarketChanged(shown, Kind.ADDED);
            r = assertEquals("repeat is a no-op", 1, notified.get());
            if (!r.passed()) return r;
            manager.onMarketChanged(hidden, Kind.ADDED);
            r = assertFalse("unresolvable market not listed", manager.getAvailableMarkets().contains(hidden));
            if (!r.passed()) return r;
            r = assertEquals("no refresh for it", 1, notified.get());
            if (!r.passed()) return r;
            // SM-310: state changes reach the cached ClientMarket
            TradingState open = TradingState.OPEN, closed = TradingState.CLOSED, maintenance = TradingState.MAINTENANCE;
            r = assertEquals("unknown state reads open", open, manager.getMarket(shown).getTradingState());
            if (!r.passed()) return r;
            manager.onMarketChanged(shown, Kind.CLOSED);
            r = assertEquals("CLOSED applied", closed, manager.getMarket(shown).getTradingState());
            if (!r.passed()) return r;
            manager.onMarketChanged(shown, Kind.MAINTENANCE_ON);
            r = assertEquals("MAINTENANCE_ON applied", maintenance, manager.getMarket(shown).getTradingState());
            if (!r.passed()) return r;
            manager.onMarketChanged(shown, Kind.MAINTENANCE_OFF);
            r = assertEquals("MAINTENANCE_OFF back to closed", closed, manager.getMarket(shown).getTradingState());
            if (!r.passed()) return r;
            manager.onMarketChanged(shown, Kind.OPENED);
            r = assertEquals("OPENED applied", open, manager.getMarket(shown).getTradingState());
            if (!r.passed()) return r;
            manager.onMarketChanged(hidden, Kind.CLOSED);
            r = assertEquals("screen told per state change, not for an unlisted market", 5, notified.get());
            if (!r.passed()) return r;
            manager.onMarketChanged(shown, Kind.REMOVED);
            r = assertFalse("REMOVED drops the market", manager.getAvailableMarkets().contains(shown));
            if (!r.passed()) return r;
            return assertEquals("nothing sent to the server", 0, sends.get());
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /**
     * SM-283, slave only: the real {@code /stockmarket} dispatcher neither suggests a market this slave cannot
     * resolve ({@code open}, {@code close} and the root {@code <market>} argument) nor accepts it
     * ({@code open <id>} answers "not found"). The master of a test cell keeps no markets, so like the other tests
     * here the slave's market manager is swapped for the real slave {@link AsyncMarketManager} on a fake transport
     * that lists two resolvable items; nothing reaches the master. Checks first that the market is suggested while
     * it resolves, so the hidden case cannot pass vacuously.
     */
    private TestResult test_slaveCommandsHideUnresolvableMarket() {
        if (BACKEND_INSTANCES == null || !BACKEND_INSTANCES.MARKET_MANAGER.isSlave())
            return skip("slave only");
        MinecraftServer server = getServer();
        if (server == null)
            return skip("no server");
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID hidden = items.get(1);
        String quotedName = "\"" + hidden.getName() + "\"";
        String numericID = String.valueOf(hidden.getShort());
        // a name shared by several markets stays suggested for the others; the numeric id is then suggested too
        boolean nameShared = items.get(0).getName().equals(hidden.getName());

        MarketManager real = BACKEND_INSTANCES.MARKET_MANAGER;
        BACKEND_INSTANCES.MARKET_MANAGER = MarketManager.createSlaveForTesting(AsyncMarketManager.createForTesting(input ->
                CompletableFuture.completedFuture(input.function == AsyncMarketManager.FunctionType.GetAvailableMarketIDs
                        ? AsyncMarketManager.OutputData.of(input.function, items)
                        : AsyncMarketManager.OutputData.of(input.function))));
        try {
            TestResult r = assertTrue("suggested while it resolves",
                    suggestionTexts(server, "stockmarket open ").contains(nameShared ? numericID : quotedName));
            if (!r.passed()) return r;

            MultiServerUtils.setUnresolvableForTesting(hidden, true);
            for (String input : List.of("stockmarket open ", "stockmarket close ", "stockmarket ")) {
                List<String> suggested = suggestionTexts(server, input);
                r = assertFalse("'" + input + "' does not suggest its numeric id", suggested.contains(numericID));
                if (!r.passed()) return r;
                if (!nameShared) {
                    r = assertFalse("'" + input + "' does not suggest its name", suggested.contains(quotedName));
                    if (!r.passed()) return r;
                }
            }

            List<Component> failures = new CopyOnWriteArrayList<>();
            CommandSource capture = new CommandSource() {
                @Override public void sendSystemMessage(Component component) { failures.add(component); }
                @Override public boolean acceptsSuccess() { return true; }
                @Override public boolean acceptsFailure() { return true; }
                @Override public boolean shouldInformAdmins() { return false; }
            };
            try {
                server.getCommands().getDispatcher().execute("stockmarket open " + numericID,
                        server.createCommandSourceStack().withSource(capture));
            } catch (CommandSyntaxException e) {
                return fail("command did not parse: " + e.getMessage());
            }
            pumpUntil(() -> !failures.isEmpty(), WAIT_MS);
            return assertTrue("'stockmarket open " + numericID + "' answers not found, got " + failures,
                    failures.stream().anyMatch(c -> hasTranslationKey(c, "message.stockmarket.command_market_remove_not_found")));
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
            BACKEND_INSTANCES.MARKET_MANAGER = real;
        }
    }

    /**
     * SM-309: the order history, trade history and plugin list results, decoded where they are received, leave out
     * a market this side cannot resolve on a slave (also a hidden leg's whole inter-market trade, and the market's
     * plugin custom settings); on the master they keep it. Encodes what the master would send and decodes it
     * through the real requests.
     */
    private TestResult test_requestResultsLeaveOutUnresolvableMarketOnSlave() {
        if (BACKEND_INSTANCES == null)
            return skip("no server instances");
        MinecraftServer server = getServer();
        if (server == null)
            return skip("no server");
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID shown = items.get(0);
        ItemID hidden = items.get(1);
        boolean slave = BACKEND_INSTANCES.MARKET_MANAGER.isSlave();
        UUID player = UUID.randomUUID();
        UUID mixedTrade = UUID.randomUUID();
        UUID shownTrade = UUID.randomUUID();
        List<OrderRecordStruct> records = List.of(
                new OrderRecordStruct(shown.getShort(), 1, player, 0, 5, 100, 1000),
                new OrderRecordStruct(hidden.getShort(), 1, player, 0, 5, 100, 1001),
                new OrderRecordStruct(shown.getShort(), 1, player, 0, -5, 100, 1002, mixedTrade),
                new OrderRecordStruct(hidden.getShort(), 1, player, 0, 5, 100, 1002, mixedTrade),
                new OrderRecordStruct(shown.getShort(), 1, player, 0, -5, 100, 1003, shownTrade),
                new OrderRecordStruct(shown.getShort(), 1, player, 0, 5, 100, 1003, shownTrade));
        List<OrderRecordStruct> visible = slave ? List.of(records.get(0), records.get(4), records.get(5)) : records;

        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            OrderHistoryRequest history = BACKEND_INSTANCES.NETWORKING.ORDER_HISTORY_REQUEST;
            RegistryFriendlyByteBuf buf = buffer(server);
            history.encodeOutput(buf, new OrderHistoryRequest.OutputData(records));
            TestResult r = assertEquals(slave ? "order history: slave leaves out the market and its inter-market trade"
                    : "order history: master keeps everything", visible, history.decodeOutput(buf).records());
            if (!r.passed()) return r;

            TransactionHistoryRequest trades = BACKEND_INSTANCES.NETWORKING.TRANSACTION_HISTORY_REQUEST;
            buf = buffer(server);
            trades.encodeOutput(buf, new TransactionHistoryRequest.OutputData(records));
            r = assertEquals(slave ? "trade history: slave leaves out the market" : "trade history: master keeps everything",
                    visible, trades.decodeOutput(buf).records());
            if (!r.passed()) return r;

            PluginListRequest plugins = BACKEND_INSTANCES.NETWORKING.PLUGIN_LIST_REQUEST;
            buf = buffer(server);
            Map<ItemID, byte[]> settings = new HashMap<>();
            settings.put(shown, new byte[]{1});
            settings.put(hidden, new byte[]{2});
            plugins.encodeOutput(buf, List.of(new PluginSyncData(pluginData(server), List.of(shown, hidden), settings)));
            List<PluginSyncData> decoded = plugins.decodeOutput(buf);
            r = assertEquals("one plugin", 1, decoded.size());
            if (!r.passed()) return r;
            r = assertEquals(slave ? "plugin list: slave leaves out the subscription" : "plugin list: master keeps it",
                    slave ? List.of(shown) : List.of(shown, hidden), decoded.get(0).getSubscribedMarkets());
            if (!r.passed()) return r;
            return assertEquals(slave ? "plugin list: slave leaves out its custom settings" : "plugin list: master keeps them",
                    slave ? Set.of(shown) : Set.of(shown, hidden), decoded.get(0).getCustomSettings().keySet());
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /** @return an empty network buffer bound to the server's registries */
    private static RegistryFriendlyByteBuf buffer(MinecraftServer server) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
    }

    /** @return generic plugin data as the master would send it (built through its codec: the type ID has no setter) */
    private static GenericPluginData pluginData(MinecraftServer server) {
        RegistryFriendlyByteBuf buf = buffer(server);
        ByteBufCodecs.STRING_UTF8.encode(buf, "test:plugin");
        UUIDUtil.STREAM_CODEC.encode(buf, UUID.randomUUID());
        ByteBufCodecs.STRING_UTF8.encode(buf, "name");
        ByteBufCodecs.STRING_UTF8.encode(buf, "description");
        ByteBufCodecs.BOOL.encode(buf, false);
        ByteBufCodecs.BOOL.encode(buf, true);
        ByteBufCodecs.BOOL.encode(buf, false);
        return GenericPluginData.STREAM_CODEC.decode(buf);
    }

    /**
     * SM-283: the gate every client screen uses to hide a market ({@link StockMarketGuiElement#isMarketVisible}:
     * order history panel, plugin market icons, management grid, trade screen orders). Needs a client in this JVM
     * (sp: master, spslave: slave); skips on a dedicated server.
     */
    private TestResult test_clientScreenGateHidesUnresolvableMarketOnSlave() {
        if (BACKEND_INSTANCES == null)
            return skip("no server instances");
        StockMarketModBackend.ClientInstances client = StockMarketModBackend.getClientInstances();
        if (client == null || client.SETTINGS == null)
            return skip("no client in this JVM");
        List<ItemID> items = twoResolvableItems();
        if (items == null)
            return skip("fewer than two resolvable items in the registry");
        ItemID shown = items.get(0);
        ItemID hidden = items.get(1);
        boolean slave = BACKEND_INSTANCES.MARKET_MANAGER.isSlave();
        MultiServerUtils.setUnresolvableForTesting(hidden, true);
        try {
            TestResult r = assertEquals("client knows whether it is on the master", !slave, StockMarketGuiElement.isMasterServer());
            if (!r.passed()) return r;
            r = assertTrue("resolvable market visible", StockMarketGuiElement.isMarketVisible(shown));
            if (!r.passed()) return r;
            return assertEquals(slave ? "slave screens hide the unresolvable market" : "master screens still show it",
                    !slave, StockMarketGuiElement.isMarketVisible(hidden));
        } finally {
            MultiServerUtils.setUnresolvableForTesting(hidden, false);
        }
    }

    /** @return the suggestion texts the real command dispatcher offers the console for {@code input} */
    private List<String> suggestionTexts(MinecraftServer server, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        ParseResults<CommandSourceStack> parse = dispatcher.parse(input, server.createCommandSourceStack());
        return await(dispatcher.getCompletionSuggestions(parse), WAIT_MS).getList().stream().map(Suggestion::getText).toList();
    }

    /** @return true if {@code component} or one of its siblings is the translatable text {@code key} */
    private static boolean hasTranslationKey(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents t && t.getKey().equals(key))
            return true;
        return component.getSiblings().stream().anyMatch(s -> hasTranslationKey(s, key));
    }

    /** @return a market order for {@code marketID} */
    private static Order order(ItemID marketID) {
        return new Order(marketID, Order.Type.MARKET, 10, 100, 0, UUID.randomUUID(), 1);
    }
}
