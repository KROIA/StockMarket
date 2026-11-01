package net.kroia.stockmarket.api.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.MarketSettings;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IAsyncMarket {

    ItemID getItemIDAsync();
    CompletableFuture<Long> getDefaultPriceAsync();
    CompletableFuture<Long> getCurrentMarketPriceAsync();
    CompletableFuture<Long> getCurrentTimeAsync();
    CompletableFuture<Long> getRawVolumeAsync(long price);
    CompletableFuture<Long> getRawVolumeAsync(long startPrice, long endPrice);
    CompletableFuture<Float> getRealVolumeAsync(double price);
    CompletableFuture<Float> getRealVolumeAsync(double startPrice, double endPrice);

    CompletableFuture<Boolean> putOrderAsync(Order order);
    CompletableFuture<List<Order>> getLimitOrdersAsync();
    CompletableFuture<Boolean> isMarketOpenAsync();
    CompletableFuture<Boolean> setMarketOpenAsync(boolean marketOpen);

    CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAsync();
    CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAndResetAsync();

    CompletableFuture<MarketSettings> getSettingsAsync();
    CompletableFuture<Boolean> setSettingsAsync(MarketSettings settings);

    CompletableFuture<Boolean> resetNetPlayerItemFlowAsync();

    /**
     * Async counterpart of {@link net.kroia.stockmarket.api.market.ISyncServerMarket#resetVirtualOrderbook()}.
     * Refills the virtual orderbook from the default distribution and drops the sticky-clear
     * flag. Admin-gated server-side; the future resolves to false when denied.
     */
    CompletableFuture<Boolean> resetVirtualOrderbookAsync();

    /**
     * Async counterpart of {@link net.kroia.stockmarket.api.market.ISyncServerMarket#clearVirtualOrderbook()}.
     * Zeroes the virtual orderbook and sets the sticky-clear flag so shift-fill stays at 0.
     * Admin-gated server-side; the future resolves to false when denied.
     */
    CompletableFuture<Boolean> clearVirtualOrderbookAsync();

}
