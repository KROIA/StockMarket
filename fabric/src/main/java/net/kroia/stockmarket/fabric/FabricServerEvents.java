package net.kroia.stockmarket.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketServerEvents;
import net.minecraft.server.level.ServerLevel;

public class FabricServerEvents {

    private static long saveCooldownTimeMS = 0;
    public static void register() {
        // Server start (world load)
        ServerWorldEvents.LOAD.register((server, world)-> {
            if(world.isClientSide())
                return;
            UtilitiesPlatformFabric.setServer(server);
            StockMarketMod.onServerSetup();
            //if(world.getLevel().dimension().equals(ServerLevel.OVERWORLD))
            //    StockMarketServerEvents.onServerStart(server);
        });
       /* ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                StockMarketServerEvents.onServerStop(server);
            }
        });*/

        // Server stop (world unload)
        ServerWorldEvents.UNLOAD.register((server, world)-> {
            if(world.isClientSide())
                return;
            if(world.getLevel().dimension().equals(ServerLevel.OVERWORLD))
                StockMarketServerEvents.onServerStop(server);
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
