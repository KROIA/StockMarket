package net.kroia.stockmarket.api.preset;

import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPresetCategory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous access to the market preset catalog (the Presets tab of the Management screen). The preset files
 * live on the master; on a client or a slave every call is a request to it.
 * <p>
 * Admin data: the master answers a call without a player, or from a player without StockMarket admin mode, with an
 * empty list or {@code false}, and refuses every call from an untrusted slave (the future then completes with
 * {@code null}).
 * <p>
 * <b>Request failures (2.2.0):</b> a future that waits for the master's answer can fail with ModUtilities'
 * {@link net.kroia.modutilities.networking.client_server.arrs.RequestOutcomeUnknownException} (sent, but no answer
 * in time or the connection was lost: the master <b>may have applied it</b>) or
 * {@link net.kroia.modutilities.networking.client_server.arrs.RequestNotSentException} (never sent, nothing
 * changed), often wrapped in a {@link java.util.concurrent.CompletionException}. After an unknown outcome a caller
 * must never undo or resend a write automatically: reload the catalog instead. A slave without a master connection
 * sends nothing and completes at once with an empty list or {@code false}. Details:
 * {@code documentation/developer-guide/api/async-market-api.md}, section 3.
 */
public interface IAsyncPresetManager {

    /**
     * @return future with all preset categories and their presets
     */
    CompletableFuture<List<MarketPresetCategory>> getCategoriesAsync();

    /**
     * Replaces presets in the catalog, matched by their item, and saves the preset files.
     * @param updatedPresets the new versions; presets whose item is in no category are ignored
     * @return future resolving to {@code true} when saved
     */
    CompletableFuture<Boolean> updatePresetsAsync(List<MarketPreset> updatedPresets);

    /**
     * Adds a category with all its presets, or replaces the category with the same name, and saves it.
     * @param category the complete category
     * @return future resolving to {@code true} when saved
     */
    CompletableFuture<Boolean> saveCategoryAsync(MarketPresetCategory category);

    /**
     * Deletes a category, its presets and its preset file.
     * @param categoryName the category's name
     * @return future resolving to {@code true} if the category existed and was deleted
     */
    CompletableFuture<Boolean> deleteCategoryAsync(String categoryName);

    /**
     * Renames a category and its preset file.
     * @param oldName the current name
     * @param newName the new name
     * @return future resolving to {@code true} if a category with the old name existed and was renamed
     */
    CompletableFuture<Boolean> renameCategoryAsync(String oldName, String newName);
}
