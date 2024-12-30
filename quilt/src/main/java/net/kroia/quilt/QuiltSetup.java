package net.kroia.quilt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.kroia.stockmarket.StockMarketMod;

public class QuiltSetup implements ModInitializer, ClientModInitializer {

    @Override
    public void onInitialize() {
        StockMarketMod.LOGGER.info("[QuiltSetup] Common setup for server.");
        StockMarketMod.onServerSetup();
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void onInitializeClient() {
        StockMarketMod.LOGGER.info("[QuiltSetup] Client setup.");
        StockMarketMod.onClientSetup();
    }
}