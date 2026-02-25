package net.kroia.stockmarket.plugin.interaction;

import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.plugin.base.cache.MarketCache;
import net.kroia.stockmarket.plugin.base.interaction.IPluginMarket;
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
    public @NotNull TradingPair getTradingPair() {
        return serverMarket.getTradingPair();
    }

    /*@Override
    public int getStreamPacketSendTickInterval() {
        return 0;
    }

    @Override
    public void setStreamPacketSendTickInterval(int interval) {

    }*/

    @Override
    public float getDefaultPrice() {
        return serverMarket.getDefaultRealPrice();
    }

    @Override
    public float getPrice() {
        return serverMarket.getCurrentRealPrice();
    }

    /*@Override
    public float getPriceOf(TradingPair pair) {
        return 0;
    }*/

    @Override
    public float convertBackendPriceToRealPrice(int backendPrice) {
        return serverMarket.mapToRealPrice(backendPrice);
    }

    @Override
    public int convertRealPriceToBackendPrice(float realPrice) {
        return serverMarket.mapToRawPrice(realPrice);
    }

    @Override
    public void setTargetPrice(float targetPrice)
    {
        cache.setNextTargetPrice(targetPrice);
    }

    @Override
    public float getPreviousTargetPrice() {
        return cache.getLastTargetPrice();
    }

    @Override
    public void addToTargetPrice(float delta) {
        cache.addToNextTargetPrice(delta);
    }

    @Override
    public long placeOrder(float amount, float price) {
        LimitOrder order = serverMarket.createBotLimitOrder(amount, price);
        cache.addLimitOrder(order);
        return order.getOrderID();
    }

    @Override
    public void placeOrder(float amount) {
        cache.addMarketOrder(amount);
    }
}
