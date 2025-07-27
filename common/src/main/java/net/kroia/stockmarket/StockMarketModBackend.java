package net.kroia.stockmarket;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.BankSystemAPI;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.api.StockMarketAPI;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.command.StockMarketCommands;
import net.kroia.stockmarket.entity.StockMarketEntities;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.item.StockMarketItems;
import net.kroia.stockmarket.market.client.ClientStockMarketManager;
import net.kroia.stockmarket.market.server.*;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.menu.StockMarketMenus;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.kroia.stockmarket.screen.custom.BotSettingsScreen;
import net.kroia.stockmarket.screen.custom.StockMarketManagementScreen;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.kroia.stockmarket.screen.custom.botsetup.BotSetupScreen;
import net.kroia.stockmarket.screen.uiElements.*;
import net.kroia.stockmarket.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;


public class StockMarketModBackend implements StockMarketAPI {

    public static class Instances
    {
        public BankSystemAPI BANK_SYSTEM_API;
        public StockMarketModSettings SERVER_SETTINGS;
        public StockMarketDataHandler SERVER_DATA_HANDLER;
        public ServerStockMarketManager SERVER_STOCKMARKET_MANAGER;
        public ClientStockMarketManager CLIENT_STOCKMARKET_MANAGER;
        public StockMarketEvents SERVER_EVENTS;

        public StockMarketNetworking NETWORKING;


        public StockMarketModLogger LOGGER;
    }

    private static boolean bankSystemModLoaded = false;
    private static long lastTimeMS = 0;
    private static Instances INSTANCES = new Instances();


    StockMarketModBackend()
    {

        INSTANCES.LOGGER = new StockMarketModLogger(INSTANCES);
        StockMarketDataHandler.setBackend(INSTANCES);
        ServerStockMarketManager.setBackend(INSTANCES);
        StockMarketModSettings.setBackend(INSTANCES);
        StockMarketCommands.setBackend(INSTANCES);
        StockMarketBlockEntity.setBackend(INSTANCES);
        StockMarketNetworkPacket.setBackend(INSTANCES);
        StockMarketGenericRequest.setBackend(INSTANCES);
        StockMarketTextMessages.setBackend(INSTANCES);

        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> {
            StockMarketCommands.register(dispatcher);
        });



        StockMarketBlocks.init();
        StockMarketItems.init();
        StockMarketEntities.init();
        StockMarketMenus.init();
        StockMarketCreativeModeTab.init();
        StockMarketTextMessages.init();


        INSTANCES.NETWORKING = new StockMarketNetworking();
        INSTANCES.BANK_SYSTEM_API = BankSystemMod.getAPI();


    }


    // Called from the client side
    public static void onClientSetup()
    {
        StockMarketMenus.setupScreens();
        INSTANCES.CLIENT_STOCKMARKET_MANAGER = new ClientStockMarketManager(INSTANCES);

        BotSetupScreen.setBackend(INSTANCES);
        BotSettingsScreen.setBackend(INSTANCES);
        StockMarketManagementScreen.setBackend(INSTANCES);
        TradeScreen.setBackend(INSTANCES);
        CandleStickChart.setBackend(INSTANCES);
        LimitOrderInChartDisplay.setBackend(INSTANCES);
        OrderListView.setBackend(INSTANCES);
        TradePanel.setBackend(INSTANCES);
        OrderView.setBackend(INSTANCES);

        StockMarketMenus.setupScreens();
    }

    // Called from the server side
    public static void onServerSetup()
    {
        if(INSTANCES.SERVER_EVENTS == null)
            INSTANCES.SERVER_EVENTS = new StockMarketEvents();
        INSTANCES.BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener(()->{bankSystemModLoaded = true;}, 1);
        Order.setBackend(INSTANCES);
        ServerTradingBot.setBackend(INSTANCES);
        MatchingEngine.setBackend(INSTANCES);
        TradeManager.setBackend(INSTANCES);
        ServerTradeItem.setBackend(INSTANCES);
        DefaultMarketBotSettings.setBackend(INSTANCES);
        TransactionEngine.setBackend(INSTANCES);
    }

    // Called from the server side
    public static void onServerStart(MinecraftServer server) {
        INSTANCES.SERVER_SETTINGS = new StockMarketModSettings();
        INSTANCES.SERVER_SETTINGS.setLogger((msg)->{INSTANCES.LOGGER.error(msg);}, (msg, e)->{INSTANCES.LOGGER.error(msg, e);}, (msg)->{INSTANCES.LOGGER.info(msg);});

        INSTANCES.SERVER_DATA_HANDLER = new StockMarketDataHandler();
        INSTANCES.SERVER_STOCKMARKET_MANAGER = new ServerStockMarketManager();



        loadDataFromFiles(server);
        TickEvent.SERVER_POST.register(StockMarketModBackend::onServerTick);

        if(bankSystemModLoaded)
        {
            onBankSystemLoadedSingleShotEventReceiver();
        }
        else
        {
            INSTANCES.BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener(StockMarketModBackend::onBankSystemLoadedSingleShotEventReceiver, 1);
        }
    }

    // Called from the server side
    public static void onServerStop(MinecraftServer server) {
        TickEvent.SERVER_POST.unregister(StockMarketModBackend::onServerTick);
        saveDataToFiles(server);
        INSTANCES.SERVER_SETTINGS = null;
        INSTANCES.SERVER_DATA_HANDLER = null;
        INSTANCES.SERVER_STOCKMARKET_MANAGER = null;
        INSTANCES.SERVER_EVENTS.removeListeners();
    }

    private static void onBankSystemLoadedSingleShotEventReceiver()
    {
        loadDataFromFiles(UtilitiesPlatform.getServer());
        INSTANCES.SERVER_SETTINGS.MARKET_BOT.getBotBuilder(); // Create the default bot settings files if they don't exist
        INSTANCES.SERVER_STOCKMARKET_MANAGER = new ServerStockMarketManager();
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
        INSTANCES.SERVER_STOCKMARKET_MANAGER.removePlayerUpdateSubscription(player);
    }

    // Called from the server side
    private static void onServerTick(MinecraftServer server)
    {
        long currentTimeMillis = System.currentTimeMillis();

        INSTANCES.SERVER_STOCKMARKET_MANAGER.onServerTick(server);
        if(currentTimeMillis - lastTimeMS > INSTANCES.SERVER_SETTINGS.MARKET.SHIFT_PRICE_CANDLE_INTERVAL_MS.get()) {
            lastTimeMS = currentTimeMillis;
            INSTANCES.SERVER_STOCKMARKET_MANAGER.shiftPriceHistory();
        }
        INSTANCES.SERVER_DATA_HANDLER.tickUpdate();
    }

    public static void loadDataFromFiles(MinecraftServer server)
    {
        File rootSaveFolder = server.getWorldPath(LevelResource.ROOT).toFile();
        // Load data from the root save folder
        INSTANCES.SERVER_DATA_HANDLER.setSaveFolder(rootSaveFolder);
        INSTANCES.SERVER_DATA_HANDLER.loadAll();
    }
    public static void saveDataToFiles(MinecraftServer server)
    {
        File rootSaveFolder = server.getWorldPath(LevelResource.ROOT).toFile();
        // Load data from the root save folder
        INSTANCES.SERVER_DATA_HANDLER.setSaveFolder(rootSaveFolder);
        INSTANCES.SERVER_DATA_HANDLER.saveAll();
    }
}


