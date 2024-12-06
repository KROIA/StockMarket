package net.kroia.stockmarket.networking.packet;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.Market;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.market.MarketData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
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
        StockMarketMod.LOGGER.info("[CLIENT] Sending RequestPricePacket for item "+itemID);
        ModMessages.sendToServer(new RequestPricePacket(itemID));
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
            StockMarketMod.LOGGER.info("[SERVER] Receiving RequestPricePacket for item "+this.itemID+" from the player "+player.getName().getString());

            // Send the packet to the client
            UpdatePricePacket.sendPacket(itemID, player);
        });
        context.setPacketHandled(true);
    }
}
