package net.kroia.stockmarket.neoforge;

import net.kroia.stockmarket.StockMarketMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = StockMarketMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class NeoForgeSetup {

    // Mod setup for common (server)
    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        StockMarketMod.LOGGER.info("[ForgeSetup] Common setup for server.");
        StockMarketMod.onServerSetup();
    }

    // Client setup (for client-side logic)
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        StockMarketMod.LOGGER.info("[ForgeSetup] Client setup.");
        StockMarketMod.onClientSetup();
    }

    /*
        This is a workaround since the Architectury screen registration does not work with NeoForge.
     */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        //event.register(BankSystemMenus.BANK_TERMINAL_CONTAINER_MENU.get(), BankTerminalScreen::new);
    }
}
