package net.kroia.stockmarket.networking.packet.server_sender.update.entity;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.networking.NetworkPacketS2C;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class SyncStockMarketBlockEntityPacket extends NetworkPacketS2C {
    private BlockPos pos;
    private String itemID;
    private int amount;
    private int price;

    @Override
    public MessageType getType() {
        return StockMarketNetworking.SYNC_STOCK_MARKET_BLOCK_ENTITY;
    }

    public SyncStockMarketBlockEntityPacket(BlockPos pos, StockMarketBlockEntity blockEntity) {
        super();
        this.pos = pos;
        this.itemID = blockEntity.getItemID();
        this.amount = blockEntity.getAmount();
        this.price = blockEntity.getPrice();
    }


    public SyncStockMarketBlockEntityPacket(RegistryFriendlyByteBuf buf) {
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
        new SyncStockMarketBlockEntityPacket(pos, blockEntity).sendTo(player);
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        buf.writeUtf(itemID);
        buf.writeInt(amount);
        buf.writeInt(price);
    }
    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf)
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
