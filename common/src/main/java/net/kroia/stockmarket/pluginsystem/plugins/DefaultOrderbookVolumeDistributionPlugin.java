package net.kroia.stockmarket.pluginsystem.plugins;

import net.kroia.modutilities.NbtCompat;


import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.plugin.interaction.IPluginMarket;
import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;
import net.kroia.stockmarket.api.plugin.interaction.IVolumeDistributionCalculator;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.kroia.modutilities.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultOrderbookVolumeDistributionPlugin extends ServerPlugin<DefaultOrderbookVolumeDistributionPlugin.Settings, DefaultOrderbookVolumeDistributionPlugin.RuntimeStreamData> {

    /**
     * Custom settings record for volume scale and convergence speed parameters.
     *
     * @param trendPeriodMinutes trend accumulation: time constant of the price's moving average (minutes)
     * @param trendGain          trend accumulation: gain k of the side factor {@code exp(k * ln(price / MA))}
     * @param trendMaxFactor     trend accumulation: clamp of the side factor to [1 / max, max]; 1 = off
     */
    public record Settings(float volumeScale, float speed, float accumulationRate, float decumulationRate, boolean resetVolume,
                           float trendPeriodMinutes, float trendGain, float trendMaxFactor) {
        public Settings(float volumeScale, float speed, float accumulationRate, float decumulationRate) {
            this(volumeScale, speed, accumulationRate, decumulationRate, false);
        }

        public Settings(float volumeScale, float speed, float accumulationRate, float decumulationRate, boolean resetVolume) {
            this(volumeScale, speed, accumulationRate, decumulationRate, resetVolume,
                    DEFAULT_TREND_PERIOD_MINUTES, DEFAULT_TREND_GAIN, DEFAULT_TREND_MAX_FACTOR);
        }

        /** @return the default settings of a newly subscribed market (no volume reset) */
        public static Settings createDefault() {
            return new Settings(DEFAULT_VOLUME_SCALE, DEFAULT_SPEED, DEFAULT_ACCUMULATION_RATE, DEFAULT_DECUMULATION_RATE);
        }

        /** @return a copy with other trend accumulation parameters */
        public Settings withTrend(float periodMinutes, float gain, float maxFactor) {
            return new Settings(volumeScale, speed, accumulationRate, decumulationRate, resetVolume, periodMinutes, gain, maxFactor);
        }

        public static final StreamCodec<ByteBuf, Settings> CODEC = new StreamCodec<>() {
            @Override
            public Settings decode(ByteBuf buf) {
                float scale = buf.readFloat();
                float speed = buf.readFloat();
                float acc = buf.readFloat();
                float dec = buf.readFloat();
                boolean reset = buf.readBoolean();
                // Trend accumulation, appended later: every decode path wraps exactly one settings payload per
                // buffer, so "no bytes left" identifies the old format (defaults).
                float period = buf.isReadable() ? buf.readFloat() : DEFAULT_TREND_PERIOD_MINUTES;
                float gain = buf.isReadable() ? buf.readFloat() : DEFAULT_TREND_GAIN;
                float max = buf.isReadable() ? buf.readFloat() : DEFAULT_TREND_MAX_FACTOR;
                return new Settings(scale, speed, acc, dec, reset, period, gain, max);
            }
            @Override
            public void encode(ByteBuf buf, Settings s) {
                buf.writeFloat(s.volumeScale());
                buf.writeFloat(s.speed());
                buf.writeFloat(s.accumulationRate());
                buf.writeFloat(s.decumulationRate());
                buf.writeBoolean(s.resetVolume());
                buf.writeFloat(s.trendPeriodMinutes());
                buf.writeFloat(s.trendGain());
                buf.writeFloat(s.trendMaxFactor());
            }
        };
    }

    /**
     * Runtime data containing sampled volume distribution points per market,
     * computed on the server using the actual DistributionCalculator formula.
     */
    public record RuntimeStreamData(List<MarketDistribution> entries) {
        /**
         * Sampled default volume of one market: {@code volumes[k]} is the raw volume per backend price
         * point at real price {@code startPrice + k * priceStep}.
         */
        public record MarketDistribution(short itemId, double startPrice, double priceStep, float[] volumes) {
            /**
             * Linearly interpolates the sampled volume at {@code price}.
             *
             * @param price real price to sample
             * @return the interpolated volume, 0 outside the sampled range or for NaN/infinite input
             */
            public float volumeAt(double price) {
                double fractionalIndex = (price - startPrice) / priceStep;
                // Range check in double before the int cast: zoomed far out the index saturates at
                // Integer.MAX_VALUE and low + 1 overflowed past an int check (crash).
                if (!(fractionalIndex >= 0) || !(fractionalIndex < volumes.length - 1)) return 0;
                int low = (int) fractionalIndex;
                double t = fractionalIndex - low;
                return (float) (volumes[low] * (1 - t) + volumes[low + 1] * t);
            }
        }

        public static final StreamCodec<ByteBuf, RuntimeStreamData> CODEC = new StreamCodec<>() {
            @Override
            public RuntimeStreamData decode(ByteBuf buf) {
                int count = buf.readInt();
                List<MarketDistribution> entries = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    short itemId = buf.readShort();
                    double startPrice = buf.readDouble();
                    double priceStep = buf.readDouble();
                    int volCount = buf.readInt();
                    float[] volumes = new float[volCount];
                    for (int j = 0; j < volCount; j++) {
                        volumes[j] = buf.readFloat();
                    }
                    entries.add(new MarketDistribution(itemId, startPrice, priceStep, volumes));
                }
                return new RuntimeStreamData(entries);
            }
            @Override
            public void encode(ByteBuf buf, RuntimeStreamData data) {
                buf.writeInt(data.entries().size());
                for (MarketDistribution e : data.entries()) {
                    buf.writeShort(e.itemId());
                    buf.writeDouble(e.startPrice());
                    buf.writeDouble(e.priceStep());
                    buf.writeInt(e.volumes().length);
                    for (float v : e.volumes()) {
                        buf.writeFloat(v);
                    }
                }
            }
        };
    }

    /**
     * The default virtual depth profile. Public so in-game tests can build a market with the
     * production depth shape without running the plugin (see PluginTestSuite #89 replay).
     * <p>
     * T-214: the volume is {@code shape(|p - P| / P) x depthScale x D / P} (items per raw price tick), with the
     * depth scale from {@link #depthScaleFor}. The {@code D / P} factor keeps the depth per unit of
     * <em>relative</em> price constant when the price moves away from the default price D.
     */
    public static class DistributionCalculator implements IVolumeDistributionCalculator
    {
        private float volumeScale = 1000;
        private double defaultPrice = 0;

        public DistributionCalculator()
        {

        }

        /**
         * @param defaultPrice the market's default (fair) price in real units (0 = no value cluster, no
         *                     price normalisation)
         * @param volumeScale  depth scale in items per raw tick at {@code P = D}; the plugin uses
         *                     {@link #depthScaleFor}
         */
        public DistributionCalculator(double defaultPrice, float volumeScale)
        {
            this.defaultPrice = defaultPrice;
            this.volumeScale = volumeScale;
        }

        @Override
        public float getVolume(double marketPrice, double volumePickPrice) {
            if (marketPrice <= 0) return 0;

            double absDistance = Math.abs((volumePickPrice - marketPrice) / marketPrice);

            // Spread gap width: at least one tick (0.01) relative to market price
            double spreadWidth = Math.max(0.005, 0.01 / marketPrice);
            double spreadNorm = absDistance / spreadWidth;

            // Bouchaud-style shape: delta^(1-mu) rise near spread, exponential decay further out.
            // mu ~ 0.6 (empirical power-law exponent), so volume grows as delta^0.4 near the spread.
            double coreShape = Math.pow(spreadNorm + 0.001, 0.4) * Math.exp(-absDistance / 0.15);
            // T-214: the spread gap is at least one 0.01 tick, so on cheap items (P < 2) spreadWidth grows as 1 / P and
            // (d / spreadWidth)^0.4 shrank the core away from the price: dirt (0.1) held only ~58 max orders of asks
            // against ~160 on diamond, so a 60-order pump emptied the book. Scaling by (spreadWidth / 0.005)^0.4 makes
            // the core density outside the spread independent of the tick-to-price ratio; at P >= 2 the factor is 1.
            coreShape *= Math.pow(spreadWidth / 0.005, 0.4);

            // Value-seeking cluster: Gaussian bump near the default/fair price
            double valueCluster = 0;
            if (defaultPrice > 0) {
                double fairRelDistance = (volumePickPrice - defaultPrice) / defaultPrice;
                double sigma = 0.15;
                valueCluster = 0.4 * Math.exp(-fairRelDistance * fairRelDistance / (2 * sigma * sigma));
            }

            // Background liquidity: thin but persistent orders at all price levels
            double background = 0.08 * Math.exp(-absDistance / 0.6);

            // Buy-side demand boost: increasing buying interest at lower prices
            double demandBoost = 0;
            if (volumePickPrice < marketPrice) {
                double dropFraction = (marketPrice - volumePickPrice) / marketPrice;
                demandBoost = 0.5 * Math.sqrt(dropFraction);
            }

            // T-214: depth per relative price stays constant as the price moves (more ticks per % below D)
            double priceNorm = defaultPrice > 0 ? defaultPrice / marketPrice : 1;
            return (float) ((coreShape + valueCluster + background + demandBoost) * volumeScale * priceNorm);
        }

        /** @return the depth scale (items per raw tick at the default price) */
        public float getVolumeScale() { return volumeScale; }

        /** @return the default price the shape and the normalisation use */
        public double getDefaultPrice() { return defaultPrice; }
    }

    /**
     * T-214: book density per unit of relative price, per max order item and volumeScale unit. At the default
     * price the asks within +1% then hold about 3.47 max orders, so one max order walks the price about 0.36%
     * on every market. 250 is exactly the pre-T-214 diamond (160, abundance 50, max order 3,200).
     */
    public static final double DEPTH_PER_MAX_ORDER = 250;
    /**
     * T-214: floor of the depth scale per volumeScale unit, in items per raw tick. Keeps expensive items
     * (netherite, dragon egg) from a book so thin that single items jump the price.
     */
    public static final double MIN_DEPTH_PER_TICK = 0.1;

    /**
     * T-214: depth scale of a market, sized to its max order:
     * {@code volumeScale x max(250 x maxOrder x tick / D, 0.1)}, with tick the real price of one raw tick.
     * The density per unit of relative price is then {@code 250 x maxOrder x volumeScale} on every market.
     *
     * @param defaultPrice the market's default price D (real); &lt;= 0 gives the floor
     * @param maxOrder     the effective max order (real items, see {@link #effectiveMaxOrder})
     * @param volumeScale  the plugin's volumeScale setting (relative multiplier, 1 = default)
     * @return the calculator's depth scale (items per raw tick at {@code P = D})
     */
    public static float depthScaleFor(double defaultPrice, double maxOrder, float volumeScale)
    {
        double tick = MarketManager.convertToRealAmountStatic(1);
        double perTick = defaultPrice > 0 ? DEPTH_PER_MAX_ORDER * maxOrder * tick / defaultPrice : 0;
        return (float) (volumeScale * Math.max(perTick, MIN_DEPTH_PER_TICK));
    }

    /**
     * T-214: the max order the depth is sized to. An unlimited market (0) uses the default formula
     * {@link MarketSettings#defaultMaxOrderVolume(float, long)} with the compile-time factor 64, so the book does
     * not depend on the MAX_ORDER_VOLUME_FACTOR mod setting.
     *
     * @param maxOrder  the market's max order volume (real items), &lt;= 0 = unlimited
     * @param abundance the market's natural abundance
     * @return the max order in real items, &gt; 0
     */
    public static double effectiveMaxOrder(double maxOrder, float abundance)
    {
        return maxOrder > 0 ? maxOrder : MarketSettings.defaultMaxOrderVolume(abundance, MarketSettings.DEFAULT_MAX_ORDER_VOLUME_FACTOR);
    }

    private static final float DEFAULT_VOLUME_SCALE = 1.0f;
    private static final float DEFAULT_SPEED = 0.05f;
    private static final float DEFAULT_ACCUMULATION_RATE = 0.1f;
    private static final float DEFAULT_DECUMULATION_RATE = 0.01f;
    /** Trend accumulation: moving average time constant in minutes. */
    public static final float DEFAULT_TREND_PERIOD_MINUTES = 10f;
    /** Trend accumulation: side factor gain k. */
    public static final float DEFAULT_TREND_GAIN = 10f;
    /** Trend accumulation: side factor clamp (1 = off). */
    public static final float DEFAULT_TREND_MAX_FACTOR = 4f;

    static class RuntimeData
    {
        public float currentMarketPrice = 0;
        public final DistributionCalculator calculator;
        public float volumeScale = DEFAULT_VOLUME_SCALE;
        public float speed = DEFAULT_SPEED;
        public float accumulationRate = DEFAULT_ACCUMULATION_RATE;
        public float decumulationRate = DEFAULT_DECUMULATION_RATE;
        public boolean pendingReset = false;
        /** T-214: effective max order the depth scale was computed for (NaN = not yet bound to a market). */
        public double depthMaxOrder = Double.NaN;
        public float trendPeriodMinutes = DEFAULT_TREND_PERIOD_MINUTES;
        public float trendGain = DEFAULT_TREND_GAIN;
        public float trendMaxFactor = DEFAULT_TREND_MAX_FACTOR;
        /** Trend accumulation: ln of the price's moving average, NaN = start at the next price. */
        public double trendLnMA = Double.NaN;
        /** Trend accumulation: last ask side factor (bid side = 1 / this). */
        public float trendAskFactor = 1f;

        // ---- Target-distribution cache ----
        // The expensive distribution targets (Math.pow/exp/sqrt per price level) are cached
        // together with the inputs they were computed from. As long as those inputs are
        // unchanged, the cheap convergence loop reuses the cached targets and the recompute
        // is skipped entirely.
        public float[] cachedTargets = null;            // raw target volume per editable price level
        public long cachedRangeStart = Long.MIN_VALUE;  // first backend price of the cached range
        public float cachedMarketPrice = Float.NaN;     // market price used for the computation (drives the buy/sell sign flip)
        public float cachedVolumeScale = Float.NaN;     // effective calculator volume scale used (changes via settings/load)
        public double cachedDefaultPrice = Double.NaN;  // calculator default/fair price used

        public RuntimeData()
        {
            calculator = new DistributionCalculator();
        }
    }
    private final Map<ItemID, RuntimeData> marketData = new HashMap<>();

    /** T-214: NBT key of the depth model version; saves without it (pre-T-214 books) get one hard reset on load. */
    private static final String DEPTH_MODEL_KEY = "depthModel";
    /** T-214: current depth model (price-normalised, sized to the max order). */
    private static final int DEPTH_MODEL = 2;

    /**
     * T-214: recomputes the calculator's default price and depth scale from the market. With
     * {@code resetOnChange}, a changed default price or effective max order (after the first binding) also
     * schedules a hard reset, so the book is rebuilt at the new depth instead of converging slowly.
     *
     * @param data          the market's runtime data
     * @param market        the market
     * @param resetOnChange true = a change sets {@link RuntimeData#pendingReset}
     */
    private static void refreshDepth(RuntimeData data, IPluginMarket market, boolean resetOnChange)
    {
        double d = market.getDefaultRealPrice();
        double maxOrder = effectiveMaxOrder(market.getMaxRealOrderVolume(), market.getNaturalAbundance());
        if (resetOnChange && !Double.isNaN(data.depthMaxOrder)
                && (d != data.calculator.defaultPrice || maxOrder != data.depthMaxOrder))
            data.pendingReset = true;
        data.depthMaxOrder = maxOrder;
        data.calculator.defaultPrice = d;
        data.calculator.volumeScale = depthScaleFor(d, maxOrder, data.volumeScale);
    }

    /** @return a subscribed market's distribution calculator, null if not subscribed (tests) */
    public DistributionCalculator test_getCalculator(ItemID marketID)
    {
        RuntimeData data = marketData.get(marketID);
        return data == null ? null : data.calculator;
    }

    public DefaultOrderbookVolumeDistributionPlugin()
    {
        super();
    }

    @Override
    public void init() {

    }

    @Override
    public void deInit() {

    }

    // Maximum number of markets that may recompute their expensive target distribution
    // per update cycle (round-robin). All other markets keep converging toward their
    // cached targets until it is their turn again.
    private static final int MAX_TARGET_REFRESHES_PER_UPDATE = 20;

    // Rotating start index of the round-robin refresh window.
    private int targetRefreshRotation = 0;

    @Override
    public void update(List<MarketInterface> markets)
    {
        int marketCount = markets.size();
        if(marketCount == 0)
            return;

        // Round-robin: markets at indices [rotationStart, rotationStart+K) (mod n) may
        // refresh their target distribution this cycle. Advancing the rotation by K each
        // update guarantees every market gets a turn, even when markets subscribe or
        // unsubscribe in between (the modulo keeps the index valid for any list size).
        int rotationStart = ((targetRefreshRotation % marketCount) + marketCount) % marketCount;
        targetRefreshRotation = (rotationStart + MAX_TARGET_REFRESHES_PER_UPDATE) % marketCount;

        for(int i = 0; i < marketCount; i++)
        {
            MarketInterface market = markets.get(i);
            RuntimeData data = marketData.get(market.market.getMarketID());
            if(data == null)
                continue; // No runtime data for this market (interface without subscription) -> skip
            refreshDepth(data, market.market, true);   // T-214: max order / default price changes rebuild the book
            // Markets with the virtual orderbook disabled are outside this plugin's scope:
            // there is no virtual depth to converge toward a target distribution on.
            if(!market.market.isVirtualOrderbookEnabled())
                continue;
            data.currentMarketPrice = (float) market.market.getPrice();
            updateTrend(market, data);
            boolean mayRefreshTargets =
                    ((i - rotationStart + marketCount) % marketCount) < MAX_TARGET_REFRESHES_PER_UPDATE;
            updateForMarket(market, data, mayRefreshTargets);
        }
    }

    /**
     * Trend accumulation: ask side factor {@code clamp(exp(gain * (lnPrice - lnMA)), 1 / maxFactor, maxFactor)};
     * the bid side factor is its inverse. Above the moving average (rising) the asks refill faster and the bids
     * slower, below it the other way round, so the book leans against the current move.
     *
     * @param lnPrice   ln of the current price
     * @param lnMA      ln of the price's moving average
     * @param gain      gain k (&lt;= 0 = neutral)
     * @param maxFactor clamp (&lt;= 1 = neutral)
     * @return the ask side factor, 1 if any input is not finite
     */
    public static float trendAskFactor(double lnPrice, double lnMA, float gain, float maxFactor)
    {
        if(!(maxFactor > 1) || !(gain > 0) || !Double.isFinite(lnPrice) || !Double.isFinite(lnMA)) return 1f;
        double lnMax = Math.log(maxFactor);
        double x = Math.max(-lnMax, Math.min(lnMax, gain * (lnPrice - lnMA)));
        return (float) Math.exp(x);
    }

    /**
     * Trend accumulation: one tick of the moving average (exponential in ln price, time constant
     * {@code trendPeriodMinutes}), then sends the side factors to the book's depth memory.
     */
    private void updateTrend(MarketInterface market, RuntimeData data)
    {
        double price = data.currentMarketPrice;
        if(!(price > 0)) return;
        double lnP = Math.log(price);
        if(Double.isNaN(data.trendLnMA)) data.trendLnMA = lnP;
        double periodSeconds = Math.max(1.0, data.trendPeriodMinutes * 60.0);
        data.trendLnMA += (lnP - data.trendLnMA) * (1 - Math.exp(-ServerMarket.TICK_SECONDS / periodSeconds));
        data.trendAskFactor = trendAskFactor(lnP, data.trendLnMA, data.trendGain, data.trendMaxFactor);
        market.oderBook.setVirtualDepthSideFactors(1f / data.trendAskFactor, data.trendAskFactor);
    }

    /** @return the trend accumulation ask side factor of a subscribed market (1 = neutral; tests and reports) */
    public float test_getTrendAskFactor(ItemID marketID)
    {
        RuntimeData data = marketData.get(marketID);
        return data == null ? 1f : data.trendAskFactor;
    }

    /** @return ln of a subscribed market's moving average, NaN if none yet (tests) */
    public double test_getTrendLnMA(ItemID marketID)
    {
        RuntimeData data = marketData.get(marketID);
        return data == null ? Double.NaN : data.trendLnMA;
    }

    /**
     * Converges the virtual orderbook volume of one market toward its target distribution.
     *
     * @param market the market to update
     * @param data the plugin's runtime data for that market
     * @param mayRefreshTargets true if this market is inside the round-robin refresh window
     *                          and may recompute its (expensive) target distribution this cycle
     */
    private void updateForMarket(MarketInterface market, RuntimeData data, boolean mayRefreshTargets)
    {
        Tuple<@NotNull Long,@NotNull  Long> editableRange = market.oderBook.getEditableBackendPriceRange();
        // T-202: game time, one server tick per update (as the bot's PID, T-201), so convergence
        // does not depend on server speed or wall-clock jitter.
        double deltaT = ServerMarket.TICK_SECONDS;

        IPluginOrderBook orderBook = market.oderBook;
        long rangeStart = editableRange.getA();
        int rangeSize = (int)(editableRange.getB() - rangeStart + 1);

        boolean hardReset = data.pendingReset;
        if (hardReset)
            data.pendingReset = false;

        // The cached target array is only usable at all if it still covers the current
        // editable range. The range shifts when the dynamic array re-centers on large
        // price moves (see VirtualOrderbook.setCurrentMarketPrice).
        boolean rangeMatches = data.cachedTargets != null
                && data.cachedTargets.length == rangeSize
                && data.cachedRangeStart == rangeStart;
        // The cache is fully valid only if none of the distribution inputs changed:
        // market price (also drives the buy/sell sign flip), effective volume scale
        // (settings change / load) and default price.
        boolean cacheValid = rangeMatches
                && data.cachedMarketPrice == data.currentMarketPrice
                && data.cachedVolumeScale == data.calculator.volumeScale
                && data.cachedDefaultPrice == data.calculator.defaultPrice;

        // Recompute the expensive target distribution only when needed:
        // - immediately if there is no usable cache (first update after subscribe,
        //   editable-range shift) or on a hard reset (bypasses the rotation),
        // - otherwise only when it is this market's round-robin turn. Until then the
        //   market keeps converging toward its slightly stale cached targets, which is
        //   an acceptable transient because the convergence itself is time-based.
        if (!cacheValid && (mayRefreshTargets || !rangeMatches || hardReset))
        {
            // Batch computation: market price and item fraction scale factor are fetched
            // once for the whole range instead of once per price level.
            data.cachedTargets = orderBook.getDefaultRawVolume(rangeStart, rangeSize);
            data.cachedRangeStart = rangeStart;
            data.cachedMarketPrice = data.currentMarketPrice;
            data.cachedVolumeScale = data.calculator.volumeScale;
            data.cachedDefaultPrice = data.calculator.defaultPrice;
        }
        float[] targets = data.cachedTargets;

        if (hardReset) {
            // Hard reset: write the target distribution directly, skipping convergence.
            // Clone so the manipulation cache does not hold a reference to our cached array.
            // T-199: the reset (applied before the SET) also wipes the depth memory outside the window,
            // so the whole book starts from the new distribution.
            orderBook.resetVirtualVolume();
            orderBook.setRawVolume(rangeStart, targets.clone());
            data.trendLnMA = Double.NaN;   // the trend starts over with the fresh book
            return;
        }

        // Cheap convergence loop: plain float arithmetic toward the cached targets.
        float askFactor = data.trendAskFactor, bidFactor = 1f / askFactor;
        float[] newVolume = new float[rangeSize];
        for(int idx = 0; idx < rangeSize; idx++)
        {
            float targetAmount = targets[idx];
            float currentVal = orderBook.getRawVirtualVolume(rangeStart + idx);
            if(currentVal < 0 && targetAmount > 0 || currentVal > 0 && targetAmount < 0)
            {
                currentVal = 0;
                newVolume[idx] = 0;
            }

            // Trend accumulation: side factor f (bids: target > 0) speeds refill up by f, slows excess removal by f
            float side = targetAmount > 0 ? bidFactor : askFactor;
            float scale;
            if(Math.abs(currentVal) > Math.abs(targetAmount))
            {
                scale = data.decumulationRate / side;
            }else{
                scale = data.accumulationRate * side;
            }
            float deltaAmount = (targetAmount - currentVal) * (float) deltaT * scale;
            if(deltaAmount < 0 && currentVal > 0 && -deltaAmount > currentVal)
            {
                deltaAmount = -currentVal;
            }
            else if(deltaAmount > 0 && currentVal < 0 && deltaAmount > -currentVal)
            {
                deltaAmount = -currentVal;
            }
            newVolume[idx] = currentVal + (deltaAmount * data.speed);
        }
        orderBook.setRawVolume(rangeStart, newVolume);
    }

    @Override
    public void finalize(List<MarketInterface> markets) {

    }

    @Override
    public void onMarketSubscribed(ItemID marketID) {
        RuntimeData data = new RuntimeData();
        data.speed = DEFAULT_SPEED;
        data.accumulationRate = DEFAULT_ACCUMULATION_RATE;
        data.decumulationRate = DEFAULT_DECUMULATION_RATE;
        data.volumeScale = DEFAULT_VOLUME_SCALE;
        marketData.put(marketID, data);

        MarketInterface interf = getMarketInterface(marketID);
        if(interf == null)
            return;
        refreshDepth(data, interf.market, false);
        interf.oderBook.registerDefaultVolumeDistributionCalculator(data.calculator);
        interf.oderBook.resetVirtualVolume();
    }

    @Override
    public void onMarketUnsubscribed(ItemID marketID) {
        RuntimeData data = marketData.remove(marketID);
        if(data == null)
            return;

        MarketInterface interf = getMarketInterface(marketID);
        if(interf == null)
            return;
        interf.oderBook.unregisterDefaultVolumeDistributionCalculator(data.calculator);
        interf.oderBook.setVirtualDepthSideFactors(1f, 1f);
    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    @Override
    public boolean save(CompoundTag tag) {
        ListTag marketDataTag = new ListTag();
        for (Map.Entry<ItemID, RuntimeData> entry : marketData.entrySet()) {
            CompoundTag marketTag = new CompoundTag();
            entry.getKey().save(marketTag);
            marketTag.putFloat("volumeScale", entry.getValue().volumeScale);
            marketTag.putFloat("speed", entry.getValue().speed);
            marketTag.putFloat("accumulationRate", entry.getValue().accumulationRate);
            marketTag.putFloat("decumulationRate", entry.getValue().decumulationRate);
            marketTag.putFloat("trendPeriodMinutes", entry.getValue().trendPeriodMinutes);
            marketTag.putFloat("trendGain", entry.getValue().trendGain);
            marketTag.putFloat("trendMaxFactor", entry.getValue().trendMaxFactor);
            if (!Double.isNaN(entry.getValue().trendLnMA)) marketTag.putDouble("trendLnMA", entry.getValue().trendLnMA);
            marketTag.putInt(DEPTH_MODEL_KEY, DEPTH_MODEL);
            marketDataTag.add(marketTag);
        }
        tag.put("marketData", marketDataTag);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if (tag.contains("marketData")) {
            ListTag marketDataTag = NbtCompat.getList(tag, "marketData", 10);
            for (int i = 0; i < marketDataTag.size(); i++) {
                CompoundTag marketTag = NbtCompat.getCompound(marketDataTag, i);
                ItemID marketID = ItemID.createFromTag(marketTag);
                if (marketID != null) {
                    RuntimeData data = marketData.get(marketID);
                    if (data != null) {
                        if (marketTag.contains("volumeScale")) data.volumeScale = NbtCompat.getFloat(marketTag, "volumeScale");
                        if (marketTag.contains("speed")) data.speed = NbtCompat.getFloat(marketTag, "speed");
                        if (marketTag.contains("accumulationRate")) data.accumulationRate = NbtCompat.getFloat(marketTag, "accumulationRate");
                        if (marketTag.contains("decumulationRate")) data.decumulationRate = NbtCompat.getFloat(marketTag, "decumulationRate");
                        // Trend accumulation (older saves: defaults, the average starts at the current price)
                        if (marketTag.contains("trendPeriodMinutes")) data.trendPeriodMinutes = NbtCompat.getFloat(marketTag, "trendPeriodMinutes");
                        if (marketTag.contains("trendGain")) data.trendGain = NbtCompat.getFloat(marketTag, "trendGain");
                        if (marketTag.contains("trendMaxFactor")) data.trendMaxFactor = NbtCompat.getFloat(marketTag, "trendMaxFactor");
                        data.trendLnMA = marketTag.contains("trendLnMA") ? NbtCompat.getDouble(marketTag, "trendLnMA") : Double.NaN;

                        // T-214: books saved before the price-normalised depth are rebuilt once
                        if (!marketTag.contains(DEPTH_MODEL_KEY) || NbtCompat.getInt(marketTag, DEPTH_MODEL_KEY) < DEPTH_MODEL)
                            data.pendingReset = true;

                        MarketInterface interf = getMarketInterface(marketID);
                        if (interf != null) refreshDepth(data, interf.market, false);
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
        RuntimeData data = marketData.get(marketID);
        if (data == null) return;
        data.volumeScale = settings.volumeScale();
        data.speed = settings.speed();
        data.accumulationRate = settings.accumulationRate();
        data.decumulationRate = settings.decumulationRate();
        data.trendPeriodMinutes = settings.trendPeriodMinutes();
        data.trendGain = settings.trendGain();
        data.trendMaxFactor = settings.trendMaxFactor();

        MarketInterface interf = getMarketInterface(marketID);
        if (interf != null) refreshDepth(data, interf.market, false);   // else the next update() applies it

        // Drop the cached target distribution so the new settings take effect on this
        // market's next update instead of waiting for its round-robin refresh turn.
        data.cachedTargets = null;

        if (settings.resetVolume()) {
            data.pendingReset = true;
        }
    }

    /**
     * Samples of the streamed default distribution over {@code [0.01, 2.5 x price]}. The client draws
     * them linearly interpolated, so features (the peaks next to the price) snap to the sample grid:
     * with 64 samples (step about 1.6% of the price) the line sat up to about 1.6% off, at 256 it stays
     * within about 0.4%. Costs 1 KB per market per stream update.
     */
    private static final int SAMPLE_COUNT = 256;

    @Override
    protected StreamCodec<ByteBuf, RuntimeStreamData> runtimeDataCodec() {
        return RuntimeStreamData.CODEC;
    }

    @Override
    protected RuntimeStreamData provideRuntimeData() {
        if (marketData.isEmpty()) return null;
        List<RuntimeStreamData.MarketDistribution> entries = new ArrayList<>();
        for (Map.Entry<ItemID, RuntimeData> entry : marketData.entrySet()) {
            RuntimeData data = entry.getValue();
            MarketInterface interf = getMarketInterface(entry.getKey());
            if (interf == null) continue;
            double marketPrice = data.currentMarketPrice;
            double maxPrice = Math.max(marketPrice * 2.5, data.calculator.defaultPrice * 2.5);
            if (maxPrice <= 0.01) maxPrice = 10;
            double startPrice = 0.01;
            double priceStep = (maxPrice - startPrice) / (SAMPLE_COUNT - 1);
            float[] volumes = new float[SAMPLE_COUNT];
            // Stream raw per-backend-price-point volumes (same units as orderbook)
            for (int i = 0; i < SAMPLE_COUNT; i++) {
                double price = startPrice + priceStep * i;
                volumes[i] = interf.oderBook.getDefaultRawVolume(price);
            }
            entries.add(new RuntimeStreamData.MarketDistribution(
                    entry.getKey().getShort(), startPrice, priceStep, volumes));
        }
        return new RuntimeStreamData(entries);
    }

    @Override
    public long getRuntimeDataStreamInterval() {
        return 500;
    }
}
