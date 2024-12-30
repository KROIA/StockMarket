package net.kroia.stockmarket.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;

import net.kroia.stockmarket.StockMarketMod;

@Mod(StockMarketMod.MOD_ID)
public final class StockMarketForge {
    public StockMarketForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(StockMarketMod.MOD_ID, Mod.EventBusSubscriber.Bus.MOD.bus().get());

        // Run our common setup.
        StockMarketMod.init();
    }
}
