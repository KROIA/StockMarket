package net.kroia.stockmarket.pluginsystem.interaction;

import net.kroia.stockmarket.StockMarketModBackend;
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
    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }



    private final ServerMarket serverMarket;
    private final Orderbook orderbook;
    private final MarketCache cache;

    private final List<IVolumeDistributionCalculator> volumeDistributionCalculators = new ArrayList<>();


    public PluginOrderBook(@NotNull ServerMarket serverMarket,
                        @NotNull MarketCache cache)
    {
        this.serverMarket = serverMarket;
        this.orderbook = serverMarket.getOrderbook();
        serverMarket.test_setDefaultVolumeProviderFunction(this::getDefaultRealVolume);
        //serverMarket.test_resetVirtualOrderBookVolume();
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
        return serverMarket.getIncomingOrders();
    }

    @Override
    public float getRealVolume(double minPrice, double maxPrice) {
        if(minPrice > maxPrice)
        {
            double tmp = minPrice;
            minPrice = maxPrice;
            maxPrice = tmp;
        }
        return orderbook.getRealVolume(minPrice, maxPrice);
    }

    @Override
    public float getRealVolume(long backendPrice) {
        return (float)MarketManager.convertToRealAmountStatic(orderbook.getRawVolume(backendPrice));
    }

    @Override
    public float getRealVirtualVolume(long backendPrice)
    {
        float rawVirtualValue = orderbook.getRawVirtualVolume(backendPrice);
        int scaleFactor = BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor();
        return rawVirtualValue/scaleFactor;
    }

    @Override
    public long getRawVolume(long backendPrice)
    {
        return orderbook.getRawVolume(backendPrice);
    }

    @Override
    public float getRawVirtualVolume(long backendPrice)
    {
        return orderbook.getRawVirtualVolume(backendPrice);
    }

    @Override
    public @NotNull Tuple<@NotNull Double, @NotNull Double> getEditablePriceRange() {
        return orderbook.getEditableRealPriceRange();
    }

    @Override
    public @NotNull Tuple<@NotNull Long, @NotNull Long> getEditableBackendPriceRange() {
        return orderbook.getEditableRawPriceRange();
    }

    @Override
    public void setRawVolume(double minPrice, double maxPrice, float volume) {
        cache.addManipulation(minPrice, maxPrice, volume, VirtualOrderBookCache.ManipulationOperator.SET);
    }

    @Override
    public void setRawVolume(long backendStartPrice, float[] volume) {
        cache.addManipulation(backendStartPrice, volume, VirtualOrderBookCache.ManipulationOperator.SET);
    }

    @Override
    public void addRawVolume(double minPrice, double maxPrice, float volume) {
        cache.addManipulation(minPrice, maxPrice, volume, VirtualOrderBookCache.ManipulationOperator.ADD);
    }

    @Override
    public void addRawVolume(long backendStartPrice, float[] volume) {
        cache.addManipulation(backendStartPrice, volume, VirtualOrderBookCache.ManipulationOperator.ADD);
    }

    @Override
    public void resetVirtualVolume()
    {
        cache.resetVirtualVolume();
    }

    @Override
    public void registerDefaultVolumeDistributionCalculator(IVolumeDistributionCalculator distributionCalculator) {
        volumeDistributionCalculators.add(distributionCalculator);
    }

    @Override
    public void unregisterDefaultVolumeDistributionCalculator(IVolumeDistributionCalculator distributionCalculator) {
        volumeDistributionCalculators.remove(distributionCalculator);
    }

    /**
     * @param pickPrice the real value price at which the volume gets measured
     * @return the real volume at the given price
     */
    @Override
    public float getDefaultRealVolume(double pickPrice)
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
    @Override
    public float getDefaultRawVolume(double pickPrice)
    {
        return getDefaultRealVolume(pickPrice) * BACKEND_INSTANCES.BANK_SYSTEM_API.getServerBankManager().getSync().getItemFractionScaleFactor();
    }

    @Override
    public float getCapital(long startPrice, long endPrice) {
        return orderbook.getCapital(startPrice, endPrice);
    }

    @Override
    public @NotNull List<Order> getBuyOrders(long startPrice, long endPrice) {
        return orderbook.getBuyOrders(startPrice, endPrice);
    }

    @Override
    public @NotNull List<Order> getSellOrders(long startPrice, long endPrice) {
        return orderbook.getSellOrders(startPrice, endPrice);
    }
}
