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
                StockMarketModBackend.onClientSetup();
            });
        }


        // Server Events
        ServerLifecycleEvents.STARTING.register(server-> {
            StockMarketModBackend.onServerSetup();
        });

        // Handle world load (start)
        ServerLifecycleEvents.READY.register((server)->
        {
            StockMarketModBackend.onServerStart(server);
            // Check if NEZNAMY/TAB is present and register placeholders
            if (Platform.isModLoaded("tab")) {
                NEZNAMY_TAB_Placeholders.register();
            }
        });

        // Handle world save (stop)
        ServerLifecycleEvents.STOPPING.register(StockMarketModBackend::onServerStop);


        // Player Events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            StockMarketModBackend.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StockMarketModBackend.onPlayerLeave(handler.getPlayer());
        });

        BankSystemMod.init();
    }
}
