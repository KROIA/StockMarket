package net.kroia.stockmarket.market.server.order;

import net.kroia.modutilities.networking.INetworkPayloadConverter;
import net.kroia.modutilities.persistence.ServerSaveable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class OrderDataRecord implements ServerSaveable, INetworkPayloadConverter{

    long amount;
    UUID player;
    Order.Type type;
    Order.Status status;

    public OrderDataRecord(long amount, UUID player, Order.Type type, Order.Status status){
        this.amount = amount;
        this.player = player;
        this.type = type;
        this.status = status;
    }

    public OrderDataRecord(){}

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
        compoundTag.putLong("amount", amount);
        compoundTag.putUUID("player", player != null ? player : UUID.randomUUID());
        compoundTag.putByte("enumData", (byte)((type.ordinal() << 4) | status.ordinal()));
        return true;
    }

    @Override
    public boolean load(CompoundTag compoundTag) {
        if(!compoundTag.contains("amount") || !compoundTag.contains("player") || !compoundTag.contains("enumData")){
            return false;
        }
        amount = compoundTag.getLong("amount");
        player = compoundTag.getUUID("player");
        byte enumData = compoundTag.getByte("enumData");
        type = Order.Type.values()[enumData >> 4 & 0x0F];
        status = Order.Status.values()[enumData & 0x0F];

        return true;
    }

    public static OrderDataRecord fromOrder(Order order){
        return new OrderDataRecord(order.amount, order.playerUUID, order instanceof MarketOrder ? Order.Type.MARKET : Order.Type.LIMIT ,order.status);
    }
}
