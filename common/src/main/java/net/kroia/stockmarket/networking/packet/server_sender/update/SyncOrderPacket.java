package net.kroia.stockmarket.networking.packet.server_sender.update;

/*
public class SyncOrderPacket extends StockMarketNetworkPacket {

    private Order order;

    public SyncOrderPacket(Order order) {
        super();
        this.order = order;
    }
    public SyncOrderPacket(FriendlyByteBuf buf)
    {
        super(buf);

    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        order.toBytes(buf);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        this.order = Order.construct(buf);
    }

    public static void sendResponse(Order order) {
        ServerPlayer player =  ServerPlayerList.getPlayer(order.getPlayerUUID());
        if(player == null)
        {
            BACKEND_INSTANCES.LOGGER.warn("[SERVER] Player not found for order: "+order.toString());
            return;
        }
        BACKEND_INSTANCES.NETWORKING.sendToClient(player, new SyncOrderPacket(order));
    }

    public Order getOrder() {
        return order;
    }

    @Override
    protected void handleOnClient() {
        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.handlePacket(this);
    }
}
*/