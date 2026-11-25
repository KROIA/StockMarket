package net.kroia.stockmarket.news;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.NbtCompat;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * World-event registry of the news event system (sequences plan §3, task T-096) —
 * the master-side long-term memory that outlives individual news events.
 * <p>
 * The registry stores two kinds of world state, both persisted to
 * {@code world/data/StockMarket/News/registry.nbt} by the {@code DataManager}
 * (beside {@link NewsHistory} — master-only by construction, the DataManager only
 * exists on the master):
 * <ul>
 *   <li><b>Auto fire records</b> — one {@link FireInfo} entry per distinct event id,
 *       created/updated via {@link #recordFire} on every publish (the publish hook is
 *       wired in T-098, {@code ServerNewsPublisher}). Bounded by the number of distinct
 *       event ids that ever fired, i.e. by the definition count — no cap needed.</li>
 *   <li><b>Custom key/value pairs</b> — explicit {@code "records": {...}} writes from
 *       event definitions (applied at publish, T-098) and admin edits. String→string,
 *       last write wins. Hard caps (plan §3): at most {@value #MAX_CUSTOM_KEYS} keys,
 *       key and value each at most {@value #MAX_KEY_LENGTH}/{@value #MAX_VALUE_LENGTH}
 *       characters — a write beyond a cap is <b>refused</b> (WARN log,
 *       {@link #putValue} returns false).</li>
 *   <li><b>Permanent shifts</b> (MU-181) — per permanent event id and market, how many of
 *       its bakes are not reverted yet ({@link #addShifts}/{@link #removeShift}), each
 *       market's normal default price from before its first unreverted shift
 *       ({@link #getNormalPrice}) and, per permanent event, when its counterpart event
 *       (the one with {@code "undoes"}) may fire next ({@link #getUndoEligibleAtMs}).</li>
 * </ul>
 * <p>
 * <b>Consumers of this contract:</b> the T-097 requirement predicates read via
 * {@link #getFireInfo}, {@link #fireCount}, {@link #hasFired} and {@link #getValue};
 * the T-099 admin ops ({@code REGISTRY_LIST}/{@code REGISTRY_CLEAR}) render the
 * unmodifiable {@link #fireInfos()}/{@link #customValues()} views and call
 * {@link #clearEvent}, {@link #clearKey} and {@link #clearAll}.
 * <p>
 * <b>Time basis (resolved decision, plan §10.6): wall clock, epoch milliseconds.</b>
 * {@code firstFiredEpochMs}/{@code lastFiredEpochMs} are {@code System.currentTimeMillis()}
 * values, matching {@code NewsRecord.timestampEpochMs} of the published history. This
 * deliberately <b>differs from event cooldowns</b>, which tick on active-server time and
 * therefore freeze while the server is offline: registry age predicates such as
 * {@code firedBefore.minSecondsAgo} keep "aging" across downtime. {@code lastFiredGameDay}
 * additionally stores the in-game day of the last fire for game-time-flavoured content.
 * <p>
 * <b>Thread model:</b> not thread-safe — main server thread only (like
 * {@link NewsHistory}): publishes come from the plugin update loop, admin request
 * handlers and world save/load run on the main thread as well.
 */
public class NewsWorldRegistry implements ServerSaveable {

    /** Maximum number of distinct custom keys (plan §3); further NEW keys are refused. */
    public static final int MAX_CUSTOM_KEYS = 256;
    /** Maximum length of a custom key in characters; longer keys are refused. */
    public static final int MAX_KEY_LENGTH = 256;
    /** Maximum length of a custom value in characters; longer values are refused. */
    public static final int MAX_VALUE_LENGTH = 256;

    // ── NBT keys ─────────────────────────────────────────────────────────

    private static final String KEY_FIRES = "fires";
    private static final String KEY_VALUES = "values";
    private static final String KEY_FIRE_ID = "id";
    private static final String KEY_FIRE_COUNT = "count";
    private static final String KEY_FIRE_FIRST_MS = "firstMs";
    private static final String KEY_FIRE_LAST_MS = "lastMs";
    private static final String KEY_FIRE_GAME_DAY = "gameDay";
    private static final String KEY_SHIFTS = "permanentShifts";
    private static final String KEY_SHIFT_EVENT = "event";
    private static final String KEY_SHIFT_COUNT = "count";
    private static final String KEY_NORMAL_PRICES = "normalPrices";
    private static final String KEY_NORMAL_PRICE = "price";
    private static final String KEY_UNDO_AT = "undoEligibleAt";
    private static final String KEY_SHIFTS_MIGRATED = "permanentShiftsMigrated";

    /**
     * Immutable per-event fire record — the auto-recorded side of the registry.
     * One instance per distinct event id; updated (replaced) on every fire.
     *
     * @param fireCount        how many times the event was published (≥ 1 once recorded)
     * @param firstFiredEpochMs wall-clock epoch ms of the FIRST publish (never changes)
     * @param lastFiredEpochMs  wall-clock epoch ms of the most recent publish
     * @param lastFiredGameDay  in-game day ({@code level.getDayTime() / 24000}) of the
     *                          most recent publish
     */
    public record FireInfo(int fireCount, long firstFiredEpochMs,
                           long lastFiredEpochMs, long lastFiredGameDay) {
    }

    // ── State ────────────────────────────────────────────────────────────

    /** Auto fire records, keyed by event id (insertion order kept for stable listing). */
    private final Map<String, FireInfo> fireInfos = new LinkedHashMap<>();

    /** Custom key/value store (insertion order kept for stable listing). */
    private final Map<String, String> customValues = new LinkedHashMap<>();

    /**
     * MU-181: per permanent event id and market, the number of that event's bakes that are
     * not reverted yet. The event may fire again only when none of its subscribed markets
     * has a count left; each firing of its counterpart takes one off per market.
     */
    private final Map<String, Map<ItemID, Integer>> shiftCounts = new LinkedHashMap<>();

    /**
     * MU-181: per market, the default price (real units) from before its first unreverted
     * permanent shift. Present exactly while the market has at least one shift left.
     */
    private final Map<ItemID, Double> normalPrices = new LinkedHashMap<>();

    /**
     * MU-181: per permanent event id, the wall-clock epoch ms from which its counterpart
     * may fire (absent = right away).
     */
    private final Map<String, Long> undoEligibleAtMs = new LinkedHashMap<>();

    /** MU-181: true once the one-time migration from the fire records has run. */
    private boolean shiftsMigrated;

    // ── Permanent shifts (MU-181) ────────────────────────────────────────

    /**
     * Records {@code count} unreverted permanent shifts of {@code eventId} on a market.
     * If the market had no shift left so far, {@code normalPrice} becomes its normal level.
     *
     * @param eventId     the permanent event whose bake shifted the market (null/blank ignored)
     * @param marketID    the shifted market (null ignored)
     * @param count       how many shifts to add (&lt;= 0 ignored)
     * @param normalPrice the market's default price before the shift (real units, must be
     *                    positive); only used when the market had no shift left
     */
    public void addShifts(@Nullable String eventId, @Nullable ItemID marketID, int count, double normalPrice) {
        if (eventId == null || eventId.isBlank() || marketID == null || count <= 0) return;
        if (totalShiftCount(marketID) == 0) {
            if (!(normalPrice > 0) || !Double.isFinite(normalPrice)) return;
            normalPrices.put(marketID, normalPrice);
        }
        shiftCounts.computeIfAbsent(eventId, k -> new LinkedHashMap<>()).merge(marketID, count, Integer::sum);
    }

    /**
     * Takes one shift of {@code eventId} off a market. When the market has no shift left
     * afterwards, its normal level is forgotten.
     *
     * @param eventId  the permanent event being reverted
     * @param marketID the market
     * @return true if a shift was taken off
     */
    public boolean removeShift(@Nullable String eventId, @Nullable ItemID marketID) {
        Map<ItemID, Integer> perMarket = eventId == null ? null : shiftCounts.get(eventId);
        Integer count = perMarket == null ? null : perMarket.get(marketID);
        if (count == null) return false;
        if (count <= 1) {
            perMarket.remove(marketID);
            if (perMarket.isEmpty()) shiftCounts.remove(eventId);
        } else {
            perMarket.put(marketID, count - 1);
        }
        if (totalShiftCount(marketID) == 0) normalPrices.remove(marketID);
        return true;
    }

    /**
     * @param eventId  the permanent event
     * @param marketID the market
     * @return how many of the event's shifts on that market are not reverted yet
     */
    public int shiftCount(@Nullable String eventId, @Nullable ItemID marketID) {
        Map<ItemID, Integer> perMarket = eventId == null ? null : shiftCounts.get(eventId);
        Integer count = perMarket == null ? null : perMarket.get(marketID);
        return count == null ? 0 : count;
    }

    /**
     * @param marketID the market
     * @return the unreverted shifts on that market, summed over all permanent events
     */
    public int totalShiftCount(@Nullable ItemID marketID) {
        int total = 0;
        for (Map<ItemID, Integer> perMarket : shiftCounts.values()) {
            total += perMarket.getOrDefault(marketID, 0);
        }
        return total;
    }

    /**
     * The event-level counter: the highest per-market shift count of the event.
     *
     * @param eventId the permanent event
     * @param markets only count these markets (e.g. the subscribed ones), or null for all
     * @return how many reversals the event still needs (0 = it may fire again)
     */
    public int pendingUndos(@Nullable String eventId, @Nullable Collection<ItemID> markets) {
        Map<ItemID, Integer> perMarket = eventId == null ? null : shiftCounts.get(eventId);
        if (perMarket == null) return 0;
        int max = 0;
        for (Map.Entry<ItemID, Integer> entry : perMarket.entrySet()) {
            if (markets == null || markets.contains(entry.getKey())) max = Math.max(max, entry.getValue());
        }
        return max;
    }

    /**
     * @param marketID the market
     * @return its default price (real units) from before its first unreverted shift, or
     *         null when it has no shift left
     */
    public @Nullable Double getNormalPrice(@Nullable ItemID marketID) {
        return marketID == null ? null : normalPrices.get(marketID);
    }

    /**
     * @param eventId the permanent event
     * @return the epoch ms from which its counterpart may fire; 0 when not set (right away)
     */
    public long getUndoEligibleAtMs(@Nullable String eventId) {
        Long at = eventId == null ? null : undoEligibleAtMs.get(eventId);
        return at == null ? 0 : at;
    }

    /**
     * @param eventId the permanent event (null/blank ignored)
     * @param epochMs the wall-clock epoch ms from which its counterpart may fire next
     */
    public void setUndoEligibleAtMs(@Nullable String eventId, long epochMs) {
        if (eventId == null || eventId.isBlank()) return;
        undoEligibleAtMs.put(eventId, epochMs);
    }

    /** @return true once the one-time migration from the fire records has run */
    public boolean isShiftsMigrated() {
        return shiftsMigrated;
    }

    /** Marks the one-time migration from the fire records as done (persisted). */
    public void markShiftsMigrated() {
        shiftsMigrated = true;
    }

    // ── Auto fire records ────────────────────────────────────────────────

    /**
     * Records one publish of the given event: creates the entry on the first fire
     * ({@code fireCount = 1}, first == last == {@code nowEpochMs}) or updates it
     * (count incremented, {@code firstFiredEpochMs} kept, last timestamp and game
     * day replaced). Called by the {@code ServerNewsPublisher} on every publish
     * (wired in T-098).
     *
     * @param eventId    the published event's id; null/blank is ignored with a WARN
     * @param nowEpochMs the publish wall-clock time in epoch ms (see class Javadoc
     *                   on the time basis)
     * @param gameDay    the current in-game day at publish time
     */
    public void recordFire(@Nullable String eventId, long nowEpochMs, long gameDay) {
        if (eventId == null || eventId.isBlank()) {
            StockMarketMod.LOGGER.warn("[NewsWorldRegistry] recordFire() ignored: null/blank event id");
            return;
        }
        FireInfo previous = fireInfos.get(eventId);
        if (previous == null) {
            fireInfos.put(eventId, new FireInfo(1, nowEpochMs, nowEpochMs, gameDay));
        } else {
            fireInfos.put(eventId, new FireInfo(previous.fireCount() + 1,
                    previous.firstFiredEpochMs(), nowEpochMs, gameDay));
        }
    }

    /**
     * @param eventId the event id to look up (null-safe)
     * @return the fire record of the event, or null if it never fired
     *         (primary read API of the T-097 {@code firedBefore}/{@code notFiredWithin}
     *         age predicates)
     */
    public @Nullable FireInfo getFireInfo(@Nullable String eventId) {
        return eventId == null ? null : fireInfos.get(eventId);
    }

    /**
     * @param eventId the event id to look up (null-safe)
     * @return how many times the event fired; 0 for unknown ids
     *         (T-097 {@code countAtLeast}/{@code countAtMost})
     */
    public int fireCount(@Nullable String eventId) {
        FireInfo info = getFireInfo(eventId);
        return info != null ? info.fireCount() : 0;
    }

    /**
     * @param eventId the event id to look up (null-safe)
     * @return true if the event fired at least once
     *         (T-097 {@code firedBefore}/{@code notFired})
     */
    public boolean hasFired(@Nullable String eventId) {
        return getFireInfo(eventId) != null;
    }

    /**
     * @return an unmodifiable live view of all fire records, keyed by event id
     *         (T-099 {@code REGISTRY_LIST} rendering); iterate, never mutate
     */
    public @NotNull Map<String, FireInfo> fireInfos() {
        return Collections.unmodifiableMap(fireInfos);
    }

    // ── Custom key/value store ───────────────────────────────────────────

    /**
     * Stores one custom key/value pair (explicit {@code "records"} write or admin
     * edit). Overwrites an existing key (last write wins). The write is <b>refused</b>
     * (WARN log, false returned) when:
     * <ul>
     *   <li>key or value is null, or the key is blank,</li>
     *   <li>the key is longer than {@value #MAX_KEY_LENGTH} characters,</li>
     *   <li>the value is longer than {@value #MAX_VALUE_LENGTH} characters,</li>
     *   <li>the key is NEW and the store already holds {@value #MAX_CUSTOM_KEYS} keys
     *       (overwriting an existing key is always allowed at the cap).</li>
     * </ul>
     *
     * @param key   the key to write
     * @param value the value to store (numeric strings allowed — the T-097
     *              {@code keyAtLeast}/{@code keyAtMost} predicates parse them)
     * @return true if the pair was stored, false if the write was refused
     */
    public boolean putValue(@Nullable String key, @Nullable String value) {
        if (key == null || key.isBlank() || value == null) {
            StockMarketMod.LOGGER.warn("[NewsWorldRegistry] putValue() refused: null/blank key or null value");
            return false;
        }
        if (key.length() > MAX_KEY_LENGTH) {
            StockMarketMod.LOGGER.warn("[NewsWorldRegistry] putValue() refused: key longer than {} chars: '{}…'",
                    MAX_KEY_LENGTH, key.substring(0, 32));
            return false;
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            StockMarketMod.LOGGER.warn("[NewsWorldRegistry] putValue() refused: value of key '{}' longer than {} chars",
                    key, MAX_VALUE_LENGTH);
            return false;
        }
        if (!customValues.containsKey(key) && customValues.size() >= MAX_CUSTOM_KEYS) {
            StockMarketMod.LOGGER.warn("[NewsWorldRegistry] putValue() refused: custom key cap of {} reached (new key '{}')",
                    MAX_CUSTOM_KEYS, key);
            return false;
        }
        customValues.put(key, value);
        return true;
    }

    /**
     * @param key the key to look up (null-safe)
     * @return the stored value, or null if the key is absent
     *         (T-097 {@code keyEquals}/{@code keyExists}/… predicates)
     */
    public @Nullable String getValue(@Nullable String key) {
        return key == null ? null : customValues.get(key);
    }

    /**
     * Removes one custom key/value pair.
     *
     * @param key the key to remove (null-safe)
     * @return true if the key existed and was removed
     */
    public boolean removeValue(@Nullable String key) {
        return key != null && customValues.remove(key) != null;
    }

    /**
     * @return an unmodifiable live view of all custom key/value pairs
     *         (T-099 {@code REGISTRY_LIST} rendering); iterate, never mutate
     */
    public @NotNull Map<String, String> customValues() {
        return Collections.unmodifiableMap(customValues);
    }

    // ── Admin clear operations (T-099 REGISTRY_CLEAR) ────────────────────

    /**
     * Deletes the fire record of one event — the event counts as "never fired"
     * again for the T-097 predicates.
     *
     * @param eventId the event id to clear (null-safe)
     * @return true if a record existed and was removed
     */
    public boolean clearEvent(@Nullable String eventId) {
        return eventId != null && fireInfos.remove(eventId) != null;
    }

    /**
     * Deletes one custom key/value pair (alias of {@link #removeValue}, named for
     * the T-099 {@code REGISTRY_CLEAR} vocabulary).
     *
     * @param key the key to clear (null-safe)
     * @return true if the key existed and was removed
     */
    public boolean clearKey(@Nullable String key) {
        return removeValue(key);
    }

    /**
     * Wipes the whole registry: all fire records, custom pairs and permanent-shift state
     * (counts, normal prices, counterpart timers and the migration flag).
     */
    public void clearAll() {
        fireInfos.clear();
        customValues.clear();
        shiftCounts.clear();
        normalPrices.clear();
        undoEligibleAtMs.clear();
        shiftsMigrated = false;
    }

    /** @return true if the registry holds no fire records, custom pairs or permanent shifts */
    public boolean isEmpty() {
        return fireInfos.isEmpty() && customValues.isEmpty() && shiftCounts.isEmpty();
    }

    // ── NBT persistence (ServerSaveable) ─────────────────────────────────

    /**
     * Writes both stores into the given tag: the fire records as a list of compounds
     * ({@code id/count/firstMs/lastMs/gameDay}) and the custom pairs as one string
     * compound.
     *
     * @param tag the tag to populate
     * @return true (this save cannot fail)
     */
    @Override
    public boolean save(CompoundTag tag) {
        ListTag firesTag = new ListTag();
        for (Map.Entry<String, FireInfo> entry : fireInfos.entrySet()) {
            FireInfo info = entry.getValue();
            CompoundTag fireTag = new CompoundTag();
            fireTag.putString(KEY_FIRE_ID, entry.getKey());
            fireTag.putInt(KEY_FIRE_COUNT, info.fireCount());
            fireTag.putLong(KEY_FIRE_FIRST_MS, info.firstFiredEpochMs());
            fireTag.putLong(KEY_FIRE_LAST_MS, info.lastFiredEpochMs());
            fireTag.putLong(KEY_FIRE_GAME_DAY, info.lastFiredGameDay());
            firesTag.add(fireTag);
        }
        tag.put(KEY_FIRES, firesTag);

        CompoundTag valuesTag = new CompoundTag();
        for (Map.Entry<String, String> entry : customValues.entrySet()) {
            valuesTag.putString(entry.getKey(), entry.getValue());
        }
        tag.put(KEY_VALUES, valuesTag);

        ListTag shiftsTag = new ListTag();
        for (Map.Entry<String, Map<ItemID, Integer>> perEvent : shiftCounts.entrySet()) {
            for (Map.Entry<ItemID, Integer> entry : perEvent.getValue().entrySet()) {
                CompoundTag shiftTag = new CompoundTag();
                shiftTag.putString(KEY_SHIFT_EVENT, perEvent.getKey());
                entry.getKey().save(shiftTag);
                shiftTag.putInt(KEY_SHIFT_COUNT, entry.getValue());
                shiftsTag.add(shiftTag);
            }
        }
        tag.put(KEY_SHIFTS, shiftsTag);

        ListTag normalsTag = new ListTag();
        for (Map.Entry<ItemID, Double> entry : normalPrices.entrySet()) {
            CompoundTag normalTag = new CompoundTag();
            entry.getKey().save(normalTag);
            normalTag.putDouble(KEY_NORMAL_PRICE, entry.getValue());
            normalsTag.add(normalTag);
        }
        tag.put(KEY_NORMAL_PRICES, normalsTag);

        CompoundTag undoAtTag = new CompoundTag();
        undoEligibleAtMs.forEach(undoAtTag::putLong);
        tag.put(KEY_UNDO_AT, undoAtTag);
        tag.putBoolean(KEY_SHIFTS_MIGRATED, shiftsMigrated);
        return true;
    }

    /**
     * Restores the registry saved by {@link #save(CompoundTag)}, replacing the current
     * content. All keys are contains()-guarded — an empty/absent tag section simply
     * yields an empty store (first run / pre-registry world). Malformed fire entries
     * (blank id) are skipped and custom pairs beyond the caps (hand-edited file) are
     * dropped, both with a WARN — skip-and-continue, a partially readable registry is
     * better than none.
     *
     * @param tag the tag to read from
     * @return true (this load never blocks — see skip-and-continue above)
     */
    @Override
    public boolean load(CompoundTag tag) {
        clearAll();
        if (NbtCompat.contains(tag, KEY_FIRES, Tag.TAG_LIST)) {
            ListTag firesTag = NbtCompat.getList(tag, KEY_FIRES, Tag.TAG_COMPOUND);
            for (int i = 0; i < firesTag.size(); i++) {
                CompoundTag fireTag = NbtCompat.getCompound(firesTag, i);
                String eventId = NbtCompat.getString(fireTag, KEY_FIRE_ID);
                if (eventId.isBlank()) {
                    StockMarketMod.LOGGER.warn("[NewsWorldRegistry] load(): skipped fire entry {} without event id", i);
                    continue;
                }
                // Absent numeric keys default to 0 (getInt/getLong contract); clamp the
                // count so a loaded entry always means "fired at least once".
                fireInfos.put(eventId, new FireInfo(
                        Math.max(1, NbtCompat.getInt(fireTag, KEY_FIRE_COUNT)),
                        NbtCompat.getLong(fireTag, KEY_FIRE_FIRST_MS),
                        NbtCompat.getLong(fireTag, KEY_FIRE_LAST_MS),
                        NbtCompat.getLong(fireTag, KEY_FIRE_GAME_DAY)));
            }
        }
        if (NbtCompat.contains(tag, KEY_VALUES, Tag.TAG_COMPOUND)) {
            CompoundTag valuesTag = NbtCompat.getCompound(tag, KEY_VALUES);
            for (String key : NbtCompat.keys(valuesTag)) {
                // Re-apply the write caps (a saved file honors them, but the file may
                // have been edited by hand); putValue logs the WARN for each refusal.
                putValue(key, NbtCompat.getString(valuesTag, key));
            }
        }
        // MU-181 permanent shifts. A pre-MU-181 file has none of these keys (its SM-341
        // "bakeDeltas" are ignored): the plugin then runs the one-time migration from the
        // fire records. A shift whose market has no normal level cannot be reverted and
        // is dropped.
        Map<ItemID, Double> loadedNormals = new LinkedHashMap<>();
        if (NbtCompat.contains(tag, KEY_NORMAL_PRICES, Tag.TAG_LIST)) {
            ListTag normalsTag = NbtCompat.getList(tag, KEY_NORMAL_PRICES, Tag.TAG_COMPOUND);
            for (int i = 0; i < normalsTag.size(); i++) {
                CompoundTag normalTag = NbtCompat.getCompound(normalsTag, i);
                ItemID marketID = ItemID.createFromTag(normalTag);
                if (marketID == null || marketID.getShort() == 0) continue;
                loadedNormals.put(marketID, NbtCompat.getDouble(normalTag, KEY_NORMAL_PRICE));
            }
        }
        if (NbtCompat.contains(tag, KEY_SHIFTS, Tag.TAG_LIST)) {
            ListTag shiftsTag = NbtCompat.getList(tag, KEY_SHIFTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < shiftsTag.size(); i++) {
                CompoundTag shiftTag = NbtCompat.getCompound(shiftsTag, i);
                ItemID marketID = ItemID.createFromTag(shiftTag);
                if (marketID == null || marketID.getShort() == 0) continue;
                addShifts(NbtCompat.getString(shiftTag, KEY_SHIFT_EVENT), marketID,
                        NbtCompat.getInt(shiftTag, KEY_SHIFT_COUNT), loadedNormals.getOrDefault(marketID, 0.0));
            }
        }
        if (NbtCompat.contains(tag, KEY_UNDO_AT, Tag.TAG_COMPOUND)) {
            CompoundTag undoAtTag = NbtCompat.getCompound(tag, KEY_UNDO_AT);
            for (String eventId : NbtCompat.keys(undoAtTag)) {
                setUndoEligibleAtMs(eventId, NbtCompat.getLong(undoAtTag, eventId));
            }
        }
        shiftsMigrated = NbtCompat.getBoolean(tag, KEY_SHIFTS_MIGRATED);
        return true;
    }
}
