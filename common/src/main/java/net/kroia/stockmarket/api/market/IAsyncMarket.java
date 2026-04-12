package net.kroia.stockmarket.api.market;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IAsyncMarket {

    ItemID getItemIDAsync();
    CompletableFuture<Long> getCurrentMarketPriceAsync();
    CompletableFuture<Long> getCurrentTimeAsync();
    CompletableFuture<Long> getVolumeAsync(long price);

    CompletableFuture<Boolean> putOrderAsync(Order order);
    CompletableFuture<Boolean> putOrderAsync(InterMarketOrder order);
    CompletableFuture<List<Order>> getLimitOrdersAsync();
    CompletableFuture<Boolean> isMarketOpenAsync();
    CompletableFuture<Boolean> setMarketOpenAsync(boolean marketOpen);

    CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAsync();
    CompletableFuture<MarketPriceStruct> getCurrentMarketPriceStructAndResetAsync();
}
