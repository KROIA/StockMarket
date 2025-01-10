package net.kroia.stockmarket.networking.packet.server_sender.update;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.networking.NetworkPacketS2C;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public class SyncOrderPacket extends NetworkPacketS2C {

    private Order order;

    @Override
    public MessageType getType() {
        return StockMarketNetworking.SYNC_ORDER;
    }
    public SyncOrderPacket(Order order) {
        super();
        this.order = order;
    }
    public SyncOrderPacket(RegistryFriendlyByteBuf buf)
    {
        super(buf);

    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf) {
        order.toBytes(buf);
    }

    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf) {
        this.order = Order.construct(buf);
    }

    public static void sendResponse(Order order) {
        String itemID = order.getItemID();
        ServerPlayer player =  ServerPlayerList.getPlayer(order.getPlayerUUID());
        if(player == null)
        {
            StockMarketMod.LOGGER.warn("[SERVER] Player not found for order: "+order.toString());
            return;
        }
        new SyncOrderPacket(order).sendTo(player);
    }

    public Order getOrder() {
        return order;
    }

    @Override
    protected void handleOnClient() {
        ClientMarket.handlePacket(this);
    }


}
