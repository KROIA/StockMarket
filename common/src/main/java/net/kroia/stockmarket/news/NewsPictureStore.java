package net.kroia.stockmarket.news;

import net.kroia.stockmarket.StockMarketMod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Published-picture store for the news event system (picture plan §2, layer 2) —
 * the <b>content-addressed, immutable, master-only</b> counterpart of the config-layer
 * {@link NewsPictureLibrary}.
 * <p>
 * While the library is the admin's <i>authoring view</i> (rescanned from
 * {@code config/StockMarket/news/pictures/} on every reload), this store holds the
 * <i>publish-time snapshots</i>: when an event with a picture is published, the
 * {@link ServerNewsPublisher} copies the picture bytes here under their SHA-1
 * ({@code world/data/StockMarket/News/pictures/<sha1hex>.png}) and stamps the hash into
 * the {@link NewsRecord}. All client/slave picture fetches (T-089) are served
 * <b>exclusively</b> from this store — a history record keeps rendering its original
 * picture forever, even after the admin swapped or deleted the config file.
 * <p>
 * <b>Garbage collection:</b> entries are content-addressed and never overwritten;
 * {@link #retainOnly(Collection)} deletes every stored file whose hash is no longer
 * referenced by the history. It is called after every publish (the history append may
 * prune old records) and after the history is loaded (see {@code DataManager}).
 * Foreign files are tolerated: only {@code *.png} files whose name is exactly 40
 * lowercase hex characters are ever touched.
 * <p>
 * <b>Failure tolerance:</b> all IO problems are logged and swallowed — a broken picture
 * store must never break publishing, saving or loading news. Before the directory is
 * assigned via {@link #setDirectory(Path)} (done by the {@code DataManager} when the
 * world save path is known), every operation is a safe logged no-op.
 * <p>
 * Not thread-safe: like the history and the libraries, call it from the server thread
 * only (publishes and save/load both run there).
 */
public final class NewsPictureStore {

    /** File extension of every stored picture (raw PNG bytes, as snapshotted). */
    public static final String FILE_EXTENSION = ".png";

    /** Length of the hex file-name stem (SHA-1 → 40 hex characters). */
    private static final int HEX_NAME_LENGTH = NewsPictureLibrary.SHA1_LENGTH * 2;

    // ── State ────────────────────────────────────────────────────────────

    /**
     * The store directory ({@code world/data/StockMarket/News/pictures}); null until
     * the DataManager resolved the world save path. Created lazily on the first put.
     */
    private @Nullable Path directory;

    // ── Directory wiring ─────────────────────────────────────────────────

    /**
     * Assigns the store directory. Called by the {@code DataManager} once the world
     * save path is known (news load/save); the directory itself is created lazily by
     * {@link #put(byte[], byte[])}.
     *
     * @param directory the pictures directory inside the news world-data folder,
     *                  or null to detach the store (all operations become no-ops)
     */
    public void setDirectory(@Nullable Path directory) {
        this.directory = directory;
    }

    /** @return the current store directory, or null while not wired to a world save */
    public @Nullable Path getDirectory() {
        return directory;
    }

    // ── Write path ───────────────────────────────────────────────────────

    /**
     * Snapshots one published picture into the store — idempotent and content-addressed:
     * if {@code <sha1hex>.png} already exists, nothing is written (entries are immutable
     * by construction, equal hash ⇒ equal bytes).
     * <p>
     * Defense in depth: the hash must be {@value NewsPictureLibrary#SHA1_LENGTH} bytes
     * and must actually be the SHA-1 of {@code bytes} — a mismatch is logged and the
     * put is skipped, so the store can never serve bytes under a wrong identity.
     * Never throws; IO failures are logged and reported via the return value.
     *
     * @param hash  the 20-byte SHA-1 of {@code bytes} (the content identity)
     * @param bytes the raw PNG file bytes to store
     * @return true if the picture is present in the store after this call
     *         (freshly written or already existing), false on rejection/failure
     */
    public boolean put(byte @Nullable [] hash, byte @Nullable [] bytes) {
        Path dir = directory;
        if (dir == null) {
            StockMarketMod.LOGGER.warn(
                    "[NewsPictureStore] put() before the store directory was set — picture dropped");
            return false;
        }
        if (!isValidHash(hash) || bytes == null) {
            StockMarketMod.LOGGER.warn("[NewsPictureStore] put() rejected: invalid hash/bytes");
            return false;
        }
        // Verify the content identity so a caller bug can never poison the store.
        if (!Arrays.equals(hash, NewsPictureLibrary.sha1(bytes))) {
            StockMarketMod.LOGGER.warn(
                    "[NewsPictureStore] put() rejected: hash {} does not match the picture bytes — skipped",
                    NewsPictureLibrary.toHex(hash));
            return false;
        }
        Path target = dir.resolve(NewsPictureLibrary.toHex(hash) + FILE_EXTENSION);
        try {
            if (Files.exists(target)) {
                return true; // idempotent: content-addressed, already snapshotted
            }
            Files.createDirectories(dir);
            // Write to a temp sibling and move into place so a crash mid-write can
            // never leave a truncated <sha1hex>.png behind.
            Path temp = Files.createTempFile(dir, "put_", ".tmp");
            try {
                Files.write(temp, bytes);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;
        } catch (Exception e) {
            // Failure-tolerant: a broken store must never break a publish.
            StockMarketMod.LOGGER.error("[NewsPictureStore] Failed to store picture {}",
                    target.getFileName(), e);
            return false;
        }
    }

    // ── Read path ────────────────────────────────────────────────────────

    /**
     * Reads one stored picture by its content hash (the T-089 request handler's
     * lookup). Never throws.
     *
     * @param hash the 20-byte SHA-1 identifying the picture
     * @return the raw PNG bytes, or null if the hash is invalid, the store is not
     *         wired, the picture is unknown, or reading failed (logged)
     */
    public byte @Nullable [] get(byte @Nullable [] hash) {
        Path dir = directory;
        if (dir == null || !isValidHash(hash)) return null;
        Path file = dir.resolve(NewsPictureLibrary.toHex(hash) + FILE_EXTENSION);
        try {
            if (!Files.isRegularFile(file)) return null;
            return Files.readAllBytes(file);
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("[NewsPictureStore] Failed to read picture {}",
                    file.getFileName(), e);
            return null;
        }
    }

    // ── Garbage collection ───────────────────────────────────────────────

    /**
     * Deletes every stored picture whose hash is not in the given referenced set —
     * the store GC, driven by {@code NewsHistory.referencedPictureHashes()} after
     * every publish (append + prune) and after a history load.
     * <p>
     * Tolerant of foreign files: only regular {@code *.png} files whose name stem is
     * exactly 40 lowercase hex characters (i.e. files this store wrote itself) are
     * candidates — anything an admin dropped in here by hand is left alone.
     * Never throws; per-file delete failures are logged and skipped.
     *
     * @param referencedHashes the hashes that must survive (20-byte SHA-1 each;
     *                         null entries and malformed lengths are ignored)
     */
    public void retainOnly(@NotNull Collection<byte[]> referencedHashes) {
        Path dir = directory;
        if (dir == null || !Files.isDirectory(dir)) return;

        // Hex-encode the referenced hashes once for O(1) name lookups.
        Set<String> referencedHex = new HashSet<>();
        for (byte[] hash : referencedHashes) {
            if (isValidHash(hash)) {
                referencedHex.add(NewsPictureLibrary.toHex(hash));
            }
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                String stem = storedHexStem(file);
                if (stem == null || referencedHex.contains(stem)) continue;
                try {
                    Files.deleteIfExists(file);
                } catch (Exception e) {
                    StockMarketMod.LOGGER.warn(
                            "[NewsPictureStore] GC failed to delete unreferenced picture {}",
                            file.getFileName(), e);
                }
            }
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("[NewsPictureStore] GC scan of {} failed", dir, e);
        }
    }

    // ── Introspection ────────────────────────────────────────────────────

    /**
     * Lists the content hashes of all currently stored pictures (files matching the
     * store's own {@code <40-lowercase-hex>.png} naming; foreign files are ignored).
     * Never throws.
     *
     * @return the stored hashes as lowercase hex strings (empty when the store is
     *         not wired, the directory is missing, or the scan failed)
     */
    public @NotNull Set<String> listHashes() {
        Set<String> hashes = new HashSet<>();
        Path dir = directory;
        if (dir == null || !Files.isDirectory(dir)) return hashes;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                String stem = storedHexStem(file);
                if (stem != null) hashes.add(stem);
            }
        } catch (Exception e) {
            StockMarketMod.LOGGER.error("[NewsPictureStore] Failed to list {}", dir, e);
        }
        return hashes;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** @return true if the hash is non-null and exactly one SHA-1 digest long */
    private static boolean isValidHash(byte @Nullable [] hash) {
        return hash != null && hash.length == NewsPictureLibrary.SHA1_LENGTH;
    }

    /**
     * Extracts the hex name stem of a file this store owns.
     *
     * @param file a directory entry
     * @return the 40-lowercase-hex stem if the entry is a regular
     *         {@code <sha1hex>.png} file written by this store, null for anything
     *         foreign (which the store must never touch)
     */
    private static @Nullable String storedHexStem(Path file) {
        String name = file.getFileName().toString();
        if (name.length() != HEX_NAME_LENGTH + FILE_EXTENSION.length()
                || !name.endsWith(FILE_EXTENSION)) {
            return null;
        }
        String stem = name.substring(0, HEX_NAME_LENGTH);
        for (int i = 0; i < stem.length(); i++) {
            char c = stem.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) return null;
        }
        return Files.isRegularFile(file) ? stem : null;
    }
}
