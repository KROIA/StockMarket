package net.kroia.stockmarket.networking.packet.server_sender.update.entity;

import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class SyncStockMarketBlockEntityPacket extends NetworkPacket {
    private BlockPos pos;
    private String itemID;
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

    public String getItemID() {
        return itemID;
    }

    public int getAmount() {
        return amount;
    }

    public int getPrice() {
        return price;
    }

    public static void sendPacketToClient(BlockPos pos, StockMarketBlockEntity blockEntity, ServerPlayer player) {
        ModMessages.sendToPlayer(new SyncStockMarketBlockEntityPacket(pos, blockEntity), player);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        buf.writeUtf(itemID);
        buf.writeInt(amount);
        buf.writeInt(price);
    }
    @Override
    public void fromBytes(FriendlyByteBuf buf)
    {
        this.pos = buf.readBlockPos();
        this.itemID = buf.readUtf();
        this.amount = buf.readInt();
        this.price = buf.readInt();
    }

    @Override
    protected void handleOnClient() {
        TradeScreen.handlePacket(this);
    }
}
