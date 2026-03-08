package net.kroia.stockmarket;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.BankSystemAPI;
import net.kroia.banksystem.block.BankSystemBlocks;
import net.kroia.banksystem.entity.BankSystemEntities;
import net.kroia.banksystem.item.BankSystemCreativeModeTab;
import net.kroia.banksystem.item.BankSystemItems;
import net.kroia.banksystem.menu.BankSystemMenus;
import net.kroia.banksystem.util.BankSystemDataHandler;
import net.kroia.banksystem.util.BankSystemTextMessages;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.streaming.StreamSystem;
import net.kroia.stockmarket.api.StockMarketAPI;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.command.StockMarketCommands;
import net.kroia.stockmarket.compat.NEZNAMY_TAB_Placeholders;
import net.kroia.stockmarket.data.table.MarketPriceManager;
import net.kroia.stockmarket.entity.StockMarketEntities;
import net.kroia.stockmarket.event.EventRegistration;
import net.kroia.stockmarket.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.item.StockMarketItems;
import net.kroia.stockmarket.market.orders.Order;
import net.kroia.stockmarket.market.server.Market;
import net.kroia.stockmarket.market.server.MarketManager;
import net.kroia.stockmarket.market.server.Testing;
import net.kroia.stockmarket.menu.StockMarketMenus;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.util.StockMarketLogger;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StockMarketModBackend implements StockMarketAPI {

    public static class Instances
    {
        public BankSystemAPI BANK_SYSTEM_API;
        public StockMarketModSettings SERVER_SETTINGS;
        public MarketManager MARKET_MANAGER;

        public MarketPriceManager MARKET_PRICE_HISTORY_MANAGER;

        public StockMarketNetworking NETWORKING;
        public StockMarketLogger LOGGER;
    }

    private static Instances INSTANCES = new Instances();

    StockMarketModBackend()
    {
        INSTANCES.BANK_SYSTEM_API = null;
        INSTANCES.NETWORKING = null;
        INSTANCES.LOGGER = new StockMarketLogger(INSTANCES);


        Testing.setBackend(INSTANCES);
        StockMarketModSettings.setBackend(INSTANCES);
        StockMarketTextMessages.setBackend(INSTANCES);
        MarketManager.setBackend(INSTANCES);
        StockMarketNetworking.setBackend(INSTANCES);

        StockMarketBlocks.init();
        StockMarketItems.init();
        StockMarketEntities.init();
        StockMarketMenus.init();
        StockMarketCreativeModeTab.init();
        StockMarketTextMessages.init();

        EventRegistration.init();

        INSTANCES.BANK_SYSTEM_API = BankSystemMod.getAPI();
        INSTANCES.NETWORKING = new StockMarketNetworking();
    }



    // Called from the client side
    public static void onClientSetup()
    {
        StockMarketMenus.setupScreens();


        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(StockMarketModBackend::onPlayerLeaveClientSide);
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(StockMarketModBackend::onPlayerJoinClientSide);
        ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.register(StockMarketModBackend::onPlayerRespawnClientSide);
    }

    // Called from the server side
    public static void onServerSetup()
    {

    }


    // Called from the server side
    public static void onServerStart(MinecraftServer server)
    {
        INSTANCES.SERVER_SETTINGS = new StockMarketModSettings();
        INSTANCES.SERVER_SETTINGS.setLogger(INSTANCES.LOGGER::error, INSTANCES.LOGGER::error, INSTANCES.LOGGER::debug);
        INSTANCES.MARKET_PRICE_HISTORY_MANAGER = new MarketPriceManager();
        INSTANCES.MARKET_MANAGER = new MarketManager();

        NEZNAMY_TAB_Placeholders.setBackend(INSTANCES);
        StockMarketCommands.setBackend(INSTANCES);

        loadDataFromFiles(server);

        TickEvent.SERVER_POST.register(StockMarketModBackend::onServerTick);



        //INSTANCES.BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener();

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
    private static void onPostBankSystemDataLoaded()
    {
        Testing testing = new Testing();
        if(!testing.setup())
        {
            INSTANCES.LOGGER.info("Can't setup testing");
            return;
        }
        if(testing.runTests())
        {
            INSTANCES.LOGGER.info("All tests executed successfully");
        }
        else
        {
            INSTANCES.LOGGER.info("Some tests failed");
            testing.runTests();
        }
        //ItemID id = ItemID.getOrRegisterFromItemStack(Items.GOLD_INGOT.getDefaultInstance());
        //testMarket = new Market(id);
//
        //Order botOrder1 = new Order(id, Order.Type.LIMIT, -3, 12, 0, UUID.randomUUID(), 1);
        //testMarket.putOrder(botOrder1);
    }


    // Called from the server side
    public static void onServerStop(MinecraftServer server)
    {
        TickEvent.SERVER_POST.unregister(StockMarketModBackend::onServerTick);
        saveDataToFiles(server);

    }


    // Called from the server side
    public static void onPlayerJoin(ServerPlayer player)
    {

    }


    // Called from the server side
    public static void onPlayerLeave(ServerPlayer player)
    {

    }

    // Called from the client side
    private static void onPlayerJoinClientSide(@Nullable LocalPlayer localPlayer)
    {

    }
    private static void onPlayerRespawnClientSide(LocalPlayer oldPlayer, LocalPlayer newPlayer)
    {
        StreamSystem.startServerToClientStream(INSTANCES.NETWORKING.MARKET_PRICE_STREAM, ItemID.of(Items.GOLD_INGOT.getDefaultInstance()), (price)->
        {
            INSTANCES.LOGGER.info("Price received: " + price);
        },()->
        {
            // Stream stoppedz
        });
    }
    // Called from the client side
    private static void onPlayerLeaveClientSide(@Nullable LocalPlayer localPlayer)
    {

    }

    // Called from the server side
    private static void onServerTick(MinecraftServer server)
    {
        INSTANCES.MARKET_MANAGER.update();
    }

    public static void loadDataFromFiles(MinecraftServer server)
    {
        Path rootSaveFolder = server.getWorldPath(LevelResource.ROOT);
        // Load data from the root save folder


    }
    public static void saveDataToFiles(MinecraftServer server)
    {
        Path rootSaveFolder = server.getWorldPath(LevelResource.ROOT);
        // Load data from the root save folder

    }



    @Override
    public String getModID()
    {
        return StockMarketMod.MOD_ID;
    }

    @Override
    public String getModVersion() {
        return StockMarketMod.VERSION;
    }
}
