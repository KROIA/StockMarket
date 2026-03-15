package net.kroia.stockmarket;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.banksystem.api.BankSystemAPI;
import net.kroia.banksystem.util.BankSystemDataHandler;
import net.kroia.stockmarket.api.StockMarketAPI;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.command.StockMarketCommands;
import net.kroia.stockmarket.compat.NEZNAMY_TAB_Placeholders;
import net.kroia.stockmarket.data.table.MarketPriceManager;
import net.kroia.stockmarket.entity.StockMarketEntities;
import net.kroia.stockmarket.event.EventRegistration;
import net.kroia.stockmarket.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.item.StockMarketItems;
import net.kroia.stockmarket.market.client.ClientMarketManager;
import net.kroia.stockmarket.networking.interserver.child.ChildInboundHandler;
import net.kroia.stockmarket.networking.interserver.child.HubConnector;
import net.kroia.stockmarket.networking.interserver.config.ModConfig;
import net.kroia.stockmarket.networking.interserver.events.CommandEvents;
import net.kroia.stockmarket.networking.interserver.hub.HubChildHandler;
import net.kroia.stockmarket.networking.interserver.hub.HubTcpServer;
import net.kroia.stockmarket.market.server.MarketManager;
import net.kroia.stockmarket.market.server.Testing;
import net.kroia.stockmarket.menu.StockMarketMenus;
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
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class StockMarketModBackend implements StockMarketAPI {


    public static class CommonInstances
    {
        public StockMarketLogger LOGGER;
        public StockMarketNetworking NETWORKING;
    }

    public static class ServerInstances
    {
        public BankSystemAPI BANK_SYSTEM_API;
        public StockMarketModSettings SERVER_SETTINGS;
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



        ModConfig.setBackend(COMMON_INSTANCES);

        // Register /hubsend and /hubsendto commands (child servers only, but safe to register always)
        CommandEvents.register();

        // inter server test
        ModConfig cfg = ModConfig.get();

        COMMON_INSTANCES.LOGGER.info("[HubMod] Initializing — mode: "+ (cfg.isHub ? "HUB" : "CHILD"));



        if (cfg.isHub) {
            initHub(cfg);
        } else {
            initChild(cfg);
        }
    }

    // ── Hub mode ──────────────────────────────────────────────────────────────

    private static void initHub(ModConfig cfg) {
        LifecycleEvent.SERVER_STARTED.register(mcServer -> {
            COMMON_INSTANCES.LOGGER.info("[HubMod] Starting hub TCP listener on port "+ cfg.hubTcpPort);
            HubTcpServer.start(cfg.hubTcpPort, mcServer);
        });

        LifecycleEvent.SERVER_STOPPING.register(mcServer -> {
            COMMON_INSTANCES.LOGGER.info("[HubMod] Stopping hub TCP listener...");
            HubTcpServer.stop();
        });
    }

    // ── Child mode ────────────────────────────────────────────────────────────

    private static void initChild(ModConfig cfg) {
        LifecycleEvent.SERVER_STARTED.register(mcServer -> {
            // Give the handler access to the MC server so it can broadcast messages to players
            ChildInboundHandler.setMcServer(mcServer);

            COMMON_INSTANCES.LOGGER.info("[HubMod] Connecting to hub at "+cfg.hubHost+":"+cfg.hubTcpPort+" as '"+cfg.serverId+"'");
            HubConnector.init(cfg.hubHost, cfg.hubTcpPort, cfg.serverId, cfg.sharedSecret);
        });

        LifecycleEvent.SERVER_STOPPING.register(mcServer -> {
            if (HubConnector.get() != null) {
                HubConnector.get().disconnect();
            }
        });
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

    }


    // Called from the server side
    public static void onServerStart(MinecraftServer server)
    {
        SERVER_INSTANCES =  new ServerInstances();

        Testing.setBackend(SERVER_INSTANCES);
        StockMarketModSettings.setBackend(SERVER_INSTANCES);
        MarketManager.setBackend(SERVER_INSTANCES);
        StockMarketNetworking.setBackend(SERVER_INSTANCES);
        NEZNAMY_TAB_Placeholders.setBackend(SERVER_INSTANCES);
        StockMarketCommands.setBackend(SERVER_INSTANCES);

        ChildInboundHandler.setBackend(SERVER_INSTANCES);
        HubConnector.setBackend(SERVER_INSTANCES);
        HubChildHandler.setBackend(SERVER_INSTANCES);
        HubTcpServer.setBackend(SERVER_INSTANCES);


        SERVER_INSTANCES.LOGGER = COMMON_INSTANCES.LOGGER;
        SERVER_INSTANCES.BANK_SYSTEM_API = BankSystemMod.getAPI();
        SERVER_INSTANCES.NETWORKING = COMMON_INSTANCES.NETWORKING;
        SERVER_INSTANCES.SERVER_SETTINGS = new StockMarketModSettings();
        SERVER_INSTANCES.SERVER_SETTINGS.setLogger(SERVER_INSTANCES.LOGGER::error, SERVER_INSTANCES.LOGGER::error, SERVER_INSTANCES.LOGGER::debug);
        SERVER_INSTANCES.MARKET_PRICE_HISTORY_MANAGER = new MarketPriceManager();
        SERVER_INSTANCES.MARKET_MANAGER = new MarketManager();



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
            SERVER_INSTANCES.BANK_SYSTEM_API.getEvents().getBankDataLoadedFromFileSignal().addListener(StockMarketModBackend::onPostBankSystemDataLoaded, 1);
        }



    }
    private static void onPostBankSystemDataLoaded()
    {
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
        SERVER_INSTANCES.MARKET_MANAGER.update();
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
