package net.kroia.stockmarket.util;

import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.banking.BankUser;
import net.kroia.banksystem.banking.ServerBankManager;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public class StockMarketServerEvents {
    private static long lastTimeMS = 0;

    public static void onServerStart(MinecraftServer server) {
        BankSystemMod.loadDataFromFiles(server);
        StockMarketMod.loadDataFromFiles(server);
        StockMarketModSettings.MarketBot.getBotBuilder(); // Create the default bot settings files if they don't exist
        ServerMarket.init();

        // Remove StockMarketBotBank if it exists since it is no longer in use
        UUID botUserUUID = UUID.nameUUIDFromBytes("StockMarketBot".getBytes());
        BankUser botUser = ServerBankManager.getUser(botUserUUID);
        if(botUser != null)
        {
            ServerMarket.cancelAllOrders(botUserUUID);
            ServerBankManager.removeUser(botUserUUID);
        }

        TickEvent.SERVER_POST.register(StockMarketServerEvents::onServerTick);
    }

    public static void onServerStop(MinecraftServer server) {
        TickEvent.SERVER_POST.unregister(StockMarketServerEvents::onServerTick);

        // Save data to the root save folder
        StockMarketMod.saveDataToFiles(server);

        ServerMarket.disableAllTradingBots();

        // Cleanup
        ServerMarket.clear();
    }

    public static void onWorldSave(ServerLevel level) {
        if(level.dimension().equals(ServerLevel.OVERWORLD))
            StockMarketMod.saveDataToFiles(level.getServer());
    }

    private static void onServerTick(MinecraftServer server)
    {
        long currentTimeMillis = System.currentTimeMillis();

        if(currentTimeMillis - lastTimeMS > ServerMarket.shiftPriceHistoryInterval) {
            lastTimeMS = currentTimeMillis;

            ServerMarket.shiftPriceHistory();
        }
    }
}
