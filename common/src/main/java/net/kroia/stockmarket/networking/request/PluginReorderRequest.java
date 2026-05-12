package net.kroia.stockmarket.networking.request;

import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ARRS request to reorder a plugin in the execution order on the server.
 * Input: PluginReorderRequest.InputData containing the plugin's instanceID and the direction to move.
 * Output: The full updated list of PluginSyncData reflecting the new order.
 */
public class PluginReorderRequest extends StockMarketGenericRequest<PluginReorderRequest.InputData, List<PluginSyncData>> {

    /**
     * Input payload sent from the client to the server.
     * Contains the target plugin's instance ID and the direction to move it.
     *
     * @param instanceID  the UUID of the plugin instance to reorder
     * @param direction   -1 to move up (earlier in execution), +1 to move down (later)
     */
    public record InputData(UUID instanceID, int direction) {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, InputData::instanceID,
                ByteBufCodecs.INT, InputData::direction,
                InputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return PluginReorderRequest.class.getName();
    }

    @Override
    protected List<PluginSyncData> getDefaultResponse() {
        return List.of();
    }

    @Override
    public CompletableFuture<List<PluginSyncData>> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        ServerPluginManager pluginManager = (ServerPluginManager) getPluginManager();

        // Check op level 2 instead of stockmarket admin — the management screen
        // already requires op level 2, so the permission model must match.
        if (playerSender == null || !hasPermission(playerSender)) {
            return CompletableFuture.completedFuture(buildCurrentPluginList(pluginManager));
        }

        if (pluginManager == null) {
            return CompletableFuture.completedFuture(getDefaultResponse());
        }

        pluginManager.reorderPlugin(input.instanceID(), input.direction());

        // Notify other admins about the reorder
        broadcastToAdmins(playerSender,
                getPlayerName(playerSender) + " reordered plugins");

        return CompletableFuture.completedFuture(buildCurrentPluginList(pluginManager));
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

    /**
     * Builds the full plugin list from the server. Returns an empty list if
     * the plugin manager is null (should not happen on master).
     */
    private List<PluginSyncData> buildCurrentPluginList(@Nullable ServerPluginManager pluginManager) {
        if (pluginManager == null) {
            return List.of();
        }
        List<PluginSyncData> list = new ArrayList<>();
        for (ServerPlugin plugin : pluginManager.getPlugins().values()) {
            list.add(PluginSyncData.fromServerPlugin(plugin));
        }
        return list;
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, List<PluginSyncData> output) {
        ExtraCodecUtils.listStreamCodec(PluginSyncData.STREAM_CODEC).encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public List<PluginSyncData> decodeOutput(RegistryFriendlyByteBuf buf) {
        return ExtraCodecUtils.listStreamCodec(PluginSyncData.STREAM_CODEC).decode(buf);
    }
}
