package net.kroia.quilt;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;

import net.kroia.stockmarket.StockMarketMod;

public final class StockMarketQuilt implements ModInitializer {
    @Override
    public void onInitialize(ModContainer mod) {
        // Run our common setup.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            StockMarketMod.LOGGER.info("[QuiltSetup] Common setup for server.");
            StockMarketMod.onServerSetup();
        });

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
                StockMarketMod.LOGGER.info("[QuiltSetup] Client setup.");
                StockMarketMod.onClientSetup();
            });
        }

        StockMarketMod.init();
        QuiltPlayerEvents.register();
        QuiltServerEvents.register();
    }
}
