package net.kroia.stockmarket.pluginsystem.plugins;

import net.kroia.modutilities.NbtCompat;

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
     */
    public record Settings(float pidP, float pidI, float pidD, float pidRate, float maxOrderVolumeFactor,
                           float maxTargetMovePerSecond) {
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
                return new Settings(p, i, d, rate, factor, maxMove);
            }
            @Override
            public void encode(ByteBuf buf, Settings s) {
                buf.writeFloat(s.pidP());
                buf.writeFloat(s.pidI());
                buf.writeFloat(s.pidD());
                buf.writeFloat(s.pidRate());
                buf.writeFloat(s.maxOrderVolumeFactor());
                buf.writeFloat(s.maxTargetMovePerSecond());
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
     * Creates the bot's PID with its output limited to [-1, 1], the range the bot actually uses
     * as order fraction. Without the limit the output filter winds up while the price sits far
     * from the target and the bot keeps pushing past the target for several cycles (T-178).
     */
    private static PID createPid(float p, float i, float d, float rate) {
        PID pid = new PID(p, i, d, rate);
        pid.setOutputLimit(1);
        return pid;
    }

    // Default PID gains — configurable via custom settings (per-market)
    private static final float DEFAULT_PID_P = 0.5f;
    private static final float DEFAULT_PID_I = 0.1f;
    private static final float DEFAULT_PID_D = 0.0f;
    private static final float DEFAULT_PID_RATE = 0.1f;
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
        // #89: chase the plugins' target only through the rate limit (persisted as targetPrice).
        data.targetPrice = limitTargetMove(data.targetPrice, market.market.getPreviousTargetPrice(),
                settings != null ? settings.maxTargetMovePerSecond() : DEFAULT_MAX_TARGET_MOVE_PER_SECOND, STEP_SECONDS);
        double currentPrice = market.market.getPrice();

        double output = data.pid.update(data.targetPrice - currentPrice, STEP_SECONDS);
        // Clamp PID output to [-1, 1] as a fraction of how aggressively to push
        double pidFraction = Math.min(Math.max(-1, output), 1);

        // Size from virtual depth only: counting resting player limit orders here let the bot fill a
        // whole player order in one go at its limit price, before the player's flow could move the price.
        float volumeToTarget = market.oderBook.getRealVirtualVolume(currentPrice, data.targetPrice);
        // Cap the order: an oversized bot order walks the price through the whole virtual book in one
        // tick and can fill a far-away player limit order at its price (trading exploit).
        float factor = settings != null ? settings.maxOrderVolumeFactor() : DEFAULT_MAX_ORDER_VOLUME_FACTOR;
        double absVolumeToTarget = Math.min(botOrderVolumeLimit(market.market.getMaxRealOrderVolume(), factor), Math.abs(volumeToTarget));

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
        return new Settings(DEFAULT_PID_P, DEFAULT_PID_I, DEFAULT_PID_D, DEFAULT_PID_RATE, DEFAULT_MAX_ORDER_VOLUME_FACTOR,
                DEFAULT_MAX_TARGET_MOVE_PER_SECOND);
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
                    }
                }
            }
        }
        return true;
    }
}
