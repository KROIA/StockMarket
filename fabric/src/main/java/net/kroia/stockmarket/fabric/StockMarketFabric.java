package net.kroia.stockmarket.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kroia.stockmarket.StockMarketMod;

public final class StockMarketFabric implements ModInitializer {
    @Override
    public void onInitialize() {

        // Client Events
        if(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
                StockMarketMod.LOGGER.info("[FabricSetup] CLIENT_STARTED");
                StockMarketMod.onClientSetup();
            });
        }



        // Server Events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            StockMarketMod.LOGGER.info("[FabricSetup] SERVER_STARTING");
            StockMarketMod.onServerSetup();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            StockMarketMod.LOGGER.info("[FabricSetup] SERVER_STARTED");
            // todo: replace this
            //StockMarketServerEvents.onServerStart(server); // Handle world load (start)
        });

        // World save
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            StockMarketMod.LOGGER.info("[FabricSetup] SERVER_STOPPING");
            // todo: replace this
            //StockMarketServerEvents.onServerStop(server);
        });


        // Player Events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // todo: replace this
            //StockMarketPlayerEvents.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // todo: replace this
            //StockMarketPlayerEvents.onPlayerLeave(handler.getPlayer());
        });


        // Run our common setup.
        StockMarketMod.init();
    }
}
