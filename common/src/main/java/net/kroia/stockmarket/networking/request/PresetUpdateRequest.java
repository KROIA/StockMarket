package net.kroia.stockmarket.networking.request;

import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.data.DataManager;
import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPresetCategory;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPresetManager;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ARRS request to update market preset prices and abundance values on the server.
 * Input: list of PresetData entries with updated values.
 * Output: boolean indicating success or failure.
 */
public class PresetUpdateRequest extends StockMarketGenericRequest<PresetUpdateRequest.InputData, Boolean> {

    /**
     * Single preset entry sent from client to server.
     *
     * @param itemId           the registry item ID (e.g. "minecraft:iron_ingot")
     * @param defaultPrice     the updated default price
     * @param naturalAbundance the updated natural abundance
     */
    public record PresetData(String itemId, float defaultPrice, float naturalAbundance) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PresetData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PresetData::itemId,
                ByteBufCodecs.FLOAT, PresetData::defaultPrice,
                ByteBufCodecs.FLOAT, PresetData::naturalAbundance,
                PresetData::new
        );
    }

    /**
     * Input payload containing all presets to update.
     *
     * @param presets the list of preset entries with new values
     */
    public record InputData(List<PresetData> presets) {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                ExtraCodecUtils.listStreamCodec(PresetData.STREAM_CODEC), InputData::presets,
                InputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return PresetUpdateRequest.class.getName();
    }

    @Override
    protected Boolean getDefaultResponse() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        // Require the StockMarket-admin flag (the same flag /stockmarket manage
        // requires), resolved from the master's user map so it also works for
        // players connected through a slave. Fail closed on a missing sender.
        if (playerSender == null || !playerIsAdmin(playerSender)) {
            return CompletableFuture.completedFuture(false);
        }
        // T-123 (untrusted slave gate): preset edits are mutating.
        if (!NetworkGate.isMutatingCallAllowed(slaveID, "PresetUpdateRequest")) {
            return CompletableFuture.completedFuture(false);
        }

        MarketPresetManager presetManager = BACKEND_INSTANCES.PRESET_MANAGER;
        if (presetManager == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Apply each preset update by finding and replacing the record in the category's list
        for (PresetData pd : input.presets()) {
            for (MarketPresetCategory cat : presetManager.getCategories()) {
                List<MarketPreset> presets = cat.getPresets();
                for (int i = 0; i < presets.size(); i++) {
                    if (presets.get(i).getItemId().equals(pd.itemId())) {
                        presets.set(i, new MarketPreset(pd.itemId(), pd.defaultPrice(), pd.naturalAbundance()));
                        break;
                    }
                }
            }
        }

        // Persist updated presets to JSON files
        presetManager.saveAll(DataManager.getPresetPath());
        info("Updated " + input.presets().size() + " preset(s)");
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, Boolean output) {
        ByteBufCodecs.BOOL.encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public Boolean decodeOutput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.BOOL.decode(buf);
    }
}
