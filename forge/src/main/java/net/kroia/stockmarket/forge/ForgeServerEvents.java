package net.kroia.stockmarket.forge;

import dev.architectury.event.events.common.LifecycleEvent;
import net.kroia.modutilities.ModUtilitiesMod;
import net.kroia.stockmarket.util.StockMarketServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ForgeServerEvents {
    public static void init()
    {
        LifecycleEvent.SERVER_STARTED.register(server -> {
            ModUtilitiesMod.LOGGER.info("[ForgeSetup] SERVER_STARTING");
            StockMarketServerEvents.onServerStart(server);
        });
        LifecycleEvent.SERVER_STARTING.register(server -> {
            ModUtilitiesMod.LOGGER.info("[ForgeSetup] SERVER_STOPPED");
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
