package net.kroia.stockmarket.networking.packet.client_sender.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBotTargetPricePacket;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class RequestBotTargetPricePacket extends StockMarketNetworkPacket {

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
        packet.sendToServer();
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(itemID.getStack());
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        itemID = new ItemID(buf.readItem());
    }

    @Override
    protected void handleOnServer(ServerPlayer sender) {
        if(sender.hasPermissions(2)) {
            ServerTradingBot bot = BACKEND_INSTANCES.SERVER_STOCKMARKET_MANAGER.getTradingBot(itemID);
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
