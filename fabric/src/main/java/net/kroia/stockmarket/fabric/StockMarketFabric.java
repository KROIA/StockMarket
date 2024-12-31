package net.kroia.stockmarket.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketServerEvents;

public final class StockMarketFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        UtilitiesPlatform.setPlatform(new UtilitiesPlatformFabric());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            StockMarketMod.LOGGER.info("[FabricSetup] Common setup for server.");

            StockMarketServerEvents.onServerStart(server);
        });

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
                StockMarketMod.LOGGER.info("[FabricSetup] Client setup.");
                StockMarketMod.onClientSetup();
            });
        }


        // Run our common setup.
        StockMarketMod.init();
        FabricPlayerEvents.register();
        FabricServerEvents.register();
    }
}
