package net.kroia.stockmarket.news;

import net.kroia.stockmarket.StockMarketMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Config-layer picture library for the news event system (picture plan §2, layer 1).
 * <p>
 * Scans the drop-in folder {@code config/StockMarket/news/pictures/} on every
 * {@link NewsEventLibrary#reload()} pass, validates each {@code *.png}
 * (PNG signature + IHDR via {@link PngHeader}, dimension and file-size caps below)
 * and keeps {@code fileName → (sha1, bytes, width, height)} in memory. This is the
 * <b>authoring view</b>: at publish time the publisher snapshots the current entry
 * (hash + bytes) into the content-addressed published store (T-088) — a later swap
 * or delete of the config file never affects already-published records.
 * <p>
 * Skip-and-continue contract (like {@link NewsEventLibrary}): an invalid file adds a
 * {@link ValidationReport} ERROR for that file and the scan continues; a scan never
 * throws. The server never decodes pixel data — validation is header-only.
 * <p>
 * Not thread-safe: rescan and getters must be called from the server thread only
 * (same contract as {@link NewsEventLibrary}).
 */
public final class NewsPictureLibrary {

    /** Name of the pictures folder inside the news config directory. */
    public static final String PICTURES_DIR_NAME = "pictures";

    /** Minimum allowed picture width/height in pixels (plan §9). */
    public static final int MIN_DIMENSION = 16;

    /** Maximum allowed picture width/height in pixels (resolved decision §12.1). */
    public static final int MAX_DIMENSION = 512;

    /**
     * Maximum allowed picture file size in bytes (128 KiB).
     * <p>
     * <b>Load-bearing constant:</b> the picture network protocol (T-089) batches up to
     * 8 pictures per request inside a 640 KiB response budget, safely under the ~1 MiB
     * S2C custom-payload cap, <b>without any chunking</b>. That math only holds while
     * every stored picture is ≤ 128 KiB — do not raise this cap without revisiting the
     * batching design (picture plan §4.2).
     */
    public static final int MAX_FILE_BYTES = 128 * 1024;

    /** Length in bytes of a SHA-1 digest (the picture content hash). */
    public static final int SHA1_LENGTH = 20;

    /**
     * One validated picture from the config folder.
     * <p>
     * Immutable snapshot of the file at scan time: the raw PNG bytes, their SHA-1
     * (content identity for the published store and client caches, T-088/T-090) and
     * the header dimensions. {@code bytes} is intentionally not copied defensively
     * on access — callers must not mutate it.
     */
    public static final class Entry {
        private final String fileName;
        private final byte[] sha1;
        private final byte[] bytes;
        private final int width;
        private final int height;

        private Entry(String fileName, byte[] sha1, byte[] bytes, int width, int height) {
            this.fileName = fileName;
            this.sha1 = sha1;
            this.bytes = bytes;
            this.width = width;
            this.height = height;
        }

        /** @return the plain file name inside the pictures folder (e.g. {@code "diamond_rush.png"}) */
        public String getFileName() { return fileName; }

        /** @return the 20-byte SHA-1 of the raw file bytes (content identity; do not mutate) */
        public byte[] getSha1() { return sha1; }

        /** @return the SHA-1 as a lowercase hex string (log/store file naming) */
        public String getSha1Hex() { return toHex(sha1); }

        /** @return the raw PNG file bytes as read from disk (do not mutate) */
        public byte[] getBytes() { return bytes; }

        /** @return the image width in pixels from the IHDR */
        public int getWidth() { return width; }

        /** @return the image height in pixels from the IHDR */
        public int getHeight() { return height; }

        @Override
        public String toString() {
            return "Entry{" + fileName + ", " + width + "x" + height
                    + ", " + bytes.length + " bytes, sha1=" + getSha1Hex() + "}";
        }
    }

    // ── State ────────────────────────────────────────────────────────────

    /** Scanned entries (fileName → entry, alphabetical file order); replaced on each rescan. */
    private Map<String, Entry> entries = new LinkedHashMap<>();

    // ── Accessors ────────────────────────────────────────────────────────

    /** @return the absolute pictures directory ({@code config/StockMarket/news/pictures}) */
    public static Path getPicturesPath() {
        return NewsEventLibrary.getNewsConfigPath().resolve(PICTURES_DIR_NAME);
    }

    /**
     * Looks up a validated picture by its plain file name.
     *
     * @param fileName the file name as referenced by an event's {@code picture} field
     * @return the entry, or null if no valid picture with that name was found in the last scan
     */
    public @Nullable Entry get(@Nullable String fileName) {
        if (fileName == null) return null;
        return entries.get(fileName);
    }

    /** @return all validated pictures of the last scan as an unmodifiable fileName → entry map */
    public Map<String, Entry> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    /** @return the number of valid pictures found by the last scan */
    public int size() {
        return entries.size();
    }

    // ── Scanning ─────────────────────────────────────────────────────────

    /**
     * Rescans the pictures folder, replacing the in-memory entry map.
     * <p>
     * Creates the folder when missing. Every {@code *.png} file (case-insensitive
     * extension) is read and validated: PNG signature + IHDR must parse
     * ({@link PngHeader}), {@code width,height ∈ [}{@value #MIN_DIMENSION}{@code ,}
     * {@value #MAX_DIMENSION}{@code ]}, file size ≤ {@value #MAX_FILE_BYTES} bytes.
     * An invalid file adds an ERROR to the report and is skipped — the scan continues
     * with the remaining files (skip-and-continue). Never throws.
     *
     * @param picturesDir the pictures directory (created if missing)
     * @param report      collector for validation problems (merged into the reload report)
     */
    public void rescan(Path picturesDir, ValidationReport report) {
        Map<String, Entry> newEntries = new LinkedHashMap<>();
        try {
            if (!Files.exists(picturesDir)) {
                Files.createDirectories(picturesDir);
            }
            for (Path file : listPngFiles(picturesDir)) {
                scanFile(file, newEntries, report);
            }
        } catch (Exception e) {
            // Never throw out of a reload pass; keep whatever was collected so far.
            report.addError("", null, "failed to scan news pictures directory '"
                    + picturesDir + "': " + e);
            StockMarketMod.LOGGER.error("[NewsPictureLibrary] Failed to scan {}", picturesDir, e);
        }
        entries = newEntries;
    }

    /**
     * Reads and validates a single picture file; adds it to the target map when valid,
     * otherwise records an ERROR (file skipped, scan continues).
     */
    private static void scanFile(Path file, Map<String, Entry> target, ValidationReport report) {
        String fileName = file.getFileName().toString();
        byte[] bytes;
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                report.addError(fileName, null, "picture file is " + size + " bytes (max "
                        + MAX_FILE_BYTES + " = 128 KiB) — file skipped");
                return;
            }
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            report.addError(fileName, null, "failed to read picture file: " + e);
            return;
        }

        PngHeader header = PngHeader.parse(bytes);
        if (header == null) {
            report.addError(fileName, null,
                    "not a valid PNG (signature/IHDR check failed) — file skipped");
            return;
        }
        if (header.getWidth() < MIN_DIMENSION || header.getHeight() < MIN_DIMENSION
                || header.getWidth() > MAX_DIMENSION || header.getHeight() > MAX_DIMENSION) {
            report.addError(fileName, null, "picture is " + header.getWidth() + "x"
                    + header.getHeight() + " px (allowed: " + MIN_DIMENSION + ".."
                    + MAX_DIMENSION + " per side) — file skipped");
            return;
        }

        target.put(fileName, new Entry(fileName, sha1(bytes), bytes,
                header.getWidth(), header.getHeight()));
    }

    /** Lists all {@code *.png} files (case-insensitive), sorted by filename. */
    private static List<Path> listPngFiles(Path picturesDir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(picturesDir)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file)
                        && name.toLowerCase(Locale.ROOT).endsWith(".png")) {
                    files.add(file);
                }
            }
        }
        files.sort((a, b) -> a.getFileName().toString()
                .compareToIgnoreCase(b.getFileName().toString()));
        return files;
    }

    // ── File-name validation (shared with the event schema) ─────────────

    /**
     * Validates an event's {@code picture} file-name reference (picture plan §1).
     * <p>
     * The value must be a <b>bare file name</b>, never a path — this is the
     * path-traversal guard for everything that later resolves the name against the
     * pictures folder. Rejected: blank, containing {@code /}, {@code \}, {@code ..}
     * or {@code :}, starting with a dot, or not ending in {@code .png}
     * (case-insensitive).
     *
     * @param fileName the candidate file name from the JSON {@code picture} field
     * @return null if the name is acceptable, otherwise a human-readable problem
     *         description for the validation report
     */
    public static @Nullable String validatePictureFileName(@Nullable String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file name is empty";
        }
        if (fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0) {
            return "file name must not contain path separators";
        }
        if (fileName.contains("..")) {
            return "file name must not contain '..'";
        }
        if (fileName.indexOf(':') >= 0) {
            return "file name must not contain ':'";
        }
        if (fileName.startsWith(".")) {
            return "file name must not start with a dot";
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".png")) {
            return "file name must end with '.png'";
        }
        return null;
    }

    // ── Hashing ──────────────────────────────────────────────────────────

    /**
     * Computes the SHA-1 digest of the given bytes (the picture content identity used
     * by the published store, network protocol and client caches).
     *
     * @param data the raw file bytes
     * @return the 20-byte SHA-1 digest
     */
    public static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is mandatory for every conforming JRE — this cannot happen.
            throw new IllegalStateException("JRE without SHA-1 support", e);
        }
    }

    /** @return the given bytes as a lowercase hex string */
    public static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
