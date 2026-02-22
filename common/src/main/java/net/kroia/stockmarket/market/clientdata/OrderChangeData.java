package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class OrderChangeData implements INetworkPayloadEncoder {
    public final TradingPairData tradingPair;
    public final long orderID;
    public final float newPrice;

    public OrderChangeData(@NotNull TradingPair pair, long orderID, float newPrice)
    {
        this.tradingPair = new TradingPairData(pair);
        this.orderID = orderID;
        this.newPrice = newPrice;
    }
    private OrderChangeData(@NotNull TradingPairData tradingPair, long orderID, float newPrice) {
        this.tradingPair = tradingPair;
        this.orderID = orderID;
        this.newPrice = newPrice;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        tradingPair.encode(buf);
        buf.writeLong(orderID);
        buf.writeFloat(newPrice);
    }


    public static OrderChangeData decode(FriendlyByteBuf buf) {
        TradingPairData tradingPair = TradingPairData.decode(buf);
        long orderID = buf.readLong();
        float newPrice = buf.readFloat();
        return new OrderChangeData(tradingPair, orderID, newPrice);
    }
}
