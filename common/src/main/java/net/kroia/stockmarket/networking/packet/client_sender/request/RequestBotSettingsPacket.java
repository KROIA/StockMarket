package net.kroia.stockmarket.networking.packet.client_sender.request;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.ItemUtilities;
import net.kroia.modutilities.networking.NetworkPacketC2S;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBotSettingsPacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestBotSettingsPacket extends NetworkPacketC2S {

    String itemID;

    @Override
    public MessageType getType() {
        return StockMarketNetworking.REQUEST_BOT_SETTINGS;
    }
    private RequestBotSettingsPacket() {
        super();
    }
    public RequestBotSettingsPacket(RegistryFriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(String itemID)
    {
        RequestBotSettingsPacket packet = new RequestBotSettingsPacket();
        packet.itemID = itemID;
        packet.sendToServer();
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(itemID);
    }

    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf) {
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
