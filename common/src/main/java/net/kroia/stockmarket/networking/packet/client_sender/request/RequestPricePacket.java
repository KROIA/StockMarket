package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
public class RequestPricePacket extends NetworkPacket {


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
        StockMarketNetworking.sendToServer(new RequestPricePacket(itemID));
    }

    public ItemID getItemID() {
        return itemID;
    }

    @Override
    protected void handleOnServer(ServerPlayer sender)
    {
        ServerMarket.handlePacket(sender, this);
    }
}
