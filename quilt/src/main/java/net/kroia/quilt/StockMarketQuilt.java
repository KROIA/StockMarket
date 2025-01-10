package net.kroia.quilt;

import net.fabricmc.api.EnvType;
import net.kroia.stockmarket.util.StockMarketPlayerEvents;
import net.kroia.stockmarket.util.StockMarketServerEvents;
import org.quiltmc.loader.api.minecraft.MinecraftQuiltLoader;
import org.quiltmc.qsl.lifecycle.api.client.event.ClientLifecycleEvents;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;

import net.kroia.stockmarket.StockMarketMod;
import org.quiltmc.qsl.lifecycle.api.event.ServerLifecycleEvents;
import org.quiltmc.qsl.networking.api.ServerPlayConnectionEvents;

public final class StockMarketQuilt implements ModInitializer {
    @Override
    public void onInitialize(ModContainer mod) {

        // Client Events
        if(MinecraftQuiltLoader.getEnvironmentType() == EnvType.CLIENT) {
            ClientLifecycleEvents.READY.register(client -> {
                StockMarketMod.LOGGER.info("[QuiltSetup] CLIENT READY");
                StockMarketMod.onClientSetup();
            });
        }


        // Server Events
        ServerLifecycleEvents.STARTING.register(server-> {
            StockMarketMod.LOGGER.info("[QuiltSetup] SERVER STARTING");
            StockMarketMod.onServerSetup(); // Handle world load (start)
        });

        ServerLifecycleEvents.READY.register(server -> {
            StockMarketMod.LOGGER.info("[QuiltSetup] SERVER READY");
            StockMarketServerEvents.onServerStart(server);
        });

        // World save event
        ServerLifecycleEvents.STOPPING.register(server -> {
            StockMarketMod.LOGGER.info("[QuiltSetup] SERVER STOPPING");
            StockMarketServerEvents.onServerStop(server); // Handle world save (stop)
        });

        // Player Events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            StockMarketPlayerEvents.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StockMarketPlayerEvents.onPlayerLeave(handler.getPlayer());
        });

        StockMarketMod.init();
    }
}
