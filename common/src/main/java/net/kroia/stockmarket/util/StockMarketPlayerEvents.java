package net.kroia.stockmarket.util;

import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.minecraft.server.level.ServerPlayer;
public class StockMarketPlayerEvents {


    public static void onPlayerJoin(ServerPlayer player) {
        ServerPlayerList.addPlayer(player);
        SyncTradeItemsPacket.sendPacket(player);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        // Add logic for player leaving
        ServerMarket.removePlayerUpdateSubscription(player);
    }
}
