package net.kroia.stockmarket.plugin.interaction;

import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.server.OrderBook;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.plugin.base.IVolumeDistributionCalculator;
import net.kroia.stockmarket.plugin.base.cache.MarketCache;
import net.kroia.stockmarket.plugin.base.cache.VirtualOrderBookCache;
import net.kroia.stockmarket.plugin.base.interaction.IPluginOrderBook;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PluginOrderBook implements IPluginOrderBook
{
    private final IServerMarket serverMarket;
    private final OrderBook orderBook;
    private final MarketCache cache;

    private final List<IVolumeDistributionCalculator> volumeDistributionCalculators = new ArrayList<>();


    public PluginOrderBook(@NotNull IServerMarket serverMarket,
                        @NotNull MarketCache cache)
    {
        this.serverMarket = serverMarket;
        this.orderBook = serverMarket.getOrderBook();
        orderBook.setDefaultVirtualVolumeDistributionFunction(this::getDefaultVolume);
        this.cache = cache;
    }

    @Override
    public @NotNull List<LimitOrder> getBuyOrders() {
        return orderBook.getBuyOrders().stream().toList();
    }

    @Override
    public @NotNull List<LimitOrder> getSellOrders() {
        return orderBook.getSellOrders().stream().toList();
    }

    @Override
    public @NotNull List<Order> getNewOrders() {
        return orderBook.getIncommingOrders();
    }

    @Override
    public float getVolume(float minPrice, float maxPrice) {
        if(minPrice > maxPrice)
        {
            float tmp = minPrice;
            minPrice = maxPrice;
            maxPrice = tmp;
        }
        return orderBook.getVolumeInRealRange(minPrice, maxPrice);
    }

    @Override
    public float getVolume(int backendPrice) {
        return orderBook.getVolumeRawPrice(backendPrice, serverMarket.getCurrentRawPrice());
    }

    @Override
    public @NotNull Tuple<@NotNull Float, @NotNull Float> getEditablePriceRange() {
        return orderBook.getEditablePriceRange();
    }

    @Override
    public @NotNull Tuple<@NotNull Integer, @NotNull Integer> getEditableBackendPriceRange() {
        return orderBook.getEditableBackendPriceRange();
    }

    @Override
    public void setVolume(float minPrice, float maxPrice, float volume) {
        cache.addManipulation(minPrice, maxPrice, volume, VirtualOrderBookCache.ManipulationOperator.SET);
    }

    @Override
    public void setVolume(int backendStartPrice, float[] volume) {
        cache.addManipulation(backendStartPrice, volume, VirtualOrderBookCache.ManipulationOperator.SET);
    }

    @Override
    public void addVolume(float minPrice, float maxPrice, float volume) {
        cache.addManipulation(minPrice, maxPrice, volume, VirtualOrderBookCache.ManipulationOperator.ADD);
    }

    @Override
    public void addVolume(int backendStartPrice, float[] volume) {
        cache.addManipulation(backendStartPrice, volume, VirtualOrderBookCache.ManipulationOperator.ADD);
    }

    @Override
    public void registerDefaultVolumeDistributionCalculator(IVolumeDistributionCalculator distributionCalculator) {
        volumeDistributionCalculators.add(distributionCalculator);
    }

    @Override
    public void unregisterDefaultVolumeDistributionCalculator(IVolumeDistributionCalculator distributionCalculator) {
        volumeDistributionCalculators.remove(distributionCalculator);
    }

    @Override
    public float getDefaultVolume(float pickPrice)
    {
        float volume = 0;
        float currentMarketPrice = serverMarket.getCurrentRealPrice();
        for(IVolumeDistributionCalculator distributionCalculator : volumeDistributionCalculators)
        {
            volume += Math.abs(distributionCalculator.getVolume(currentMarketPrice, pickPrice));
        }
        if(currentMarketPrice > pickPrice)
            return volume;
        return -volume;
    }
}
