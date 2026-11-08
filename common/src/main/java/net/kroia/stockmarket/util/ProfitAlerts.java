package net.kroia.stockmarket.util;

import net.kroia.modutilities.ServerPlayerUtilities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * #89: per-player realized trading profit per time window (master server only).
 * <p>
 * Fed with the net money of every finished player order (sell proceeds positive, buy costs
 * negative, summed over all markets). When a player's sum within one window first exceeds the
 * admin threshold, one alert is raised through {@link TradeAlerts}; further profit in the same
 * window does not alert again.
 * <p>
 * Simplification: cash based, no cost basis. Selling items bought before the window also counts
 * as profit, so the threshold should sit well above normal farm sales.
 */
public final class ProfitAlerts {

    /**
     * Running sum of one player's window.
     * ponytail: fixed (tumbling) windows; a sliding window if exploiters straddle window borders.
     */
    private static final class Window {
        long startMillis;
        double profit;
        boolean alerted;
    }

    // ponytail: static, master-only state, never persisted; a restart starts fresh windows.
    private static final Map<UUID, Window> WINDOWS = new HashMap<>();

    private ProfitAlerts() {}

    /**
     * Adds one finished order's money to the player's window and alerts the admins once the
     * threshold is exceeded. Called on the server thread.
     *
     * @param player        the order's owner
     * @param realMoney     the order's net money in real units (positive = player received money)
     * @param threshold     alert threshold in real money; &lt;= 0 = off
     * @param windowMinutes window length in minutes
     */
    public static void onOrderFinished(UUID player, double realMoney, double threshold, long windowMinutes) {
        if (!record(player, realMoney, System.currentTimeMillis(), threshold, windowMinutes * 60_000L))
            return;
        double profit = WINDOWS.get(player).profit;
        ServerPlayer online = ServerPlayerUtilities.getOnlinePlayer(player);
        String name = online != null ? online.getName().getString() : player.toString();
        String amount = String.format(Locale.ROOT, "%.2f", profit);
        TradeAlerts.raise("Player " + name + " (" + player + ") realized " + amount
                        + " trading profit within " + windowMinutes + " min (threshold " + threshold + ")",
                Component.translatable("message.stockmarket.alert.profit", name, amount, windowMinutes));
    }

    /**
     * Pure bookkeeping of {@link #onOrderFinished}, separated for tests.
     *
     * @param player    the order's owner
     * @param realMoney the order's net money in real units
     * @param nowMillis current time
     * @param threshold alert threshold; &lt;= 0 = off (nothing is recorded)
     * @param windowMs  window length in ms
     * @return true exactly once per window, when the window's profit first exceeds the threshold
     */
    public static boolean record(UUID player, double realMoney, long nowMillis, double threshold, long windowMs) {
        if (!(threshold > 0)) return false;
        Window w = WINDOWS.computeIfAbsent(player, k -> new Window());
        if (nowMillis - w.startMillis >= windowMs) {
            w.startMillis = nowMillis;
            w.profit = 0;
            w.alerted = false;
        }
        w.profit += realMoney;
        if (w.alerted || w.profit <= threshold) return false;
        w.alerted = true;
        return true;
    }
}
