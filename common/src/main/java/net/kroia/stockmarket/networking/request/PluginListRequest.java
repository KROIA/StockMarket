package net.kroia.stockmarket.networking.request;

import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ARRS request to fetch the full list of plugins from the server.
 * Input: Integer (placeholder, unused).
 * Output: List of PluginSyncData containing each plugin's metadata and subscribed markets.
 */
public class PluginListRequest extends StockMarketGenericRequest<Integer, List<PluginSyncData>> {

    @Override
    public String getRequestTypeID() {
        return PluginListRequest.class.getName();
    }

    @Override
    protected List<PluginSyncData> getDefaultResponse() {
        return List.of();
    }

    @Override
    public CompletableFuture<List<PluginSyncData>> handleOnMasterServer(Integer input, String slaveID, UUID playerSender) {
        ServerPluginManager pluginManager = (ServerPluginManager) getPluginManager();
        List<PluginSyncData> list = new ArrayList<>();
        if(pluginManager != null) {
            for (ServerPlugin plugin : pluginManager.getPlugins().values()) {
                list.add(PluginSyncData.fromServerPlugin(plugin));
            }
        }
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, Integer input) {
        ByteBufCodecs.INT.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, List<PluginSyncData> output) {
        ExtraCodecUtils.listStreamCodec(PluginSyncData.STREAM_CODEC).encode(buf, output);
    }

    @Override
    public Integer decodeInput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.INT.decode(buf);
    }

    @Override
    public List<PluginSyncData> decodeOutput(RegistryFriendlyByteBuf buf) {
        return ExtraCodecUtils.listStreamCodec(PluginSyncData.STREAM_CODEC).decode(buf);
    }
}
