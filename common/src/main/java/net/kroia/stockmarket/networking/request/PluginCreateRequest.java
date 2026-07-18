package net.kroia.stockmarket.networking.request;

import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistry;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ARRS request to create a new plugin instance on the server.
 * Input: the plugin type ID (registry key) to instantiate.
 * Output: whether the creation succeeded.
 */
public class PluginCreateRequest extends StockMarketGenericRequest<String, Boolean> {

    @Override
    public String getRequestTypeID() {
        return PluginCreateRequest.class.getName();
    }

    @Override
    protected Boolean getDefaultResponse() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> handleOnMasterServer(String pluginTypeID, String slaveID, @Nullable UUID playerSender) {
        // Require the StockMarket-admin flag (the same flag /stockmarket manage
        // requires), resolved from the master's user map so it also works for
        // players connected through a slave. Fail closed on a missing sender.
        if (playerSender == null || !playerIsAdmin(playerSender)) {
            return CompletableFuture.completedFuture(false);
        }
        // T-123 (untrusted slave gate): plugin management is mutating.
        if (!NetworkGate.isMutatingCallAllowed(slaveID, "PluginCreateRequest")) {
            return CompletableFuture.completedFuture(false);
        }

        ServerPluginManager pluginManager = (ServerPluginManager) getPluginManager();
        if (pluginManager == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Look up the plugin type in the registry
        PluginRegistryObject registryObject = PluginRegistry.findPlugin(pluginTypeID);
        if (registryObject == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Create the plugin instance
        ServerPlugin plugin = pluginManager.addPlugin(registryObject);

        // Notify other admins about the new plugin
        if (plugin != null) {
            broadcastToAdmins(playerSender,
                    getPlayerName(playerSender) + " created plugin '" + plugin.getName() + "'");
        }

        return CompletableFuture.completedFuture(plugin != null);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, String input) {
        ByteBufCodecs.STRING_UTF8.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, Boolean output) {
        ByteBufCodecs.BOOL.encode(buf, output);
    }

    @Override
    public String decodeInput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.STRING_UTF8.decode(buf);
    }

    @Override
    public Boolean decodeOutput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.BOOL.decode(buf);
    }
}
