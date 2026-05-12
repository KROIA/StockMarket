package net.kroia.stockmarket.api.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.market.IAsyncMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
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

    /**
     * Asynchronously gets the player's trading preferences.
     * @param playerUUID the player to get preferences for
     * @return future containing the preferences
     */
    CompletableFuture<PlayerPreferences> getPlayerPreferencesAsync(UUID playerUUID);

    /**
     * Asynchronously updates the player's trading preferences.
     * @param playerUUID the player to update
     * @param preferences the new preferences
     * @return future containing true if successful
     */
    CompletableFuture<Boolean> updatePlayerPreferencesAsync(UUID playerUUID, PlayerPreferences preferences);

    void onPlayerJoinAsync(UUID playerUUID, String playerName);


}
