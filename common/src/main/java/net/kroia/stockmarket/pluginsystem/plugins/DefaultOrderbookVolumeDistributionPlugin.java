package net.kroia.stockmarket.pluginsystem.plugins;


import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;
import net.kroia.stockmarket.api.plugin.interaction.IVolumeDistributionCalculator;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.minecraft.nbt.CompoundTag;
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

    static class RuntimeData
    {
        public long lastMillis;
        public float currentMarketPrice = 0;
        public final DistributionCalculator calculator;
        public float speed = 0.05f;
        public float accumulationRate = 0.1f;
        public float decumulationRate = 0.01f;
        public boolean pendingReset = false;

        public RuntimeData()
        {
            lastMillis = System.currentTimeMillis();
            calculator = new DistributionCalculator();
        }
    }
    private final Map<ItemID, RuntimeData> marketData = new HashMap<>();
    private float volumeScale = 1.0f;
    private float speed = 0.05f;
    private float accumulationRate = 0.1f;
    private float decumulationRate = 0.01f;

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

    @Override
    public void update(List<MarketInterface> markets)
    {
        for(MarketInterface market : markets)
        {
            RuntimeData data = marketData.get(market.market.getMarketID());
            data.currentMarketPrice = (float) market.market.getPrice();
            updateForMarket(market, data);
        }
    }
    private void updateForMarket(MarketInterface market, RuntimeData data)
    {
        long currentMillis = System.currentTimeMillis();
        Tuple<@NotNull Long,@NotNull  Long> editableRange = market.oderBook.getEditableBackendPriceRange();
        double deltaT = Math.min((currentMillis - data.lastMillis) / 1000.0, 1.0);
        data.lastMillis = currentMillis;

        IPluginOrderBook orderBook = market.oderBook;
        float[] newVolume = new float[(int)(editableRange.getB() - editableRange.getA()+1)];

        boolean hardReset = data.pendingReset;
        if (hardReset)
            data.pendingReset = false;

        for(long i=editableRange.getA(); i<=editableRange.getB(); i++)
        {
            float targetAmount = orderBook.getDefaultRawVolume(market.market.convertBackendPriceToRealPrice(i));

            if (hardReset) {
                newVolume[(int)(i - editableRange.getA())] = targetAmount;
                continue;
            }

            float currentVal = orderBook.getRawVirtualVolume(i);
            if(currentVal < 0 && targetAmount > 0 || currentVal > 0 && targetAmount < 0)
            {
                currentVal = 0;
                newVolume[(int)(i - editableRange.getA())] = 0;
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
            newVolume[(int)(i - editableRange.getA())] = currentVal + (deltaAmount * data.speed);
        }
        orderBook.setRawVolume(editableRange.getA(), newVolume);
    }

    @Override
    public void finalize(List<MarketInterface> markets) {

    }

    @Override
    public void onMarketSubscribed(ItemID marketID) {
        RuntimeData data = new RuntimeData();
        data.speed = this.speed;
        data.accumulationRate = this.accumulationRate;
        data.decumulationRate = this.decumulationRate;
        data.calculator.volumeScale = this.volumeScale;
        marketData.put(marketID, data);

        MarketInterface interf = getMarketInterface(marketID);
        if(interf == null)
            return;
        data.calculator.defaultPrice = interf.market.getDefaultRealPrice();
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
        tag.putFloat("volumeScale", volumeScale);
        tag.putFloat("speed", speed);
        tag.putFloat("accumulationRate", accumulationRate);
        tag.putFloat("decumulationRate", decumulationRate);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if (tag.contains("volumeScale")) volumeScale = tag.getFloat("volumeScale");
        if (tag.contains("speed")) speed = tag.getFloat("speed");
        if (tag.contains("accumulationRate")) accumulationRate = tag.getFloat("accumulationRate");
        if (tag.contains("decumulationRate")) decumulationRate = tag.getFloat("decumulationRate");
        return true;
    }

    @Override
    protected StreamCodec<ByteBuf, Settings> customSettingsCodec() {
        return Settings.CODEC;
    }

    @Override
    protected Settings provideCustomSettings() {
        return new Settings(volumeScale, speed, accumulationRate, decumulationRate);
    }

    @Override
    protected boolean applyCustomSettings(Settings settings) {
        volumeScale = settings.volumeScale();
        speed = settings.speed();
        accumulationRate = settings.accumulationRate();
        decumulationRate = settings.decumulationRate();
        // Update all existing market runtimes
        for (Map.Entry<ItemID, RuntimeData> entry : marketData.entrySet()) {
            RuntimeData data = entry.getValue();
            data.calculator.volumeScale = volumeScale;
            data.speed = speed;
            data.accumulationRate = accumulationRate;
            data.decumulationRate = decumulationRate;

            if (settings.resetVolume()) {
                data.pendingReset = true;
            }
        }
        return true;
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
