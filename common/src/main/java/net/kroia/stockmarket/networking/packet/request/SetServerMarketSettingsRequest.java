package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.clientdata.ServerMarketSettingsData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class SetServerMarketSettingsRequest extends StockMarketGenericRequest<ServerMarketSettingsData, Boolean> {
    @Override
    public String getRequestTypeID() {
        return SetServerMarketSettingsRequest.class.getName();
    }

    @Override
    public Boolean handleOnClient(ServerMarketSettingsData input) {
        return null;
    }

    @Override
    public Boolean handleOnServer(ServerMarketSettingsData input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            // If the player has admin permissions, set the market settings
            return BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.setMarketSettingsData(input.tradingPairData.toTradingPair(), input);
        }
        return false;
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, ServerMarketSettingsData input) {
        input.encode(buf); // Encode the ServerMarketSettingsData
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Boolean output) {
        buf.writeBoolean(output != null && output); // Encode the Boolean output
    }

    @Override
    public ServerMarketSettingsData decodeInput(FriendlyByteBuf buf) {
        return ServerMarketSettingsData.decode(buf); // Decode the ServerMarketSettingsData
    }

    @Override
    public Boolean decodeOutput(FriendlyByteBuf buf) {
        return buf.readBoolean(); // Decode the Boolean output
    }
}
