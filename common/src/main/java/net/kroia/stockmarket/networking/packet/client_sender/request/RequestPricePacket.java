package net.kroia.stockmarket.networking.packet.client_sender.request;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.networking.NetworkPacketC2S;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestPricePacket extends NetworkPacketC2S {


    private String itemID;

    @Override
    public MessageType getType() {
        return StockMarketNetworking.REQUEST_PRICE;
    }
    public RequestPricePacket(String itemID) {
        super();
        this.itemID = itemID;
    }

    public RequestPricePacket(RegistryFriendlyByteBuf buf) {
        super(buf);
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(itemID);
    }
    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf) {
        this.itemID = buf.readUtf();
    }

    public static void generateRequest(String itemID) {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestPricePacket for item "+itemID);
        new RequestPricePacket(itemID).sendToServer();
    }

    public String getItemID() {
        return itemID;
    }

    @Override
    protected void handleOnServer(ServerPlayer sender)
    {
        ServerMarket.handlePacket(sender, this);
    }


}
