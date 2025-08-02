package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class ChartResetRequest extends StockMarketGenericRequest<TradingPair, Boolean> {


    @Override
    public String getRequestTypeID() {
        return ChartResetRequest.class.getSimpleName();
    }

    @Override
    public Boolean handleOnClient(TradingPair input) {
        return null;
    }

    @Override
    public Boolean handleOnServer(TradingPair input, ServerPlayer sender) {
        if(playerIsAdmin(sender)) {
            ServerMarket market = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getMarket(input);
            if (market == null) {
                return false; // Market not found
            }
            market.resetHistoricalMarketData();
            return true; // Successfully reset the chart
        }
        return false; // Player is not an admin
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPair input) {
        input.encode(buf);
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, Boolean output) {
        buf.writeBoolean(output);
    }

    @Override
    public TradingPair decodeInput(FriendlyByteBuf buf) {
        TradingPair tradingPair = new TradingPair();
        tradingPair.decode(buf);
        return tradingPair;
    }

    @Override
    public Boolean decodeOutput(FriendlyByteBuf buf) {
        return buf.readBoolean();
    }
}
