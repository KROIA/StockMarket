package net.kroia.quilt;


import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketServerEvents;
import net.minecraft.server.level.ServerLevel;
import org.quiltmc.qsl.lifecycle.api.event.ServerLifecycleEvents;

public class QuiltServerEvents {
    private static long saveCooldownTimeMS = 0;
    public static void register() {
        // World load event
        ServerLifecycleEvents.STARTING.register(server-> {
            StockMarketMod.onServerSetup(); // Handle world load (start)
        });

        // World save event
        ServerLifecycleEvents.STOPPING.register(server -> {
            int a = 0;
            a++;
            StockMarketServerEvents.onServerStop(server); // Handle world save (stop)
        });

        // World save
        ServerChunkEvents.CHUNK_UNLOAD.register((serverWorld, chunk) -> {
            long currentMS = System.currentTimeMillis();
            if (currentMS - saveCooldownTimeMS < 1000) {
                return;
            }
            saveCooldownTimeMS = currentMS;
            for (ServerLevel level : serverWorld.getServer().getAllLevels()) {
                StockMarketServerEvents.onWorldSave(level);
            }
        });
    }
}
