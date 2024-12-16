package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestPricePacket {


    private String itemID;
    public RequestPricePacket(String itemID) {
        this.itemID = itemID;
    }

    public RequestPricePacket(FriendlyByteBuf buf) {
        this.itemID = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemID);
    }

    public static void generateRequest(String itemID) {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestPricePacket for item "+itemID);
        ModMessages.sendToServer(new RequestPricePacket(itemID));
    }

    public String getItemID() {
        return itemID;
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
