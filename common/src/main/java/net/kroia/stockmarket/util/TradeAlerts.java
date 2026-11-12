package net.kroia.stockmarket.util;

import net.kroia.banksystem.data.DiagnosticsDatabase;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.PermissionCompat;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.OrderOriginLog;
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
 * {@link PumpDumpDetector}) only decide <i>when</i> to flag and call {@link #raise}. SM-316: every alert also
 * becomes one row of the TradeAlert table in {@code stockmarket_diagnostics.db} ({@link Row}).
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
     * Its TradeAlert row has kind {@link Row#KIND_OTHER} and no structured data; detectors use
     * {@link #raise(Row, String, Component)}.
     *
     * @param logText plain text for the server log and the Discord post (language independent)
     * @param message chat message for admins, usually {@code Component.translatable(key, args)}
     *                so it is resolved in each admin's client language; prefixed with
     *                "[StockMarket] " and colored red
     */
    public static void raise(String logText, Component message) {
        raise(new Row(Row.KIND_OTHER, null, null, null, null, null, null, null), logText, message);
    }

    /**
     * SM-316: one alert's structured data, written as a row of the TradeAlert table in
     * {@code stockmarket_diagnostics.db} ({@link OrderOriginLog}). Every field but {@code kind} may be null when
     * the detector does not know it.
     *
     * @param kind      {@link #KIND_PROFIT}, {@link #KIND_PUMP}, {@link #KIND_CYCLE} or {@link #KIND_OTHER}
     *                  (column {@code kind})
     * @param player    the flagged player ({@code player_uuid})
     * @param account   the bank account of the flagged order ({@code account_number}); null for profit alerts,
     *                  which sum over all of a player's accounts
     * @param market    the market's item ({@code item_id}, the BankSystem ItemID short); null for profit alerts,
     *                  which sum over all markets
     * @param amount    raw value that crossed the threshold ({@code amount}): profit and cycle = profit in raw
     *                  money units, pump = raw market price rise of the tick (end - start)
     * @param windowMs  time window in ms ({@code window_ms}): profit = profit window, cycle = cycle window,
     *                  pump = null (one tick)
     * @param threshold the threshold that was exceeded ({@code threshold}): profit and cycle = real money,
     *                  pump = price move in percent
     * @param orderIds  comma-separated order IDs ({@code order_ids}): pump = the market buy, then the player's
     *                  own sell orders resting above the start price; cycle = the sell order; profit = null
     */
    public record Row(String kind, @Nullable UUID player, @Nullable Integer account, @Nullable ItemID market,
                      @Nullable Long amount, @Nullable Long windowMs, @Nullable Double threshold, @Nullable String orderIds) {
        /** Realized trading profit within the profit window ({@link ProfitAlerts}). */
        public static final String KIND_PROFIT = "profit";
        /** Pump-and-dump pump: market buy moved the price with an own sell resting above ({@link PumpDumpDetector}). */
        public static final String KIND_PUMP = "pump_dump_pump";
        /** Pump-and-dump cycle: profitable sell of items bought within the cycle window ({@link PumpDumpDetector}). */
        public static final String KIND_CYCLE = "pump_dump_cycle";
        /** Alert raised without structured data ({@link #raise(String, Component)}). */
        public static final String KIND_OTHER = "other";
    }

    /**
     * {@link #raise(String, Component)} plus one TradeAlert row in {@code stockmarket_diagnostics.db} (SM-316).
     * The row is queued on the diagnostics writer thread (never blocks) and only written where that file is open:
     * the master. It is also written while {@link #alertCapture} is set.
     *
     * @param row     the alert's structured data
     * @param logText plain text for the server log, the Discord post and the row's {@code text}
     * @param message chat message for admins, as in {@link #raise(String, Component)}
     */
    public static void raise(Row row, String logText, Component message) {
        StockMarketMod.LOGGER.warn("[StockMarket Alert] {}", logText);
        record(OrderOriginLog.live(), row, logText, System.currentTimeMillis());
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

    /**
     * Queues one TradeAlert row; never throws (diagnostics must not break the order flow).
     *
     * @param db      the diagnostics file ({@link OrderOriginLog#live()}), or null to do nothing (slave, client)
     * @param row     the alert's structured data
     * @param text    the alert's log text
     * @param tsMillis the alert's time, epoch ms
     */
    public static void record(@Nullable DiagnosticsDatabase db, Row row, String text, long tsMillis) {
        if (db == null)
            return;
        try {
            db.execute(OrderOriginLog.INSERT_TRADE_ALERT, tsMillis, row.kind(),
                    row.player() == null ? null : row.player().toString(), row.account(),
                    row.market() == null ? null : (int) row.market().getShort(), row.amount(), row.windowMs(),
                    row.threshold(), text, row.orderIds());
        } catch (RuntimeException e) {
            StockMarketMod.LOGGER.warn("[TradeAlerts] could not record alert {}: {}", row.kind(), e.toString());
        }
    }

    private static @Nullable User user(UUID uuid) {
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.MARKET_MANAGER == null)
            return null;
        ServerMarketManager manager = BACKEND_INSTANCES.MARKET_MANAGER.getServerMarketManager();
        return manager == null ? null : manager.getUser(uuid);
    }
}
