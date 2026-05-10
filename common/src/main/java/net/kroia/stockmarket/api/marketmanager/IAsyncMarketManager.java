package net.kroia.stockmarket.api.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IAsyncMarketManager {

    CompletableFuture<ItemID> getTradingCurrencyIDAsync();
    CompletableFuture<List<ItemID>> getAvailableMarketIDsAsync();
    CompletableFuture<Boolean> marketExistsAsync(@NotNull ItemID marketID);

    CompletableFuture<@Nullable IAsyncMarket> createMarketAsync(@NotNull ItemID marketID);
    CompletableFuture<Boolean> deleteMarketAsync(@NotNull ItemID marketID);
    CompletableFuture<@Nullable IAsyncMarket> getMarketAsync(@NotNull ItemID marketID);

    CompletableFuture<Boolean> setStockmarketAdminModeAsync(UUID playerUUID, boolean isAdmin);
    CompletableFuture<Boolean> isStockmarketAdminAsync(UUID playerUUID);

    void onPlayerJoinAsync(UUID playerUUID, String playerName);


}
