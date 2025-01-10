package net.kroia.stockmarket.networking.packet.client_sender.request;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.networking.NetworkPacketC2S;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public class RequestTradeItemsPacket extends NetworkPacketC2S {

    @Override
    public MessageType getType() {
        return StockMarketNetworking.REQUEST_TRADE_ITEMS;
    }
    public RequestTradeItemsPacket()
    {
        super();
    }




    public RequestTradeItemsPacket(RegistryFriendlyByteBuf buf) {
        super(buf);
    }


    public static void generateRequest() {
        new RequestTradeItemsPacket().sendToServer();
    }



    @Override
    protected void handleOnServer(ServerPlayer sender) {
        ServerMarket.handlePacket(sender, this);
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf) {

    }

    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf) {

    }
}
