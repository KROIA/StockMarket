package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class DefaultMarketSetupDataRequest extends StockMarketGenericRequest<TradingPairData, List<MarketFactory.DefaultMarketSetupData>> {
    @Override
    public String getRequestTypeID() {
        return DefaultMarketSetupDataRequest.class.getSimpleName();
    }

    @Override
    public List<MarketFactory.DefaultMarketSetupData> handleOnClient(TradingPairData input) {
        return null;
    }

    @Override
    public List<MarketFactory.DefaultMarketSetupData> handleOnServer(TradingPairData input, ServerPlayer sender) {
        if(!playerIsAdmin(sender)) {
            return null; // Only allow admins to request default market setup data
        }
        List<MarketFactory.DefaultMarketSetupDataGroup> categories = MarketFactory.DefaultMarketSetupDataGroup.loadAll();
        if (categories.isEmpty()) {
            return null; // No categories found, return null
        }
        TradingPair targetTradingPair = input.toTradingPair();

        for(MarketFactory.DefaultMarketSetupDataGroup category : categories) {
            List<MarketFactory.DefaultMarketSetupData> defaultDataList = category.get(targetTradingPair);
            return defaultDataList;
        }
        return null; // No matching default data found
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPairData input) {
        input.encode(buf);
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, List<MarketFactory.DefaultMarketSetupData> output) {
        buf.writeBoolean(output != null);
        buf.writeInt(output != null ? output.size() : 0);
        if (output != null) {
            for (MarketFactory.DefaultMarketSetupData data : output) {
                data.encode(buf);
            }
        }
    }

    @Override
    public TradingPairData decodeInput(FriendlyByteBuf buf) {
        return TradingPairData.decode(buf);
    }

    @Override
    public List<MarketFactory.DefaultMarketSetupData> decodeOutput(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        int size = buf.readInt();
        List<MarketFactory.DefaultMarketSetupData> output = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            MarketFactory.DefaultMarketSetupData data = MarketFactory.DefaultMarketSetupData.decode(buf);
            output.add(data);
        }
        return output;
    }
}
