package net.kroia.stockmarket.networking.packet.server_sender.update;

/*
public class SyncBotTargetPricePacket extends StockMarketNetworkPacket {

    int targetPrice;
    public SyncBotTargetPricePacket(int targetPrice) {
        super();
        this.targetPrice = targetPrice;
    }
    public SyncBotTargetPricePacket(FriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(ServerPlayer receiver, int targetPrice)
    {
        SyncBotTargetPricePacket packet = new SyncBotTargetPricePacket(targetPrice);
        packet.sendToClient(receiver);
    }

    public int getTargetPrice() {
        return targetPrice;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(targetPrice);

    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        targetPrice = buf.readInt();

    }

    @Override
    protected void handleOnClient() {
        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.handlePacket(this);
    }
}
*/