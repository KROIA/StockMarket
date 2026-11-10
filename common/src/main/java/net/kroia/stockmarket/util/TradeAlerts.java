package net.kroia.stockmarket.util;

import net.kroia.modutilities.PermissionCompat;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.minecraft.compat.StockMarketDiscord;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * #89: single sink for trading alerts / suspicious-activity flags (master server only).
 * <p>
 * Every alert is written to the server log (the record) and sent as a red chat message to every
 * online player that holds {@link StockMarketPermissions#NOTIFY} and has alerts enabled
 * ({@link #alertsEnabledFor(ServerPlayer)}). Detectors (currently {@link ProfitAlerts}, later the
 * T-192 pump-and-dump detector) only decide <i>when</i> to flag and call {@link #raise}.
 * <p>
 * Never pass secrets into an alert: the text goes to the log and to admin chat.
 */
public final class TradeAlerts {

    private TradeAlerts() {}

    /**
     * Records an alert in the server log, notifies the online admins and posts it to the Discord feed
     * ({@code market-alert}, T-217; only on the master while {@code Integrations.DISCORD_ENABLED} is on).
     *
     * @param logText plain text for the server log and the Discord post (language independent)
     * @param message chat message for admins, usually {@code Component.translatable(key, args)}
     *                so it is resolved in each admin's client language; prefixed with
     *                "[StockMarket] " and colored red
     */
    public static void raise(String logText, Component message) {
        StockMarketMod.LOGGER.warn("[StockMarket Alert] {}", logText);
        StockMarketDiscord.alert(logText);
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server == null) return;
        Component chat = Component.literal("[StockMarket] ").append(message).withStyle(ChatFormatting.RED);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (PermissionCompat.hasPermission(player, StockMarketPermissions.NOTIFY, StockMarketPermissions.FALLBACK_LEVEL)
                    && alertsEnabledFor(player))
                player.sendSystemMessage(chat);
        }
    }

    /**
     * Per-admin alert toggle. T-192 hook: replace with the {@code /stockmarket alerts on|off} user
     * flag (customData, default from a config setting), mirroring BankSystem's {@code alertsEnabled}.
     *
     * @param admin an online player that already passed the NOTIFY permission check
     * @return whether this admin wants alert chat messages; always true for now
     */
    static boolean alertsEnabledFor(ServerPlayer admin) {
        return true;
    }
}
