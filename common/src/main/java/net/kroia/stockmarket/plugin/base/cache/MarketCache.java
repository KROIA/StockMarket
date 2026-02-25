package net.kroia.stockmarket.plugin.base.cache;

import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.plugin.base.interaction.IPluginMarket;
import net.kroia.stockmarket.plugin.base.interaction.IPluginOrderBook;
import net.kroia.stockmarket.plugin.interaction.PluginMarket;
import net.kroia.stockmarket.plugin.interaction.PluginOrderBook;
import org.jetbrains.annotations.NotNull;

public class MarketCache {
    private final OrderCache orderCache = new OrderCache();
    private final VirtualOrderBookCache virtualOrderBookCache = new VirtualOrderBookCache();
    private float lastTargetPrice = 0;
    private float nextTargetPrice = 0;

    private final IServerMarket serverMarket;
    private final IPluginMarket pluginMarket;
    private final IPluginOrderBook pluginOrderBook;




    public MarketCache(@NotNull IServerMarket serverMarket)
    {
        this.serverMarket = serverMarket;
        pluginMarket = new PluginMarket(serverMarket, this);
        pluginOrderBook = new PluginOrderBook(serverMarket, this);
    }

    public IPluginMarket getPluginMarket()
    {
        return pluginMarket;
    }
    public IPluginOrderBook getPluginOrderBook()
    {
        return pluginOrderBook;
    }

    public void apply()
    {
        orderCache.apply(serverMarket);
        virtualOrderBookCache.apply(serverMarket);

        lastTargetPrice = nextTargetPrice;
    }


    public void addLimitOrder(LimitOrder limitOrder)
    {
        orderCache.addLimitOrder(limitOrder);
    }
    public void addMarketOrder(float amount)
    {
        orderCache.addMarketOrder(amount);
    }


    public void addManipulation(float minPrice, float maxPrice, float volume, VirtualOrderBookCache.ManipulationOperator operator)
    {
        virtualOrderBookCache.addManipulation(minPrice, maxPrice, volume, operator);
    }
    public void addManipulation(int backendStartPrice, float[] volume, VirtualOrderBookCache.ManipulationOperator operator)
    {
        virtualOrderBookCache.addManipulation(backendStartPrice, volume, operator);
    }


    /*public void addVolumeDistributionCalculator(@Nullable IVolumeDistributionCalculator volumeDistributionCalculator)
    {
        this.volumeDistributionCalculators.add(volumeDistributionCalculator);
    }
    public List<IVolumeDistributionCalculator> getVolumeDistributionCalculators()
    {
        return volumeDistributionCalculators;
    }*/

    public void setNextTargetPrice(float nextTargetPrice)
    {
        this.nextTargetPrice = nextTargetPrice;
    }
    public void addToNextTargetPrice(float delta)
    {
        this.nextTargetPrice += delta;
    }
    public float getNextTargetPrice()
    {
        return nextTargetPrice;
    }
    public float getLastTargetPrice()
    {
        return lastTargetPrice;
    }
}
