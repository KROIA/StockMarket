package net.kroia.stockmarket.networking.packet.client_sender.update.entity;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class UpdateStockMarketBlockEntityPacket extends NetworkPacket {
    private BlockPos pos;
    private ItemID itemID;
    private int amount;
    private int price;



    public UpdateStockMarketBlockEntityPacket(BlockPos pos, StockMarketBlockEntity blockEntity) {
        super();
        this.pos = pos;
        this.itemID = blockEntity.getItemID();
        this.amount = blockEntity.getAmount();
        this.price = blockEntity.getPrice();
    }


    public UpdateStockMarketBlockEntityPacket(FriendlyByteBuf buf) {
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

    public static void sendPacketToServer(BlockPos pos, StockMarketBlockEntity blockEntity) {
        StockMarketNetworking.sendToServer(new UpdateStockMarketBlockEntityPacket(pos, blockEntity));
    }

    @Override
    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        buf.writeItem(itemID.getStack());
        buf.writeInt(amount);
        buf.writeInt(price);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf)
    {
        this.pos = buf.readBlockPos();
        this.itemID = new ItemID(buf.readItem());
        this.amount = buf.readInt();
        this.price = buf.readInt();
    }

    @Override
    protected void handleOnServer(ServerPlayer sender)
    {
        StockMarketMod.LOGGER.info("[SERVER] Received UpdateStockMarketBlockEntityPacket from client");
        StockMarketBlockEntity blockEntity = (StockMarketBlockEntity) sender.level().getBlockEntity(this.pos);
        if(blockEntity == null)
        {
            StockMarketMod.LOGGER.error("BlockEntity not found at position "+this.pos);
            return;
        }
        blockEntity.setItemID(this.itemID);
        blockEntity.setAmount(this.amount);
        blockEntity.setPrice(this.price);
        blockEntity.setChanged();
        sender.level().getChunkAt(this.pos).setUnsaved(true);
    }
}
