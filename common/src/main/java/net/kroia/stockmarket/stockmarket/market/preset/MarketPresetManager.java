package net.kroia.stockmarket.stockmarket.market.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class MarketPresetManager {

    private static final String PRESET_DIR = "stockmarket/market_presets";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<MarketPresetCategory> categories = new ArrayList<>();

    // Call on server startup
    public void loadOrGenerate() {
        categories.clear();
        Path presetDir = Platform.getConfigFolder().resolve(PRESET_DIR);

        try {
            if (!Files.exists(presetDir)) {
                Files.createDirectories(presetDir);
            }

            // Check if any JSON files exist
            boolean hasFiles;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(presetDir, "*.json")) {
                hasFiles = stream.iterator().hasNext();
            }

            if (!hasFiles) {
                // First startup: generate defaults and write to JSON
                List<MarketPresetCategory> defaults = DefaultPresets.generate();
                for (MarketPresetCategory cat : defaults) {
                    saveCategory(presetDir, cat);
                }
                categories.addAll(defaults);
                StockMarketMod.LOGGER.info("Generated {} default market preset files", defaults.size());
            } else {
                // Load existing JSON files
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(presetDir, "*.json")) {
                    for (Path file : stream) {
                        try {
                            String json = Files.readString(file);
                            MarketPresetCategory cat = GSON.fromJson(json, MarketPresetCategory.class);
                            if (cat != null && cat.getCategory() != null) {
                                categories.add(cat);
                            }
                        } catch (Exception e) {
                            StockMarketMod.LOGGER.error("Failed to load market preset file: {}", file.getFileName(), e);
                        }
                    }
                }
                StockMarketMod.LOGGER.info("Loaded {} market preset categories", categories.size());
            }
        } catch (IOException e) {
            StockMarketMod.LOGGER.error("Failed to initialize market preset directory", e);
        }
    }

    private void saveCategory(Path presetDir, MarketPresetCategory category) throws IOException {
        String filename = category.getCategory() + ".json";
        Path file = presetDir.resolve(filename);
        Files.writeString(file, GSON.toJson(category));
    }

    // Save all categories back to JSON (for when values are edited)
    public void saveAll() {
        Path presetDir = Platform.getConfigFolder().resolve(PRESET_DIR);
        try {
            for (MarketPresetCategory cat : categories) {
                saveCategory(presetDir, cat);
            }
        } catch (IOException e) {
            StockMarketMod.LOGGER.error("Failed to save market presets", e);
        }
    }

    // Look up a preset by item ID string (e.g. "minecraft:iron_ingot") -- returns first match
    public @Nullable MarketPreset getPreset(String itemId) {
        for (MarketPresetCategory cat : categories) {
            MarketPreset preset = cat.findPreset(itemId);
            if (preset != null) return preset;
        }
        return null;
    }

    // Convenience: look up by ItemID
    public @Nullable MarketPreset getPreset(ItemID itemId) {
        return getPreset(itemId.getName());
    }

    public List<MarketPresetCategory> getCategories() {
        return categories;
    }

    public @Nullable MarketPresetCategory getCategory(String name) {
        for (MarketPresetCategory cat : categories) {
            if (cat.getCategory().equals(name)) return cat;
        }
        return null;
    }
}
