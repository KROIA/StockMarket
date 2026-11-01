package net.kroia.stockmarket.stockmarket.market.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.core.RegistryAccess;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                // Load existing JSON files. Before deserializing, forward-merge any fields
                // that the current MarketPreset schema declares but the on-disk JSON lacks
                // (e.g. new preset flags added in a mod update). This keeps admin-authored
                // presets working across upgrades without them having to hand-edit every
                // file. See {@link #forwardMergeCategoryJson} for the merge policy.
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(presetDir, "*.json")) {
                    for (Path file : stream) {
                        try {
                            String json = Files.readString(file);
                            JsonElement rootEl = JsonParser.parseString(json);
                            if (rootEl == null || !rootEl.isJsonObject()) {
                                StockMarketMod.LOGGER.warn("Preset file {} is not a JSON object; skipping schema merge", file.getFileName());
                            } else {
                                JsonObject root = rootEl.getAsJsonObject();
                                List<String> filledFieldsPerPreset = new ArrayList<>();
                                boolean changed = forwardMergeCategoryJson(root, filledFieldsPerPreset);
                                if (changed) {
                                    // Only rewrite when something was actually filled — avoids
                                    // touching the file's mtime on every load (file-watchers,
                                    // backup tooling, docker image layer churn).
                                    Files.writeString(file, GSON.toJson(root));
                                    StockMarketMod.LOGGER.info(
                                            "Auto-completed preset file {} with missing fields per entry: {}",
                                            file.getFileName(), filledFieldsPerPreset);
                                }
                                json = GSON.toJson(root);
                            }
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

    /**
     * Looks up a preset by its {@link ItemID}.
     * <p>
     * Presets are pre-registered with their ItemID short during server startup
     * (see {@code StockMarketModBackend.preRegisterPresetItemStacks}) and on the
     * client via the preset sync stream codec. Matching on that registered ID is
     * component-aware: two presets sharing the same registry name (e.g. enchanted
     * books with different enchantments, or items whose data components drift over
     * time) resolve to the correct entry, whereas a registry-name-only lookup would
     * return the first name match regardless of components.
     * <p>
     * Falls back to the registry-name lookup of {@link #getPreset(String)} for
     * presets that carry no registered ItemID (e.g. lookups performed before the
     * startup registration ran, or presets whose stack failed to register).
     *
     * @param itemId the market's ItemID
     * @return the matching preset, or {@code null} if none matches
     */
    public @Nullable MarketPreset getPreset(ItemID itemId) {
        // First pass: exact, component-aware match via the pre-registered ItemID short.
        for (MarketPresetCategory cat : categories) {
            for (MarketPreset preset : cat.getPresets()) {
                if (preset.hasRegisteredItemID() && preset.getRegisteredItemIDShort() == itemId.getShort()) {
                    return preset;
                }
            }
        }
        // Fallback: registry-name match (legacy behavior) for unregistered presets.
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

    /**
     * Forward-merges any fields present in a fresh {@link MarketPreset}'s serialized JSON
     * but absent from the preset entries in {@code categoryRoot["presets"]}. The default
     * value comes from a freshly-constructed {@link MarketPreset} run through the same
     * {@link #GSON} instance, so the "current schema" is derived purely from the POJO —
     * any new field added to {@code MarketPreset} auto-completes on next load with zero
     * changes here.
     * <p>
     * Merge policy:
     * <ul>
     *   <li>Per-preset-entry, not per-file: only entries missing keys are touched.</li>
     *   <li>Existing keys are never overwritten (admin-modified values are preserved).</li>
     *   <li>Only fields whose default serializes to a non-null JSON element are candidates
     *       — Gson's default behaviour skips null fields, so nullable fields like
     *       {@code components} do not spuriously appear on presets that lack them.</li>
     * </ul>
     *
     * @param categoryRoot         the parsed {@code MarketPresetCategory} JSON object
     * @param filledFieldsPerPreset out-param: appended one string per touched entry,
     *                              describing the entry and the fields filled
     * @return {@code true} iff at least one entry was mutated (caller should rewrite the file)
     */
    static boolean forwardMergeCategoryJson(JsonObject categoryRoot, List<String> filledFieldsPerPreset) {
        JsonElement presetsEl = categoryRoot.get("presets");
        if (presetsEl == null || !presetsEl.isJsonArray()) return false;
        JsonArray presets = presetsEl.getAsJsonArray();

        // Serialize a fresh default MarketPreset to establish the "current schema" reference.
        // Gson skips null and transient fields, so this yields exactly the set of keys
        // present-by-default in a new-instance JSON (e.g. orderbookEnabled=true,
        // ignorePluginAutosubscribe=false, itemId="", defaultPrice=0, naturalAbundance=0).
        JsonElement defaultsEl = GSON.toJsonTree(new MarketPreset());
        if (!defaultsEl.isJsonObject()) return false;
        JsonObject defaults = defaultsEl.getAsJsonObject();

        boolean anyChanged = false;
        for (int i = 0; i < presets.size(); i++) {
            JsonElement entryEl = presets.get(i);
            if (entryEl == null || !entryEl.isJsonObject()) continue;
            JsonObject entry = entryEl.getAsJsonObject();

            List<String> filled = new ArrayList<>();
            for (Map.Entry<String, JsonElement> defField : defaults.entrySet()) {
                String key = defField.getKey();
                if (entry.has(key)) continue;
                entry.add(key, defField.getValue().deepCopy());
                filled.add(key);
            }
            if (!filled.isEmpty()) {
                String label = entry.has("itemId") ? entry.get("itemId").getAsString() : ("index " + i);
                filledFieldsPerPreset.add(label + " -> " + filled);
                anyChanged = true;
            }
        }
        return anyChanged;
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
