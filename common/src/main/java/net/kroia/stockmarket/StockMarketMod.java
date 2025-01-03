package net.kroia.stockmarket;

import com.mojang.logging.LogUtils;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.kroia.stockmarket.util.DataHandler;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.command.StockMarketCommands;
import net.kroia.stockmarket.entity.StockMarketEntities;
import net.kroia.stockmarket.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.item.StockMarketItems;
import net.kroia.stockmarket.menu.StockMarketMenus;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.File;

public final class StockMarketMod {
    public static final String MOD_ID = "stockmarket";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        StockMarketModSettings.init();
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> {
            StockMarketCommands.register(dispatcher);
        });
        StockMarketBlocks.init();
        StockMarketItems.init();
        StockMarketEntities.init();
        StockMarketMenus.init();
        StockMarketCreativeModeTab.init();
        StockMarketTextMessages.init();
        StockMarketNetworking.setupClientReceiverPackets();
        StockMarketNetworking.setupServerReceiverPackets();
    }

    public static void onClientSetup()
    {
        StockMarketMenus.setupScreens();

    }

    public static void onServerSetup()
    {
        //BankSystemNetworking.setupServerReceiverPackets();
    }

    public static void loadDataFromFiles(MinecraftServer server)
    {
        File rootSaveFolder = server.getWorldPath(LevelResource.ROOT).toFile();
        // Load data from the root save folder
        DataHandler.setSaveFolder(rootSaveFolder);
        DataHandler.loadAll();
    }
    public static void saveDataToFiles(MinecraftServer server)
    {
        File rootSaveFolder = server.getWorldPath(LevelResource.ROOT).toFile();
        // Load data from the root save folder
        DataHandler.setSaveFolder(rootSaveFolder);
        DataHandler.saveAll();
    }
    public static boolean isDataLoaded() {
        return DataHandler.isLoaded();
    }

}
