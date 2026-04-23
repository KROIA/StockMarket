package net.kroia.stockmarket;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.BankSystemAPI;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.networking.multi_server.MultiServerManager;
import net.kroia.stockmarket.api.StockMarketAPI;
import net.kroia.stockmarket.minecraft.block.StockMarketBlocks;
import net.kroia.stockmarket.minecraft.command.StockMarketCommands;
import net.kroia.stockmarket.minecraft.compat.NEZNAMY_TAB_Placeholders;
import net.kroia.stockmarket.data.DataManager;
import net.kroia.stockmarket.data.table.MarketPriceManager;
import net.kroia.stockmarket.minecraft.entity.StockMarketEntities;
import net.kroia.stockmarket.event.EventRegistration;
import net.kroia.stockmarket.minecraft.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.minecraft.item.StockMarketItems;
import net.kroia.stockmarket.stockmarket.marketmanager.ClientMarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.stockmarket.market.core.Testing;
import net.kroia.stockmarket.minecraft.menu.StockMarketMenus;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.PlayerJoinSyncPacket;
import net.kroia.stockmarket.util.ClientSettings;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.kroia.stockmarket.util.StockMarketLogger;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public class StockMarketModBackend implements StockMarketAPI {


    public static class CommonInstances
    {
        public BankSystemAPI BANK_SYSTEM_API;
        public StockMarketLogger LOGGER;
        public StockMarketNetworking NETWORKING;
    }

    public static class ServerInstances
    {
        public BankSystemAPI BANK_SYSTEM_API;
        public StockMarketModSettings SERVER_SETTINGS;
        public DataManager DATA_MANAGER;
        public MarketManager MARKET_MANAGER;

        public MarketPriceManager MARKET_PRICE_HISTORY_MANAGER;

        public StockMarketNetworking NETWORKING;
        public StockMarketLogger LOGGER;
    }
    public static class ClientInstances
    {
        public BankSystemAPI BANK_SYSTEM_API;

        public ClientMarketManager MARKET_MANAGER;
        public StockMarketNetworking NETWORKING;
        public StockMarketLogger LOGGER;
        public ClientSettings SETTINGS;
    }

    private static final CommonInstances COMMON_INSTANCES = new CommonInstances();
    private static ServerInstances SERVER_INSTANCES = null;
    private static ClientInstances CLIENT_INSTANCES = null;

    StockMarketModBackend()
    {
        COMMON_INSTANCES.LOGGER = new StockMarketLogger();
        COMMON_INSTANCES.NETWORKING = new StockMarketNetworking();



        StockMarketBlocks.init();
        StockMarketItems.init();
        StockMarketEntities.init();
        StockMarketMenus.init();
        StockMarketCreativeModeTab.init();
        StockMarketTextMessages.init();

        EventRegistration.init();

        StockMarketNetworking.setBackend(COMMON_INSTANCES);

    }



    // Called from the client side
    public static void onClientSetup()
    {

        StockMarketMenus.setupScreens();



        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(StockMarketModBackend::onPlayerLeaveClientSide);
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(StockMarketModBackend::onPlayerJoinClientSide);
        ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.register(StockMarketModBackend::onPlayerRespawnClientSide);
        ClientTickEvent.CLIENT_LEVEL_POST.register(StockMarketModBackend::onClientTickEvent);
    }

    // Called from the server side
    public static void onServerSetup()
    {
        SERVER_INSTANCES =  new ServerInstances();
        SERVER_INSTANCES.BANK_SYSTEM_API = BankSystemMod.getAPI();
        SERVER_INSTANCES.LOGGER = COMMON_INSTANCES.LOGGER;
        SERVER_INSTANCES.NETWORKING = COMMON_INSTANCES.NETWORKING;
        SERVER_INSTANCES.SERVER_SETTINGS = new StockMarketModSettings();
        SERVER_INSTANCES.SERVER_SETTINGS.setLogger(SERVER_INSTANCES.LOGGER::error, SERVER_INSTANCES.LOGGER::error, SERVER_INSTANCES.LOGGER::debug);

        Testing.setBackend(SERVER_INSTANCES);
        MarketManager.setBackend(SERVER_INSTANCES);
        StockMarketNetworking.setBackend(SERVER_INSTANCES);
        NEZNAMY_TAB_Placeholders.setBackend(SERVER_INSTANCES);
        StockMarketCommands.setBackend(SERVER_INSTANCES);
        DataManager.setBackend(SERVER_INSTANCES);

        SERVER_INSTANCES.BANK_SYSTEM_API.getEvents().getBanksystemSetupCompleteSignal().addListener(StockMarketModBackend::onBankSystemSetupComplete, 1);
        SERVER_INSTANCES.BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener(StockMarketModBackend::onPostBankSystemDataLoaded, 1);
    }


    // Called from the server side
    public static void onServerStart(MinecraftServer server)
    {
        //INSTANCES.BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener();

        /*if(BankSystemDataHandler.isBankDataLoaded())
        {
            BankSystemDataHandler.resetBankDataLoaded();
            onPostBankSystemDataLoaded();
        }
        else
        {
            SERVER_INSTANCES.BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener(StockMarketModBackend::onPostBankSystemDataLoaded, 1);
        }*/

        /*if(SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.ENABLE_SERVER_SERVER_COMMUNICATION.get()) {
            boolean isMaster = SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.IS_MASTER.get();
            String sharedSecret = SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.SHARED_SECRET.get();
            int port = SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.MASTER_TCP_PORT.get();

            if(!MultiServerManager.instanceExists()) {
                if (isMaster) {
                    MultiServerManager.createMaster(server, sharedSecret, port);
                    MultiServerManager.start();
                } else {
                    String hostIP = SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.MASTER_IP.get();
                    String thisServerID = SERVER_INSTANCES.SERVER_SETTINGS.NETWORKING.SLAVE_ID.get();
                    MultiServerManager.createSlave(server, sharedSecret, thisServerID, hostIP, port);
                    MultiServerManager.start();
                }
            }
        }*/
    }
    private static void onPostBankSystemDataLoaded()
    {

    }
    private static void onBankSystemSetupComplete()
    {
        boolean isMaster = BankSystemMod.getAPI().getServerBankManager().isMaster();
        if(isMaster)
        {
            SERVER_INSTANCES.MARKET_PRICE_HISTORY_MANAGER = new MarketPriceManager();
            SERVER_INSTANCES.MARKET_MANAGER = MarketManager.createMaster();
            SERVER_INSTANCES.DATA_MANAGER = new DataManager();

            Testing testing = new Testing();
            if(!testing.setup())
            {
                SERVER_INSTANCES.LOGGER.info("Can't setup testing");
                return;
            }
            if(testing.runTests())
            {
                SERVER_INSTANCES.LOGGER.info("All tests executed successfully");
            }
            else
            {
                SERVER_INSTANCES.LOGGER.info("Some tests failed");
                testing.runTests();
            }




            loadDataFromFiles(UtilitiesPlatform.getServer());
            TickEvent.SERVER_POST.register(StockMarketModBackend::onServerTick);

        }
        else
        {
            SERVER_INSTANCES.MARKET_MANAGER = MarketManager.createSlave();
        }
    }


    // Called from the server side
    public static void onServerStop(MinecraftServer server)
    {
        TickEvent.SERVER_POST.unregister(StockMarketModBackend::onServerTick);
        saveDataToFiles(server);

        if(MultiServerManager.instanceExists())
        {
            MultiServerManager.stop();
            MultiServerManager.cleanup();
        }
    }


    // Called from the server side
    public static void onPlayerJoin(ServerPlayer player)
    {
        PlayerJoinSyncPacket.send(player);
    }


    // Called from the server side
    public static void onPlayerLeave(ServerPlayer player)
    {

    }

    // Called from the client side
    private static void onPlayerJoinClientSide(@Nullable LocalPlayer localPlayer)
    {
        if(CLIENT_INSTANCES != null)
            return;
        CLIENT_INSTANCES = new ClientInstances();

        StockMarketNetworking.setBackend(CLIENT_INSTANCES);
        StockMarketGuiScreen.setBackend(CLIENT_INSTANCES);
        ClientMarketManager.setBackend(CLIENT_INSTANCES);
        StockMarketTextMessages.setBackend(CLIENT_INSTANCES);


        CLIENT_INSTANCES.LOGGER = COMMON_INSTANCES.LOGGER;
        CLIENT_INSTANCES.NETWORKING = COMMON_INSTANCES.NETWORKING;
        CLIENT_INSTANCES.BANK_SYSTEM_API = BankSystemMod.getAPI();
        CLIENT_INSTANCES.MARKET_MANAGER = new ClientMarketManager();
        CLIENT_INSTANCES.SETTINGS = new ClientSettings();




        CLIENT_INSTANCES.MARKET_MANAGER.onPlayerJoin(localPlayer);
    }
    // Called from the client side
    private static void onPlayerLeaveClientSide(@Nullable LocalPlayer localPlayer)
    {
        if(CLIENT_INSTANCES == null)
            return;
        CLIENT_INSTANCES.MARKET_MANAGER.onPlayerLeave(localPlayer);

        StockMarketNetworking.setBackend((ClientInstances)null);
        StockMarketGuiScreen.setBackend(null);
        ClientMarketManager.setBackend(null);
        StockMarketTextMessages.setBackend(null);
        CLIENT_INSTANCES = null;


    }


    private static void onPlayerRespawnClientSide(LocalPlayer oldPlayer, LocalPlayer newPlayer)
    {

    }


    private static void onClientTickEvent(ClientLevel clientLevel)
    {
        CLIENT_INSTANCES.MARKET_MANAGER.update();
    }

    // Called from the server side
    private static void onServerTick(MinecraftServer server)
    {
        SERVER_INSTANCES.MARKET_MANAGER.getSync().update();
    }

    public static void loadDataFromFiles(MinecraftServer server)
    {
        if(SERVER_INSTANCES != null && SERVER_INSTANCES.DATA_MANAGER != null)
            SERVER_INSTANCES.DATA_MANAGER.load(server);
    }
    public static void saveDataToFiles(MinecraftServer server)
    {
        if(SERVER_INSTANCES != null && SERVER_INSTANCES.DATA_MANAGER != null)
            SERVER_INSTANCES.DATA_MANAGER.save(server);
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
