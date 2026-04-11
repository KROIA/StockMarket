package net.kroia.stockmarket.neoforge;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
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
        // BankSystemModBackend..LOGGER.info("[NeoForgeSetup] Common setup for server.");
        StockMarketModBackend.onServerSetup();
    }

    // Client setup (for client-side logic)
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        //BankSystemModBackend.LOGGER.info("[NeoForgeSetup] Client setup.");
        StockMarketModBackend.onClientSetup();
    }

    /*
        This is a workaround since the Architectury screen registration does not work with NeoForge.
     */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        // todo: register container menus here
        //event.register(BankSystemMenus.BANK_TERMINAL_CONTAINER_MENU.get(), BankTerminalScreen::new);
        //event.register(BankSystemMenus.BANK_UPLOAD_CONTAINER_MENU.get(), BankUploadScreen::new);
        //event.register(BankSystemMenus.BANK_DOWNLOAD_CONTAINER_MENU.get(), BankDownloadScreen::new);
    }
}
