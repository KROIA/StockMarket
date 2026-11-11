package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.User;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.kroia.stockmarket.util.PumpDumpDetector;
import net.kroia.stockmarket.util.TradeAlerts;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * T-192 — pump-and-dump detector ({@link PumpDumpDetector}) and the per-admin alert toggle
 * ({@link TradeAlerts#alertsEnabled}, {@code /stockmarket alerts on|off}).
 * <p>
 * Alerts are caught with {@link TradeAlerts#alertCapture} (no Discord post, no admin chat); the server
 * log line is still written. Detector settings come from a fresh {@link StockMarketModSettings.Market}
 * (defaults), never the live config. Synthetic ItemIDs and random player UUIDs keep the detector's
 * static state apart from real players.
 * <p>
 * <b>Server type:</b> master only (the toggle lives in the master's StockMarket user data). The toggle
 * tests add a scratch user and remove it again.
 */
public class PumpDumpDetectorTestSuite extends TestSuite {

    private static StockMarketModBackend.ServerInstances backend;

    /** @param backend the master's server instances (set by DevBootstrap on the master only) */
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        PumpDumpDetectorTestSuite.backend = backend;
    }

    private static final ItemID MARKET = new ItemID((short) 30192, "test:pump_dump");
    private static final long MIN = 60_000L;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.PUMP_DUMP;
    }

    @Override
    public void registerTests() {
        addTest("pump_flagged", this::pumpFlagged);
        addTest("normal_buy_not_flagged", this::normalBuyNotFlagged);
        addTest("cycle_profit_flagged", this::cycleProfitFlagged);
        addTest("cycle_below_threshold_not_flagged", this::cycleBelowThreshold);
        addTest("cycle_order_path_flagged", this::cycleOrderPath);
        addTest("cycle_window_expiry", this::cycleWindowExpiry);
        addTest("detector_off_flags_nothing", this::detectorOff);
        addTest("toggle_off_no_chat_but_recorded", () -> master(this::toggleOffStillRecorded));
        addTest("toggle_default_from_config", () -> master(this::toggleDefault));
        addTest("toggle_persisted", this::togglePersisted);
    }

    // ------------------------------------------------------------------ pump

    /** A market buy that moves the price 30 % (> 20 %) with the buyer's own sell resting above the start is flagged. */
    private TestResult pumpFlagged() {
        UUID p = UUID.randomUUID();
        List<String> alerts = capture(() -> PumpDumpDetector.checkPumps(MARKET, List.of(buy(p)), 100, 130,
                List.of(sell(p, 150)), new StockMarketModSettings.Market()));
        if (alerts.size() != 1 || !alerts.get(0).contains("pump") || !alerts.get(0).contains(p.toString()))
            return fail("alerts " + alerts);
        return pass("1 pump alert for a 30% move with an own sell at 150 > 100");
    }

    /** No flag: small move, no own sell, own sell below the start price, or another player's sell. */
    private TestResult normalBuyNotFlagged() {
        UUID p = UUID.randomUUID();
        StockMarketModSettings.Market s = new StockMarketModSettings.Market();
        List<String> alerts = capture(() -> {
            PumpDumpDetector.checkPumps(MARKET, List.of(buy(p)), 100, 110, List.of(sell(p, 150)), s); // 10 %
            PumpDumpDetector.checkPumps(MARKET, List.of(buy(p)), 100, 130, List.of(), s);              // no sell
            PumpDumpDetector.checkPumps(MARKET, List.of(buy(p)), 100, 130, List.of(sell(p, 90)), s);   // below start
            PumpDumpDetector.checkPumps(MARKET, List.of(buy(p)), 100, 130,
                    List.of(sell(UUID.randomUUID(), 150)), s);                                         // someone else
        });
        return alerts.isEmpty() ? pass("small move / no own sell above start -> no alert") : fail("alerts " + alerts);
    }

    // ------------------------------------------------------------------ cycle

    /** Buy 100 for 1000, sell 100 for 1500 within the window: profit 500 > 400 is returned. */
    private TestResult cycleProfitFlagged() {
        UUID p = UUID.randomUUID();
        PumpDumpDetector.recordBuy(p, MARKET, 100, 1000, 0, 30 * MIN);
        double profit = PumpDumpDetector.recordSell(p, MARKET, 100, 1500, 10 * MIN, 30 * MIN, 400);
        return assertEquals("cycle profit", 500.0, profit);
    }

    /** Profit 300 is not above 400; the partly sold lot keeps its average cost for the next sell. */
    private TestResult cycleBelowThreshold() {
        UUID p = UUID.randomUUID();
        PumpDumpDetector.recordBuy(p, MARKET, 200, 2000, 0, 30 * MIN);                          // avg 10 per raw unit
        double low = PumpDumpDetector.recordSell(p, MARKET, 100, 1300, MIN, 30 * MIN, 400);    // profit 300
        if (!Double.isNaN(low))
            return fail("profit 300 flagged at threshold 400: " + low);
        double high = PumpDumpDetector.recordSell(p, MARKET, 100, 1500, 2 * MIN, 30 * MIN, 400); // rest: profit 500
        return assertEquals("second sell against the remaining lot", 500.0, high);
    }

    /** Through {@link PumpDumpDetector#onOrderFinished} with real orders: the money signs and the alert. */
    private TestResult cycleOrderPath() {
        UUID p = UUID.randomUUID();
        StockMarketModSettings.Market s = new StockMarketModSettings.Market();
        double realProfit = MarketManager.convertToRealAmountStatic(2_000_000L);
        s.PUMP_DUMP_CYCLE_PROFIT.set((float) (realProfit / 2));
        Order buy = buy(p);
        buy.edit(100, -1_000_000L);
        Order sell = new Order(MARKET, Order.Type.MARKET, -100, 0, System.nanoTime(), p, 0);
        sell.edit(-100, 3_000_000L);
        List<String> alerts = capture(() -> {
            PumpDumpDetector.onOrderFinished(MARKET, buy, s);
            PumpDumpDetector.onOrderFinished(MARKET, sell, s);
        });
        if (alerts.size() != 1 || !alerts.get(0).contains("cycle"))
            return fail("alerts " + alerts);
        return pass("buy + sell orders -> 1 cycle alert");
    }

    /** A sell after the window does not count, and a buy after expiry starts a fresh lot. */
    private TestResult cycleWindowExpiry() {
        UUID p = UUID.randomUUID();
        long w = 30 * MIN;
        PumpDumpDetector.recordBuy(p, MARKET, 100, 1000, 0, w);
        double late = PumpDumpDetector.recordSell(p, MARKET, 100, 1_000_000, w + 1, w, 400);
        if (!Double.isNaN(late))
            return fail("sell after the window flagged: " + late);
        PumpDumpDetector.recordBuy(p, MARKET, 100, 1000, 0, w);       // old lot at t=0 ...
        PumpDumpDetector.recordBuy(p, MARKET, 100, 1000, w + 2, w);   // ... dropped by this buy (new lot at w+2)
        double fresh = PumpDumpDetector.recordSell(p, MARKET, 100, 1500, w + 3, w, 400);
        return assertEquals("fresh lot after expiry: only the new buy counts", 500.0, fresh);
    }

    /** With PUMP_DUMP_ENABLED off neither check raises anything. */
    private TestResult detectorOff() {
        UUID p = UUID.randomUUID();
        StockMarketModSettings.Market s = new StockMarketModSettings.Market();
        s.PUMP_DUMP_ENABLED.set(false);
        s.PUMP_DUMP_CYCLE_PROFIT.set(0.0001f);
        Order buy = buy(p);
        buy.edit(100, -1_000L);
        Order sell = new Order(MARKET, Order.Type.MARKET, -100, 0, System.nanoTime(), p, 0);
        sell.edit(-100, 1_000_000L);
        List<String> alerts = capture(() -> {
            PumpDumpDetector.checkPumps(MARKET, List.of(buy(p)), 100, 200, List.of(sell(p, 150)), s);
            PumpDumpDetector.onOrderFinished(MARKET, buy, s);
            PumpDumpDetector.onOrderFinished(MARKET, sell, s);
        });
        return alerts.isEmpty() ? pass("detector off -> no alerts") : fail("alerts " + alerts);
    }

    // ------------------------------------------------------------------ toggle

    /** An admin with alerts off is skipped by the chat loop, but the flag still reaches the record. */
    private TestResult toggleOffStillRecorded() {
        UUID admin = UUID.randomUUID();
        manager().onPlayerJoin(admin, "PumpDumpTestAdmin");
        try {
            if (!TradeAlerts.setAlertsEnabled(admin, false))
                return fail("toggle not stored");
            if (TradeAlerts.alertsEnabled(admin))
                return fail("alerts still on for the admin -> chat would be sent");
            List<String> alerts = capture(() -> PumpDumpDetector.checkPumps(MARKET, List.of(buy(admin)), 100, 130,
                    List.of(sell(admin, 150)), new StockMarketModSettings.Market()));
            return alerts.size() == 1 ? pass("alerts off -> no chat; flag recorded: " + alerts.get(0))
                    : fail("flag not recorded: " + alerts);
        } finally {
            manager().removeUser(admin);
        }
    }

    /** Without an own toggle the config default applies; an own toggle beats it either way; unknown users fail. */
    private TestResult toggleDefault() {
        UUID admin = UUID.randomUUID();
        var def = backend.SERVER_SETTINGS.UTILITIES.ALERTS_DEFAULT_ON;
        Boolean prev = def.get();
        manager().onPlayerJoin(admin, "PumpDumpTestAdmin");
        try {
            def.set(true);
            if (!TradeAlerts.alertsEnabled(admin)) return fail("default true not used");
            def.set(false);
            if (TradeAlerts.alertsEnabled(admin)) return fail("default false not used");
            TradeAlerts.setAlertsEnabled(admin, true);
            if (!TradeAlerts.alertsEnabled(admin)) return fail("on must beat default off");
            def.set(true);
            TradeAlerts.setAlertsEnabled(admin, false);
            if (TradeAlerts.alertsEnabled(admin)) return fail("off must beat default on");
            if (TradeAlerts.setAlertsEnabled(UUID.randomUUID(), true)) return fail("unknown user accepted");
            return pass("config default applies until the admin sets the toggle");
        } finally {
            def.set(prev);
            manager().removeUser(admin);
        }
    }

    /** The toggle survives save/load and a name change; an unset toggle stays unset. */
    private TestResult togglePersisted() {
        User user = new User(UUID.randomUUID(), "PumpDumpTestAdmin");
        CompoundTag unsetTag = new CompoundTag();
        user.save(unsetTag);
        User unset = User.createFromTag(unsetTag);
        if (unset == null || unset.getAlertsEnabled() != null)
            return fail("unset toggle not kept unset");
        user.setAlertsEnabled(false);
        CompoundTag tag = new CompoundTag();
        user.save(tag);
        User loaded = User.createFromTag(tag);
        if (loaded == null || !Boolean.FALSE.equals(loaded.getAlertsEnabled()))
            return fail("toggle lost on save/load");
        if (!Boolean.FALSE.equals(User.createWithChangedName(loaded, "Renamed").getAlertsEnabled()))
            return fail("toggle lost on name change");
        return pass("toggle off survives save/load and rename");
    }

    // ------------------------------------------------------------------ helpers

    private static Order buy(UUID player) {
        return new Order(MARKET, Order.Type.MARKET, 100, 0, System.nanoTime(), player, 0);
    }

    private static Order sell(UUID player, long limit) {
        return new Order(MARKET, Order.Type.LIMIT, -100, limit, System.nanoTime(), player, 0);
    }

    /** Runs {@code body} with {@link TradeAlerts#alertCapture} set and returns the captured log texts. */
    private static List<String> capture(Runnable body) {
        List<String> out = new ArrayList<>();
        TradeAlerts.alertCapture = out::add;
        try {
            body.run();
        } finally {
            TradeAlerts.alertCapture = null;
        }
        return out;
    }

    private TestResult master(Supplier<TestResult> body) {
        return backend == null ? skip("master only") : body.get();
    }

    private static ServerMarketManager manager() {
        return backend.MARKET_MANAGER.getServerMarketManager();
    }
}
