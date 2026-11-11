package net.kroia.stockmarket.fabric;

import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;

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
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(StockMarketModBackend::onServerStop);
        // T-191: Fabric runs BankSystem's stop handler (which closes its database) before ours, so the
        // SHUTDOWN ledger rows are written in a phase that runs before the default one.
        ResourceLocation ledgerFlushPhase = ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, "ledger_flush"); // mc<=1.21.10
        //Identifier ledgerFlushPhase = Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, "ledger_flush"); // mc>=1.21.11
        ServerLifecycleEvents.SERVER_STOPPING.addPhaseOrdering(ledgerFlushPhase, Event.DEFAULT_PHASE);
        ServerLifecycleEvents.SERVER_STOPPING.register(ledgerFlushPhase, StockMarketModBackend::flushLedgerOnShutdown);


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
