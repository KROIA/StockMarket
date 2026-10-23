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
    // capacity, minMult, maxMult of this tick's flow price curve; null = flow impact off
    private double[] flowCurveShape = null;

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

    public void apply()
    {
        orderCache.apply(serverMarket);
        virtualOrderBookCache.apply(serverMarket);
        applyFlowCurve();

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

    public void setFlowCurveShape(double capacity, double minMult, double maxMult)
    {
        flowCurveShape = new double[]{capacity, minMult, maxMult};
    }

    /**
     * The curve's anchor is the price the final target implies at zero flow, so the curve
     * agrees with whatever every plugin together asked for this tick.
     */
    private void applyFlowCurve()
    {
        double[] shape = flowCurveShape;
        flowCurveShape = null;
        net.kroia.stockmarket.stockmarket.market.core.FlowPriceCurve curve = null;
        if (shape != null && nextTargetPrice > 0 && shape[0] > 0 && shape[1] > 0 && shape[1] < 1 && shape[2] > 1)
        {
            double flowReal = MarketManager.convertToRealAmountStatic(serverMarket.getNetPlayerItemFlow());
            var unanchored = new net.kroia.stockmarket.stockmarket.market.core.FlowPriceCurve(1.0, shape[0], shape[1], shape[2]);
            double anchor = nextTargetPrice / unanchored.multiplier(flowReal);
            curve = new net.kroia.stockmarket.stockmarket.market.core.FlowPriceCurve(anchor, shape[0], shape[1], shape[2]);
        }
        serverMarket.setFlowPriceCurve(curve);
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
