package net.kroia.stockmarket.news;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.NewsPublishedPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The production {@link NewsPublisher} installed on every NewsPlugin instance on the
 * <b>master</b> server (see {@code ServerPluginManager}, which wires it whenever a
 * NewsPlugin is instantiated — defaults, save-load, migration and runtime creation).
 * <p>
 * Responsibilities per the {@link NewsPublisher} contract:
 * <ol>
 *   <li><b>T-088 (pictures):</b> resolve the published event's {@code picture} file
 *       reference against the config-layer {@link NewsPictureLibrary} and snapshot it —
 *       stamp the content hash into the {@link NewsRecord} and copy the bytes into the
 *       content-addressed {@link NewsPictureStore} — <b>before</b> the history append
 *       and broadcast, so a client fetching the picture right after the packet always
 *       hits the store. Pictures are strictly best-effort: a missing/invalid picture is
 *       warned about <i>once per event id</i> and the record publishes text-only; a
 *       picture problem never blocks or fails a publish.</li>
 *   <li><b>T-072:</b> append the record to the master-side {@link NewsHistory}
 *       (persisted by {@code DataManager.saveNews()/loadNews()}). The history cap is read
 *       from the supplier <b>at publish time</b>, so {@code historyMaxEntries} changes from
 *       a news-config reload apply lazily on the next publish without any rewiring.
 *       After the append (which may prune old records) the picture store is
 *       garbage-collected against the history's remaining references.</li>
 *   <li><b>T-073:</b> broadcast the record to all clients via
 *       {@link NewsPublishedPacket#broadcast} (master → local clients + slave relay).</li>
 * </ol>
 * The info log of the default (pre-T-072) publisher is kept so publishes stay observable
 * in the server log.
 * <p>
 * Called on the server thread from the plugin update loop; must not block (the history
 * append is an in-memory deque operation; the picture snapshot writes at most one
 * ≤128 KiB file once per content hash, persistence happens with the regular world saves).
 */
public class ServerNewsPublisher implements NewsPublisher {

    /** The master-side history store (owned by the DataManager, persisted with the world). */
    private final NewsHistory history;

    /**
     * Provides the current {@code historyMaxEntries} scheduler-config value of the owning
     * plugin's news library. Queried lazily per publish (see class Javadoc).
     */
    private final IntSupplier historyCapSupplier;

    /**
     * The master's published-picture store (world data, owned by the DataManager).
     * Null in picture-less contexts (unit tests) — publishing then always stays text-only.
     */
    private final @Nullable NewsPictureStore pictureStore;

    /**
     * Provides the owning plugin's news library at publish time (definition + picture
     * lookup). Queried lazily so config reloads and library swaps are always respected.
     * Null in picture-less contexts (unit tests).
     */
    private final @Nullable Supplier<NewsEventLibrary> librarySupplier;

    /**
     * The world-event registry for auto-recording fire info and applying
     * {@code records{}} writes at publish time (T-098). Null in contexts
     * without a registry (unit tests) — recording is then skipped.
     */
    private final @Nullable Supplier<NewsWorldRegistry> registrySupplier;

    /**
     * Event ids whose picture reference failed to resolve at publish time — used to
     * warn <b>once per event id</b> instead of spamming the log on every publish of a
     * repeating event. An id is removed again once its picture resolves, so a
     * fixed-then-rebroken picture warns anew.
     */
    private final Set<String> warnedPictureEventIds = new HashSet<>();

    /**
     * Picture-less convenience constructor (unit tests / contexts without a picture
     * store): records always publish text-only, no registry wiring.
     *
     * @param history            the history store to append published records to
     * @param historyCapSupplier supplier of the current history cap
     *                           ({@code library.getSchedulerConfig().getHistoryMaxEntries()})
     */
    public ServerNewsPublisher(@NotNull NewsHistory history, @NotNull IntSupplier historyCapSupplier) {
        this(history, historyCapSupplier, null, null, null);
    }

    /**
     * Full production constructor (T-088, extended T-098 for registry wiring).
     *
     * @param history            the history store to append published records to
     * @param historyCapSupplier supplier of the current history cap
     *                           ({@code library.getSchedulerConfig().getHistoryMaxEntries()})
     * @param pictureStore       the master's published-picture store, or null to
     *                           disable picture snapshotting entirely
     * @param librarySupplier    supplier of the owning plugin's news library (for the
     *                           publish-time definition/picture lookup), or null to
     *                           disable picture snapshotting entirely
     * @param registrySupplier   supplier of the world-event registry for fire-recording
     *                           and records{} writes, or null to skip registry wiring
     */
    public ServerNewsPublisher(@NotNull NewsHistory history, @NotNull IntSupplier historyCapSupplier,
                               @Nullable NewsPictureStore pictureStore,
                               @Nullable Supplier<NewsEventLibrary> librarySupplier,
                               @Nullable Supplier<NewsWorldRegistry> registrySupplier) {
        this.history = history;
        this.historyCapSupplier = historyCapSupplier;
        this.pictureStore = pictureStore;
        this.librarySupplier = librarySupplier;
        this.registrySupplier = registrySupplier;
    }

    @Override
    public void publish(NewsRecord record) {
        if (record == null) return;

        // T-088: snapshot the event's picture (hash into the record, bytes into the
        // store) BEFORE the append/broadcast — a client fetching immediately after
        // the packet must already find the bytes in the store.
        snapshotPicture(record);

        // T-098: auto-record fire info and apply records{} writes to the world registry.
        // Same best-effort pattern as the picture snapshot — registry failures must
        // never block or fail a publish.
        recordToRegistry(record);

        // Read the cap lazily so config reloads take effect; a shrunken cap prunes on
        // this append (NewsHistory prunes lazily, never eagerly).
        history.setMaxEntries(historyCapSupplier.getAsInt());
        history.append(record);

        // GC the picture store against the post-append/post-prune reference set:
        // pictures of just-pruned records are dropped, the fresh snapshot survives
        // because the record referencing it is now in the history.
        if (pictureStore != null) {
            pictureStore.retainOnly(history.referencedPictureHashes());
        }

        StockMarketMod.LOGGER.info(
                "[NewsPlugin] Published news '{}' (uid {}, day {}) affecting {} market(s) — history now holds {} record(s)",
                record.getEventId(), record.getNewsUid(), record.getGameDay(),
                record.getAffectedMarkets().size(), history.size());

        // T-073: broadcast to all clients (master → local clients + slave relay; the
        // slaves re-send to their own players). Kept AFTER the history append so a
        // client fetching history right after the packet always sees the record.
        // No-op when no server is running (e.g. headless test contexts).
        NewsPublishedPacket.broadcast(record);
    }

    /**
     * Resolves the published event's {@code picture} reference through the config-layer
     * picture library and, when found, stamps the content hash into the record and
     * snapshots the bytes into the published store (picture plan §8).
     * <p>
     * Best-effort by contract: any miss (definition gone after a reload, picture file
     * missing/invalid since the last rescan) logs one warning per event id and leaves
     * the record text-only; any unexpected exception is caught and logged — a picture
     * problem must never block or fail a publish.
     *
     * @param record the record about to be appended and broadcast
     */
    /**
     * Auto-records fire info and applies the event's {@code records{}} map to the
     * world-event registry (sequences plan §3, task T-098).
     * <p>
     * Best-effort by contract (same pattern as {@link #snapshotPicture}): any failure
     * is caught and logged — a registry problem must never block or fail a publish.
     * <ul>
     *   <li>{@link NewsWorldRegistry#recordFire}: creates/updates the per-event-id fire
     *       record (count, first/last timestamps, game day).</li>
     *   <li>{@link NewsWorldRegistry#putValue}: applies every key/value pair from the
     *       definition's {@code records{}} map (caps enforced by the registry —
     *       a refused write logs WARN, never blocks publish).</li>
     * </ul>
     *
     * @param record the record being published
     */
    private void recordToRegistry(NewsRecord record) {
        if (registrySupplier == null) return;
        try {
            NewsWorldRegistry registry = registrySupplier.get();
            if (registry == null) return;

            // Auto-record fire info for every published event.
            registry.recordFire(record.getEventId(),
                    record.getTimestampEpochMs(), record.getGameDay());

            // Apply the definition's records{} map (last write wins, caps enforced
            // by the registry itself; a refused write logs WARN, never blocks publish).
            if (librarySupplier != null) {
                NewsEventLibrary library = librarySupplier.get();
                if (library != null) {
                    NewsEventDefinition definition = library.getDefinition(record.getEventId());
                    if (definition != null) {
                        for (Map.Entry<String, String> entry : definition.getRecords().entrySet()) {
                            registry.putValue(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            StockMarketMod.LOGGER.error(
                    "[NewsPlugin] Registry recording failed for news event '{}' — publishing continues",
                    record.getEventId(), e);
        }
    }

    private void snapshotPicture(NewsRecord record) {
        if (pictureStore == null || librarySupplier == null) return;
        try {
            NewsEventLibrary library = librarySupplier.get();
            if (library == null) return;
            NewsEventDefinition definition = library.getDefinition(record.getEventId());
            // Definition gone (deleted between activation and publish) → text-only,
            // silently: there is nothing to resolve a picture from anymore.
            if (definition == null) return;
            String fileName = definition.getPictureFileName();
            if (fileName == null) return; // text-only event — today's default

            NewsPictureLibrary.Entry entry = library.getPictureLibrary().get(fileName);
            if (entry == null) {
                // Referenced file missing/invalid at publish time (deleted or broken
                // since the last reload). Warn once per event id, publish text-only.
                if (warnedPictureEventIds.add(record.getEventId())) {
                    StockMarketMod.LOGGER.warn(
                            "[NewsPlugin] Picture '{}' of news event '{}' is missing or invalid — publishing without a picture "
                                    + "(this is only logged once per event)",
                            fileName, record.getEventId());
                }
                return;
            }
            // Resolvable again → future failures should warn again.
            warnedPictureEventIds.remove(record.getEventId());

            // Stamp the identity into the record only if the bytes made it into the
            // store — a hash no client could ever fetch would be worse than no hash.
            if (pictureStore.put(entry.getSha1(), entry.getBytes())) {
                record.setPictureHash(entry.getSha1());
            }
        } catch (Exception e) {
            StockMarketMod.LOGGER.error(
                    "[NewsPlugin] Picture snapshot failed for news event '{}' — publishing without a picture",
                    record.getEventId(), e);
        }
    }
}
