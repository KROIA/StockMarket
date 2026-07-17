package net.kroia.stockmarket.news;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Client-side cache of recently published {@link NewsRecord}s (T-073, plan §3/§4).
 * <p>
 * Filled live by the {@code NewsPublishedPacket} client handler while the player is
 * connected, so the future newspaper screen (T-074) can render a "LIVE" section
 * instantly — without waiting for a {@code NewsHistoryRequest} round-trip — and merge
 * these records with the paginated history pages it fetches on open (records carry a
 * unique {@code newsUid}, so merging is a simple uid-based de-duplication; this cache
 * already ignores duplicate uids on {@link #add}).
 * <p>
 * <b>Lifecycle:</b> one instance lives in
 * {@code StockMarketModBackend.ClientInstances.NEWS_CACHE}, created on client join and
 * discarded with the whole {@code ClientInstances} on disconnect — exactly like
 * {@code ClientMarketManager} &amp; co. — so the cache is inherently per-connection and
 * never leaks records across servers/worlds.
 * <p>
 * <b>Access for T-074:</b> from any {@code StockMarketGuiScreen}/{@code StockMarketGuiElement}
 * via {@code BACKEND_INSTANCES.NEWS_CACHE} (may be null while not connected — always
 * null-check). {@link #getRecords()} returns the records newest-first, ready for a
 * newest-first feed. {@link #setChangeListener} lets an open screen refresh live when
 * a new record arrives.
 * <p>
 * Not thread-safe by design: the packet handler (Architectury dispatches S2C handlers
 * on the client main thread) and the GUI both run on the client main thread.
 */
public class ClientNewsCache {

    /** Default maximum number of cached records ({@value}) — a modest "recent news" window. */
    public static final int DEFAULT_MAX_ENTRIES = 50;

    /** Cached records, newest first (index 0 = most recently published). */
    private final ArrayDeque<NewsRecord> records = new ArrayDeque<>();

    /** Maximum number of cached records; the oldest entries are pruned beyond it. */
    private final int maxEntries;

    /**
     * Optional change notification for an open newspaper screen (T-074). Invoked on the
     * client main thread after every accepted {@link #add}, after every changing
     * {@link #seed} and after {@link #clear()}.
     */
    private @Nullable Runnable changeListener;

    /** Creates a cache with the {@link #DEFAULT_MAX_ENTRIES default} cap. */
    public ClientNewsCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /**
     * Creates a cache with an explicit cap (used by tests; production uses the default).
     *
     * @param maxEntries maximum number of cached records; values &lt; 1 are clamped to 1
     */
    public ClientNewsCache(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
    }

    // ── Mutation ─────────────────────────────────────────────────────────

    /**
     * Adds a freshly published record as the newest entry and prunes the oldest entries
     * beyond the cap. Called by the {@code NewsPublishedPacket} client handler.
     * <p>
     * A record whose {@code newsUid} is already cached is ignored (defensive
     * de-duplication — e.g. a publish that raced a history fetch the screen pushed in).
     *
     * @param record the newly published record; null is ignored
     * @return true if the record was added, false if it was null or a duplicate
     */
    public boolean add(@Nullable NewsRecord record) {
        if (record == null) return false;
        if (containsUid(record.getNewsUid())) return false;

        records.addFirst(record);
        while (records.size() > maxEntries) {
            records.removeLast();
        }
        notifyChanged();
        return true;
    }

    /**
     * Seeds the cache with a fetched {@code NewsHistoryRequest} page (join-time
     * catch-up, T-077) so the newspaper opens instantly populated.
     * <p>
     * Unlike {@link #add} (which always prepends, because live publishes arrive in
     * order), seeding must cope with records that are <b>older</b> than live records
     * already cached — a publish can race ahead of the history response. The merge
     * therefore de-duplicates by {@code newsUid}, re-sorts the combined set
     * newest-first by uid (uid order == publish order), prunes to the cap and fires
     * the change listener <b>once</b> if anything changed.
     *
     * @param fetchedRecords one history page (any order; typically newest-first);
     *                       null/empty is a no-op; null elements and already-cached
     *                       uids are skipped
     * @return true if at least one record was added (listener fired), false otherwise
     */
    public boolean seed(@Nullable List<NewsRecord> fetchedRecords) {
        if (fetchedRecords == null || fetchedRecords.isEmpty()) return false;

        boolean changed = false;
        for (NewsRecord record : fetchedRecords) {
            if (record == null || containsUid(record.getNewsUid())) continue;
            records.addLast(record); // position corrected by the uid sort below
            changed = true;
        }
        if (!changed) return false;

        List<NewsRecord> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparingLong(NewsRecord::getNewsUid).reversed());
        records.clear();
        for (NewsRecord record : sorted) {
            records.addLast(record);
            if (records.size() >= maxEntries) break;
        }
        notifyChanged();
        return true;
    }

    /**
     * Removes all cached records. The cache clears itself implicitly on disconnect
     * (the owning {@code ClientInstances} is discarded), so this only exists for
     * explicit resets and tests.
     */
    public void clear() {
        if (records.isEmpty()) return;
        records.clear();
        notifyChanged();
    }

    // ── Queries ──────────────────────────────────────────────────────────

    /**
     * @return an unmodifiable snapshot of the cached records, <b>newest first</b>
     *         (index 0 = most recently published); possibly empty, never null
     */
    public @NotNull List<NewsRecord> getRecords() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    /** @return the {@code newsUid} of the newest cached record, or 0 if the cache is empty */
    public long getNewestUid() {
        NewsRecord newest = records.peekFirst();
        return newest != null ? newest.getNewsUid() : 0;
    }

    /** @return true if a record with the given uid is already cached */
    public boolean containsUid(long newsUid) {
        for (NewsRecord cached : records) {
            if (cached.getNewsUid() == newsUid) return true;
        }
        return false;
    }

    /** @return the number of cached records */
    public int size() {
        return records.size();
    }

    /** @return true if no records are cached */
    public boolean isEmpty() {
        return records.isEmpty();
    }

    /** @return the entry cap of this cache */
    public int getMaxEntries() {
        return maxEntries;
    }

    // ── Change notification ──────────────────────────────────────────────

    /**
     * Sets (or clears with null) the change listener. Intended for the newspaper screen
     * (T-074): set it in the screen's init, clear it when the screen closes. Only one
     * listener is supported — the newest setter wins.
     *
     * @param changeListener invoked on the client main thread after every accepted
     *                       {@link #add}, after every changing {@link #seed} and after
     *                       {@link #clear()}; null to unset
     */
    public void setChangeListener(@Nullable Runnable changeListener) {
        this.changeListener = changeListener;
    }

    /** Fires the change listener if one is set. */
    private void notifyChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }
}
