package net.kroia.stockmarket.neoforge;

import net.kroia.stockmarket.util.StockMarketServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber
public class NeoForgeServerEvents {
    @SubscribeEvent
    public static void onServerStart(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // Check if the world is the overworld
            if (serverLevel.dimension().equals(ServerLevel.OVERWORLD))
                StockMarketServerEvents.onServerStart(serverLevel.getServer());

        }
    }

    @SubscribeEvent
    public static void onServerStop(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension().equals(ServerLevel.OVERWORLD))
                StockMarketServerEvents.onServerStop(serverLevel.getServer());
        }
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension().equals(ServerLevel.OVERWORLD))
                StockMarketServerEvents.onWorldSave(serverLevel);
        }
    }
}
