package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.networking.packet.NetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;


import java.util.function.Supplier;

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
        ModMessages.sendToServer(new RequestTradeItemsPacket());
    }



    @Override
    protected void handleOnServer(ServerPlayer sender) {
        ServerMarket.handlePacket(sender, this);
    }
}
