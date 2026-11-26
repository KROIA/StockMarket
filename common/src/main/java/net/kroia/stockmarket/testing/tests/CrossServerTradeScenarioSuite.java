package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.bank.BankStatus;
import net.kroia.banksystem.api.bank.IAsyncBank;
import net.kroia.banksystem.api.bankmanager.IAsyncBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.SlaveTrustCache;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;
import net.kroia.testkit.players.FakePlayers;
import net.kroia.testkit.scenario.Scenario;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TK-6: a player trade across servers, written once with TestKit's {@link Scenario} and run by every server of a
 * two-slave setup (instances {@code master}, {@code slaveA}, {@code slaveB}). Without those instances (every other
 * cell) the scenario skips.
 * <p>
 * Story: the master sets up a clean market (plugins detached, virtual order book off, so the seller's ask is the
 * only liquidity) and funds two fake players; a seller on slaveA places a limit sell, a buyer on slaveB a limit buy
 * at the same price; the master then reads both filled orders from the order history and checks that the items and
 * the money moved between the two players' banks.
 * <p>
 * Every step runs on its server's thread ({@link Scenario} hops there), so the waits use {@link #await}, which keeps
 * the server's queued tasks running. Both slaves must be trusted by the master: an untrusted slave may not trade.
 */
public class CrossServerTradeScenarioSuite extends TestSuite {

    static final long WAIT_MS = 15_000;
    /** Fake names only: never a real player's. */
    private static final String SELLER = "ScenarioSeller", BUYER = "ScenarioBuyer";
    /** A vanilla item no default preset trades; its market is recreated by every run. */
    static final ItemStack ITEM = new ItemStack(Items.DEAD_BUSH);
    /** Items traded (real units). */
    static final int ITEMS = 2;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.SCENARIO;
    }

    @Override
    public Side side() {
        return Side.SCENARIO;
    }

    @Override
    public void registerTests() {
        addTest("trade_across_slaves", this::test_tradeAcrossSlaves);
    }

    /** What the master's first step remembers for its last one (both run in the master's JVM). */
    static final class MasterState {
        ItemID item, money;
        UUID seller, buyer;
        long price, since, sellerItems, sellerMoney, buyerItems, buyerMoney;
        int scale;
    }

    /**
     * Seller on slaveA sells {@link #ITEMS} items to the buyer on slaveB at the market price; the master checks the
     * fill (both orders filled, volume, price) and both players' item and money balances.
     */
    private TestResult test_tradeAcrossSlaves() {
        MinecraftServer server = getServer();
        if (server == null) return skip("no server in this JVM");
        MasterState s = new MasterState();
        return Scenario.named("sm_trade_across_slaves")
                .step("master", "clean market and funded players", () -> setUpOnMaster(server, s))
                .step("slaveA", "seller places a limit sell", () -> placeOnSlave(server, SELLER, false))
                .step("slaveB", "buyer places a limit buy at the same price", () -> placeOnSlave(server, BUYER, true))
                .step("master", "orders filled and balances moved", () -> checkOnMaster(s))
                .run("trade_across_slaves", server);
    }

    // ---- master ----------------------------------------------------------------------------------------------

    /** Recreates the market (empty book), detaches its plugins, turns the virtual book off and funds both players. */
    private TestResult setUpOnMaster(MinecraftServer server, MasterState s) {
        ServerPlayer seller = FakePlayers.get(server, SELLER), buyer = FakePlayers.get(server, BUYER);
        return setUpOnMaster(s, ITEM, seller.getUUID(), seller, SELLER, buyer.getUUID(), buyer, BUYER);
    }

    /**
     * Recreates the market for {@code item} (empty book), detaches its plugins, turns the virtual book off, funds the
     * seller with {@link #ITEMS} items and the buyer with enough money, and remembers item, price and balances in
     * {@code s}. Shared with {@link ScreenTradeScenarioSuite} (real players instead of fake ones).
     *
     * @param item         the market's item: {@link #ITEM} here, {@link ScreenTradeScenario#ITEM} for the screen
     *                     trade (both scenarios run at once in slave2-clients, so they must not share a market)
     * @param sellerPlayer the seller to add as a BankSystem user if it is none yet, or null for a real player (its
     *                     join adds it)
     * @param buyerPlayer  the same for the buyer
     */
    TestResult setUpOnMaster(MasterState s, ItemStack item, UUID seller, @Nullable ServerPlayer sellerPlayer, String sellerName,
                             UUID buyer, @Nullable ServerPlayer buyerPlayer, String buyerName) {
        StockMarketModBackend.ServerInstances b = StockMarketModBackend.getServerInstances();
        if (b == null || b.MARKET_MANAGER.isSlave()) return fail("the 'master' instance is not the StockMarket master");
        s.item = await(ItemIDManager.registerItemStackServerSide(item), WAIT_MS);
        if (!s.item.isValid()) return fail("no ItemID for " + item);

        // a run aborted before its cleanup leaves the market behind: delete it so the book starts empty
        if (Boolean.TRUE.equals(await(b.MARKET_MANAGER.getAsync().marketExistsAsync(s.item), WAIT_MS)))
            await(b.MARKET_MANAGER.getAsync().deleteMarketAsync(s.item), WAIT_MS);
        IAsyncMarket market = await(b.MARKET_MANAGER.getAsync().createMarketAsync(s.item), WAIT_MS);
        if (market == null) return fail("market for " + s.item + " not created");
        b.PLUGIN_MANAGER.getSync().removeCache(s.item); // no plugin bot orders
        MarketSettings settings = await(market.getSettingsAsync(), WAIT_MS);
        settings.virtualOrderbookEnabled = false; // the seller's ask is the only liquidity
        if (!Boolean.TRUE.equals(await(market.setSettingsAsync(settings), WAIT_MS))) return fail("virtual order book not turned off");

        s.price = await(market.getCurrentMarketPriceAsync(), WAIT_MS);
        if (s.price <= 0) return fail("market has no price: " + s.price);
        IAsyncBankManager bank = BankSystemMod.getAPI().getServerBankManager().getAsync();
        s.scale = await(bank.getItemFractionScaleFactorAsync(), WAIT_MS);
        s.money = await(b.MARKET_MANAGER.getAsync().getTradingCurrencyIDAsync(), WAIT_MS);
        s.seller = seller;
        s.buyer = buyer;

        String error = fund(bank, seller, sellerPlayer, sellerName, s.item, (long) ITEMS * s.scale);
        if (error == null) error = fund(bank, buyer, buyerPlayer, buyerName, s.money, 2 * s.price * ITEMS + 100);
        if (error != null) return fail(error);

        s.sellerItems = balance(bank, seller, s.item);
        s.sellerMoney = balance(bank, seller, s.money);
        s.buyerItems = balance(bank, buyer, s.item);
        s.buyerMoney = balance(bank, buyer, s.money);
        s.since = System.currentTimeMillis();
        return pass("market " + s.item + " at " + s.price);
    }

    /**
     * Reads both players' filled orders from the order history, then their balances; deletes the market at the end.
     */
    TestResult checkOnMaster(MasterState s) {
        StockMarketModBackend.ServerInstances b = StockMarketModBackend.getServerInstances();
        if (b == null || s.item == null) return fail("the master's set-up step did not run");
        try {
            UUID seller = s.seller, buyer = s.buyer;
            OrderRecordStruct sell = null, buy = null;
            for (long end = System.currentTimeMillis() + WAIT_MS; (sell == null || buy == null) && System.currentTimeMillis() < end; pump(250)) {
                List<OrderRecordStruct> rows = await(b.ORDER_RECORD_MANAGER.getHistory(
                        Optional.of(new DateFilter(s.since, Long.MAX_VALUE)), Optional.empty(), Optional.empty(), Optional.empty()), WAIT_MS);
                sell = row(rows, seller, s.item);
                buy = row(rows, buyer, s.item);
            }
            TestResult r = assertNotNull("seller's order is in the history (filled)", sell);
            if (!r.passed()) return r;
            r = assertNotNull("buyer's order is in the history (filled)", buy);
            if (!r.passed()) return r;
            long volume = (long) ITEMS * s.scale;
            for (OrderRecordStruct o : List.of(sell, buy)) {
                String who = o == sell ? "seller" : "buyer";
                r = assertEquals(who + " order filled", OrderRecordStruct.STATUS_FILLED, o.finalStatus());
                if (!r.passed()) return r;
                r = assertEquals(who + " volume", volume, Math.abs(o.amount()));
                if (!r.passed()) return r;
                r = assertEquals(who + " price = the seller's ask", s.price, o.price());
                if (!r.passed()) return r;
            }

            IAsyncBankManager bank = BankSystemMod.getAPI().getServerBankManager().getAsync();
            r = assertEquals("seller gave the items", s.sellerItems - volume, balance(bank, seller, s.item));
            if (!r.passed()) return r;
            r = assertEquals("buyer got the items", s.buyerItems + volume, balance(bank, buyer, s.item));
            if (!r.passed()) return r;
            long paid = s.buyerMoney - balance(bank, buyer, s.money);
            long earned = balance(bank, seller, s.money) - s.sellerMoney;
            r = assertTrue("buyer paid (" + paid + ")", paid > 0);
            if (!r.passed()) return r;
            return assertEquals("seller got what the buyer paid", paid, earned);
        } finally {
            try {
                await(b.MARKET_MANAGER.getAsync().deleteMarketAsync(s.item), WAIT_MS);
            } catch (RuntimeException ignored) {
                // best effort: the next run deletes it first
            }
        }
    }

    /** @return the player's filled order on the item, or null */
    private static @Nullable OrderRecordStruct row(List<OrderRecordStruct> rows, UUID player, ItemID item) {
        return rows.stream().filter(o -> player.equals(o.user()) && o.itemID() == item.getShort()).findFirst().orElse(null);
    }

    /**
     * Makes the player a BankSystem and StockMarket user with a personal account and deposits {@code amount}
     * into its bank for {@code item}.
     *
     * @param player the player to add as a BankSystem user if it is none yet; null for a real player, whose join
     *               adds it (this then only waits for it)
     * @return why it failed, or null on success
     */
    private @Nullable String fund(IAsyncBankManager bank, UUID uuid, @Nullable ServerPlayer player, String name, ItemID item, long amount) {
        if (!Boolean.TRUE.equals(await(bank.userExistsAsync(uuid), WAIT_MS))) {
            if (player != null) bank.addUserAsync(player);
            for (int i = 0; !Boolean.TRUE.equals(await(bank.userExistsAsync(uuid), WAIT_MS)); i++) {
                if (i >= 50) return name + " not added as a BankSystem user";
                pump(100);
            }
        }
        StockMarketModBackend.getServerInstances().MARKET_MANAGER.getAsync().onPlayerJoinAsync(uuid, name);
        Integer account = await(bank.createPersonalBankAccountGetAccountNrAsync(uuid), WAIT_MS);
        if (account == null || account < 0) return name + " has no personal account: " + account;
        IAsyncBank b = await(bank.getOrCreatePersonalBankAsync(uuid, item), WAIT_MS);
        if (b == null) return name + " has no bank for " + item;
        BankStatus status = await(b.depositAsync(amount), WAIT_MS);
        return status == BankStatus.SUCCESS ? null : name + ": deposit of " + amount + " failed: " + status;
    }

    private long balance(IAsyncBankManager bank, UUID player, ItemID item) {
        IAsyncBank b = await(bank.getOrCreatePersonalBankAsync(player, item), WAIT_MS);
        if (b == null) throw new IllegalStateException(player + " has no bank for " + item);
        return await(b.getBalanceAsync(), WAIT_MS);
    }

    // ---- slaves ----------------------------------------------------------------------------------------------

    /**
     * Places the fake player's limit order for {@link #ITEMS} items at the market's current price through this
     * slave's player path (forwarded to the master).
     *
     * @param buy true for the buyer's limit buy, false for the seller's limit sell
     */
    private TestResult placeOnSlave(MinecraftServer server, String name, boolean buy) {
        StockMarketModBackend.ServerInstances b = StockMarketModBackend.getServerInstances();
        if (b == null || !b.MARKET_MANAGER.isSlave()) return fail("this instance is not a StockMarket slave");
        if (!MultiServerUtils.canInteractWithStockMarket()) return fail("not connected to the master");
        if (!Boolean.TRUE.equals(SlaveTrustCache.get())) return fail("this slave is not trusted by the master (players may not trade)");

        ItemID item = await(ItemIDManager.registerItemStackServerSide(ITEM), WAIT_MS);
        if (!item.isValid()) return fail("no ItemID for " + ITEM + " on this slave");
        IAsyncMarket market = await(b.MARKET_MANAGER.getAsync().getMarketAsync(item), WAIT_MS);
        if (market == null) return fail("the master's market for " + item + " is not visible here");
        long price = await(market.getCurrentMarketPriceAsync(), WAIT_MS);
        int scale = await(BankSystemMod.getAPI().getServerBankManager().getAsync().getItemFractionScaleFactorAsync(), WAIT_MS);

        ServerPlayer player = FakePlayers.get(server, name);
        var account = await(BankSystemMod.getAPI().getServerBankManager().getAsync()
                .getOrCreatePersonalBankAccountAsync(player.getUUID()), WAIT_MS);
        if (account == null) return fail(name + " has no personal account");
        long volume = (long) ITEMS * scale * (buy ? 1 : -1);
        return assertEquals(name + "'s order accepted (" + volume + " @ " + price + ")", Boolean.TRUE, await(market.putOrderAsync(
                new Order(item, Order.Type.LIMIT, volume, price, 0, player.getUUID(), account.getAccountNumberAsync())), WAIT_MS));
    }
}
