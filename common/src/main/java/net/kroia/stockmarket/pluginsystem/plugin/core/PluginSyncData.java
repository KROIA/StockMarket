package net.kroia.stockmarket.pluginsystem.plugin.core;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import java.util.List;
import java.util.UUID;

/**
 * Network-transferable snapshot of a plugin's state.
 * Bundles the generic plugin metadata with its list of subscribed markets.
 * Used by PluginListRequest to sync server plugin state to the client.
 */
public class PluginSyncData {
    public static final StreamCodec<RegistryFriendlyByteBuf, PluginSyncData> STREAM_CODEC = StreamCodec.composite(
            GenericPluginData.STREAM_CODEC, p -> p.genericData,
            ExtraCodecUtils.listStreamCodec(ItemID.STREAM_CODEC), p -> p.subscribedMarkets,
            PluginSyncData::new
    );

    private final GenericPluginData genericData;
    private final List<ItemID> subscribedMarkets;

    public PluginSyncData(GenericPluginData genericData, List<ItemID> subscribedMarkets) {
        this.genericData = genericData;
        this.subscribedMarkets = subscribedMarkets;
    }

    public GenericPluginData getGenericData() { return genericData; }
    public List<ItemID> getSubscribedMarkets() { return subscribedMarkets; }
    public UUID getInstanceID() { return genericData.getInstanceID(); }
    public String getName() { return genericData.getName(); }
    public String getDescription() { return genericData.getDescription(); }
    public String getPluginTypeID() { return genericData.getPluginTypeID(); }
    public boolean isEnabled() { return genericData.isEnabled(); }
    public boolean isLoggerEnabled() { return genericData.isLoggerEnabled(); }
}
