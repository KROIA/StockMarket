package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public class RequestPricePacket extends StockMarketNetworkPacket {


    private ItemID itemID;
    public RequestPricePacket(ItemID itemID) {
        super();
        this.itemID = itemID;
    }

    public RequestPricePacket(FriendlyByteBuf buf) {
        super(buf);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(itemID.getStack());
    }
    @Override
    public void decode(FriendlyByteBuf buf) {
        this.itemID = new ItemID(buf.readItem());
    }

    public static void generateRequest(ItemID itemID) {
        //StockMarketMod.LOGGER.info("[CLIENT] Sending RequestPricePacket for item "+itemID);
        new RequestPricePacket(itemID).sendToServer();
    }

    public ItemID getItemID() {
        return itemID;
    }

    @Override
    protected void handleOnServer(ServerPlayer sender)
    {
        BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.handlePacket(sender, this);
    }
}
