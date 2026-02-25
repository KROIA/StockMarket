package net.kroia.stockmarket.plugin.networking;

import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.stockmarket.plugin.base.PluginRegistry;
import net.kroia.stockmarket.plugin.base.PluginRegistryObject;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PluginTypesRequest extends StockMarketGenericRequest<Boolean, List<PluginTypesRequest.PluginTypeData>>
{
    public static class PluginTypeData implements INetworkPayloadConverter
    {
        public String typeID;
        public String name;
        public String description;

        public PluginTypeData(String typeID, String name, String description)
        {
            this.typeID = typeID;
            this.name = name;
            this.description = description;
        }
        public PluginTypeData(FriendlyByteBuf buf)
        {
            decode(buf);
        }

        @Override
        public void encode(FriendlyByteBuf buf)
        {
            buf.writeUtf(typeID);
            buf.writeUtf(name);
            buf.writeUtf(description);
        }

        @Override
        public void decode(FriendlyByteBuf buf)
        {
            typeID = buf.readUtf();
            name = buf.readUtf();
            description = buf.readUtf();
        }


    }


    @Override
    public String getRequestTypeID() {
        return PluginTypesRequest.class.getName();
    }

    public List<PluginTypesRequest.PluginTypeData> handleOnServer(Boolean input, ServerPlayer sender)
    {
        List<PluginTypesRequest.PluginTypeData>  result = new ArrayList<>();
        if(playerIsAdmin(sender))
        {
            Map<String, PluginRegistryObject> registeredPlugins = PluginRegistry.getRegistryObjects();
            for(Map.Entry<String, PluginRegistryObject> entry : registeredPlugins.entrySet())
            {
                PluginRegistryObject registryObject = entry.getValue();
                PluginTypesRequest.PluginTypeData data = new PluginTypesRequest.PluginTypeData(
                        registryObject.getPluginTypeID(),
                        registryObject.getPluginName(),
                        registryObject.getPluginDescription());
                result.add(data);
            }
        }
        return result;
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, Boolean input) {
        buf.writeBoolean(input);
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, List<PluginTypeData> output) {
        buf.writeInt(output.size());
        for (PluginTypeData pluginTypeData : output) {
            pluginTypeData.encode(buf);
        }
    }

    @Override
    public Boolean decodeInput(FriendlyByteBuf buf) {
        return buf.readBoolean();
    }

    @Override
    public List<PluginTypeData> decodeOutput(FriendlyByteBuf buf) {
        List<PluginTypeData> output = new ArrayList<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            output.add(new PluginTypeData(buf));
        }
        return output;
    }

}
