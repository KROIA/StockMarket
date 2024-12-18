package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.networking.packet.NetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public class RequestOrderPacket extends NetworkPacket {

    private String itemID;
    private int amount;
    private int price;
    public enum OrderType {
        limit,
        market,
    }
    private OrderType orderType;

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

    public RequestOrderPacket(FriendlyByteBuf buf) {
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
        ModMessages.sendToServer(new RequestOrderPacket(itemID, amount, OrderType.limit, price));
    }
    public static void generateRequest(String itemID, int amount) {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestOrderPacket for item: "+itemID + " amount: "+amount);
        ModMessages.sendToServer(new RequestOrderPacket(itemID, amount));
    }

    @Override
    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeUtf(itemID);
        buf.writeInt(amount);
        buf.writeUtf(orderType.name());
        buf.writeInt(price);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf)
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
