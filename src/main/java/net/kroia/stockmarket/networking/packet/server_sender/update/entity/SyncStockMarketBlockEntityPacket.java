package net.kroia.stockmarket.networking.packet.server_sender.update.entity;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncStockMarketBlockEntityPacket {
    private final BlockPos pos;
    private final String itemID;
    private final int amount;
    private final int price;



    public SyncStockMarketBlockEntityPacket(BlockPos pos, StockMarketBlockEntity blockEntity) {
        this.pos = pos;
        this.itemID = blockEntity.getItemID();
        this.amount = blockEntity.getAmount();
        this.price = blockEntity.getPrice();
    }


    public SyncStockMarketBlockEntityPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.itemID = buf.readUtf();
        this.amount = buf.readInt();
        this.price = buf.readInt();
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
        StockMarketMod.LOGGER.info("[SERVER] Sending UpdateStockMarketBlockEntityPacket");
        ModMessages.sendToPlayer(new SyncStockMarketBlockEntityPacket(pos, blockEntity), player);
    }

    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        buf.writeUtf(itemID);
        buf.writeInt(amount);
        buf.writeInt(price);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server_sender or client
        if(contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            //StockMarketMod.LOGGER.info("[CLIENT] Received current prices from the server_sender");
            // HERE WE ARE ON THE CLIENT!
            // Update client-side data
            // Get the data from the packet
            //MarketData.setPrice(this.itemID, this.price);
            TradeScreen.handlePacket(this);
            context.setPacketHandled(true);
            return;
        }


        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!


        });
        context.setPacketHandled(true);
    }
}
