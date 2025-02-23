package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBotSettingsPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBotTargetPricePacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestBotTargetPricePacket extends NetworkPacket {

    ItemID itemID;

    private RequestBotTargetPricePacket() {
        super();
    }
    public RequestBotTargetPricePacket(FriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(ItemID itemID)
    {
        RequestBotTargetPricePacket packet = new RequestBotTargetPricePacket();
        packet.itemID = itemID;
        StockMarketNetworking.sendToServer(packet);
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeItem(itemID.getStack());
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        itemID = new ItemID(buf.readItem());
    }

    @Override
    protected void handleOnServer(ServerPlayer sender) {
        if(sender.hasPermissions(2)) {
            ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
            if(bot == null)
            {
                return;
            }
            if(bot.getSettings() instanceof ServerVolatilityBot.Settings settings)
            {
                SyncBotTargetPricePacket.sendPacket(sender, settings.targetPrice);
            }
        }
    }
}
