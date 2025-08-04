package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class RemoveMarketRequest extends StockMarketGenericRequest<List<TradingPair>, List<Boolean>> {
    @Override
    public String getRequestTypeID() {
        return RemoveMarketRequest.class.getName();
    }

    @Override
    public List<Boolean> handleOnClient(List<TradingPair> input) {
        return null;
    }

    @Override
    public List<Boolean> handleOnServer(List<TradingPair> input, ServerPlayer sender) {
        List<Boolean> results = new ArrayList<>();
        if(playerIsAdmin(sender)) {
            for (TradingPair data : input) {
                // Attempt to remove the trading pair from the market
                results.add(BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.removeTradeItem(data));
            }
            return results;
        }
        // If the player is not an admin, return a list of false values
        for (TradingPair data : input) {
            results.add(false); // Indicate failure for each item
        }
        return results;
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, List<TradingPair> input) {
        buf.writeInt(input.size());
        for (TradingPair data : input) {
            data.encode(buf); // Encode each TradingPairData
        }
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, List<Boolean> output) {
        buf.writeInt(output.size());
        for (Boolean result : output) {
            buf.writeBoolean(result); // Encode each Boolean result
        }
    }

    @Override
    public List<TradingPair> decodeInput(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<TradingPair> input = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            TradingPair data = new TradingPair(buf); // Decode each TradingPairData
            input.add(data);
        }
        return input;
    }

    @Override
    public List<Boolean> decodeOutput(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Boolean> output = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            output.add(buf.readBoolean()); // Decode each Boolean result
        }
        return output;
    }
}
