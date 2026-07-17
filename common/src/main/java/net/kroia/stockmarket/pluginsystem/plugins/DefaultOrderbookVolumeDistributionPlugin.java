package net.kroia.stockmarket.pluginsystem.plugins;


import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;
import net.kroia.stockmarket.api.plugin.interaction.IVolumeDistributionCalculator;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultOrderbookVolumeDistributionPlugin extends ServerPlugin<DefaultOrderbookVolumeDistributionPlugin.Settings, DefaultOrderbookVolumeDistributionPlugin.RuntimeStreamData> {

    /**
     * Custom settings record for volume scale and convergence speed parameters.
     */
    public record Settings(float volumeScale, float speed, float accumulationRate, float decumulationRate, boolean resetVolume) {
        public Settings(float volumeScale, float speed, float accumulationRate, float decumulationRate) {
            this(volumeScale, speed, accumulationRate, decumulationRate, false);
        }
        public static final StreamCodec<ByteBuf, Settings> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Settings::volumeScale,
                ByteBufCodecs.FLOAT, Settings::speed,
                ByteBufCodecs.FLOAT, Settings::accumulationRate,
                ByteBufCodecs.FLOAT, Settings::decumulationRate,
                ByteBufCodecs.BOOL, Settings::resetVolume,
                Settings::new
        );
    }

    /**
     * Runtime data containing sampled volume distribution points per market,
     * computed on the server using the actual DistributionCalculator formula.
     */
    public record RuntimeStreamData(List<MarketDistribution> entries) {
        public record MarketDistribution(short itemId, double startPrice, double priceStep, float[] volumes) {}

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

    static class DistributionCalculator implements IVolumeDistributionCalculator
    {
        private float volumeScale = 1000;
        private double defaultPrice = 0;

        public DistributionCalculator()
        {

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

            return (float) ((coreShape + valueCluster + background + demandBoost) * volumeScale);
        }
    }

    private static final float DEFAULT_VOLUME_SCALE = 1.0f;
    private static final float DEFAULT_SPEED = 0.05f;
    private static final float DEFAULT_ACCUMULATION_RATE = 0.1f;
    private static final float DEFAULT_DECUMULATION_RATE = 0.01f;

    static class RuntimeData
    {
        public long lastMillis;
        public float currentMarketPrice = 0;
        public final DistributionCalculator calculator;
        public float volumeScale = DEFAULT_VOLUME_SCALE;
        public float speed = DEFAULT_SPEED;
        public float accumulationRate = DEFAULT_ACCUMULATION_RATE;
        public float decumulationRate = DEFAULT_DECUMULATION_RATE;
        public boolean pendingReset = false;

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
            lastMillis = System.currentTimeMillis();
            calculator = new DistributionCalculator();
        }
    }
    private final Map<ItemID, RuntimeData> marketData = new HashMap<>();

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
    private static final int MAX_TARGET_REFRESHES_PER_UPDATE = 1;

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
            data.currentMarketPrice = (float) market.market.getPrice();
            boolean mayRefreshTargets =
                    ((i - rotationStart + marketCount) % marketCount) < MAX_TARGET_REFRESHES_PER_UPDATE;
            updateForMarket(market, data, mayRefreshTargets);
        }
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
        long currentMillis = System.currentTimeMillis();
        Tuple<@NotNull Long,@NotNull  Long> editableRange = market.oderBook.getEditableBackendPriceRange();
        double deltaT = Math.min((currentMillis - data.lastMillis) / 1000.0, 1.0);
        data.lastMillis = currentMillis;

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
            orderBook.setRawVolume(rangeStart, targets.clone());
            return;
        }

        // Cheap convergence loop: plain float arithmetic toward the cached targets.
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

            float scale;
            if(Math.abs(currentVal) > Math.abs(targetAmount))
            {
                scale = data.decumulationRate;
            }else{
                scale = data.accumulationRate;
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
        data.calculator.defaultPrice = interf.market.getDefaultRealPrice();
        data.calculator.volumeScale = DEFAULT_VOLUME_SCALE * interf.market.getNaturalAbundance();
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
            marketDataTag.add(marketTag);
        }
        tag.put("marketData", marketDataTag);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if (tag.contains("marketData")) {
            ListTag marketDataTag = tag.getList("marketData", 10);
            for (int i = 0; i < marketDataTag.size(); i++) {
                CompoundTag marketTag = marketDataTag.getCompound(i);
                ItemID marketID = ItemID.createFromTag(marketTag);
                if (marketID != null) {
                    RuntimeData data = marketData.get(marketID);
                    if (data != null) {
                        if (marketTag.contains("volumeScale")) data.volumeScale = marketTag.getFloat("volumeScale");
                        if (marketTag.contains("speed")) data.speed = marketTag.getFloat("speed");
                        if (marketTag.contains("accumulationRate")) data.accumulationRate = marketTag.getFloat("accumulationRate");
                        if (marketTag.contains("decumulationRate")) data.decumulationRate = marketTag.getFloat("decumulationRate");

                        MarketInterface interf = getMarketInterface(marketID);
                        float abundance = (interf != null) ? interf.market.getNaturalAbundance() : 1.0f;
                        data.calculator.volumeScale = data.volumeScale * abundance;
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
        return new Settings(DEFAULT_VOLUME_SCALE, DEFAULT_SPEED, DEFAULT_ACCUMULATION_RATE, DEFAULT_DECUMULATION_RATE);
    }

    @Override
    protected void onCustomSettingsApplied(ItemID marketID, Settings settings) {
        RuntimeData data = marketData.get(marketID);
        if (data == null) return;
        data.volumeScale = settings.volumeScale();
        data.speed = settings.speed();
        data.accumulationRate = settings.accumulationRate();
        data.decumulationRate = settings.decumulationRate();

        MarketInterface interf = getMarketInterface(marketID);
        float abundance = (interf != null) ? interf.market.getNaturalAbundance() : 1.0f;
        data.calculator.volumeScale = settings.volumeScale() * abundance;

        // Drop the cached target distribution so the new settings take effect on this
        // market's next update instead of waiting for its round-robin refresh turn.
        data.cachedTargets = null;

        if (settings.resetVolume()) {
            data.pendingReset = true;
        }
    }

    private static final int SAMPLE_COUNT = 64;

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
