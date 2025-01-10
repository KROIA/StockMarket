package net.kroia.stockmarket.networking.packet.client_sender.request;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.networking.NetworkPacketC2S;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestOrderChangePacket extends NetworkPacketC2S {

    String itemID;
    long targetOrderID;
    int newPrice;

    @Override
    public MessageType getType() {
        return StockMarketNetworking.REQUEST_ORDER_CHANGE;
    }

    public RequestOrderChangePacket(String itemID, long targetOrderID, int newPrice) {
        super();
        this.itemID = itemID;
        this.targetOrderID = targetOrderID;
        this.newPrice = newPrice;
    }

    public RequestOrderChangePacket(RegistryFriendlyByteBuf buf) {
        super(buf);
    }

    public static void sendRequest(String itemID, long targetOrderID, int newPrice) {
        new RequestOrderChangePacket(itemID, targetOrderID, newPrice).sendToServer();
    }

    public long getTargetOrderID() {
        return targetOrderID;
    }
    public int getNewPrice() {
        return newPrice;
    }
    public String getItemID() {
        return itemID;
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(itemID);
        buf.writeLong(targetOrderID);
        buf.writeInt(newPrice);
    }

    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf) {
        itemID = buf.readUtf();
        targetOrderID = buf.readLong();
        newPrice = buf.readInt();
    }

    protected void handleOnServer(ServerPlayer sender) {
        ServerMarket.handlePacket(sender, this);
    }


}
