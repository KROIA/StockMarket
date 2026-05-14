package net.kroia.stockmarket.pluginsystem.interaction;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.plugin.interaction.IPluginMarket;
import net.kroia.stockmarket.pluginsystem.plugin.core.cache.MarketCache;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import org.jetbrains.annotations.NotNull;

public class PluginMarket implements IPluginMarket
{
    private final IServerMarket serverMarket;
    private final MarketCache cache;


    public PluginMarket(@NotNull IServerMarket serverMarket,
                        @NotNull MarketCache cache)
    {
        this.serverMarket = serverMarket;
        this.cache = cache;
    }


    @Override
    public @NotNull ItemID getMarketID() {
        return serverMarket.getItemID();
    }

    /*@Override
    public int getStreamPacketSendTickInterval() {
        return 0;
    }

    @Override
    public void setStreamPacketSendTickInterval(int interval) {

    }*/

    @Override
    public double getDefaultRealPrice() {
        return MarketManager.convertToRealAmountStatic(serverMarket.getDefaultPrice());
    }

    @Override
    public double getPrice() {
        return MarketManager.convertToRealAmountStatic(serverMarket.getCurrentMarketPrice());
    }

    /*@Override
    public float getPriceOf(TradingPair pair) {
        return 0;
    }*/

    @Override
    public double convertBackendPriceToRealPrice(long backendPrice) {
        return MarketManager.convertToRealAmountStatic(backendPrice);
    }

    @Override
    public long convertRealPriceToBackendPrice(double realPrice) {
        return MarketManager.convertToRawAmountStatic(realPrice);
    }

    @Override
    public void setTargetPrice(double targetPrice)
    {
        cache.setNextTargetPrice(targetPrice);
    }

    @Override
    public double getPreviousTargetPrice() {
        return cache.getLastTargetPrice();
    }

    @Override
    public void addToTargetPrice(double delta) {
        cache.addToNextTargetPrice(delta);
    }

    @Override
    public long placeOrder(double amount, double price) {
        Order order = new Order(serverMarket.getItemID(), Order.Type.LIMIT,
                MarketManager.convertToRawAmountStatic(amount), convertRealPriceToBackendPrice(price), System.currentTimeMillis());
        cache.addLimitOrder(order);
        return order.getTime();
    }

    @Override
    public void placeOrder(double amount) {
        cache.addMarketOrder(amount);
    }

    @Override
    public double getNetPlayerItemFlow() {
        return MarketManager.convertToRealAmountStatic(serverMarket.getNetPlayerItemFlow());
    }
}
