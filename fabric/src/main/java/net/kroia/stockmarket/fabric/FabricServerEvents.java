package net.kroia.stockmarket.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.kroia.stockmarket.util.ServerEvents;
import net.minecraft.server.level.ServerLevel;

public class FabricServerEvents {
    public static void register() {
        // Server start (world load)
        ServerWorldEvents.LOAD.register((server, world)-> {
            if(world.isClientSide())
                return;
            if(world.getLevel().dimension().equals(ServerLevel.OVERWORLD))
                ServerEvents.onServerStart(server);
        });

        // Server stop (world unload)
        ServerWorldEvents.UNLOAD.register((server, world)-> {
            if(world.isClientSide())
                return;
            if(world.getLevel().dimension().equals(ServerLevel.OVERWORLD))
                ServerEvents.onServerStop(server);
        });

        // World save
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                ServerEvents.onWorldSave(level);
            }
        });
    }
}
