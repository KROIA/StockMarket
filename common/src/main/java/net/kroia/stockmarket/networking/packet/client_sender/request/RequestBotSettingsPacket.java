package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBotSettingsPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestBotSettingsPacket extends NetworkPacket {

    String itemID;

    private RequestBotSettingsPacket() {
        super();
    }
    public RequestBotSettingsPacket(FriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(String itemID)
    {
        RequestBotSettingsPacket packet = new RequestBotSettingsPacket();
        packet.itemID = itemID;
        StockMarketNetworking.sendToServer(packet);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemID);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        itemID = buf.readUtf();
    }

    @Override
    protected void handleOnServer(ServerPlayer sender) {
        if(sender.hasPermissions(2)) {
            String id = ItemUtilities.getNormalizedItemID(itemID);
            if(id == null)
                return;

            SyncBotSettingsPacket.sendPacket(sender, itemID, ServerMarket.getBotUserUUID());
        }
    }
}
