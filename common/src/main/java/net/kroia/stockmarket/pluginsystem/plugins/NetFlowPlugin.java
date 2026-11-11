package net.kroia.stockmarket.pluginsystem.plugins;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.NbtCompat;
import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * T-213: the net-flow price model, split out of the VolatilityPlugin. Turns the players' net item flow into a
 * per-market equilibrium price and publishes it through {@link net.kroia.stockmarket.api.plugin.interaction.IPluginMarket#setEquilibriumPrice}.
 * The VolatilityPlugin adds its random walk on top of that equilibrium; without a VolatilityPlugin (or when it
 * runs first) this plugin also sets the target price to the bare equilibrium, but never overwrites a target
 * another plugin already wrote this tick.
 * <p>
 * Sub-algorithms, each with its own toggle:
 * <ul>
 *   <li><b>flow</b>: {@code E = D x exp(clamp(-satFlow / capacity, ln minMult, ln maxMult))};
 *       off = the equilibrium is the default price D;</li>
 *   <li><b>θ cap</b> (T-208): {@code capacity = max(sensitivity capacity, bookEFold / θ)}, so the lasting move is at
 *       most θ x the volume's one-shot walk through the default book; off = sensitivity capacity only;</li>
 *   <li><b>saturation</b> (T-174/T-207): a plugin-private copy of the faded flow, clamped to the band where the
 *       equilibrium reaches its limits, so a buy-back after a huge one-sided trade moves the price at once;
 *       off = the faded flow is used directly (the min/max clamp stays);</li>
 *   <li><b>anchor</b> (T-213): a slow long-term memory {@code A = D x e^a} the flow moves away from. Each tick
 *       {@code a} drifts toward the current flow exponent {@code f}: {@code da = clamp(f x dt / τ_A, ±rate x dt)},
 *       {@code a} clamped to [ln anchorMinMult, ln anchorMaxMult]; zero flow leaves {@code a} unchanged; an admin
 *       flow reset sets it to 0. Off = {@code A = D}.</li>
 * </ul>
 * The market's own flow counters are only read, never written.
 */
public class NetFlowPlugin extends ServerPlugin<NetFlowPlugin.Settings, NetFlowPlugin.RuntimeStreamData> {

    // ── Default settings values ─────────────────────────────────────────

    /** Flow-driven price equilibrium is active by default. */
    public static final boolean DEFAULT_FLOW_ENABLED = true;
    /**
     * Default flow sensitivity: items per unit of naturalAbundance that shift the equilibrium price by a
     * factor of e (~2.72x).
     * <p>
     * T-197 (2026-11-08): raised from 500 to 2000 after a replay sweep (flowSensitivity 125..2000 x abundance
     * 25/50/100 x maxPriceMult 20/5/3/2) on the diamond preset (price 160, abundance 50, player max order
     * 3,200). Patient hold -3.5%, rapid pump -24%, organic demand +26..+148% per 10 min by abundance.
     * Smaller moves happen proportionally earlier (e.g. ~10% price change after ~capacity/10 items).
     */
    public static final float DEFAULT_FLOW_SENSITIVITY = 2000f;
    /** Default lower clamp of the equilibrium: never below 5% of the default price. */
    public static final float DEFAULT_MIN_PRICE_MULT = 0.05f;
    /**
     * Default upper clamp of the equilibrium: never above 3x the default price.
     * <p>
     * T-197: lowered from 20 to 3. The cap bounds how far a pump can drag the bot's target.
     */
    public static final float DEFAULT_MAX_PRICE_MULT = 3.0f;
    /**
     * T-208 (θ): default permanent impact. The equilibrium never moves more than this fraction of the
     * log price move the same volume makes when it walks the default order book in one go.
     * Valid range (0, 1]; invalid values fall back to this default.
     */
    public static final float DEFAULT_PERMANENT_IMPACT = 0.5f;
    /**
     * T-213: default volatility scale used in the capacity ({@code / (1 + scale)}). Before T-213 the capacity
     * used the VolatilityPlugin's own scale (default 0.3); keeping 0.3 keeps every equilibrium bit-identical.
     * Migrated worlds copy their volatility scale here.
     */
    public static final float DEFAULT_CAPACITY_VOLATILITY_SCALE = 0.3f;
    /** T-213: the θ cap is on by default. */
    public static final boolean DEFAULT_THETA_CAP_ENABLED = true;
    /** T-213: flow saturation at the band is on by default. */
    public static final boolean DEFAULT_SATURATION_ENABLED = true;

    /**
     * T-213: the long-term anchor is off by default until the T-213 exploit gates G1-G5 (no round trip
     * profits, the anchor only keeps part of a sustained one-sided flow) decide otherwise.
     */
    public static final boolean DEFAULT_ANCHOR_ENABLED = false;
    /**
     * T-213: anchor time constant τ_A in days. With the flow's 8.66 h fade time an impulse leaves about
     * τ_fade / τ_A (~18%) in the anchor. Values below 1 day let a fast dump-and-rebuy profit (τ_A must stay
     * above 2 x τ_fade).
     */
    public static final float DEFAULT_ANCHOR_TIME_DAYS = 2f;
    /** T-213: largest anchor move per day in ln units (0.1 = about 10% per day), caps saturated flows. */
    public static final float DEFAULT_ANCHOR_MAX_MOVE_PER_DAY = 0.1f;
    /** T-213: lowest anchor as a multiple of the default price. */
    public static final float DEFAULT_ANCHOR_MIN_MULT = 0.25f;
    /** T-213: highest anchor as a multiple of the default price. */
    public static final float DEFAULT_ANCHOR_MAX_MULT = 4f;
    /** Seconds per day (anchor rates are per day). */
    static final double SECONDS_PER_DAY = 86_400.0;

    /** T-208: ticks between two {@link #bookEFoldDepth} recomputations per market (1 min at 20 TPS). */
    static final int BOOK_EFOLD_REFRESH_TICKS = 1200;
    /** T-208: ticks until the next try while no default book volume is found (calculators may register late). */
    static final int BOOK_EFOLD_RETRY_TICKS = 20;
    /** T-208: zero results in a row after which the "no volume calculator" warning is logged (once per market). */
    static final int BOOK_EFOLD_WARN_AFTER = 5;
    /** T-208: log-price sampling step of {@link #bookEFoldDepth} (0.1%). */
    static final double BOOK_EFOLD_LN_STEP = 0.001;

    /**
     * Per-market settings of the net-flow price model.
     *
     * @param flowEnabled             if true, the equilibrium shifts with the players' net item flow (sell
     *                                pressure lowers it, buy pressure raises it); if false it stays at the default price
     * @param flowSensitivity         items (per unit of the market's naturalAbundance) players must net-sell to
     *                                lower the equilibrium by a factor of e. Higher = more inert. &lt;= 0 disables the flow
     * @param minPriceMult            lower clamp of the equilibrium as a multiple of the default price, in (0, 1);
     *                                invalid values fall back to the default
     * @param maxPriceMult            upper clamp of the equilibrium as a multiple of the default price, &gt; 1;
     *                                invalid values fall back to the default
     * @param permanentImpact         T-208 θ in (0, 1]: the largest lasting move as a fraction of the volume's own
     *                                one-shot walk through the default book; invalid values become the default
     * @param capacityVolatilityScale capacity divisor {@code 1 + scale} (pre-T-213 the VolatilityPlugin's scale)
     * @param thetaCapEnabled         false = the capacity ignores the default book (no θ cap)
     * @param saturationEnabled       false = no plugin-private saturated flow, the faded flow is used directly
     * @param anchorEnabled           T-213: true = the flow moves away from the long-term anchor instead of D
     * @param anchorTimeDays          anchor time constant τ_A in days (&gt; 0, else the default); &lt; 1 day is exploitable
     * @param anchorMaxMovePerDay     largest anchor move per day in ln units (&gt;= 0, else the default)
     * @param anchorMinMult           lowest anchor as a multiple of D, in (0, 1) (else the default)
     * @param anchorMaxMult           highest anchor as a multiple of D, &gt; 1 (else the default)
     */
    public record Settings(boolean flowEnabled, float flowSensitivity, float minPriceMult, float maxPriceMult,
                           float permanentImpact, float capacityVolatilityScale, boolean thetaCapEnabled,
                           boolean saturationEnabled, boolean anchorEnabled, float anchorTimeDays,
                           float anchorMaxMovePerDay, float anchorMinMult, float anchorMaxMult) {
        /** Sanitizes θ: anything outside (0, 1] (including NaN) becomes {@link #DEFAULT_PERMANENT_IMPACT}. */
        public Settings {
            if (!(permanentImpact > 0f && permanentImpact <= 1f)) permanentImpact = DEFAULT_PERMANENT_IMPACT;
            if (!(anchorTimeDays > 0f) || Float.isInfinite(anchorTimeDays)) anchorTimeDays = DEFAULT_ANCHOR_TIME_DAYS;
            if (!(anchorMaxMovePerDay >= 0f) || Float.isInfinite(anchorMaxMovePerDay)) anchorMaxMovePerDay = DEFAULT_ANCHOR_MAX_MOVE_PER_DAY;
            if (!(anchorMinMult > 0f && anchorMinMult < 1f)) anchorMinMult = DEFAULT_ANCHOR_MIN_MULT;
            if (!(anchorMaxMult > 1f) || Float.isInfinite(anchorMaxMult)) anchorMaxMult = DEFAULT_ANCHOR_MAX_MULT;
        }

        /** Settings with the default anchor. */
        public Settings(boolean flowEnabled, float flowSensitivity, float minPriceMult, float maxPriceMult, float permanentImpact,
                        float capacityVolatilityScale, boolean thetaCapEnabled, boolean saturationEnabled) {
            this(flowEnabled, flowSensitivity, minPriceMult, maxPriceMult, permanentImpact, capacityVolatilityScale,
                    thetaCapEnabled, saturationEnabled, DEFAULT_ANCHOR_ENABLED, DEFAULT_ANCHOR_TIME_DAYS,
                    DEFAULT_ANCHOR_MAX_MOVE_PER_DAY, DEFAULT_ANCHOR_MIN_MULT, DEFAULT_ANCHOR_MAX_MULT);
        }

        /** Settings with the default capacity volatility scale and both toggles on. */
        public Settings(boolean flowEnabled, float flowSensitivity, float minPriceMult, float maxPriceMult, float permanentImpact) {
            this(flowEnabled, flowSensitivity, minPriceMult, maxPriceMult, permanentImpact, DEFAULT_CAPACITY_VOLATILITY_SCALE,
                    DEFAULT_THETA_CAP_ENABLED, DEFAULT_SATURATION_ENABLED);
        }

        /** @return a copy with the given permanent impact θ (sanitized to (0, 1]) */
        public Settings withPermanentImpact(float theta) {
            return new Settings(flowEnabled, flowSensitivity, minPriceMult, maxPriceMult, theta, capacityVolatilityScale,
                    thetaCapEnabled, saturationEnabled, anchorEnabled, anchorTimeDays, anchorMaxMovePerDay, anchorMinMult, anchorMaxMult);
        }

        /** @return a copy with another capacity volatility scale */
        public Settings withCapacityVolatilityScale(float scale) {
            return new Settings(flowEnabled, flowSensitivity, minPriceMult, maxPriceMult, permanentImpact, scale,
                    thetaCapEnabled, saturationEnabled, anchorEnabled, anchorTimeDays, anchorMaxMovePerDay, anchorMinMult, anchorMaxMult);
        }

        /** @return a copy with the three sub-algorithm toggles replaced */
        public Settings withToggles(boolean flow, boolean thetaCap, boolean saturation) {
            return new Settings(flow, flowSensitivity, minPriceMult, maxPriceMult, permanentImpact, capacityVolatilityScale,
                    thetaCap, saturation, anchorEnabled, anchorTimeDays, anchorMaxMovePerDay, anchorMinMult, anchorMaxMult);
        }

        /** @return a copy with the anchor settings replaced (invalid values become the defaults) */
        public Settings withAnchor(boolean enabled, float timeDays, float maxMovePerDay, float minMult, float maxMult) {
            return new Settings(flowEnabled, flowSensitivity, minPriceMult, maxPriceMult, permanentImpact, capacityVolatilityScale,
                    thetaCapEnabled, saturationEnabled, enabled, timeDays, maxMovePerDay, minMult, maxMult);
        }

        /** @return a copy with the anchor switched on or off (other anchor settings kept) */
        public Settings withAnchorEnabled(boolean enabled) {
            return withAnchor(enabled, anchorTimeDays, anchorMaxMovePerDay, anchorMinMult, anchorMaxMult);
        }

        /**
         * Wire layout: flowEnabled, flowSensitivity, minPriceMult, maxPriceMult, permanentImpact,
         * capacityVolatilityScale, thetaCapEnabled, saturationEnabled, anchorEnabled, anchorTimeDays,
         * anchorMaxMovePerDay, anchorMinMult, anchorMaxMult. Fields missing at the end of a
         * (shorter, older) payload get their defaults; unknown trailing bytes (newer payload) are skipped.
         * Every decode path wraps exactly one payload per buffer.
         */
        public static final StreamCodec<ByteBuf, Settings> CODEC = new StreamCodec<>() {
            @Override
            public Settings decode(ByteBuf buf) {
                Settings d = createDefault();
                boolean flow = buf.isReadable() ? buf.readBoolean() : d.flowEnabled();
                float sens = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : d.flowSensitivity();
                float min = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : d.minPriceMult();
                float max = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : d.maxPriceMult();
                float theta = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : d.permanentImpact();
                float capVs = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : d.capacityVolatilityScale();
                boolean thetaCap = buf.isReadable() ? buf.readBoolean() : d.thetaCapEnabled();
                boolean sat = buf.isReadable() ? buf.readBoolean() : d.saturationEnabled();
                boolean anchor = buf.isReadable() ? buf.readBoolean() : d.anchorEnabled();
                float aDays = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : d.anchorTimeDays();
                float aRate = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : d.anchorMaxMovePerDay();
                float aMin = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : d.anchorMinMult();
                float aMax = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : d.anchorMaxMult();
                buf.skipBytes(buf.readableBytes());
                return new Settings(flow, sens, min, max, theta, capVs, thetaCap, sat, anchor, aDays, aRate, aMin, aMax);
            }

            @Override
            public void encode(ByteBuf buf, Settings s) {
                buf.writeBoolean(s.flowEnabled());
                buf.writeFloat(s.flowSensitivity());
                buf.writeFloat(s.minPriceMult());
                buf.writeFloat(s.maxPriceMult());
                buf.writeFloat(s.permanentImpact());
                buf.writeFloat(s.capacityVolatilityScale());
                buf.writeBoolean(s.thetaCapEnabled());
                buf.writeBoolean(s.saturationEnabled());
                buf.writeBoolean(s.anchorEnabled());
                buf.writeFloat(s.anchorTimeDays());
                buf.writeFloat(s.anchorMaxMovePerDay());
                buf.writeFloat(s.anchorMinMult());
                buf.writeFloat(s.anchorMaxMult());
            }
        };

        /** Creates a Settings instance with all default values. */
        public static Settings createDefault() {
            return new Settings(DEFAULT_FLOW_ENABLED, DEFAULT_FLOW_SENSITIVITY, DEFAULT_MIN_PRICE_MULT, DEFAULT_MAX_PRICE_MULT,
                    DEFAULT_PERMANENT_IMPACT);
        }
    }

    /**
     * T-213: streamed to the settings GUI: the current anchor of every subscribed market.
     *
     * @param itemIds     the markets' item ids ({@link ItemID#getShort()})
     * @param anchorMults the anchors as multiples of the default price ({@code e^a}), same order
     */
    public record RuntimeStreamData(short[] itemIds, double[] anchorMults) {
        public static final StreamCodec<ByteBuf, RuntimeStreamData> CODEC = new StreamCodec<>() {
            @Override
            public RuntimeStreamData decode(ByteBuf buf) {
                int n = buf.readInt();
                short[] ids = new short[n];
                double[] mults = new double[n];
                for (int i = 0; i < n; i++) {
                    ids[i] = buf.readShort();
                    mults[i] = buf.readDouble();
                }
                return new RuntimeStreamData(ids, mults);
            }

            @Override
            public void encode(ByteBuf buf, RuntimeStreamData d) {
                buf.writeInt(d.itemIds().length);
                for (int i = 0; i < d.itemIds().length; i++) {
                    buf.writeShort(d.itemIds()[i]);
                    buf.writeDouble(d.anchorMults()[i]);
                }
            }
        };

        /** @return the anchor multiple of the market with item id {@code id}, NaN if not listed */
        public double anchorOf(short id) {
            for (int i = 0; i < itemIds.length; i++) if (itemIds[i] == id) return anchorMults[i];
            return Double.NaN;
        }
    }

    /**
     * T-213 migration, decode only: a pre-T-213 VolatilityPlugin settings payload (which also carried the flow
     * settings) as NetFlow settings. Layouts: 1 float (volatilityScale only: flow defaults); 5 fields (scale,
     * flowEnabled, sensitivity, min, max: default θ); 6 fields (+ θ); 8 fields (unreleased T-204 dev builds, the
     * trailing floats are skipped). The old volatility scale becomes the capacity scale, so the equilibrium stays
     * bit-identical; the toggles get their defaults.
     */
    public static final StreamCodec<ByteBuf, Settings> LEGACY_VOLATILITY_CODEC = new StreamCodec<>() {
        @Override
        public Settings decode(ByteBuf buf) {
            Settings d = Settings.createDefault();
            float vs = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : d.capacityVolatilityScale();
            if (!buf.isReadable()) return d.withCapacityVolatilityScale(vs);
            boolean flow = buf.readBoolean();
            float sens = buf.readFloat();
            float min = buf.readFloat();
            float max = buf.readFloat();
            float theta = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : DEFAULT_PERMANENT_IMPACT;
            buf.skipBytes(buf.readableBytes());
            return new Settings(flow, sens, min, max, theta).withCapacityVolatilityScale(vs);
        }

        @Override
        public void encode(ByteBuf buf, Settings s) {
            throw new UnsupportedOperationException("LEGACY_VOLATILITY_CODEC is decode-only (migration)");
        }
    };

    /**
     * T-213 migration: applies the per-market NBT keys a pre-T-213 VolatilityPlugin saved over its settings (the
     * old plugin ran with these, not with its settings map). Missing keys keep {@code s}, a missing
     * {@code permanentImpact} becomes the default θ (as the old load did).
     *
     * @param s         the settings decoded with {@link #LEGACY_VOLATILITY_CODEC}
     * @param marketTag one entry of the old plugin's {@code marketData} list
     * @return the effective settings
     */
    public static Settings applyLegacyVolatilityNbt(Settings s, CompoundTag marketTag) {
        return new Settings(
                marketTag.contains("flowInfluenceEnabled") ? NbtCompat.getBoolean(marketTag, "flowInfluenceEnabled") : s.flowEnabled(),
                marketTag.contains("flowSensitivity") ? NbtCompat.getFloat(marketTag, "flowSensitivity") : s.flowSensitivity(),
                marketTag.contains("minPriceMult") ? NbtCompat.getFloat(marketTag, "minPriceMult") : s.minPriceMult(),
                marketTag.contains("maxPriceMult") ? NbtCompat.getFloat(marketTag, "maxPriceMult") : s.maxPriceMult(),
                marketTag.contains("permanentImpact") ? NbtCompat.getFloat(marketTag, "permanentImpact") : DEFAULT_PERMANENT_IMPACT,
                marketTag.contains("volatilityScale") ? NbtCompat.getFloat(marketTag, "volatilityScale") : s.capacityVolatilityScale(),
                s.thetaCapEnabled(), s.saturationEnabled(), s.anchorEnabled(), s.anchorTimeDays(), s.anchorMaxMovePerDay(),
                s.anchorMinMult(), s.anchorMaxMult());
    }

    /** Per-market state. */
    static class MarketData {
        Settings settings = Settings.createDefault();
        /**
         * T-207: plugin-private saturated copy of the market's faded net flow (items). Follows the faded flow's
         * per-tick change but is clamped to the {@link FlowBand}. Never written to the market. Saved.
         */
        double satFlow = 0;
        /** T-207: the market's faded net flow (items) seen on the previous update; saved. */
        double lastFaded = 0;
        /** T-207: false until satFlow/lastFaded are seeded from the market (new market or old save). */
        boolean satSeeded = false;
        /** T-213: the anchor in ln units relative to the default price ({@code A = D x e^anchorLn}); saved. */
        double anchorLn = 0;
        /** Equilibrium published on the last update (NaN = none yet). Not saved. */
        double equilibrium = Double.NaN;
        /** T-208: cached {@link #bookEFoldDepth} of the default book (0 = unknown). Not saved, recomputed. */
        double bookEFold = 0;
        /** T-208: default price the cached bookEFold was computed for (NaN = never computed). */
        double bookDefaultPrice = Double.NaN;
        /** T-208: natural abundance the cached bookEFold was computed for. */
        float bookAbundance = Float.NaN;
        /** T-208: ticks until the next bookEFold recomputation. */
        int bookTicksLeft = 0;
        /** T-208: zero bookEFold results in a row (for the one-time warning). */
        int bookZeroCount = 0;
        /** T-208: true once the "no volume calculator" warning was logged for this market. */
        boolean bookWarned = false;
    }

    private final Map<ItemID, MarketData> marketData = new HashMap<>();

    public NetFlowPlugin() {
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
        for (MarketInterface market : markets) {
            MarketData data = marketData.get(market.market.getMarketID());
            if (data == null) continue;
            Settings s = data.settings;

            // T-207: the market's net-flow counters are only read. Saturation happens on the private satFlow.
            // T-208: capacity = max(sensitivity capacity, bookEFold / θ) while the θ cap is on.
            double bookEFold = s.thetaCapEnabled() ? refreshBookEFold(market, data) : 0;
            FlowBand band = flowBand(market.market.getNaturalAbundance(), s, bookEFold);
            double faded = market.market.getFadedNetPlayerItemFlow();
            double raw = market.market.getNetPlayerItemFlow();
            boolean reset = data.satSeeded && isFlowReset(data, faded, raw);
            data.satFlow = saturatedFlow(data, faded, raw, s.saturationEnabled() ? band : null, ServerMarket.netFlowHalfLifeSeconds());
            // T-213: the anchor learns from the same (saturated, θ-capped) flow exponent; an admin reset clears it
            data.anchorLn = reset ? 0 : stepAnchor(data.anchorLn, flowExponent(data.satFlow, band), ServerMarket.TICK_SECONDS, s);

            // The target price only matters for markets with a virtual order book (the bot skips the others).
            if (!market.market.isVirtualOrderbookEnabled()) continue;

            double anchor = market.market.getDefaultRealPrice() * Math.exp(data.anchorLn); // exp(0) = 1: A = D exactly
            double equilibrium = computeEquilibriumPrice(anchor, data.satFlow, market.market.getNaturalAbundance(), s, bookEFold);
            data.equilibrium = equilibrium;
            market.market.setEquilibriumPrice(equilibrium);
            // Normal order (NetFlow first): this sets the bare equilibrium, the VolatilityPlugin then adds its
            // noise. Misordered: the target is already written, keep it (one tick lag instead of no noise).
            if (!market.market.isTargetPriceSetThisTick())
                market.market.setTargetPrice(equilibrium);
        }
    }

    /**
     * T-213: one tick of the long-term anchor. The anchor drifts toward the current flow exponent with time
     * constant τ_A, at most {@code anchorMaxMovePerDay} per day, and stays within [anchorMinMult, anchorMaxMult]:
     * <pre>
     * da = clamp(f x dt / (τ_A x 86400), ±rate x dt / 86400)
     * a  = clamp(a + da, ln anchorMinMult, ln anchorMaxMult)
     * </pre>
     * Zero flow leaves the anchor unchanged (no reversion toward D). Pure function.
     *
     * @param anchorLn     the anchor before the step (ln of A / D)
     * @param flowExponent the current flow exponent {@link #flowExponent} (ln of E / A)
     * @param dtSeconds    the step length in seconds
     * @param settings     the market's settings (null or anchor off = 0)
     * @return the anchor after the step
     */
    public static double stepAnchor(double anchorLn, double flowExponent, double dtSeconds, Settings settings) {
        if (settings == null || !settings.anchorEnabled()) return 0;
        if (flowExponent == 0 || Double.isNaN(flowExponent)) return anchorLn;
        double maxStep = settings.anchorMaxMovePerDay() * dtSeconds / SECONDS_PER_DAY;
        double da = flowExponent * dtSeconds / (settings.anchorTimeDays() * SECONDS_PER_DAY);
        da = Math.min(Math.max(da, -maxStep), maxStep);
        return Math.min(Math.max(anchorLn + da, Math.log(settings.anchorMinMult())), Math.log(settings.anchorMaxMult()));
    }

    /**
     * Computes the flow-driven equilibrium price.
     * <pre>
     * capacity    = flowSensitivity * naturalAbundance / (1 + capacityVolatilityScale)   (θ cap: max with bookEFold / θ)
     * exponent    = clamp(-netPlayerItemFlow / capacity, ln(minPriceMult), ln(maxPriceMult))
     * equilibrium = anchorPrice * e^exponent
     * </pre>
     * SIGN CONVENTION (ServerMarket.trackPlayerNetFlow): players net-SELL -&gt; flow &gt; 0 -&gt; price DOWN;
     * players net-BUY -&gt; flow &lt; 0 -&gt; price UP. The exponent is clamped before {@link Math#exp}, so the
     * result is always strictly positive and finite for a positive anchor price.
     * <p>
     * Returns exactly {@code anchorPrice} for: settings null or flow disabled, flow 0 or NaN, invalid capacity.
     *
     * @param anchorPrice       the price the flow moves away from (the default price) in real units
     * @param netPlayerItemFlow net items players sold (positive) or bought (negative)
     * @param naturalAbundance  the market item's natural abundance
     * @param settings          the per-market settings (may be null)
     * @return the equilibrium price, always &gt;= 0
     */
    public static double computeEquilibriumPrice(double anchorPrice, double netPlayerItemFlow,
                                                 float naturalAbundance, Settings settings) {
        return computeEquilibriumPrice(anchorPrice, netPlayerItemFlow, naturalAbundance, settings, 0);
    }

    /**
     * T-208: {@link #computeEquilibriumPrice(double, double, float, Settings)} with the market's default book
     * depth, so the capacity is {@code max(sensitivity capacity, bookEFold / permanentImpact)}.
     *
     * @param anchorPrice       the price the flow moves away from (real units)
     * @param netPlayerItemFlow net items players sold (positive) or bought (negative)
     * @param naturalAbundance  the market item's natural abundance
     * @param settings          the per-market settings (may be null)
     * @param bookEFold         {@link #bookEFoldDepth} of the default book; &lt;= 0 = sensitivity capacity only
     * @return the equilibrium price, always &gt;= 0
     */
    public static double computeEquilibriumPrice(double anchorPrice, double netPlayerItemFlow,
                                                 float naturalAbundance, Settings settings, double bookEFold) {
        if (netPlayerItemFlow == 0.0 || Double.isNaN(netPlayerItemFlow)) return anchorPrice;
        FlowBand band = flowBand(naturalAbundance, settings, bookEFold);
        if (band == null) return anchorPrice;
        return anchorPrice * Math.exp(flowExponent(netPlayerItemFlow, band));
    }

    /**
     * T-213: the log price move a net flow makes: {@code clamp(-flow / capacity, lnMinMult, lnMaxMult)}.
     * Pure function.
     *
     * @param netPlayerItemFlow net items players sold (positive) or bought (negative)
     * @param band              the flow band, or null (flow term inactive)
     * @return the exponent, 0 for a null band, flow 0 or NaN
     */
    public static double flowExponent(double netPlayerItemFlow, FlowBand band) {
        if (band == null || netPlayerItemFlow == 0.0 || Double.isNaN(netPlayerItemFlow)) return 0;
        // Negative sign: net-sold items (flow > 0) push the exponent negative and the price down.
        double exponent = -netPlayerItemFlow / band.capacity();
        // Clamp BEFORE exp so extreme flows can neither overflow to infinity nor underflow the price to 0.
        return Math.min(Math.max(exponent, band.lnMinMult()), band.lnMaxMult());
    }

    /**
     * The flow-equilibrium parameters of one market, all in real (item) units.
     *
     * @param capacity  items needed to move the equilibrium by a factor of e
     * @param lnMinMult ln(minPriceMult) (sanitized), the lowest allowed exponent (&lt; 0)
     * @param lnMaxMult ln(maxPriceMult) (sanitized), the highest allowed exponent (&gt; 0)
     * @param lower     net flow at which the equilibrium reaches maxPriceMult (negative: players net-BOUGHT)
     * @param upper     net flow at which the equilibrium reaches minPriceMult (positive: players net-SOLD)
     */
    public record FlowBand(double capacity, double lnMinMult, double lnMaxMult, double lower, double upper) {}

    /**
     * {@link #flowBand(float, Settings, double)} without the default book (sensitivity capacity only).
     *
     * @param naturalAbundance the market item's natural abundance
     * @param settings         the per-market settings (may be null)
     * @return the band, or null if the flow term is inactive
     */
    public static FlowBand flowBand(float naturalAbundance, Settings settings) {
        return flowBand(naturalAbundance, settings, 0.0);
    }

    /**
     * Single source of the capacity / saturation formula.
     * <pre>
     * capacity = max(flowSensitivity * naturalAbundance / (1 + capacityVolatilityScale), bookEFold / θ)
     * lower    = -capacity * ln(maxPriceMult)   (buy side, price at ceiling)
     * upper    = -capacity * ln(minPriceMult)   (sell side, price at floor)
     * </pre>
     *
     * @param naturalAbundance the market item's natural abundance
     * @param settings         the per-market settings (may be null)
     * @param bookEFold        {@link #bookEFoldDepth} (items per unit log price); &lt;= 0 = sensitivity capacity only
     * @return the band, or null if the flow term is inactive (settings null, flow disabled, invalid capacity)
     */
    public static FlowBand flowBand(float naturalAbundance, Settings settings, double bookEFold) {
        if (settings == null || !settings.flowEnabled()) return null;

        double capacity = capacity(naturalAbundance, settings);
        // Disable the flow term if capacity is invalid (<= 0, NaN or infinite).
        if (!(capacity > 0.0) || Double.isInfinite(capacity)) return null;
        // T-208: never move the equilibrium more than θ x the volume's own walk through the default book.
        if (bookEFold > 0 && Double.isFinite(bookEFold))
            capacity = Math.max(capacity, bookEFold / settings.permanentImpact());

        double lnMin = Math.log(sanitizedMinMult(settings.minPriceMult()));
        double lnMax = Math.log(sanitizedMaxMult(settings.maxPriceMult()));
        return new FlowBand(capacity, lnMin, lnMax, -capacity * lnMax, -capacity * lnMin);
    }

    /** @return {@code m} if in (0, 1), else {@link #DEFAULT_MIN_PRICE_MULT} (also for NaN) */
    static double sanitizedMinMult(double m) {
        return m > 0.0 && m < 1.0 ? m : DEFAULT_MIN_PRICE_MULT;
    }

    /** @return {@code m} if &gt; 1, else {@link #DEFAULT_MAX_PRICE_MULT} (also for NaN) */
    static double sanitizedMaxMult(double m) {
        return m > 1.0 ? m : DEFAULT_MAX_PRICE_MULT;
    }

    /**
     * Items needed to move the equilibrium by a factor of e:
     * {@code flowSensitivity x naturalAbundance / (1 + capacityVolatilityScale)}. Not validated.
     *
     * @param naturalAbundance the market item's natural abundance
     * @param settings         the per-market settings (not null)
     * @return the capacity in items (may be &lt;= 0, NaN or infinite for invalid settings)
     */
    public static double capacity(float naturalAbundance, Settings settings) {
        return (double) settings.flowSensitivity() * naturalAbundance / (1.0 + settings.capacityVolatilityScale());
    }

    /**
     * T-208: the densest default-book depth per unit of log price, both sides, sampled every
     * {@link #BOOK_EFOLD_LN_STEP} over [P0 x e^lnMin, P0 x e^lnMax] (prices below one tick skipped). A one-shot
     * walk of F items moves the log price by at least about {@code F / bookEFold}.
     * Each price level (width {@code tick}) holds {@code |volume(P)|} items, so the depth per unit of log price at
     * P is {@code |volume(P)| x P / tick}.
     *
     * @param volumeAt the default book volume per price level at a real price, market price at P0 (sign ignored)
     * @param p0       the market's default price (real units)
     * @param tick     the real price of one backend price level
     * @param lnMin    ln of the lowest sampled price multiple (&lt; 0)
     * @param lnMax    ln of the highest sampled price multiple (&gt; 0)
     * @return items per unit of log price, 0 if there is no volume or the input is invalid
     */
    public static double bookEFoldDepth(DoubleUnaryOperator volumeAt, double p0, double tick, double lnMin, double lnMax) {
        if (volumeAt == null || !(p0 > 0) || !(tick > 0) || !(lnMax > lnMin)) return 0;
        double max = 0;
        int n = (int) Math.ceil((lnMax - lnMin) / BOOK_EFOLD_LN_STEP);
        for (int i = 0; i <= n; i++) {
            double p = p0 * Math.exp(Math.min(lnMin + i * BOOK_EFOLD_LN_STEP, lnMax));
            if (p < tick) continue;
            double d = Math.abs(volumeAt.applyAsDouble(p)) * p / tick;
            if (d > max && Double.isFinite(d)) max = d;
        }
        return max;
    }

    /**
     * T-208: {@link #bookEFoldDepth(DoubleUnaryOperator, double, double, double, double)} for a market's default
     * book ({@link IPluginOrderBook#getDefaultRealVolumeAround} with the price at P0) over the settings' sanitized
     * [minPriceMult, maxPriceMult] range.
     *
     * @param book     the market's plugin order book (null = 0)
     * @param p0       the market's default price (real units)
     * @param tick     the real price of one backend price level
     * @param settings the per-market settings (null = default clamps)
     * @return items per unit of log price, 0 without volume calculators
     */
    public static double bookEFoldDepth(IPluginOrderBook book, double p0, double tick, Settings settings) {
        if (book == null) return 0;
        double minMult = sanitizedMinMult(settings != null ? settings.minPriceMult() : DEFAULT_MIN_PRICE_MULT);
        double maxMult = sanitizedMaxMult(settings != null ? settings.maxPriceMult() : DEFAULT_MAX_PRICE_MULT);
        return bookEFoldDepth(pick -> book.getDefaultRealVolumeAround(p0, pick), p0, tick, Math.log(minMult), Math.log(maxMult));
    }

    /**
     * T-208: returns the market's cached bookEFold, recomputed when the default price or abundance changed, or
     * every {@link #BOOK_EFOLD_REFRESH_TICKS} ticks. While no volume is found it retries every
     * {@link #BOOK_EFOLD_RETRY_TICKS} ticks and warns once per market; the flow then uses the sensitivity capacity.
     *
     * @param market the market (its order book may be null in tests)
     * @param data   the market's plugin data (cache fields updated)
     * @return bookEFold (items per unit log price), 0 = unknown
     */
    private double refreshBookEFold(MarketInterface market, MarketData data) {
        double p0 = market.market.getDefaultRealPrice();
        float abundance = market.market.getNaturalAbundance();
        if (--data.bookTicksLeft > 0 && p0 == data.bookDefaultPrice && abundance == data.bookAbundance)
            return data.bookEFold;
        data.bookDefaultPrice = p0;
        data.bookAbundance = abundance;
        data.bookEFold = market.oderBook == null ? 0
                : bookEFoldDepth(market.oderBook, p0, MarketManager.convertToRealAmountStatic(1), data.settings);
        if (data.bookEFold > 0) {
            data.bookZeroCount = 0;
            data.bookTicksLeft = BOOK_EFOLD_REFRESH_TICKS;
        } else {
            data.bookTicksLeft = BOOK_EFOLD_RETRY_TICKS;
            if (++data.bookZeroCount >= BOOK_EFOLD_WARN_AFTER && !data.bookWarned) {
                data.bookWarned = true;
                warn("Market " + market.market.getMarketID() + " has no default order book volume (no volume"
                        + " distribution calculator); the permanent impact limit is off, the flow uses the"
                        + " sensitivity capacity only");
            }
        }
        return data.bookEFold;
    }

    /**
     * T-207: advances the plugin-private saturated flow by one tick and records {@code faded} as the last-seen
     * faded flow. The market's own counters are only read.
     * <p>
     * Since the last tick the market faded its flow by one tick and then added the fills, so
     * {@code fills = faded - fade(lastFaded)}; the saturated copy gets the same fade and fills:
     * {@code sat = clamp(fade(sat) + fills, band)}.
     * <ul>
     *   <li>Not seeded yet (new market, old save): {@code sat = clamp(faded)}.</li>
     *   <li>Admin Reset (raw and faded both 0 while the last faded was not): {@code sat = 0}.</li>
     *   <li>No band (flow off, invalid capacity or saturation off): {@code sat = faded}.</li>
     * </ul>
     *
     * @param data            the market's plugin data (lastFaded/satSeeded are updated)
     * @param faded           the market's faded net flow now (items)
     * @param raw             the market's raw net flow now (items), only used to detect a reset
     * @param band            the saturation band, or null for none
     * @param halfLifeSeconds the market's net-flow half-life, &lt;= 0 = no fading
     * @return the new saturated flow (items)
     */
    static double saturatedFlow(MarketData data, double faded, double raw, FlowBand band, double halfLifeSeconds) {
        double sat;
        if (!data.satSeeded) {
            sat = faded;
            data.satSeeded = true;
        } else if (isFlowReset(data, faded, raw)) {
            sat = 0;
        } else {
            double fills = faded - ServerMarket.fade(data.lastFaded, halfLifeSeconds, ServerMarket.TICK_SECONDS);
            sat = ServerMarket.fade(data.satFlow, halfLifeSeconds, ServerMarket.TICK_SECONDS) + fills;
        }
        data.lastFaded = faded;
        if (band == null) return faded;
        return Math.min(Math.max(sat, band.lower()), band.upper());
    }

    /** T-207: an admin flow reset: raw and faded both 0 while the faded flow seen last tick was not. */
    private static boolean isFlowReset(MarketData data, double faded, double raw) {
        return faded == 0 && raw == 0 && data.lastFaded != 0;
    }

    /**
     * @param marketID the market
     * @return the T-208 cached default book depth (items per unit log price), 0 if unknown
     */
    public double getBookEFold(ItemID marketID) {
        MarketData data = marketData.get(marketID);
        return data != null ? data.bookEFold : 0;
    }

    /**
     * @param marketID the market
     * @return the settings the plugin currently runs this market with, null for unknown markets
     */
    public Settings getMarketSettings(ItemID marketID) {
        MarketData data = marketData.get(marketID);
        return data != null ? data.settings : null;
    }

    /**
     * @param marketID the market
     * @return the T-207 plugin-private saturated flow (items), 0 for unknown markets
     */
    public double getSaturatedFlow(ItemID marketID) {
        MarketData data = marketData.get(marketID);
        return data != null ? data.satFlow : 0;
    }

    /**
     * @param marketID the market
     * @return the equilibrium published on the last update, NaN if none yet (unknown market, no VO book)
     */
    public double getEquilibrium(ItemID marketID) {
        MarketData data = marketData.get(marketID);
        return data != null ? data.equilibrium : Double.NaN;
    }

    /**
     * @param marketID the market
     * @return T-213: the anchor as a multiple of the default price ({@code e^anchorLn}), 1 for unknown markets
     */
    public double getAnchorMultiplier(ItemID marketID) {
        MarketData data = marketData.get(marketID);
        return data != null ? Math.exp(data.anchorLn) : 1;
    }

    /**
     * T-213 (tests / migration): sets the anchor of one market.
     *
     * @param marketID the market (must be subscribed)
     * @param anchorLn ln of anchor / default price
     */
    public void setAnchorLn(ItemID marketID, double anchorLn) {
        MarketData data = marketData.get(marketID);
        if (data != null) data.anchorLn = anchorLn;
    }

    @Override
    public void finalize(List<MarketInterface> markets) {
    }

    @Override
    public void onMarketSubscribed(ItemID marketID) {
        marketData.put(marketID, new MarketData());
    }

    @Override
    public void onMarketUnsubscribed(ItemID marketID) {
        marketData.remove(marketID);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    /**
     * Restores the saturated flow state of one market (migration from the VolatilityPlugin).
     *
     * @param marketID  the market (must be subscribed)
     * @param satFlow   saturated flow (items)
     * @param lastFaded last-seen faded flow (items)
     */
    public void restoreFlowState(ItemID marketID, double satFlow, double lastFaded) {
        MarketData data = marketData.get(marketID);
        if (data == null) return;
        data.satFlow = satFlow;
        data.lastFaded = lastFaded;
        data.satSeeded = true;
    }

    @Override
    public boolean save(CompoundTag tag) {
        ListTag marketsTag = new ListTag();
        for (Map.Entry<ItemID, MarketData> entry : marketData.entrySet()) {
            MarketData data = entry.getValue();
            if (!data.satSeeded && data.anchorLn == 0) continue; // nothing to keep (unseeded = seed on the next tick)
            CompoundTag marketTag = new CompoundTag();
            entry.getKey().save(marketTag);
            if (data.satSeeded) {
                marketTag.putDouble("satFlow", data.satFlow);
                marketTag.putDouble("lastFaded", data.lastFaded);
            }
            marketTag.putDouble("anchorLn", data.anchorLn);
            marketsTag.add(marketTag);
        }
        tag.put("marketData", marketsTag);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        // marketData is already populated: subscribeToMarket() runs before load() in ServerPluginManager.
        if (!tag.contains("marketData")) return true;
        ListTag marketsTag = NbtCompat.getList(tag, "marketData", 10);
        for (int i = 0; i < marketsTag.size(); i++) {
            CompoundTag marketTag = NbtCompat.getCompound(marketsTag, i);
            ItemID marketID = ItemID.createFromTag(marketTag);
            if (marketID == null || !marketID.isValid()) continue;
            if (marketTag.contains("satFlow") && marketTag.contains("lastFaded"))
                restoreFlowState(marketID, NbtCompat.getDouble(marketTag, "satFlow"), NbtCompat.getDouble(marketTag, "lastFaded"));
            if (marketTag.contains("anchorLn")) setAnchorLn(marketID, NbtCompat.getDouble(marketTag, "anchorLn"));
        }
        return true;
    }

    @Override
    protected StreamCodec<ByteBuf, RuntimeStreamData> runtimeDataCodec() {
        return RuntimeStreamData.CODEC;
    }

    @Override
    protected RuntimeStreamData provideRuntimeData() {
        if (marketData.isEmpty()) return null;
        short[] ids = new short[marketData.size()];
        double[] mults = new double[ids.length];
        int i = 0;
        for (Map.Entry<ItemID, MarketData> e : marketData.entrySet()) {
            ids[i] = e.getKey().getShort();
            mults[i++] = Math.exp(e.getValue().anchorLn);
        }
        return new RuntimeStreamData(ids, mults);
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
        MarketData data = marketData.get(marketID);
        if (data != null) {
            data.settings = settings;
            data.bookTicksLeft = 0; // T-208: the clamps define the bookEFold range, recompute next tick
        }
    }
}
