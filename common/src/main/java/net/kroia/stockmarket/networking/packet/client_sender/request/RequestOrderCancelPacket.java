package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestOrderCancelPacket extends NetworkPacket {
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
        StockMarketNetworking.sendToServer(new RequestOrderCancelPacket(orderID));
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
        ServerMarket.handlePacket(sender, this);
    }


}
