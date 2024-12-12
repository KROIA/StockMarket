package net.kroia.stockmarket.networking.packet.client_sender.update.entity;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.BankTerminalBlockEntity;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.function.Supplier;

public class UpdateBankTerminalBlockEntityPacket {

    private final BlockPos pos;

    private final HashMap<String, Integer> itemTransferToMarketAmounts;
    private final boolean sendItemsToMarket;
    public UpdateBankTerminalBlockEntityPacket(BlockPos pos, HashMap<String, Integer> itemTransferToMarketAmounts, boolean sendItemsToMarket) {
        this.pos = pos;
        this.itemTransferToMarketAmounts = itemTransferToMarketAmounts;
        this.sendItemsToMarket = sendItemsToMarket;
    }


    public UpdateBankTerminalBlockEntityPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.itemTransferToMarketAmounts = new HashMap<>();
        this.sendItemsToMarket = buf.readBoolean();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String itemID = buf.readUtf();
            int amount = buf.readInt();
            this.itemTransferToMarketAmounts.put(itemID, amount);
        }
    }

    public BlockPos getPos() {
        return pos;
    }

    public HashMap<String, Integer> getItemTransferToMarketAmounts() {
        return itemTransferToMarketAmounts;
    }
    public boolean isSendItemsToMarket() {
        return sendItemsToMarket;
    }


    public static void sendPacketToServer(BlockPos pos, HashMap<String, Integer> itemTransferToMarketAmounts, boolean sendItemsToMarket) {
        StockMarketMod.LOGGER.info("[CLIENT] Sending UpdateBankTerminalBlockEntityPacket");
        ModMessages.sendToServer(new UpdateBankTerminalBlockEntityPacket(pos, itemTransferToMarketAmounts, sendItemsToMarket));
    }
    /*public static void sendPacketToClient(BlockPos pos, StockMarketBlockEntity blockEntity, ServerPlayer player) {
        StockMarketMod.LOGGER.info("[SERVER] Sending UpdateStockMarketBlockEntityPacket");
        ModMessages.sendToPlayer(new UpdateStockMarketBlockEntityPacket(pos, blockEntity), player);
    }*/

    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        buf.writeBoolean(sendItemsToMarket);
        buf.writeInt(itemTransferToMarketAmounts.size());
        itemTransferToMarketAmounts.forEach((itemID, amount) -> {
            buf.writeUtf(itemID);
            buf.writeInt(amount);
        });
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
            //TradeScreen.handlePacket(this);
            context.setPacketHandled(true);
            return;
        }


        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!
            // Update client-side data
            ServerPlayer player = context.getSender();
            StockMarketMod.LOGGER.info("[SERVER] Received UpdateStockMarketBlockEntityPacket from client");
            BlockEntity blockEntity = player.level().getBlockEntity(this.pos);
            if(blockEntity instanceof BankTerminalBlockEntity bankTerminalBlockEntity) {
                bankTerminalBlockEntity.handlePacket(this, player);
                //bankTerminalBlockEntity.setChanged();
                //player.level().getChunkAt(this.pos).setUnsaved(true);
            }else
            {
                StockMarketMod.LOGGER.error("BankTerminalBlockEntity not found at position "+this.pos);
            }
        });
        context.setPacketHandled(true);
    }
}
