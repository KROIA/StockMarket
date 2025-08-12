package net.kroia.stockmarket.forge;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.platform.Platform;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.compat.NEZNAMY_TAB_Placeholders;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ForgeServerEvents {
    public static void init()
    {
        LifecycleEvent.SERVER_STARTED.register((server)->
        {
            StockMarketModBackend.onServerStart(server);
            DistExecutor.unsafeRunWhenOn(Dist.DEDICATED_SERVER, () -> {
                if (Platform.isModLoaded("tab"))
                {
                    NEZNAMY_TAB_Placeholders.register();
                }
                return () -> {};
            });
        });
        LifecycleEvent.SERVER_STOPPING.register(StockMarketModBackend::onServerStop);
    }
}
