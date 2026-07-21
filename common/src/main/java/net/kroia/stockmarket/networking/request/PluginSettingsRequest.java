package net.kroia.stockmarket.networking.request;

import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
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
 * ARRS request to update the settings of a specific plugin instance on the server.
 * Input: PluginSettingsRequest.InputData containing the plugin's instanceID and the updated GenericPluginData.
 * Output: PluginSettingsRequest.OutputData indicating success/failure and the confirmed data from the server.
 */
public class PluginSettingsRequest extends StockMarketGenericRequest<PluginSettingsRequest.InputData, PluginSettingsRequest.OutputData> {

    /**
     * Input payload sent from the client to the server.
     * Contains the target plugin's instance ID and the updated settings values.
     *
     * @param instanceID   the UUID of the plugin instance to update
     * @param updatedData  the new settings values to apply
     */
    public record InputData(UUID instanceID, GenericPluginData updatedData) {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, InputData::instanceID,
                GenericPluginData.STREAM_CODEC, InputData::updatedData,
                InputData::new
        );
    }

    /**
     * Output payload returned from the server to the client.
     * Contains whether the update succeeded and the confirmed plugin data from the server.
     *
     * @param success        true if the settings were applied successfully
     * @param confirmedData  the plugin's confirmed GenericPluginData after the update, or null on failure
     */
    public record OutputData(boolean success, @Nullable GenericPluginData confirmedData) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, OutputData::success,
                ExtraCodecUtils.nullable(GenericPluginData.STREAM_CODEC), OutputData::confirmedData,
                OutputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return PluginSettingsRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(false, null);
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        // Require the StockMarket-admin flag (the same flag /stockmarket manage
        // requires), resolved from the master's user map so it also works for
        // players connected through a slave. Fail closed on a missing sender.
        if (playerSender == null || !playerIsAdmin(playerSender)) {
            return CompletableFuture.completedFuture(new OutputData(false, null));
        }
        // T-123 (untrusted slave gate): plugin settings are mutating.
        if (!NetworkGate.isMutatingCallAllowed(slaveID, "PluginSettingsRequest")) {
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

        // Apply the updated settings from the client
        GenericPluginData updated = input.updatedData();
        plugin.setEnabled(updated.isEnabled());
        plugin.setName(updated.getName());
        plugin.setDescription(updated.getDescription());
        plugin.setLoggerEnabled(updated.isLoggerEnabled());
        plugin.setAutoSubscribeNewMarkets(updated.getAutoSubscribeNewMarkets());
        plugin.setSubscriptionOrder(updated.getSubscriptionOrder());

        // Notify other admins about the settings change
        broadcastToAdmins(playerSender,
                getPlayerName(playerSender) + " updated settings for plugin '" + plugin.getName() + "'");

        return CompletableFuture.completedFuture(new OutputData(true, plugin.getGenericPluginData()));
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
