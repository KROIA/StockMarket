package net.kroia.quilt;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.util.StockMarketServerEvents;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;

import net.kroia.stockmarket.StockMarketMod;

public final class StockMarketQuilt implements ModInitializer {
    @Override
    public void onInitialize(ModContainer mod) {
        // Run our common setup.
        UtilitiesPlatform.setPlatform(new UtilitiesPlatformQuilt());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            StockMarketMod.LOGGER.info("[QuiltSetup] Common setup for server.");
            UtilitiesPlatformQuilt.setServer(server);
            StockMarketServerEvents.onServerStart(server);
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
