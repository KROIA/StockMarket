package net.kroia.stockmarket.forge;

import dev.architectury.event.events.common.LifecycleEvent;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ForgeServerEvents {
    public static void init()
    {
        LifecycleEvent.SERVER_STARTED.register(server -> {
            StockMarketMod.logInfo("[ForgeSetup] SERVER_STARTING");
            StockMarketMod.onServerStart(server);
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            StockMarketMod.logInfo("[ForgeSetup] SERVER_STOPPED");
            StockMarketMod.onServerStop(server);
        });
    }
}
