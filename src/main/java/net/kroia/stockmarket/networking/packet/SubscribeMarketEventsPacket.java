package net.kroia.stockmarket.networking.packet;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SubscribeMarketEventsPacket {
    private final String itemID;
    private final boolean subscribe;

    public SubscribeMarketEventsPacket(String itemID, boolean subscribe) {
        this.itemID = itemID;
        this.subscribe = subscribe;
    }

    public SubscribeMarketEventsPacket(FriendlyByteBuf buf)
    {
        this.itemID = buf.readUtf();
        this.subscribe = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeUtf(itemID);
        buf.writeBoolean(subscribe);
    }

    public static void generateRequest(String itemID, boolean subscribe) {
        StockMarketMod.LOGGER.info("[CLIENT] Sending SubscribeMarketEventsPacket for item "+itemID+
                " to "+(subscribe ? "subscribe" : "unsubscribe"));
        ModMessages.sendToServer(new SubscribeMarketEventsPacket(itemID, subscribe));
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
            StockMarketMod.LOGGER.info("[SERVER] Receiving SubscribeMarketEventsPacket for item "+itemID+
                    " to "+(subscribe ? "subscribe" : "unsubscribe"));

            // Subscribe or unsubscribe the player
            if(subscribe) {
                ServerMarket.addPlayerUpdateSubscription(itemID, player);
            } else {
                ServerMarket.removePlayerUpdateSubscription(itemID, player);
            }
        });
        context.setPacketHandled(true);
    }
}
