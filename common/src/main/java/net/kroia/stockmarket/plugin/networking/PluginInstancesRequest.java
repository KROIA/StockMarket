package net.kroia.stockmarket.plugin.networking;

import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.ServerPlugin;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PluginInstancesRequest extends StockMarketGenericRequest<Boolean, List<PluginInstancesRequest.PluginInstanceData>>
{


    public static class PluginInstanceData implements INetworkPayloadConverter
    {
        public String typeID;
        public UUID instanceID;
        public boolean enabled;
        public List<TradingPair> subscribedMarkets = new ArrayList<>();

        public PluginInstanceData(FriendlyByteBuf buf)
        {
            decode(buf);
        }
        public PluginInstanceData(String typeID,
                                  UUID instanceID,
                                  boolean enabled,
                                  List<TradingPair> subscribedMarkets)
        {
            this.typeID = typeID;
            this.instanceID = instanceID;
            this.enabled = enabled;
            this.subscribedMarkets = subscribedMarkets;
        }

        @Override
        public void decode(FriendlyByteBuf buf) {
            typeID = buf.readUtf();
            instanceID = buf.readUUID();
            enabled = buf.readBoolean();
            if(subscribedMarkets == null)
            {
                subscribedMarkets = new ArrayList<>();
            }
            else  {
                subscribedMarkets.clear();
            }
            int marketCount = buf.readInt();
            for(int i = 0; i < marketCount; i++)
            {
                subscribedMarkets.add(new TradingPair(buf));
            }
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(typeID);
            buf.writeUUID(instanceID);
            buf.writeBoolean(enabled);
            buf.writeInt(subscribedMarkets.size());

            for(TradingPair pair : subscribedMarkets)
            {
                pair.encode(buf);
            }
        }
    }


    @Override
    public String getRequestTypeID() {
        return PluginInstancesRequest.class.getName();
    }


    public List<PluginInstanceData> handleOnServer(Boolean input, ServerPlayer sender)
    {
        List<PluginInstanceData>  result = new ArrayList<>();
        if(playerIsAdmin(sender))
        {
            Map<UUID, ServerPlugin> plugins = BACKEND_INSTANCES.SERVER_PLUGIN_MANAGER.getPlugins();
            for(Map.Entry<UUID, ServerPlugin> entry : plugins.entrySet())
            {
                ServerPlugin  plugin = entry.getValue();
                PluginInstanceData data = new PluginInstanceData(
                        plugin.getPluginTypeID(),
                        plugin.getInstanceID(),
                        plugin.isEnabled(),
                        plugin.getSubscribedMarkets());
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
    public void encodeOutput(FriendlyByteBuf buf, List<PluginInstanceData> output) {
        buf.writeInt(output.size());
        for(PluginInstanceData data : output)
            data.encode(buf);
    }

    @Override
    public Boolean decodeInput(FriendlyByteBuf buf) {
        return buf.readBoolean();
    }

    @Override
    public List<PluginInstanceData> decodeOutput(FriendlyByteBuf buf) {
        List<PluginInstanceData> output = new ArrayList<>();
        int size = buf.readInt();
        for(int i = 0; i < size; i++)
        {
            output.add(new PluginInstanceData(buf));
        }
        return output;
    }
}
