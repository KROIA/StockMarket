package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestOrderCancelPacket {
    private long orderID;

    public RequestOrderCancelPacket(long orderID) {
        this.orderID = orderID;
    }
    public RequestOrderCancelPacket(FriendlyByteBuf buf) {
        this.orderID = buf.readLong();
    }

    public static void generateRequest(long orderID) {

        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestOrderCancelPacket for order: "+orderID);
        ModMessages.sendToServer(new RequestOrderCancelPacket(orderID));
    }

    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeLong(orderID);
    }

    public long getOrderID() {
        return orderID;
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
            context.setPacketHandled(true);
            return;
        }


        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!
            // Update client-side data
            ServerPlayer player = context.getSender();
            ServerMarket.handlePacket(player,this);


        });
        context.setPacketHandled(true);
    }
}
