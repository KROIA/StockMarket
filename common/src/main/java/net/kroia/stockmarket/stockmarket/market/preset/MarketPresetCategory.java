package net.kroia.stockmarket.stockmarket.market.preset;

import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.minecraft.locale.Language;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// One JSON file's worth of presets, representing a category of items
public class MarketPresetCategory {
    private String category;
    private List<MarketPreset> presets;

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketPresetCategory> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MarketPresetCategory::getCategory,
            ExtraCodecUtils.listStreamCodec(MarketPreset.STREAM_CODEC), MarketPresetCategory::getPresets,
            MarketPresetCategory::new
    );

    public MarketPresetCategory() {
        this.category = "";
        this.presets = new ArrayList<>();
    }

    public MarketPresetCategory(String category, List<MarketPreset> presets) {
        this.category = category;
        this.presets = new ArrayList<>(presets);
    }

    public String getCategory() { return category; }

    /**
     * Name to show for a category in the GUI. The stored name stays the identifier
     * (file name, network, commands); only the display is translated. Default categories
     * have a lang key {@code gui.stockmarket.preset_category.<lowercase name>}; custom
     * categories have none and are shown as typed.
     *
     * @param category the stored category name
     * @return the translated name, or {@code category} when no lang key exists
     */
    public static String displayName(String category) {
        return Language.getInstance().getOrDefault(
                "gui.stockmarket.preset_category." + category.toLowerCase(Locale.ROOT), category);
    }
    public void setCategory(String name) { this.category = name; }
    public List<MarketPreset> getPresets() { return presets; }

    public @Nullable MarketPreset findPreset(String itemId) {
        for (MarketPreset preset : presets) {
            if (preset.getItemId().equals(itemId)) return preset;
        }
        return null;
    }

    public @Nullable MarketPreset findPresetByKey(String uniqueKey) {
        for (MarketPreset preset : presets) {
            if (preset.getUniqueKey().equals(uniqueKey)) return preset;
        }
        return null;
    }
}
