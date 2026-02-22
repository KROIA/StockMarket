package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class DefaultMarketSetupDataGroupRequest extends StockMarketGenericRequest<String, MarketFactory.DefaultMarketSetupDataGroup> {
    @Override
    public String getRequestTypeID() {
        return DefaultMarketSetupDataGroupRequest.class.getSimpleName();
    }

    @Override
    public MarketFactory.DefaultMarketSetupDataGroup handleOnClient(String input) {
        return null;
    }

    @Override
    public MarketFactory.DefaultMarketSetupDataGroup handleOnServer(String input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            return MarketFactory.DefaultMarketSetupDataGroup.load(input);
        }
        return null;
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, String input) {
        buf.writeUtf(input);
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, MarketFactory.DefaultMarketSetupDataGroup output) {
        buf.writeBoolean(output != null);
        if (output != null) {
            output.encode(buf);
        }
    }

    @Override
    public String decodeInput(FriendlyByteBuf buf) {
        return buf.readUtf(256); // Read a UTF-8 string with a maximum length of 256 characters
    }

    @Override
    public MarketFactory.DefaultMarketSetupDataGroup decodeOutput(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null; // If the output is not present, return null
        }
        return MarketFactory.DefaultMarketSetupDataGroup.decode(buf); // Decode the DefaultMarketSetupDataGroup from the buffer
    }
}
