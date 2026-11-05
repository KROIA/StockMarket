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
    /**
     * Queries whether this market is under maintenance (admin/integration maintenance mode).
     * @return future resolving to {@code true} if under maintenance
     */
    CompletableFuture<Boolean> isUnderMaintenanceAsync();
    /**
     * Enables or disables maintenance mode. Enabling it cancels all player orders and inter-market orders
     * referencing this market; bots keep trading.
     * @param maintenance {@code true} to enter maintenance, {@code false} to end it
     * @return future resolving to {@code true} on success
     */
    CompletableFuture<Boolean> setMaintenanceAsync(boolean maintenance);

    /**
     * Async counterpart of {@link net.kroia.stockmarket.api.market.ISyncServerMarket#setTradingHours(int, int)}.
     * Admin-gated for players; refused when forwarded from an untrusted slave.
     * @param openMinute  opening minute of the day (0..1440)
     * @param closeMinute closing minute of the day (0..1440)
     * @return future resolving to {@code true} on success, {@code false} when denied
     */
    CompletableFuture<Boolean> setTradingHoursAsync(int openMinute, int closeMinute);

    /**
     * Async counterpart of {@link net.kroia.stockmarket.api.market.ISyncServerMarket#setTradingOverride(boolean)}.
     * Server-side only (not callable by clients); refused when forwarded from an untrusted slave.
     * @param open {@code true} to force open, {@code false} to force closed until the next scheduled transition
     * @return future resolving to {@code true} on success, {@code false} when denied
     */
    CompletableFuture<Boolean> setTradingOverrideAsync(boolean open);

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
