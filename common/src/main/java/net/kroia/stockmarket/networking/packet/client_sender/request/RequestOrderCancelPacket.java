package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestOrderCancelPacket extends StockMarketNetworkPacket {
    private long orderID;

    public RequestOrderCancelPacket(long orderID) {
        super();
        this.orderID = orderID;
    }
    public RequestOrderCancelPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static void generateRequest(long orderID) {

        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestOrderCancelPacket for order: "+orderID);
        new RequestOrderCancelPacket(orderID).sendToServer();
    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeLong(orderID);
    }
    @Override
    public void decode(FriendlyByteBuf buf)
    {
        this.orderID = buf.readLong();
    }

    public long getOrderID() {
        return orderID;
    }

    @Override
    protected void handleOnServer(ServerPlayer sender)
    {
        BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.handlePacket(sender, this);
    }


}
