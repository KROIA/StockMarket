package net.kroia.stockmarket.networking.request;

import net.kroia.modutilities.UtilitiesPlatform;
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
 * ARRS request to delete a plugin instance on the server.
 * Input: the UUID of the plugin instance to delete.
 * Output: whether the deletion succeeded.
 */
public class PluginDeleteRequest extends StockMarketGenericRequest<UUID, Boolean> {

    @Override
    public String getRequestTypeID() {
        return PluginDeleteRequest.class.getName();
    }

    @Override
    protected Boolean getDefaultResponse() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> handleOnMasterServer(UUID instanceID, String slaveID, @Nullable UUID playerSender) {
        if (playerSender == null || !hasPermission(playerSender)) {
            return CompletableFuture.completedFuture(false);
        }

        ServerPluginManager pluginManager = (ServerPluginManager) getPluginManager();
        if (pluginManager == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Find the plugin by instance ID
        ServerPlugin plugin = pluginManager.getPlugins().get(instanceID);
        if (plugin == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Capture the name before deletion for the broadcast message
        String pluginName = plugin.getName();

        // Remove the plugin
        pluginManager.removePlugin(plugin);

        // Notify other admins about the deletion
        broadcastToAdmins(playerSender,
                getPlayerName(playerSender) + " deleted plugin '" + pluginName + "'");

        return CompletableFuture.completedFuture(true);
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
    public void encodeInput(RegistryFriendlyByteBuf buf, UUID input) {
        UUIDUtil.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, Boolean output) {
        ByteBufCodecs.BOOL.encode(buf, output);
    }

    @Override
    public UUID decodeInput(RegistryFriendlyByteBuf buf) {
        return UUIDUtil.STREAM_CODEC.decode(buf);
    }

    @Override
    public Boolean decodeOutput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.BOOL.decode(buf);
    }
}
