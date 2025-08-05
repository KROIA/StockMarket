package net.kroia.stockmarket;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.BankSystemAPI;
import net.kroia.banksystem.util.BankSystemDataHandler;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.api.StockMarketAPI;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.command.StockMarketCommands;
import net.kroia.stockmarket.entity.StockMarketEntities;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.item.StockMarketItems;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.client.ClientMarketManager;
import net.kroia.stockmarket.market.server.*;
import net.kroia.stockmarket.market.server.bot.ServerTradingBot;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.market.server.order.OrderFactory;
import net.kroia.stockmarket.menu.StockMarketMenus;
import net.kroia.stockmarket.networking.StockMarketNetworking;
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
        public DefaultMarketSettings.DefaultPrices SERVER_DEFAULT_PRICES;
        public StockMarketDataHandler SERVER_DATA_HANDLER;
        public ServerMarketManager SERVER_MARKET_MANAGER;
        public ClientMarketManager CLIENT_MARKET_MANAGER;
        public StockMarketEvents SERVER_EVENTS;

        public StockMarketNetworking NETWORKING;


        public StockMarketModLogger LOGGER;
    }

    private static final Instances INSTANCES = new Instances();


    StockMarketModBackend()
    {
        INSTANCES.LOGGER = new StockMarketModLogger(INSTANCES);
        StockMarketDataHandler.setBackend(INSTANCES);
        ServerMarketManager.setBackend(INSTANCES);
        StockMarketModSettings.setBackend(INSTANCES);
        StockMarketCommands.setBackend(INSTANCES);
        StockMarketBlockEntity.setBackend(INSTANCES);
        StockMarketNetworkPacket.setBackend(INSTANCES);
        StockMarketGenericRequest.setBackend(INSTANCES);
        StockMarketTextMessages.setBackend(INSTANCES);
        TradingPair.setBackend(INSTANCES);

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
        INSTANCES.CLIENT_MARKET_MANAGER = new ClientMarketManager(INSTANCES);

        StockMarketGuiElement.setBackend(INSTANCES);
        StockMarketGuiScreen.setBackend(INSTANCES);

        StockMarketMenus.setupScreens();
    }

    // Called from the server side
    public static void onServerSetup()
    {
        if(INSTANCES.SERVER_EVENTS == null)
            INSTANCES.SERVER_EVENTS = new StockMarketEvents();
        Order.setBackend(INSTANCES);
        ServerTradingBot.setBackend(INSTANCES);
        MatchingEngine.setBackend(INSTANCES);
        DefaultMarketSettings.setBackend(INSTANCES);
        TransactionEngine.setBackend(INSTANCES);
        OrderFactory.setBackend(INSTANCES);
        ServerMarket.setBackend(INSTANCES);
        MarketFactory.setBackend(INSTANCES);
        VirtualOrderBook.setBackend(INSTANCES);
    }

    // Called from the server side
    public static void onServerStart(MinecraftServer server) {
        INSTANCES.SERVER_SETTINGS = new StockMarketModSettings();
        INSTANCES.SERVER_SETTINGS.setLogger((msg)->{INSTANCES.LOGGER.error(msg);}, (msg, e)->{INSTANCES.LOGGER.error(msg, e);}, (msg)->{INSTANCES.LOGGER.info(msg);});
        INSTANCES.SERVER_DEFAULT_PRICES = new DefaultMarketSettings.DefaultPrices();

        INSTANCES.SERVER_DATA_HANDLER = new StockMarketDataHandler();
        INSTANCES.SERVER_MARKET_MANAGER = new ServerMarketManager();



        TickEvent.SERVER_POST.register(StockMarketModBackend::onServerTick);

        if(BankSystemDataHandler.isBankDataLoaded())
        {
            BankSystemDataHandler.resetBankDataLoaded();
            onPostBankSystemDataLoaded();
        }
        else
        {
            INSTANCES.BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener(StockMarketModBackend::onPostBankSystemDataLoaded, 1);
        }
    }

    // Called from the server side
    public static void onServerStop(MinecraftServer server) {
        TickEvent.SERVER_POST.unregister(StockMarketModBackend::onServerTick);
        saveDataToFiles(server);
        INSTANCES.SERVER_SETTINGS = null;
        INSTANCES.SERVER_DATA_HANDLER = null;
        INSTANCES.SERVER_MARKET_MANAGER = null;
        INSTANCES.SERVER_EVENTS.removeListeners();
    }

    private static void onPostBankSystemDataLoaded()
    {
        if(INSTANCES.SERVER_SETTINGS != null) {
            loadDataFromFiles(UtilitiesPlatform.getServer());
            DefaultMarketSettings.createDefaultMarketSettingsIfNotExist();
        }
    }

    // Called from the server side
    public static void onPlayerJoin(ServerPlayer player)
    {
        ServerPlayerList.addPlayer(player);
    }

    // Called from the server side
    public static void onPlayerLeave(ServerPlayer player)
    {

    }

    // Called from the server side
    private static void onServerTick(MinecraftServer server)
    {
        INSTANCES.SERVER_MARKET_MANAGER.onServerTick(server);
        INSTANCES.SERVER_DATA_HANDLER.tickUpdate();
    }

    public static void loadDataFromFiles(MinecraftServer server)
    {
        File rootSaveFolder = server.getWorldPath(LevelResource.ROOT).toFile();
        // Load data from the root save folder
        INSTANCES.SERVER_DATA_HANDLER.setLevelSavePath(rootSaveFolder.toPath());
        INSTANCES.SERVER_DATA_HANDLER.loadAll();
    }
    public static void saveDataToFiles(MinecraftServer server)
    {
        File rootSaveFolder = server.getWorldPath(LevelResource.ROOT).toFile();
        // Load data from the root save folder
        INSTANCES.SERVER_DATA_HANDLER.setLevelSavePath(rootSaveFolder.toPath());
        INSTANCES.SERVER_DATA_HANDLER.saveAll();
    }
}


