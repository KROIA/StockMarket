package net.kroia.stockmarket.networking.packet.client_sender.request;

/*
public class RequestOrderPacket extends StockMarketNetworkPacket {

    private ItemID itemID;
    private ItemID currencyItemID;
    private int amount;
    private int price;
    public enum OrderType {
        limit,
        market,
    }
    private OrderType orderType;

    public RequestOrderPacket() {
        super();
        itemID = null;
        amount = 0;
    }
    public RequestOrderPacket(ItemID itemID, ItemID currencyItemID, int amount) {
        super();
        this.itemID = itemID;
        this.currencyItemID = currencyItemID;
        this.amount = amount;
        this.orderType = OrderType.market;
        this.price = 0;
    }
    public RequestOrderPacket(ItemID itemID, ItemID currencyItemID, int amount, OrderType orderType, int price) {
        super();
        this.itemID = itemID;
        this.currencyItemID = currencyItemID;
        this.amount = amount;
        this.orderType = orderType;
        this.price = price;
    }

    public RequestOrderPacket(FriendlyByteBuf buf) {
        super(buf);
    }

    public ItemID getItemID() {
        return itemID;
    }

    public ItemID getCurrencyItemID() {
        return currencyItemID;
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
    public static void generateRequest(ItemID itemID, ItemID currencyItemID, int amount, int price) {

        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestOrderPacket for item: "+itemID + " amount: "+amount);
        new RequestOrderPacket(itemID, currencyItemID, amount, OrderType.limit, price).sendToServer();
    }
    public static void generateRequest(ItemID itemID, ItemID currencyItemID, int amount) {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestOrderPacket for item: "+itemID + " amount: "+amount);
        new RequestOrderPacket(itemID, currencyItemID, amount).sendToServer();
    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeItem(itemID.getStack());
        buf.writeItem(currencyItemID.getStack());
        buf.writeInt(amount);
        buf.writeUtf(orderType.name());
        buf.writeInt(price);
    }

    @Override
    public void decode(FriendlyByteBuf buf)
    {
        this.itemID = new ItemID(buf.readItem());
        this.currencyItemID = new ItemID(buf.readItem());
        this.amount = buf.readInt();
        this.orderType = OrderType.valueOf(buf.readUtf());
        this.price = buf.readInt();
    }

    @Override
    protected void handleOnServer(ServerPlayer sender) {
        BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.handlePacket(sender, this);
    }


}
*/