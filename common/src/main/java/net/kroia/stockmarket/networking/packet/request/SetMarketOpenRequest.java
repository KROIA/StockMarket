package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;

import java.util.ArrayList;
import java.util.List;

public class SetMarketOpenRequest extends StockMarketGenericRequest<List<Tuple<TradingPair, Boolean>>, List<Boolean>> {
    @Override
    public String getRequestTypeID() {
        return SetMarketOpenRequest.class.getSimpleName();
    }

    @Override
    public List<Boolean> handleOnClient(List<Tuple<TradingPair, Boolean>> input) {
        return null;
    }

    @Override
    public List<Boolean> handleOnServer(List<Tuple<TradingPair, Boolean>> input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            return BACKEND_INSTANCES.SERVER_MARKET_MANAGER.setMarketOpen(input);
        }
        List<Boolean> result = new ArrayList<>(input.size());
        for (Tuple<TradingPair, Boolean> pair : input) {
            result.add(false); // Deny all requests if not admin
        }
        return result;
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, List<Tuple<TradingPair, Boolean>> input) {
        buf.writeInt(input.size());
        for (Tuple<TradingPair, Boolean> pair : input) {
            pair.getA().encode(buf);
            buf.writeBoolean(pair.getB());
        }
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, List<Boolean> output) {
        buf.writeInt(output.size());
        for (Boolean value : output) {
            buf.writeBoolean(value);
        }
    }

    @Override
    public List<Tuple<TradingPair, Boolean>> decodeInput(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Tuple<TradingPair, Boolean>> input = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            TradingPair tradingPair = new TradingPair(buf);
            boolean isOpen = buf.readBoolean();
            input.add(new Tuple<>(tradingPair, isOpen));
        }
        return input;
    }

    @Override
    public List<Boolean> decodeOutput(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Boolean> output = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            output.add(buf.readBoolean());
        }
        return output;
    }
}
