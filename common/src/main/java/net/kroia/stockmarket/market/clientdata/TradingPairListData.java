package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public class TradingPairListData implements INetworkPayloadEncoder {

    public final List<TradingPairData> tradingPairs;

    public TradingPairListData(List<TradingPair> tradingPairs) {
        this.tradingPairs = new ArrayList<>(tradingPairs.size());
        for (TradingPair pair : tradingPairs) {
            this.tradingPairs.add(new TradingPairData(pair));
        }
    }

    private TradingPairListData() {
        this.tradingPairs = new ArrayList<>();
    }


    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(tradingPairs.size());
        for (TradingPairData pair : tradingPairs) {
            pair.encode(buf);
        }
    }

    public static TradingPairListData decode(FriendlyByteBuf buf) {
        TradingPairListData data = new TradingPairListData();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            data.tradingPairs.add(TradingPairData.decode(buf));
        }
        return data;
    }
}
