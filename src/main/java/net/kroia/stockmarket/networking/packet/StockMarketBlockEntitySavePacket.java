package net.kroia.stockmarket.networking.packet;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StockMarketBlockEntitySavePacket {


    public StockMarketBlockEntitySavePacket() {

    }


    public StockMarketBlockEntitySavePacket(FriendlyByteBuf buf) {

    }

    public static void sendPacket() {
        StockMarketMod.LOGGER.info("[CLIENT] Sending StockMarketBlockEntitySavePacket");
        ModMessages.sendToServer(new StockMarketBlockEntitySavePacket());
    }

    public void toBytes(FriendlyByteBuf buf)
    {

    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server or client
        if(contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            //StockMarketMod.LOGGER.info("[CLIENT] Received current prices from the server");
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
            //ServerMarket.handlePacket(context.getSender(), this);



            // Send the packet to the client
            //UpdatePricePacket.sendPacket(itemID, player);

        });
        context.setPacketHandled(true);
    }
}
