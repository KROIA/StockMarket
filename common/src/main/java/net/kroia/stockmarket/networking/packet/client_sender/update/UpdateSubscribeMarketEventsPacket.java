package net.kroia.stockmarket.networking.packet.client_sender.update;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public class UpdateSubscribeMarketEventsPacket extends StockMarketNetworkPacket {
    private ItemID itemID;
    private boolean subscribe;


    public UpdateSubscribeMarketEventsPacket(ItemID itemID, boolean subscribe) {
        super();
        this.itemID = itemID;
        this.subscribe = subscribe;
    }

    public UpdateSubscribeMarketEventsPacket(FriendlyByteBuf buf)
    {
        super(buf);

    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeItem(itemID.getStack());
        buf.writeBoolean(subscribe);
    }

    @Override
    public void decode(FriendlyByteBuf buf)
    {
        this.itemID = new ItemID(buf.readItem());
        this.subscribe = buf.readBoolean();
    }

    public static void generateRequest(ItemID itemID, boolean subscribe) {
        new UpdateSubscribeMarketEventsPacket(itemID, subscribe).sendToServer();
    }

    public ItemID getItemID() {
        return itemID;
    }
    public boolean doesSubscribe() {
        return subscribe;
    }


    @Override
    protected void handleOnServer(ServerPlayer sender)
    {
        BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.handlePacket(sender, this);
    }
}
