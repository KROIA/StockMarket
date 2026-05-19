package net.kroia.stockmarket.pluginsystem.plugins;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.util.PID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TargetPriceBot extends ServerPlugin<TargetPriceBot.Settings, TargetPriceBot.RuntimeStreamData> {

    /**
     * Custom settings record for the TargetPriceBot PID controller gains.
     */
    public record Settings(float pidP, float pidI, float pidD, float pidRate) {
        public static final StreamCodec<ByteBuf, Settings> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Settings::pidP,
                ByteBufCodecs.FLOAT, Settings::pidI,
                ByteBufCodecs.FLOAT, Settings::pidD,
                ByteBufCodecs.FLOAT, Settings::pidRate,
                Settings::new
        );
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
        float pidP, pidI, pidD, pidRate;

        RuntimeData(float p, float i, float d, float rate) {
            this.pidP = p;
            this.pidI = i;
            this.pidD = d;
            this.pidRate = rate;
            this.pid = new PID(p, i, d, rate);
        }
    }
    private final Map<ItemID, RuntimeData> marketData = new HashMap<>();

    // Default PID gains — configurable via custom settings (per-market)
    private static final float DEFAULT_PID_P = 0.5f;
    private static final float DEFAULT_PID_I = 0.1f;
    private static final float DEFAULT_PID_D = 0.0f;
    private static final float DEFAULT_PID_RATE = 0.1f;



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
            updateForMarket(market, data);
        }
    }

    private void updateForMarket(MarketInterface market, RuntimeData data)
    {
        data.tickCounter++;
        if(data.tickCounter < 5)
            return;
        data.tickCounter = 0;
        data.targetPrice = market.market.getPreviousTargetPrice();
        double currentPrice = market.market.getPrice();

        double output = data.pid.update(data.targetPrice - currentPrice);
        // Clamp PID output to [-1, 1] as a fraction of how aggressively to push
        double pidFraction = Math.min(Math.max(-1, output), 1);

        float volumeToTarget = market.oderBook.getRealVolume(currentPrice, data.targetPrice);
        double absVolumeToTarget = Math.abs(volumeToTarget);

        double normalized;
        if (absVolumeToTarget < 0.01) {
            normalized = 0;
        } else {
            // Scale order size proportional to orderbook depth and PID urgency
            normalized = pidFraction * absVolumeToTarget;
            // Constrain: don't overshoot by consuming more than the volume to target
            if (normalized < 0 && volumeToTarget > 0)
                normalized = Math.max(-volumeToTarget, normalized);
            else if (normalized > 0 && volumeToTarget < 0)
                normalized = Math.min(-volumeToTarget, normalized);
        }

        double marketOrderAmount = ((double) Math.round(normalized * 100)) / 100;
        if(marketOrderAmount != 0)
        {
            market.market.placeOrder(marketOrderAmount);
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
        return new Settings(DEFAULT_PID_P, DEFAULT_PID_I, DEFAULT_PID_D, DEFAULT_PID_RATE);
    }

    @Override
    protected void onCustomSettingsApplied(ItemID marketID, Settings settings) {
        RuntimeData data = marketData.get(marketID);
        if (data != null) {
            data.pidP = settings.pidP();
            data.pidI = settings.pidI();
            data.pidD = settings.pidD();
            data.pidRate = settings.pidRate();
            data.pid = new PID(data.pidP, data.pidI, data.pidD, data.pidRate);
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
            ListTag marketsTag = tag.getList("marketData", 10);
            for (int i = 0; i < marketsTag.size(); i++) {
                CompoundTag marketTag = marketsTag.getCompound(i);
                ItemID marketID = ItemID.createFromTag(marketTag);
                if (marketID != null && marketID.isValid()) {
                    RuntimeData data = marketData.get(marketID);
                    if (data != null) {
                        if (marketTag.contains("pidP")) {
                            data.pidP = marketTag.getFloat("pidP");
                            data.pidI = marketTag.getFloat("pidI");
                            data.pidD = marketTag.getFloat("pidD");
                            data.pidRate = marketTag.getFloat("pidRate");
                            data.pid = new PID(data.pidP, data.pidI, data.pidD, data.pidRate);
                        }
                        if (marketTag.contains("targetPrice")) data.targetPrice = marketTag.getDouble("targetPrice");
                        if (marketTag.contains("tickCounter")) data.tickCounter = marketTag.getInt("tickCounter");
                        if (marketTag.contains("pid")) data.pid.load(marketTag.getCompound("pid"));
                    }
                }
            }
        }
        return true;
    }
}
