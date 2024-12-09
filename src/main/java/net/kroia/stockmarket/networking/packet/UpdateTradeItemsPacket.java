package net.kroia.stockmarket.networking.packet;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.ServerTradeItem;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Supplier;

public class UpdateTradeItemsPacket {
    private ArrayList<UpdatePricePacket> updatePricePackets = new ArrayList<>();

    public UpdateTradeItemsPacket(ArrayList<UpdatePricePacket> updatePricePackets) {
        this.updatePricePackets = updatePricePackets;
    }

    public UpdateTradeItemsPacket(FriendlyByteBuf buf) {
        int size = buf.readInt();
        if(size == 0)
            return;

        for (int i = 0; i < size; i++) {
            this.updatePricePackets.add(new UpdatePricePacket(buf));
        }
    }


    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(updatePricePackets.size());
        for (UpdatePricePacket updatePricePacket : updatePricePackets) {
             updatePricePacket.toBytes(buf);
        }
    }

    public ArrayList<UpdatePricePacket> getUpdatePricePackets() {
        return updatePricePackets;
    }

    public static void sendResponse(ServerPlayer player)
    {
        StockMarketMod.LOGGER.info("[SERVER] Sending UpdateTradeItemsPacket");
        Map<String, ServerTradeItem> serverTradeItemMap = ServerMarket.getTradeItems();
        ArrayList<UpdatePricePacket> updatePricePackets = new ArrayList<>();
        int i=0;
        for(var entry : serverTradeItemMap.entrySet())
        {
            ServerTradeItem item = entry.getValue();
            updatePricePackets.add(new UpdatePricePacket(item.getItemID()));
            i++;
        }

        ModMessages.sendToPlayer(new UpdateTradeItemsPacket(updatePricePackets), player);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server or client
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
