package net.kroia.stockmarket.util;

import net.kroia.stockmarket.ModSettings;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

@Mod.EventBusSubscriber
public class PlayerEvents {

    // Called when a player joins the server_sender
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerPlayerList.addPlayer(player);
            ServerBankManager.createUser(player.getUUID(),new ArrayList<>(),true, ModSettings.Player.STARTING_BALANCE);

            //player.sendSystemMessage(Component.literal("Welcome to the server_sender, " + player.getName().getString() + "!"));
            //System.out.println(player.getName().getString() + " joined the server_sender.");
        }
    }

    // Called when a player leaves the server_sender
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            //System.out.println(player.getName().getString() + " left the server_sender.");
        }
    }
}
