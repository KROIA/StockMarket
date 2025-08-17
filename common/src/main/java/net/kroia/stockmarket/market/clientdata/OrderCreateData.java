package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.Order;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class OrderCreateData implements INetworkPayloadEncoder {

    public final UUID owner;
    public int bankAccountNumber;

    public final Order.Type type;
    public final TradingPairData tradingPair;

    public final float volume;


    /**
     * The limit price for the order.
     * For market orders, this is 0.
     */
    public final float limitPrice;


    public OrderCreateData(@NotNull UUID owner, int bankAccountNumber, @NotNull TradingPair pair, float volume, float limitPrice)
    {
        this.owner = owner;
        this.bankAccountNumber = bankAccountNumber;
        this.type = Order.Type.LIMIT;
        this.tradingPair = new TradingPairData(pair);
        this.volume = volume;
        this.limitPrice = limitPrice;
    }

    private OrderCreateData(@NotNull UUID owner, int bankAccountNumber, Order.Type type, @NotNull TradingPairData pair, float volume, float limitPrice) {
        this.owner = owner;
        this.bankAccountNumber = bankAccountNumber;
        this.type = type;
        this.tradingPair = pair;
        this.volume = volume;
        this.limitPrice = limitPrice;
    }

    public OrderCreateData(@NotNull UUID owner, int bankAccountNumber, @NotNull TradingPair pair, float volume) {
        this.owner = owner;
        this.bankAccountNumber = bankAccountNumber;
        this.type = Order.Type.MARKET;
        this.tradingPair = new TradingPairData(pair);
        this.volume = volume;
        this.limitPrice = 0; // Market orders do not have a limit price
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(owner); // Write the owner UUID
        buf.writeInt(bankAccountNumber); // Write the bank account number
        buf.writeUtf(type.name(), 16); // Write the order type
        tradingPair.encode(buf);
        buf.writeFloat(volume);
        buf.writeFloat(limitPrice);
    }

    public static OrderCreateData decode(FriendlyByteBuf buf) {
        UUID owner = buf.readUUID(); // Read the owner UUID
        int bankAccountNumber = buf.readInt(); // Read the bank account number
        Order.Type type = Order.Type.valueOf(buf.readUtf(16));
        TradingPairData tradingPair = TradingPairData.decode(buf);
        float volume = buf.readFloat();
        float limitPrice = buf.readFloat();

        return new OrderCreateData(owner, bankAccountNumber, type, tradingPair, volume, limitPrice);
    }
}
