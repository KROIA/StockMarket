package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kroia.banksystem.BankSystemModSettings;
import net.kroia.banksystem.api.bank.IAsyncBank;
import net.kroia.banksystem.testing.fixture.FixtureKey;
import net.kroia.banksystem.testing.fixture.FixtureKey.Case;
import net.kroia.banksystem.testing.fixture.FixtureStepRequest;
import net.kroia.banksystem.testing.fixture.FixtureSteps;
import net.kroia.banksystem.testing.fixture.FixtureSteps.Tester;
import net.kroia.banksystem.util.ItemID;
import net.kroia.banksystem.util.ItemIDManager;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestRegistry;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.api.marketmanager.IAsyncMarketManager;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.data.table.record.OrderRecordStruct;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.testing.FixtureTestRequest;
import net.kroia.stockmarket.testing.FixtureTestRequest.AlertRow;
import net.kroia.stockmarket.testing.FixtureTestRequest.OrderRow;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.SlaveTrustCache;
import net.kroia.stockmarket.util.TradeAlerts;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * BS-269 — opt-in driver of the generated audit fixture, run on the trusted slave (slave-trusted cell, only with
 * {@code --only stockmarket/sm_audit_fixture}). Drives the whole story: {@link FixtureKey#start}, BankSystem's
 * background (step A), the StockMarket market cases B1–B8 and the market part of story E, BankSystem's C9–C12, the
 * StockMarket quiet step, and the finish call point. Every case goes into {@link FixtureKey#current()} with the
 * audit tool's rule ids (plan {@code .claude/plans/bs269-sm-market-cases.md}, section "AT answer-key spec").
 * <p>
 * Ground rules: real vanilla items with markets created from the slave and never deleted (except B8); player orders
 * through the real player path ({@link IAsyncMarket#putOrderAsync}); bot liquidity, candle backfill and plugin detach
 * through {@link FixtureTestRequest}; no fake clock (real waits). Every counted order fills (OrderHistory keeps only
 * orders with fills). Market prices are only moved by bot orders on markets without plugins or virtual book, so every
 * fill price is known and checked when its OrderHistory row is read back.
 * <p>
 * Runs on the server thread like every suite: waits pump queued server tasks ({@link #pumpUntil}), which is also how
 * the blocking {@link FixtureSteps} calls must run.
 */
public class AuditFixtureSuite extends TestSuite {

    private static final String TOPIC = "stockmarket/sm_audit_fixture";
    private static final long SCALE = BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR; // raw per real unit (items and money)
    private static final long SECOND = 1_000, MINUTE = 60_000, HOUR = 3_600_000;
    private static final long WAIT_MS = 30_000, MASTER_MS = 420_000, ROW_WAIT_MS = 20_000;
    /** TRADE_COOLDOWN_MS is 500 ms per player; one order per player every 600 ms stays clear of it. */
    private static final long ORDER_GAP_MS = 600;
    /** Small trades cost about $100: below every money threshold (B6: under $1,000 and 1,000 items). */
    private static final long SMALL = 100 * SCALE;
    /** A market price below $1 makes the integer price ratios (2.5x, 1.3x, ...) too coarse. */
    private static final long MIN_PRICE = 100;
    /** Hour-profit threshold of the audit tool and PROFIT_ALERT_THRESHOLD: $1,000,000. */
    private static final long PROFIT_LIMIT = 1_000_000 * SCALE;
    /** Items of the BankSystem story (terminal deposits): no markets, or their price would move the testers' wealth. */
    private static final Set<Item> STORY_ITEMS = Set.of(Items.AIR, Items.DIAMOND, Items.IRON_INGOT, Items.STONE);

    /** A fixture market: its item, the slave's handle and its price when it was set up (raw). */
    private record Mkt(ItemID id, IAsyncMarket market, long price) {
        String item() { return id.getName(); }
        /** far-price / price-spike / market subject: the numeric ItemID */
        String subject() { return String.valueOf(id.getShort()); }
    }

    private StockMarketModBackend.ServerInstances backend;
    private MinecraftServer slave;
    private ItemID currency;
    /** Start of the StockMarket part: collect() reads OrderHistory and TradeAlert rows from here on. */
    private long since;
    private final Set<Long> seenOrders = new HashSet<>();
    private final Map<UUID, Long> lastOrder = new HashMap<>();
    private final Set<ItemID> usedItems = new HashSet<>();
    private final Map<Short, Mkt> markets = new HashMap<>();
    private final Map<UUID, String> testerNames = new HashMap<>();
    private final List<String> cases = new ArrayList<>(), warnings = new ArrayList<>();

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.AUDIT_FIXTURE;
    }

    @Override
    public void registerTests() {
        addTest("fixture_story", this::test_fixtureStory);
    }

    // ==== driver ====================================================================================================

    private TestResult test_fixtureStory() {
        String include = System.getProperty(TestRegistry.INCLUDE_PROPERTY);
        if (include == null || include.isBlank() || TestRegistry.isExcluded(TestRegistry.topicKey(this), include, null))
            return skip("opt-in: --only " + TOPIC);
        backend = StockMarketModBackend.getServerInstances();
        slave = getServer();
        if (backend == null || slave == null) return skip("no server in this JVM");
        if (!backend.MARKET_MANAGER.isSlave()) return skip("slave only");
        if (!MultiServerUtils.canInteractWithStockMarket()) return skip("not connected to the master");
        if (!Boolean.TRUE.equals(SlaveTrustCache.get())) return skip("this slave is not trusted (slave-trusted cell only)");

        Long cooldown = null, candleTime = null;
        List<Mkt> closed = new ArrayList<>();
        try {
            FixtureKey.start(TOPIC, "dev"); // ponytail: "dev" until the harness passes the commit
            FixtureSteps.Background bg = FixtureSteps.background(slave);
            currency = await(mm().getTradingCurrencyIDAsync(), WAIT_MS);
            since = now();
            Story s = setUp(bg);

            // One clock hour for the hour-profit cases (E, B3); everything after may cross hours.
            clockHourGuard();
            storyEMarket(s);
            caseB3(s);
            checkOneClockHour(s);

            caseB1(s);
            cooldown = await(FixtureTestRequest.setMarketSetting("TRADE_COOLDOWN_MS", 0), WAIT_MS);
            if (cooldown == null) throw new IllegalStateException("TRADE_COOLDOWN_MS not set to 0");
            caseB2(s);
            restoreSetting("TRADE_COOLDOWN_MS", cooldown);
            cooldown = null;
            caseB4(s);
            caseB5(s);
            caseB6(s);
            caseB7Cycle(s);
            caseB7Pump(s);
            caseB1SlowSell(s); // >= 40 s after TesterI's buy: the cases above filled the wait
            caseB8(s);

            List<FixtureSteps.Step> steps = new ArrayList<>();
            steps.add(FixtureSteps.balanceGap(slave));
            steps.add(FixtureSteps.adminCommands(slave));
            steps.add(FixtureSteps.suspicionFlags(slave));
            // BS sub-step 5 (C13-C16): each call runs its step now. TesterW/X are deleted after C16 (SM uses F..V only).
            steps.add(FixtureSteps.notes(slave));
            steps.add(FixtureSteps.shares(slave));
            steps.add(FixtureSteps.converterAndBlocks(slave));
            steps.add(FixtureSteps.deletedUser(slave));
            steps.add(FixtureSteps.transfers(slave)); // D, bank part
            // C16's parked DROP and D's planted transfers have no final journal step: AT needs them >= 10 min before the copy
            long parkedTransfersEnd = now();

            // Tester E needs >= 15 min after A's and D's last order: the BankSystem steps above filled most of it.
            storyELateTrader(s);
            // E, bank part: after every SM trade of A, D and E, so A's WEALTH_JUMP includes the market profit
            steps.add(FixtureSteps.exploitBank(slave));
            alertCases(s);

            steps.add(FixtureSteps.lastSave(slave));

            // SM quiet step: markets closed (no matching, no plugin trading) and no new candles until restored.
            candleTime = await(FixtureTestRequest.setMarketSetting("CANDLE_TIME", 86_400_000L), WAIT_MS);
            if (candleTime == null) throw new IllegalStateException("CANDLE_TIME not set");
            for (ItemID id : await(mm().getAvailableMarketIDsAsync(), WAIT_MS)) {
                IAsyncMarket m = await(mm().getMarketAsync(id), WAIT_MS);
                if (m != null && Boolean.TRUE.equals(await(m.setTradingOverrideAsync(false), WAIT_MS)))
                    closed.add(new Mkt(id, m, 0));
            }

            // C16 + D guard: story E's 15-min wait normally covers it, so this wait is ~0
            long parkedWait = parkedTransfersEnd + 10 * MINUTE + 5 * SECOND - now();
            if (parkedWait > 0) {
                StockMarketMod.LOGGER.info("[{}] waiting {} ms so the C16 and D parked transfers are >= 10 min old at the copy", TOPIC, parkedWait);
                waitUntil(parkedTransfersEnd + 10 * MINUTE + 5 * SECOND);
            }
            // the copy moment, last step: exports both servers, banknotes, answer key. Every SM case is in the key by now.
            Path file = FixtureSteps.finish(slave, null);
            StringBuilder notes = new StringBuilder();
            for (FixtureSteps.Step st : steps) notes.append("; ").append(st.note());
            return pass(cases.size() + " SM cases, key " + file + (warnings.isEmpty() ? "" : "; warnings: " + warnings) + notes);
        } catch (RuntimeException e) {
            return fail("fixture story failed after " + cases.size() + " SM cases: " + e);
        } finally {
            // best-effort cleanup: settings back, markets open, bank saves on
            if (cooldown != null) restoreSetting("TRADE_COOLDOWN_MS", cooldown);
            if (candleTime != null) restoreSetting("CANDLE_TIME", candleTime);
            for (Mkt m : closed) {
                try {
                    await(m.market().setTradingOverrideAsync(true), WAIT_MS);
                } catch (RuntimeException ignored) {
                    // best effort
                }
            }
            try {
                await(FixtureStepRequest.call("suppressSave", "{\"on\":false}"), WAIT_MS);
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
    }

    /** The testers and markets of the StockMarket cases, plus the times later steps depend on. */
    private static final class Story {
        Tester a, d, e;                               // story E (background testers)
        Tester f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v;
        Mkt mE, mE2, m1a, m1b, m1c, m2, m3, m4, m4c, m5, m6, m7c, m7p, m7q, m8;
        List<OrderRow> hourRows = new ArrayList<>();  // rows that must share one clock hour
        long lastAorD;                                // newest order time of A or D
        OrderRow iBuy;                                // B1 slow clean: TesterI's buy, sold >= 40 s later
        long iVol;
        String designatedProfit, designatedCycle, designatedPump; // B7 subjects (tester names)
        Set<String> cleanProfit = new HashSet<>(), cleanCycle = new HashSet<>(), cleanPump = new HashSet<>();
    }

    /** Markets first (their prices size the funding), then testers, funding and one balance snapshot. */
    private Story setUp(FixtureSteps.Background bg) {
        Story s = new Story();
        s.a = bg.tester("TesterA");
        s.d = bg.tester("TesterD");
        s.e = bg.tester("TesterE");
        for (Tester t : bg.testers()) testerNames.put(t.uuid(), t.name());

        s.mE = newMarket(); s.mE2 = newMarket(); s.m3 = newMarket();
        s.m1a = newMarket(); s.m1b = newMarket(); s.m1c = newMarket();
        s.m2 = newMarket(); s.m4 = newMarket(); s.m4c = newMarket(); s.m5 = newMarket(); s.m6 = newMarket();
        s.m7c = newMarket(); s.m7p = newMarket(); s.m7q = newMarket(); s.m8 = newMarket();

        FixtureKey key = FixtureKey.current();
        String[][] extra = {
                {"TesterF", "B1 round trip"}, {"TesterG", "B1 clean: another fill between"}, {"TesterH", "B1 fill between"},
                {"TesterI", "B1 clean: slow round trip"}, {"TesterJ", "B2 order burst"}, {"TesterK", "B2 clean: 5 orders"},
                {"TesterL", "B3 hour profit"}, {"TesterM", "B3 clean: just under"}, {"TesterN", "B4 far price"},
                {"TesterO", "B4 clean: 9x"}, {"TesterP", "B6 ledger edited"}, {"TesterQ", "B6 clean"},
                {"TesterR", "B7 cycle"}, {"TesterS", "B7 clean cycle"}, {"TesterT", "B7 pump"}, {"TesterU", "B7 clean pump"},
                {"TesterV", "B8 trades a deleted market"}};
        List<Tester> made = new ArrayList<>();
        for (String[] x : extra) {
            Tester t = FixtureSteps.ensureTester(slave, x[0]);
            testerNames.put(t.uuid(), t.name());
            key.addPlayer(x[0], x[1]);
            made.add(t);
        }
        s.f = made.get(0); s.g = made.get(1); s.h = made.get(2); s.i = made.get(3); s.j = made.get(4); s.k = made.get(5);
        s.l = made.get(6); s.m = made.get(7); s.n = made.get(8); s.o = made.get(9); s.p = made.get(10); s.q = made.get(11);
        s.r = made.get(12); s.s = made.get(13); s.t = made.get(14); s.u = made.get(15); s.v = made.get(16);

        // dollars; B3 and B7 cycle are sized to their profit targets (see the cases), plus room
        fund(s.a, 400_000);
        for (Tester t : List.of(s.d, s.e, s.f, s.g, s.h, s.i, s.j, s.k, s.n, s.o, s.p, s.q, s.t, s.u, s.v)) fund(t, 1_000);
        fund(s.l, costFor(PROFIT_LIMIT + 10_000 * SCALE, s.m3) / SCALE + 1_000);
        fund(s.m, costFor(PROFIT_LIMIT - 10_000 * SCALE, s.m3) / SCALE + 1_000);
        fund(s.r, costFor(150_000 * SCALE, s.m7c) / SCALE + 1_000);
        fund(s.s, costFor(90_000 * SCALE, s.m7c) / SCALE + 1_000);
        // baseline of every account's wealth before the trades (TesterA's wealth jump is story E's bank part)
        master("snapshot", obj());
        return s;
    }

    // ==== story E, market part ======================================================================================

    /**
     * E: TesterA compounds 4 round trips on M_E (buy at P, sell at 1.5P against bot liquidity, ratio below the
     * round-trip 2x): over $1M realised in one clock hour (hour-profit), the profit alert and a cycle alert per trip.
     * TesterD trades M_E inside A's session but never between A's buy and sell (weak market link). A then buys
     * normally on another market.
     */
    private void storyEMarket(Story s) {
        Mkt me = s.mE;
        long p = me.price(), hi = p * 3 / 2, dVol = small(hi);
        List<OrderRow> buys = new ArrayList<>(), sells = new ArrayList<>(), dRows = new ArrayList<>();
        for (int trip = 1; trip <= 4; trip++) {
            long vol = volumeFor(moneyOf(s.a) * 99 / 100, p);
            buys.add(trade(s.a, me, vol, p));
            sells.add(trade(s.a, me, -vol, hi));
            if (trip == 1) dRows.add(trade(s.d, me, dVol, hi));
            if (trip == 2) dRows.add(trade(s.d, me, -dVol, hi));
        }
        OrderRow normal = trade(s.a, s.mE2, small(s.mE2.price()), s.mE2.price());
        s.hourRows.addAll(buys);
        s.hourRows.addAll(sells);
        s.hourRows.add(normal);
        s.lastAorD = latest(List.of(normal, dRows.get(1), sells.get(3)));

        long profit = money(sells) - money(buys);
        if (profit <= PROFIT_LIMIT) throw new IllegalStateException("E: TesterA realised only " + profit + " raw");
        // tolerance covers the normal buy, in case the tool counts the hour's cash instead of sold cost
        add(Case.of("E-hour-profit-flag", "flag", "hour-profit").subject(uuid(s.a)).item(me.item())
                .window(buys.get(0).time(), latest(sells)).amount(profit).tolerance(100 + money(List.of(normal)))
                .orders(ids(sells)).planted("test hook")
                .note("TesterA: 4 round trips buy P / sell 1.5P on bot liquidity, compounding $400k; then a normal buy on "
                        + s.mE2.item()));
    }

    /** E: TesterE trades M_E in one short session starting >= 15 min after A's and D's last order; then the key links. */
    private void storyELateTrader(Story s) {
        long start = s.lastAorD + 15 * MINUTE + 30 * SECOND;
        waitUntil(start);
        long price = await(s.mE.market().getCurrentMarketPriceAsync(), WAIT_MS), vol = small(price);
        trade(s.e, s.mE, vol, price);
        trade(s.e, s.mE, -vol, price);
        addMarketLink(s.a, s.d, "weak", s.mE);
        addMarketLink(s.a, s.e, "none", s.mE);
        addMarketLink(s.d, s.e, "none", s.mE);
    }

    // ==== B cases ===================================================================================================

    /** B3: TesterL buys and sells at 1.5x in one clock hour for just over $1M profit; TesterM just under (clean). */
    private void caseB3(Story s) {
        Mkt m = s.m3;
        long p = m.price(), q = p * 3 / 2;
        for (Tester t : List.of(s.l, s.m)) {
            boolean flag = t == s.l;
            long vol = volumeFor(flag ? PROFIT_LIMIT + 10_000 * SCALE : PROFIT_LIMIT - 10_000 * SCALE, q - p);
            OrderRow buy = trade(t, m, vol, p), sell = trade(t, m, -vol, q);
            s.hourRows.add(buy);
            s.hourRows.add(sell);
            long profit = money(List.of(sell)) - money(List.of(buy));
            if (flag != profit > PROFIT_LIMIT) throw new IllegalStateException("B3: " + t.name() + " realised " + profit + " raw");
            add(Case.of(flag ? "B3-flag" : "B3-clean", flag ? "flag" : "clean", "hour-profit").subject(uuid(t)).item(m.item())
                    .window(buy.time(), latest(List.of(sell))).amount(profit).tolerance(100).orders(sell.orderId())
                    .planted("test hook").note(t.name() + " bought at P and sold at 1.5P (bot liquidity) in one clock hour, "
                            + (flag ? "just over" : "just under") + " $1,000,000"));
        }
        s.designatedProfit = s.l.name();
        s.cleanProfit.add(s.m.name());
    }

    /**
     * B1: TesterF buys at P and sells at 2.5P within seconds (flag, profit far below the $100k cycle alert). Clean
     * look-alikes on their own markets: TesterG with TesterH's fill between buy and sell, TesterI selling >= 40 s later
     * ({@link #caseB1SlowSell}).
     */
    private void caseB1(Story s) {
        OrderRow fb = trade(s.f, s.m1a, small(s.m1a.price()), s.m1a.price());
        OrderRow fs = trade(s.f, s.m1a, -fb.amount(), s.m1a.price() * 5 / 2);
        if (fs.time() - fb.time() >= 30 * SECOND) throw new IllegalStateException("B1: round trip took " + (fs.time() - fb.time()) + " ms");
        roundTripCase("B1-flag", "flag", s.f, s.m1a, fb, fs, "TesterF bought at P and sold at 2.5P within seconds");

        long pb = s.m1b.price(), vol = small(pb);
        OrderRow gb = trade(s.g, s.m1b, vol, pb);
        trade(s.h, s.m1b, vol, pb);
        OrderRow gs = trade(s.g, s.m1b, -vol, pb * 5 / 2);
        if (gs.time() - gb.time() >= 30 * SECOND) throw new IllegalStateException("B1 between: took " + (gs.time() - gb.time()) + " ms");
        roundTripCase("B1-between-clean", "clean", s.g, s.m1b, gb, gs,
                "TesterG's 2.5x round trip within 30 s, but TesterH's buy filled on the market between");

        s.iVol = small(s.m1c.price());
        s.iBuy = trade(s.i, s.m1c, s.iVol, s.m1c.price());
    }

    /** B1 clean (b): TesterI sells at 2.5P at least 40 s after the buy. */
    private void caseB1SlowSell(Story s) {
        waitUntil(s.iBuy.time() + 40 * SECOND);
        OrderRow is = trade(s.i, s.m1c, -s.iVol, s.m1c.price() * 5 / 2);
        roundTripCase("B1-slow-clean", "clean", s.i, s.m1c, s.iBuy, is, "TesterI's 2.5x round trip with the sell 40 s after the buy");
    }

    private void roundTripCase(String id, String expect, Tester t, Mkt m, OrderRow buy, OrderRow sell, String note) {
        String ratio = BigDecimal.valueOf(sell.price()).divide(BigDecimal.valueOf(buy.price()), 4, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        add(Case.of(id, expect, "round-trip").subject(uuid(t)).item(m.item())
                .window(buy.time(), latest(List.of(sell))).amount(ratio).tolerance("0.01")
                .orders(buy.orderId(), sell.orderId()).planted("test hook").note(note));
    }

    /**
     * B2 (TRADE_COOLDOWN_MS is 0 here): TesterJ sends 6 small market buys in one burst, TesterK 5 followed by more than
     * 1 s of nothing (clean). Both fill against one bot ask at the market price.
     */
    private void caseB2(Story s) {
        Mkt m = s.m2;
        long vol = small(m.price());
        bot(m, Order.Type.LIMIT, -11 * vol, m.price());
        for (Tester t : List.of(s.j, s.k)) {
            boolean flag = t == s.j;
            int count = flag ? 6 : 5;
            // start right after a second boundary, so the burst also fits one calendar second
            pumpUntil(() -> now() % SECOND < 50, 2 * SECOND);
            List<CompletableFuture<Boolean>> sent = new ArrayList<>();
            for (int x = 0; x < count; x++)
                sent.add(m.market().putOrderAsync(new Order(m.id(), Order.Type.MARKET, vol, 0, 0, t.uuid(), t.account())));
            for (CompletableFuture<Boolean> f : sent)
                if (!Boolean.TRUE.equals(await(f, WAIT_MS))) throw new IllegalStateException("B2: burst order of " + t.name() + " refused");
            lastOrder.put(t.uuid(), now());
            List<OrderRow> rows = rows(t, m, count);
            for (OrderRow r : rows) check(r, vol, m.price());
            long first = rows.get(0).time(), last = rows.get(count - 1).time();
            if (last - first >= SECOND) throw new IllegalStateException("B2: burst of " + t.name() + " spread over " + (last - first) + " ms");
            if (first / SECOND != last / SECOND) warnings.add("B2 burst of " + t.name() + " crosses a calendar second");
            add(Case.of(flag ? "B2-flag" : "B2-clean", flag ? "flag" : "clean", "order-rate").subject(uuid(t)).item(m.item())
                    .window(first, last).amount(count).tolerance(0).orders(ids(rows)).planted("test hook")
                    .note(t.name() + ": " + count + " market buys in one burst (cooldown set to 0)" + (flag ? "" : ", then over 1 s of nothing")));
            pump(1_100);
        }
    }

    /**
     * B4: two markets with 1,500 backfilled one-minute candles at P (ending 60 min before this clock hour). TesterN's
     * limit buy fills at 11P (flag), TesterO's at 9P on the other market (clean). Both fills also make a spike candle
     * (low P, high 9-11P), keyed as price-spike flags.
     */
    private void caseB4(Story s) {
        long hourStart = now() - now() % HOUR;
        for (Mkt m : List.of(s.m4, s.m4c)) backfill(m, hourStart - HOUR);
        for (Mkt m : List.of(s.m4, s.m4c)) {
            boolean flag = m == s.m4;
            Tester t = flag ? s.n : s.o;
            long price = m.price() * (flag ? 11 : 9);
            OrderRow far = trade(t, m, small(price), price);
            add(Case.of(flag ? "B4-flag" : "B4-clean", flag ? "flag" : "clean", "far-price").subject(m.subject()).item(m.item())
                    .window(far.time(), latest(List.of(far))).orders(far.orderId()).planted("test hook")
                    .note(t.name() + "'s limit buy filled at " + (flag ? 11 : 9) + "x the backfilled usual price"));
            add(Case.of(flag ? "B4-spike-flag" : "B4-clean-spike-flag", "flag", "price-spike").subject(m.subject()).item(m.item())
                    .window(far.time() - MINUTE, latest(List.of(far)) + MINUTE).planted("test hook")
                    .note("the " + (flag ? 11 : 9) + "x fill makes a candle with low P and high " + (flag ? 11 : 9) + "P"));
        }
    }

    /** B5: two backfilled past candles on one market, 1 h apart: low P / high 6P (flag), low 1 raw / high 10 raw (clean). */
    private void caseB5(Story s) {
        Mkt m = s.m5;
        long p = m.price(), hourStart = now() - now() % HOUR, t1 = hourStart - 3 * HOUR, t2 = hourStart - 2 * HOUR;
        writeCandles(m, List.of(new MarketPriceStruct(m.id().getShort(), p, p, 6 * p, t1, 0f),
                new MarketPriceStruct(m.id().getShort(), 5, 1, 10, t2, 0f)));
        add(Case.of("B5-flag", "flag", "price-spike").subject(m.subject()).item(m.item()).window(t1, t1 + MINUTE - 1)
                .planted("test hook").note("backfilled candle low " + p + " high " + 6 * p + " raw (6x)"));
        add(Case.of("B5-clean", "clean", "price-spike").subject(m.subject()).item(m.item()).window(t2, t2 + MINUTE - 1)
                .planted("test hook").note("backfilled candle low 1 high 10 raw: 10x, but the low is at the 1 raw floor"));
    }

    /**
     * B6: TesterP's small filled buy, then its {@code sm:order} ledger row is edited to 0.50 items less (DB edit); TesterQ's
     * identical buy stays untouched (clean).
     */
    private void caseB6(Story s) {
        Mkt m = s.m6;
        long vol = Math.max(small(m.price()), SCALE); // at least 1 item, so 0.50 can come off
        for (Tester t : List.of(s.p, s.q)) {
            boolean flag = t == s.p;
            long from = now();
            OrderRow r = trade(t, m, vol, m.price());
            List<Long> ledger = new ArrayList<>();
            JsonObject itemRow = null;
            for (JsonElement e : ledgerRows(r.orderId())) {
                ledger.add(e.getAsJsonObject().get("id").getAsLong());
                if (e.getAsJsonObject().get("item_id").getAsInt() == m.id().getShort()) itemRow = e.getAsJsonObject();
            }
            if (itemRow == null) throw new IllegalStateException("B6: no ledger row with the item for order " + r.orderId());
            long delta = SCALE / 2;
            if (flag) {
                long amount = itemRow.get("amount").getAsLong();
                JsonObject out = master("ledgerEdit", obj("id", itemRow.get("id").getAsLong(),
                        "set", obj("amount", amount - Long.signum(amount) * delta)));
                if (out.get("updated").getAsInt() != 1) throw new IllegalStateException("B6: ledger edit missed: " + out);
            }
            Case c = Case.of(flag ? "B6-flag" : "B6-clean", flag ? "flag" : "clean", "order-ledger").subject(uuid(t))
                    .item(m.item()).window(from, now()).orders(r.orderId()).ledger(ledger.toArray()).planted(flag ? "DB edit" : "test hook")
                    .note(t.name() + "'s filled buy" + (flag ? "; its sm:order ledger row " + itemRow.get("id") + " edited to 0.50 items less" : ", ledger untouched"));
            add(flag ? c.amount(delta).tolerance(0) : c);
        }
    }

    /** B7 cycle: TesterR round trip at 1.5x for about $150k profit (cycle alert); TesterS about $90k (clean). */
    private void caseB7Cycle(Story s) {
        Mkt m = s.m7c;
        long p = m.price(), q = p * 3 / 2;
        for (Tester t : List.of(s.r, s.s)) {
            long vol = volumeFor((t == s.r ? 150_000 : 90_000) * SCALE, q - p);
            trade(t, m, vol, p);
            trade(t, m, -vol, q);
        }
        s.designatedCycle = s.r.name();
        s.cleanCycle.add(s.s.name());
    }

    /**
     * B7 pump: TesterT buys a little, rests an own sell at 1.5P, then a market buy takes a bot ask at 1.3P (30% move in
     * one tick, over the 20% threshold). TesterU does the same with a 1.1P ask (10%, clean).
     */
    private void caseB7Pump(Story s) {
        for (Tester t : List.of(s.t, s.u)) {
            boolean flag = t == s.t;
            Mkt m = flag ? s.m7p : s.m7q;
            long p = m.price(), vol = small(p), ask = p * (flag ? 13 : 11) / 10;
            trade(t, m, vol, p);
            place(t, m, Order.Type.LIMIT, -vol, p * 3 / 2); // rests above the price, never fills
            bot(m, Order.Type.LIMIT, -vol, ask);
            place(t, m, Order.Type.MARKET, vol, 0);
            check(rows(t, m, 1).get(0), vol, ask);
        }
        s.designatedPump = s.t.name();
        s.cleanPump.add(s.u.name());
    }

    /** B8: TesterV buys and sells on a market, then the slave deletes it; the tool lists it with its order count. */
    private void caseB8(Story s) {
        Mkt m = s.m8;
        long vol = small(m.price());
        OrderRow b = trade(s.v, m, vol, m.price()), sl = trade(s.v, m, -vol, m.price());
        if (!Boolean.TRUE.equals(await(mm().deleteMarketAsync(m.id()), WAIT_MS))) throw new IllegalStateException("B8: market not deleted");
        long deleted = now();
        List<OrderRow> all = collect().orders().stream().filter(o -> o.itemId() == m.id().getShort()).toList();
        add(Case.of("B8-flag", "flag", "market").subject(m.subject()).item(m.item()).window(b.time(), deleted)
                .amount(all.size()).tolerance(0).orders(ids(all)).planted("test hook")
                .note("TesterV traded " + m.item() + ", then the market was deleted from the slave; OrderHistory, candles, ledger and ItemID stay"));
        if (all.size() != 2) warnings.add("B8: " + all.size() + " OrderHistory rows on the deleted market (expected 2: " + b.orderId() + "," + sl.orderId() + ")");
    }

    /**
     * B7 / E: every TradeAlert row since the start is its own case (the tool does not merge alert rows): subject the
     * player, a narrow window around the row's ts, the row's raw amount, its order_ids and their sm:order ledger rows.
     * Ids: {@code <base>-<n>-flag}, n = 1.. in row-id order per player and kind (always numbered: stable ids).
     * Clean B7 subjects must have no row of that kind.
     */
    private void alertCases(Story s) {
        Map<String, List<AlertRow>> groups = new LinkedHashMap<>();
        for (AlertRow a : collect().alerts())
            groups.computeIfAbsent(a.kind() + "|" + a.playerUuid(), k -> new ArrayList<>()).add(a);
        Map<String, String> designated = Map.of(TradeAlerts.Row.KIND_PROFIT, s.designatedProfit,
                TradeAlerts.Row.KIND_CYCLE, s.designatedCycle, TradeAlerts.Row.KIND_PUMP, s.designatedPump);
        Map<String, Set<String>> clean = Map.of(TradeAlerts.Row.KIND_PROFIT, s.cleanProfit,
                TradeAlerts.Row.KIND_CYCLE, s.cleanCycle, TradeAlerts.Row.KIND_PUMP, s.cleanPump);
        Set<String> found = new HashSet<>();
        for (List<AlertRow> rows : groups.values()) {
            rows.sort(Comparator.comparingLong(AlertRow::id));
            AlertRow first = rows.get(0);
            String kind = first.kind();
            String name = first.playerUuid() == null ? null : testerNames.get(UUID.fromString(first.playerUuid()));
            if (name != null && clean.getOrDefault(kind, Set.of()).contains(name))
                throw new IllegalStateException("B7: clean subject " + name + " got a " + kind + " alert: " + rows);
            String label = name == null ? (first.playerUuid() == null ? "none" : first.playerUuid().substring(0, 8)) : name;
            String base = s.a.name().equals(name) ? "E-" + kind
                    : label.equals(designated.get(kind)) ? "B7-" + kind : "B7-" + kind + "-" + label;
            found.add(base);
            for (int n = 0; n < rows.size(); n++) {
                AlertRow a = rows.get(n);
                List<Object> orders = new ArrayList<>();
                List<Long> ledger = new ArrayList<>();
                if (a.orderIds() != null)
                    for (String o : a.orderIds().split(",")) {
                        if (o.isBlank()) continue;
                        long id = Long.parseLong(o.trim());
                        orders.add(id);
                        for (JsonElement e : ledgerRows(id)) ledger.add(e.getAsJsonObject().get("id").getAsLong());
                    }
                Mkt m = a.itemId() == null ? null : markets.get(a.itemId().shortValue());
                Case c = Case.of(base + "-" + (n + 1) + "-flag", "flag", "market-alert:" + kind)
                        .subject(a.playerUuid()).item(m == null ? null : m.item()).window(a.ts() - SECOND, a.ts() + SECOND)
                        .orders(orders.toArray()).ledger(ledger.toArray()).planted("test hook")
                        .note(label + ": TradeAlert row " + a.id());
                if (a.amount() != null) c.amount(a.amount()).tolerance(0);
                else warnings.add("TradeAlert row " + a.id() + " (" + kind + ") has no amount");
                add(c);
            }
        }
        for (String need : List.of("B7-profit", "B7-pump_dump_cycle", "B7-pump_dump_pump", "E-profit", "E-pump_dump_cycle"))
            if (!found.contains(need)) throw new IllegalStateException("alert case " + need + " has no TradeAlert row; rows: " + groups.keySet());

        // the clean look-alikes (the check above made sure they have no row of that kind)
        cleanAlert("B7-profit-clean", TradeAlerts.Row.KIND_PROFIT, s.m, s.m3, "TesterM realised just under $1,000,000 within the hour");
        cleanAlert("B7-pump_dump_cycle-clean", TradeAlerts.Row.KIND_CYCLE, s.s, s.m7c, "TesterS's round trip made about $90k, below $100k");
        cleanAlert("B7-pump_dump_pump-clean", TradeAlerts.Row.KIND_PUMP, s.u, s.m7q, "TesterU's market buy moved the price 10%, below 20%");
    }

    private void cleanAlert(String id, String kind, Tester t, Mkt m, String note) {
        long from = lastOrder.getOrDefault(t.uuid(), since) - 5 * MINUTE;
        add(Case.of(id, "clean", "market-alert:" + kind).subject(uuid(t))
                .item(TradeAlerts.Row.KIND_PROFIT.equals(kind) ? null : m.item()).window(from, now()).planted("test hook").note(note));
    }

    // ==== market + order helpers ====================================================================================

    /**
     * A new market on the next vanilla item without one: plugins detached, virtual book off, no order size limit, so
     * only this suite's bot orders give liquidity and move the price.
     */
    private Mkt newMarket() {
        List<ItemID> existing = await(mm().getAvailableMarketIDsAsync(), WAIT_MS);
        for (Item item : BuiltInRegistries.ITEM) {
            if (STORY_ITEMS.contains(item) || !"minecraft".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) continue;
            ItemID id = ItemIDManager.getItemID(new ItemStack(item));
            if (!id.isValid() || existing.contains(id) || !usedItems.add(id)) continue;
            IAsyncMarket market = await(mm().createMarketAsync(id), WAIT_MS);
            if (market == null) continue; // blacklisted
            if (!Boolean.TRUE.equals(await(FixtureTestRequest.detachPlugins(id), WAIT_MS)))
                throw new IllegalStateException("plugins not detached from " + id.getName());
            MarketSettings settings = await(market.getSettingsAsync(), WAIT_MS);
            settings.virtualOrderbookEnabled = false;
            settings.maxOrderVolume = 0;
            if (!Boolean.TRUE.equals(await(market.setSettingsAsync(settings), WAIT_MS)))
                throw new IllegalStateException("settings of " + id.getName() + " not set");
            long price = await(market.getCurrentMarketPriceAsync(), WAIT_MS);
            if (price < MIN_PRICE) continue; // stays an untraded market
            Mkt m = new Mkt(id, market, price);
            markets.put(id.getShort(), m);
            return m;
        }
        throw new IllegalStateException("no vanilla item without a market left");
    }

    /**
     * Bot liquidity at {@code price}, then {@code t}'s limit order that takes exactly it.
     *
     * @param volume raw item volume, + buy, - sell
     * @return {@code t}'s filled OrderHistory row (checked: full volume at {@code price})
     */
    private OrderRow trade(Tester t, Mkt m, long volume, long price) {
        bot(m, Order.Type.LIMIT, -volume, price);
        place(t, m, Order.Type.LIMIT, volume, price);
        OrderRow r = rows(t, m, 1).get(0);
        check(r, volume, price);
        return r;
    }

    private void bot(Mkt m, Order.Type type, long volume, long price) {
        if (!Boolean.TRUE.equals(await(FixtureTestRequest.botOrder(m.id(), type, volume, price), WAIT_MS)))
            throw new IllegalStateException("bot order refused on " + m.item() + ": " + volume + " @ " + price);
    }

    /** A player order through the slave's player path, at least {@link #ORDER_GAP_MS} after the player's last one. */
    private void place(Tester t, Mkt m, Order.Type type, long volume, long price) {
        long next = lastOrder.getOrDefault(t.uuid(), 0L) + ORDER_GAP_MS;
        waitUntil(next);
        boolean ok = Boolean.TRUE.equals(await(m.market().putOrderAsync(
                new Order(m.id(), type, volume, price, 0, t.uuid(), t.account())), WAIT_MS));
        lastOrder.put(t.uuid(), now());
        if (!ok) throw new IllegalStateException(t.name() + "'s " + type + " order refused on " + m.item() + ": " + volume + " @ " + price);
    }

    /** Waits for {@code n} filled rows of {@code t} on {@code m} not returned before; oldest first. */
    private List<OrderRow> rows(Tester t, Mkt m, int n) {
        long end = now() + ROW_WAIT_MS;
        while (true) {
            List<OrderRow> fresh = collect().orders().stream()
                    .filter(o -> t.uuid().equals(o.player()) && o.itemId() == m.id().getShort() && o.orderId() != null
                            && !seenOrders.contains(o.orderId()))
                    .sorted(Comparator.comparingLong(OrderRow::orderId)).toList();
            if (fresh.size() >= n) {
                fresh = fresh.subList(0, n);
                for (OrderRow o : fresh) seenOrders.add(o.orderId());
                return fresh;
            }
            if (now() > end) throw new IllegalStateException(t.name() + ": " + fresh.size() + " of " + n + " filled orders on " + m.item());
            pump(250);
        }
    }

    private FixtureTestRequest.Output collect() {
        FixtureTestRequest.Output out = await(FixtureTestRequest.collect(since), WAIT_MS);
        if (!out.ok()) throw new IllegalStateException("collect refused");
        return out;
    }

    private static void check(OrderRow r, long volume, long price) {
        if (!Integer.valueOf(OrderRecordStruct.STATUS_FILLED).equals(r.finalStatus()) || r.amount() != volume || r.price() != price)
            throw new IllegalStateException("unexpected fill (want " + volume + " @ " + price + "): " + r);
    }

    /** 1,500 one-minute candles at the market's price (+-0.5% noise) ending at {@code end}. */
    private void backfill(Mkt m, long end) {
        Random noise = new Random(m.id().getShort());
        List<MarketPriceStruct> rows = new ArrayList<>();
        for (int x = 1500; x >= 1; x--) {
            long open = m.price() + Math.round(m.price() * (noise.nextDouble() - 0.5) / 100);
            rows.add(new MarketPriceStruct(m.id().getShort(), open, open - open / 300, open + open / 300, end - x * MINUTE, 0f));
        }
        for (int x = 0; x < rows.size(); x += 250) writeCandles(m, rows.subList(x, Math.min(rows.size(), x + 250)));
    }

    private void writeCandles(Mkt m, List<MarketPriceStruct> rows) {
        Long count = await(FixtureTestRequest.backfillCandles(m.id(), new ArrayList<>(rows)), WAIT_MS);
        if (count == null || count < rows.size()) throw new IllegalStateException("backfill of " + m.item() + ": " + count + " of " + rows.size());
    }

    private void restoreSetting(String name, long value) {
        try {
            await(FixtureTestRequest.setMarketSetting(name, value), WAIT_MS);
        } catch (RuntimeException e) {
            warnings.add(name + " not restored: " + e);
        }
    }

    // ==== bank + ledger helpers =====================================================================================

    private JsonObject master(String step, JsonObject args) {
        return JsonParser.parseString(await(FixtureStepRequest.call(step, args.toString()), MASTER_MS)).getAsJsonObject();
    }

    /** /money add as the fixture admin (an admin event, not a case, like BankSystem's own funding). */
    private void fund(Tester t, long dollars) {
        if (!master("money", obj("action", "add", "user", t.name(), "amount", dollars)).get("ok").getAsBoolean())
            throw new IllegalStateException("funding " + t.name() + " refused");
    }

    private long moneyOf(Tester t) {
        IAsyncBank bank = await(backend.BANK_SYSTEM_API.getServerBankManager().getAsync().getOrCreatePersonalBankAsync(t.uuid(), currency), WAIT_MS);
        if (bank == null) throw new IllegalStateException("no money bank for " + t.name());
        return await(bank.getBalanceAsync(), WAIT_MS);
    }

    /** The master's TransactionLog rows of {@code sm:order:<orderId>} (the request drains the ledger queue first). */
    private JsonArray ledgerRows(long orderId) {
        return master("ledgerRows", obj("ref", "sm:order:" + orderId)).getAsJsonArray("rows");
    }

    // ==== key helpers ===============================================================================================

    private void add(Case c) {
        FixtureKey.current().addCase(c);
        cases.add(c.json.get("id").getAsString());
    }

    /** Story E link of the market kind: player uuids (lowercase, dashed) and the market's item. */
    private void addMarketLink(Tester a, Tester b, String strength, Mkt m) {
        FixtureKey.current().addLink(uuid(a), uuid(b), strength, "market", m.item());
    }

    // ==== small helpers =============================================================================================

    /**
     * Every wait of this suite (MU's {@code await} and {@code pump} included) goes through BankSystem's pump: it also runs
     * ModUtilities' request-timeout sweep, which no tick runs while the server thread blocks here, so a request without
     * a reply still times out.
     */
    @Override
    protected boolean pumpUntil(BooleanSupplier done, long timeoutMillis) {
        return FixtureSteps.pumpUntil(getServer(), done, timeoutMillis);
    }

    private IAsyncMarketManager mm() {
        return backend.MARKET_MANAGER.getAsync();
    }

    /** If less than 5 min of the clock hour are left, waits for the next hour (+5 s): the hour-profit cases fit one hour. */
    private void clockHourGuard() {
        long inHour = now() % HOUR;
        if (inHour >= 55 * MINUTE) waitUntil(now() - inHour + HOUR + 5 * SECOND);
    }

    private static void checkOneClockHour(Story s) {
        long hour = s.hourRows.get(0).time() / HOUR;
        for (OrderRow r : s.hourRows)
            if (r.time() / HOUR != hour) throw new IllegalStateException("hour-profit orders crossed a clock hour: " + r);
    }

    private void waitUntil(long epochMs) {
        long wait = epochMs - now();
        if (wait > 0) pumpUntil(() -> now() >= epochMs, wait + 5 * SECOND);
    }

    /** Raw item volume worth {@code moneyRaw} at {@code price} (at least 1 raw). */
    private static long volumeFor(long moneyRaw, long price) {
        return Math.max(1, moneyRaw * SCALE / price);
    }

    /** Raw money to buy the volume that makes {@code profitRaw} when sold at 1.5x the market's price. */
    private static long costFor(long profitRaw, Mkt m) {
        long p = m.price();
        return volumeFor(profitRaw, p * 3 / 2 - p) * p / SCALE;
    }

    private static long small(long price) {
        return volumeFor(SMALL, price);
    }

    /** Raw money moved by the rows' fills (|volume| * price). */
    private static long money(List<OrderRow> rows) {
        long sum = 0;
        for (OrderRow r : rows) sum += Math.abs(r.amount()) * r.price() / SCALE;
        return sum;
    }

    private static long latest(List<OrderRow> rows) {
        long t = 0;
        for (OrderRow r : rows) t = Math.max(t, Math.max(r.time(), r.finishTime() == null ? 0 : r.finishTime()));
        return t;
    }

    private static Object[] ids(List<OrderRow> rows) {
        return rows.stream().map(OrderRow::orderId).toArray();
    }

    private static String uuid(Tester t) {
        return t.uuid().toString();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /** {@code obj("k", v, ...)}: numbers, strings, booleans and nested JsonElements. */
    private static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int x = 0; x < kv.length; x += 2) {
            Object v = kv[x + 1];
            if (v instanceof JsonElement j) o.add((String) kv[x], j);
            else if (v instanceof Number n) o.addProperty((String) kv[x], n);
            else if (v instanceof Boolean b) o.addProperty((String) kv[x], b);
            else o.addProperty((String) kv[x], String.valueOf(v));
        }
        return o;
    }
}
