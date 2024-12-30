package net.kroia.quilt;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kroia.stockmarket.util.PlayerEvents;

public class QuiltPlayerEvents {
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerEvents.onPlayerJoin(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlayerEvents.onPlayerLeave(handler.getPlayer());
        });
    }
}
