package net.kroia.stockmarket.news;

import net.kroia.stockmarket.StockMarketMod;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Ships the default pictures that {@link NewsEventLibrary} extracts into
 * {@code config/StockMarket/news/pictures/} on first run — the picture-side twin of
 * {@link DefaultNewsEvents#writeDefaultFile} (picture plan §7).
 * <p>
 * <b>Additive self-healing:</b> {@link #extractMissingDefaults} runs during every reload,
 * right after the defaults-json step. It creates the folder if absent and, for each shipped
 * default event, writes {@code <eventId>.png} <b>only when that file does not already
 * exist</b> — so newly shipped default pictures reach existing installs while admin art and
 * admin-modified defaults are never overwritten. (The legacy {@link #extractDefaultsIfEmpty}
 * all-or-nothing variant is retained for callers/tests that want the empty-only semantics.)
 * Source priority per picture:
 * <ol>
 *   <li><b>Baked jar resource</b> {@code news_pictures/<eventId>.png} (classloader,
 *       same mechanism as the shipped {@code sql/} resources) — drop real art into
 *       {@code common/src/main/resources/news_pictures/} and it automatically takes
 *       precedence, no code change;</li>
 *   <li><b>Procedural fallback</b>: a deterministic square grayscale placeholder PNG
 *       ({@value #GENERATED_SIZE}×{@value #GENERATED_SIZE} px, well under both
 *       {@link NewsPictureLibrary} caps) with simple newsprint motifs (horizon,
 *       skyline/mountain silhouette, bar chart with trend arrow, hatching, ordered
 *       Bayer dithering), seeded by the event id — same id, same bytes, every run.</li>
 * </ol>
 * <p>
 * The PNG bytes are produced by a minimal hand-rolled grayscale encoder
 * ({@link #encodeGrayscalePng}) instead of {@code ImageIO}: it avoids any AWT
 * dependency on dedicated servers, guarantees 8-bit grayscale (color type 0) output,
 * and doubles as the test suite's fixture builder for arbitrary-dimension PNGs.
 * Every generated/baked picture is re-validated against {@link PngHeader} and the
 * {@link NewsPictureLibrary} caps before it is written.
 */
public final class DefaultNewsPictures {

    /** Classpath directory of the baked default pictures (inside the mod jar). */
    public static final String RESOURCE_DIR = "/news_pictures/";

    /** Side length of procedurally generated placeholder pictures (square, §12.3). */
    public static final int GENERATED_SIZE = 384;

    // Grayscale palette of the generated placeholders (newsprint-ish tones).
    private static final int TONE_PAPER = 232;
    private static final int TONE_LIGHT = 176;
    private static final int TONE_MID = 104;
    private static final int TONE_INK = 24;

    /** Standard 4×4 Bayer ordered-dithering matrix (thresholds 0..15). */
    private static final int[] BAYER_4X4 = {
            0, 8, 2, 10,
            12, 4, 14, 6,
            3, 11, 1, 9,
            15, 7, 13, 5
    };

    private DefaultNewsPictures() {
    }

    // ── Extraction ───────────────────────────────────────────────────────

    /**
     * Additively self-heals the default pictures: for every shipped default event id,
     * writes {@code <eventId>.png} if (and only if) it does not already exist. The
     * directory is created when absent. An existing {@code .png} — admin art or an
     * admin-modified default — is <b>never overwritten</b>, so newly shipped defaults
     * reach existing installs without clobbering customizations. Per id the baked woodcut
     * jar resource is preferred and the procedural placeholder is the fallback (see
     * {@link #loadBakedOrGenerate}). Never throws — failures are logged and skipped.
     *
     * @param picturesDir the pictures directory ({@code config/StockMarket/news/pictures})
     * @return the number of missing default pictures written in this call
     */
    public static int extractMissingDefaults(Path picturesDir) {
        int written = 0;
        try {
            if (!Files.exists(picturesDir)) {
                Files.createDirectories(picturesDir);
            }
            for (String eventId : DefaultNewsEvents.DEFAULT_EVENT_IDS) {
                Path target = picturesDir.resolve(eventId + ".png");
                if (Files.exists(target)) continue; // admin art / modified default — hands off
                byte[] png = loadBakedOrGenerate(eventId);
                if (png == null) continue; // problem already logged
                Files.write(target, png);
                written++;
            }
            if (written > 0) {
                StockMarketMod.LOGGER.info(
                        "[NewsPictureLibrary] Self-healed {} missing default news picture(s) in {}",
                        written, picturesDir);
            }
        } catch (Exception e) {
            StockMarketMod.LOGGER.error(
                    "[NewsPictureLibrary] Failed to write missing default news pictures", e);
        }
        return written;
    }

    /**
     * Writes the default pictures into the given pictures directory if it is empty.
     * <p>
     * "Empty" means: the directory does not exist (it is created) or contains no
     * {@code .png} file (case-insensitive). A non-empty directory is left untouched.
     * Never throws — failures are logged and reported via the return value.
     *
     * @param picturesDir the pictures directory ({@code config/StockMarket/news/pictures})
     * @return true if default pictures were written in this call
     */
    public static boolean extractDefaultsIfEmpty(Path picturesDir) {
        try {
            if (Files.exists(picturesDir)) {
                if (containsPng(picturesDir)) return false; // admin content present — hands off
            } else {
                Files.createDirectories(picturesDir);
            }

            int written = 0;
            for (String eventId : DefaultNewsEvents.DEFAULT_EVENT_IDS) {
                byte[] png = loadBakedOrGenerate(eventId);
                if (png == null) continue; // problem already logged
                Files.write(picturesDir.resolve(eventId + ".png"), png);
                written++;
            }
            StockMarketMod.LOGGER.info(
                    "[NewsPictureLibrary] Generated {} default news picture(s) in {}",
                    written, picturesDir);
            return written > 0;
        } catch (Exception e) {
            StockMarketMod.LOGGER.error(
                    "[NewsPictureLibrary] Failed to write default news pictures", e);
            return false;
        }
    }

    /** @return true if the directory contains at least one {@code *.png} file */
    private static boolean containsPng(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file) && file.getFileName().toString()
                        .toLowerCase(Locale.ROOT).endsWith(".png")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Resolves the picture bytes for one default event: baked jar resource first,
     * procedural placeholder second. The result is validated against
     * {@link PngHeader} and the {@link NewsPictureLibrary} caps; null (with an error
     * log) only if even the generated fallback fails validation — which would be a
     * programming error, not an admin problem.
     */
    private static @Nullable byte[] loadBakedOrGenerate(String eventId) {
        byte[] baked = loadBakedResource(eventId);
        if (baked != null) {
            if (isValidPicture(baked)) return baked;
            StockMarketMod.LOGGER.warn(
                    "[NewsPictureLibrary] Baked default picture '{}' fails validation — using procedural placeholder",
                    eventId + ".png");
        }
        byte[] generated = generatePlaceholder(eventId);
        if (isValidPicture(generated)) return generated;
        StockMarketMod.LOGGER.error(
                "[NewsPictureLibrary] Generated placeholder for '{}' fails its own validation — skipped (bug)",
                eventId);
        return null;
    }

    /** Reads a baked {@code news_pictures/<eventId>.png} from the jar, or null if absent. */
    private static @Nullable byte[] loadBakedResource(String eventId) {
        try (InputStream in = DefaultNewsPictures.class
                .getResourceAsStream(RESOURCE_DIR + eventId + ".png")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            StockMarketMod.LOGGER.warn(
                    "[NewsPictureLibrary] Failed to read baked default picture for '{}'", eventId, e);
            return null;
        }
    }

    /** @return true if the bytes pass the same checks {@link NewsPictureLibrary} applies */
    private static boolean isValidPicture(byte[] png) {
        if (png.length > NewsPictureLibrary.MAX_FILE_BYTES) return false;
        PngHeader header = PngHeader.parse(png);
        return header != null
                && header.getWidth() >= NewsPictureLibrary.MIN_DIMENSION
                && header.getHeight() >= NewsPictureLibrary.MIN_DIMENSION
                && header.getWidth() <= NewsPictureLibrary.MAX_DIMENSION
                && header.getHeight() <= NewsPictureLibrary.MAX_DIMENSION;
    }

    // ── Procedural placeholder generator ─────────────────────────────────

    /**
     * Generates the deterministic square grayscale placeholder PNG for one event id.
     * <p>
     * The seed is derived from the SHA-1 of the id's UTF-8 bytes (never
     * {@code Math.random()}/time) — the same id always yields byte-identical pixel
     * data, which makes defaults extraction and the test suite reproducible.
     * Composition (simple newsprint motifs, placeholders the server owner is expected
     * to replace): hatched paper background, sun, mountain-range or city-skyline
     * silhouette on a horizon, an ink bar chart with a trend arrow, a double border
     * frame, all quantized with 4×4 Bayer ordered dithering for a print look.
     *
     * @param eventId the event id the picture is for (seed + target file name)
     * @return the encoded PNG bytes ({@value #GENERATED_SIZE}×{@value #GENERATED_SIZE},
     *         8-bit grayscale)
     */
    public static byte[] generatePlaceholder(String eventId) {
        final int size = GENERATED_SIZE;
        long seed = seedFromId(eventId);
        Random rnd = new Random(seed);

        // Working luminance buffer (row-major, values 0..255).
        int[] lum = new int[size * size];

        // 1. Paper background with faint diagonal hatching.
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean hatch = ((x + y) % 14) == 0;
                lum[y * size + x] = hatch ? TONE_LIGHT + 24 : TONE_PAPER;
            }
        }

        // 2. Horizon with a darker, hatched ground below it.
        int horizon = size / 2 + 20 + rnd.nextInt(60);
        for (int y = horizon; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean hatch = ((x - y) % 8) == 0;
                lum[y * size + x] = hatch ? TONE_MID : TONE_LIGHT;
            }
        }

        // 3. Sun: a filled mid-tone disc in the upper area.
        int sunX = 50 + rnd.nextInt(size - 100);
        int sunY = 50 + rnd.nextInt(horizon / 3);
        int sunR = 18 + rnd.nextInt(16);
        fillCircle(lum, size, sunX, sunY, sunR, TONE_MID);

        // 4. Silhouette on the horizon: mountains or a city skyline (seeded choice).
        if (rnd.nextBoolean()) {
            // Mountain range: a few triangular peaks.
            int peaks = 3 + rnd.nextInt(3);
            for (int p = 0; p < peaks; p++) {
                int peakX = rnd.nextInt(size);
                int peakH = 40 + rnd.nextInt(horizon / 3);
                int halfW = peakH + 10 + rnd.nextInt(40);
                for (int x = Math.max(0, peakX - halfW); x < Math.min(size, peakX + halfW); x++) {
                    int h = peakH - Math.abs(x - peakX) * peakH / halfW;
                    for (int y = horizon - h; y < horizon; y++) {
                        if (y >= 0) lum[y * size + x] = TONE_MID;
                    }
                }
            }
        } else {
            // City skyline: adjacent rectangles of varying height.
            int x = 10;
            while (x < size - 10) {
                int w = 24 + rnd.nextInt(40);
                int h = 30 + rnd.nextInt(horizon / 3);
                fillRect(lum, size, x, horizon - h, Math.min(w, size - 10 - x), h, TONE_MID);
                x += w + 4 + rnd.nextInt(10);
            }
        }

        // 5. Ink bar chart standing on the ground, with a trend arrow across the tops.
        int bars = 5;
        boolean rising = rnd.nextBoolean();
        int chartLeft = 60, chartRight = size - 60;
        int barW = (chartRight - chartLeft) / bars - 10;
        int baseline = size - 50;
        int minH = 40, maxH = 130;
        int[] topX = new int[bars];
        int[] topY = new int[bars];
        for (int b = 0; b < bars; b++) {
            double t = b / (double) (bars - 1);
            double trend = rising ? t : 1.0 - t;
            int h = minH + (int) (trend * (maxH - minH)) + rnd.nextInt(16) - 8;
            int bx = chartLeft + b * (barW + 10);
            fillRect(lum, size, bx, baseline - h, barW, h, TONE_INK);
            topX[b] = bx + barW / 2;
            topY[b] = baseline - h - 14;
        }
        for (int b = 0; b < bars - 1; b++) {
            drawLine(lum, size, topX[b], topY[b], topX[b + 1], topY[b + 1], 3, TONE_INK);
        }
        // Arrowhead barbs at the last bar top, pointing back against the trend direction.
        int ax = topX[bars - 1], ay = topY[bars - 1];
        int barbDy = rising ? 12 : -12; // rising trend points up → barbs go down/left
        drawLine(lum, size, ax, ay, ax - 14, ay, 3, TONE_INK);
        drawLine(lum, size, ax, ay, ax, ay + barbDy, 3, TONE_INK);

        // 6. Double border frame (outer thick ink, inner thin line).
        drawFrame(lum, size, 4, 6, TONE_INK);
        drawFrame(lum, size, 16, 2, TONE_MID);

        // 7. Bayer ordered dithering: quantize to 4 tones for the newsprint look.
        byte[] pixels = new byte[size * size];
        int levels = 3; // 4 output tones = 3 intervals
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double v = (lum[y * size + x] - TONE_INK) / (double) (TONE_PAPER - TONE_INK);
                v = Math.max(0.0, Math.min(1.0, v));
                double scaled = v * levels;
                int base = (int) Math.floor(scaled);
                if (base >= levels) base = levels - 1;
                double frac = scaled - base;
                double threshold = (BAYER_4X4[(y % 4) * 4 + (x % 4)] + 0.5) / 16.0;
                int level = frac > threshold ? base + 1 : base;
                pixels[y * size + x] = (byte) (TONE_INK + level * (TONE_PAPER - TONE_INK) / levels);
            }
        }

        return encodeGrayscalePng(size, size, pixels);
    }

    /** Derives a stable 64-bit seed from the SHA-1 of the event id's UTF-8 bytes. */
    private static long seedFromId(String eventId) {
        byte[] hash = NewsPictureLibrary.sha1(eventId.getBytes(StandardCharsets.UTF_8));
        long seed = 0;
        for (int i = 0; i < 8; i++) {
            seed = (seed << 8) | (hash[i] & 0xFFL);
        }
        return seed;
    }

    // ── Drawing helpers (clipping, luminance buffer) ─────────────────────

    /** Fills an axis-aligned rectangle, clipped to the buffer. */
    private static void fillRect(int[] lum, int size, int x, int y, int w, int h, int tone) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y);
        int x1 = Math.min(size, x + w), y1 = Math.min(size, y + h);
        for (int yy = y0; yy < y1; yy++) {
            for (int xx = x0; xx < x1; xx++) {
                lum[yy * size + xx] = tone;
            }
        }
    }

    /** Fills a disc, clipped to the buffer. */
    private static void fillCircle(int[] lum, int size, int cx, int cy, int r, int tone) {
        for (int yy = Math.max(0, cy - r); yy <= Math.min(size - 1, cy + r); yy++) {
            for (int xx = Math.max(0, cx - r); xx <= Math.min(size - 1, cx + r); xx++) {
                int dx = xx - cx, dy = yy - cy;
                if (dx * dx + dy * dy <= r * r) {
                    lum[yy * size + xx] = tone;
                }
            }
        }
    }

    /** Draws a thick line by stamping squares along the segment, clipped to the buffer. */
    private static void drawLine(int[] lum, int size, int x0, int y0, int x1, int y1,
                                 int thickness, int tone) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            fillRect(lum, size, x - thickness / 2, y - thickness / 2, thickness, thickness, tone);
        }
    }

    /** Draws a square frame with the given inset and stroke width. */
    private static void drawFrame(int[] lum, int size, int inset, int stroke, int tone) {
        fillRect(lum, size, inset, inset, size - 2 * inset, stroke, tone);
        fillRect(lum, size, inset, size - inset - stroke, size - 2 * inset, stroke, tone);
        fillRect(lum, size, inset, inset, stroke, size - 2 * inset, tone);
        fillRect(lum, size, size - inset - stroke, inset, stroke, size - 2 * inset, tone);
    }

    // ── Minimal grayscale PNG encoder ────────────────────────────────────

    /**
     * Encodes 8-bit grayscale pixels as a minimal, spec-conforming PNG
     * (color type 0, bit depth 8, no interlace, filter type 0 per scanline,
     * single IDAT chunk).
     * <p>
     * Pure JDK ({@code Deflater}/{@code CRC32}), no AWT/ImageIO — safe on fully
     * headless dedicated servers. Also used by the picture test suite to build
     * valid PNG fixtures of arbitrary dimensions.
     *
     * @param width  image width in pixels (&gt; 0)
     * @param height image height in pixels (&gt; 0)
     * @param pixels row-major luminance values, length {@code width * height}
     * @return the encoded PNG file bytes
     * @throws IllegalArgumentException if the dimensions and pixel count disagree
     */
    public static byte[] encodeGrayscalePng(int width, int height, byte[] pixels) {
        if (width <= 0 || height <= 0 || pixels.length != width * height) {
            throw new IllegalArgumentException("pixel buffer does not match " + width + "x" + height);
        }

        // Raw image data: every scanline is prefixed with filter type 0 (None).
        byte[] raw = new byte[(width + 1) * height];
        for (int y = 0; y < height; y++) {
            raw[y * (width + 1)] = 0; // filter: None
            System.arraycopy(pixels, y * width, raw, y * (width + 1) + 1, width);
        }

        // zlib-compress the scanlines for the IDAT chunk.
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            idat.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();

        // IHDR data: width(4) height(4) bitDepth(1) colorType(1) compression(1) filter(1) interlace(1)
        byte[] ihdr = new byte[13];
        writeIntBE(ihdr, 0, width);
        writeIntBE(ihdr, 4, height);
        ihdr[8] = 8;  // bit depth
        ihdr[9] = 0;  // color type: grayscale
        ihdr[10] = 0; // compression: deflate
        ihdr[11] = 0; // filter method: adaptive
        ihdr[12] = 0; // interlace: none

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PngHeader.PNG_SIGNATURE);
        writeChunk(out, "IHDR", ihdr);
        writeChunk(out, "IDAT", idat.toByteArray());
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    /** Writes one PNG chunk: length, type, data, CRC32(type + data). */
    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        byte[] lengthBytes = new byte[4];
        writeIntBE(lengthBytes, 0, data.length);
        out.writeBytes(lengthBytes);
        out.writeBytes(typeBytes);
        out.writeBytes(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] crcBytes = new byte[4];
        writeIntBE(crcBytes, 0, (int) crc.getValue());
        out.writeBytes(crcBytes);
    }

    /** Writes a big-endian 32-bit integer at the given offset. */
    private static void writeIntBE(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}
