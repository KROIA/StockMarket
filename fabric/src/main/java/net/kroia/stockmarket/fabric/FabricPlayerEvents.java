package net.kroia.stockmarket.fabric;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kroia.stockmarket.util.StockMarketPlayerEvents;

public class FabricPlayerEvents {
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            StockMarketPlayerEvents.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StockMarketPlayerEvents.onPlayerLeave(handler.getPlayer());
        });
    }
}