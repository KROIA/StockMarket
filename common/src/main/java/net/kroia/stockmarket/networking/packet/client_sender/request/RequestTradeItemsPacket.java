package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public class RequestTradeItemsPacket extends NetworkPacket {


    public RequestTradeItemsPacket()
    {
        super();
    }


    public RequestTradeItemsPacket(FriendlyByteBuf buf) {
        super(buf);
    }


    public static void generateRequest() {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestTradeItemsPacket");
        StockMarketNetworking.sendToServer(new RequestTradeItemsPacket());
    }



    @Override
    protected void handleOnServer(ServerPlayer sender) {
        ServerMarket.handlePacket(sender, this);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {

    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {

    }
}
