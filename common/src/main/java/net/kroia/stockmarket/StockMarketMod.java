package net.kroia.stockmarket;

import com.mojang.logging.LogUtils;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.BankSystemAPI;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.command.StockMarketCommands;
import net.kroia.stockmarket.entity.StockMarketEntities;
import net.kroia.stockmarket.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.item.StockMarketItems;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.menu.StockMarketMenus;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.kroia.stockmarket.util.StockMarketDataHandler;
import net.kroia.stockmarket.util.StockMarketEvents;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.File;

public final class StockMarketMod {
    public static final String MOD_ID = "stockmarket";
    private static final Logger LOGGER = LogUtils.getLogger();


    public static BankSystemAPI BANK_SYSTEM_API = BankSystemMod.getAPI();
    public static StockMarketModSettings SERVER_SETTINGS;
    public static StockMarketDataHandler SERVER_DATA_HANDLER;
    public static StockMarketEvents SERVER_EVENTS;

    private static long lastTimeMS = 0;
    private static boolean bankSystemModLoaded = false;

    public static void init() {
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

    // Called from the client side
    public static void onClientSetup()
    {
        StockMarketMenus.setupScreens();
    }

    // Called from the server side
    public static void onServerSetup()
    {
        SERVER_EVENTS = new StockMarketEvents();
        //BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener(StockMarketMod::onBankSystemLoadedSingleShotEventReceiver, 1);
        BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener(()->{bankSystemModLoaded = true;}, 1);

    }

    // Called from the server side
    public static void onServerStart(MinecraftServer server) {
        SERVER_SETTINGS = new StockMarketModSettings();
        SERVER_DATA_HANDLER = new StockMarketDataHandler();

        TickEvent.SERVER_POST.register(StockMarketMod::onServerTick);

        if(bankSystemModLoaded)
        {
            onBankSystemLoadedSingleShotEventReceiver();
        }
        else
        {
            BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener(StockMarketMod::onBankSystemLoadedSingleShotEventReceiver, 1);
        }
    }

    // Called from the server side
    public static void onServerStop(MinecraftServer server) {
        TickEvent.SERVER_POST.unregister(StockMarketMod::onServerTick);
        // Save data to files when the server stops
        ServerMarket.disableAllTradingBots();
        saveDataToFiles(server);
        ServerMarket.clear();


        SERVER_SETTINGS = null;
        SERVER_DATA_HANDLER = null;
        SERVER_EVENTS.clearListeners();
    }

    private static void onBankSystemLoadedSingleShotEventReceiver()
    {
        StockMarketMod.loadDataFromFiles(UtilitiesPlatform.getServer());
        StockMarketModSettings.MarketBot.getBotBuilder(); // Create the default bot settings files if they don't exist
        ServerMarket.init();
    }


    // Called from the server side
    public static void onPlayerJoin(ServerPlayer player)
    {
        ServerPlayerList.addPlayer(player);
        SyncTradeItemsPacket.sendPacket(player);
    }
    // Called from the server side
    public static void onPlayerLeave(ServerPlayer player)
    {
        ServerMarket.removePlayerUpdateSubscription(player);
    }

    // Called from the server side
    private static void onServerTick(MinecraftServer server)
    {
        long currentTimeMillis = System.currentTimeMillis();

        ServerMarket.onServerTick(server);
        if(currentTimeMillis - lastTimeMS > StockMarketMod.SERVER_SETTINGS.MARKET.SHIFT_PRICE_CANDLE_INTERVAL_MS.get()) {
            lastTimeMS = currentTimeMillis;
            ServerMarket.shiftPriceHistory();
        }
        SERVER_DATA_HANDLER.tickUpdate();
    }

    public static void loadDataFromFiles(MinecraftServer server)
    {
        File rootSaveFolder = server.getWorldPath(LevelResource.ROOT).toFile();
        // Load data from the root save folder
        SERVER_DATA_HANDLER.setSaveFolder(rootSaveFolder);
        SERVER_DATA_HANDLER.loadAll();
    }
    public static void saveDataToFiles(MinecraftServer server)
    {
        File rootSaveFolder = server.getWorldPath(LevelResource.ROOT).toFile();
        // Load data from the root save folder
        SERVER_DATA_HANDLER.setSaveFolder(rootSaveFolder);
        SERVER_DATA_HANDLER.saveAll();
    }
    public static boolean isDataLoaded() {
        return SERVER_DATA_HANDLER.isLoaded();
    }




    public static void logInfo(String message) {
        boolean enabled = true;
        if(SERVER_SETTINGS != null)
            enabled = SERVER_SETTINGS.UTILITIES.LOGGING_ENABLE_INFO.get();
        if(enabled)
            LOGGER.info(message);
    }
    public static void logError(String message) {
        boolean enabled = true;
        if(SERVER_SETTINGS != null)
            enabled = SERVER_SETTINGS.UTILITIES.LOGGING_ENABLE_ERROR.get();
        if(enabled)
            LOGGER.error(message);
    }
    public static void logWarning(String message) {
        boolean enabled = true;
        if(SERVER_SETTINGS != null)
            enabled = SERVER_SETTINGS.UTILITIES.LOGGING_ENABLE_WARNING.get();
        if(enabled)
            LOGGER.warn(message);
    }
    public static void logDebug(String message) {
        boolean enabled = true;
        if(SERVER_SETTINGS != null)
            enabled = SERVER_SETTINGS.UTILITIES.LOGGING_ENABLE_DEBUG.get();
        if(enabled)
            LOGGER.debug(message);
    }
}
