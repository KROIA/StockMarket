package net.kroia.stockmarket.testing.tests;

import net.kroia.banksystem.api.bank.IServerBank;
import net.kroia.banksystem.api.bankaccount.IServerBankAccount;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;
import net.kroia.stockmarket.news.NewsEventDefinition;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.core.cache.MarketCache;
import net.kroia.stockmarket.pluginsystem.plugins.DefaultOrderbookVolumeDistributionPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.NetFlowPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.TargetPriceBot;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * SM-336: long-run market simulation, an observation tool (nothing is asserted or tuned). One market runs for
 * days of server time (20 TPS) with every default plugin on at today's defaults, in production order:
 * NetFlowPlugin, VolatilityPlugin (real scale {@link VolatilityPlugin#DEFAULT_VOLATILITY_SCALE}),
 * DefaultOrderbookVolumeDistributionPlugin (book refill, trend, depth), TargetPriceBot and NewsPlugin (the real
 * news library from {@code config/StockMarket/news/}, events resolved as if the scratch market were the preset's
 * item). Scripted players ({@link #scenario}) trade on it; per scenario the suite writes a per-minute CSV, an
 * in-game-hour OHLC CSV, a news log and a summary JSON. The CSV ends with {@code anchor_price} (the T-213 long-term
 * anchor {@code D x e^a}), {@code anchor_mult} ({@code e^a}) and {@code flow_mult} (the short-term flow part,
 * {@code equilibrium = anchor_price x flow_mult}); the summary's {@code limits} block gives the first tick each cap
 * was hit (-1 = never).
 * <p>
 * Deterministic: the volatility random walk ({@link VolatilityPlugin#test_setSeed}, stepped by a simulated clock,
 * {@link VolatilityPlugin#test_setClock}), the bot's gap guard ({@link TargetPriceBot#test_setGapGuardSeed}) and the
 * news RNG are seeded with {@link #SEED} (news: SEED + 1); news time advances 50 ms per tick. Every scenario uses
 * the same seeds, so the {@code baseline} run is the control for the others.
 * <p>
 * Dev only, gated: its own topic ({@code stockmarket/sm_market_gates/LongRunMarketTestSuite}) and every test
 * skips unless {@code -Dstockmarket.longrun} is set. Properties:
 * <ul>
 *   <li>{@code stockmarket.longrun}: {@code all} or a comma list of scenario names (required)</li>
 *   <li>{@code stockmarket.longrun.markets}: comma list of T-208 markets (default {@code diamond_default};
 *       also {@code dirt}, {@code netherite})</li>
 *   <li>{@code stockmarket.longrun.days}: simulated days of server time (default 10)</li>
 *   <li>{@code stockmarket.longrun.news}: {@code off} leaves the NewsPlugin out (default on)</li>
 *   <li>{@code stockmarket.longrun.volatility}: {@code off} runs the VolatilityPlugin with scale 0, so its walk adds
 *       nothing and the bot target is the bare NetFlow equilibrium (default on)</li>
 *   <li>{@code stockmarket.longrun.wall}: {@code off} sets the SM-340 wall strength to 0 (default on)</li>
 *   <li>{@code stockmarket.longrun.out}: output folder (default {@code longrun} in the game directory)</li>
 * </ul>
 */
public class LongRunMarketTestSuite extends PluginTestSuite {

    /** Seed of the volatility walk and the bot's gap guard; the news RNG uses SEED + 1. */
    static final long SEED = 336L;
    /** Players start after 1 h of plugin-only warm-up (the baseline shows the same first hour). */
    static final int START = 72_000;
    /** Interval of the small, steady trades: 5 min. */
    static final int STEP = 6_000;
    /** One in-game day (24,000 ticks = 20 min). */
    static final int GAME_DAY = 24_000;
    /** One in-game hour (1,000 ticks = 50 s): candle length. */
    static final int GAME_HOUR = 1_000;
    /** Sample interval of the main CSV: 1 real minute. */
    static final int SAMPLE = 1_200;
    /** One real hour in ticks. */
    static final int HOUR = 72_000;

    /** Scenario names, see {@link #scenario}. */
    static final String[] SCENARIOS = {"baseline", "pump", "dump", "steadyBuy", "steadySell", "limitBuy", "cycle",
            "netZero", "accumulateDump", "dayNight", "pumpSlowSell",
            "megaPump", "megaDump", "floodBuy", "floodSell", "floodBuyStop", "pumpTwice", "megaPumpSell"};
    /** Scenarios whose players start with {@link #BIG_CASH} instead of 1e9 (the mega/flood ones would run dry). */
    static final List<String> BIG_CASH_SCENARIOS = List.of("megaPump", "megaDump", "floodBuy", "floodSell", "floodBuyStop",
            "pumpTwice", "megaPumpSell");
    /** Start cash of the {@link #BIG_CASH_SCENARIOS} players (real). */
    static final double BIG_CASH = 1e12;
    /** Size of a mega pump / dump: 2,000,000 real items. */
    static final long MEGA = 2_000_000L;
    /** T-208 markets a scenario can run on ({@link #t208Market}). */
    static final String[] MARKETS = {"diamond_default", "dirt", "netherite"};

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.MARKET_GATES;
    }

    @Override
    public void registerTests() {
        addTest("longrun_determinism", this::test_determinism);
        for (String market : MARKETS)
            for (String sc : SCENARIOS) {
                String name = "longrun_" + market + "_" + sc;
                addTest(name, () -> test_longRun(market, sc));
            }
    }

    // ── Properties ──────────────────────────────────────────────────────────────────────────────

    /** SM-340: false with {@code -Dstockmarket.longrun.wall=off} (wall strength 0, the pre-SM-340 book). */
    private static boolean wallEnabled() {
        return !"off".equalsIgnoreCase(System.getProperty("stockmarket.longrun.wall", "on").trim());
    }

    private static boolean requested(String market, String scenario) {
        String v = System.getProperty("stockmarket.longrun", "").trim();
        if (v.isEmpty()) return false;
        List<String> markets = List.of(System.getProperty("stockmarket.longrun.markets", "diamond_default").split(","));
        return markets.contains(market) && (v.equals("all") || List.of(v.split(",")).contains(scenario));
    }

    private static int ticks() {
        return (int) Math.round(Double.parseDouble(System.getProperty("stockmarket.longrun.days", "10")) * 1_728_000);
    }

    private static boolean newsOn() {
        return !System.getProperty("stockmarket.longrun.news", "on").equals("off");
    }

    private static boolean volatilityOn() {
        return !System.getProperty("stockmarket.longrun.volatility", "on").equals("off");
    }

    private static java.nio.file.Path outDir() throws java.io.IOException {
        return java.nio.file.Files.createDirectories(java.nio.file.Path.of(System.getProperty("stockmarket.longrun.out", "longrun")));
    }

    // ── Players ─────────────────────────────────────────────────────────────────────────────────

    /** What a scripted player does on tick {@code t} (before the plugins run), seeing the current price. */
    @FunctionalInterface
    interface Behaviour {
        void act(Trader me, int t, double price);
    }

    /**
     * One scripted player: its bank account, a start holding, scratch state for its {@link Behaviour} and the
     * P/L bookkeeping (average cost; the start holding is booked at the default price).
     */
    final class Trader {
        final String name;
        final Behaviour behaviour;
        final IServerBankAccount account;
        final UUID uuid;
        final IServerBank items, money;
        final double startItems, startCash;
        final int sf;
        final ServerMarket market;
        /** First tick the cash could not pay one max order at the current price, -1 = never. */
        int cashShortTick = -1;
        /** Behaviour scratch state. */
        int state, stateTick, count;
        long amount;
        final List<Order> orders = new ArrayList<>();
        /** Bookkeeping (real units). */
        double prevItems, prevCash, avgCost, realized, bought, sold, spent, received;

        Trader(String name, long startItems, Behaviour behaviour, ServerMarket market, int sf, double defaultPrice, double startCash) {
            this.name = name;
            this.behaviour = behaviour;
            this.market = market;
            this.sf = sf;
            account = fixture.createBankAccount("longrun_" + name);
            uuid = fixture.createUser("longrun_" + name);
            ItemID moneyID = backend.MARKET_MANAGER.getSync().getTradingCurrencyID();
            account.createBank(itemID, startItems * sf);
            account.createBank(moneyID, MarketManager.convertToRawAmountStatic(startCash));
            items = account.getBank(itemID);
            money = account.getBank(moneyID);
            this.startItems = startItems;
            this.startCash = cash();
            prevItems = startItems;
            prevCash = startCash;
            avgCost = defaultPrice;
        }

        double held() { return items.getTotalBalance() / (double) sf; }
        double cash() { return MarketManager.convertToRealAmountStatic(money.getTotalBalance()); }
        /** @return free (unlocked) items, real */
        double free() { return items.getBalance() / (double) sf; }

        /** Market buy of {@code real} items. */
        void buy(double real) {
            long raw = Math.round(real * sf);
            if (raw > 0) market.putOrder(new Order(itemID, Order.Type.MARKET, raw, 0, System.currentTimeMillis(), uuid, account.getAccountNumber()));
        }

        /** Market sell of up to {@code real} items (capped at the free items). */
        void sellUpTo(double real) {
            long raw = Math.min(Math.round(real * sf), items.getBalance());
            if (raw > 0) sell(market, items, raw, uuid, account);
        }

        /** Limit buy of {@code real} items at {@code price}; the money is locked first, as a real order does. */
        Order limitBuy(double real, double price) {
            long raw = Math.round(real * sf), lp = MarketManager.convertToRawAmountStatic(price);
            money.lockAmount(Math.min(Math.round((double) raw * lp / sf) + 1, money.getBalance()));
            Order o = new Order(itemID, Order.Type.LIMIT, raw, lp, System.currentTimeMillis(), uuid, account.getAccountNumber());
            market.putOrder(o);
            orders.add(o);
            return o;
        }

        /** Cancels this player's resting orders (unfilled rest unlocked). */
        void cancelAll() {
            for (Order o : orders)
                if (!o.isFilled())
                    market.cancelOrder(o.getExecutorPlayerUUID(), o.getTime(), o.getType(), o.getStartPrice(), o.getTargetVolume());
            orders.clear();
        }

        /** Books this tick's fills from the balance changes (average cost, realized P/L). */
        void book() {
            double h = held(), c = cash(), di = h - prevItems, dc = c - prevCash;
            if (di > 1e-9) {
                avgCost = (avgCost * Math.max(0, prevItems) - dc) / h;
                bought += di;
                spent -= dc;
            } else if (di < -1e-9) {
                realized += dc - avgCost * -di;
                sold -= di;
                received += dc;
            }
            prevItems = h;
            prevCash = c;
        }

        /** @return mark-to-market P/L: cash change + holding at {@code price} - start holding at the default price */
        double mtm(double price, double defaultPrice) {
            return cash() - startCash + held() * price - startItems * defaultPrice;
        }
    }

    /**
     * A scenario: player names, their start holdings (real items) and behaviours.
     */
    record Scenario(String[] names, long[] startItems, Behaviour[] players) {
        static Scenario of(Object... nameItemsBehaviour) {
            int n = nameItemsBehaviour.length / 3;
            String[] names = new String[n];
            long[] items = new long[n];
            Behaviour[] b = new Behaviour[n];
            for (int i = 0; i < n; i++) {
                names[i] = (String) nameItemsBehaviour[3 * i];
                items[i] = ((Number) nameItemsBehaviour[3 * i + 1]).longValue();
                b[i] = (Behaviour) nameItemsBehaviour[3 * i + 2];
            }
            return new Scenario(names, items, b);
        }
    }

    /** Buys {@code amount} every {@link #STEP} from {@link #START} on, while {@code t < until}. */
    static Behaviour steadyBuy(double amount, int until) {
        return (me, t, p) -> { if (t >= START && t < until && (t - START) % STEP == 0) me.buy(amount); };
    }

    /** Sells {@code amount} every {@link #STEP} from {@link #START} on. */
    static Behaviour steadySell(double amount) {
        return (me, t, p) -> { if (t >= START && (t - START) % STEP == 0) me.sellUpTo(amount); };
    }

    /** Buys {@code orders} max orders from {@code start}, one every 10 ticks. */
    static Behaviour pump(long maxOrder, int orders) {
        return (me, t, p) -> {
            if (t >= START && me.count < orders && (t - START) % 10 == 0) { me.buy(maxOrder); me.count++; }
        };
    }

    /** Market-buys {@code total} items from tick {@code from} on, one max order {@code maxOrder} per 10 ticks. */
    static Behaviour megaBuy(long maxOrder, long total, int from) {
        long[] done = {0};
        return (me, t, p) -> {
            if (t < from || done[0] >= total || (t - from) % 10 != 0) return;
            long n = Math.min(maxOrder, total - done[0]);
            me.buy(n);
            done[0] += n;
        };
    }

    /**
     * Market-sells everything free from tick {@code from} on, one max order {@code maxOrder} per 10 ticks.
     * SM-339: one sell-off only. As soon as a sell was not fully filled (rest cancelled or still open for lack of
     * bids, seen 10 ticks later as a total holding above the expected one) the player stops selling for the rest
     * of the run, so the market can recover. Uses {@link Trader#count} (sells sent), {@link Trader#amount}
     * (expected raw holding after the last sell) and {@link Trader#state} (1 = stopped).
     */
    static Behaviour sellAll(long maxOrder, int from) {
        return (me, t, p) -> {
            if (t < from || (t - from) % 10 != 0 || me.state == 1) return;
            if (me.count > 0 && me.items.getTotalBalance() > me.amount) { me.state = 1; return; }
            long raw = Math.min(Math.round(maxOrder * (double) me.sf), me.items.getBalance());
            if (raw <= 0) return;
            me.amount = me.items.getTotalBalance() - raw;
            me.sellUpTo(maxOrder);
            me.count++;
        };
    }

    /** Market-buys {@code maxOrder} every minute ({@link #SAMPLE}) from {@link #START} on, while {@code t < until}. */
    static Behaviour floodBuy(long maxOrder, int until) {
        return (me, t, p) -> { if (t >= START && t < until && (t - START) % SAMPLE == 0) me.buy(maxOrder); };
    }

    /**
     * Builds a scenario. Sizes scale with the market's max order M (diamond 3,200) and the small trade
     * S = M / 50 (diamond 64); all players start at {@link #START} (1 h).
     * <ul>
     *   <li>baseline: no players (control: the plugins' own drift and noise)</li>
     *   <li>pump: 60 market buys of M, one per 10 ticks (diamond 192,000), then holds to the end</li>
     *   <li>dump: starts with 60 M, sells it in 60 market sells of M, one per 10 ticks, then nothing</li>
     *   <li>steadyBuy: a market buy of S every 5 min for the whole run</li>
     *   <li>steadySell: starts with enough items, a market sell of S every 5 min for the whole run</li>
     *   <li>limitBuy: three resting limit buys of M each at 95 / 90 / 80% of the price at the start, kept to the end</li>
     *   <li>cycle: repeat: limit buy of 10 M at 110% of the price (walks the book; the unfilled rest is cancelled
     *       after 1 min), hold 4 h, market sell everything (M per 10 ticks, at most 1 h), pause 4 h</li>
     *   <li>netZero: one player buys S every 5 min, another sells S every 5 min (net flow 0)</li>
     *   <li>accumulateDump: buys S every 5 min for the first 70% of the run, then dumps the whole position
     *       (M per 10 ticks)</li>
     *   <li>dayNight: every 5 min buys 2 S during the in-game day (first half of each 24,000 ticks), sells 2 S
     *       (what it holds) at night</li>
     *   <li>pumpSlowSell: the pump, holds 1 h, then sells 1% of the pumped position every 5 min (exploit attempt)</li>
     * </ul>
     * Mega / flood scenarios (absolute sizes in real items, M per 10 ticks is the order limit; players start with
     * {@link #BIG_CASH}):
     * <ul>
     *   <li>megaPump: market-buys 2,000,000 from 1 h on, M per 10 ticks (diamond: 625 orders, ~5 min), then holds to
     *       the end</li>
     *   <li>megaDump: starts with 2,000,000 and sells them from 1 h on, M per 10 ticks; stops for good after the
     *       first sell that is not fully filled (lack of bids) or when everything is sold, then nothing (one
     *       sell-off, then the market may recover)</li>
     *   <li>floodBuy: a market buy of M every minute for the whole run (diamond, 10 days: ~46M items)</li>
     *   <li>floodSell: starts with enough items, a market sell of M every minute for the whole run</li>
     *   <li>floodBuyStop: floodBuy until day 2 of the run, then nothing (how far the anchor moved, what the price
     *       does once the flow stops)</li>
     *   <li>pumpTwice: megaPump at 1 h, a second megaPump at day 3, holds both (does the anchor stack?)</li>
     *   <li>megaPumpSell: megaPump, holds 2 days after the last buy, then sells at M per 10 ticks until everything
     *       is sold or the first sell is not fully filled; never resells after that</li>
     * </ul>
     *
     * @param name     scenario name ({@link #SCENARIOS})
     * @param maxOrder M (real items)
     * @param ticks    run length
     */
    static Scenario scenario(String name, long maxOrder, int ticks) {
        double s = Math.max(1, maxOrder / 50);
        long steadyTotal = (long) Math.ceil(s * ((ticks - START) / (double) STEP + 1));
        switch (name) {
            case "baseline": return Scenario.of();
            case "pump": return Scenario.of("pumper", 0, pump(maxOrder, 60));
            case "dump": return Scenario.of("dumper", 60 * maxOrder, (Behaviour) (me, t, p) -> {
                if (t >= START && (t - START) % 10 == 0 && me.count < 60 && me.free() > 0) { me.sellUpTo(maxOrder); me.count++; }
            });
            case "steadyBuy": return Scenario.of("buyer", 0, steadyBuy(s, Integer.MAX_VALUE));
            case "steadySell": return Scenario.of("seller", steadyTotal, steadySell(s));
            case "limitBuy": return Scenario.of("limit", 0, (Behaviour) (me, t, p) -> {
                if (t == START) for (double m : new double[]{0.95, 0.90, 0.80}) me.limitBuy(maxOrder, p * m);
            });
            case "cycle": return Scenario.of("cycler", 0, (Behaviour) (me, t, p) -> {
                // state 0 buy, 1 buy rest pending, 2 hold, 3 sell, 4 pause
                if (t < START) return;
                if (t == START) me.stateTick = START;
                int rel = t - me.stateTick;
                switch (me.state) {
                    case 0 -> { me.limitBuy(10 * maxOrder, p * 1.10); me.state = 1; me.stateTick = t; }
                    case 1 -> { if (rel >= SAMPLE) { me.cancelAll(); me.state = 2; me.stateTick = t; } }
                    case 2 -> { if (rel >= 4 * HOUR) { me.state = 3; me.stateTick = t; } }
                    case 3 -> {
                        if (rel % 10 != 0) return;
                        if (me.free() > 0 && rel < HOUR) me.sellUpTo(maxOrder);
                        else { me.state = 4; me.stateTick = t; me.count++; }
                    }
                    default -> { if (rel >= 4 * HOUR) { me.state = 0; me.stateTick = t; } }
                }
            });
            case "netZero": return Scenario.of("buyer", 0, steadyBuy(s, Integer.MAX_VALUE), "seller", steadyTotal, steadySell(s));
            case "accumulateDump": {
                int until = START + (int) (0.7 * (ticks - START));
                Behaviour acc = steadyBuy(s, until);
                return Scenario.of("accumulator", 0, (Behaviour) (me, t, p) -> {
                    acc.act(me, t, p);
                    if (t >= until && (t - until) % 10 == 0 && me.free() > 0) me.sellUpTo(maxOrder);
                });
            }
            case "dayNight": return Scenario.of("dayNight", 0, (Behaviour) (me, t, p) -> {
                if (t < START || (t - START) % STEP != 0) return;
                if (t % GAME_DAY < GAME_DAY / 2) me.buy(2 * s);
                else me.sellUpTo(2 * s);
            });
            case "pumpSlowSell": {
                Behaviour pump = pump(maxOrder, 60);
                int sellFrom = START + 600 + HOUR;
                return Scenario.of("pumper", 0, (Behaviour) (me, t, p) -> {
                    pump.act(me, t, p);
                    if (t >= sellFrom && (t - sellFrom) % STEP == 0) me.sellUpTo(0.6 * maxOrder);
                });
            }
            case "megaPump": return Scenario.of("pumper", 0, megaBuy(maxOrder, MEGA, START));
            case "megaDump": return Scenario.of("dumper", MEGA, sellAll(maxOrder, START));
            case "floodBuy": return Scenario.of("flooder", 0, floodBuy(maxOrder, Integer.MAX_VALUE));
            case "floodSell": return Scenario.of("flooder", maxOrder * ((ticks - START) / SAMPLE + 1), (Behaviour) (me, t, p) -> {
                if (t >= START && (t - START) % SAMPLE == 0) me.sellUpTo(maxOrder);
            });
            case "floodBuyStop": return Scenario.of("flooder", 0, floodBuy(maxOrder, 48 * HOUR));
            case "pumpTwice": {
                Behaviour first = megaBuy(maxOrder, MEGA, START), second = megaBuy(maxOrder, MEGA, 72 * HOUR);
                return Scenario.of("pumper", 0, (Behaviour) (me, t, p) -> { first.act(me, t, p); second.act(me, t, p); });
            }
            case "megaPumpSell": {
                Behaviour pump = megaBuy(maxOrder, MEGA, START);
                Behaviour sell = sellAll(maxOrder, START + 10 * (int) ((MEGA + maxOrder - 1) / maxOrder) + 48 * HOUR);
                return Scenario.of("pumper", 0, (Behaviour) (me, t, p) -> { pump.act(me, t, p); sell.act(me, t, p); });
            }
            default: throw new IllegalArgumentException("unknown SM-336 scenario " + name);
        }
    }

    // ── News ────────────────────────────────────────────────────────────────────────────────────

    /**
     * The production NewsPlugin with its environment seams pinned for the simulation: events resolve as if the
     * scratch market were {@code as} (e.g. diamond), default per-market settings, publish time and game day from
     * the simulated clock, a seeded RNG and a publisher that only logs. No world registry: requirements are not
     * checked (every event counts as allowed).
     */
    static final class SimNews extends NewsPlugin {
        final ItemID market, as;
        final long[] clockMs;

        /**
         * @param market  the scratch market
         * @param as      the item the events resolve against
         * @param clockMs simulated clock (ms), read on publish
         * @param seed    news RNG seed
         * @param log     CSV body of published events ("time_s,event,impact,peak,duration_s")
         */
        SimNews(ItemID market, ItemID as, long[] clockMs, long seed, StringBuilder log) {
            this.market = market;
            this.as = as;
            this.clockMs = clockMs;
            setLoggerEnabled(false);
            test_setRandom(new Random(seed));
            setPublisher(r -> log.append(String.format(Locale.ROOT, "%.2f,%s,%s,%.4g,%d%n", clockMs[0] / 1000.0,
                    r.getEventId(), r.getImpactType(), r.getPeakFactor(), r.getTotalDurationSeconds())));
        }

        /** Maps a resolution against {@code as} onto the scratch market. */
        private Map<ItemID, Float> remap(Map<ItemID, Float> resolved) {
            Map<ItemID, Float> out = new LinkedHashMap<>();
            Float w = resolved.get(as);
            if (w != null) out.put(market, w);
            return out;
        }

        @Override
        protected Map<ItemID, Float> resolveEventMarkets(NewsEventDefinition definition, Collection<ItemID> candidates) {
            return candidates.contains(market) ? remap(definition.resolveMarkets(List.of(as))) : new LinkedHashMap<>();
        }

        @Override
        protected Map<ItemID, Float> resolveStepMarkets(NewsEventDefinition definition, NewsEventDefinition.StepDefinition step,
                                                        Map<ItemID, Float> eventLevelResolved, Collection<ItemID> stepCandidates) {
            if (step.getMarkets() == null) return super.resolveStepMarkets(definition, step, eventLevelResolved, stepCandidates);
            return remap(NewsEventDefinition.resolveMatchers(step.getMarkets(), List.of(as)));
        }

        @Override protected Settings settingsFor(ItemID marketID) { return Settings.createDefault(); }
        @Override protected long currentEpochMs() { return clockMs[0]; }
        @Override protected long currentGameDay() { return clockMs[0] / (GAME_DAY * 50L); }
    }

    /** @return the item a T-208 market stands for (news matching) */
    private static ItemID presetItem(String market) {
        return ItemID.of(new ItemStack(market.equals("dirt") ? Items.DIRT : market.equals("netherite") ? Items.NETHERITE_INGOT : Items.DIAMOND));
    }

    // ── Simulation ──────────────────────────────────────────────────────────────────────────────

    /**
     * Result of {@link #simulate}.
     *
     * @param hash      FNV hash of the price after every tick (determinism)
     * @param summary   summary JSON
     * @param newsCount published news events
     */
    record Result(long hash, long wallNanos, int ticks, String summary, int newsCount, String line) {}

    /**
     * Runs one scenario on the scratch market for {@code ticks} ticks.
     *
     * @param volatility false = VolatilityPlugin scale 0 (no walk, bot target = equilibrium)
     * @param csv     per-minute CSV (with header), or null
     * @param candles in-game-hour OHLC CSV (with header), or null
     * @param newsLog published news CSV (with header)
     */
    Result simulate(ReplaySetup s, String scenarioName, int ticks, boolean news, boolean volatility, StringBuilder csv,
                    StringBuilder candles, StringBuilder newsLog) {
        ServerMarket market = (ServerMarket) serverMarket;
        StockMarketModSettings.Market modSettings = backend.SERVER_SETTINGS.MARKET;
        Float prevHalfLife = modSettings.NET_FLOW_HALF_LIFE_MINUTES.get();
        Float prevAlert = modSettings.PROFIT_ALERT_THRESHOLD.get();
        int sf = backend.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor();
        MarketSettings settings = market.getSettings();
        long prevDefault = settings.defaultPrice;
        float prevAbundance = settings.naturalAbundance;
        long prevMaxOrder = settings.maxOrderVolume;
        long originalPrice = market.getCurrentMarketPrice();
        long[] clockMs = {1_000_000L};
        List<Trader> traders = new ArrayList<>();
        try {
            modSettings.NET_FLOW_HALF_LIFE_MINUTES.set(s.halfLifeMinutes());
            modSettings.PROFIT_ALERT_THRESHOLD.set(0f);
            settings.defaultPrice = MarketManager.convertToRawAmountStatic(s.price());
            settings.naturalAbundance = s.abundance();
            settings.maxOrderVolume = s.maxOrder();
            market.test_clearOrderbook();
            market.test_clearIncomingOrderBuffers();
            market.test_setCurrentMarketPrice(settings.defaultPrice);
            market.resetNetPlayerItemFlow();

            MarketCache cache = new MarketCache(market);
            DefaultOrderbookVolumeDistributionPlugin.DistributionCalculator calc =
                    harnessCalc(s.price(), DefaultOrderbookVolumeDistributionPlugin.depthScaleFor(s.price(), s.abundance(), 1f));
            cache.getPluginOrderBook().registerDefaultVolumeDistributionCalculator(calc);
            market.test_resetVirtualOrderBookVolume();
            List<MarketInterface> markets = List.of(new MarketInterface(cache.getPluginMarket(), cache.getPluginOrderBook()));
            IPluginOrderBook book = cache.getPluginOrderBook();

            // Every default plugin at today's defaults, seeded, walk stepped by the simulated clock
            VolatilityPlugin.test_setSeed(SEED);
            VolatilityPlugin.test_setClock(() -> clockMs[0]);
            TargetPriceBot.test_setGapGuardSeed(SEED);
            HarnessModel model = new HarnessModel(itemID, NetFlowPlugin.Settings.createDefault(),
                    volatility ? VolatilityPlugin.DEFAULT_VOLATILITY_SCALE : 0f);
            DefaultOrderbookVolumeDistributionPlugin refill = newHarnessRefill();
            // SM-340: the registered calculator is the harness one, so the plugin's slow-average wall is mirrored onto
            // it (production registers the plugin's own calculator); stockmarket.longrun.wall=off: strength 0
            if (!wallEnabled())
                applySettings(refill, DefaultOrderbookVolumeDistributionPlugin.Settings.CODEC,
                        DefaultOrderbookVolumeDistributionPlugin.Settings.createDefault().withWall(
                                DefaultOrderbookVolumeDistributionPlugin.DEFAULT_WALL_PERIOD_MINUTES,
                                DefaultOrderbookVolumeDistributionPlugin.DEFAULT_WALL_DEAD_ZONE, 0f,
                                DefaultOrderbookVolumeDistributionPlugin.DEFAULT_WALL_CAP));
            refill.test_mirrorWallTo(itemID, calc);
            TargetPriceBot bot = new TargetPriceBot();
            bot.setLoggerEnabled(false);
            bot.onMarketSubscribed(itemID);
            applySettings(bot, TargetPriceBot.Settings.CODEC, TargetPriceBot.Settings.createDefault());
            newsLog.append("time_s,event,impact,peak_factor,duration_s\n");
            SimNews newsPlugin = news ? new SimNews(itemID, presetItem(s.market()), clockMs, SEED + 1, newsLog) : null;
            if (newsPlugin != null) newsPlugin.reloadLibrary();

            Scenario sc = scenario(scenarioName, s.maxOrder(), ticks);
            double startCash = BIG_CASH_SCENARIOS.contains(scenarioName) ? BIG_CASH : 1e9;
            for (int i = 0; i < sc.names().length; i++)
                traders.add(new Trader(sc.names()[i], sc.startItems()[i], sc.players()[i], market, sf, s.price(), startCash));

            if (csv != null) {
                csv.append("tick,time_s,game_day,price,bot_target,equilibrium,default_price,saturated_flow,net_flow");
                for (Trader tr : traders) csv.append(',').append(tr.name).append("_items,").append(tr.name).append("_cash_delta,")
                        .append(tr.name).append("_mtm_pl");
                csv.append(",anchor_price,anchor_mult,flow_mult\n");
            }
            if (candles != null) candles.append("game_hour,tick,game_day,open,high,low,close\n");

            double price = s.price(), min = price, max = price, startPrice = price;
            double cO = price, cH = price, cL = price;
            List<Double> minutes = new ArrayList<>();
            // First tick each cap was hit, -1 = never (read-only: nothing below changes the simulation)
            int eqMaxTick = -1, eqMinTick = -1, anchorMaxTick = -1, anchorMinTick = -1, anchorRateTick = -1, anchorRateTicks = 0;
            double anchorMin = 1, anchorMax = 1;
            long hash = 0xcbf29ce484222325L;
            long wallStart = System.nanoTime();
            for (int t = 0; t < ticks; t++) {
                double d = MarketManager.convertToRealAmountStatic(settings.defaultPrice);
                if (t % SAMPLE == 0) {
                    minutes.add(price);
                    if (csv != null) {
                        csv.append(String.format(Locale.ROOT, "%d,%.0f,%.4f,%.6g,%.6g,%.6g,%.6g,%.6g,%.6g", t, t / 20.0, t / (double) GAME_DAY,
                                price, bot.test_getTargetPrice(itemID), model.equilibrium(itemID, d, s.abundance()), d,
                                model.getSaturatedFlow(itemID), market.getNetPlayerItemFlow() / (double) sf));
                        for (Trader tr : traders)
                            csv.append(String.format(Locale.ROOT, ",%.6g,%.6g,%.6g", tr.held(), tr.cash() - tr.startCash, tr.mtm(price, s.price())));
                        double am = model.flow.getAnchorMultiplier(itemID);
                        csv.append(String.format(Locale.ROOT, ",%.6g,%.6g,%.6g\n", d * am, am,
                                model.equilibrium(itemID, d, s.abundance()) / (d * am)));
                    }
                }
                if (t % GAME_HOUR == 0) { cO = cH = cL = price; }
                if (t > 0 && t % (24 * HOUR) == 0)
                    StockMarketMod.LOGGER.info("[SM-336] {} {} day {} price {} ({} ticks/s)", s.market(), scenarioName, t / (24 * HOUR),
                            String.format(Locale.ROOT, "%.4g", price), (long) (t * 1e9 / (System.nanoTime() - wallStart)));

                for (Trader tr : traders) tr.behaviour.act(tr, t, price);
                model.update(markets);
                refill.update(markets);
                bot.update(markets);
                if (newsPlugin != null) {
                    newsPlugin.advanceTime(50, markets);
                    newsPlugin.finalize(markets);
                }
                cache.apply();
                market.update();
                clockMs[0] += 50;
                for (Trader tr : traders) {
                    tr.book();
                    if (tr.cashShortTick < 0 && tr.cash() < price * s.maxOrder()) tr.cashShortTick = t;
                }
                {
                    NetFlowPlugin.Settings fs = model.flow.getMarketSettings(itemID);
                    NetFlowPlugin.FlowBand band = NetFlowPlugin.flowBand(s.abundance(), fs, model.getBookEFold(itemID));
                    double f = NetFlowPlugin.flowExponent(model.getSaturatedFlow(itemID), band);
                    double aLn = Math.log(model.flow.getAnchorMultiplier(itemID));
                    anchorMin = Math.min(anchorMin, Math.exp(aLn));
                    anchorMax = Math.max(anchorMax, Math.exp(aLn));
                    if (band != null && eqMaxTick < 0 && f >= band.lnMaxMult() - 1e-12) eqMaxTick = t;
                    if (band != null && eqMinTick < 0 && f <= band.lnMinMult() + 1e-12) eqMinTick = t;
                    if (fs != null && fs.anchorEnabled()) {
                        if (anchorMaxTick < 0 && aLn >= Math.log(fs.anchorMaxMult()) - 1e-9) anchorMaxTick = t;
                        if (anchorMinTick < 0 && aLn <= Math.log(fs.anchorMinMult()) + 1e-9) anchorMinTick = t;
                        if (Math.abs(f) / fs.anchorTimeDays() > fs.anchorMaxMovePerDay()) {
                            anchorRateTicks++;
                            if (anchorRateTick < 0) anchorRateTick = t;
                        }
                    }
                }

                price = MarketManager.convertToRealAmountStatic(market.getCurrentMarketPrice());
                hash = (hash ^ Double.doubleToLongBits(price)) * 0x100000001b3L;
                min = Math.min(min, price);
                max = Math.max(max, price);
                cH = Math.max(cH, price);
                cL = Math.min(cL, price);
                if (candles != null && (t + 1) % GAME_HOUR == 0)
                    candles.append(String.format(Locale.ROOT, "%d,%d,%.4f,%.6g,%.6g,%.6g,%.6g%n", t / GAME_HOUR, t + 1 - GAME_HOUR,
                            (t + 1 - GAME_HOUR) / (double) GAME_DAY, cO, cH, cL, price));
            }
            long wall = System.nanoTime() - wallStart;
            minutes.add(price);

            // Largest move over any 60 sampled minutes
            double rise = 0, drop = 0;
            for (int i = 0; i + 60 < minutes.size(); i++) {
                double m = minutes.get(i + 60) / minutes.get(i) - 1;
                rise = Math.max(rise, m);
                drop = Math.min(drop, m);
            }
            double end = price, d = MarketManager.convertToRealAmountStatic(settings.defaultPrice);
            StringBuilder json = new StringBuilder("{\n");
            json.append(String.format(Locale.ROOT, "  \"market\": \"%s\", \"scenario\": \"%s\", \"days\": %.4f, \"ticks\": %d,%n",
                    s.market(), scenarioName, ticks / 1_728_000.0, ticks));
            json.append(String.format(Locale.ROOT, "  \"seeds\": {\"volatility\": %d, \"gap_guard\": %d, \"news\": %s},%n", SEED, SEED,
                    news ? String.valueOf(SEED + 1) : "null"));
            json.append(String.format(Locale.ROOT, "  \"wall_s\": %.1f, \"ticks_per_s\": %.0f, \"news_events\": %d,%n", wall / 1e9,
                    ticks * 1e9 / wall, newsCount(newsLog)));
            json.append(String.format(Locale.ROOT, "  \"default_price_start\": %.6g, \"default_price_end\": %.6g, \"max_order\": %d,%n",
                    s.price(), d, s.maxOrder()));
            json.append(String.format(Locale.ROOT, "  \"price\": {\"start\": %.6g, \"end\": %.6g, \"min\": %.6g, \"max\": %.6g, "
                            + "\"end_pct_of_start\": %.2f, \"max_rise_1h_pct\": %.2f, \"max_drop_1h_pct\": %.2f},%n",
                    startPrice, end, min, max, 100 * end / startPrice, 100 * rise, 100 * drop));
            json.append(String.format(Locale.ROOT, "  \"volatility\": %s, \"start_cash\": %.6g,%n", volatility, startCash));
            json.append(String.format(Locale.ROOT, "  \"anchor_mult\": {\"end\": %.6g, \"min\": %.6g, \"max\": %.6g},%n",
                    model.flow.getAnchorMultiplier(itemID), anchorMin, anchorMax));
            json.append(String.format(Locale.ROOT, "  \"limits\": {\"equilibrium_max_cap_first_tick\": %d, "
                            + "\"equilibrium_min_cap_first_tick\": %d, \"anchor_max_cap_first_tick\": %d, "
                            + "\"anchor_min_cap_first_tick\": %d, \"anchor_rate_cap_first_tick\": %d, "
                            + "\"anchor_rate_capped_hours\": %.2f},%n",
                    eqMaxTick, eqMinTick, anchorMaxTick, anchorMinTick, anchorRateTick, anchorRateTicks / (double) HOUR));
            json.append("  \"players\": [");
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "%s %s: start %.4g end %.4g min %.4g max %.4g 1h +%.1f%%/%.1f%%",
                    s.market(), scenarioName, startPrice, end, min, max, 100 * rise, 100 * drop));
            for (int i = 0; i < traders.size(); i++) {
                Trader tr = traders.get(i);
                double held = tr.held(), cashDelta = tr.cash() - tr.startCash;
                double unrealized = held * (end - tr.avgCost), mtm = tr.mtm(end, s.price());
                double liq = cashDelta + markToBook(book, end, held) - tr.startItems * s.price();
                json.append(i == 0 ? "\n" : ",\n").append(String.format(Locale.ROOT,
                        "    {\"name\": \"%s\", \"start_items\": %.6g, \"end_items\": %.6g, \"bought\": %.6g, \"sold\": %.6g, "
                                + "\"spent\": %.6g, \"received\": %.6g, \"cash_delta\": %.6g, \"avg_cost\": %.6g, "
                                + "\"realized_pl\": %.6g, \"unrealized_pl\": %.6g, \"mtm_pl\": %.6g, \"liquidation_pl\": %.6g, "
                                + "\"cash_short_first_tick\": %d}",
                        tr.name, tr.startItems, held, tr.bought, tr.sold, tr.spent, tr.received, cashDelta, tr.avgCost,
                        tr.realized, unrealized, mtm, liq, tr.cashShortTick));
                line.append(String.format(Locale.ROOT, " | %s mtm %+.0f realized %+.0f liq %+.0f", tr.name, mtm, tr.realized, liq));
            }
            json.append(traders.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
            return new Result(hash, wall, ticks, json.toString(), newsCount(newsLog), line.toString());
        } finally {
            for (Trader tr : traders) tr.cancelAll();
            VolatilityPlugin.test_setSeed(null);
            VolatilityPlugin.test_setClock(null);
            modSettings.NET_FLOW_HALF_LIFE_MINUTES.set(prevHalfLife);
            modSettings.PROFIT_ALERT_THRESHOLD.set(prevAlert);
            settings.defaultPrice = prevDefault;
            settings.naturalAbundance = prevAbundance;
            settings.maxOrderVolume = prevMaxOrder;
            market.test_setDefaultVolumeProviderFunction(null);
            market.test_clearIncomingOrderBuffers();
            market.test_clearOrderbook();
            market.test_setCurrentMarketPrice(originalPrice);
            market.resetNetPlayerItemFlow();
        }
    }

    private static int newsCount(StringBuilder log) {
        int n = -1;  // header
        for (int i = 0; i < log.length(); i++) if (log.charAt(i) == '\n') n++;
        return Math.max(0, n);
    }

    // ── Tests ───────────────────────────────────────────────────────────────────────────────────

    /** One market x scenario: runs it and writes {market}_{scenario}.csv / _candles.csv / _news.csv / _summary.json. */
    private TestResult test_longRun(String marketName, String scenarioName) {
        if (!requested(marketName, scenarioName)) return skip("SM-336 long run not requested");
        try {
            StringBuilder csv = new StringBuilder(), candles = new StringBuilder(), newsLog = new StringBuilder();
            Result r = simulate(t208Market(marketName), scenarioName, ticks(), newsOn(), volatilityOn(), csv, candles, newsLog);
            java.nio.file.Path dir = outDir();
            String base = marketName + "_" + scenarioName;
            boolean ok = t208WriteFile(dir.resolve(base + ".csv"), csv.toString())
                    & t208WriteFile(dir.resolve(base + "_candles.csv"), candles.toString())
                    & t208WriteFile(dir.resolve(base + "_news.csv"), newsLog.toString())
                    & t208WriteFile(dir.resolve(base + "_summary.json"), r.summary());
            String msg = String.format(Locale.ROOT, "[SM-336] %s | %d ticks in %.0f s (%.0f ticks/s), %d news", r.line(), r.ticks(),
                    r.wallNanos() / 1e9, r.ticks() * 1e9 / r.wallNanos(), r.newsCount());
            StockMarketMod.LOGGER.info(msg);
            return ok ? pass(msg) : fail("could not write the output files: " + msg);
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("[SM-336] " + marketName + " " + scenarioName, e);
            return fail("Exception: " + e);
        }
    }

    /** Runs the first 2 h of the cycle scenario (news on) twice; the per-tick price paths must match. */
    private TestResult test_determinism() {
        if (System.getProperty("stockmarket.longrun", "").isBlank()) return skip("SM-336 long run not requested");
        try {
            ReplaySetup s = t208Market("diamond_default");
            Result a = simulate(s, "cycle", 2 * HOUR, newsOn(), volatilityOn(), null, null, new StringBuilder());
            Result b = simulate(s, "cycle", 2 * HOUR, newsOn(), volatilityOn(), null, null, new StringBuilder());
            String msg = String.format(Locale.ROOT, "[SM-336] determinism: hash %016x / %016x, %.0f ticks/s", a.hash(), b.hash(),
                    a.ticks() * 1e9 / a.wallNanos());
            StockMarketMod.LOGGER.info(msg);
            return a.hash() == b.hash() ? pass(msg) : fail(msg);
        } catch (Exception e) {
            return fail("Exception: " + e);
        }
    }
}
