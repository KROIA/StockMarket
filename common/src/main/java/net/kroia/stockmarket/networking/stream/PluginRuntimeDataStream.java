package net.kroia.stockmarket.networking.stream;

import net.kroia.modutilities.networking.client_server.streaming.GenericStream;
import net.kroia.stockmarket.api.pluginmanager.IServerPluginManager;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Arrays;
import java.util.UUID;

/**
 * Server-to-client stream that delivers live runtime data from a {@link ServerPlugin}
 * to the corresponding client-side GUI element.
 * <p>
 * Context data (IN): {@link UUID} -- the plugin instance ID to stream data for.
 * Stream data (OUT): {@link RuntimeData} -- the runtime payload.
 * <p>
 * Follows the same pattern as {@link MarketPriceStream}.
 */
public class PluginRuntimeDataStream extends StockMarketGenericStream<UUID, PluginRuntimeDataStream.RuntimeData> {

    /**
     * Runtime data payload streamed from a server plugin to the client.
     */
    public static class RuntimeData {
        public static final StreamCodec<RegistryFriendlyByteBuf, RuntimeData> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public void encode(RegistryFriendlyByteBuf buf, RuntimeData data) {
                UUIDUtil.STREAM_CODEC.encode(buf, data.pluginInstanceID);
                ByteBufCodecs.VAR_INT.encode(buf, data.payload.length);
                buf.writeBytes(data.payload);
            }

            @Override
            public RuntimeData decode(RegistryFriendlyByteBuf buf) {
                UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
                int length = ByteBufCodecs.VAR_INT.decode(buf);
                byte[] payload = new byte[length];
                buf.readBytes(payload);
                return new RuntimeData(id, payload);
            }
        };

        public final UUID pluginInstanceID;
        public final byte[] payload;

        public RuntimeData(UUID pluginInstanceID, byte[] payload) {
            this.pluginInstanceID = pluginInstanceID;
            this.payload = payload;
        }
    }

    private UUID pluginInstanceID;
    private long updateInterval = 500;
    private long lastTimeMs = System.currentTimeMillis();
    private byte[] lastPayload = new byte[0];

    @Override
    public GenericStream<UUID, RuntimeData> copy() {
        return new PluginRuntimeDataStream();
    }

    @Override
    public String getStreamTypeID() {
        return PluginRuntimeDataStream.class.getName();
    }

    @Override
    public void onStartStreamSendingOnSever() {
        pluginInstanceID = getContextData();
        // Look up the plugin to get its stream interval
        IServerPluginManager mgr = getPluginManager();
        if (mgr instanceof ServerPluginManager serverMgr) {
            ServerPlugin plugin = serverMgr.getPlugins().get(pluginInstanceID);
            if (plugin != null) {
                updateInterval = plugin.getRuntimeDataStreamInterval();
            }
        }
    }

    @Override
    public void onStopStreamSendingOnServer() {
        info("PluginRuntimeDataStream stopped for plugin: " + pluginInstanceID);
    }

    @Override
    protected void updateOnServer() {
        long now = System.currentTimeMillis();
        if (now - lastTimeMs < updateInterval) return;
        lastTimeMs = now;

        IServerPluginManager mgr = getPluginManager();
        if (mgr == null) {
            stopStream();
            return;
        }

        ServerPlugin plugin = null;
        if (mgr instanceof ServerPluginManager serverMgr) {
            plugin = serverMgr.getPlugins().get(pluginInstanceID);
        }

        if (plugin == null) {
            stopStream();
            return;
        }

        byte[] newPayload = plugin.provideRuntimeData();
        if (newPayload == null) return;

        // Only send if data changed
        if (!Arrays.equals(newPayload, lastPayload)) {
            lastPayload = newPayload;
            sendPacket();
        }
    }

    @Override
    public RuntimeData provideStreamPacketOnServer() {
        return new RuntimeData(pluginInstanceID, lastPayload);
    }

    @Override
    public void encodeContextData(RegistryFriendlyByteBuf buf, UUID contextData) {
        UUIDUtil.STREAM_CODEC.encode(buf, contextData);
    }

    @Override
    public UUID decodeContextData(RegistryFriendlyByteBuf buf) {
        return UUIDUtil.STREAM_CODEC.decode(buf);
    }

    @Override
    public void encodeData(RegistryFriendlyByteBuf buf, RuntimeData data) {
        RuntimeData.STREAM_CODEC.encode(buf, data);
    }

    @Override
    public RuntimeData decodeData(RegistryFriendlyByteBuf buf) {
        return RuntimeData.STREAM_CODEC.decode(buf);
    }
}
