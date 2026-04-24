package net.kroia.stockmarket.pluginsystem.plugins;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.util.PID;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TargetPriceBot extends ServerPlugin {

    static class RuntimeData
    {
        public final PID pid = new PID(0.1f, 0.1f, 0, 0.1f);
        public int tickCounter = 0;
        public double targetPrice = 0;
    }
    private final Map<ItemID, RuntimeData> marketData = new HashMap<>();



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
        float volumeToTarget = market.oderBook.getVolume(currentPrice, data.targetPrice);
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
        RuntimeData data = new RuntimeData();
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
    public boolean save(CompoundTag tag) {
        return false;
    }

    @Override
    public boolean load(CompoundTag tag) {
        return false;
    }
}
