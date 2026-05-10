package net.kroia.stockmarket.networking.request;

import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
    public record InputData(UUID instanceID, byte[] payload) {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, InputData::instanceID,
                ByteBufCodecs.BYTE_ARRAY, InputData::payload,
                InputData::new
        );
    }

    /**
     * @param success          true if the custom settings were applied successfully
     * @param confirmedPayload the plugin's confirmed custom settings after update, or null on failure
     */
    public record OutputData(boolean success, @Nullable byte[] confirmedPayload) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, OutputData::success,
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
        return new OutputData(false, null);
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        if (playerSender == null || !hasPermission(playerSender)) {
            return CompletableFuture.completedFuture(new OutputData(false, null));
        }

        ServerPluginManager pluginManager = (ServerPluginManager) getPluginManager();
        if (pluginManager == null) {
            return CompletableFuture.completedFuture(new OutputData(false, null));
        }

        ServerPlugin plugin = pluginManager.getPlugins().get(input.instanceID());
        if (plugin == null) {
            return CompletableFuture.completedFuture(new OutputData(false, null));
        }

        boolean applied = plugin.applyCustomSettings(input.payload());
        byte[] confirmed = applied ? plugin.provideCustomSettings() : null;
        return CompletableFuture.completedFuture(new OutputData(applied, confirmed));
    }

    /**
     * Checks whether the player has op level 2 (required by /stockmarket manage).
     */
    private boolean hasPermission(UUID playerUUID) {
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server == null) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        return player != null && player.hasPermissions(2);
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
