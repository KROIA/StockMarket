package net.kroia.stockmarket.neoforge;

import dev.architectury.event.events.common.LifecycleEvent;
import net.kroia.modutilities.ModUtilitiesMod;
import net.kroia.stockmarket.util.StockMarketServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber
public class NeoForgeServerEvents {

    public static void init()
    {
        LifecycleEvent.SERVER_STARTED.register(server -> {
            ModUtilitiesMod.LOGGER.info("[NeoForgeSetup] SERVER_STARTING");
            StockMarketServerEvents.onServerStart(server);
        });
        LifecycleEvent.SERVER_STARTING.register(server -> {
            ModUtilitiesMod.LOGGER.info("[NeoForgeSetup] SERVER_STOPPED");
            StockMarketServerEvents.onServerStop(server);
        });
    }
    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension().equals(ServerLevel.OVERWORLD))
                StockMarketServerEvents.onWorldSave(serverLevel);
        }
    }
}
