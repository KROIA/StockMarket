package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class ChartResetRequest extends StockMarketGenericRequest<List<TradingPair>, List<Boolean>> {


    @Override
    public String getRequestTypeID() {
        return ChartResetRequest.class.getSimpleName();
    }

    @Override
    public List<Boolean> handleOnClient(List<TradingPair> input) {
        return null;
    }

    @Override
    public List<Boolean> handleOnServer(List<TradingPair> input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            // Reset the chart for each trading pair in the input list
            List<Boolean> results = new java.util.ArrayList<>(input.size());
            for (TradingPair tradingPair : input) {
                IServerMarket market = BACKEND_INSTANCES.SERVER_MARKET_MANAGER.getMarket(tradingPair);
                if (market != null) {
                    market.resetHistoricalMarketData();
                    results.add(true);
                }
                else {
                    results.add(false); // Market not found
                }
            }
            return results;
        }
        else {
            // If the player is not an admin, return a list of false values
            List<Boolean> results = new java.util.ArrayList<>(input.size());
            for (int i = 0; i < input.size(); i++) {
                results.add(false);
            }
            return results;
        }
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, List<TradingPair> input) {
        buf.writeInt(input.size());
        for (TradingPair tradingPair : input) {
            tradingPair.encode(buf);
        }
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, List<Boolean> output) {
        buf.writeInt(output.size());
        for (Boolean result : output) {
            buf.writeBoolean(result);
        }
    }

    @Override
    public List<TradingPair> decodeInput(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<TradingPair> tradingPairs = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            TradingPair tradingPair = new TradingPair(buf);
            tradingPairs.add(tradingPair);
        }
        return tradingPairs;
    }

    @Override
    public List<Boolean> decodeOutput(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Boolean> results = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            results.add(buf.readBoolean());
        }
        return results;
    }
}
