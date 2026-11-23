package net.kroia.stockmarket.neoforge;

import net.kroia.stockmarket.StockMarketMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

@Mod(StockMarketMod.MOD_ID)
public final class StockMarketNeoForge {
    public StockMarketNeoForge(IEventBus modBus) {
        // TK-12: NeoForge constructs mods in parallel; Architectury event lists are not thread-safe.
        // Defer registration to the construct-mod work queue (main thread, after all constructors).
        modBus.addListener((FMLConstructModEvent e) -> e.enqueueWork(() -> {
            NeoForgeServerEvents.init();
            StockMarketMod.init();
        }));
    }
}
