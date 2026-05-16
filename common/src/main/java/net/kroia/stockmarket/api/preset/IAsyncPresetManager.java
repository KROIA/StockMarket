package net.kroia.stockmarket.api.preset;

import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPresetCategory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

// Async interface for accessing market presets from client/slave side
public interface IAsyncPresetManager {

    // Fetches all preset categories with their presets from the server
    CompletableFuture<List<MarketPresetCategory>> getCategoriesAsync();

    // Sends updated preset values to the server for persistence
    CompletableFuture<Boolean> updatePresetsAsync(List<MarketPreset> updatedPresets);
}
