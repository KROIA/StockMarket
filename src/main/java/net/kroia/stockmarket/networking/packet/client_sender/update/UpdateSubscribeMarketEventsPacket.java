package net.kroia.stockmarket.networking.packet.client_sender.update;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.networking.packet.NetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.function.Supplier;

public class UpdateSubscribeMarketEventsPacket extends NetworkPacket {
    private String itemID;
    private boolean subscribe;

    public UpdateSubscribeMarketEventsPacket(String itemID, boolean subscribe) {
        super();
        this.itemID = itemID;
        this.subscribe = subscribe;
    }

    public UpdateSubscribeMarketEventsPacket(FriendlyByteBuf buf)
    {
        super(buf);

    }

    @Override
    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeUtf(itemID);
        buf.writeBoolean(subscribe);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf)
    {
        this.itemID = buf.readUtf();
        this.subscribe = buf.readBoolean();
    }

    public static void generateRequest(String itemID, boolean subscribe) {
        /*StockMarketMod.LOGGER.info("[CLIENT] Sending UpdateSubscribeMarketEventsPacket for item "+itemID+
                " to "+(subscribe ? "subscribe" : "unsubscribe"));*/
        ModMessages.sendToServer(new UpdateSubscribeMarketEventsPacket(itemID, subscribe));
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
