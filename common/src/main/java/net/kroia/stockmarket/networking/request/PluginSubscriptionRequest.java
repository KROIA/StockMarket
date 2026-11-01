package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
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
 * ARRS request to subscribe or unsubscribe a plugin to/from a market.
 * Input: plugin instanceID, market ItemID, and subscribe flag.
 * Output: success flag and updated plugin sync data.
 */
public class PluginSubscriptionRequest extends StockMarketGenericRequest<PluginSubscriptionRequest.InputData, PluginSubscriptionRequest.OutputData> {

    /**
     * @param instanceID the UUID of the plugin instance
     * @param marketID   the market to subscribe/unsubscribe
     * @param subscribe  true to subscribe, false to unsubscribe
     */
    public record InputData(UUID instanceID, ItemID marketID, boolean subscribe) {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, InputData::instanceID,
                ItemID.STREAM_CODEC, InputData::marketID,
                ByteBufCodecs.BOOL, InputData::subscribe,
                InputData::new
        );
    }

    /**
     * @param success       true if the subscription change was applied
     * @param updatedPlugin the plugin's updated sync data after the change, or null on failure
     */
    public record OutputData(boolean success, @Nullable PluginSyncData updatedPlugin) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, OutputData::success,
                ExtraCodecUtils.nullable(PluginSyncData.STREAM_CODEC), OutputData::updatedPlugin,
                OutputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return PluginSubscriptionRequest.class.getName();
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
        // T-123 (untrusted slave gate): subscribe/unsubscribe changes plugin state.
        if (!NetworkGate.isMutatingCallAllowed(slaveID, "PluginSubscriptionRequest")) {
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

        if (input.subscribe()) {
            plugin.subscribeToMarket(input.marketID());
            // Verify subscription succeeded
            if (!plugin.getSubscribedMarkets().contains(input.marketID())) {
                return CompletableFuture.completedFuture(new OutputData(false, PluginSyncData.fromServerPlugin(plugin)));
            }
        } else {
            plugin.unsubscribeFromMarket(input.marketID());
            // Verify unsubscription succeeded
            if (plugin.getSubscribedMarkets().contains(input.marketID())) {
                return CompletableFuture.completedFuture(new OutputData(false, PluginSyncData.fromServerPlugin(plugin)));
            }
        }

        // Notify other admins about the subscription change
        String action = input.subscribe() ? "subscribed" : "unsubscribed";
        broadcastToAdmins(playerSender,
                getPlayerName(playerSender) + " " + action + " '" + plugin.getName() + "' "
                        + (input.subscribe() ? "to" : "from") + " " + input.marketID());

        return CompletableFuture.completedFuture(new OutputData(true, PluginSyncData.fromServerPlugin(plugin)));
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
