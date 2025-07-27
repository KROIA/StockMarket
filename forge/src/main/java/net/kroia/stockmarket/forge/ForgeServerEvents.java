package net.kroia.stockmarket.forge;

import dev.architectury.event.events.common.LifecycleEvent;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ForgeServerEvents {
    public static void init()
    {
        LifecycleEvent.SERVER_STARTED.register(StockMarketModBackend::onServerStart);
        LifecycleEvent.SERVER_STOPPING.register(StockMarketModBackend::onServerStop);
    }
}
