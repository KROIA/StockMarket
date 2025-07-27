package net.kroia.stockmarket.networking.packet.client_sender.update;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncBotSettingsPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class UpdateBotSettingsPacket extends NetworkPacket {

    ItemID itemID;
    ServerVolatilityBot.Settings settings;

    private boolean destroyBot;
    private boolean createBot;
    private boolean marketOpen;


    private UpdateBotSettingsPacket() {
        super();
    }
    public UpdateBotSettingsPacket(FriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(ItemID itemID, ServerVolatilityBot.Settings settings, boolean destroyBot, boolean createBot, boolean marketOpen)
    {
        UpdateBotSettingsPacket packet = new UpdateBotSettingsPacket();
        packet.itemID = itemID;
        packet.settings = settings;
        packet.destroyBot = destroyBot;
        packet.createBot = createBot;
        packet.marketOpen = marketOpen;
        StockMarketNetworking.sendToServer(packet);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(itemID.getStack());
        buf.writeBoolean(destroyBot);
        buf.writeBoolean(createBot);
        buf.writeBoolean(marketOpen);
        CompoundTag tag = new CompoundTag();
        settings.save(tag);
        buf.writeNbt(tag);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        itemID = new ItemID(buf.readItem());
        destroyBot = buf.readBoolean();
        createBot = buf.readBoolean();
        marketOpen = buf.readBoolean();
        settings = new ServerVolatilityBot.Settings();
        settings.load(buf.readNbt());

    }

    @Override
    protected void handleOnServer(ServerPlayer sender) {
        if(sender.hasPermissions(2)) {
            // Apply bot settings
            ServerTradingBot bot = ServerMarket.getTradingBot(itemID);
            if(bot == null && createBot)
            {
                bot = new ServerVolatilityBot();
                bot.setSettings(settings);
                ServerMarket.setTradingBot(itemID, bot);
            }
            else if(bot != null && destroyBot)
            {
                ServerMarket.removeTradingBot(itemID);
            }
            else {
                if (bot instanceof ServerVolatilityBot volatilityBot) {
                    volatilityBot.setSettings(settings);
                }
            }
            ServerMarket.setMarketOpen(itemID, marketOpen);
            SyncBotSettingsPacket.sendPacket(sender, itemID);
        }
    }
}
