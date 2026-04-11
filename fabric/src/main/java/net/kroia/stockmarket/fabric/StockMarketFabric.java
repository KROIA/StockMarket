package net.kroia.stockmarket.fabric;

import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.compat.NEZNAMY_TAB_Placeholders;

public final class StockMarketFabric implements ModInitializer {
    @Override
    public void onInitialize() {

        // Client Events
        if(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
                StockMarketModBackend.onClientSetup();
            });
        }


        // Server Events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            StockMarketModBackend.onServerSetup();
        });

        // Handle world load (start)
        ServerLifecycleEvents.SERVER_STARTED.register((server)->
        {
            StockMarketModBackend.onServerStart(server);
            // Check if NEZNAMY/TAB is present and register placeholders
            if (Platform.isModLoaded("tab")) {
                NEZNAMY_TAB_Placeholders.register();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(StockMarketModBackend::onServerStop);


        // Player Events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            StockMarketModBackend.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StockMarketModBackend.onPlayerLeave(handler.getPlayer());
        });



        /*ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // code to run when leaving a server
            StockMarketModBackend.onPlayerLeaveClientSide();
        });*/


        // Run our common setup.
        StockMarketMod.init();


        if (isJeiLoaded() && Platform.getEnv() == EnvType.CLIENT) {
            // todo: Replace this function to notify that JEI is active and that the window of container screens
            //       overlap with the JEI item list view
            //BankSystemGuiScreen.setJeiModLoaded(true);
        }
    }


    public static boolean isJeiLoaded() {
        return FabricLoader.getInstance().isModLoaded("jei");
    }
}
