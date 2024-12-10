package net.kroia.stockmarket.util;

import net.kroia.stockmarket.bank.ServerBank;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class PlayerEvents {

    // Called when a player joins the server
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerBank.createBankIfNotExist(player, 1000);

            //player.sendSystemMessage(Component.literal("Welcome to the server, " + player.getName().getString() + "!"));
            //System.out.println(player.getName().getString() + " joined the server.");
        }
    }

    // Called when a player leaves the server
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            //System.out.println(player.getName().getString() + " left the server.");
        }
    }
}
