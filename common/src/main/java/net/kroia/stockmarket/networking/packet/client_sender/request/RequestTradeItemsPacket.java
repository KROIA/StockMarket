package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public class RequestTradeItemsPacket extends StockMarketNetworkPacket {


    public RequestTradeItemsPacket()
    {
        super();
    }


    public RequestTradeItemsPacket(FriendlyByteBuf buf) {
        super(buf);
    }


    public static void generateRequest() {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestTradeItemsPacket");
        new RequestTradeItemsPacket().sendToServer();
    }



    @Override
    protected void handleOnServer(ServerPlayer sender) {
        BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.handlePacket(sender, this);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {

    }

    @Override
    public void decode(FriendlyByteBuf buf) {

    }
}
