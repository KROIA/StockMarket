package net.kroia.stockmarket.pluginsystem.plugin.core.cache;

import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.plugin.interaction.IPluginMarket;
import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;
import net.kroia.stockmarket.pluginsystem.interaction.PluginMarket;
import net.kroia.stockmarket.pluginsystem.interaction.PluginOrderBook;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import org.jetbrains.annotations.NotNull;

public class MarketCache {
    private final OrderCache orderCache = new OrderCache();
    private final VirtualOrderBookCache virtualOrderBookCache = new VirtualOrderBookCache();
    private double lastTargetPrice = 0;
    private double nextTargetPrice = 0;

    private final IServerMarket serverMarket;
    private final IPluginMarket pluginMarket;
    private final IPluginOrderBook pluginOrderBook;




    public MarketCache(@NotNull ServerMarket serverMarket)
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


    public void addLimitOrder(Order limitOrder)
    {
        orderCache.addLimitOrder(limitOrder);
    }
    public void addMarketOrder(double amount)
    {
        orderCache.addMarketOrder(amount);
    }


    public void addManipulation(double minPrice, double maxPrice, float volume, VirtualOrderBookCache.ManipulationOperator operator)
    {
        virtualOrderBookCache.addManipulation(minPrice, maxPrice, volume, operator);
    }
    public void addManipulation(long backendStartPrice, float[] volume, VirtualOrderBookCache.ManipulationOperator operator)
    {
        virtualOrderBookCache.addManipulation(backendStartPrice, volume, operator);
    }
    public void resetVirtualVolume()
    {
        virtualOrderBookCache.resetVirtualVolume();
    }

    public void setNextTargetPrice(double nextTargetPrice)
    {
        this.nextTargetPrice = nextTargetPrice;
    }
    public void addToNextTargetPrice(double delta)
    {
        this.nextTargetPrice += delta;
    }
    public double getNextTargetPrice()
    {
        return nextTargetPrice;
    }
    public double getLastTargetPrice()
    {
        return lastTargetPrice;
    }
}
