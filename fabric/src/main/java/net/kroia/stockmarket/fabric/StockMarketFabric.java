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
                StockMarketMod.logInfo("[FabricSetup] CLIENT_STARTED");
                StockMarketMod.onClientSetup();
            });
        }



        // Server Events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            StockMarketMod.logInfo("[FabricSetup] SERVER_STARTING");
            StockMarketMod.onServerSetup();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            StockMarketMod.logInfo("[FabricSetup] SERVER_STARTED");
            StockMarketMod.onServerStart(server); // Handle world load (start)
        });

        // World save
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            StockMarketMod.logInfo("[FabricSetup] SERVER_STOPPING");
            StockMarketMod.onServerStop(server);
        });


        // Player Events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            StockMarketMod.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StockMarketMod.onPlayerLeave(handler.getPlayer());
        });


        // Run our common setup.
        StockMarketMod.init();
    }
}
