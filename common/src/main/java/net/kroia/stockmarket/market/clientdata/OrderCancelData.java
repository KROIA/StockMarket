package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class OrderCancelData implements INetworkPayloadEncoder {

    public final TradingPairData tradingPair;
    public final long orderID;

    public OrderCancelData(@NotNull TradingPair pair, long orderID) {
        this.tradingPair = new TradingPairData(pair);
        this.orderID = orderID;
    }
    private OrderCancelData(@NotNull TradingPairData tradingPair, long orderID) {
        this.tradingPair = tradingPair;
        this.orderID = orderID;
    }


    @Override
    public void encode(FriendlyByteBuf buf) {
        tradingPair.encode(buf);
        buf.writeLong(orderID);
    }

    public static OrderCancelData decode(FriendlyByteBuf buf) {
        TradingPairData tradingPair = TradingPairData.decode(buf);
        long orderID = buf.readLong();
        return new OrderCancelData(tradingPair, orderID);
    }
}
