package net.kroia.stockmarket.util;

import net.kroia.modutilities.PermissionCompat;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.minecraft.compat.StockMarketDiscord;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.User;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * #89: single sink for trading alerts / suspicious-activity flags (master server only).
 * <p>
 * Every alert is written to the server log (the record) and sent as a red chat message to every
 * online player that holds {@link StockMarketPermissions#NOTIFY} and has alerts enabled
 * ({@link #alertsEnabled(UUID)}, {@code /stockmarket alerts on|off}). An admin with alerts off gets no
 * chat message, but the alert is still logged and posted. Detectors ({@link ProfitAlerts},
 * {@link PumpDumpDetector}) only decide <i>when</i> to flag and call {@link #raise}.
 * <p>
 * Never pass secrets into an alert: the text goes to the log and to admin chat.
 */
public final class TradeAlerts {

    private static StockMarketModBackend.ServerInstances BACKEND_INSTANCES;

    /**
     * Test hook (T-192): when set, receives the log text of every alert instead of the Discord post and
     * the admin chat messages. The server log line is still written.
     */
    public static volatile @Nullable Consumer<String> alertCapture;

    private TradeAlerts() {}

    /** @param backend the server instances (alert toggle default and user data) */
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        BACKEND_INSTANCES = backend;
    }

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
        Consumer<String> capture = alertCapture;
        if (capture != null) {
            capture.accept(logText);
            return;
        }
        StockMarketDiscord.alert(logText);
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server == null) return;
        Component chat = Component.literal("[StockMarket] ").append(message).withStyle(ChatFormatting.RED);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (PermissionCompat.hasPermission(player, StockMarketPermissions.NOTIFY, StockMarketPermissions.FALLBACK_LEVEL)
                    && alertsEnabled(player.getUUID()))
                player.sendSystemMessage(chat);
        }
    }

    /**
     * T-192: per-admin alert chat toggle.
     *
     * @param admin UUID of an admin that already passed the NOTIFY permission check
     * @return the admin's own toggle ({@code /stockmarket alerts on|off}), else the config default
     *         {@code Utilities.ALERTS_DEFAULT_ON} (true without settings)
     */
    public static boolean alertsEnabled(UUID admin) {
        User user = user(admin);
        if (user != null && user.getAlertsEnabled() != null)
            return user.getAlertsEnabled();
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.SERVER_SETTINGS == null)
            return true;
        Boolean def = BACKEND_INSTANCES.SERVER_SETTINGS.UTILITIES.ALERTS_DEFAULT_ON.get();
        return def == null || def;
    }

    /**
     * T-192: stores an admin's alert chat toggle in the StockMarket user data (saved with the market
     * data on the master).
     *
     * @param admin   the admin's UUID
     * @param enabled true = chat alerts on
     * @return false if the user is unknown (or this is not the master)
     */
    public static boolean setAlertsEnabled(UUID admin, boolean enabled) {
        User user = user(admin);
        if (user == null)
            return false;
        user.setAlertsEnabled(enabled);
        return true;
    }

    private static @Nullable User user(UUID uuid) {
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.MARKET_MANAGER == null)
            return null;
        ServerMarketManager manager = BACKEND_INSTANCES.MARKET_MANAGER.getServerMarketManager();
        return manager == null ? null : manager.getUser(uuid);
    }
}
