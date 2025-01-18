package net.kroia.stockmarket.networking.packet.client_sender.update;

import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class UpdateBotSettingsPacket extends NetworkPacket {

    String itemID;
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

    public static void sendPacket(String itemID, ServerVolatilityBot.Settings settings, boolean destroyBot, boolean createBot, boolean marketOpen)
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
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemID);
        buf.writeBoolean(destroyBot);
        buf.writeBoolean(createBot);
        buf.writeBoolean(marketOpen);
        CompoundTag tag = new CompoundTag();
        settings.save(tag);
        buf.writeNbt(tag);
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        itemID = buf.readUtf();
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
        }
    }
}
