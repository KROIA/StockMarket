package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ARRS request to update plugin-specific custom settings on the server.
 * Input: instanceID + opaque byte[] payload (each plugin type encodes/decodes its own format).
 * Output: success flag + confirmed settings bytes from the server.
 */
public class PluginCustomSettingsRequest extends StockMarketGenericRequest<PluginCustomSettingsRequest.InputData, PluginCustomSettingsRequest.OutputData> {

    /**
     * @param instanceID the UUID of the plugin instance to update
     * @param payload    the encoded custom settings bytes
     */
    public record InputData(UUID instanceID, ItemID marketID, byte[] payload) {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, InputData::instanceID,
                ItemID.STREAM_CODEC, InputData::marketID,
                ByteBufCodecs.BYTE_ARRAY, InputData::payload,
                InputData::new
        );
    }

    /**
     * @param success          true if the custom settings were applied successfully
     * @param confirmedPayload the plugin's confirmed custom settings after update, or null on failure
     */
    public record OutputData(boolean success, @Nullable ItemID marketID, @Nullable byte[] confirmedPayload) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, OutputData::success,
                ExtraCodecUtils.nullable(ItemID.STREAM_CODEC), OutputData::marketID,
                ExtraCodecUtils.nullable(ByteBufCodecs.BYTE_ARRAY), OutputData::confirmedPayload,
                OutputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return PluginCustomSettingsRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(false, null, null);
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        // Require the StockMarket-admin flag (the same flag /stockmarket manage
        // requires), resolved from the master's user map so it also works for
        // players connected through a slave. Fail closed on a missing sender.
        if (playerSender == null || !playerIsAdmin(playerSender)) {
            return CompletableFuture.completedFuture(new OutputData(false, null, null));
        }
        // T-123 (untrusted slave gate): plugin custom-settings write is mutating.
        if (!NetworkGate.isMutatingCallAllowed(slaveID, "PluginCustomSettingsRequest")) {
            return CompletableFuture.completedFuture(new OutputData(false, null, null));
        }

        ServerPluginManager pluginManager = (ServerPluginManager) getPluginManager();
        if (pluginManager == null) {
            return CompletableFuture.completedFuture(new OutputData(false, null, null));
        }

        ServerPlugin plugin = pluginManager.getPlugins().get(input.instanceID());
        if (plugin == null) {
            return CompletableFuture.completedFuture(new OutputData(false, null, null));
        }

        boolean applied = plugin.decodeAndApplyCustomSettings(input.marketID(), input.payload());
        byte[] confirmed = applied ? plugin.encodeCustomSettings(input.marketID()) : null;

        if (!applied) {
            StockMarketMod.LOGGER.warn("[PluginCustomSettingsRequest] Failed to apply custom settings for plugin '{}' (instance {})",
                    plugin.getName(), input.instanceID());
        }

        // Notify other admins about the custom settings change
        if (applied) {
            broadcastToAdmins(playerSender,
                    getPlayerName(playerSender) + " updated custom settings for plugin '" + plugin.getName() + "'");
        }

        return CompletableFuture.completedFuture(new OutputData(applied, input.marketID(), confirmed));
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, OutputData output) {
        OutputData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public OutputData decodeOutput(RegistryFriendlyByteBuf buf) {
        return OutputData.STREAM_CODEC.decode(buf);
    }
}
