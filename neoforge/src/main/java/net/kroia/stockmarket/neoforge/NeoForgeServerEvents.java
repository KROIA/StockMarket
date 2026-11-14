package net.kroia.stockmarket.neoforge;

import dev.architectury.event.events.common.LifecycleEvent;
import net.kroia.modutilities.ModUtilitiesMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber
public class NeoForgeServerEvents {

    public static void init()
    {
        LifecycleEvent.SERVER_STARTED.register((server)->{
            ModUtilitiesMod.LOGGER.info("[NeoForgeSetup] SERVER_STARTING");
            StockMarketModBackend.onServerStart(server);


        });
        LifecycleEvent.SERVER_STOPPING.register(StockMarketModBackend::onServerStop);
    }
    /**
     * SM-205: writes the SHUTDOWN ledger rows before BankSystem's stop handler closes its database. Both mods stop
     * through Architectury's SERVER_STOPPING (bridged at HIGH priority), where the order only rests on mod
     * construction order; HIGHEST runs this before both. {@link StockMarketModBackend#onServerStop} flushes again,
     * which finds nothing left to write.
     *
     * @param event the server stopping event
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStoppingFlushLedger(ServerStoppingEvent event) {
        StockMarketModBackend.flushLedgerOnShutdown(event.getServer());
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension().equals(ServerLevel.OVERWORLD))
                StockMarketModBackend.saveDataToFiles(serverLevel.getServer());
        }
    }
}
