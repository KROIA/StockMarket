package net.kroia.stockmarket.neoforge;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.kroia.modutilities.ModUtilitiesMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.compat.NEZNAMY_TAB_Placeholders;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber
public class NeoForgeServerEvents {

    public static void init()
    {
        LifecycleEvent.SERVER_STARTED.register((server)->{
            // Check if NEZNAMY/TAB is present and register placeholders
            StockMarketModBackend.onServerStart(server);
            if (Platform.getEnvironment() == Env.SERVER && Platform.isModLoaded("tab")) {
                NEZNAMY_TAB_Placeholders.register();
            }

            ModUtilitiesMod.LOGGER.info("[NeoForgeSetup] SERVER_STARTING");
            StockMarketModBackend.onServerStart(server);


        });
        LifecycleEvent.SERVER_STOPPING.register(StockMarketModBackend::onServerStop);
    }
    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension().equals(ServerLevel.OVERWORLD))
                StockMarketModBackend.saveDataToFiles(serverLevel.getServer());
        }
    }
}
