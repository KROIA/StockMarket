package net.kroia.stockmarket;

import com.mojang.logging.LogUtils;
import net.kroia.stockmarket.block.ModBlocks;
import net.kroia.stockmarket.entity.ModEntities;
import net.kroia.stockmarket.item.ModCreativeModTabs;
import net.kroia.stockmarket.item.ModItems;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.menu.ModMenus;
import net.kroia.stockmarket.networking.ModMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(StockMarketMod.MODID)
public class StockMarketMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "stockmarket";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "examplemod" namespace

    private static long lastTimeMinute = 0;
    public StockMarketMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModCreativeModTabs.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMenus.register(modEventBus);


        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register ourselves for server-side events
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");



        ModMessages.register();
        onServerTickSetup();

        LOGGER.info("HELLO FROM CLIENT COMMON SETUP");


    }
    @SubscribeEvent
    public void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // This code runs when the player enters a server on the client side
        System.out.println("Player has logged into a server!");

        // Call your desired function here
        //performClientSideAction();
        //ClientMarket.init();
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
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
        ServerMarket.init();






    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());

        }
    }

    public void onServerTickSetup() {
        lastTimeMinute = getCurrentTimeMinute();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            long currentTimeMinute = getCurrentTimeMinute();

            if(currentTimeMinute - lastTimeMinute > ServerMarket.shiftPriceHistoryInterval) {
                lastTimeMinute = currentTimeMinute;

                ServerMarket.shiftPriceHistory();
            }

            /*
            if (currentTimeMinute != lastTimeMinute) { // Check if one minute has passed
                lastTimeMinute = currentTimeMinute; // Update the last execution time

                LOGGER.info("One minute has passed");
                Market.shiftPriceHistory();
                // Call the executeServerSideCode method
                //executeServerSideCode();
            }*/
        }
    }

    public static long getCurrentTimeMinute() {
        return System.currentTimeMillis()/500;
    }


}
