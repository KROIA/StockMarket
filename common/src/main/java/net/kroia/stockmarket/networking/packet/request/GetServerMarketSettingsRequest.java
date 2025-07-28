package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.clientdata.ServerMarketSettingsData;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class GetServerMarketSettingsRequest extends StockMarketGenericRequest<TradingPairData, ServerMarketSettingsData> {
    @Override
    public String getRequestTypeID() {
        return GetServerMarketSettingsRequest.class.getName();
    }

    @Override
    public ServerMarketSettingsData handleOnClient(TradingPairData input) {
        return null;
    }

    @Override
    public ServerMarketSettingsData handleOnServer(TradingPairData input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            // If the player has admin permissions, return the market settings data
            return BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getMarketSettingsData(input.toTradingPair());
        }
        return null; // If not, return null or handle accordingly
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPairData input) {
        input.encode(buf); // Encode the TradingPairData
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, ServerMarketSettingsData output) {
        buf.writeBoolean(output != null);
        if (output != null) {
            output.encode(buf); // Encode the ServerMarketSettingsData
        }
    }

    @Override
    public TradingPairData decodeInput(FriendlyByteBuf buf) {
        return TradingPairData.decode(buf); // Decode the TradingPairData
    }

    @Override
    public ServerMarketSettingsData decodeOutput(FriendlyByteBuf buf) {
        return buf.readBoolean() ? ServerMarketSettingsData.decode(buf) : null; // Decode the ServerMarketSettingsData
    }
}
