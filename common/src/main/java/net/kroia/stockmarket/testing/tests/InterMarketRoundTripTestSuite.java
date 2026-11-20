package net.kroia.stockmarket.testing.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.cache.MarketCache;
import net.kroia.stockmarket.pluginsystem.plugins.DefaultOrderbookVolumeDistributionPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.NetFlowPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.TargetPriceBot;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * SM-335: profit-taking round trips over three markets with inter-market swaps. Reported on a server: right after
 * diamond was pumped, five inter-market swaps (diamond to another market) sold ~2,400 diamonds at the pumped price
 * before the price fell back. Each scenario here tries to turn that into money.
 * <p>
 * Harness: three scratch markets (A "diamond" 160 / abundance 50, B "netherite" 22,000 / 5, C "iron" 8 / 300), every
 * default plugin at today's defaults in production order (NetFlowPlugin, VolatilityPlugin, the volume distribution
 * plugin as book refill, TargetPriceBot) over all three, seeded ({@link #SEED}) so a run is deterministic. Orders go
 * through the real server paths: regular market orders via {@code ServerMarket.putOrder}, swaps as sell-direction
 * market inter-market orders via {@code ServerMarketManager.putInterMarketOrder} + {@code update()} (the path
 * {@code PlaceInterMarketOrderRequest} takes after its checks). The request's checks are mirrored: no order above a
 * market's max order volume (a swap's want leg estimated at current prices, as the request does) and one order per
 * trade cooldown ({@link #COOLDOWN_TICKS}).
 * <p>
 * "Frozen" phases model "before the bot reacts": no plugin runs, each order is matched in its own market-only tick.
 * <p>
 * Every scenario starts with cash (one also with items, valued at the start price), runs its route, closes every
 * position back to cash with market sells (unsellable rest valued at the bid side of the book), and reports net profit
 * absolute and in % of the capital at risk (largest cash drawdown + start items value). Pass: profit <=
 * {@link #THRESHOLD_PCT}% (no fees exist, so balances are already net). The numbers are logged for every scenario.
 * <p>
 * Topic {@code stockmarket/sm_exploit/InterMarketRoundTripTestSuite}, master only; a scenario takes a few thousand
 * simulated ticks (no in-game wait).
 */
public class InterMarketRoundTripTestSuite extends PluginTestSuite {

    /** Max net profit in % of the capital at risk that still counts as normal fluctuation. */
    static final double THRESHOLD_PCT = 5.0;
    /** Seed of the volatility walk and the bot's gap guard. */
    static final long SEED = 335L;
    /** Plugin warm-up before a route starts (as the T-208 gate). */
    static final int WARMUP = 200;
    /** Trade cooldown: MARKET.TRADE_COOLDOWN_MS default 500 ms = 10 ticks. */
    static final int COOLDOWN_TICKS = 10;
    /** Size of a pump in max orders of the pumped market. */
    static final int PUMP = 30;
    /** Start cash (real), large enough for any route. */
    static final double CASH = 1e12;

    private IServerMarket marketB, marketC;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.EXPLOIT;
    }

    @Override
    public void registerTests() {
        addTest("control_pump_A_sell_A", () -> run("control_pump_A_sell_A", e -> {
            e.buy(e.a, PUMP * e.a.maxOrder);
            e.sellAll(e.a);
        }));
        addTest("pump_A_swap_A_to_B_sell_B", () -> run("pump_A_swap_A_to_B_sell_B", e -> {
            e.buy(e.a, PUMP * e.a.maxOrder);
            e.swapAll(e.a, e.b, 1);
            e.sellAll(e.b);
        }));
        addTest("frozen_pump_A_swap_A_to_B_sell_B", () -> run("frozen_pump_A_swap_A_to_B_sell_B", e -> {
            e.frozen = true;
            e.buy(e.a, PUMP * e.a.maxOrder);
            e.swapAll(e.a, e.b, 1);
            e.sellAll(e.b);
        }));
        addTest("frozen_pump_A_swap_A_to_B_then_live_sell_B", () -> run("frozen_pump_A_swap_A_to_B_then_live_sell_B", e -> {
            e.frozen = true;
            e.buy(e.a, PUMP * e.a.maxOrder);
            e.swapAll(e.a, e.b, 1);
            e.frozen = false;
            e.sellAll(e.b);
        }));
        addTest("pump_A_wait_30s_swap_A_to_B_sell_B", () -> run("pump_A_wait_30s_swap_A_to_B_sell_B", e -> {
            e.buy(e.a, PUMP * e.a.maxOrder);
            e.advance(600);
            e.swapAll(e.a, e.b, 1);
            e.sellAll(e.b);
        }));
        addTest("pump_A_swap_A_to_C_sell_C", () -> run("pump_A_swap_A_to_C_sell_C", e -> {
            e.buy(e.a, PUMP * e.a.maxOrder);
            e.swapAll(e.a, e.c, 1);
            e.sellAll(e.c);
        }));
        addTest("pump_B_swap_B_to_A_sell_A", () -> run("pump_B_swap_B_to_A_sell_A", e -> {
            e.buy(e.b, PUMP * e.b.maxOrder);
            e.swapAll(e.b, e.a, 1);
            e.sellAll(e.a);
        }));
        addTest("holding_A_swap_A_to_B_sell_B", () -> run("holding_A_swap_A_to_B_sell_B", e -> {
            e.give(e.a, PUMP * e.a.maxOrder);
            e.swapAll(e.a, e.b, 1);
            e.sellAll(e.b);
        }));
        addTest("cash_A_B_A_cash_x5", () -> run("cash_A_B_A_cash_x5", e -> {
            for (int i = 0; i < 5; i++) {
                e.buy(e.a, 5 * e.a.maxOrder);
                e.swapAll(e.a, e.b, 1);
                e.swapAll(e.b, e.a, 1);
                e.sellAll(e.a);
            }
        }));
        addTest("frozen_cash_A_B_A_cash_x5", () -> run("frozen_cash_A_B_A_cash_x5", e -> {
            e.frozen = true;
            for (int i = 0; i < 5; i++) {
                e.buy(e.a, 5 * e.a.maxOrder);
                e.swapAll(e.a, e.b, 1);
                e.swapAll(e.b, e.a, 1);
                e.sellAll(e.a);
            }
        }));
        addTest("triangle_cash_A_B_C_A_cash_x3", () -> run("triangle_cash_A_B_C_A_cash_x3", e -> {
            for (int i = 0; i < 3; i++) {
                e.buy(e.a, 5 * e.a.maxOrder);
                e.swapAll(e.a, e.b, 1);
                e.swapAll(e.b, e.c, 1);
                e.swapAll(e.c, e.a, 1);
                e.sellAll(e.a);
            }
        }));
        addTest("pump_A_swap_large_chunks", () -> run("pump_A_swap_large_chunks", e -> {
            e.buy(e.a, 10 * e.a.maxOrder);
            e.swapAll(e.a, e.b, 1);
            e.sellAll(e.b);
        }));
        addTest("pump_A_swap_20x_smaller_chunks", () -> run("pump_A_swap_20x_smaller_chunks", e -> {
            e.buy(e.a, 10 * e.a.maxOrder);
            e.swapAll(e.a, e.b, 20);
            e.sellAll(e.b);
        }));
    }

    @Override
    public void setup() {
        super.setup();
        marketB = fixture.createMarket("rt_B");
        marketC = fixture.createMarket("rt_C");
    }

    @Override
    public void teardown() {
        marketB = null;
        marketC = null;
        super.teardown();
    }

    /** Runs one scenario on a fresh {@link Env}, logs its numbers and asserts the profit threshold. */
    private TestResult run(String name, Consumer<Env> route) {
        long wall = System.nanoTime();
        try (Env e = new Env()) {
            route.accept(e);
            e.frozen = false;
            for (Mk m : e.all) e.sellAll(m);
            String msg = e.report(name, (System.nanoTime() - wall) / 1_000_000);
            StockMarketMod.LOGGER.info(msg);
            if (e.capital() <= 0) return fail("route did not trade: " + msg);
            return e.profitPct() <= THRESHOLD_PCT ? pass(msg) : fail("profit above " + THRESHOLD_PCT + "%: " + msg);
        } catch (Exception ex) {
            StockMarketMod.LOGGER.error("[SM-335] " + name, ex);
            return fail("Exception: " + ex);
        }
    }

    /** One market of the harness. */
    final class Mk {
        final String name;
        final ServerMarket market;
        final ItemID id;
        final MarketCache cache;
        final IPluginOrderBook book;
        final DefaultOrderbookVolumeDistributionPlugin.DistributionCalculator calc;
        final MarketInterface iface;
        final double startPrice;
        /** Max order volume (real items). */
        final long maxOrder;
        double peak;

        Mk(String name, IServerMarket m, double price, float abundance) {
            this.name = name;
            market = (ServerMarket) m;
            id = m.getItemID();
            MarketSettings s = market.getSettings();
            s.defaultPrice = MarketManager.convertToRawAmountStatic(price);
            s.naturalAbundance = abundance;
            s.maxOrderVolume = maxOrder = MarketSettings.defaultMaxOrderVolume(abundance);
            market.setMaintenance(false);
            market.test_clearOrderbook();
            market.test_clearIncomingOrderBuffers();
            market.test_setCurrentMarketPrice(s.defaultPrice);
            market.resetNetPlayerItemFlow();
            cache = new MarketCache(market);
            calc = harnessCalc(price, DefaultOrderbookVolumeDistributionPlugin.depthScaleFor(price, abundance, 1f));
            cache.getPluginOrderBook().registerDefaultVolumeDistributionCalculator(calc);
            market.test_resetVirtualOrderBookVolume();
            book = cache.getPluginOrderBook();
            iface = new MarketInterface(cache.getPluginMarket(), book);
            market.setTradingHours(0, 1440);
            market.setTradingOverride(true);
            startPrice = peak = price();
        }

        double price() { return MarketManager.convertToRealAmountStatic(market.getCurrentMarketPrice()); }
    }

    /** Swap totals of one {@link Env#swapAll} call, for the report. */
    private record SwapStats(String route, double sold, double soldValue, double bought, double haveStartPrice, double havePriceAtSwap, int orders) {}

    /** The three markets, the plugins and one player. {@link #close()} restores the shared state. */
    final class Env implements AutoCloseable {
        final StockMarketModSettings.Market modSettings = backend.SERVER_SETTINGS.MARKET;
        final Float prevHalfLife = modSettings.NET_FLOW_HALF_LIFE_MINUTES.get();
        final Float prevAlert = modSettings.PROFIT_ALERT_THRESHOLD.get();
        final ServerMarketManager manager = (ServerMarketManager) backend.MARKET_MANAGER.getSync();
        final int sf = backend.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor();
        final long[] clockMs = {1_000_000L};
        final Mk a, b, c;
        final List<Mk> all;
        final List<MarketInterface> ifaces = new ArrayList<>();
        final NetFlowPlugin flow = new NetFlowPlugin();
        final VolatilityPlugin vol = new VolatilityPlugin();
        final DefaultOrderbookVolumeDistributionPlugin refill = new DefaultOrderbookVolumeDistributionPlugin();
        final TargetPriceBot bot = new TargetPriceBot();
        final IServerBankAccount account;
        final UUID player;
        final IServerBank money;
        /** True: no plugin runs, each order is matched in its own market-only tick (the bot never reacts). */
        boolean frozen;
        boolean imoPending;
        long seq;
        int ticks;
        double startCash, minCash, startItemsValue;
        final List<SwapStats> swaps = new ArrayList<>();

        Env() {
            modSettings.NET_FLOW_HALF_LIFE_MINUTES.set(modSettings.NET_FLOW_HALF_LIFE_MINUTES.getDefaultValue());
            modSettings.PROFIT_ALERT_THRESHOLD.set(0f);
            VolatilityPlugin.test_setSeed(SEED);
            VolatilityPlugin.test_setClock(() -> clockMs[0]);
            TargetPriceBot.test_setGapGuardSeed(SEED);
            a = new Mk("A", serverMarket, 160, 50);
            b = new Mk("B", marketB, 22_000, 5);
            c = new Mk("C", marketC, 8, 300);
            all = List.of(a, b, c);
            for (Mk m : all) {
                manager.cancelInterMarketOrdersForMarket(m.id);
                m.market.updateTradingHours(manager.getCurrentTradingMinute());
                ifaces.add(m.iface);
            }
            flow.setLoggerEnabled(false);
            vol.setLoggerEnabled(false);
            refill.setLoggerEnabled(false);
            bot.setLoggerEnabled(false);
            for (Mk m : all) {
                flow.onMarketSubscribed(m.id);
                vol.onMarketSubscribed(m.id);
                refill.onMarketSubscribed(m.id);
                bot.onMarketSubscribed(m.id);
                apply(flow, NetFlowPlugin.Settings.CODEC, NetFlowPlugin.Settings.createDefault(), m.id);
                apply(vol, VolatilityPlugin.Settings.CODEC, new VolatilityPlugin.Settings(VolatilityPlugin.DEFAULT_VOLATILITY_SCALE), m.id);
                apply(bot, TargetPriceBot.Settings.CODEC, TargetPriceBot.Settings.createDefault(), m.id);
                refill.test_mirrorWallTo(m.id, m.calc);
            }
            account = fixture.createBankAccount("sm335");
            player = fixture.createUser("sm335");
            ItemID moneyID = manager.getTradingCurrencyID();
            for (Mk m : all) account.createBank(m.id, 0);
            account.createBank(moneyID, MarketManager.convertToRawAmountStatic(CASH));
            money = account.getBank(moneyID);
            advance(WARMUP);
            for (Mk m : all) m.peak = m.price();
            startCash = minCash = cash();
        }

        <T> void apply(ServerPlugin<T, ?> plugin, StreamCodec<ByteBuf, T> codec, T settings, ItemID id) {
            ByteBuf buf = Unpooled.buffer();
            codec.encode(buf, settings);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            buf.release();
            if (!plugin.decodeAndApplyCustomSettings(id, bytes))
                throw new IllegalStateException("Could not apply plugin settings " + settings);
        }

        double cash() { return MarketManager.convertToRealAmountStatic(money.getTotalBalance()); }
        IServerBank bank(Mk m) { return account.getBank(m.id); }
        double held(Mk m) { return bank(m).getTotalBalance() / (double) sf; }

        /** One server tick: plugins (unless frozen), then the markets; through the manager when a swap is queued. */
        void tick() {
            if (!frozen) {
                flow.update(ifaces);
                vol.update(ifaces);
                refill.update(ifaces);
                bot.update(ifaces);
                for (Mk m : all) m.cache.apply();
            }
            if (imoPending) {
                manager.update();
                imoPending = false;
            } else {
                for (Mk m : all) m.market.update();
            }
            clockMs[0] += 50;
            ticks++;
            minCash = Math.min(minCash, cash());
            for (Mk m : all) m.peak = Math.max(m.peak, m.price());
        }

        void advance(int n) { for (int i = 0; i < n; i++) tick(); }

        /** After an order: frozen = one market-only tick, else one trade cooldown of normal ticks. */
        void step() { if (frozen) tick(); else advance(COOLDOWN_TICKS); }

        long now() { return System.currentTimeMillis() + seq++; }

        /** Gives the player start items, valued at the market's current price. */
        void give(Mk m, long real) {
            bank(m).setBalance(bank(m).getTotalBalance() + real * sf);
            startItemsValue += real * m.price();
        }

        /** Market-buys {@code real} items in max-order chunks, one per {@link #step()}. */
        void buy(Mk m, long real) {
            for (long left = real; left > 0; ) {
                long n = Math.min(left, m.maxOrder);
                m.market.putOrder(new Order(m.id, Order.Type.MARKET, n * sf, 0, now(), player, account.getAccountNumber()));
                left -= n;
                step();
            }
        }

        /** Market-sells every free item of {@code m} in max-order chunks until none are left or 3 sells fill nothing. */
        void sellAll(Mk m) {
            IServerBank items = bank(m);
            for (int guard = 0, stalls = 0; guard < 5_000 && stalls < 3 && items.getBalance() > 0; guard++) {
                long before = items.getTotalBalance(), raw = Math.min(items.getBalance(), m.maxOrder * sf);
                items.lockAmount(raw);
                m.market.putOrder(new Order(m.id, Order.Type.MARKET, -raw, 0, now(), player, account.getAccountNumber()));
                step();
                stalls = items.getTotalBalance() < before ? 0 : stalls + 1;
            }
        }

        /**
         * Swaps every free {@code have} item into {@code want} with sell-direction market inter-market orders, one per
         * {@link #step()}. Chunk = the largest the request accepts (have max order, want leg estimated at current
         * prices within the want max order), divided by {@code split}.
         */
        void swapAll(Mk have, Mk want, int split) {
            IServerBank items = bank(have);
            double sold = 0, value = 0, bought = 0, priceAtSwap = have.price();
            int orders = 0;
            for (int guard = 0, stalls = 0; guard < 5_000 && stalls < 3 && items.getBalance() > 0; guard++) {
                double limit = Math.min(have.maxOrder, want.maxOrder * want.price() / have.price());
                long raw = Math.min(items.getBalance(), Math.max(1, (long) Math.floor(limit * sf / split)));
                long before = items.getTotalBalance();
                items.lockAmount(raw);
                InterMarketOrder imo = new InterMarketOrder(want.id, have.id, Order.Type.MARKET,
                        0, want.market.getCurrentMarketPrice(), raw, have.market.getCurrentMarketPrice(),
                        now(), player, account.getAccountNumber(), 0);
                if (!manager.putInterMarketOrder(imo)) {
                    items.unlockAmount(raw);
                    throw new IllegalStateException("inter-market order " + have.name + "->" + want.name + " not accepted");
                }
                imoPending = true;
                orders++;
                step();
                sold += -imo.getSellOrder().getFilledVolume() / (double) sf;
                value += MarketManager.convertToRealAmountStatic(imo.getSellOrder().getTransferredMoney());
                bought += imo.getBuyOrder().getFilledVolume() / (double) sf;
                stalls = items.getTotalBalance() < before ? 0 : stalls + 1;
            }
            swaps.add(new SwapStats(have.name + "->" + want.name, sold, value, bought, have.startPrice, priceAtSwap, orders));
        }

        /** End value: cash plus what is left valued at the bid side of each book. */
        double endValue() {
            double v = cash();
            for (Mk m : all) v += markToBook(m.book, m.price(), held(m));
            return v;
        }

        double profit() { return endValue() - startCash - startItemsValue; }
        double capital() { return startCash - minCash + startItemsValue; }
        double profitPct() { return capital() > 0 ? 100 * profit() / capital() : 0; }

        String report(String name, long wallMs) {
            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                    "[SM-335] %s: profit %+.4g (%+.3f%% of capital %.4g, threshold %.1f%%), %d ticks, %d ms",
                    name, profit(), profitPct(), capital(), THRESHOLD_PCT, ticks, wallMs));
            for (Mk m : all)
                sb.append(String.format(Locale.ROOT, " | %s start %.6g peak %.6g end %.6g left %.4g", m.name, m.startPrice, m.peak, m.price(), held(m)));
            for (SwapStats s : swaps)
                if (s.sold() > 0)
                    sb.append(String.format(Locale.ROOT, " | swap %s %d orders: sold %.6g at avg %.6g (have start %.6g, at swap start %.6g), got %.6g",
                            s.route(), s.orders(), s.sold(), s.soldValue() / s.sold(), s.haveStartPrice(), s.havePriceAtSwap(), s.bought()));
            return sb.toString();
        }

        @Override
        public void close() {
            for (Mk m : all) {
                try { manager.cancelInterMarketOrdersForMarket(m.id); } catch (Exception ignored) { }
                m.market.test_clearIncomingOrderBuffers();
                m.market.test_clearOrderbook();
                m.market.test_setDefaultVolumeProviderFunction(null);
                m.market.resetNetPlayerItemFlow();
            }
            VolatilityPlugin.test_setSeed(null);
            VolatilityPlugin.test_setClock(null);
            TargetPriceBot.test_setGapGuardSeed(334L);  // PluginTestSuite.setup's seed
            modSettings.NET_FLOW_HALF_LIFE_MINUTES.set(prevHalfLife);
            modSettings.PROFIT_ALERT_THRESHOLD.set(prevAlert);
        }
    }
}
