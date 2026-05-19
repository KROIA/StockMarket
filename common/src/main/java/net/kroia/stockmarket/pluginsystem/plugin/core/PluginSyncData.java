package net.kroia.stockmarket.pluginsystem.plugin.core;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Network-transferable snapshot of a plugin's state.
 * Bundles the generic plugin metadata, subscribed markets, and optional custom settings.
 * Used by PluginListRequest to sync server plugin state to the client.
 */
public class PluginSyncData {
    public static final StreamCodec<RegistryFriendlyByteBuf, PluginSyncData> STREAM_CODEC = StreamCodec.composite(
            GenericPluginData.STREAM_CODEC, p -> p.genericData,
            ExtraCodecUtils.listStreamCodec(ItemID.STREAM_CODEC), p -> p.subscribedMarkets,
            ExtraCodecUtils.nullable(ExtraCodecUtils.mapStreamCodec(ItemID.STREAM_CODEC, ByteBufCodecs.BYTE_ARRAY, HashMap::new)), p -> p.customSettingsMap,
            PluginSyncData::new
    );

    private final GenericPluginData genericData;
    private final List<ItemID> subscribedMarkets;
    private final @Nullable Map<ItemID, byte[]> customSettingsMap;

    public PluginSyncData(GenericPluginData genericData, List<ItemID> subscribedMarkets, @Nullable Map<ItemID, byte[]> customSettingsMap) {
        this.genericData = genericData;
        this.subscribedMarkets = subscribedMarkets;
        this.customSettingsMap = customSettingsMap;
    }

    /**
     * Creates a PluginSyncData snapshot from a server-side plugin instance.
     *
     * @param plugin the server plugin to snapshot
     * @return a new PluginSyncData with current generic data, subscriptions, and custom settings
     */
    public static PluginSyncData fromServerPlugin(ServerPlugin<?, ?> plugin) {
        return new PluginSyncData(plugin.getGenericPluginData(), plugin.getSubscribedMarkets(), plugin.encodeAllCustomSettings());
    }

    public GenericPluginData getGenericData() { return genericData; }
    public List<ItemID> getSubscribedMarkets() { return subscribedMarkets; }
    public @Nullable Map<ItemID, byte[]> getCustomSettings() { return customSettingsMap; }
    public UUID getInstanceID() { return genericData.getInstanceID(); }
    public String getName() { return genericData.getName(); }
    public String getDescription() { return genericData.getDescription(); }
    public String getPluginTypeID() { return genericData.getPluginTypeID(); }
    public boolean isEnabled() { return genericData.isEnabled(); }
    public boolean isLoggerEnabled() { return genericData.isLoggerEnabled(); }
}
