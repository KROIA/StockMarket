package net.kroia.stockmarket.networking.packet.server_sender.update;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.networking.packet.NetworkPacket;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class SyncOrderPacket extends NetworkPacket {

    private Order order;

    public SyncOrderPacket(Order order) {
        super();
        this.order = order;
    }
    public SyncOrderPacket(FriendlyByteBuf buf)
    {
        super(buf);

    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        order.toBytes(buf);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        this.order = Order.construct(buf);
    }

    public static void sendResponse(Order order) {
        String itemID = order.getItemID();

        //StockMarketMod.LOGGER.info("[SERVER] Sending SyncOrderPacket for order: "+order.toString());


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

    @Override
    protected void handleOnClient() {
        ClientMarket.handlePacket(this);
    }
}
