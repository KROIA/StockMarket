package net.kroia.stockmarket.pluginsystem.plugin.core.cache;

import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.plugin.interaction.IPluginMarket;
import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;
import net.kroia.stockmarket.pluginsystem.interaction.PluginMarket;
import net.kroia.stockmarket.pluginsystem.interaction.PluginOrderBook;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import org.jetbrains.annotations.NotNull;

public class MarketCache {
    private final OrderCache orderCache = new OrderCache();
    private final VirtualOrderBookCache virtualOrderBookCache = new VirtualOrderBookCache();
    private double lastTargetPrice = 0;
    private double nextTargetPrice = 0;
    /** T-213: equilibrium published this tick (NaN = none yet). */
    private double nextEquilibrium = Double.NaN;
    /** T-213: equilibrium of the previous tick (NaN = none). */
    private double lastEquilibrium = Double.NaN;
    /** T-213: true once a plugin wrote the target price this tick. */
    private boolean targetSetThisTick = false;

    private final IServerMarket serverMarket;
    private final IPluginMarket pluginMarket;
    private final IPluginOrderBook pluginOrderBook;




    public MarketCache(@NotNull ServerMarket serverMarket)
    {
        this.serverMarket = serverMarket;
        pluginMarket = new PluginMarket(serverMarket, this);
        pluginOrderBook = new PluginOrderBook(serverMarket, this);
        double currentPrice = MarketManager.convertToRealAmountStatic(serverMarket.getCurrentMarketPrice());
        lastTargetPrice = currentPrice;
        nextTargetPrice = currentPrice;
    }

    public IPluginMarket getPluginMarket()
    {
        return pluginMarket;
    }
    public IPluginOrderBook getPluginOrderBook()
    {
        return pluginOrderBook;
    }

    /** @return whether the underlying market is inside its trading hours (maintenance is ignored). */
    public boolean isTradingOpen()
    {
        return serverMarket.isTradingOpen();
    }

    public void apply()
    {
        // T-213: the equilibrium channel and the "target written" flag roll every tick, also outside trading
        // hours, so a stale value never outlives its publisher (NetFlowPlugin removed -> default price).
        lastEquilibrium = nextEquilibrium;
        nextEquilibrium = Double.NaN;
        targetSetThisTick = false;
        // Outside trading hours: the market would reject the bot orders anyway, so drop them.
        // Pending virtual-orderbook manipulations are kept for the reopen and the target price
        // is not rolled, so the plugins resume from where they stopped.
        if (!serverMarket.isTradingOpen())
        {
            orderCache.clear();
            return;
        }
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

    /** Trend accumulation: see {@link VirtualOrderBookCache#setVirtualDepthSideFactors}. */
    public void setVirtualDepthSideFactors(float bidFactor, float askFactor)
    {
        virtualOrderBookCache.setVirtualDepthSideFactors(bidFactor, askFactor);
    }

    public void setNextTargetPrice(double nextTargetPrice)
    {
        this.nextTargetPrice = nextTargetPrice;
        targetSetThisTick = true;
    }
    public void addToNextTargetPrice(double delta)
    {
        this.nextTargetPrice += delta;
        targetSetThisTick = true;
    }

    /** T-213: @return true if a plugin wrote the target price since the last {@link #apply()} */
    public boolean isTargetSetThisTick()
    {
        return targetSetThisTick;
    }

    /**
     * T-213: publishes this tick's equilibrium price.
     * @param equilibrium the equilibrium in real units
     */
    public void setNextEquilibrium(double equilibrium)
    {
        this.nextEquilibrium = equilibrium;
    }

    /** T-213: @return this tick's equilibrium if published, else last tick's, else NaN */
    public double getEquilibrium()
    {
        return Double.isNaN(nextEquilibrium) ? lastEquilibrium : nextEquilibrium;
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
