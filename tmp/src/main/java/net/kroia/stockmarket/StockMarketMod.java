package net.kroia.stockmarket;

import com.mojang.logging.LogUtils;
import net.kroia.banksystem.BankSystemMod;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.command.StockMarketCommands;
import net.kroia.stockmarket.entity.StockMarketEntities;
import net.kroia.stockmarket.item.StockMarketCreativeModeTab;
import net.kroia.stockmarket.item.StockMarketItems;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.menu.StockMarketMenus;
import net.kroia.stockmarket.networking.ModMessages;
import net.kroia.stockmarket.util.DataHandler;
import net.kroia.stockmarket.util.ServerPlayerList;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.File;
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



        StockMarketCreativeModeTab.register(modEventBus);
        StockMarketItems.register(modEventBus);
        StockMarketBlocks.register(modEventBus);
        StockMarketEntities.register(modEventBus);
        StockMarketMenus.register(modEventBus);



        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server_sender and other game events we are interested in
        // MinecraftForge.EVENT_BUS.register(this);
        //modEventBus.addListener(this::onServerStarting);


        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);



        // Register event listeners to differentiate client and server_sender
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::setupClient);
        //DistExecutor.unsafeRunWhenOn(Dist.DEDICATED_SERVER, () -> this::setupServer);
        // Server setup is called both for dedicated and integrated server_sender
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        // Register the event handler class to the Forge Event Bus
       // MinecraftForge.EVENT_BUS.register(new PlayerEvents());
        MinecraftForge.EVENT_BUS.addListener(StockMarketMod::onRegisterCommands);
        // Register client-side commands only on the client
        //MinecraftForge.EVENT_BUS.addListener(StockMarketMod::onRegisterClientCommands);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StockMarketCommands.register(event.getDispatcher());
    }

    /*
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        // Client-side command registration
        StockMarketCommands.register(event.getDispatcher());
    }*/

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        //LOGGER.info("HELLO FROM COMMON SETUP");



        ModMessages.register();
        onServerTickSetup();

        //LOGGER.info("HELLO FROM CLIENT COMMON SETUP");

        if (ModList.get().isLoaded("banksystem"))
        {
            System.out.println("banksystem is loaded");
        }
        else
        {
            System.out.println("banksystem is not loaded");
        }
    }
    @SubscribeEvent
    public void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // This code runs when the player enters a server_sender on the client side
        //System.out.println("Player has logged into a server_sender!");

        // Call your desired function here
        //performClientSideAction();
        //ClientMarket.init();
    }

    private void setupClient()
    {
        //LOGGER.info("HELLO FROM CLIENT SETUP");
        //LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        if(side == Side.UNKNOWN)
            side = Side.MULTIPLAYER_CLIENT;
        else
            side = Side.SINGLE_PLAYER;
        MinecraftForge.EVENT_BUS.register(this);
    }
    private void setupServer() {
        //LOGGER.info("HELLO from server_sender starting");
        // Register ourselves for server_sender-side events
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        if(side == Side.UNKNOWN)
            side = Side.MULTIPLAYER_SERVER;
        else
            side = Side.SINGLE_PLAYER;


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
        // Do something when the server_sender starts
        LOGGER.info("HELLO from server_sender starting");
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


    public static void printToClientConsole(String message) {
        // Check that the code is running on the client side
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(message)
            );
        }
    }
    public static void printToClientConsole(ServerPlayer player, String message)
    {
        if(player == null)
            return;
        player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(message)
        );
    }

    public static void printToClientConsole(UUID playerUUID, String message)
    {
        ServerPlayer player = ServerPlayerList.getPlayer(playerUUID);
        if(player == null)
        {
            return;
        }
        player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(message)
        );
    }
    public static void printToClientConsone(String msg)
    {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();

        if (server == null) {
            throw new IllegalStateException("Server instance is null. Are you calling this from the server_sender?");
        }

        // Get the player list and fetch the player by UUID
        PlayerList playerList = server.getPlayerList();
        for(ServerPlayer player : playerList.getPlayers())
        {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(msg)
            );
        }
    }

    public static ItemStack createItemStackFromId(String itemId, int amount) {
        ResourceLocation resourceLocation = new ResourceLocation(itemId); // "minecraft:diamond"
        Item item = ForgeRegistries.ITEMS.getValue(resourceLocation); // Get the item from the registry

        if (item != null) {
            return new ItemStack(item, amount); // Create an ItemStack with the specified amount
        }

        return ItemStack.EMPTY; // Return an empty stack if the item is not found
    }
    public static String getNormalizedItemID(String maybeNotCompleteItemID)
    {
        ItemStack itemStack = StockMarketMod.createItemStackFromId(maybeNotCompleteItemID,1);
        if(itemStack == null) {
            return null;
        }
        if(itemStack.getItem() == Items.AIR) {
            return null;
        }
        // Get the item's ResourceLocation
        ResourceLocation itemLocation = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        return itemLocation.toString();
    }



    public static void loadDataFromFiles(MinecraftServer server)
    {
        // First load BankSystem data
        if(!BankSystemMod.isDataLoaded())
        {
            BankSystemMod.loadDataFromFiles(server);
        }
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
