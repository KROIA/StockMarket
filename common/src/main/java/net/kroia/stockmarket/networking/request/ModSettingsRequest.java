package net.kroia.stockmarket.networking.request;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kroia.modutilities.UtilitiesPlatform;
import net.kroia.modutilities.setting.SettingsStore;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.networking.NetworkGate;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * ARRS request used by the master-only "Mod Settings" admin screen
 * ({@code net.kroia.stockmarket.screen.ModSettingsScreen}) to read and edit the
 * server's {@code settings.json} in-game.
 * <p>
 * <b>Actions:</b>
 * <ul>
 *   <li>{@link Action#GET} — returns the current values of all editable settings groups.</li>
 *   <li>{@link Action#SET} — applies the values from the payload, sanitizes them
 *       (see {@link #sanitize}), persists them to {@code settings.json} and returns
 *       the confirmed (post-sanitize) state.</li>
 * </ul>
 * <p>
 * <b>Wire format:</b> the payload is the {@link SettingsStore} JSON string of
 * {@link StockMarketModSettings#getEditableGroups()} — the exact same generic
 * serialization (including the ItemStack custom parser for the CURRENCY setting)
 * that is used for the settings.json file itself. This avoids duplicating a
 * structured codec for every setting and automatically covers settings added later.
 * <p>
 * <b>Security:</b> the handler enforces BOTH conditions server-side, regardless of
 * any client-side UI gating:
 * <ul>
 *   <li>the sending player must have op permission level 2 (same level required by
 *       {@code /stockmarket manage}), and</li>
 *   <li>this server must be the MASTER server — only the master loads and owns
 *       settings.json; slaves keep compile-time defaults by design.</li>
 * </ul>
 */
public class ModSettingsRequest extends StockMarketGenericRequest<ModSettingsRequest.InputData, ModSettingsRequest.OutputData> {

    // ------------------------------------------------------------------
    // Validation bounds applied by sanitize(). Documented here so UI and
    // server agree on a single source of truth.
    // ------------------------------------------------------------------

    /** Minimum autosave interval in minutes (avoid save-spam every tick). */
    public static final long MIN_SAVE_INTERVAL_MINUTES = 1L;
    /** Maximum autosave interval in minutes (1 day). */
    public static final long MAX_SAVE_INTERVAL_MINUTES = 1440L;

    /** Minimum virtual orderbook array size (values below break price resolution). */
    public static final int MIN_ORDERBOOK_ARRAY_SIZE = 100;
    /** Maximum virtual orderbook array size (memory guard). */
    public static final int MAX_ORDERBOOK_ARRAY_SIZE = 1_000_000;

    /** Minimum candle time in milliseconds (1 second). */
    public static final long MIN_CANDLE_TIME_MS = 1_000L;
    /** Maximum candle time in milliseconds (1 day). */
    public static final long MAX_CANDLE_TIME_MS = 86_400_000L;

    /** Minimum villager price refresh interval in minutes. */
    public static final long MIN_PRICE_REFRESH_INTERVAL_MINUTES = 1L;
    /** Maximum villager price refresh interval in minutes (1 week). */
    public static final long MAX_PRICE_REFRESH_INTERVAL_MINUTES = 10_080L;

    /** Minimum villager buy/sell margin (must be > 0; 0 would make trades free/worthless). */
    public static final float MIN_VILLAGER_MARGIN = 0.01f;
    /** Maximum villager buy/sell margin. */
    public static final float MAX_VILLAGER_MARGIN = 100.f;

    /**
     * The two request actions.
     */
    public enum Action {
        /** Fetch the current settings state (input JSON is ignored/empty). */
        GET,
        /** Apply + persist the settings contained in the input JSON. */
        SET;

        /** Wire codec: encoded as a single byte (the ordinal). */
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC =
                ByteBufCodecs.BYTE.map(b -> Action.values()[b], a -> (byte) a.ordinal())
                        .cast();
    }

    /**
     * Input payload sent from the client to the server.
     *
     * @param action       GET to fetch, SET to apply + persist
     * @param settingsJson SettingsStore JSON of the editable groups (empty string for GET)
     */
    public record InputData(Action action, String settingsJson) {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                Action.STREAM_CODEC, InputData::action,
                ByteBufCodecs.STRING_UTF8, InputData::settingsJson,
                InputData::new
        );
    }

    /**
     * Output payload returned from the server to the client.
     *
     * @param success      true if the action succeeded (for SET: values applied AND persisted)
     * @param message      short human-readable failure reason, empty on success
     * @param settingsJson the CONFIRMED current server state (post-sanitize) as
     *                     SettingsStore JSON; empty string when the request was rejected
     *                     before touching the settings (no permission / not master)
     */
    public record OutputData(boolean success, String message, String settingsJson) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, OutputData::success,
                ByteBufCodecs.STRING_UTF8, OutputData::message,
                ByteBufCodecs.STRING_UTF8, OutputData::settingsJson,
                OutputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return ModSettingsRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(false, "Request failed", "");
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        // --- Permission check: op level 2, matching /stockmarket manage and the
        // other management requests (PluginSettingsRequest etc.). Enforced here
        // regardless of the client-side button gating.
        if (playerSender == null || !hasPermission(playerSender)) {
            warn("Rejected mod settings " + input.action() + " from " + getPlayerName(playerSender) + ": no permission");
            return CompletableFuture.completedFuture(new OutputData(false, "No permission (op level 2 required)", ""));
        }

        // --- Master check: only the master server loads/owns settings.json.
        // Requests are normally auto-routed to the master, but enforce it anyway
        // (defense in depth against misconfigured multi-server setups).
        if (!getServerBankManager().isMaster()) {
            warn("Rejected mod settings " + input.action() + " from " + getPlayerName(playerSender) + ": this server is not the master");
            return CompletableFuture.completedFuture(new OutputData(false, "Settings can only be edited on the master server", ""));
        }

        StockMarketModSettings settings = BACKEND_INSTANCES.SERVER_SETTINGS;
        if (settings == null || BACKEND_INSTANCES.DATA_MANAGER == null) {
            return CompletableFuture.completedFuture(new OutputData(false, "Server settings not available", ""));
        }

        SettingsStore store = new SettingsStore();

        if (input.action() == Action.GET) {
            // GET is intentionally NOT gated by the untrusted-slave check.
            // Read-only queries stay open on untrusted slaves so the admin UI
            // can still be browsed / diagnosed — the mutation is the SET path.
            return CompletableFuture.completedFuture(
                    new OutputData(true, "", store.toJsonString(settings.getEditableGroups())));
        }

        // T-123 (untrusted slave gate): SET is mutating — a forwarded SET from
        // an untrusted slave is refused before it touches settings.json.
        // Master-local ModSettings edits (single-server, or admin on the master)
        // pass with slaveID="" and always proceed here.
        if (!NetworkGate.isMutatingCallAllowed(slaveID, "ModSettingsRequest")) {
            return CompletableFuture.completedFuture(new OutputData(false,
                    "Rejected: this slave server is not trusted by the master",
                    store.toJsonString(settings.getEditableGroups())));
        }

        // ------------------------------ SET ------------------------------
        JsonObject root;
        try {
            root = JsonParser.parseString(input.settingsJson()).getAsJsonObject();
        } catch (Exception e) {
            error("Malformed mod settings payload from " + getPlayerName(playerSender), e);
            return CompletableFuture.completedFuture(new OutputData(false, "Malformed settings payload",
                    store.toJsonString(settings.getEditableGroups())));
        }

        // Snapshots taken BEFORE applying: used to restore an invalid currency and
        // to detect whether the VillagerTrading group changed (slave propagation).
        ItemStack currencyBefore = settings.MARKET.CURRENCY.get();
        String villagerBefore = store.toJsonString(settings.VILLAGER_TRADING);

        String applyError = null;
        try {
            // Generic apply: settings/groups missing from the payload keep their
            // current values (SettingsStore skips them).
            store.fromJson(settings.getEditableGroups(), root);
        } catch (Exception e) {
            // Partial application is possible (groups are applied in order) —
            // sanitize below and return the ACTUAL server state so the client re-syncs.
            error("Failed to apply mod settings from " + getPlayerName(playerSender), e);
            applyError = "Failed to apply some values: " + e.getMessage();
        }

        // Clamp all numeric values into their documented bounds and restore the
        // previous currency if the new one is empty/unresolvable.
        sanitize(settings, currencyBefore);

        // Persist ONLY settings.json (targeted save — no full market/plugin NBT save).
        boolean saved = BACKEND_INSTANCES.DATA_MANAGER.saveSettingsToFile(UtilitiesPlatform.getServer());
        if (!saved && applyError == null) {
            applyError = "Applied in memory, but failed to write settings.json";
        }

        // --- Per-group slave propagation decision ---
        // * VillagerTrading: slaves render villager prices from the price table the
        //   master broadcasts. Recompute + broadcast immediately (same path as the
        //   periodic refresh in VillagerTradeManager.tickMaster()) so slaves pick
        //   up ENABLED/margin changes without waiting for the next interval.
        // * Utilities (autosave, logging) and ServerMarket (orderbook size, currency,
        //   candle time) are consumed ONLY on the master (slaves have no DataManager,
        //   no ServerMarketManager and no master-side logger settings) — no
        //   propagation needed.
        String villagerAfter = store.toJsonString(settings.VILLAGER_TRADING);
        if (!villagerAfter.equals(villagerBefore) && BACKEND_INSTANCES.VILLAGER_TRADE_MANAGER != null) {
            BACKEND_INSTANCES.VILLAGER_TRADE_MANAGER.recomputeTable();
            BACKEND_INSTANCES.VILLAGER_TRADE_MANAGER.broadcastTable();
            info("VillagerTrading settings changed — price table recomputed and broadcast to slaves");
        }

        // Notify other online admins about the change.
        broadcastToAdmins(playerSender, getPlayerName(playerSender) + " updated the StockMarket mod settings");
        info("Mod settings updated by " + getPlayerName(playerSender) + (saved ? " (persisted)" : " (PERSIST FAILED)"));

        boolean success = saved && applyError == null;
        return CompletableFuture.completedFuture(new OutputData(success,
                applyError == null ? "" : applyError,
                store.toJsonString(settings.getEditableGroups())));
    }

    /**
     * Clamps all numeric settings into their documented bounds and restores the
     * previous currency item if the (newly applied) currency is empty or null.
     * <p>
     * Bounds (see the constants above):
     * <ul>
     *   <li>{@code Utilities.SAVE_INTERVAL_MINUTES} ∈ [1, 1440]</li>
     *   <li>{@code ServerMarket.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE} ∈ [100, 1,000,000]</li>
     *   <li>{@code ServerMarket.CANDLE_TIME} ∈ [1,000 ms, 86,400,000 ms]</li>
     *   <li>{@code ServerMarket.CURRENCY}: must be a non-empty ItemStack, else previous value</li>
     *   <li>{@code VillagerTrading.PRICE_REFRESH_INTERVAL_MINUTES} ∈ [1, 10,080]</li>
     *   <li>{@code VillagerTrading.VILLAGER_BUY_MARGIN / VILLAGER_SELL_MARGIN} ∈ [0.01, 100];
     *       NaN/Infinite values fall back to the setting's default</li>
     * </ul>
     * Static and side-effect free apart from the settings object itself, so the
     * in-game test suite can verify the bounds without a running request.
     *
     * @param settings         the live (or test) settings object to sanitize
     * @param previousCurrency the currency value before the edit, restored when the
     *                         new currency is empty; may be null
     */
    public static void sanitize(StockMarketModSettings settings, @Nullable ItemStack previousCurrency) {
        // Utilities
        settings.UTILITIES.SAVE_INTERVAL_MINUTES.set(
                clamp(settings.UTILITIES.SAVE_INTERVAL_MINUTES.get(), MIN_SAVE_INTERVAL_MINUTES, MAX_SAVE_INTERVAL_MINUTES));

        // ServerMarket
        settings.MARKET.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE.set(
                Math.max(MIN_ORDERBOOK_ARRAY_SIZE, Math.min(MAX_ORDERBOOK_ARRAY_SIZE,
                        settings.MARKET.VIRTUAL_ORDERBOOK_DEFAULT_ARRAY_SIZE.get())));
        settings.MARKET.CANDLE_TIME.set(
                clamp(settings.MARKET.CANDLE_TIME.get(), MIN_CANDLE_TIME_MS, MAX_CANDLE_TIME_MS));
        ItemStack currency = settings.MARKET.CURRENCY.get();
        if (currency == null || currency.isEmpty()) {
            // Never allow an empty trading currency — keep the previous one.
            settings.MARKET.CURRENCY.set(previousCurrency != null ? previousCurrency
                    : settings.MARKET.CURRENCY.getDefaultValue());
        }

        // VillagerTrading
        settings.VILLAGER_TRADING.PRICE_REFRESH_INTERVAL_MINUTES.set(
                clamp(settings.VILLAGER_TRADING.PRICE_REFRESH_INTERVAL_MINUTES.get(),
                        MIN_PRICE_REFRESH_INTERVAL_MINUTES, MAX_PRICE_REFRESH_INTERVAL_MINUTES));
        settings.VILLAGER_TRADING.VILLAGER_BUY_MARGIN.set(
                clampMargin(settings.VILLAGER_TRADING.VILLAGER_BUY_MARGIN.get(),
                        settings.VILLAGER_TRADING.VILLAGER_BUY_MARGIN.getDefaultValue()));
        settings.VILLAGER_TRADING.VILLAGER_SELL_MARGIN.set(
                clampMargin(settings.VILLAGER_TRADING.VILLAGER_SELL_MARGIN.get(),
                        settings.VILLAGER_TRADING.VILLAGER_SELL_MARGIN.getDefaultValue()));
    }

    /** Clamps a Long setting value into [min, max]; null falls back to min. */
    private static long clamp(@Nullable Long value, long min, long max) {
        if (value == null) return min;
        return Math.max(min, Math.min(max, value));
    }

    /** Clamps a margin into [{@link #MIN_VILLAGER_MARGIN}, {@link #MAX_VILLAGER_MARGIN}]; NaN/Infinite/null → default. */
    private static float clampMargin(@Nullable Float value, float defaultValue) {
        if (value == null || value.isNaN() || value.isInfinite()) return defaultValue;
        return Math.max(MIN_VILLAGER_MARGIN, Math.min(MAX_VILLAGER_MARGIN, value));
    }

    /**
     * Checks whether the player has op permission level 2 (required by
     * {@code /stockmarket manage} — the management screen's permission model).
     *
     * @param playerUUID the sending player's UUID
     * @return true if the player is online and has op level 2
     */
    private boolean hasPermission(UUID playerUUID) {
        MinecraftServer server = UtilitiesPlatform.getServer();
        if (server == null) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        return player != null && player.hasPermissions(2);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, OutputData output) {
        OutputData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public OutputData decodeOutput(RegistryFriendlyByteBuf buf) {
        return OutputData.STREAM_CODEC.decode(buf);
    }
}
