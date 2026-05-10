package net.kroia.stockmarket.pluginsystem.plugins;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.util.PID;
import net.minecraft.nbt.CompoundTag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TargetPriceBot extends ServerPlugin {

    static class RuntimeData
    {
        public PID pid;
        public int tickCounter = 0;
        public double targetPrice = 0;

        RuntimeData(float p, float i, float d, float rate) {
            this.pid = new PID(p, i, d, rate);
        }
    }
    private final Map<ItemID, RuntimeData> marketData = new HashMap<>();

    // Default PID gains — configurable via custom settings
    private float pidP = 0.5f;
    private float pidI = 0.1f;
    private float pidD = 0.0f;
    private float pidRate = 0.1f;



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
        if(data.tickCounter < 5) //update once per second
            return;
        data.tickCounter = 0;
        data.targetPrice = market.market.getPreviousTargetPrice();
        double currentPrice = market.market.getPrice();

        double output = data.pid.update(data.targetPrice - currentPrice);
        double normalized = (Math.min(Math.max(-10, output*5),10));
        float volumeToTarget = market.oderBook.getRealVolume(currentPrice, data.targetPrice);
        if(normalized < 0 && volumeToTarget > 0)
            normalized = Math.max(-volumeToTarget, normalized);
        else if(normalized > 0 && volumeToTarget < 0)
            normalized = Math.min(-volumeToTarget, normalized);
        else if(normalized != 0)
            normalized = 0; //we are at target price
        double marketOrderAmount = ((double) Math.round(normalized * 100)) /100;
        if(marketOrderAmount != 0)
        {
            market.market.placeOrder(marketOrderAmount);
            info("Placing order: " + marketOrderAmount + " (PID-OUT: " + output + ", normalized-order-size: " + normalized +" , volumeToTarget: " + volumeToTarget + ")");
        }
    }

    @Override
    public void finalize(List<MarketInterface> markets) {

    }

    @Override
    public void onMarketSubscribed(ItemID marketID) {
        RuntimeData data = new RuntimeData(pidP, pidI, pidD, pidRate);
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

    /**
     * Encodes the current target prices for all subscribed markets into a binary payload.
     * Format: [count:int] then for each market: [itemID:short, targetPrice:double].
     *
     * @return encoded runtime data bytes, or null if no markets are tracked
     */
    @Override
    public byte[] provideRuntimeData() {
        if (marketData.isEmpty()) return null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(marketData.size());
            for (Map.Entry<ItemID, RuntimeData> entry : marketData.entrySet()) {
                dos.writeShort(entry.getKey().getShort());
                dos.writeDouble(entry.getValue().targetPrice);
            }
            dos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            error("Failed to encode runtime data", e);
            return null;
        }
    }

    /**
     * Returns the update interval for the runtime data stream.
     * Uses 200ms for fast target price visualization updates.
     *
     * @return stream update interval in ms
     */
    @Override
    public long getRuntimeDataStreamInterval() {
        return 200;
    }

    /**
     * Encodes the current PID gain settings into a binary payload.
     * Format: [pidP:float, pidI:float, pidD:float, pidRate:float].
     *
     * @return encoded custom settings bytes, or null on error
     */
    @Override
    public byte[] provideCustomSettings() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeFloat(pidP);
            dos.writeFloat(pidI);
            dos.writeFloat(pidD);
            dos.writeFloat(pidRate);
            dos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            error("Failed to encode custom settings", e);
            return null;
        }
    }

    /**
     * Decodes PID gain settings from the client and updates all existing market PIDs.
     * Format: [pidP:float, pidI:float, pidD:float, pidRate:float].
     *
     * @param payload the encoded custom settings bytes from the client
     * @return true if settings were applied successfully
     */
    @Override
    public boolean applyCustomSettings(byte[] payload) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(payload);
            DataInputStream dis = new DataInputStream(bais);
            pidP = dis.readFloat();
            pidI = dis.readFloat();
            pidD = dis.readFloat();
            pidRate = dis.readFloat();

            // Update all existing market PIDs with the new gains
            for (RuntimeData data : marketData.values()) {
                data.pid = new PID(pidP, pidI, pidD, pidRate);
            }
            return true;
        } catch (Exception e) {
            error("Failed to decode custom settings", e);
            return false;
        }
    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putFloat("pidP", pidP);
        tag.putFloat("pidI", pidI);
        tag.putFloat("pidD", pidD);
        tag.putFloat("pidRate", pidRate);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if (tag.contains("pidP")) pidP = tag.getFloat("pidP");
        if (tag.contains("pidI")) pidI = tag.getFloat("pidI");
        if (tag.contains("pidD")) pidD = tag.getFloat("pidD");
        if (tag.contains("pidRate")) pidRate = tag.getFloat("pidRate");
        return true;
    }
}
