package net.kroia.stockmarket.stockmarket.market.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.core.RegistryAccess;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class MarketPresetManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<MarketPresetCategory> categories = new ArrayList<>();

    // Call on server startup
    public void loadOrGenerate(Path presetDir, @Nullable RegistryAccess registries) {
        categories.clear();

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
                List<MarketPresetCategory> defaults = DefaultPresets.generate(registries);
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

    public void saveCategory(Path presetDir, MarketPresetCategory category) throws IOException {
        String filename = category.getCategory() + ".json";
        Path file = presetDir.resolve(filename);
        Files.writeString(file, GSON.toJson(category));
    }

    // Save all categories back to JSON (for when values are edited)
    public void saveAll(Path presetDir) {
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

    // Adds a new category or replaces existing with the same name, then saves
    public void addOrReplaceCategory(MarketPresetCategory category, Path presetDir) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getCategory().equals(category.getCategory())) {
                categories.set(i, category);
                try { saveCategory(presetDir, category); } catch (IOException e) {
                    StockMarketMod.LOGGER.error("Failed to save category: {}", category.getCategory(), e);
                }
                return;
            }
        }
        categories.add(category);
        try { saveCategory(presetDir, category); } catch (IOException e) {
            StockMarketMod.LOGGER.error("Failed to save category: {}", category.getCategory(), e);
        }
    }

    // Removes a category by name and deletes its JSON file
    public boolean removeCategory(String name, Path presetDir) {
        boolean removed = categories.removeIf(c -> c.getCategory().equals(name));
        if (removed) {
            try {
                Path file = presetDir.resolve(name + ".json");
                Files.deleteIfExists(file);
            } catch (IOException e) {
                StockMarketMod.LOGGER.error("Failed to delete category file: {}", name, e);
            }
        }
        return removed;
    }

    // Renames a category and its JSON file
    public boolean renameCategory(String oldName, String newName, Path presetDir) {
        for (MarketPresetCategory cat : categories) {
            if (cat.getCategory().equals(oldName)) {
                // Delete old file
                try {
                    Path oldFile = presetDir.resolve(oldName + ".json");
                    Files.deleteIfExists(oldFile);
                } catch (IOException e) {
                    StockMarketMod.LOGGER.error("Failed to delete old category file: {}", oldName, e);
                }
                // Rename in memory and save new file
                cat.setCategory(newName);
                try { saveCategory(presetDir, cat); } catch (IOException e) {
                    StockMarketMod.LOGGER.error("Failed to save renamed category: {}", newName, e);
                }
                return true;
            }
        }
        return false;
    }
}
