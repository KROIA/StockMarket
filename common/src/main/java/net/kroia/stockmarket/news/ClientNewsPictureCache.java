package net.kroia.stockmarket.news;

import com.mojang.blaze3d.platform.NativeImage;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.request.NewsPictureRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Client-side cache of published news pictures: fetches picture bytes by content hash
 * (via {@link NewsPictureRequest}), converts them to the newspaper's ink-on-paper
 * grayscale look and manages the resulting GPU textures (T-090, picture plan §3/§5).
 * <p>
 * <b>Lifecycle:</b> one instance lives in
 * {@code StockMarketModBackend.ClientInstances.NEWS_PICTURE_CACHE} — created on client
 * join next to {@code NEWS_CACHE}, and explicitly {@link #releaseAll() released} at the
 * disconnect discard site. Unlike the record cache, discarding the object alone is NOT
 * enough: the registered {@code DynamicTexture}s hold GL resources that must be freed.
 * <p>
 * <b>Per-hash state machine</b> (spec: {@code ABSENT → REQUESTED → LOADED |
 * UNAVAILABLE(retryAt)}; internally REQUESTED is split into QUEUED / IN_FLIGHT):
 * <ul>
 *   <li><b>ABSENT</b> — hash unknown (no map entry). Evicted textures return here, so
 *       they are re-fetchable.</li>
 *   <li><b>QUEUED</b> — waiting in the HIGH or BACKGROUND queue for the next batch.</li>
 *   <li><b>IN_FLIGHT</b> — part of the single currently outstanding request.</li>
 *   <li><b>LOADED</b> — texture registered; tracked by the LRU
 *       (cap {@value #DEFAULT_MAX_LOADED_TEXTURES} textures, eldest evicted+released).</li>
 *   <li><b>UNAVAILABLE(retryAt)</b> — fetch/verify/decode failed. Under
 *       {@value #MAX_FETCH_ATTEMPTS} attempts the hash re-enqueues itself automatically
 *       once {@code retryAt} passed (doubling backoff
 *       {@value #INITIAL_RETRY_DELAY_MILLIS}→{@value #MAX_RETRY_DELAY_MILLIS} ms);
 *       at/after the attempt cap it stays parked with a long
 *       ({@value #GIVE_UP_RETRY_MILLIS} ms) {@code retryAt} and only a
 *       {@link #getTexture} call after that time re-enqueues it — so scrolling past a
 *       dead picture never spams the server.</li>
 * </ul>
 * <p>
 * <b>Two-priority batching (resolved decision §12.4):</b> {@link #getTexture} enqueues
 * at HIGH priority (a widget is literally waiting for this picture), {@link #prefetch}/
 * {@link #prefetchAll} at BACKGROUND (publish notification, history catch-up, feed
 * rebuild). The queue is flushed once per client tick ({@link #clientTick()}) as one
 * request of ≤{@value NewsPictureRequest#MAX_HASHES_PER_REQUEST} hashes, HIGH draining
 * before BACKGROUND — visible entries pop in first, the rest trickles behind them.
 * <p>
 * <b>Single request in flight:</b> the next batch is only sent after the previous
 * response arrived. This keeps all state transitions strictly serial (no interleaved
 * response handling), naturally paces the client against the server's per-player rate
 * limiter (which would answer parallel bursts with empty responses anyway), and costs
 * little: one 8-hash round trip per tick ceiling is far above the rate limit budget.
 * <p>
 * <b>Map key:</b> {@code byte[]} has identity hashCode/equals, so hashes are keyed by
 * their lowercase hex string ({@link NewsPictureLibrary#toHex}). The hex doubles as the
 * stable texture path: {@code stockmarket:news_picture/<sha1hex>}.
 * <p>
 * <b>Testability:</b> all pure logic (state machine, queueing, batching, backoff, LRU)
 * lives in this class against two injected seams — the {@link PictureFetcher} (network)
 * and the {@link TextureSink} (GL side effects). The production implementations
 * ({@link RequestFetcher}, {@link MinecraftTextureSink}) are nested classes, so the in-game
 * test suite can drive the full logic with fakes and without any GL/network context.
 * <p>
 * Not thread-safe by design: every method must be called on the client main thread
 * (same contract as {@link ClientNewsCache}); the {@link PictureFetcher} contract
 * guarantees the response callback is delivered there too.
 */
public class ClientNewsPictureCache {

    // ── Tuning constants (picture plan §5) ───────────────────────────────

    /** Maximum number of LOADED textures kept before LRU eviction ({@value}). */
    public static final int DEFAULT_MAX_LOADED_TEXTURES = 64;

    /** First retry delay after a hash was missing from a response ({@value} ms). */
    public static final long INITIAL_RETRY_DELAY_MILLIS = 5_000L;

    /** Backoff ceiling for the doubling retry delay ({@value} ms). */
    public static final long MAX_RETRY_DELAY_MILLIS = 60_000L;

    /**
     * Failed fetch attempts after which a hash stops retrying automatically
     * ({@value}). It then parks UNAVAILABLE for {@link #GIVE_UP_RETRY_MILLIS} and is
     * only re-enqueued by an explicit {@link #getTexture} call after that time.
     */
    public static final int MAX_FETCH_ATTEMPTS = 5;

    /** Park time of a given-up (or corrupt/mismatched) hash before a retry is allowed ({@value} ms). */
    public static final long GIVE_UP_RETRY_MILLIS = 5 * 60_000L;

    // ── Newsprint palette (plan §3; matches the NewsEntryPanel paper styling) ──

    /** Darkest newsprint tone (ARGB): what a black source pixel becomes. */
    public static final int COLOR_INK = 0xFF1E1B16;

    /** Lightest newsprint tone (ARGB): what a white source pixel becomes. */
    public static final int COLOR_ENTRY_PAPER = 0xFFF3EDDE;

    /** Texture path prefix under the {@code stockmarket} namespace. */
    public static final String TEXTURE_PATH_PREFIX = "news_picture/";

    // ── Injected seams ───────────────────────────────────────────────────

    /**
     * Network seam: sends one hash batch to the server.
     * Production: {@link RequestFetcher}; tests inject a fake.
     */
    @FunctionalInterface
    public interface PictureFetcher {
        /**
         * Sends the batch and later delivers the response.
         *
         * @param hashes     up to {@value NewsPictureRequest#MAX_HASHES_PER_REQUEST}
         *                   20-byte hashes (the list is owned by the caller — copy if kept)
         * @param onResponse MUST be invoked exactly once, <b>on the client main
         *                   thread</b>, with the served pictures — or with an empty
         *                   list on any failure. Hashes absent from the list are
         *                   treated as unknown/over-budget/rate-limited by the cache.
         */
        void fetch(List<byte[]> hashes, Consumer<List<NewsPictureRequest.Picture>> onResponse);
    }

    /**
     * GL seam: turns verified PNG bytes into a registered texture and frees it again.
     * Production: {@link MinecraftTextureSink}; tests inject a fake — the cache logic
     * itself never touches GL, {@code NativeImage} or the {@code TextureManager}.
     */
    public interface TextureSink {
        /**
         * Decodes, converts (newsprint ramp, §3) and registers the picture.
         *
         * @param sha1Hex  the verified content hash as lowercase hex (texture identity)
         * @param pngBytes the raw PNG bytes (SHA-1 already verified by the cache)
         * @return the registered picture, or null when the bytes could not be decoded
         *         (the cache marks the hash UNAVAILABLE)
         */
        @Nullable LoadedPicture register(String sha1Hex, byte[] pngBytes);

        /**
         * Frees a picture previously returned by {@link #register} (LRU eviction or
         * {@link ClientNewsPictureCache#releaseAll()}). Must be idempotent-safe.
         */
        void release(LoadedPicture picture);
    }

    /**
     * One loaded, registered news picture — everything a widget (T-091) needs to draw:
     * the texture location plus the source dimensions for aspect-fit / center-crop UV
     * math.
     *
     * @param location the registered texture, {@code stockmarket:news_picture/<sha1hex>}
     * @param width    source image width in pixels
     * @param height   source image height in pixels
     * @param texture  internal handle used to close the underlying texture on release;
     *                 typed {@link AutoCloseable} (not {@code DynamicTexture}) so the
     *                 pure cache logic stays free of client-class references — widgets
     *                 must ignore it
     */
    public record LoadedPicture(ResourceLocation location, int width, int height,
                                @Nullable AutoCloseable texture) {
    }

    // ── Internal state ───────────────────────────────────────────────────

    /** Internal fetch state of one known hash (ABSENT hashes have no entry at all). */
    private enum State { QUEUED, IN_FLIGHT, LOADED, UNAVAILABLE }

    /** Mutable per-hash bookkeeping, keyed by the hash's hex string. */
    private static final class HashEntry {
        /** The original 20-byte hash (what a request batch actually sends). */
        final byte[] hash;
        State state;
        /** Meaningful while QUEUED: which queue this entry currently belongs to. */
        boolean highPriority;
        /** Failed fetch attempts so far (drives the backoff / give-up logic). */
        int attempts;
        /** Meaningful while UNAVAILABLE: earliest time a retry may happen. */
        long retryAtMillis;
        /** Meaningful while LOADED. */
        @Nullable LoadedPicture picture;

        HashEntry(byte[] hash) {
            this.hash = hash;
        }
    }

    private final PictureFetcher fetcher;
    private final TextureSink sink;
    /** Injectable clock (tests use a controlled value; production the wall clock). */
    private final LongSupplier timeSource;
    /** LRU cap for LOADED textures (test constructor may shrink it). */
    private final int maxLoadedTextures;

    /** All known hashes by hex key; absence = state ABSENT. */
    private final Map<String, HashEntry> entries = new HashMap<>();

    /**
     * Queues hold hex keys, not entries: a promotion (BACKGROUND → HIGH) or a state
     * change simply leaves a stale key behind, which the drain skips by re-checking
     * {@code state == QUEUED && highPriority == <queue>} — no O(n) queue removal.
     */
    private final ArrayDeque<String> highQueue = new ArrayDeque<>();
    private final ArrayDeque<String> backgroundQueue = new ArrayDeque<>();

    /**
     * Hex keys of UNAVAILABLE entries that retry automatically (attempts below the
     * cap). Checked from the head each tick; because the backoff doubles, insertion
     * order is approximately due order — a slightly late retry behind a longer head
     * delay is acceptable and keeps the tick O(1).
     */
    private final ArrayDeque<String> retryQueue = new ArrayDeque<>();

    /**
     * LOADED entries in LRU order (access-ordered LinkedHashMap): {@link #getTexture}
     * touches, overflow evicts the eldest via the sink.
     */
    private final LinkedHashMap<String, HashEntry> loadedLru =
            new LinkedHashMap<>(16, 0.75f, true);

    /** True while one request is outstanding (single in-flight rule). */
    private boolean requestInFlight = false;

    /** Set by {@link #releaseAll()}: the cache is terminal, all calls become no-ops. */
    private boolean released = false;

    /** Single change listener (same contract as {@link ClientNewsCache}). */
    private @Nullable Runnable changeListener;

    // ── Construction ─────────────────────────────────────────────────────

    /**
     * Production constructor.
     *
     * @param fetcher the network seam (typically a {@link RequestFetcher})
     * @param sink    the GL seam (typically a {@link MinecraftTextureSink})
     */
    public ClientNewsPictureCache(@NotNull PictureFetcher fetcher, @NotNull TextureSink sink) {
        this(fetcher, sink, System::currentTimeMillis, DEFAULT_MAX_LOADED_TEXTURES);
    }

    /**
     * Test constructor with an injectable clock and LRU cap.
     *
     * @param fetcher           the network seam (fake in tests)
     * @param sink              the GL seam (fake in tests)
     * @param timeSource        clock used for all backoff/retry decisions
     * @param maxLoadedTextures LRU cap; values &lt; 1 are clamped to 1
     */
    public ClientNewsPictureCache(@NotNull PictureFetcher fetcher, @NotNull TextureSink sink,
                                  @NotNull LongSupplier timeSource, int maxLoadedTextures) {
        this.fetcher = fetcher;
        this.sink = sink;
        this.timeSource = timeSource;
        this.maxLoadedTextures = Math.max(1, maxLoadedTextures);
    }

    // ── Public API (consumed by T-091 widgets) ───────────────────────────

    /**
     * Returns the loaded texture for the given picture hash, or null while it is not
     * (yet) available — <b>and, when null, self-enqueues the hash at HIGH priority</b>
     * so a widget can simply poll this every frame: the first call starts the fetch,
     * the {@link #setChangeListener change listener} fires once the texture landed,
     * and the next call returns it.
     * <p>
     * Details:
     * <ul>
     *   <li>null hash or a length other than {@value NewsPictureLibrary#SHA1_LENGTH}
     *       bytes → null, nothing enqueued (text-only records pass their null hash
     *       straight through).</li>
     *   <li>LOADED → returns the picture and touches its LRU slot.</li>
     *   <li>Queued at BACKGROUND → promoted to HIGH (a widget is now waiting).</li>
     *   <li>UNAVAILABLE → re-enqueued at HIGH only once its {@code retryAt} passed;
     *       before that the call is a cheap null (no server spam while scrolling).</li>
     * </ul>
     *
     * @param hash the record's 20-byte picture content hash (may be null)
     * @return the loaded picture, or null while absent/pending/unavailable
     */
    public @Nullable LoadedPicture getTexture(byte @Nullable [] hash) {
        if (released || !isValidHash(hash)) return null;
        String key = NewsPictureLibrary.toHex(hash);
        HashEntry entry = entries.get(key);
        if (entry == null) {
            enqueue(new HashEntry(hash), key, true);
            return null;
        }
        switch (entry.state) {
            case LOADED -> {
                loadedLru.get(key); // touch LRU order
                return entry.picture;
            }
            case QUEUED -> {
                if (!entry.highPriority) { // promote: a widget is waiting now
                    entry.highPriority = true;
                    highQueue.addLast(key); // stale BACKGROUND key skipped on drain
                }
            }
            case IN_FLIGHT -> { /* answer is already on its way */ }
            case UNAVAILABLE -> {
                if (timeSource.getAsLong() >= entry.retryAtMillis) {
                    enqueue(entry, key, true); // explicit retry after the park time
                }
            }
        }
        return null;
    }

    /**
     * Enqueues the hash at BACKGROUND priority if it is completely unknown (ABSENT).
     * Called by the {@code NewsPublishedPacket} client handler on publish and by the
     * newspaper screen for history/feed prefetching (plan §12.4: visible entries load
     * via {@link #getTexture} at HIGH, everything else trickles in behind them).
     * <p>
     * Dedup: any already-known state (queued, in flight, loaded, unavailable) is left
     * untouched — in particular an UNAVAILABLE hash keeps its backoff schedule instead
     * of being re-poked by every feed rebuild.
     *
     * @param hash a 20-byte picture hash; null/invalid lengths are ignored
     */
    public void prefetch(byte @Nullable [] hash) {
        if (released || !isValidHash(hash)) return;
        String key = NewsPictureLibrary.toHex(hash);
        if (entries.containsKey(key)) return; // dedup against every known state
        enqueue(new HashEntry(hash), key, false);
    }

    /**
     * {@link #prefetch(byte[])} for a whole batch of record hashes (history page load,
     * feed rebuild). Null iterable and null/invalid elements are ignored.
     *
     * @param hashes the picture hashes of e.g. every record in the feed
     */
    public void prefetchAll(@Nullable Iterable<byte[]> hashes) {
        if (hashes == null) return;
        for (byte[] hash : hashes) {
            prefetch(hash);
        }
    }

    /**
     * Sets (or clears with null) the single change listener — same contract as
     * {@link ClientNewsCache#setChangeListener}: screens set it in init, clear it on
     * close; the newest setter wins.
     *
     * @param changeListener invoked <b>on the client main thread</b>, at most once per
     *                       response batch, after at least one new texture landed —
     *                       repaint/rebuild and re-call {@link #getTexture} from there
     */
    public void setChangeListener(@Nullable Runnable changeListener) {
        this.changeListener = changeListener;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Flushes the fetch queue: called once per client tick (wired in
     * {@code StockMarketModBackend.onClientTickEvent}). Promotes due automatic
     * retries, then — unless a request is already in flight — drains up to
     * {@value NewsPictureRequest#MAX_HASHES_PER_REQUEST} hashes (HIGH before
     * BACKGROUND) into one request. Safe to call with nothing to do.
     */
    public void clientTick() {
        if (released) return;
        promoteDueRetries();
        if (requestInFlight) return; // single in-flight rule

        List<HashEntry> batch = drainBatch();
        if (batch.isEmpty()) return;

        List<byte[]> hashes = new ArrayList<>(batch.size());
        for (HashEntry entry : batch) {
            entry.state = State.IN_FLIGHT;
            hashes.add(entry.hash);
        }
        requestInFlight = true;
        try {
            fetcher.fetch(hashes, response -> handleResponse(batch, response));
        } catch (Exception e) {
            // Defensive: a throwing fetcher must not wedge the in-flight flag.
            StockMarketMod.LOGGER.error("[ClientNewsPictureCache] Picture fetch failed to send", e);
            handleResponse(batch, List.of());
        }
    }

    /**
     * Releases every registered texture and turns the cache terminal (all further
     * calls are no-ops, late in-flight responses are dropped). MUST be called at the
     * disconnect discard site: the GPU textures are not freed by garbage collection —
     * without this hook every connect/disconnect cycle would leak them.
     */
    public void releaseAll() {
        if (released) return;
        released = true;
        for (HashEntry entry : loadedLru.values()) {
            releaseQuietly(entry);
        }
        loadedLru.clear();
        entries.clear();
        highQueue.clear();
        backgroundQueue.clear();
        retryQueue.clear();
        changeListener = null;
    }

    // ── Introspection (tests / diagnostics) ──────────────────────────────

    /** @return the number of currently LOADED textures */
    public int loadedCount() {
        return loadedLru.size();
    }

    /** @return true once {@link #releaseAll()} ran (the cache is terminal) */
    public boolean isReleased() {
        return released;
    }

    // ── Queue & response internals ───────────────────────────────────────

    /** @return true for a well-formed 20-byte hash */
    private static boolean isValidHash(byte @Nullable [] hash) {
        return hash != null && hash.length == NewsPictureLibrary.SHA1_LENGTH;
    }

    /** Puts the entry into QUEUED state and the matching priority queue. */
    private void enqueue(HashEntry entry, String key, boolean highPriority) {
        entry.state = State.QUEUED;
        entry.highPriority = highPriority;
        entries.put(key, entry);
        (highPriority ? highQueue : backgroundQueue).addLast(key);
    }

    /** Moves due automatic retries (attempts below the cap) back into the BACKGROUND queue. */
    private void promoteDueRetries() {
        long now = timeSource.getAsLong();
        while (!retryQueue.isEmpty()) {
            String key = retryQueue.peekFirst();
            HashEntry entry = entries.get(key);
            if (entry == null || entry.state != State.UNAVAILABLE) {
                retryQueue.removeFirst(); // stale (re-enqueued via getTexture, or evicted)
                continue;
            }
            if (now < entry.retryAtMillis) return; // head not due → nothing behind it is (≈)
            retryQueue.removeFirst();
            enqueue(entry, key, false);
        }
    }

    /**
     * Drains up to one request's worth of QUEUED entries, HIGH strictly before
     * BACKGROUND; stale queue keys (promoted/answered since insertion) are skipped.
     */
    private List<HashEntry> drainBatch() {
        List<HashEntry> batch = new ArrayList<>(NewsPictureRequest.MAX_HASHES_PER_REQUEST);
        drainQueue(highQueue, true, batch);
        drainQueue(backgroundQueue, false, batch);
        return batch;
    }

    private void drainQueue(ArrayDeque<String> queue, boolean highPriority, List<HashEntry> batch) {
        Iterator<String> iterator = queue.iterator();
        while (batch.size() < NewsPictureRequest.MAX_HASHES_PER_REQUEST && iterator.hasNext()) {
            String key = iterator.next();
            iterator.remove();
            HashEntry entry = entries.get(key);
            // Skip stale keys: entry gone, no longer queued, or moved to the other queue.
            if (entry == null || entry.state != State.QUEUED || entry.highPriority != highPriority)
                continue;
            batch.add(entry);
        }
    }

    /**
     * Response handling for one batch — runs on the client main thread (fetcher
     * contract). Every requested hash is resolved to LOADED (verified + registered),
     * UNAVAILABLE (mismatch/corrupt → long park; missing → backoff) exactly once; the
     * change listener fires at most once per batch.
     */
    private void handleResponse(List<HashEntry> batch, List<NewsPictureRequest.Picture> pictures) {
        requestInFlight = false;
        if (released) return; // disconnected while in flight — textures were never registered

        // Index the response by hex key; entries we did not ask for are ignored.
        Map<String, byte[]> byKey = new HashMap<>(pictures.size());
        for (NewsPictureRequest.Picture picture : pictures) {
            if (picture != null && isValidHash(picture.hash()) && picture.pngBytes() != null) {
                byKey.put(NewsPictureLibrary.toHex(picture.hash()), picture.pngBytes());
            }
        }

        long now = timeSource.getAsLong();
        int loaded = 0;
        int missed = 0;
        for (HashEntry entry : batch) {
            if (entry.state != State.IN_FLIGHT) continue; // defensive
            String key = NewsPictureLibrary.toHex(entry.hash);
            byte[] pngBytes = byKey.get(key);
            if (pngBytes == null) {
                // Unknown / over-budget / rate-limited (indistinguishable, see
                // NewsPictureRequest Javadoc) → backoff re-enqueue or give up.
                onFetchMiss(entry, key, now);
                missed++;
                continue;
            }
            // Defense in depth: the bytes must hash to exactly what we asked for.
            if (!Arrays.equals(NewsPictureLibrary.sha1(pngBytes), entry.hash)) {
                StockMarketMod.LOGGER.warn(
                        "[ClientNewsPictureCache] Served picture bytes do not match hash {} — marked unavailable", key);
                parkLong(entry, now);
                missed++;
                continue;
            }
            LoadedPicture picture = sink.register(key, pngBytes);
            if (picture == null) { // corrupt PNG — decode failed
                StockMarketMod.LOGGER.warn(
                        "[ClientNewsPictureCache] Picture {} could not be decoded — marked unavailable", key);
                parkLong(entry, now);
                missed++;
                continue;
            }
            entry.state = State.LOADED;
            entry.picture = picture;
            loadedLru.put(key, entry);
            loaded++;
        }
        evictOverflow();

        // T-106 diagnostic (downgraded T-112): one DEBUG per response batch.
        // Reveals whether pictures are actually landing on the client — silent
        // placeholder-only rendering is often a chain break (server didn't stamp,
        // rate-limiter refused, decode failed). Kept for future troubleshooting
        // but now DEBUG-level: the pipeline root cause was fixed via the picture-
        // store self-heal path (T-112), covered by tests.
        if (loaded > 0 || missed > 0) {
            StockMarketMod.LOGGER.debug(
                    "[ClientNewsPictureCache] Response batch: {} loaded, {} missed (of {} requested)",
                    loaded, missed, batch.size());
        }

        if (loaded > 0 && changeListener != null) {
            changeListener.run(); // once per response batch, on the client main thread
        }
    }

    /** A requested hash was absent from the response: doubling backoff, then give up. */
    private void onFetchMiss(HashEntry entry, String key, long now) {
        entry.attempts++;
        entry.state = State.UNAVAILABLE;
        entry.picture = null;
        if (entry.attempts >= MAX_FETCH_ATTEMPTS) {
            // Give up: long park, no automatic retry — only getTexture after retryAt.
            entry.retryAtMillis = now + GIVE_UP_RETRY_MILLIS;
        } else {
            // 5 s, 10 s, 20 s, 40 s (capped at 60 s), then the give-up park above.
            long delay = Math.min(
                    INITIAL_RETRY_DELAY_MILLIS << (entry.attempts - 1),
                    MAX_RETRY_DELAY_MILLIS);
            entry.retryAtMillis = now + delay;
            retryQueue.addLast(key); // automatic retry once due
        }
    }

    /** Marks a mismatched/corrupt entry UNAVAILABLE with the long give-up park. */
    private void parkLong(HashEntry entry, long now) {
        entry.state = State.UNAVAILABLE;
        entry.picture = null;
        entry.attempts = MAX_FETCH_ATTEMPTS; // never auto-retried
        entry.retryAtMillis = now + GIVE_UP_RETRY_MILLIS;
    }

    /** Evicts least-recently-used textures beyond the cap; evicted hashes become ABSENT. */
    private void evictOverflow() {
        while (loadedLru.size() > maxLoadedTextures) {
            Iterator<Map.Entry<String, HashEntry>> iterator = loadedLru.entrySet().iterator();
            Map.Entry<String, HashEntry> eldest = iterator.next();
            iterator.remove();
            releaseQuietly(eldest.getValue());
            // Back to ABSENT: a later getTexture/prefetch simply re-fetches it.
            entries.remove(eldest.getKey());
        }
    }

    /** Releases one LOADED entry's texture via the sink, never throwing out of the cache. */
    private void releaseQuietly(HashEntry entry) {
        if (entry.picture == null) return;
        try {
            sink.release(entry.picture);
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("[ClientNewsPictureCache] Failed to release picture texture", e);
        }
        entry.picture = null;
    }

    // ── Newsprint conversion (plan §3) ───────────────────────────────────

    /**
     * Converts one pixel to the newspaper's ink-on-paper look: luminance
     * {@code lum = 0.299·r + 0.587·g + 0.114·b} (integer-rounded), remapped linearly
     * onto the {@link #COLOR_INK} → {@link #COLOR_ENTRY_PAPER} ramp
     * ({@code channel = ink + (paper - ink) · lum / 255}); the source alpha is
     * preserved unchanged.
     * <p>
     * <b>Channel order is ABGR</b>, as used by {@code NativeImage} pixel access for
     * RGBA-format images (verified against the 1.21.1 mappings: {@code getPixelRGBA}/
     * {@code applyToAllPixels} operate on {@code FastColor.ABGR32} packed ints — alpha
     * in bits 24–31, blue 16–23, green 8–15, <b>red in bits 0–7</b>). Public and pure
     * so the test suite can verify the math on known pixels without any GL context.
     *
     * @param abgr the source pixel, ABGR-packed
     * @return the newsprint pixel, ABGR-packed, alpha preserved
     */
    public static int newsprintPixelAbgr(int abgr) {
        int alpha = (abgr >>> 24) & 0xFF;
        int blue = (abgr >>> 16) & 0xFF;
        int green = (abgr >>> 8) & 0xFF;
        int red = abgr & 0xFF;
        // Integer-rounded Rec.601 luminance (0.299/0.587/0.114 scaled by 1000).
        int lum = (red * 299 + green * 587 + blue * 114 + 500) / 1000;
        int outRed = lerpChannel((COLOR_INK >>> 16) & 0xFF, (COLOR_ENTRY_PAPER >>> 16) & 0xFF, lum);
        int outGreen = lerpChannel((COLOR_INK >>> 8) & 0xFF, (COLOR_ENTRY_PAPER >>> 8) & 0xFF, lum);
        int outBlue = lerpChannel(COLOR_INK & 0xFF, COLOR_ENTRY_PAPER & 0xFF, lum);
        return (alpha << 24) | (outBlue << 16) | (outGreen << 8) | outRed;
    }

    /** Linear ink→paper interpolation of one channel: {@code ink + (paper-ink)·lum/255}. */
    private static int lerpChannel(int ink, int paper, int lum) {
        return ink + (paper - ink) * lum / 255;
    }

    // ── Production seam implementations (client classes confined here) ──
    //
    // These nested classes are separate class files: the outer cache class carries no
    // references to net.minecraft.client.* in its own constant pool, so the pure logic
    // (and its BOTH-sided test suite) loads fine on a dedicated server where the
    // client classes do not exist. Only actually joining a client instantiates these.

    /**
     * Production {@link PictureFetcher}: sends the batch through the registered
     * {@link NewsPictureRequest} (auto slave→master routed) and hops the response onto
     * the client main thread via {@code Minecraft.execute} — satisfying the fetcher
     * contract. Any error/timeout degrades to an empty response (= backoff for every
     * requested hash).
     */
    public static final class RequestFetcher implements PictureFetcher {
        private final StockMarketNetworking networking;

        /** @param networking the per-connection networking manager holding {@code NEWS_PICTURE_REQUEST} */
        public RequestFetcher(@NotNull StockMarketNetworking networking) {
            this.networking = networking;
        }

        @Override
        public void fetch(List<byte[]> hashes, Consumer<List<NewsPictureRequest.Picture>> onResponse) {
            networking.NEWS_PICTURE_REQUEST
                    .sendRequestToServer(new NewsPictureRequest.InputData(hashes))
                    .whenComplete((response, error) -> Minecraft.getInstance().execute(() ->
                            onResponse.accept(error != null || response == null
                                    ? List.of()
                                    : response.pictures())));
        }
    }

    /**
     * Production {@link TextureSink}: decodes the PNG with {@code NativeImage},
     * applies the newsprint conversion in place and registers a {@code DynamicTexture}
     * under {@code stockmarket:news_picture/<sha1hex>} with the {@code TextureManager}.
     * Runs on the client main thread (= render thread), so the GL upload inside the
     * {@code DynamicTexture} constructor is safe.
     */
    public static final class MinecraftTextureSink implements TextureSink {

        @Override
        public @Nullable LoadedPicture register(String sha1Hex, byte[] pngBytes) {
            NativeImage image;
            try {
                // Stream overload on purpose: NativeImage.read(byte[]) copies through
                // LWJGL's MemoryStack (64 KiB default) and would overflow for pictures
                // near the 128 KiB file cap; the stream path uses heap-independent
                // native allocation. read() always produces RGBA format, so the
                // ABGR pixel conversion below is valid for every PNG color type.
                image = NativeImage.read(new ByteArrayInputStream(pngBytes));
            } catch (Exception e) {
                StockMarketMod.LOGGER.warn("[ClientNewsPictureCache] Failed to decode picture {}: {}",
                        sha1Hex, e.toString());
                return null;
            }
            try {
                image.applyToAllPixels(ClientNewsPictureCache::newsprintPixelAbgr);
                ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                        StockMarketMod.MOD_ID, TEXTURE_PATH_PREFIX + sha1Hex);
                DynamicTexture texture = new DynamicTexture(image);
                Minecraft.getInstance().getTextureManager().register(location, texture);
                return new LoadedPicture(location, image.getWidth(), image.getHeight(), texture);
            } catch (Exception e) {
                image.close(); // registration failed → free the native pixel buffer
                StockMarketMod.LOGGER.warn("[ClientNewsPictureCache] Failed to register picture {}: {}",
                        sha1Hex, e.toString());
                return null;
            }
        }

        @Override
        public void release(LoadedPicture picture) {
            // TextureManager.release() already close()s the removed texture (verified
            // in the 1.21.1 mappings: release → safeClose → AbstractTexture.close),
            // freeing the GL id and the NativeImage. The explicit close below is a
            // belt-and-braces no-op then (DynamicTexture.close guards on pixels!=null)
            // but also covers a texture that was never registered/already replaced.
            Minecraft.getInstance().getTextureManager().release(picture.location());
            try {
                if (picture.texture() != null) {
                    picture.texture().close();
                }
            } catch (Exception ignored) {
                // close() is best-effort — the manager release above did the real work.
            }
        }
    }
}
