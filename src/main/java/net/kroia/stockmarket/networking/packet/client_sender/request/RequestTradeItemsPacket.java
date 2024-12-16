package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestTradeItemsPacket {


    public RequestTradeItemsPacket()
    {
        //updatePricePackets = null;
    }


    public RequestTradeItemsPacket(FriendlyByteBuf buf) {
        //int size = buf.readInt();
        //if(size == 0)
        //    return;

        /*for (int i = 0; i < size; i++) {
            this.updatePricePackets.add(new SyncPricePacket(buf));
        }*/
    }

    public void toBytes(FriendlyByteBuf buf) {

    }



    public static void generateRequest() {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestTradeItemsPacket");
        ModMessages.sendToServer(new RequestTradeItemsPacket());
    }



    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server_sender or client
        if(contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            // HERE WE ARE ON THE CLIENT!
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!
            // Update client-side data
            ServerPlayer player = context.getSender();
            assert player != null;
            ServerMarket.handlePacket(player, this);
        });
        context.setPacketHandled(true);
    }

}
