package net.kroia.stockmarket.plugin.plugins;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.MarketBehaviorPlugin;
import net.kroia.stockmarket.plugin.interaction.MarketInterfaces;
import net.kroia.stockmarket.util.PID;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TargetPriceBot extends MarketBehaviorPlugin {

    static class RuntimeData
    {
        public final PID pid = new PID(0.1f, 0.1f, 0, 0.1f);
        public int tickCounter = 0;
        public float targetPrice = 0;
    }
    private final Map<TradingPair, RuntimeData> marketData = new HashMap<>();



    public TargetPriceBot() {
        super("TargetPriceBot");
    }

    @Override
    public void init() {

    }

    @Override
    public void deInit() {

    }

    @Override
    public void update(List<MarketInterfaces> markets) {

        for(MarketInterfaces market : markets)
        {
            RuntimeData data = marketData.get(market.market.getTradingPair());
            updateForMarket(market, data);
        }
    }

    private void updateForMarket(MarketInterfaces market, RuntimeData data)
    {
        data.tickCounter++;
        if(data.tickCounter < 5) //update once per second
            return;
        data.tickCounter = 0;
        data.targetPrice = market.market.getPreviousTargetPrice();
        float currentPrice = market.market.getPrice();

        float output = data.pid.update(data.targetPrice - currentPrice);
        float normalized = (Math.min(Math.max(-10, output*5),10));
        float volumeToTarget = market.oderBook.getVolume(currentPrice, data.targetPrice);
        if(normalized < 0 && volumeToTarget > 0)
            normalized = Math.max(-volumeToTarget, normalized);
        else if(normalized > 0 && volumeToTarget < 0)
            normalized = Math.min(-volumeToTarget, normalized);
        else if(normalized != 0)
            normalized = 0; //we are at target price
        long marketOrderAmount = Math.round(normalized);
        if(marketOrderAmount != 0)
        {
            market.market.placeOrder(marketOrderAmount);
            info("Placing order: " + marketOrderAmount + " (PID-OUT: " + output + ", normalized-order-size: " + normalized +" , volumeToTarget: " + volumeToTarget + ")");
        }
    }

    @Override
    public void finalize(List<MarketInterfaces> markets) {

    }

    @Override
    public void onMarketSubscribed(TradingPair tradingPair) {
        RuntimeData data = new RuntimeData();
        marketData.put(tradingPair, data);
    }

    @Override
    public void onMarketUnsubscribed(TradingPair tradingPair) {
        marketData.remove(tradingPair);
    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    @Override
    public boolean saveData(CompoundTag tag) {
        return false;
    }

    @Override
    public boolean loadData(CompoundTag tag) {
        return false;
    }
}
