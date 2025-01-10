package net.kroia.stockmarket.networking.packet.client_sender.request;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.networking.NetworkPacketC2S;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestOrderCancelPacket extends NetworkPacketC2S {
    private long orderID;

    @Override
    public MessageType getType() {
        return StockMarketNetworking.REQUEST_ORDER_CANCEL;
    }
    public RequestOrderCancelPacket(long orderID) {
        super();
        this.orderID = orderID;
    }
    public RequestOrderCancelPacket(RegistryFriendlyByteBuf buf) {
        super(buf);
    }

    public static void generateRequest(long orderID) {
        new RequestOrderCancelPacket(orderID).sendToServer();
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf)
    {
        buf.writeLong(orderID);
    }
    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf)
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
