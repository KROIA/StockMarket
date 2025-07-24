package net.kroia.stockmarket.forge;

import net.kroia.stockmarket.StockMarketMod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = StockMarketMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeSetup {

    // Mod setup for common (server)
    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        StockMarketMod.logInfo("[ForgeSetup] Common setup for server.");
        StockMarketMod.onServerSetup();
    }

    // Client setup (for client-side logic)
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        StockMarketMod.logInfo("[ForgeSetup] Client setup.");
        StockMarketMod.onClientSetup();
    }
}
