package net.kroia.stockmarket.neoforge;

import net.kroia.stockmarket.StockMarketMod;
import net.neoforged.fml.common.Mod;

@Mod(StockMarketMod.MOD_ID)
public final class StockMarketNeoForge {
    public StockMarketNeoForge() {
        // Run our common setup.
        StockMarketMod.init();
    }
}
