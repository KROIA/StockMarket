package net.kroia.stockmarket;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.BankSystemAPI;
import net.kroia.banksystem.util.BankSystemDataHandler;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.api.IClientMarketManager;
import net.kroia.stockmarket.api.IServerMarketManager;
import net.kroia.stockmarket.api.StockMarketAPI;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.command.StockMarketCommands;
import net.kroia.stockmarket.compat.NEZNAMY_TAB_Placeholders;
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
import net.kroia.stockmarket.player.ServerPlayerManager;
import net.kroia.stockmarket.plugin.ClientPluginManager;
import net.kroia.stockmarket.plugin.Plugins;
import net.kroia.stockmarket.plugin.ServerPluginManager;
import net.kroia.stockmarket.plugin.base.PluginRegistry;
import net.kroia.stockmarket.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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
        public ServerPluginManager SERVER_PLUGIN_MANAGER;
        public ClientPluginManager CLIENT_PLUGIN_MANAGER;
        public ServerPlayerManager SERVER_PLAYER_MANAGER;
        public ClientMarketManager CLIENT_MARKET_MANAGER;
        public StockMarketEvents SERVER_EVENTS;

        public StockMarketNetworking NETWORKING;


        public StockMarketModLogger LOGGER;
    }

    protected static final Instances INSTANCES = new Instances();


    StockMarketModBackend()
    {
        INSTANCES.BANK_SYSTEM_API = null;
        INSTANCES.SERVER_SETTINGS = null;
        INSTANCES.SERVER_DEFAULT_PRICES = null;
        INSTANCES.SERVER_DATA_HANDLER = null;
        INSTANCES.SERVER_MARKET_MANAGER = null;
        INSTANCES.SERVER_PLUGIN_MANAGER = null;
        INSTANCES.CLIENT_PLUGIN_MANAGER = null;
        INSTANCES.SERVER_PLAYER_MANAGER = null;
        INSTANCES.CLIENT_MARKET_MANAGER = null;
        INSTANCES.SERVER_EVENTS = null;
        INSTANCES.NETWORKING = null;
        INSTANCES.LOGGER = new StockMarketModLogger(INSTANCES);
        StockMarketDataHandler.setBackend(INSTANCES);
        ServerMarketManager.setBackend(INSTANCES);
        ServerPluginManager.setBackend(INSTANCES);
        ClientPluginManager.setBackend(INSTANCES);
        PluginRegistry.setBackend(INSTANCES);
        ServerPlayerManager.setBackend(INSTANCES);
        StockMarketModSettings.setBackend(INSTANCES);
        StockMarketCommands.setBackend(INSTANCES);
        StockMarketBlockEntity.setBackend(INSTANCES);
        StockMarketNetworkPacket.setBackend(INSTANCES);
        StockMarketGenericRequest.setBackend(INSTANCES);
        StockMarketTextMessages.setBackend(INSTANCES);
        TradingPair.setBackend(INSTANCES);
        Plugins.setBackend(INSTANCES);

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


    // Called from the client side when the player enters the main menu
    public static void onClientSetup()
    {
        StockMarketMenus.setupScreens();
        INSTANCES.CLIENT_MARKET_MANAGER = new ClientMarketManager(INSTANCES);
        INSTANCES.CLIENT_PLUGIN_MANAGER = new ClientPluginManager();

        StockMarketGuiElement.setBackend(INSTANCES);
        StockMarketGuiScreen.setBackend(INSTANCES);

        StockMarketMenus.setupScreens();
        Plugins.clientSetup();
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
        Plugins.serverSetup();
        //Plugins.serverSetup();
    }

    // Called from the server side
    public static void onServerStart(MinecraftServer server) {
        INSTANCES.SERVER_SETTINGS = new StockMarketModSettings();
        INSTANCES.SERVER_SETTINGS.setLogger(INSTANCES.LOGGER::error, INSTANCES.LOGGER::error, INSTANCES.LOGGER::debug);
        INSTANCES.SERVER_DEFAULT_PRICES = new DefaultMarketSettings.DefaultPrices();
        INSTANCES.SERVER_DEFAULT_PRICES.setLogger(INSTANCES.LOGGER::error, INSTANCES.LOGGER::error, INSTANCES.LOGGER::debug);

        INSTANCES.SERVER_DATA_HANDLER = new StockMarketDataHandler();
        File rootSaveFolder = server.getWorldPath(LevelResource.ROOT).toFile();
        INSTANCES.SERVER_DATA_HANDLER.setLevelSavePath(rootSaveFolder.toPath());
        INSTANCES.SERVER_MARKET_MANAGER = new ServerMarketManager(INSTANCES.SERVER_DATA_HANDLER.getOrderHistoryFolderPath());
        INSTANCES.SERVER_PLAYER_MANAGER = new ServerPlayerManager(INSTANCES.SERVER_DATA_HANDLER.getPlayerManagerFolderPath());
        INSTANCES.SERVER_PLUGIN_MANAGER = new ServerPluginManager(INSTANCES.SERVER_DATA_HANDLER.getPluginManagerFolderPath());

        NEZNAMY_TAB_Placeholders.setBackend(INSTANCES);



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

        // Save the data when the game saves the world
        LifecycleEvent.SERVER_LEVEL_SAVE.register((ServerLevel level) -> {
            if (level.dimension() == Level.OVERWORLD) {
                INSTANCES.SERVER_DATA_HANDLER.saveAllAsync();
            }
        });
    }

    // Called from the server side
    public static void onServerStop(MinecraftServer server) {
        TickEvent.SERVER_POST.unregister(StockMarketModBackend::onServerTick);
        saveDataToFiles(server);

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
        //ServerPlayerList.addPlayer(player);
        INSTANCES.SERVER_PLAYER_MANAGER.onPlayerJoin(player);
    }

    // Called from the server side
    public static void onPlayerLeave(ServerPlayer player)
    {
        INSTANCES.SERVER_PLAYER_MANAGER.onPlayerLeave(player);
    }

    // Called from the server side
    private static void onServerTick(MinecraftServer server)
    {
        if(INSTANCES.SERVER_MARKET_MANAGER.getMarketUpdateChunkIndex() == 0)
        {
            // Update only once after all market chunks have been updated
            INSTANCES.SERVER_PLUGIN_MANAGER.updatePlugins();
            INSTANCES.SERVER_PLUGIN_MANAGER.finalizePlugis();
        }
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



    @Override
    public String getModID() {
        return StockMarketMod.MOD_ID;
    }

    @Override
    public String getModVersion() {
        return StockMarketMod.VERSION;
    }

    @Override
    public IServerMarketManager getServerMarketManager() {
        return INSTANCES.SERVER_MARKET_MANAGER;
    }

    @Override
    public IClientMarketManager getClientMarketManager() {
        return INSTANCES.CLIENT_MARKET_MANAGER;
    }
}


