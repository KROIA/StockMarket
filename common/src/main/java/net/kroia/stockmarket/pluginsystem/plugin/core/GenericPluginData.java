package net.kroia.stockmarket.pluginsystem.plugin.core;

import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;
import net.minecraft.core.UUIDUtil;
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
        }
        @Override
        public @NotNull GenericPluginData decode(RegistryFriendlyByteBuf buf) {
            String pluginTypeID = ByteBufCodecs.STRING_UTF8.decode(buf);
            UUID instanceID = UUIDUtil.STREAM_CODEC.decode(buf);
            String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            String description = ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean loggerEnabled = ByteBufCodecs.BOOL.decode(buf);
            boolean enabled = ByteBufCodecs.BOOL.decode(buf);
            return new GenericPluginData(pluginTypeID, instanceID,
                    name, description, loggerEnabled, enabled);
        }
    };

    private String pluginTypeID;
    private final UUID instanceID;
    private String name;
    private String description;
    private boolean loggerEnabled;
    private boolean enabled;


    private GenericPluginData(String pluginTypeID, UUID instanceID, String name,
                              String description, boolean loggerEnabled, boolean enabled)
    {
        this.pluginTypeID = pluginTypeID;
        this.instanceID = instanceID;
        this.name = name;
        this.description = description;
        this.loggerEnabled = loggerEnabled;
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

    /*protected final void setInstanceID(UUID id)
    {
        this.instanceID = id;
    }*/
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
}
