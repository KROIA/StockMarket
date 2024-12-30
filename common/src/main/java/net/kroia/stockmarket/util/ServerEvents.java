package net.kroia.stockmarket.util;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public class ServerEvents {

    public static void onServerStart(MinecraftServer server) {
        ServerMarket.createBotUser();
        StockMarketMod.loadDataFromFiles(server);
        ServerMarket.init();
    }

    public static void onServerStop(MinecraftServer server) {
        ServerMarket.disableAllTradingBots();

        // Save data to the root save folder
        StockMarketMod.saveDataToFiles(server);

        // Cleanup
        ServerMarket.clear();
    }

    public static void onWorldSave(ServerLevel level) {
        if(level.dimension().equals(ServerLevel.OVERWORLD))
            StockMarketMod.saveDataToFiles(level.getServer());
    }

/*
    @SubscribeEvent
    public static void onServerStart(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            ResourceKey<Level> levelKey = serverLevel.dimension();

            // Only load data for the main overworld level
            if (levelKey.equals(ServerLevel.OVERWORLD)) {
                BankSystemMod.loadDataFromFiles(server);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStop(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            ResourceKey<Level> levelKey = serverLevel.dimension();

            // Only save data for the main overworld level
            if (levelKey.equals(ServerLevel.OVERWORLD)) {

                // Save data to the root save folder
                BankSystemMod.saveDataToFiles(server);

                // Cleanup
                ServerBankManager.clear();
            }
        }
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            BankSystemMod.saveDataToFiles(server);
        }
    }*/

}
