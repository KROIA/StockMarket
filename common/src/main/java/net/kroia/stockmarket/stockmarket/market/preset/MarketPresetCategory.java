package net.kroia.stockmarket.stockmarket.market.preset;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

// One JSON file's worth of presets, representing a category of items
public class MarketPresetCategory {
    private String category;
    private List<MarketPreset> presets;

    public MarketPresetCategory() {
        this.category = "";
        this.presets = new ArrayList<>();
    }

    public MarketPresetCategory(String category, List<MarketPreset> presets) {
        this.category = category;
        this.presets = new ArrayList<>(presets);
    }

    public String getCategory() { return category; }
    public List<MarketPreset> getPresets() { return presets; }

    public @Nullable MarketPreset findPreset(String itemId) {
        for (MarketPreset preset : presets) {
            if (preset.itemId().equals(itemId)) return preset;
        }
        return null;
    }
}
