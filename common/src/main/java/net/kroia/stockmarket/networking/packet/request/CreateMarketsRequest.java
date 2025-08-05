package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class CreateMarketsRequest extends StockMarketGenericRequest<List<MarketFactory.DefaultMarketSetupData>, List<Boolean>> {
    @Override
    public String getRequestTypeID() {
        return CreateMarketsRequest.class.getSimpleName();
    }

    @Override
    public List<Boolean> handleOnClient(List<MarketFactory.DefaultMarketSetupData> input) {
        return null;
    }

    @Override
    public List<Boolean> handleOnServer(List<MarketFactory.DefaultMarketSetupData> input, ServerPlayer sender) {
        if(!playerIsAdmin(sender)) {
            return null; // Only allow admins to create markets
        }
        return BACKEND_INSTANCES.SERVER_MARKET_MANAGER.createMarkets(input);
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, List<MarketFactory.DefaultMarketSetupData> input) {
        buf.writeInt(input.size());
        for (MarketFactory.DefaultMarketSetupData data : input) {
            data.encode(buf); // Encode each DefaultMarketSetupData
        }
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, List<Boolean> output) {
        buf.writeInt(output.size());
        for (Boolean result : output) {
            buf.writeBoolean(result != null && result); // Encode each Boolean result
        }
    }

    @Override
    public List<MarketFactory.DefaultMarketSetupData> decodeInput(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<MarketFactory.DefaultMarketSetupData> input = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            input.add(MarketFactory.DefaultMarketSetupData.decode(buf)); // Decode each DefaultMarketSetupData
        }
        return input;
    }

    @Override
    public List<Boolean> decodeOutput(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Boolean> output = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            output.add(buf.readBoolean()); // Decode each Boolean result
        }
        return output;
    }
}
