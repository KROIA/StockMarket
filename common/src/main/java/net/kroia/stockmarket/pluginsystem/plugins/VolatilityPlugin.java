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

    /**
     * Custom settings record for the volatility scale parameter.
     */
    public record Settings(float volatilityScale) {
        public static final StreamCodec<ByteBuf, Settings> CODEC = new StreamCodec<>() {
            @Override
            public Settings decode(ByteBuf buf) { return new Settings(buf.readFloat()); }
            @Override
            public void encode(ByteBuf buf, Settings s) { buf.writeFloat(s.volatilityScale()); }
        };
    }
    /**
     * Per-market data holding an independent price generator and timer,
     * so each market gets its own random walk.
     */
    static class MarketData {
        final NormalizedRandomPriceGenerator priceGenerator;
        final TimerMillis timer;

        MarketData() {
            priceGenerator = new NormalizedRandomPriceGenerator(5);
            timer = new TimerMillis(false);
            timer.start(random.nextInt(10000));
        }
    }

    private static final Random random = new Random();
    private final Map<ItemID, MarketData> marketData = new HashMap<>();
    private float volatilityScale = 1.0f;

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

            // Scale random walk proportionally to default price for equal percentage volatility
            double defaultPrice = market.market.getDefaultRealPrice();
            double deviation = data.priceGenerator.getCurrentValue() * volatilityScale;
            market.market.setTargetPrice(Math.max(0, defaultPrice * (1.0 + deviation)));
        }
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
        tag.putFloat("volatilityScale", volatilityScale);

        // Save per-market price generator state so random walks persist across restarts
        ListTag marketsTag = new ListTag();
        for (Map.Entry<ItemID, MarketData> entry : marketData.entrySet()) {
            CompoundTag marketTag = new CompoundTag();
            entry.getKey().save(marketTag);
            CompoundTag generatorTag = new CompoundTag();
            entry.getValue().priceGenerator.save(generatorTag);
            marketTag.put("priceGenerator", generatorTag);
            marketsTag.add(marketTag);
        }
        tag.put("marketData", marketsTag);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if (tag.contains("volatilityScale")) volatilityScale = tag.getFloat("volatilityScale");

        // Restore per-market price generator state (marketData map is already populated
        // because subscribeToMarket() is called before load() in ServerPluginManager)
        if (tag.contains("marketData")) {
            ListTag marketsTag = tag.getList("marketData", 10);
            for (int i = 0; i < marketsTag.size(); i++) {
                CompoundTag marketTag = marketsTag.getCompound(i);
                ItemID marketID = ItemID.createFromTag(marketTag);
                if (marketID != null && marketID.isValid()) {
                    MarketData data = marketData.get(marketID);
                    if (data != null && marketTag.contains("priceGenerator")) {
                        data.priceGenerator.load(marketTag.getCompound("priceGenerator"));
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
    protected Settings provideCustomSettings() {
        return new Settings(volatilityScale);
    }

    @Override
    protected boolean applyCustomSettings(Settings settings) {
        volatilityScale = settings.volatilityScale();
        return true;
    }
}
