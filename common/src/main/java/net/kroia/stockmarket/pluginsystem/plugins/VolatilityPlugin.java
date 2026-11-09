package net.kroia.stockmarket.pluginsystem.plugins;

import net.kroia.modutilities.NbtCompat;

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

/**
 * Random-walk volatility on top of the flow equilibrium.
 * <p>
 * T-213: the net-flow price model (flow band, capacity, θ cap, saturation, equilibrium) moved to the
 * {@link NetFlowPlugin}. This plugin only reads the equilibrium the NetFlowPlugin published this tick
 * ({@link net.kroia.stockmarket.api.plugin.interaction.IPluginMarket#getEquilibriumPrice}, the default price if
 * there is none) and sets the target price to {@code equilibrium x (1 + noise x volatilityScale)}.
 */
public class VolatilityPlugin extends ServerPlugin<VolatilityPlugin.Settings, Void> {

    /** Default random-walk amplitude relative to the equilibrium price (30%). */
    public static final float DEFAULT_VOLATILITY_SCALE = 0.3f;

    /**
     * Custom per-market settings of the random walk.
     *
     * @param volatilityScale amplitude of the random price walk relative to the equilibrium price
     *                        (0.3 = price wanders roughly +-30%)
     */
    public record Settings(float volatilityScale) {
        /**
         * Wire layout (T-213): one float, volatilityScale. Older payloads (1 float, or 5/6/8 fields from before
         * T-213 that also carried the flow settings, now in {@link NetFlowPlugin}) start with the same float;
         * their trailing bytes are skipped (every decode path wraps exactly one payload per buffer).
         */
        public static final StreamCodec<ByteBuf, Settings> CODEC = new StreamCodec<>() {
            @Override
            public Settings decode(ByteBuf buf) {
                float volatilityScale = buf.readFloat();
                buf.skipBytes(buf.readableBytes());
                return new Settings(volatilityScale);
            }
            @Override
            public void encode(ByteBuf buf, Settings s) {
                buf.writeFloat(s.volatilityScale());
            }
        };

        /** Creates a Settings instance with all default values. */
        public static Settings createDefault() {
            return new Settings(DEFAULT_VOLATILITY_SCALE);
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

            // The random-walk target price is only meaningful for markets whose
            // virtual orderbook is enabled — the downstream consumers (e.g.
            // TargetPriceBot) also skip disabled markets, so producing a target
            // here would be dead work.
            if (!market.market.isVirtualOrderbookEnabled()) continue;

            // Each market advances its own random walk timer independently
            if (data.timer.check()) {
                data.timer.start(100 + random.nextLong(100 * 10L + 1));
                data.priceGenerator.getNextValue();
            }

            // T-213: the random walk oscillates around the NetFlowPlugin's equilibrium (default price without one).
            double equilibrium = market.market.getEquilibriumPrice();

            // Scale random walk proportionally to the equilibrium price for equal percentage volatility
            double deviation = data.priceGenerator.getCurrentValue() * data.settings.volatilityScale();
            market.market.setTargetPrice(Math.max(0, equilibrium * (1.0 + deviation)));
        }
    }

    /**
     * @param marketID the market
     * @return the settings the plugin currently runs this market with (after load), null for unknown markets
     */
    public Settings getMarketSettings(ItemID marketID) {
        MarketData data = marketData.get(marketID);
        return data != null ? data.settings : null;
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
            marketTag.putFloat("volatilityScale", entry.getValue().settings.volatilityScale());
            marketsTag.add(marketTag);
        }
        tag.put("marketData", marketsTag);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        // Restore per-market price generator state (marketData map is already populated
        // because subscribeToMarket() is called before load() in ServerPluginManager).
        // T-213: pre-T-213 saves also carry the flow keys (flowSensitivity, satFlow, ...); the
        // NetFlowPlugin migration reads them, this plugin ignores them.
        if (tag.contains("marketData")) {
            ListTag marketsTag = NbtCompat.getList(tag, "marketData", 10);
            for (int i = 0; i < marketsTag.size(); i++) {
                CompoundTag marketTag = NbtCompat.getCompound(marketsTag, i);
                ItemID marketID = ItemID.createFromTag(marketTag);
                if (marketID != null && marketID.isValid()) {
                    MarketData data = marketData.get(marketID);
                    if (data != null) {
                        if (marketTag.contains("priceGenerator")) {
                            data.priceGenerator.load(NbtCompat.getCompound(marketTag, "priceGenerator"));
                        }
                        if (marketTag.contains("volatilityScale"))
                            data.settings = new Settings(NbtCompat.getFloat(marketTag, "volatilityScale"));
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
