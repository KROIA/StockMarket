package net.kroia.stockmarket.pluginsystem.plugins;

import net.kroia.modutilities.NbtCompat;
import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.util.PID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TargetPriceBot extends ServerPlugin<TargetPriceBot.Settings, TargetPriceBot.RuntimeStreamData> {

    /**
     * Custom per-market settings of the TargetPriceBot.
     *
     * @param pidP                 proportional gain
     * @param pidI                 integral gain
     * @param pidD                 derivative gain
     * @param pidRate              integral limit (anti-windup)
     * @param maxOrderVolumeFactor max volume of one bot order as a multiple of the market's player
     *                             order limit ({@code MarketSettings.maxOrderVolume}). A factor keeps the
     *                             bot limit tracking per-market limit changes. &lt;= 0, or a market
     *                             without a player limit (0 = unlimited), means no bot limit.
     * @param maxTargetMovePerSecond #89: max relative move of the target price the bot chases, per second
     *                             of tick time (0.01 = 1%/s, compounding). The bot follows the plugins'
     *                             target through this rate limit, so a sudden target jump (e.g. a pinned
     *                             flow equilibrium) is walked towards slowly. &lt;= 0 = no limit.
     * @param reversionTimeSeconds T-208: time constant (s) in which the bot's reference (its setpoint) decays
     *                             from the price a player pushed to back to the plugins' target. &lt;= 0 = off
     *                             (the bot chases the target through the rate limit only, as before T-208)
     * @param oscillationPeriodSeconds T-208: period (s) of the reference's damped oscillation around its
     *                             decaying centre; &lt;= 0 = {@link #DEFAULT_OSCILLATION_PERIOD_SECONDS}
     * @param oscillationDamping   T-208: damping ratio ζ of that oscillation (0 = undamped, 1 = critical);
     *                             negative = 0
     * @param oscillationAmplitude T-208: initial swing towards the target as a fraction of the spike's
     *                             excess over the target (0 = no swing); negative = 0
     */
    public record Settings(float pidP, float pidI, float pidD, float pidRate, float maxOrderVolumeFactor,
                           float maxTargetMovePerSecond, float reversionTimeSeconds, float oscillationPeriodSeconds,
                           float oscillationDamping, float oscillationAmplitude) {
        /** Settings with the T-208 reference model at its defaults. */
        public Settings(float pidP, float pidI, float pidD, float pidRate, float maxOrderVolumeFactor,
                        float maxTargetMovePerSecond) {
            this(pidP, pidI, pidD, pidRate, maxOrderVolumeFactor, maxTargetMovePerSecond, DEFAULT_REVERSION_TIME_SECONDS,
                    DEFAULT_OSCILLATION_PERIOD_SECONDS, DEFAULT_OSCILLATION_DAMPING, DEFAULT_OSCILLATION_AMPLITUDE);
        }

        /** Creates a Settings instance with all default values. */
        public static Settings createDefault() {
            return new Settings(DEFAULT_PID_P, DEFAULT_PID_I, DEFAULT_PID_D, DEFAULT_PID_RATE,
                    DEFAULT_MAX_ORDER_VOLUME_FACTOR, DEFAULT_MAX_TARGET_MOVE_PER_SECOND);
        }

        /** @return a copy with the given T-208 reference model parameters */
        public Settings withReference(float reversionTime, float period, float damping, float amplitude) {
            return new Settings(pidP, pidI, pidD, pidRate, maxOrderVolumeFactor, maxTargetMovePerSecond,
                    reversionTime, period, damping, amplitude);
        }

        public static final StreamCodec<ByteBuf, Settings> CODEC = new StreamCodec<>() {
            @Override
            public Settings decode(ByteBuf buf) {
                float p = buf.readFloat();
                float i = buf.readFloat();
                float d = buf.readFloat();
                float rate = buf.readFloat();
                // Backward compatibility: saves before the volume limit stored only the 4 PID floats.
                // Every decode path wraps exactly one settings payload per buffer, so "no bytes left"
                // identifies the old format.
                float factor = buf.isReadable() ? buf.readFloat() : DEFAULT_MAX_ORDER_VOLUME_FACTOR;
                // Same for the target rate limit (#89), added after the volume factor.
                float maxMove = buf.isReadable() ? buf.readFloat() : DEFAULT_MAX_TARGET_MOVE_PER_SECOND;
                // T-208 reference model, appended after the target rate limit.
                float reversion = buf.isReadable() ? buf.readFloat() : DEFAULT_REVERSION_TIME_SECONDS;
                float period = buf.isReadable() ? buf.readFloat() : DEFAULT_OSCILLATION_PERIOD_SECONDS;
                float damping = buf.isReadable() ? buf.readFloat() : DEFAULT_OSCILLATION_DAMPING;
                float amplitude = buf.isReadable() ? buf.readFloat() : DEFAULT_OSCILLATION_AMPLITUDE;
                return new Settings(p, i, d, rate, factor, maxMove, reversion, period, damping, amplitude);
            }
            @Override
            public void encode(ByteBuf buf, Settings s) {
                buf.writeFloat(s.pidP());
                buf.writeFloat(s.pidI());
                buf.writeFloat(s.pidD());
                buf.writeFloat(s.pidRate());
                buf.writeFloat(s.maxOrderVolumeFactor());
                buf.writeFloat(s.maxTargetMovePerSecond());
                buf.writeFloat(s.reversionTimeSeconds());
                buf.writeFloat(s.oscillationPeriodSeconds());
                buf.writeFloat(s.oscillationDamping());
                buf.writeFloat(s.oscillationAmplitude());
            }
        };
    }

    /**
     * Runtime data record containing target prices for all subscribed markets.
     */
    public record RuntimeStreamData(List<MarketTargetPrice> entries) {
        public record MarketTargetPrice(short itemId, double targetPrice) {}

        public static final StreamCodec<ByteBuf, RuntimeStreamData> CODEC = new StreamCodec<>() {
            @Override
            public RuntimeStreamData decode(ByteBuf buf) {
                int count = buf.readInt();
                List<MarketTargetPrice> entries = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    entries.add(new MarketTargetPrice(buf.readShort(), buf.readDouble()));
                }
                return new RuntimeStreamData(entries);
            }
            @Override
            public void encode(ByteBuf buf, RuntimeStreamData data) {
                buf.writeInt(data.entries().size());
                for (MarketTargetPrice e : data.entries()) {
                    buf.writeShort(e.itemId());
                    buf.writeDouble(e.targetPrice());
                }
            }
        };
    }

    static class RuntimeData
    {
        public PID pid;
        public int tickCounter = 0;
        public double targetPrice = 0;
        /** Last step that placed an order (diagnostics, see {@link #test_getLastStep}). */
        StepInfo lastStep;
        /** T-208: the bot's reference (setpoint) state; {@link Reference#IDLE} = follow the target. */
        Reference ref = Reference.IDLE;
        /** T-208: the market's raw net player flow at the previous step (NaN = not seeded yet). */
        double refLastRaw = Double.NaN;
        /**
         * Bot steps since the last player fill (raw net flow change); {@link Integer#MAX_VALUE} = no fill seen
         * yet. Limits the gap guard to {@link #GAP_GUARD_MAX_BLOCK_SECONDS} after a fill.
         */
        int stepsSinceFill = Integer.MAX_VALUE;
        float pidP, pidI, pidD, pidRate;

        RuntimeData(float p, float i, float d, float rate) {
            this.pidP = p;
            this.pidI = i;
            this.pidD = d;
            this.pidRate = rate;
            this.pid = createPid(p, i, d, rate);
        }
    }
    private final Map<ItemID, RuntimeData> marketData = new HashMap<>();

    /**
     * Diagnostics of one bot step that placed an order (replay traces).
     *
     * @param target      the rate-limited bot target (real price)
     * @param priceBefore market price when the order was sized (real)
     * @param estimate    the volume the bot measured to the target (real items, signed: + = buy)
     * @param amount      the placed market order (real items, + = buy)
     */
    public record StepInfo(double target, double priceBefore, double estimate, double amount) {}

    /**
     * Test hook: the last step of the bot on {@code marketID} that placed an order. A new object per
     * order, so callers detect a new order by identity.
     *
     * @return the step, or null if none yet / not subscribed
     */
    public StepInfo test_getLastStep(ItemID marketID) {
        RuntimeData data = marketData.get(marketID);
        return data == null ? null : data.lastStep;
    }

    /**
     * Test hook: the bot's current rate-limited target on {@code marketID} (real price).
     *
     * @return the target, or NaN if not subscribed
     */
    public double test_getTargetPrice(ItemID marketID) {
        RuntimeData data = marketData.get(marketID);
        return data == null ? Double.NaN : data.targetPrice;
    }

    /**
     * Creates the bot's PID with its output limited to [-1, 1], the range the bot actually uses
     * as order fraction. Without the limit the output filter winds up while the price sits far
     * from the target and the bot keeps pushing past the target for several cycles (T-178).
     */
    private static PID createPid(float p, float i, float d, float rate) {
        PID pid = new PID(p, i, d, rate);
        pid.setOutputLimit(1);
        return pid;
    }

    /**
     * Test hook: the bot's T-208 reference state on {@code marketID}.
     *
     * @return the state, or null if not subscribed
     */
    public Reference test_getReference(ItemID marketID) {
        RuntimeData data = marketData.get(marketID);
        return data == null ? null : data.ref;
    }

    // Default PID gains — configurable via custom settings (per-market)
    public static final float DEFAULT_PID_P = 0.5f;
    public static final float DEFAULT_PID_I = 0.1f;
    public static final float DEFAULT_PID_D = 0.0f;
    public static final float DEFAULT_PID_RATE = 0.1f;
    /** T-208: default reversion time constant of the bot's reference, 10 min. */
    public static final float DEFAULT_REVERSION_TIME_SECONDS = 600f;
    /** T-208: default oscillation period of the bot's reference, 60 s. */
    public static final float DEFAULT_OSCILLATION_PERIOD_SECONDS = 60f;
    /** T-208: default damping ratio of the reference's oscillation. */
    public static final float DEFAULT_OSCILLATION_DAMPING = 0.3f;
    /** T-208: default initial swing towards the target, as a fraction of the spike's excess. */
    public static final float DEFAULT_OSCILLATION_AMPLITUDE = 0.15f;
    /** T-208: below this (log price) the reference counts as settled and goes idle. */
    public static final double REFERENCE_EPSILON = 1e-4;

    /**
     * T-208 state of the bot's reference (its setpoint), in log price. After a player pushes the price
     * away from the plugins' target T, the reference starts at that price S and relaxes to T: its centre
     * {@code ln T + c} decays with the reversion time while the reference swings around it as a damped
     * oscillator. It never leaves [min(ln T, anchor), max(ln T, anchor)], so the bot never trades past
     * the target nor beyond the last price a player pushed to.
     *
     * @param y      the reference, ln(price)
     * @param v      its velocity (ln price per second)
     * @param c      excess of the oscillation centre over ln(T); decays with the reversion time
     * @param anchor ln of the last price a player pushed to (S)
     * @param active false = idle, the bot follows T through {@link #limitTargetMove} as before T-208
     */
    public record Reference(double y, double v, double c, double anchor, boolean active) {
        /** The idle reference. */
        public static final Reference IDLE = new Reference(0, 0, 0, 0, false);
    }

    /** Angular frequency ω of the reference oscillation (rad/s) from the settings. */
    private static double omega(Settings s) {
        float period = s.oscillationPeriodSeconds() > 0 ? s.oscillationPeriodSeconds() : DEFAULT_OSCILLATION_PERIOD_SECONDS;
        return 2 * Math.PI / period;
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    /**
     * T-208: re-anchors the reference after a player fill (a change of the raw net flow). Pure.
     * <ul>
     *   <li>A fill that pushes the price further from T than the current centre (or to the other side, or
     *       while idle) starts a new spike: centre and reference at the price, plus a swing towards T of
     *       {@code amplitude x ω x excess} (capped by the rate limit).</li>
     *   <li>A fill towards T (a "ping") keeps the centre and the velocity; only the anchor moves to the
     *       price, and the reference is pulled inside the new band.</li>
     * </ul>
     *
     * @param r       the current reference
     * @param lnT     ln of the plugins' target price
     * @param lnPrice ln of the market price after the fill
     * @param s       the bot settings (reversionTimeSeconds &lt;= 0 = model off, returns IDLE)
     * @param maxMove max reference speed (ln price per second); &lt;= 0 = no limit
     * @return the new reference
     */
    public static Reference anchorReference(Reference r, double lnT, double lnPrice, Settings s, float maxMove) {
        if (!(s.reversionTimeSeconds() > 0) || Double.isNaN(lnT) || Double.isNaN(lnPrice)) return Reference.IDLE;
        double excess = lnPrice - lnT;
        boolean ping = r.active() && Math.signum(excess) == Math.signum(r.c()) && Math.abs(excess) <= Math.abs(r.c());
        if (ping)
            return new Reference(clamp(r.y(), Math.min(lnT, lnPrice), Math.max(lnT, lnPrice)), r.v(), r.c(), lnPrice, true);
        if (Math.abs(excess) < REFERENCE_EPSILON) return Reference.IDLE;
        double v0 = -Math.max(0, s.oscillationAmplitude()) * omega(s) * excess;
        if (maxMove > 0) v0 = clamp(v0, -maxMove, maxMove);
        return new Reference(lnPrice, v0, excess, lnPrice, true);
    }

    /**
     * T-208: advances the reference by {@code dt}. Pure. The centre's excess decays,
     * {@code c *= exp(-dt / reversionTime)}; the reference follows it as a damped oscillator,
     * {@code v += (ω²(ln T + c - y) - 2ζω v) dt}, |v| &lt;= maxMove, {@code y += v dt}, clamped to
     * [min(ln T, anchor), max(ln T, anchor)] (velocity 0 at the bound). Goes idle once both the centre's
     * excess and the distance to T are below {@link #REFERENCE_EPSILON}.
     *
     * @param r       the current reference (returned unchanged if idle)
     * @param lnT     ln of the plugins' target price
     * @param s       the bot settings (reversionTimeSeconds &lt;= 0 = model off, returns IDLE)
     * @param maxMove max reference speed (ln price per second); &lt;= 0 = no limit
     * @param dt      step time in seconds
     * @return the new reference
     */
    public static Reference stepReference(Reference r, double lnT, Settings s, float maxMove, double dt) {
        if (!r.active()) return r;
        float tau = s.reversionTimeSeconds();
        if (!(tau > 0) || Double.isNaN(lnT)) return Reference.IDLE;
        double c = r.c() * Math.exp(-dt / tau);
        double w = omega(s);
        double zeta = Math.max(0, s.oscillationDamping());
        double v = r.v() + (w * w * (lnT + c - r.y()) - 2 * zeta * w * r.v()) * dt;
        if (maxMove > 0) v = clamp(v, -maxMove, maxMove);
        double y = r.y() + v * dt;
        double lo = Math.min(lnT, r.anchor()), hi = Math.max(lnT, r.anchor());
        if (y < lo || y > hi) {
            y = clamp(y, lo, hi);
            v = 0;
        }
        if (Math.abs(c) < REFERENCE_EPSILON && Math.abs(y - lnT) < REFERENCE_EPSILON) return Reference.IDLE;
        return new Reference(y, v, c, r.anchor(), true);
    }
    /** Default bot order limit: 10x the market's player order limit. */
    public static final float DEFAULT_MAX_ORDER_VOLUME_FACTOR = 10f;
    /**
     * #89: default target rate limit, 0.5% per second (~35% per minute, x2 in ~2.3 min). Above the
     * normal VolatilityPlugin random walk, which moves the target by well under 1% per step, but a
     * pinned 5-20x flow target now takes minutes to reach instead of seconds.
     */
    public static final float DEFAULT_MAX_TARGET_MOVE_PER_SECOND = 0.005f;
    /** Tick time between two bot steps: the bot acts every 5th update (5 ticks x 50 ms). */
    private static final double STEP_SECONDS = 5 * 0.05;

    /**
     * Moves {@code current} towards {@code target} by at most {@code current * maxMovePerSecond * dt}.
     *
     * @param current          the rate-limited target of the previous step; &lt;= 0 = none yet (snap)
     * @param target           the new raw target from the price plugins
     * @param maxMovePerSecond max relative move per second; &lt;= 0 = no limit (returns target)
     * @param dtSeconds        tick time since the previous step
     * @return the new rate-limited target
     */
    public static double limitTargetMove(double current, double target, float maxMovePerSecond, double dtSeconds) {
        if (!(maxMovePerSecond > 0) || !(current > 0)) return target;
        double maxStep = current * maxMovePerSecond * dtSeconds;
        return Math.max(current - maxStep, Math.min(current + maxStep, target));
    }

    /**
     * Max volume of one bot order.
     *
     * @param maxPlayerOrderVolume the market's player order limit in real items; &lt;= 0 = unlimited
     * @param factor               bot limit as a multiple of the player limit; &lt;= 0 = unlimited
     * @return the limit in real items, {@link Double#POSITIVE_INFINITY} if unlimited
     */
    public static double botOrderVolumeLimit(double maxPlayerOrderVolume, float factor) {
        if (maxPlayerOrderVolume <= 0 || factor <= 0) return Double.POSITIVE_INFINITY;
        return maxPlayerOrderVolume * factor;
    }



    /**
     * T-207: width of the gap window as a fraction of the current price (1% of the price; T-210:
     * half a depth memory slot). The bot only trades if this first part of its way to the target holds liquidity.
     */
    public static final double GAP_WINDOW_FRACTION = 0.01;

    /** T-207: gap guard switch, only turned off by tests to compare against the unguarded bot. */
    private static boolean gapGuardEnabled = true;

    /**
     * Test hook: turns the T-207 gap guard on or off for all bots.
     *
     * @param enabled false = trade into empty gaps as before T-207
     */
    public static void test_setGapGuardEnabled(boolean enabled) { gapGuardEnabled = enabled; }

    /**
     * T-208: the gap guard blocks the bot for at most this long after the last player fill. The guard exists so
     * the bot does not snap the price back through the gap a big order just swept; once the book had a minute
     * to refill that job is done. Without the limit, steady player buying (one order every 30 s) kept the
     * window below the minimum fill for good, so the bot never traded and the price ignored the plugins'
     * equilibrium. Each new fill restarts the window.
     */
    public static final double GAP_GUARD_MAX_BLOCK_SECONDS = 60;

    /** T-207: default of {@link #gapMinFill}. */
    public static final double DEFAULT_GAP_MIN_FILL = 0.25;
    /**
     * T-207: how full the gap window must be, as a fraction of "default level volume at the window edge x
     * price levels in the window" (an upper estimate of an undepleted window), before the bot trades.
     */
    private static double gapMinFill = DEFAULT_GAP_MIN_FILL;

    /**
     * Test hook: sets {@link #gapMinFill} for all bots.
     *
     * @param fill required fill fraction; &lt;= 0 disables the guard's volume check
     */
    public static void test_setGapMinFill(double fill) { gapMinFill = fill; }

    /**
     * T-207 gap guard: true if a bot order from {@code price} towards {@code target} would cross an
     * (almost) empty stretch of the book, so even a tiny order would move the price far in one tick.
     * <p>
     * The window is the first {@link #GAP_WINDOW_FRACTION} of the price towards the target (or up to the
     * target if it is closer), but at least one price step. The bot may trade only if the one-sided virtual volume in that window is
     * at least one price level of the market's default (undepleted) depth at the window's edge. A swept
     * book (after a big player order) holds ~0 there until the volume distribution refills it; a normal
     * book holds dozens of levels' worth. So this covers both "too little volume up to the target" and
     * "an empty gap wider than the window". Markets without a default depth (0) are never guarded.
     *
     * @param book   the market's order book
     * @param price  the current market price (real)
     * @param target the bot's target price (real)
     * @return true if the bot should skip this step
     */
    public static boolean crossesEmptyGap(IPluginOrderBook book, double price, double target) {
        if (!(price > 0) || target == price) return false;
        // T-208: at least one price step wide. On cheap items (dirt at 0.1, step 0.01 = 10%) 1% of the
        // price holds no level at all, so the window looked empty and blocked the bot forever.
        double step = MarketManager.convertToRealAmountStatic(1);
        double windowEnd = target > price
                ? Math.max(Math.min(target, price * (1 + GAP_WINDOW_FRACTION)), price + step)
                : Math.min(Math.max(target, price * (1 - GAP_WINDOW_FRACTION)), price - step);
        double levels = Math.max(1, Math.abs(windowEnd - price) / step);
        double minVolume = gapMinFill * Math.abs(book.getDefaultRealVolume(windowEnd)) * levels;
        if (!(minVolume > 0)) return false;
        // capped at minVolume: stops scanning as soon as enough volume is found
        return Math.abs(book.getRealVirtualVolumeToPrice(windowEnd, minVolume)) < minVolume;
    }

    public TargetPriceBot() {
        super();
    }

    @Override
    public void init() {

    }

    @Override
    public void deInit() {

    }

    @Override
    public void update(List<MarketInterface> markets) {

        for(MarketInterface market : markets)
        {
            RuntimeData data = marketData.get(market.market.getMarketID());
            if(data == null) continue;
            // The bot steers price by placing orders against the virtual orderbook.
            // With the orderbook disabled it has nothing to trade against, so skip.
            if(!market.market.isVirtualOrderbookEnabled()) continue;
            updateForMarket(market, data);
        }
    }

    private void updateForMarket(MarketInterface market, RuntimeData data)
    {
        data.tickCounter++;
        if(data.tickCounter < 5)
            return;
        data.tickCounter = 0;
        Settings settings = getCustomSettings(market.market.getMarketID());
        Settings s = settings != null ? settings : Settings.createDefault();
        float maxMove = s.maxTargetMovePerSecond();
        double rawTarget = market.market.getPreviousTargetPrice();
        double currentPrice = market.market.getPrice();
        // T-208: re-anchor the reference only on player fills (the raw net flow is an exact counter of
        // them; bot orders don't change it). An admin reset of the flow (raw back to 0) drops it.
        double raw = market.market.getNetPlayerItemFlow();
        if (!(s.reversionTimeSeconds() > 0) || !(rawTarget > 0) || !(currentPrice > 0)
                || (raw == 0 && data.refLastRaw != 0))
            data.ref = Reference.IDLE;
        else if (!Double.isNaN(data.refLastRaw) && raw != data.refLastRaw)
            data.ref = anchorReference(data.ref, Math.log(rawTarget), Math.log(currentPrice), s, maxMove);
        // T-208: restart the gap guard window on every player fill
        if (!Double.isNaN(data.refLastRaw) && raw != data.refLastRaw) data.stepsSinceFill = 0;
        else if (data.stepsSinceFill < Integer.MAX_VALUE) data.stepsSinceFill++;
        data.refLastRaw = raw;
        Reference next = data.ref.active() ? stepReference(data.ref, Math.log(rawTarget), s, maxMove, STEP_SECONDS) : data.ref;
        // #89: while the reference is idle, chase the plugins' target only through the rate limit
        // (persisted as targetPrice). Model off (reversion time 0) = this path only, as before T-208.
        data.targetPrice = next.active() ? Math.exp(next.y())
                : limitTargetMove(data.targetPrice, rawTarget, maxMove, STEP_SECONDS);

        // T-208: relative error in % of the price, so cheap items get the same urgency as
        // expensive ones; an absolute error left the PID output on dirt at about 0.0025.
        double error = currentPrice > 0 ? 100 * (data.targetPrice - currentPrice) / currentPrice : 0;
        double output = data.pid.update(error, STEP_SECONDS);
        // Clamp PID output to [-1, 1] as a fraction of how aggressively to push
        double pidFraction = Math.min(Math.max(-1, output), 1);

        // Cap the order: an oversized bot order walks the price through the whole virtual book in one
        // tick and can fill a far-away player limit order at its price (trading exploit).
        float factor = settings != null ? settings.maxOrderVolumeFactor() : DEFAULT_MAX_ORDER_VOLUME_FACTOR;
        double limit = botOrderVolumeLimit(market.market.getMaxRealOrderVolume(), factor);
        // T-200: size from the exact one-sided virtual depth between the price and the target, read the
        // way the matching walk reads it (incl. the depth memory). A market order of that size ends at or
        // before the target, so the bot can never walk past its own target. Resting player limit orders
        // are not counted: counting them let the bot fill a whole player order in one go at its limit
        // price, and leaving them out only makes the order smaller.
        // Signed like the book: negative = asks to buy through (target above), positive = bids to sell into.
        float volumeToTarget = market.oderBook.getRealVirtualVolumeToPrice(data.targetPrice, limit);
        // T-208: a target less than one price step away can't be reached without passing it (T-200), so
        // the bot waits. The reference still advances, otherwise it would stay stuck next to the price.
        if (Math.abs(data.targetPrice - currentPrice) < MarketManager.convertToRealAmountStatic(1)) {
            data.ref = next;
            return;
        }
        // T-207: don't trade into an empty price gap (e.g. right after a big player order swept the
        // book). Even a tiny order would fall through the gap and snap the price back at once.
        // T-208: with an active reference the guard checks the full window in the direction of travel:
        // the reference often sits within a level or two of the price, and a window shrunk to it would
        // stay empty (and the frozen reference with it) until the swept range refills completely.
        double guardTarget = !next.active() || data.targetPrice == currentPrice ? data.targetPrice
                : currentPrice * (data.targetPrice > currentPrice ? 1 + GAP_WINDOW_FRACTION : 1 - GAP_WINDOW_FRACTION);
        // T-208: only within GAP_GUARD_MAX_BLOCK_SECONDS of the last player fill
        boolean guardWindow = data.stepsSinceFill * STEP_SECONDS < GAP_GUARD_MAX_BLOCK_SECONDS;
        if (gapGuardEnabled && guardWindow && crossesEmptyGap(market.oderBook, currentPrice, guardTarget)) {
            // T-208: the reference freezes while the bot can't trade, so it doesn't run ahead of the price.
            if (data.ref.active()) data.targetPrice = Math.exp(data.ref.y());
            return;
        }
        data.ref = next;
        double absVolumeToTarget = Math.min(limit, Math.abs(volumeToTarget));
        // Direction to the target: +1 buy, -1 sell. The PID output only sets the urgency; if its sign
        // still points the other way (filter lag right after the price crossed the target), don't
        // trade instead of pushing the price further away.
        double direction = volumeToTarget < 0 ? 1 : -1;
        double urgency = Math.max(0, pidFraction * direction);
        // Truncate (not round) to 0.01 so the order never exceeds the measured volume.
        double marketOrderAmount = direction * Math.floor(urgency * absVolumeToTarget * 100) / 100;
        if(marketOrderAmount != 0)
        {
            market.market.placeOrder(marketOrderAmount);
            data.lastStep = new StepInfo(data.targetPrice, currentPrice, volumeToTarget, marketOrderAmount);
            info("Placing order: " + marketOrderAmount + " (PID-OUT: " + output + ", fraction: " + pidFraction + ", volumeToTarget: " + volumeToTarget + ")");
        }
    }

    @Override
    public void finalize(List<MarketInterface> markets) {

    }

    @Override
    public void onMarketSubscribed(ItemID marketID) {
        RuntimeData data = new RuntimeData(DEFAULT_PID_P, DEFAULT_PID_I, DEFAULT_PID_D, DEFAULT_PID_RATE);
        marketData.put(marketID, data);
    }

    @Override
    public void onMarketUnsubscribed(ItemID itemID) {
        marketData.remove(itemID);
    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    @Override
    protected StreamCodec<ByteBuf, RuntimeStreamData> runtimeDataCodec() {
        return RuntimeStreamData.CODEC;
    }

    @Override
    protected RuntimeStreamData provideRuntimeData() {
        if (marketData.isEmpty()) return null;
        List<RuntimeStreamData.MarketTargetPrice> entries = new ArrayList<>();
        for (Map.Entry<ItemID, RuntimeData> entry : marketData.entrySet()) {
            entries.add(new RuntimeStreamData.MarketTargetPrice(
                    entry.getKey().getShort(), entry.getValue().targetPrice));
        }
        return new RuntimeStreamData(entries);
    }

    /**
     * Returns the update interval for the runtime data stream.
     * Uses 200ms for fast target price visualization updates.
     */
    @Override
    public long getRuntimeDataStreamInterval() {
        return 200;
    }

    @Override
    protected StreamCodec<ByteBuf, Settings> customSettingsCodec() {
        return Settings.CODEC;
    }

    @Override
    protected Settings provideDefaultCustomSettings() {
        return Settings.createDefault();
    }

    @Override
    protected void onCustomSettingsApplied(ItemID marketID, Settings settings) {
        RuntimeData data = marketData.get(marketID);
        if (data != null) {
            data.pidP = settings.pidP();
            data.pidI = settings.pidI();
            data.pidD = settings.pidD();
            data.pidRate = settings.pidRate();
            data.pid = createPid(data.pidP, data.pidI, data.pidD, data.pidRate);
        }
    }

    @Override
    public boolean save(CompoundTag tag) {
        // Save per-market PID settings, state, target prices, and tick counters
        ListTag marketsTag = new ListTag();
        for (Map.Entry<ItemID, RuntimeData> entry : marketData.entrySet()) {
            CompoundTag marketTag = new CompoundTag();
            entry.getKey().save(marketTag);
            RuntimeData data = entry.getValue();
            marketTag.putFloat("pidP", entry.getValue().pidP);
            marketTag.putFloat("pidI", entry.getValue().pidI);
            marketTag.putFloat("pidD", entry.getValue().pidD);
            marketTag.putFloat("pidRate", entry.getValue().pidRate);
            marketTag.putDouble("targetPrice", data.targetPrice);
            marketTag.putInt("tickCounter", data.tickCounter);
            CompoundTag pidTag = new CompoundTag();
            data.pid.save(pidTag);
            marketTag.put("pid", pidTag);
            // T-208 reference state
            marketTag.putDouble("refY", data.ref.y());
            marketTag.putDouble("refV", data.ref.v());
            marketTag.putDouble("refC", data.ref.c());
            marketTag.putDouble("refAnchor", data.ref.anchor());
            marketTag.putDouble("refLastRaw", data.refLastRaw);
            marketTag.putBoolean("refActive", data.ref.active());
            marketsTag.add(marketTag);
        }
        tag.put("marketData", marketsTag);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        // Restore per-market PID settings and state (marketData map is already populated
        // because subscribeToMarket() is called before load() in ServerPluginManager)
        if (tag.contains("marketData")) {
            ListTag marketsTag = NbtCompat.getList(tag, "marketData", 10);
            for (int i = 0; i < marketsTag.size(); i++) {
                CompoundTag marketTag = NbtCompat.getCompound(marketsTag, i);
                ItemID marketID = ItemID.createFromTag(marketTag);
                if (marketID != null && marketID.isValid()) {
                    RuntimeData data = marketData.get(marketID);
                    if (data != null) {
                        if (marketTag.contains("pidP")) {
                            data.pidP = NbtCompat.getFloat(marketTag, "pidP");
                            data.pidI = NbtCompat.getFloat(marketTag, "pidI");
                            data.pidD = NbtCompat.getFloat(marketTag, "pidD");
                            data.pidRate = NbtCompat.getFloat(marketTag, "pidRate");
                            data.pid = createPid(data.pidP, data.pidI, data.pidD, data.pidRate);
                        }
                        if (marketTag.contains("targetPrice")) data.targetPrice = NbtCompat.getDouble(marketTag, "targetPrice");
                        if (marketTag.contains("tickCounter")) data.tickCounter = NbtCompat.getInt(marketTag, "tickCounter");
                        if (marketTag.contains("pid")) data.pid.load(NbtCompat.getCompound(marketTag, "pid"));
                        // T-208: missing keys (older save) -> idle reference, flow seeded on the first step
                        data.ref = marketTag.contains("refActive") && NbtCompat.getBoolean(marketTag, "refActive")
                                ? new Reference(NbtCompat.getDouble(marketTag, "refY"), NbtCompat.getDouble(marketTag, "refV"),
                                        NbtCompat.getDouble(marketTag, "refC"), NbtCompat.getDouble(marketTag, "refAnchor"), true)
                                : Reference.IDLE;
                        data.refLastRaw = marketTag.contains("refLastRaw") ? NbtCompat.getDouble(marketTag, "refLastRaw") : Double.NaN;
                    }
                }
            }
        }
        return true;
    }
}
