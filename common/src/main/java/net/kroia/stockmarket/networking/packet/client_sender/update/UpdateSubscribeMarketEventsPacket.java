package net.kroia.stockmarket.networking.packet.client_sender.update;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public class UpdateSubscribeMarketEventsPacket extends NetworkPacket {
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
    public void toBytes(FriendlyByteBuf buf)
    {
        buf.writeItem(itemID.getStack());
        buf.writeBoolean(subscribe);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf)
    {
        this.itemID = new ItemID(buf.readItem());
        this.subscribe = buf.readBoolean();
    }

    public static void generateRequest(ItemID itemID, boolean subscribe) {
        StockMarketNetworking.sendToServer(new UpdateSubscribeMarketEventsPacket(itemID, subscribe));
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
        ServerMarket.handlePacket(sender, this);
    }
}
