package net.kroia.stockmarket.networking.request;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.news.NewsPictureLibrary;
import net.kroia.stockmarket.news.NewsPictureStore;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hash-batched fetch of published news pictures for the client picture cache
 * (news picture plan §4.2, task T-089).
 * <p>
 * The client sends up to {@value #MAX_HASHES_PER_REQUEST} 20-byte SHA-1 content hashes
 * (taken from {@code NewsRecord.getPictureHash()}); the master answers with the raw PNG
 * bytes of every hash it knows, straight from the {@link NewsPictureStore} (the
 * publish-time snapshot layer — never the admin's config folder, so there is no
 * path-traversal surface and history entries keep their original picture forever).
 * <p>
 * <b>Not admin-gated:</b> any player may fetch pictures of published news — same policy
 * as {@link NewsHistoryRequest}. Slaves hold no news state and act as <b>pure
 * pass-throughs</b> (plan §4.4): the request is auto-routed slave→master via
 * {@link #needsRoutingToMaster()}, no slave-side relay cache (revisit only if the
 * master bandwidth is ever measured to be a problem).
 * <p>
 * <b>Response byte budget:</b> found pictures are accumulated until
 * {@value #RESPONSE_BYTE_BUDGET} raw picture bytes — safely under the ~1&nbsp;MiB
 * custom-payload cap. An entry that does not fit is simply <b>omitted</b> (later,
 * smaller entries may still be included); the client re-requests omitted hashes in a
 * later batch. Unknown hashes are omitted too (the client marks them UNAVAILABLE with a
 * retry backoff). With the {@value NewsPictureLibrary#MAX_FILE_BYTES}-byte file cap at
 * least five pictures always fit, so no chunking is needed.
 * <p>
 * <b>Per-player rate limiting:</b> a sliding {@value #RATE_WINDOW_MILLIS}&nbsp;ms window
 * per player UUID caps both the number of requested hashes
 * ({@value #MAX_HASHES_PER_WINDOW}) and the served bytes
 * ({@value #MAX_BYTES_PER_WINDOW}). An over-limit request gets the EMPTY response and
 * ONE warn log per window (not per request); the client backs off and retries after the
 * window slid. See the constant Javadocs for the sizing rationale.
 * <p>
 * <b>Client entry point (T-090 codes against this):</b> exactly like
 * {@link NewsHistoryRequest}, use the registered instance on the networking manager:
 * <pre>{@code
 * BACKEND_INSTANCES.NETWORKING.NEWS_PICTURE_REQUEST
 *         .sendRequestToServer(new NewsPictureRequest.InputData(hashBatch))
 *         .thenAccept(response -> onPicturesReceived(response.pictures()));
 * }</pre>
 * The returned future completes on the client. Requested hashes that are missing from
 * the response were either unknown, over-budget, or rate-limited — the client cannot
 * distinguish these and should simply re-enqueue them with a backoff.
 */
public class NewsPictureRequest extends StockMarketGenericRequest<NewsPictureRequest.InputData, NewsPictureRequest.OutputData> {

    // ── Batch & budget constants (plan §4.2) ─────────────────────────────

    /**
     * Maximum number of hashes per request. The client cache flushes one batch per
     * client tick, so 8 hashes/request keeps a 20-entry newspaper at ~3 requests while
     * a full 500-entry history catch-up still trickles through within the rate window
     * (see {@link #MAX_HASHES_PER_WINDOW}). Also the hard decode-side cap: a claimed
     * list length beyond this is never allocated.
     */
    public static final int MAX_HASHES_PER_REQUEST = 8;

    /**
     * Maximum raw picture bytes accumulated into one response (640 KiB) — safely below
     * the ~1 MiB S2C custom-payload cap, with headroom for the hash/length framing.
     * Since every stored picture is at most {@value NewsPictureLibrary#MAX_FILE_BYTES}
     * bytes, at least five pictures always fit, so a full batch never needs chunking.
     */
    public static final int RESPONSE_BYTE_BUDGET = 640 * 1024;

    /**
     * Decode-side cap for one picture's PNG bytes: the library file cap plus slack for
     * rounding — a malicious/corrupt buffer can never make the decoder allocate a huge
     * array. The server also never serves store entries larger than the file cap
     * (defensive, in case a store file was replaced on disk by hand).
     */
    public static final int MAX_PNG_DECODE_BYTES = NewsPictureLibrary.MAX_FILE_BYTES + 4 * 1024;

    /**
     * Decode-side cap for one hash's byte array. Well-formed clients always send exactly
     * {@value NewsPictureLibrary#SHA1_LENGTH} bytes; slightly malformed lengths still
     * decode (and are then skipped by the handler), anything absurd fails the decode.
     */
    public static final int MAX_HASH_DECODE_BYTES = 64;

    // ── Rate-limit constants (plan §4.2) ─────────────────────────────────

    /** Length of the per-player sliding rate window in milliseconds (60 s). */
    public static final long RATE_WINDOW_MILLIS = 60_000L;

    /**
     * Maximum hashes one player may request per sliding window. Sizing: the client
     * batches {@value #MAX_HASHES_PER_REQUEST} hashes/request, so 60 hashes/min lets a
     * worst-case 500-entry history catch-up (500 <i>distinct</i> pictures — hashes are
     * content-deduplicated client-side, so real feeds need far fewer) trickle through in
     * roughly 8 minutes, and a typical feed (a few dozen distinct pictures) within a
     * minute — while a misbehaving client cannot hammer the store's disk reads.
     */
    public static final int MAX_HASHES_PER_WINDOW = 60;

    /**
     * Maximum picture bytes served to one player per sliding window (4 MiB). Together
     * with {@link #MAX_HASHES_PER_WINDOW} this bounds a player's bandwidth cost:
     * 60 average-sized pictures stay far below it; only maximal
     * {@value NewsPictureLibrary#MAX_FILE_BYTES}-byte pictures throttle earlier.
     * The window may overshoot by at most one response budget (the check runs before
     * serving), which is the usual sliding-window behavior and keeps the check cheap.
     */
    public static final long MAX_BYTES_PER_WINDOW = 4L * 1024 * 1024;

    /**
     * Lazy map pruning threshold of the {@link RateLimiter}: once more players than
     * this have window state, fully-expired entries are dropped on the next request.
     */
    static final int LIMITER_PRUNE_THRESHOLD = 64;

    /**
     * The live per-player limiter state of the singleton request handler. Requests run
     * on the (master) server main thread only, so plain unsynchronized state suffices.
     * Test suites construct their own {@link RateLimiter} instances instead.
     */
    private static final RateLimiter RATE_LIMITER = new RateLimiter();

    // ── Wire records ─────────────────────────────────────────────────────

    /**
     * The client's hash batch.
     *
     * @param hashes up to {@value #MAX_HASHES_PER_REQUEST} picture content hashes, each
     *               exactly {@value NewsPictureLibrary#SHA1_LENGTH} bytes (malformed
     *               lengths are skipped server-side, never answered)
     */
    public record InputData(List<byte[]> hashes) {
        /**
         * Hand-written codec (instead of a plain list codec) so the decoder can hard-cap
         * the element count at {@value #MAX_HASHES_PER_REQUEST}: a malicious buffer
         * claiming a huge list length never causes a matching allocation. Per-element
         * reads are capped at {@value #MAX_HASH_DECODE_BYTES} bytes for the same reason.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.of(
                (buf, input) -> {
                    // Encode side enforces the batch cap too — a well-behaved client
                    // never sends more, but the wire format guarantees it either way.
                    int count = Math.min(input.hashes().size(), MAX_HASHES_PER_REQUEST);
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        buf.writeByteArray(input.hashes().get(i));
                    }
                },
                buf -> {
                    // Defensive: never trust the claimed count for the allocation.
                    int claimed = buf.readVarInt();
                    int count = Math.min(Math.max(claimed, 0), MAX_HASHES_PER_REQUEST);
                    List<byte[]> hashes = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        hashes.add(buf.readByteArray(MAX_HASH_DECODE_BYTES));
                    }
                    return new InputData(hashes);
                });
    }

    /**
     * One served picture: the content hash it was requested under plus the raw PNG file
     * bytes as snapshotted at publish time. (Record equality is reference-based for the
     * arrays — wire data is never compared, so that is fine.)
     *
     * @param hash     the 20-byte SHA-1 the client asked for (echoed back so the client
     *                 can match responses without re-hashing; it still SHA-1-verifies
     *                 the bytes as defense in depth)
     * @param pngBytes the raw PNG file bytes (≤ {@value NewsPictureLibrary#MAX_FILE_BYTES})
     */
    public record Picture(byte[] hash, byte[] pngBytes) {
        /** Codec with decode-side allocation caps (see {@link InputData#STREAM_CODEC}). */
        public static final StreamCodec<RegistryFriendlyByteBuf, Picture> STREAM_CODEC = StreamCodec.of(
                (buf, picture) -> {
                    buf.writeByteArray(picture.hash());
                    buf.writeByteArray(picture.pngBytes());
                },
                buf -> new Picture(
                        buf.readByteArray(MAX_HASH_DECODE_BYTES),
                        buf.readByteArray(MAX_PNG_DECODE_BYTES)));
    }

    /**
     * The served batch: only hashes that are known to the store <b>and</b> fit the
     * {@value #RESPONSE_BYTE_BUDGET}-byte budget appear; everything else is omitted
     * (empty on rate-limit denial or error). Order follows the request order of the
     * included hashes.
     */
    public record OutputData(List<Picture> pictures) {
        /** Hand-written for the same decode-side count cap as {@link InputData}. */
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.of(
                (buf, output) -> {
                    int count = Math.min(output.pictures().size(), MAX_HASHES_PER_REQUEST);
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        Picture.STREAM_CODEC.encode(buf, output.pictures().get(i));
                    }
                },
                buf -> {
                    int claimed = buf.readVarInt();
                    int count = Math.min(Math.max(claimed, 0), MAX_HASHES_PER_REQUEST);
                    List<Picture> pictures = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        pictures.add(Picture.STREAM_CODEC.decode(buf));
                    }
                    return new OutputData(pictures);
                });
    }

    // ── Request plumbing ─────────────────────────────────────────────────

    @Override
    public String getRequestTypeID() {
        return NewsPictureRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(List.of());
    }

    /**
     * Serves the batch from the master's {@link NewsPictureStore}
     * ({@code DataManager.getNewsPictureStore()}). Runs on the master server main
     * thread; the store lookup is a small file read per hash, so it completes
     * synchronously. All budget/limiter logic lives in the static, in-game-testable
     * core {@link #servePictures}.
     */
    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        if (playerSender == null || (needsRoutingToMaster() && !MultiServerUtils.canInteractWithStockMarket(playerSender)))
            return CompletableFuture.completedFuture(getDefaultResponse());

        // The DataManager (and with it the picture store) only exists on the master.
        if (BACKEND_INSTANCES == null || BACKEND_INSTANCES.DATA_MANAGER == null)
            return CompletableFuture.completedFuture(getDefaultResponse());

        OutputData output = servePictures(
                BACKEND_INSTANCES.DATA_MANAGER.getNewsPictureStore(),
                RATE_LIMITER, playerSender, input.hashes(), System.currentTimeMillis());
        return CompletableFuture.completedFuture(output);
    }

    // ── Testable core ────────────────────────────────────────────────────

    /**
     * The complete server-side answer logic — static and side-effect-free apart from
     * the passed-in limiter, so the in-game test suite can drive it against a temp-dir
     * store and a private limiter with fake clock values, without any networking
     * (same pattern as {@code NewsAdminRequest.performStopEvent}).
     * <p>
     * Steps:
     * <ol>
     *   <li>Keep only well-formed hashes ({@value NewsPictureLibrary#SHA1_LENGTH}
     *       bytes), capped at {@value #MAX_HASHES_PER_REQUEST} — malformed entries are
     *       silently skipped (plan §9). An all-malformed/empty batch answers empty
     *       without touching the limiter.</li>
     *   <li>Ask the limiter; a denied request answers empty (the limiter warn-logs
     *       once per window).</li>
     *   <li>Serve each hash from the store; unknown hashes and (defensively) store
     *       entries larger than the {@value NewsPictureLibrary#MAX_FILE_BYTES}-byte
     *       file cap are omitted. An entry that would push the accumulated picture
     *       bytes over {@value #RESPONSE_BYTE_BUDGET} is omitted; later smaller
     *       entries may still be included.</li>
     *   <li>Record the request's hash count and served bytes in the limiter window.</li>
     * </ol>
     *
     * @param store           the published-picture store to serve from
     * @param limiter         the per-player rate-limiter state to consult and update
     * @param player          the requesting player's UUID (the rate-limit key)
     * @param requestedHashes the raw requested hashes (may contain malformed lengths)
     * @param nowMillis       the current wall-clock time (injectable for tests)
     * @return the response batch (never null; empty when denied or nothing was found)
     */
    public static @NotNull OutputData servePictures(@NotNull NewsPictureStore store,
                                                    @NotNull RateLimiter limiter,
                                                    @NotNull UUID player,
                                                    @NotNull List<byte[]> requestedHashes,
                                                    long nowMillis) {
        // 1. Malformed-length hashes are skipped, the batch cap is re-enforced here
        //    (the core is also reachable without the decode-side cap, e.g. from tests).
        List<byte[]> validHashes = new ArrayList<>(Math.min(requestedHashes.size(), MAX_HASHES_PER_REQUEST));
        for (byte[] hash : requestedHashes) {
            if (validHashes.size() >= MAX_HASHES_PER_REQUEST) break;
            if (hash != null && hash.length == NewsPictureLibrary.SHA1_LENGTH) {
                validHashes.add(hash);
            }
        }
        if (validHashes.isEmpty()) {
            return new OutputData(List.of());
        }

        // 2. Rate limit — denial answers empty; the limiter warn-logs once per window.
        if (!limiter.checkAllowed(player, validHashes.size(), nowMillis)) {
            return new OutputData(List.of());
        }

        // 3. Serve until the byte budget is reached; non-fitting/unknown hashes omitted.
        List<Picture> pictures = new ArrayList<>(validHashes.size());
        long servedBytes = 0;
        for (byte[] hash : validHashes) {
            byte[] pngBytes = store.get(hash);
            if (pngBytes == null) continue; // unknown → omitted, client backs off
            if (pngBytes.length > NewsPictureLibrary.MAX_FILE_BYTES) {
                // Defensive: someone replaced a store file on disk with an oversized
                // one — the client-side decode cap would reject it anyway.
                StockMarketMod.LOGGER.warn(
                        "[NewsPictureRequest] Stored picture {} exceeds the {} byte cap — not served",
                        NewsPictureLibrary.toHex(hash), NewsPictureLibrary.MAX_FILE_BYTES);
                continue;
            }
            if (servedBytes + pngBytes.length > RESPONSE_BYTE_BUDGET) continue; // over budget → omitted
            pictures.add(new Picture(hash, pngBytes));
            servedBytes += pngBytes.length;
        }

        // 4. Account the request against the player's sliding window.
        limiter.record(player, validHashes.size(), servedBytes, nowMillis);
        return new OutputData(pictures);
    }

    // ── Rate limiter ─────────────────────────────────────────────────────

    /**
     * Per-player sliding-window rate limiter for picture fetches (plan §4.2).
     * <p>
     * For every player it keeps the grants of the last {@value #RATE_WINDOW_MILLIS} ms
     * (hash count + served bytes each). A request is denied when the window's hash
     * total would exceed {@value #MAX_HASHES_PER_WINDOW} or its byte total has already
     * reached {@value #MAX_BYTES_PER_WINDOW} (so the byte cap can overshoot by at most
     * one response budget — usual sliding-window behavior). Denials emit ONE warn log
     * per player and window, not one per request.
     * <p>
     * State is pruned lazily: the requesting player's expired grants on every check,
     * and fully-expired players wholesale once more than
     * {@value #LIMITER_PRUNE_THRESHOLD} players have state.
     * <p>
     * Not thread-safe — like the request handlers themselves it must only be used from
     * the server main thread.
     */
    public static final class RateLimiter {

        /** One granted request: its time, its hash count and the bytes it served. */
        private record Grant(long timeMillis, int hashes, long bytes) {}

        /** The sliding-window state of one player. */
        private static final class Window {
            /** Grants inside the window, oldest first (pruned on every check). */
            final ArrayDeque<Grant> grants = new ArrayDeque<>();
            /** Time of the last denial warn log (throttles it to once per window). */
            long lastWarnMillis = Long.MIN_VALUE;
            /** Total denial warns ever emitted for this player (test observability). */
            int warningCount = 0;
        }

        /** Window state per player UUID; lazily pruned. */
        private final Map<UUID, Window> windows = new HashMap<>();

        /**
         * Checks whether a request for {@code hashCount} hashes is currently allowed
         * for the player. Denials warn-log at most once per {@value #RATE_WINDOW_MILLIS}
         * ms per player. Does <b>not</b> account the request — call
         * {@link #record} after actually serving it.
         *
         * @param player    the requesting player
         * @param hashCount the number of (valid) hashes in the request
         * @param nowMillis the current time
         * @return true when the request may be served
         */
        boolean checkAllowed(UUID player, int hashCount, long nowMillis) {
            pruneMapIfLarge(nowMillis);
            Window window = windows.get(player);
            if (window == null) return true; // no recent grants → trivially allowed
            prune(window, nowMillis);

            int hashTotal = 0;
            long byteTotal = 0;
            for (Grant grant : window.grants) {
                hashTotal += grant.hashes();
                byteTotal += grant.bytes();
            }
            boolean denied = hashTotal + hashCount > MAX_HASHES_PER_WINDOW
                    || byteTotal >= MAX_BYTES_PER_WINDOW;
            if (denied && nowMillis - window.lastWarnMillis >= RATE_WINDOW_MILLIS) {
                // One warn per window, not per request — a catching-up client may
                // legitimately bump into the limit and back off.
                window.lastWarnMillis = nowMillis;
                window.warningCount++;
                StockMarketMod.LOGGER.warn(
                        "[NewsPictureRequest] Player {} exceeded the news picture rate limit "
                                + "({} hashes / {} bytes per {} s) — answering empty until the window slides",
                        player, MAX_HASHES_PER_WINDOW, MAX_BYTES_PER_WINDOW, RATE_WINDOW_MILLIS / 1000);
            }
            return !denied;
        }

        /**
         * Accounts one served request against the player's window.
         *
         * @param player      the requesting player
         * @param hashCount   the number of (valid) hashes that were requested
         * @param servedBytes the raw picture bytes actually served
         * @param nowMillis   the current time
         */
        void record(UUID player, int hashCount, long servedBytes, long nowMillis) {
            Window window = windows.computeIfAbsent(player, uuid -> new Window());
            window.grants.addLast(new Grant(nowMillis, hashCount, servedBytes));
        }

        /**
         * @param player a player UUID
         * @return how many rate-limit warn logs were ever emitted for the player
         *         (0 if none) — exposed for the in-game test suite
         */
        public int warningCount(UUID player) {
            Window window = windows.get(player);
            return window == null ? 0 : window.warningCount;
        }

        /** Drops the window's grants that slid out of the rate window. */
        private static void prune(Window window, long nowMillis) {
            while (!window.grants.isEmpty()
                    && nowMillis - window.grants.peekFirst().timeMillis() >= RATE_WINDOW_MILLIS) {
                window.grants.removeFirst();
            }
        }

        /**
         * Lazy global cleanup: once more than {@value #LIMITER_PRUNE_THRESHOLD} players
         * have state, players whose grants all expired are dropped entirely (their
         * warn-throttle state goes with them, which at worst re-allows one extra warn).
         */
        private void pruneMapIfLarge(long nowMillis) {
            if (windows.size() <= LIMITER_PRUNE_THRESHOLD) return;
            Iterator<Map.Entry<UUID, Window>> iterator = windows.entrySet().iterator();
            while (iterator.hasNext()) {
                Window window = iterator.next().getValue();
                prune(window, nowMillis);
                if (window.grants.isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }

    // ── Codec plumbing ───────────────────────────────────────────────────

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
