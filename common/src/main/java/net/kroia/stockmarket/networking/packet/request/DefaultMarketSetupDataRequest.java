package net.kroia.stockmarket.networking.packet.request;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class DefaultMarketSetupDataRequest extends StockMarketGenericRequest<TradingPairData, MarketFactory.DefaultMarketSetupData> {
    @Override
    public String getRequestTypeID() {
        return DefaultMarketSetupDataRequest.class.getSimpleName();
    }

    @Override
    public MarketFactory.DefaultMarketSetupData handleOnClient(TradingPairData input) {
        return null;
    }

    @Override
    public MarketFactory.DefaultMarketSetupData handleOnServer(TradingPairData input, ServerPlayer sender) {
        if(!playerIsAdmin(sender)) {
            return null; // Only allow admins to request default market setup data
        }
        List<MarketFactory.DefaultMarketSetupDataGroup> categories = MarketFactory.DefaultMarketSetupDataGroup.loadAll();
        if (categories.isEmpty()) {
            return null; // No categories found, return null
        }
        TradingPair targetTradingPair = input.toTradingPair();

        for(MarketFactory.DefaultMarketSetupDataGroup category : categories) {
            MarketFactory.DefaultMarketSetupData defaultData = category.get(targetTradingPair);
            if (defaultData != null) {
                return defaultData; // Return the first matching default data found
            }
        }
        return null; // No matching default data found
    }

    @Override
    public void encodeInput(FriendlyByteBuf buf, TradingPairData input) {
        input.encode(buf);
    }

    @Override
    public void encodeOutput(FriendlyByteBuf buf, MarketFactory.DefaultMarketSetupData output) {
        buf.writeBoolean(output != null);
        if (output != null) {
            output.encode(buf);
        }
    }

    @Override
    public TradingPairData decodeInput(FriendlyByteBuf buf) {
        return TradingPairData.decode(buf);
    }

    @Override
    public MarketFactory.DefaultMarketSetupData decodeOutput(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        return MarketFactory.DefaultMarketSetupData.decode(buf);
    }
}
