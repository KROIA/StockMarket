package net.kroia.stockmarket.plugin.plugins;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.IVolumeDistributionCalculator;
import net.kroia.stockmarket.plugin.base.MarketBehaviorPlugin;
import net.kroia.stockmarket.plugin.base.interaction.IPluginOrderBook;
import net.kroia.stockmarket.plugin.interaction.MarketInterfaces;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultOrderbookVolumeDistributionPlugin extends MarketBehaviorPlugin {

    static class DistributionCalculator implements IVolumeDistributionCalculator
    {

        public DistributionCalculator()
        {

        }

        @Override
        public float getVolume(float marketPrice, float volumePickPrice) {
            float delta = Math.abs(marketPrice - volumePickPrice);
            if(delta > 10)
                delta = 10;
            return delta*100;
        }
    }

    static class RuntimeData
    {
        public long lastMillis;
        public float currentMarketPrice = 0;
        public final DistributionCalculator calculator;

        public RuntimeData()
        {
            calculator = new DistributionCalculator();
        }
    }
    private final Map<TradingPair, RuntimeData> marketData = new HashMap<>();

    public DefaultOrderbookVolumeDistributionPlugin()
    {
        super("DefaultOrderbookVolumeDistributionPlugin");
    }

    @Override
    public void init() {

    }

    @Override
    public void deInit() {

    }

    @Override
    public void update(List<MarketInterfaces> markets)
    {
        for(MarketInterfaces market : markets)
        {
            RuntimeData data = marketData.get(market.market.getTradingPair());
            updateForMarket(market, data);
        }
    }
    private void updateForMarket(MarketInterfaces market, RuntimeData data)
    {
        long currentMillis = System.currentTimeMillis();
        Tuple<@NotNull Integer,@NotNull  Integer> editableRange = market.oderBook.getEditableBackendPriceRange();
        double deltaT = Math.min((currentMillis - data.lastMillis) / 1000.0, 1.0);
        data.lastMillis = currentMillis;

        IPluginOrderBook orderBook = market.oderBook;
        float[] newVolume = new float[editableRange.getB() - editableRange.getA()+1];
        for(int i=editableRange.getA(); i<=editableRange.getB(); i++)
        {
            float targetAmount = orderBook.getDefaultVolume(market.market.convertBackendPriceToRealPrice(i));
            float currentVal = orderBook.getVolume(i);
            if(currentVal < 0 && targetAmount > 0 || currentVal > 0 && targetAmount < 0)
            {
                currentVal = 0;
                newVolume[i - editableRange.getA()] = 0;
            }

            float scale = 0.01f;

            if(Math.abs(currentVal) < Math.abs(targetAmount)*0.2f)
            {
                scale = 0.1f;
            }else if(Math.abs(currentVal) > Math.abs(targetAmount))
            {
                scale = 0.01f;
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
            newVolume[i - editableRange.getA()] = currentVal + deltaAmount;
        }
        orderBook.setVolume(editableRange.getA(), newVolume);
    }

    @Override
    public void finalize(List<MarketInterfaces> markets) {

    }

    @Override
    public void onMarketSubscribed(TradingPair tradingPair) {
        RuntimeData data = new RuntimeData();
        marketData.put(tradingPair, data);

        MarketInterfaces interf = getMarketInterface(tradingPair);
        if(interf == null)
            return;
        interf.oderBook.registerDefaultVolumeDistributionCalculator(data.calculator);
    }

    @Override
    public void onMarketUnsubscribed(TradingPair tradingPair) {
        RuntimeData data = marketData.remove(tradingPair);

        MarketInterfaces interf = getMarketInterface(tradingPair);
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
    public boolean saveData(CompoundTag tag) {
        return false;
    }

    @Override
    public boolean loadData(CompoundTag tag) {
        return false;
    }
}
