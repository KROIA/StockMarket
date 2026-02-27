package net.kroia.stockmarket.neoforge;


import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber
public class NeoForgePlayerEvents {
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // todo: replace this
            // StockMarketPlayerEvents.onPlayerJoin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // todo: replace this
            // StockMarketPlayerEvents.onPlayerLeave(player);
        }
    }
}