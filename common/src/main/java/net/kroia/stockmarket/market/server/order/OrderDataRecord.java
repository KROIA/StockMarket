package net.kroia.stockmarket.market.server.order;

import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.market.TradingPair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class OrderDataRecord implements ServerSaveable, INetworkPayloadConverter{

    private final static UUID TEST_DUMMY_UUID = UUID.randomUUID(); // For testing purposes, replace with a valid UUID if needed
    public static final class KEY
    {
        public static final String AMOUNT = "a";
        public static final String PLAYER = "p";
        public static final String ENUM_DATA = "e";
        public static final String TRADING_PAIR = "t";
    }

    long amount;
    UUID player;
    Order.Type type;
    Order.Status status;


    // The trading pair is not saved inside the save/load methods!
    TradingPair tradingPair;

    public OrderDataRecord(long amount, @NotNull UUID player, Order.Type type, Order.Status status, TradingPair tradingPair) {
        this.amount = amount;
        this.player = player;
        this.type = type;
        this.status = status;
        this.tradingPair = tradingPair;
    }

    private OrderDataRecord(){}
    public static OrderDataRecord loadFromTag(CompoundTag tag, TradingPair pair)
    {
        OrderDataRecord record = new OrderDataRecord();
        record.tradingPair = pair;
        if(!record.load(tag)){
            return null; // Failed to load
        }
        return record;
    }

    public TradingPair getTradingPair() {
        return tradingPair;
    }

    @Override
    public void decode(FriendlyByteBuf friendlyByteBuf) {
        amount = friendlyByteBuf.readVarLong();
        player = friendlyByteBuf.readUUID();
        byte enumData = friendlyByteBuf.readByte();
        type = Order.Type.values()[enumData >> 4 & 0x0F];
        status = Order.Status.values()[enumData & 0x0F];

    }

    @Override
    public void encode(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeVarLong(amount);
        friendlyByteBuf.writeUUID(player);
        friendlyByteBuf.writeByte((byte)((type.ordinal() << 4) | status.ordinal()));
    }

    @Override
    public boolean save(CompoundTag compoundTag) {
        compoundTag.putLong(KEY.AMOUNT, amount);
        compoundTag.putUUID(KEY.PLAYER, player);
        compoundTag.putByte(KEY.ENUM_DATA, (byte)((type.ordinal() << 4) | status.ordinal()));
        return true;
    }

    @Override
    public boolean load(CompoundTag compoundTag) {
        if(!compoundTag.contains(KEY.AMOUNT) || !compoundTag.contains(KEY.PLAYER) || !compoundTag.contains(KEY.ENUM_DATA)){
            return false;
        }
        amount = compoundTag.getLong(KEY.AMOUNT);
        player = compoundTag.getUUID(KEY.PLAYER);
        byte enumData = compoundTag.getByte(KEY.ENUM_DATA);
        type = Order.Type.values()[enumData >> 4 & 0x0F];
        status = Order.Status.values()[enumData & 0x0F];

        return true;
    }

    public static @Nullable OrderDataRecord fromOrder(Order order, TradingPair pair){
        UUID playerUUID = order.getPlayerUUID();
        if (playerUUID == null) {
            playerUUID = TEST_DUMMY_UUID; // Use a dummy UUID for testing purposes
            // If you removed the TEST_DUMMY_UUID, you must return null here
            // Do not create an instance of the record if the player is null since that means the order does not belong to any player
            // and is a bot order!
        }
        return new OrderDataRecord(order.amount, playerUUID, order instanceof MarketOrder ? Order.Type.MARKET : Order.Type.LIMIT ,order.status, pair);
    }
}
