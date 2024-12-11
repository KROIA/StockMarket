package net.kroia.stockmarket.networking.packet;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ResponseOrderPacket {

    private Order order;

    public ResponseOrderPacket(Order order) {
        this.order = order;
    }
    public ResponseOrderPacket(FriendlyByteBuf buf)
    {
        this.order = Order.construct(buf);
    }

    public void toBytes(FriendlyByteBuf buf) {
        order.toBytes(buf);
    }

    public static void sendResponse(Order order) {
        String itemID = order.getItemID();

        StockMarketMod.LOGGER.info("[SERVER] Sending ResponseOrderPacket for order: "+order.toString());


        ServerPlayer player =  ServerPlayerList.getPlayer(order.getPlayerUUID());
        if(player == null)
        {
            StockMarketMod.LOGGER.warn("[SERVER] Player not found for order: "+order.toString());
            return;
        }
        ModMessages.sendToPlayer(new ResponseOrderPacket(order), player);
    }

    public Order getOrder() {
        return order;
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
