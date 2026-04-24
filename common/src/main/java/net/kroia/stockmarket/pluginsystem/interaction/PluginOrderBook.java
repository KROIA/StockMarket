package net.kroia.stockmarket.pluginsystem.interaction;

import net.kroia.stockmarket.api.plugin.interaction.IPluginOrderBook;
import net.kroia.stockmarket.api.plugin.interaction.IVolumeDistributionCalculator;
import net.kroia.stockmarket.pluginsystem.plugin.core.cache.MarketCache;
import net.kroia.stockmarket.pluginsystem.plugin.core.cache.VirtualOrderBookCache;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.Orderbook;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PluginOrderBook implements IPluginOrderBook
{
    private final ServerMarket serverMarket;
    private final Orderbook orderbook;
    private final MarketCache cache;

    private final List<IVolumeDistributionCalculator> volumeDistributionCalculators = new ArrayList<>();


    public PluginOrderBook(@NotNull ServerMarket serverMarket,
                        @NotNull MarketCache cache)
    {
        this.serverMarket = serverMarket;
        this.orderbook = serverMarket.getOrderbook();
        serverMarket.test_setDefaultVolumeProviderFunction(this::getDefaultVolume);
        this.cache = cache;
    }

    @Override
    public @NotNull List<Order> getBuyOrders() {
        return orderbook.getBuyOrders().stream().toList();
    }

    @Override
    public @NotNull List<Order> getSellOrders() {
        return orderbook.getSellOrders().stream().toList();
    }

    @Override
    public @NotNull List<Order> getNewOrders() {
        return serverMarket.getIncommingOrders();
    }

    @Override
    public float getVolume(double minPrice, double maxPrice) {
        if(minPrice > maxPrice)
        {
            double tmp = minPrice;
            minPrice = maxPrice;
            maxPrice = tmp;
        }
        return orderbook.getVolume(MarketManager.convertToRawAmountStatic(minPrice),
                                   MarketManager.convertToRawAmountStatic(maxPrice));
    }

    @Override
    public float getVolume(long backendPrice) {
        return orderbook.getVolume(backendPrice);
    }

    @Override
    public float getVirtualVolume(long backendPrice)
    {
        return orderbook.getVirtualVolume(backendPrice);
    }

    @Override
    public @NotNull Tuple<@NotNull Double, @NotNull Double> getEditablePriceRange() {
        return orderbook.getEditablePriceRange();
    }

    @Override
    public @NotNull Tuple<@NotNull Long, @NotNull Long> getEditableBackendPriceRange() {
        return orderbook.getEditableBackendPriceRange();
    }

    @Override
    public void setVolume(double minPrice, double maxPrice, float volume) {
        cache.addManipulation(minPrice, maxPrice, volume, VirtualOrderBookCache.ManipulationOperator.SET);
    }

    @Override
    public void setVolume(long backendStartPrice, float[] volume) {
        cache.addManipulation(backendStartPrice, volume, VirtualOrderBookCache.ManipulationOperator.SET);
    }

    @Override
    public void addVolume(double minPrice, double maxPrice, float volume) {
        cache.addManipulation(minPrice, maxPrice, volume, VirtualOrderBookCache.ManipulationOperator.ADD);
    }

    @Override
    public void addVolume(long backendStartPrice, float[] volume) {
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
    public float getDefaultVolume(double pickPrice)
    {
        float volume = 0;
        float currentMarketPrice = (float)MarketManager.convertToRealAmountStatic(serverMarket.getCurrentMarketPrice());
        for(IVolumeDistributionCalculator distributionCalculator : volumeDistributionCalculators)
        {
            volume += Math.abs(distributionCalculator.getVolume(currentMarketPrice, pickPrice));
        }
        if(currentMarketPrice > pickPrice)
            return volume;
        return -volume;
    }
}
