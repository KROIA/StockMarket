package net.kroia.stockmarket.networking.packet.server_sender.update.entity;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class SyncStockMarketBlockEntityPacket extends NetworkPacket {
    private BlockPos pos;
    private ItemID itemID;
    private int amount;
    private int price;



    public SyncStockMarketBlockEntityPacket(BlockPos pos, StockMarketBlockEntity blockEntity) {
        super();
        this.pos = pos;
        this.itemID = blockEntity.getItemID();
        this.amount = blockEntity.getAmount();
        this.price = blockEntity.getPrice();
    }


    public SyncStockMarketBlockEntityPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public BlockPos getPos() {
        return pos;
    }

    public ItemID getItemID() {
        return itemID;
    }

    public int getAmount() {
        return amount;
    }

    public int getPrice() {
        return price;
    }

    public static void sendPacketToClient(BlockPos pos, StockMarketBlockEntity blockEntity, ServerPlayer player) {
        StockMarketNetworking.sendToClient(player, new SyncStockMarketBlockEntityPacket(pos, blockEntity));
    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        buf.writeItem(itemID.getStack());
        buf.writeInt(amount);
        buf.writeInt(price);
    }
    @Override
    public void decode(FriendlyByteBuf buf)
    {
        this.pos = buf.readBlockPos();
        this.itemID = new ItemID(buf.readItem());
        this.amount = buf.readInt();
        this.price = buf.readInt();
    }

    @Override
    protected void handleOnClient() {
        TradeScreen.handlePacket(this);
    }
}
