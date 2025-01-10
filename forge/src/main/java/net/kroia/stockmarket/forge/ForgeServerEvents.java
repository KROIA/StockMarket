package net.kroia.stockmarket.forge;

import net.kroia.stockmarket.util.StockMarketServerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ForgeServerEvents {
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
