package net.kroia.stockmarket.news;

import net.kroia.modutilities.persistence.ServerSaveable;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    /** Wipes the whole registry: all fire records and all custom pairs. */
    public void clearAll() {
        fireInfos.clear();
        customValues.clear();
    }

    /** @return true if the registry holds neither fire records nor custom pairs */
    public boolean isEmpty() {
        return fireInfos.isEmpty() && customValues.isEmpty();
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
        if (tag.contains(KEY_FIRES, Tag.TAG_LIST)) {
            ListTag firesTag = tag.getList(KEY_FIRES, Tag.TAG_COMPOUND);
            for (int i = 0; i < firesTag.size(); i++) {
                CompoundTag fireTag = firesTag.getCompound(i);
                String eventId = fireTag.getString(KEY_FIRE_ID);
                if (eventId.isBlank()) {
                    StockMarketMod.LOGGER.warn("[NewsWorldRegistry] load(): skipped fire entry {} without event id", i);
                    continue;
                }
                // Absent numeric keys default to 0 (getInt/getLong contract); clamp the
                // count so a loaded entry always means "fired at least once".
                fireInfos.put(eventId, new FireInfo(
                        Math.max(1, fireTag.getInt(KEY_FIRE_COUNT)),
                        fireTag.getLong(KEY_FIRE_FIRST_MS),
                        fireTag.getLong(KEY_FIRE_LAST_MS),
                        fireTag.getLong(KEY_FIRE_GAME_DAY)));
            }
        }
        if (tag.contains(KEY_VALUES, Tag.TAG_COMPOUND)) {
            CompoundTag valuesTag = tag.getCompound(KEY_VALUES);
            for (String key : valuesTag.getAllKeys()) {
                // Re-apply the write caps (a saved file honors them, but the file may
                // have been edited by hand); putValue logs the WARN for each refusal.
                putValue(key, valuesTag.getString(key));
            }
        }
        return true;
    }
}
