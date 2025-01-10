package net.kroia.stockmarket.networking.packet.client_sender.request;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.networking.NetworkPacketC2S;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public class RequestOrderPacket extends NetworkPacketC2S {

    private String itemID;
    private int amount;
    private int price;
    public enum OrderType {
        limit,
        market,
    }
    private OrderType orderType;

    @Override
    public MessageType getType() {
        return StockMarketNetworking.REQUEST_ORDER;
    }

    public RequestOrderPacket() {
        super();
        itemID = "";
        amount = 0;
    }



    public RequestOrderPacket(String itemID, int amount) {
        super();
        this.itemID = itemID;
        this.amount = amount;
        this.orderType = OrderType.market;
        this.price = 0;
    }
    public RequestOrderPacket(String itemID, int amount, OrderType orderType, int price) {
        super();
        this.itemID = itemID;
        this.amount = amount;
        this.orderType = orderType;
        this.price = price;
    }

    public RequestOrderPacket(RegistryFriendlyByteBuf buf) {
        super(buf);
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
        new RequestOrderPacket(itemID, amount, OrderType.limit, price).sendToServer();
    }
    public static void generateRequest(String itemID, int amount) {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestOrderPacket for item: "+itemID + " amount: "+amount);
        new RequestOrderPacket(itemID, amount).sendToServer();
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf)
    {
        buf.writeUtf(itemID);
        buf.writeInt(amount);
        buf.writeUtf(orderType.name());
        buf.writeInt(price);
    }

    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf)
    {
        this.itemID = buf.readUtf();
        this.amount = buf.readInt();
        this.orderType = OrderType.valueOf(buf.readUtf());
        this.price = buf.readInt();
    }

    @Override
    protected void handleOnServer(ServerPlayer sender) {
        ServerMarket.handlePacket(sender, this);
    }


}
