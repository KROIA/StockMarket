package net.kroia.stockmarket.networking.packet.client_sender.update;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.networking.NetworkPacketC2S;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public class UpdateSubscribeMarketEventsPacket extends NetworkPacketC2S {
    private String itemID;
    private boolean subscribe;

    @Override
    public MessageType getType() {
        return StockMarketNetworking.UPDATE_SUBSCRIBE_MARKET_EVENTS;
    }

    public UpdateSubscribeMarketEventsPacket(String itemID, boolean subscribe) {
        super();
        this.itemID = itemID;
        this.subscribe = subscribe;
    }

    public UpdateSubscribeMarketEventsPacket(RegistryFriendlyByteBuf buf)
    {
        super(buf);

    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf)
    {
        buf.writeUtf(itemID);
        buf.writeBoolean(subscribe);
    }

    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf)
    {
        this.itemID = buf.readUtf();
        this.subscribe = buf.readBoolean();
    }

    public static void generateRequest(String itemID, boolean subscribe) {
        new UpdateSubscribeMarketEventsPacket(itemID, subscribe).sendToServer();
    }

    public String getItemID() {
        return itemID;
    }
    public boolean doesSubscribe() {
        return subscribe;
    }


    @Override
    protected void handleOnServer(ServerPlayer sender)
    {
        ServerMarket.handlePacket(sender, this);
    }


}
