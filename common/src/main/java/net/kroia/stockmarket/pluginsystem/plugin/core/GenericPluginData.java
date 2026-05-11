package net.kroia.stockmarket.pluginsystem.plugin.core;

import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class GenericPluginData {

    public static StreamCodec<RegistryFriendlyByteBuf, GenericPluginData> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, GenericPluginData>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, GenericPluginData data) {
            ByteBufCodecs.STRING_UTF8.encode(buf, data.pluginTypeID);
            UUIDUtil.STREAM_CODEC.encode(buf, data.instanceID);
            ByteBufCodecs.STRING_UTF8.encode(buf, data.name);
            ByteBufCodecs.STRING_UTF8.encode(buf, data.description);
            ByteBufCodecs.BOOL.encode(buf, data.loggerEnabled);
            ByteBufCodecs.BOOL.encode(buf, data.enabled);
            ByteBufCodecs.BOOL.encode(buf, data.autoSubscribeNewMarkets);
            ByteBufCodecs.VAR_INT.encode(buf, data.subscriptionOrder);
        }
        @Override
        public @NotNull GenericPluginData decode(RegistryFriendlyByteBuf buf) {
            String pluginTypeID = ByteBufCodecs.STRING_UTF8.decode(buf);
            UUID instanceID = UUIDUtil.STREAM_CODEC.decode(buf);
            String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            String description = ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean loggerEnabled = ByteBufCodecs.BOOL.decode(buf);
            boolean enabled = ByteBufCodecs.BOOL.decode(buf);
            boolean autoSubscribeNewMarkets = ByteBufCodecs.BOOL.decode(buf);
            int subscriptionOrder = ByteBufCodecs.VAR_INT.decode(buf);
            return new GenericPluginData(pluginTypeID, instanceID,
                    name, description, loggerEnabled, enabled,
                    autoSubscribeNewMarkets, subscriptionOrder);
        }
    };

    private String pluginTypeID;
    private UUID instanceID;
    private String name;
    private String description;
    private boolean loggerEnabled;
    private boolean enabled;
    private boolean autoSubscribeNewMarkets = true;
    private int subscriptionOrder = 0;


    private GenericPluginData(String pluginTypeID, UUID instanceID, String name,
                              String description, boolean loggerEnabled, boolean enabled,
                              boolean autoSubscribeNewMarkets, int subscriptionOrder)
    {
        this.pluginTypeID = pluginTypeID;
        this.instanceID = instanceID;
        this.name = name;
        this.description = description;
        this.loggerEnabled = loggerEnabled;
        this.enabled = enabled;
        this.autoSubscribeNewMarkets = autoSubscribeNewMarkets;
        this.subscriptionOrder = subscriptionOrder;
    }
    public GenericPluginData(UUID instanceID)
    {
        this.instanceID = instanceID;
        loggerEnabled = false;
        enabled = false;
    }


    public void setLoggerEnabled(boolean enabled)
    {
        this.loggerEnabled = enabled;
    }
    public boolean isLoggerEnabled()
    {
        return loggerEnabled;
    }

    public void setName(String name)
    {
        this.name = name;
    }
    public String getName()
    {
        return name;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }
    public String getDescription()
    {
        return description;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
    public final boolean isEnabled()
    {
        return enabled;
    }

    public void setAutoSubscribeNewMarkets(boolean autoSubscribe)
    {
        this.autoSubscribeNewMarkets = autoSubscribe;
    }
    public boolean getAutoSubscribeNewMarkets()
    {
        return autoSubscribeNewMarkets;
    }

    public void setSubscriptionOrder(int order)
    {
        this.subscriptionOrder = order;
    }
    public int getSubscriptionOrder()
    {
        return subscriptionOrder;
    }

    public void setInstanceID(UUID id) {
        this.instanceID = id;
    }
    public final UUID getInstanceID()
    {
        return instanceID;
    }
    public final void setRegistrar(PluginRegistryObject registrar)
    {
        if(registrar != null)
        {
            pluginTypeID = registrar.getPluginTypeID();
            name = registrar.getPluginName();
            description = registrar.getPluginDescription();
        }
        else
        {
            pluginTypeID = null;
            name = "";
            description = "";
        }
    }
    public final @Nullable String getPluginTypeID()
    {
        return pluginTypeID;
    }

    /**
     * Saves the generic plugin data to an NBT compound tag.
     *
     * @param tag the compound tag to save into
     * @return true if the save succeeded
     */
    public boolean save(CompoundTag tag) {
        if (pluginTypeID != null) tag.putString("pluginTypeID", pluginTypeID);
        tag.putUUID("instanceID", instanceID);
        if (name != null) tag.putString("name", name);
        if (description != null) tag.putString("description", description);
        tag.putBoolean("loggerEnabled", loggerEnabled);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("autoSubscribeNewMarkets", autoSubscribeNewMarkets);
        tag.putInt("subscriptionOrder", subscriptionOrder);
        return true;
    }

    /**
     * Loads the generic plugin data from an NBT compound tag.
     * Requires at least the instanceID key to be present.
     *
     * @param tag the compound tag to load from
     * @return true if the load succeeded, false if instanceID is missing
     */
    public boolean load(CompoundTag tag) {
        if (!tag.contains("instanceID")) {
            return false;
        }
        instanceID = tag.getUUID("instanceID");
        if (tag.contains("pluginTypeID")) pluginTypeID = tag.getString("pluginTypeID");
        if (tag.contains("name")) name = tag.getString("name");
        if (tag.contains("description")) description = tag.getString("description");
        if (tag.contains("loggerEnabled")) loggerEnabled = tag.getBoolean("loggerEnabled");
        if (tag.contains("enabled")) enabled = tag.getBoolean("enabled");
        if (tag.contains("autoSubscribeNewMarkets")) autoSubscribeNewMarkets = tag.getBoolean("autoSubscribeNewMarkets");
        if (tag.contains("subscriptionOrder")) subscriptionOrder = tag.getInt("subscriptionOrder");
        return true;
    }
}
