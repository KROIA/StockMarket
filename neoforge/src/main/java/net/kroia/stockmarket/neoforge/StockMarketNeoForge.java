package net.kroia.stockmarket.neoforge;

import dev.architectury.event.events.common.LifecycleEvent;
import net.kroia.modutilities.ModUtilitiesMod;
import net.kroia.modutilities.neoforge.UtilitiesPlatformNeoForge;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketServerEvents;
import net.neoforged.fml.common.Mod;

@Mod(StockMarketMod.MOD_ID)
public final class StockMarketNeoForge {
    public StockMarketNeoForge() {

        NeoForgeServerEvents.init();
        // Run our common setup.
        StockMarketMod.init();
    }
}
