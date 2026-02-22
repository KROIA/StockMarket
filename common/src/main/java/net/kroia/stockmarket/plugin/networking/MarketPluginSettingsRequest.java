package net.kroia.stockmarket.plugin.networking;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.MarketPlugin;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public class MarketPluginSettingsRequest extends StockMarketGenericRequest<MarketPluginSettingsRequest.Input, FriendlyByteBuf> {


    public static class Input
    {
        public TradingPair tradingPair;
        public String pluginTypeID;
        @Nullable
        public FriendlyByteBuf settingsBuf;

        /**
         *
         * @param tradingPair
         * @param pluginTypeID
         * @param settingsBuf if set to null, the provided settings will not be changed, but the current settings will be returned
         */
        public Input(TradingPair tradingPair, String pluginTypeID, @Nullable FriendlyByteBuf settingsBuf)
        {
            this.tradingPair = tradingPair;
            this.pluginTypeID = pluginTypeID;
            this.settingsBuf = settingsBuf;
        }
    }

    @Override
    public String getRequestTypeID() {
        return MarketPluginSettingsRequest.class.getSimpleName();
    }



    @Override
    public FriendlyByteBuf handleOnServer(Input input, ServerPlayer sender) {
        if(playerIsAdmin(sender))
        {
            MarketPlugin plugin = BACKEND_INSTANCES.SERVER_PLUGIN_MANAGER.getMarketPlugin(input.tradingPair, input.pluginTypeID);
            if(plugin != null)
            {
                if(input.settingsBuf != null)
                    plugin.decodeSettings_internal(input.settingsBuf);
                FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                plugin.encodeSettings_internal(buf);
                return buf;
            }
        }
        return null;
    }

    @Override
    public Input decodeInput(FriendlyByteBuf buf) {
        TradingPair tradingPair = new TradingPair(buf);
        String pluginTypeID = buf.readUtf();
        boolean hasSettings = buf.readBoolean();
        if(!hasSettings)
            return new Input(tradingPair, pluginTypeID, null);
        int size = buf.readInt();
        FriendlyByteBuf settingsBuf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.readBytes(settingsBuf, size);
        return new Input(tradingPair, pluginTypeID, settingsBuf);
    }
    @Override
    public void encodeInput(FriendlyByteBuf buf, Input input) {
        input.tradingPair.encode(buf);
        buf.writeUtf(input.pluginTypeID);
        buf.writeBoolean(input.settingsBuf != null);
        if(input.settingsBuf != null) {
            buf.writeInt(input.settingsBuf.readableBytes());
            buf.writeBytes(input.settingsBuf);
        }
    }


    @Override
    public void encodeOutput(FriendlyByteBuf buf, FriendlyByteBuf output) {
        buf.writeBoolean(output != null);
        if (output != null) {
            buf.writeInt(output.readableBytes());
            buf.writeBytes(output);
        }
    }




    @Override
    public FriendlyByteBuf decodeOutput(FriendlyByteBuf buf) {
        if(buf.readBoolean())
        {
            int size = buf.readInt();
            FriendlyByteBuf output = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            buf.readBytes(output, size);
            return output;
        }
        return null;
    }
}
