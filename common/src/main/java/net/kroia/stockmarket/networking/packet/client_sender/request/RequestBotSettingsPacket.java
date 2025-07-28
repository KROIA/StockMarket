package net.kroia.stockmarket.networking.packet.client_sender.request;

/*
public class RequestBotSettingsPacket extends StockMarketNetworkPacket {

    ItemID itemID;

    private RequestBotSettingsPacket() {
        super();
    }
    public RequestBotSettingsPacket(FriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(ItemID itemID)
    {
        RequestBotSettingsPacket packet = new RequestBotSettingsPacket();
        packet.itemID = itemID;
        packet.sendToServer();
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(itemID.getStack());
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        itemID = new ItemID(buf.readItem());
    }

    @Override
    protected void handleOnServer(ServerPlayer sender) {
        if(sender.hasPermissions(2)) {
            SyncBotSettingsPacket.sendPacket(sender, itemID);
        }
    }
}
*/