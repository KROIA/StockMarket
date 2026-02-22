package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.BotSettingsData;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class BotSettingsRequest extends StockMarketGenericRequest<TradingPair, BotSettingsData> {
    @Override
    public String getRequestTypeID() {
        return BotSettingsRequest.class.getName();
    }

    @Override
    public BotSettingsData handleOnClient(TradingPair input) {
        return null;
    }

    @Override
    public BotSettingsData handleOnServer(TradingPair input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            return BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getBotSettingsData(input);
        }
        return null; // If the player is not an admin, return null
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPair input) {
        new TradingPairData(input).encode(buf); // Encode the TradingPairData
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, BotSettingsData output) {
        buf.writeBoolean(output != null);
        if (output != null) {
            output.encode(buf); // Encode the BotSettingsData
        }
    }

    @Override
    public TradingPair decodeInput(FriendlyByteBuf buf) {
        return TradingPairData.decode(buf).toTradingPair(); // Decode the TradingPairData
    }

    @Override
    public BotSettingsData decodeOutput(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            return BotSettingsData.decode(buf); // Decode the BotSettingsData
        }
        return null; // If no data was encoded, return null
    }
}
