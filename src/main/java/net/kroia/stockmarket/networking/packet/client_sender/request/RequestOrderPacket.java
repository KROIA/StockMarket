package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestOrderPacket {

    private String itemID;
    private int amount;
    private int price;
    public enum OrderType {
        limit,
        market,
    }
    private OrderType orderType;

    public RequestOrderPacket() {
        itemID = "";
        amount = 0;
    }
    public RequestOrderPacket(String itemID, int amount) {
        this.itemID = itemID;
        this.amount = amount;
        this.orderType = OrderType.market;
        this.price = 0;
    }
    public RequestOrderPacket(String itemID, int amount, OrderType orderType, int price) {
        this.itemID = itemID;
        this.amount = amount;
        this.orderType = orderType;
        this.price = price;
    }

    public RequestOrderPacket(FriendlyByteBuf buf) {
        this.itemID = buf.readUtf();
        this.amount = buf.readInt();
        this.orderType = OrderType.valueOf(buf.readUtf());
        this.price = buf.readInt();
    }

    public String getItemID() {
        return itemID;
    }
    public int getAmount() {
        return amount;
    }
    public OrderType getOrderType() {
        return orderType;
    }
    public int getPrice() {
        return price;
    }
    public static void generateRequest(String itemID, int amount, int price) {

        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestOrderPacket for item: "+itemID + " amount: "+amount);
        ModMessages.sendToServer(new RequestOrderPacket(itemID, amount, OrderType.limit, price));
    }
    public static void generateRequest(String itemID, int amount) {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestOrderPacket for item: "+itemID + " amount: "+amount);
        ModMessages.sendToServer(new RequestOrderPacket(itemID, amount));
    }

    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeUtf(itemID);
        buf.writeInt(amount);
        buf.writeUtf(orderType.name());
        buf.writeInt(price);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // Check if on server_sender or client
        if(contextSupplier.get().getDirection().getReceptionSide().isClient()) {
            //StockMarketMod.LOGGER.info("[CLIENT] Received current prices from the server_sender");
            // HERE WE ARE ON THE CLIENT!
            // Update client-side data
            // Get the data from the packet
            //MarketData.setPrice(this.itemID, this.price);
            context.setPacketHandled(true);
            return;
        }


        context.enqueueWork(() -> {
            // HERE WE ARE ON THE SERVER!
            // Update client-side data
            ServerMarket.handlePacket(context.getSender(), this);



            // Send the packet to the client
            //SyncPricePacket.sendPacket(itemID, player);

        });
        context.setPacketHandled(true);
    }
}
