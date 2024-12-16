package net.kroia.stockmarket.networking.packet.server_sender.update;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.ServerTradeItem;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Supplier;

public class SyncTradeItemsPacket {
    private ArrayList<SyncPricePacket> syncPricePackets = new ArrayList<>();

    public SyncTradeItemsPacket(ArrayList<SyncPricePacket> syncPricePackets) {
        this.syncPricePackets = syncPricePackets;
    }

    public SyncTradeItemsPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        if(size == 0)
            return;

        for (int i = 0; i < size; i++) {
            this.syncPricePackets.add(new SyncPricePacket(buf));
        }
    }


    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(syncPricePackets.size());
        for (SyncPricePacket syncPricePacket : syncPricePackets) {
             syncPricePacket.toBytes(buf);
        }
    }

    public ArrayList<SyncPricePacket> getUpdatePricePackets() {
        return syncPricePackets;
    }

    public static void sendResponse(ServerPlayer player)
    {
        //StockMarketMod.LOGGER.info("[SERVER] Sending SyncTradeItemsPacket");
        Map<String, ServerTradeItem> serverTradeItemMap = ServerMarket.getTradeItems();
        ArrayList<SyncPricePacket> syncPricePackets = new ArrayList<>();
        int i=0;
        for(var entry : serverTradeItemMap.entrySet())
        {
            ServerTradeItem item = entry.getValue();
            syncPricePackets.add(new SyncPricePacket(item.getItemID(), player.getUUID()));
            i++;
        }

        ModMessages.sendToPlayer(new SyncTradeItemsPacket(syncPricePackets), player);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server_sender or client
        if(contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            // HERE WE ARE ON THE CLIENT!
            ClientMarket.handlePacket(this);
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!
            // Update client-side data
        });
        context.setPacketHandled(true);
    }
}
