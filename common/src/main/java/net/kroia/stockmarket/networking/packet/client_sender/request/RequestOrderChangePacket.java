package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestOrderChangePacket extends NetworkPacket {

    ItemID itemID;
    long targetOrderID;
    int newPrice;

    public RequestOrderChangePacket(ItemID itemID, long targetOrderID, int newPrice) {
        super();
        this.itemID = itemID;
        this.targetOrderID = targetOrderID;
        this.newPrice = newPrice;
    }

    public RequestOrderChangePacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public static void sendRequest(ItemID itemID, long targetOrderID, int newPrice) {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestOrderPacket for item: "+itemID + " amount: "+amount);
        StockMarketNetworking.sendToServer(new RequestOrderChangePacket(itemID, targetOrderID, newPrice));
    }

    public long getTargetOrderID() {
        return targetOrderID;
    }
    public int getNewPrice() {
        return newPrice;
    }
    public ItemID getItemID() {
        return itemID;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(itemID.getStack());
        buf.writeLong(targetOrderID);
        buf.writeInt(newPrice);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        itemID = new ItemID(buf.readItem());
        targetOrderID = buf.readLong();
        newPrice = buf.readInt();
    }

    protected void handleOnServer(ServerPlayer sender) {
        ServerMarket.handlePacket(sender, this);
    }
}
