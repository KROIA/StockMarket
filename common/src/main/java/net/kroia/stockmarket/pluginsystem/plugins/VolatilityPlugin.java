package net.kroia.stockmarket.pluginsystem.plugins;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.TimerMillis;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.util.NormalizedRandomPriceGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class VolatilityPlugin extends ServerPlugin<VolatilityPlugin.Settings, Void> {

    // ── Default settings values ─────────────────────────────────────────

    /** Default random-walk amplitude relative to the equilibrium price (30%). */
    public static final float DEFAULT_VOLATILITY_SCALE = 0.3f;
    /** Flow-driven price equilibrium is active by default. */
    public static final boolean DEFAULT_FLOW_INFLUENCE_ENABLED = true;
    /**
     * Default flow sensitivity: items per unit of naturalAbundance that shift
     * the equilibrium price by a factor of e (~2.72x).
     * <p>
     * Reasoning: default preset abundances range from ~5 (diamond) to ~200
     * (cobblestone/dirt), with the preset fallback at 10. With a sensitivity of
     * 500 the "capacity" (items needed for one factor-e move, before the
     * volatility correction) is:
     * <ul>
     *   <li>diamond (abundance 5): ~2,500 diamonds net-sold to drop the price to ~37%</li>
     *   <li>iron ingot (abundance 30): ~15,000 ingots</li>
     *   <li>cobblestone (abundance 200): ~100,000 blocks</li>
     * </ul>
     * These match realistic trade volumes on a small server: bulk farmable items
     * need huge dumps to crash, while rare items react to a few thousand units.
     * Smaller moves happen proportionally earlier (e.g. ~10% price drop after
     * ~capacity/10 items), so the market visibly responds long before capacity
     * is reached.
     */
    public static final float DEFAULT_FLOW_SENSITIVITY = 500f;
    /** Default lower clamp of the equilibrium: never below 5% of the default price. */
    public static final float DEFAULT_MIN_PRICE_MULT = 0.05f;
    /** Default upper clamp of the equilibrium: never above 20x the default price. */
    public static final float DEFAULT_MAX_PRICE_MULT = 20.0f;

    /**
     * Custom per-market settings for the volatility and flow-equilibrium behavior.
     *
     * @param volatilityScale      amplitude of the random price walk relative to the
     *                             equilibrium price (0.3 = price wanders roughly +-30%)
     * @param flowInfluenceEnabled if true, the anchor price permanently shifts with the
     *                             net amount of items players traded into/out of the market
     *                             (sell pressure lowers it, buy pressure raises it);
     *                             if false, the anchor stays at the market's default price
     * @param flowSensitivity      how many items (per unit of the market's naturalAbundance)
     *                             players must net-sell to lower the equilibrium price by a
     *                             factor of e (~2.72x). Higher = more inert market.
     *                             Values &lt;= 0 disable the flow influence
     * @param minPriceMult         lower clamp for the equilibrium as a multiple of the
     *                             default price (0.05 = never below 5% of default).
     *                             Must be in (0, 1); invalid values fall back to the default
     * @param maxPriceMult         upper clamp for the equilibrium as a multiple of the
     *                             default price (20 = never above 20x default).
     *                             Must be &gt; 1; invalid values fall back to the default
     */
    public record Settings(float volatilityScale, boolean flowInfluenceEnabled,
                           float flowSensitivity, float minPriceMult, float maxPriceMult) {
        public static final StreamCodec<ByteBuf, Settings> CODEC = new StreamCodec<>() {
            @Override
            public Settings decode(ByteBuf buf) {
                float volatilityScale = buf.readFloat();
                // Backward compatibility: the old format stored only volatilityScale
                // (a single float, 4 bytes). Every decode path (NBT save data, sync
                // packets, settings requests) wraps exactly one settings payload per
                // buffer, so "no bytes left" reliably identifies the old format.
                if (!buf.isReadable()) {
                    return new Settings(volatilityScale, DEFAULT_FLOW_INFLUENCE_ENABLED,
                            DEFAULT_FLOW_SENSITIVITY, DEFAULT_MIN_PRICE_MULT, DEFAULT_MAX_PRICE_MULT);
                }
                boolean flowInfluenceEnabled = buf.readBoolean();
                float flowSensitivity = buf.readFloat();
                float minPriceMult = buf.readFloat();
                float maxPriceMult = buf.readFloat();
                return new Settings(volatilityScale, flowInfluenceEnabled,
                        flowSensitivity, minPriceMult, maxPriceMult);
            }
            @Override
            public void encode(ByteBuf buf, Settings s) {
                buf.writeFloat(s.volatilityScale());
                buf.writeBoolean(s.flowInfluenceEnabled());
                buf.writeFloat(s.flowSensitivity());
                buf.writeFloat(s.minPriceMult());
                buf.writeFloat(s.maxPriceMult());
            }
        };

        /** Creates a Settings instance with all default values. */
        public static Settings createDefault() {
            return new Settings(DEFAULT_VOLATILITY_SCALE, DEFAULT_FLOW_INFLUENCE_ENABLED,
                    DEFAULT_FLOW_SENSITIVITY, DEFAULT_MIN_PRICE_MULT, DEFAULT_MAX_PRICE_MULT);
        }
    }
    /**
     * Per-market data holding an independent price generator and timer,
     * so each market gets its own random walk.
     */
    static class MarketData {
        final NormalizedRandomPriceGenerator priceGenerator;
        final TimerMillis timer;
        Settings settings = Settings.createDefault();

        MarketData() {
            priceGenerator = new NormalizedRandomPriceGenerator(5);
            timer = new TimerMillis(false);
            timer.start(random.nextInt(10000));
        }
    }

    private static final Random random = new Random();
    private final Map<ItemID, MarketData> marketData = new HashMap<>();

    public VolatilityPlugin()
    {
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

            // Each market advances its own random walk timer independently
            if (data.timer.check()) {
                data.timer.start(100 + random.nextLong(100 * 10L + 1));
                data.priceGenerator.getNextValue();
            }

            // Anchor the random walk at a flow-driven equilibrium instead of the
            // static default price, so heavy player selling permanently lowers the
            // price range and heavy player buying permanently raises it.
            double defaultPrice = market.market.getDefaultRealPrice();
            double equilibrium = computeEquilibriumPrice(defaultPrice,
                    market.market.getNetPlayerItemFlow(),
                    market.market.getNaturalAbundance(),
                    data.settings);

            // Scale random walk proportionally to the equilibrium price for equal percentage volatility
            double deviation = data.priceGenerator.getCurrentValue() * data.settings.volatilityScale();
            market.market.setTargetPrice(Math.max(0, equilibrium * (1.0 + deviation)));
        }
    }

    /**
     * Computes the flow-driven equilibrium price around which the random walk oscillates.
     * <p>
     * Formula (all values in real/double units):
     * <pre>
     * capacity    = flowSensitivity * naturalAbundance / (1 + volatilityScale)
     * exponent    = clamp(-netPlayerItemFlow / capacity, ln(minPriceMult), ln(maxPriceMult))
     * equilibrium = defaultPrice * e^exponent
     * </pre>
     * SIGN CONVENTION (verified against ServerMarket.trackPlayerNetFlow):
     * players net-SELL items into the market -&gt; netPlayerItemFlow &gt; 0
     * -&gt; exponent &lt; 0 -&gt; equilibrium (price) goes DOWN.
     * Players net-BUY items from the market -&gt; netPlayerItemFlow &lt; 0
     * -&gt; exponent &gt; 0 -&gt; equilibrium (price) goes UP.
     * <p>
     * The exponent is clamped BEFORE {@link Math#exp}, so extreme flows can never
     * overflow to infinity, and because minPriceMult is enforced &gt; 0 the result
     * is always strictly positive for a positive defaultPrice.
     * <p>
     * Fallback behavior (returns exactly {@code defaultPrice}, identical to the
     * pre-equilibrium code path):
     * <ul>
     *   <li>settings null or {@code flowInfluenceEnabled} == false</li>
     *   <li>netPlayerItemFlow == 0 or NaN</li>
     *   <li>capacity not strictly positive/finite (e.g. abundance or sensitivity &lt;= 0 or NaN)</li>
     * </ul>
     * Invalid clamp multipliers (violating 0 &lt; min &lt; 1 &lt; max) are sanitized
     * to the defaults instead of crashing.
     * <p>
     * Pure static function (no plugin state) so it can be unit-tested in isolation.
     *
     * @param defaultPrice      the market's configured default price in real units (anchor when flow is 0)
     * @param netPlayerItemFlow cumulative net items players sold into the market (positive) or
     *                          bought out of it (negative), in real units
     * @param naturalAbundance  the market item's natural abundance (higher = more common item)
     * @param settings          the per-market plugin settings (may be null)
     * @return the equilibrium price the random walk should oscillate around, always &gt;= 0
     *         and strictly positive whenever defaultPrice &gt; 0
     */
    public static double computeEquilibriumPrice(double defaultPrice, double netPlayerItemFlow,
                                                 float naturalAbundance, Settings settings) {
        // Feature disabled or no flow: keep the anchor exactly at the default price
        // (bit-identical to the previous behavior).
        if (settings == null || !settings.flowInfluenceEnabled()) return defaultPrice;
        if (netPlayerItemFlow == 0.0 || Double.isNaN(netPlayerItemFlow)) return defaultPrice;

        // Items needed to move the price by a factor of e. Dividing by
        // (1 + volatilityScale) makes highly volatile markets react slightly
        // faster to player flow.
        double capacity = (double) settings.flowSensitivity() * naturalAbundance
                / (1.0 + settings.volatilityScale());
        // Disable the flow term if capacity is invalid (<= 0, NaN or infinite),
        // e.g. abundance or sensitivity set to 0/negative by an admin.
        if (!(capacity > 0.0) || Double.isInfinite(capacity)) return defaultPrice;

        // Sanitize clamp multipliers: enforce 0 < minPriceMult < 1 < maxPriceMult.
        // (The "!(x > y)" form also catches NaN.)
        double minMult = settings.minPriceMult();
        double maxMult = settings.maxPriceMult();
        if (!(minMult > 0.0) || !(minMult < 1.0)) minMult = DEFAULT_MIN_PRICE_MULT;
        if (!(maxMult > 1.0)) maxMult = DEFAULT_MAX_PRICE_MULT;

        // Negative sign flips the direction: net-sold items (flow > 0) push the
        // exponent negative and therefore the price down; net-bought items
        // (flow < 0) push it positive and the price up.
        double exponent = -netPlayerItemFlow / capacity;
        // Clamp BEFORE exp so extreme flows can neither overflow to infinity
        // nor underflow the price to 0.
        exponent = Math.min(Math.max(exponent, Math.log(minMult)), Math.log(maxMult));

        return defaultPrice * Math.exp(exponent);
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

    @Override
    public boolean save(CompoundTag tag) {
        // Save per-market price generator state so random walks persist across restarts
        ListTag marketsTag = new ListTag();
        for (Map.Entry<ItemID, MarketData> entry : marketData.entrySet()) {
            CompoundTag marketTag = new CompoundTag();
            entry.getKey().save(marketTag);
            CompoundTag generatorTag = new CompoundTag();
            entry.getValue().priceGenerator.save(generatorTag);
            marketTag.put("priceGenerator", generatorTag);
            Settings s = entry.getValue().settings;
            marketTag.putFloat("volatilityScale", s.volatilityScale());
            marketTag.putBoolean("flowInfluenceEnabled", s.flowInfluenceEnabled());
            marketTag.putFloat("flowSensitivity", s.flowSensitivity());
            marketTag.putFloat("minPriceMult", s.minPriceMult());
            marketTag.putFloat("maxPriceMult", s.maxPriceMult());
            marketsTag.add(marketTag);
        }
        tag.put("marketData", marketsTag);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        // Restore per-market price generator state (marketData map is already populated
        // because subscribeToMarket() is called before load() in ServerPluginManager)
        if (tag.contains("marketData")) {
            ListTag marketsTag = tag.getList("marketData", 10);
            for (int i = 0; i < marketsTag.size(); i++) {
                CompoundTag marketTag = marketsTag.getCompound(i);
                ItemID marketID = ItemID.createFromTag(marketTag);
                if (marketID != null && marketID.isValid()) {
                    MarketData data = marketData.get(marketID);
                    if (data != null) {
                        if (marketTag.contains("priceGenerator")) {
                            data.priceGenerator.load(marketTag.getCompound("priceGenerator"));
                        }
                        // Each field falls back to the currently applied settings so
                        // old saves (which only stored volatilityScale) keep the
                        // defaults for the newer flow-equilibrium fields.
                        Settings cur = data.settings;
                        data.settings = new Settings(
                                marketTag.contains("volatilityScale") ? marketTag.getFloat("volatilityScale") : cur.volatilityScale(),
                                marketTag.contains("flowInfluenceEnabled") ? marketTag.getBoolean("flowInfluenceEnabled") : cur.flowInfluenceEnabled(),
                                marketTag.contains("flowSensitivity") ? marketTag.getFloat("flowSensitivity") : cur.flowSensitivity(),
                                marketTag.contains("minPriceMult") ? marketTag.getFloat("minPriceMult") : cur.minPriceMult(),
                                marketTag.contains("maxPriceMult") ? marketTag.getFloat("maxPriceMult") : cur.maxPriceMult());
                    }
                }
            }
        }
        return true;
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
        }
    }
}
