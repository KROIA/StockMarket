package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.bank.BankStatus;
import net.kroia.banksystem.api.bank.IAsyncBank;
import net.kroia.banksystem.api.bankaccount.IAsyncBankAccount;
import net.kroia.banksystem.api.bankmanager.IAsyncBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.setting.Setting;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.testing.FixtureTestRequest;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.SlaveTrustCache;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BS-269: each {@link FixtureTestRequest} op once, from a trusted slave against a market it creates (and deletes):
 * setting round trip with restore, detach plugins, a bot ask filled by a player limit buy and read back by collect,
 * and a candle backfill read back from the price history.
 */
public class FixtureHooksTestSuite extends TestSuite {

    private static final long WAIT_MS = 15_000;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.FIXTURE_HOOKS;
    }

    @Override
    public void registerTests() {
        addTest("settings_round_trip", this::test_settingsRoundTrip);
        addTest("bot_order_fill_collect_backfill", this::test_botOrderFillCollectBackfill);
    }

    /** @return why the test can't run here, or null */
    private @Nullable String skipReason(@Nullable StockMarketModBackend.ServerInstances backend) {
        if (backend == null || getServer() == null) return "no server in this JVM";
        if (!backend.MARKET_MANAGER.isSlave()) return "slave only";
        if (!MultiServerUtils.canInteractWithStockMarket()) return "not connected to the master";
        if (!Boolean.TRUE.equals(SlaveTrustCache.get())) return "this slave is not trusted (slave-trusted cell only)";
        return null;
    }

    private TestResult test_settingsRoundTrip() {
        StockMarketModBackend.ServerInstances backend = StockMarketModBackend.getServerInstances();
        String skip = skipReason(backend);
        if (skip != null) return skip(skip);
        for (String name : List.of("TRADE_COOLDOWN_MS", "CANDLE_TIME")) {
            // default + 1 ms: a sane value, so suites running on the master meanwhile see no real change
            @SuppressWarnings("unchecked")
            long probe = ((Setting<Long>) backend.SERVER_SETTINGS.MARKET.getSetting(name)).getDefaultValue() + 1;
            Long previous = await(FixtureTestRequest.setMarketSetting(name, probe), WAIT_MS);
            TestResult r = assertNotNull(name + " set", previous);
            if (!r.passed()) return r;
            Long changed = await(FixtureTestRequest.setMarketSetting(name, previous), WAIT_MS); // restore
            r = assertEquals(name + " returned the value set before", probe, changed);
            if (!r.passed()) return r;
        }
        return assertNull("unknown setting refused", await(FixtureTestRequest.setMarketSetting("NO_SUCH_SETTING", 1), WAIT_MS));
    }

    private TestResult test_botOrderFillCollectBackfill() {
        StockMarketModBackend.ServerInstances backend = StockMarketModBackend.getServerInstances();
        String skip = skipReason(backend);
        if (skip != null) return skip(skip);
        List<ServerPlayer> players = getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return skip("no player online on this slave");
        UUID tester = players.get(0).getUUID();

        List<ItemID> markets = await(backend.MARKET_MANAGER.getAsync().getAvailableMarketIDsAsync(), WAIT_MS);
        ItemID item = itemWithoutMarket(markets);
        if (item == null) return skip("every registered vanilla item already has a market");

        IAsyncBankManager bank = backend.BANK_SYSTEM_API.getServerBankManager().getAsync();
        long since = System.currentTimeMillis();
        boolean created = false;
        try {
            IAsyncMarket market = await(backend.MARKET_MANAGER.getAsync().createMarketAsync(item), WAIT_MS);
            TestResult r = assertNotNull("market created for " + item, market);
            if (!r.passed()) return r;
            created = true;

            r = assertEquals("plugins detached", Boolean.TRUE, await(FixtureTestRequest.detachPlugins(item), WAIT_MS));
            if (!r.passed()) return r;
            // only the bot's ask may fill the player's buy
            MarketSettings settings = await(market.getSettingsAsync(), WAIT_MS);
            settings.virtualOrderbookEnabled = false;
            r = assertEquals("virtual orderbook off", Boolean.TRUE, await(market.setSettingsAsync(settings), WAIT_MS));
            if (!r.passed()) return r;

            long price = await(market.getCurrentMarketPriceAsync(), WAIT_MS);
            int scale = await(bank.getItemFractionScaleFactorAsync(), WAIT_MS);
            r = assertTrue("market has a price (" + price + ")", price > 0);
            if (!r.passed()) return r;

            // fund the tester: one item at the price, plus room
            ItemID money = await(backend.MARKET_MANAGER.getAsync().getTradingCurrencyIDAsync(), WAIT_MS);
            IAsyncBankAccount account = await(bank.getOrCreatePersonalBankAccountAsync(tester), WAIT_MS);
            r = assertNotNull("tester has a personal account", account);
            if (!r.passed()) return r;
            IAsyncBank moneyBank = await(bank.getOrCreatePersonalBankAsync(tester, money), WAIT_MS);
            r = assertNotNull("tester has a money bank", moneyBank);
            if (!r.passed()) return r;
            r = assertEquals("tester funded", BankStatus.SUCCESS, await(moneyBank.depositAsync(2 * price + 100), WAIT_MS));
            if (!r.passed()) return r;

            r = assertEquals("bot ask accepted", Boolean.TRUE,
                    await(FixtureTestRequest.botOrder(item, Order.Type.LIMIT, -scale, price), WAIT_MS));
            if (!r.passed()) return r;
            r = assertEquals("tester limit buy accepted", Boolean.TRUE, await(market.putOrderAsync(
                    new Order(item, Order.Type.LIMIT, scale, price, 0, tester, account.getAccountNumberAsync())), WAIT_MS));
            if (!r.passed()) return r;

            FixtureTestRequest.OrderRow row = null;
            FixtureTestRequest.Output out = null;
            for (long end = System.currentTimeMillis() + WAIT_MS; row == null && System.currentTimeMillis() < end; pump(250)) {
                out = await(FixtureTestRequest.collect(since), WAIT_MS);
                if (!out.ok()) return fail("collect refused");
                row = out.orders().stream()
                        .filter(o -> tester.equals(o.player()) && o.itemId() == item.getShort()).findFirst().orElse(null);
            }
            r = assertNotNull("collect returns the tester's filled order", row);
            if (!r.passed()) return r;
            r = assertTrue("order id set (" + row + ")", row.orderId() != null && row.orderId() > 0);
            if (!r.passed()) return r;
            r = assertEquals("filled", OrderRecordStruct.STATUS_FILLED, row.finalStatus());
            if (!r.passed()) return r;
            r = assertEquals("amount", (long) scale, row.amount());
            if (!r.passed()) return r;
            r = assertEquals("price = bot's ask", price, row.price());
            if (!r.passed()) return r;
            r = assertEquals("limit price", price, row.limitPrice());
            if (!r.passed()) return r;
            r = assertTrue("rows are since the start", out.orders().stream().allMatch(o -> o.time() >= since)
                    && out.alerts().stream().allMatch(a -> a.ts() >= since));
            if (!r.passed()) return r;

            // 5 candles a day back, one minute apart
            List<MarketPriceStruct> candles = new ArrayList<>();
            long start = since - 86_400_000L;
            for (int i = 0; i < 5; i++)
                candles.add(new MarketPriceStruct(item.getShort(), price, price - 1, price + 1, start + i * 60_000L, 0f));
            return assertEquals("backfilled candles read back", 5L,
                    await(FixtureTestRequest.backfillCandles(item, candles), WAIT_MS));
        } finally {
            if (created) {
                try {
                    await(backend.MARKET_MANAGER.getAsync().deleteMarketAsync(item), WAIT_MS);
                } catch (RuntimeException ignored) {
                    // best effort
                }
            }
        }
    }

    /** @return a vanilla item this slave has an ItemID for and that has no market yet, or null */
    private static @Nullable ItemID itemWithoutMarket(List<ItemID> markets) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || !"minecraft".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace()))
                continue;
            ItemID id = ItemIDManager.getItemID(new ItemStack(item));
            if (id.isValid() && !markets.contains(id))
                return id;
        }
        return null;
    }
}
