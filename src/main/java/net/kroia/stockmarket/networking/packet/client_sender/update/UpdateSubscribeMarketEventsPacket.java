package net.kroia.stockmarket.networking.packet.client_sender.update;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateSubscribeMarketEventsPacket {
    private final String itemID;
    private final boolean subscribe;

    public UpdateSubscribeMarketEventsPacket(String itemID, boolean subscribe) {
        this.itemID = itemID;
        this.subscribe = subscribe;
    }

    public UpdateSubscribeMarketEventsPacket(FriendlyByteBuf buf)
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
        StockMarketMod.LOGGER.info("[CLIENT] Sending UpdateSubscribeMarketEventsPacket for item "+itemID+
                " to "+(subscribe ? "subscribe" : "unsubscribe"));
        ModMessages.sendToServer(new UpdateSubscribeMarketEventsPacket(itemID, subscribe));
    }

    public String getItemID() {
        return itemID;
    }
    public boolean doesSubscribe() {
        return subscribe;
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