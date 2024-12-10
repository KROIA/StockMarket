package net.kroia.stockmarket;

import com.mojang.logging.LogUtils;
import net.kroia.stockmarket.block.ModBlocks;
import net.kroia.stockmarket.command.ModCommands;
import net.kroia.stockmarket.entity.ModEntities;
import net.kroia.stockmarket.item.ModCreativeModTabs;
import net.kroia.stockmarket.item.ModItems;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.menu.ModMenus;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.util.PlayerEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.UUID;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(StockMarketMod.MODID)
public class StockMarketMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "stockmarket";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public enum Side {
        MULTIPLAYER_CLIENT,
        MULTIPLAYER_SERVER,
        SINGLE_PLAYER,
        UNKNOWN
    }
    private static Side side = Side.UNKNOWN;
    // Create a Deferred Register to hold Blocks which will all be registered under the "examplemod" namespace

    private static long lastTimeMS = 0;
    public StockMarketMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);


        ModCreativeModTabs.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMenus.register(modEventBus);



        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        // MinecraftForge.EVENT_BUS.register(this);
        //modEventBus.addListener(this::onServerStarting);


        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);



        // Register event listeners to differentiate client and server
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::setupClient);
        //DistExecutor.unsafeRunWhenOn(Dist.DEDICATED_SERVER, () -> this::setupServer);
        // Server setup is called both for dedicated and integrated server
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        // Register the event handler class to the Forge Event Bus
       // MinecraftForge.EVENT_BUS.register(new PlayerEvents());
    }
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");



        ModMessages.register();
        onServerTickSetup();

        //LOGGER.info("HELLO FROM CLIENT COMMON SETUP");


    }
    @SubscribeEvent
    public void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // This code runs when the player enters a server on the client side
        System.out.println("Player has logged into a server!");

        // Call your desired function here
        //performClientSideAction();
        //ClientMarket.init();
    }

    private void setupClient()
    {
        LOGGER.info("HELLO FROM CLIENT SETUP");
        LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        if(side == Side.UNKNOWN)
            side = Side.MULTIPLAYER_CLIENT;
        else
            side = Side.SINGLE_PLAYER;
    }
    private void setupServer() {
        LOGGER.info("HELLO from server starting");
        // Register ourselves for server-side events
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        if(side == Side.UNKNOWN)
            side = Side.MULTIPLAYER_SERVER;
        else
            side = Side.SINGLE_PLAYER;
        ServerMarket.init();
    }
    private void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        // This event triggers for both dedicated and integrated servers
        setupServer();
    }
    public static Side getSide() {
        return side;
    }
    public static boolean isClient() {
        return side == Side.SINGLE_PLAYER || side == Side.MULTIPLAYER_CLIENT;
    }
    public static boolean isServer() {
        return side == Side.SINGLE_PLAYER || side == Side.MULTIPLAYER_SERVER;
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if(event.getTabKey() == CreativeModeTabs.INGREDIENTS)
        {
            //event.accept()
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    /*@SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
        ServerMarket.init();






    }*/

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            //LOGGER.info("HELLO FROM CLIENT SETUP");
            //LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        }
    }

    public void onServerTickSetup() {
        lastTimeMS = getCurrentTimeMillis();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            long currentTimeMillis = getCurrentTimeMillis();

            if(currentTimeMillis - lastTimeMS > ServerMarket.shiftPriceHistoryInterval) {
                lastTimeMS = currentTimeMillis;

                ServerMarket.shiftPriceHistory();
                //ServerMarket.updateBot();
            }
        }
    }

    public static long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    public static ServerPlayer getPlayerByUUID(String uuid)
    {
        UUID playerUUID;
        try {
            playerUUID = UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid UUID string: " + uuid);
            return null;
        }

        // Get the Minecraft server instance
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();

        if (server == null) {
            System.err.println("Server instance is null. Are you calling this from the server?");
            return null;
        }

        // Get the player list and fetch the player by UUID
        PlayerList playerList = server.getPlayerList();
        return playerList.getPlayer(playerUUID); // Returns null if the player is not online
    }

    public static void printToClientConsole(String message) {
        // Check that the code is running on the client side
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(message)
            );
        }
    }


}
