package net.kroia.stockmarket.plugin.networking;

import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.stockmarket.plugin.PluginRegistry;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class PluginTypesRequest extends StockMarketGenericRequest<Integer, PluginTypesRequest.ResponseData> {

    public static class PluginInfo implements INetworkPayloadConverter
    {
        public String pluginTypeID;
        public String displayName;
        public String description;
        public PluginInfo(String pluginTypeID, String displayName, String description)
        {
            this.pluginTypeID = pluginTypeID;
            this.displayName = displayName;
            this.description = description;
        }
        private PluginInfo()
        {

        }
        public static PluginInfo fromBuf(FriendlyByteBuf buf)
        {
            PluginInfo info = new PluginInfo();
            info.decode(buf);
            return info;
        }


        @Override
        public void decode(FriendlyByteBuf buf) {
            pluginTypeID = buf.readUtf();
            displayName = buf.readUtf();
            description = buf.readUtf();
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(pluginTypeID);
            buf.writeUtf(displayName);
            buf.writeUtf(description);
        }
    }

    public static class ResponseData implements INetworkPayloadConverter
    {
        public List<PluginInfo> marketPlugins;
        //public List<PluginInfo> globalPlugins;


        public ResponseData(List<PluginInfo> marketPlugins/*, List<PluginInfo> globalPlugins*/)
        {
            this.marketPlugins = marketPlugins;
         //   this.globalPlugins = globalPlugins;
        }
        private ResponseData()
        {

        }
        public static ResponseData fromBuf(FriendlyByteBuf buf)
        {
            ResponseData data = new ResponseData();
            data.decode(buf);
            return data;
        }
        @Override
        public void decode(FriendlyByteBuf buf) {
            int marketSize = buf.readInt();
            marketPlugins = new java.util.ArrayList<>(marketSize);
            for(int i = 0; i < marketSize; i++)
            {
                marketPlugins.add(PluginInfo.fromBuf(buf));
            }
            /*int globalSize = buf.readInt();
            globalPlugins = new java.util.ArrayList<>(globalSize);
            for(int i = 0; i < globalSize; i++)
            {
                globalPlugins.add(PluginInfo.fromBuf(buf));
            }*/
        }

        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(marketPlugins.size());
            for(PluginInfo info : marketPlugins)
            {
                info.encode(buf);
            }
            /*buf.writeInt(globalPlugins.size());
            for(PluginInfo info : globalPlugins)
            {
                info.encode(buf);
            }*/
        }
    }

    @Override
    public String getRequestTypeID() {
        return PluginTypesRequest.class.getSimpleName();
    }

    public ResponseData handleOnServer(Integer input, ServerPlayer sender) {
        List<PluginInfo> marketPlugins = new java.util.ArrayList<>();
        //List<PluginInfo> globalPlugins = new java.util.ArrayList<>();

        for(var plugin : PluginRegistry.getRegisteredMarketPlugins().values())
        {
            marketPlugins.add(new PluginInfo(plugin.pluginTypeID, plugin.name, plugin.description));
        }
        /*
        for(var plugin : PluginRegistry.getRegisteredGlobalPlugins().values())
        {
            globalPlugins.add(new PluginInfo(plugin.pluginTypeID, plugin.name, plugin.description));
        }*/
        return new ResponseData(marketPlugins/*, globalPlugins*/);
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, Integer input) {
        buf.writeInt(input);
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, ResponseData output) {
        output.encode(buf);
    }

    @Override
    public Integer decodeInput(FriendlyByteBuf buf) {
        return buf.readInt();
    }

    @Override
    public ResponseData decodeOutput(FriendlyByteBuf buf) {
        return ResponseData.fromBuf(buf);
    }


}
