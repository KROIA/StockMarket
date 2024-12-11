package net.kroia.stockmarket.networking.packet.server_sender.update;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncOrderPacket {

    private Order order;

    public SyncOrderPacket(Order order) {
        this.order = order;
    }
    public SyncOrderPacket(FriendlyByteBuf buf)
    {
        this.order = Order.construct(buf);
    }

    public void toBytes(FriendlyByteBuf buf) {
        order.toBytes(buf);
    }

    public static void sendResponse(Order order) {
        String itemID = order.getItemID();

        StockMarketMod.LOGGER.info("[SERVER] Sending SyncOrderPacket for order: "+order.toString());


        ServerPlayer player =  ServerPlayerList.getPlayer(order.getPlayerUUID());
        if(player == null)
        {
            StockMarketMod.LOGGER.warn("[SERVER] Player not found for order: "+order.toString());
            return;
        }
        ModMessages.sendToPlayer(new SyncOrderPacket(order), player);
    }

    public Order getOrder() {
        return order;
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
