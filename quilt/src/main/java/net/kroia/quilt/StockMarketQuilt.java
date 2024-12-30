package net.kroia.quilt;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.qsl.base.api.entrypoint.ModInitializer;

import net.kroia.stockmarket.StockMarketMod;

public final class StockMarketQuilt implements ModInitializer {
    @Override
    public void onInitialize(ModContainer mod) {
        // Run our common setup.
        StockMarketMod.init();

    }
}
