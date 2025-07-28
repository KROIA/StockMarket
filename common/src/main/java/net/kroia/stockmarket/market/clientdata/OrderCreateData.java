package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.Order;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class OrderCreateData implements INetworkPayloadEncoder {

    public final UUID owner;

    public final Order.Type type;
    public final TradingPairData tradingPair;

    public final long volume;


    /**
     * The limit price for the order.
     * For market orders, this is 0.
     */
    public final int limitPrice;


    public OrderCreateData(@NotNull UUID owner, @NotNull TradingPair pair, long volume, int limitPrice)
    {
        this.owner = owner;
        this.type = Order.Type.LIMIT;
        this.tradingPair = new TradingPairData(pair);
        this.volume = volume;
        this.limitPrice = limitPrice;
    }

    private OrderCreateData(@NotNull UUID owner, Order.Type type, @NotNull TradingPairData pair, long volume, int limitPrice) {
        this.owner = owner;
        this.type = type;
        this.tradingPair = pair;
        this.volume = volume;
        this.limitPrice = limitPrice;
    }

    public OrderCreateData(@NotNull UUID owner, @NotNull TradingPair pair, long volume) {
        this.owner = owner;
        this.type = Order.Type.MARKET;
        this.tradingPair = new TradingPairData(pair);
        this.volume = volume;
        this.limitPrice = 0; // Market orders do not have a limit price
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(owner); // Write the owner UUID
        buf.writeUtf(type.name(), 16); // Write the order type
        tradingPair.encode(buf);
        buf.writeLong(volume);
        buf.writeInt(limitPrice);
    }

    public static OrderCreateData decode(FriendlyByteBuf buf) {
        UUID owner = buf.readUUID(); // Read the owner UUID
        Order.Type type = Order.Type.valueOf(buf.readUtf(16));
        TradingPairData tradingPair = TradingPairData.decode(buf);
        long volume = buf.readLong();
        int limitPrice = buf.readInt();

        return new OrderCreateData(owner, type, tradingPair, volume, limitPrice);
    }
}
