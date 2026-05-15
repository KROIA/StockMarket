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

}
