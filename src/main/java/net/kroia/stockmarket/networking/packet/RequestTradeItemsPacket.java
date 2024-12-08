package net.kroia.stockmarket.networking.packet;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.ServerTradeItem;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Map;
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
            this.updatePricePackets.add(new UpdatePricePacket(buf));
        }*/
    }

    public void toBytes(FriendlyByteBuf buf) {

    }



    public static void generateRequest() {
        StockMarketMod.LOGGER.info("[CLIENT] Sending RequestTradeItemsPacket");
        ModMessages.sendToServer(new RequestTradeItemsPacket());
    }



    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server or client
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
            UpdateTradeItemsPacket.sendResponse(player);
        });
        context.setPacketHandled(true);
    }

}
