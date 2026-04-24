package net.kroia.stockmarket.pluginsystem.plugins;


import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;
import net.kroia.stockmarket.api.plugin.interaction.IVolumeDistributionCalculator;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultOrderbookVolumeDistributionPlugin extends ServerPlugin {

    static class DistributionCalculator implements IVolumeDistributionCalculator
    {

        public DistributionCalculator()
        {

        }

        @Override
        public float getVolume(double marketPrice, double volumePickPrice) {
            float delta = (float)Math.abs(marketPrice - volumePickPrice);
            // Dummy implementation
            if(delta > 1)
                delta = 1;
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

    @Override
    public void update(List<MarketInterface> markets)
    {
        for(MarketInterface market : markets)
        {
            RuntimeData data = marketData.get(market.market.getMarketID());
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
        for(long i=editableRange.getA(); i<=editableRange.getB(); i++)
        {
            float targetAmount = orderBook.getDefaultVolume(market.market.convertBackendPriceToRealPrice(i));
            float currentVal = orderBook.getVirtualVolume(i);
            if(currentVal < 0 && targetAmount > 0 || currentVal > 0 && targetAmount < 0)
            {
                currentVal = 0;
                newVolume[(int)(i - editableRange.getA())] = 0;
            }

            float scale = 0.1f;

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
            newVolume[(int)(i - editableRange.getA())] = currentVal + deltaAmount;
        }
        orderBook.setVolume(editableRange.getA(), newVolume);
    }

    @Override
    public void finalize(List<MarketInterface> markets) {

    }

    @Override
    public void onMarketSubscribed(ItemID marketID) {
        RuntimeData data = new RuntimeData();
        marketData.put(marketID, data);

        MarketInterface interf = getMarketInterface(marketID);
        if(interf == null)
            return;
        interf.oderBook.registerDefaultVolumeDistributionCalculator(data.calculator);
    }

    @Override
    public void onMarketUnsubscribed(ItemID marketID) {
        RuntimeData data = marketData.remove(marketID);

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
        return false;
    }

    @Override
    public boolean load(CompoundTag tag) {
        return false;
    }
}
