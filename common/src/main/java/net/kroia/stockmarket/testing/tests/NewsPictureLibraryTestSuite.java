package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kroia.modutilities.JsonUtilities;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.news.DefaultNewsEvents;
import net.kroia.stockmarket.news.DefaultNewsPictures;
import net.kroia.stockmarket.news.NewsEventDefinition;
import net.kroia.stockmarket.news.NewsEventLibrary;
import net.kroia.stockmarket.news.NewsPictureLibrary;
import net.kroia.stockmarket.news.PngHeader;
import net.kroia.stockmarket.news.ValidationReport;
import net.kroia.stockmarket.testing.StockMarketTestCategories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Tests for the news picture library (T-087, picture plan §10, category
 * {@code sm_news_pictures}): {@link PngHeader} signature/IHDR parsing,
 * SHA-1 hashing, {@link NewsPictureLibrary} folder scanning with
 * skip-and-continue validation (size/dimension caps), the traversal-safe
 * {@code picture} schema field on {@link NewsEventDefinition}, and the
 * {@link DefaultNewsPictures} defaults extraction + procedural generator.
 * All folder tests run against temp directories, never the live config folder.
 */
public class NewsPictureLibraryTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.NEWS_PICTURES;
    }

    @Override
    public void registerTests() {
        // PngHeader
        addTest("png_header_valid_parse", this::test_pngHeader_validParse);
        addTest("png_header_truncated_rejected", this::test_pngHeader_truncatedRejected);
        addTest("png_header_non_png_rejected", this::test_pngHeader_nonPngRejected);
        addTest("png_header_oversized_dimensions_still_parse", this::test_pngHeader_oversizedDimensionsStillParse);
        addTest("png_header_zero_dimension_rejected", this::test_pngHeader_zeroDimensionRejected);

        // Hashing
        addTest("sha1_known_vector_and_stability", this::test_sha1_knownVectorAndStability);

        // Folder scan
        addTest("scan_invalid_files_error_others_continue", this::test_scan_invalidFilesError_othersContinue);
        addTest("scan_filename_to_hash_resolution", this::test_scan_filenameToHashResolution);
        addTest("scan_byte_swap_rescan_changes_hash", this::test_scan_byteSwapRescan_changesHash);

        // File-name validation (traversal guard)
        addTest("picture_filename_traversal_rejected", this::test_pictureFileName_traversalRejected);

        // Schema field on NewsEventDefinition
        addTest("parse_picture_field_valid_string", this::test_parsePicture_validString);
        addTest("parse_picture_field_absent_is_null", this::test_parsePicture_absentIsNull);
        addTest("parse_picture_field_traversal_error_event_kept", this::test_parsePicture_traversalError_eventKept);
        addTest("parse_picture_field_wrong_type_error_event_kept", this::test_parsePicture_wrongTypeError_eventKept);

        // Defaults extraction + generator
        addTest("defaults_extraction_empty_folder_creates_all", this::test_defaultsExtraction_emptyFolder_createsAll);
        addTest("defaults_extraction_nonempty_folder_untouched", this::test_defaultsExtraction_nonEmptyFolder_untouched);
        addTest("defaults_referenced_by_default_events_json", this::test_defaults_referencedByDefaultEventsJson);
        addTest("generator_is_deterministic", this::test_generator_isDeterministic);

        // NewsEventLibrary reload integration
        addTest("library_reload_missing_picture_warns_event_loads", this::test_libraryReload_missingPictureWarns_eventLoads);
        addTest("library_reload_resolves_existing_picture", this::test_libraryReload_resolvesExistingPicture);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Encodes a small valid grayscale PNG with a deterministic gradient pattern. */
    private static byte[] validPng(int width, int height) {
        byte[] pixels = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = (byte) ((x * 7 + y * 13) & 0xFF);
            }
        }
        return DefaultNewsPictures.encodeGrayscalePng(width, height, pixels);
    }

    /** Encodes an incompressible (seeded-noise) PNG — used to exceed the file-size cap. */
    private static byte[] noisePng(int width, int height, long seed) {
        byte[] pixels = new byte[width * height];
        new Random(seed).nextBytes(pixels);
        return DefaultNewsPictures.encodeGrayscalePng(width, height, pixels);
    }

    /**
     * Hand-crafts signature + IHDR bytes only (no IDAT/IEND) for arbitrary raw
     * dimension values — lets tests probe headers the encoder refuses to produce.
     */
    private static byte[] craftedHeader(int width, int height) {
        byte[] data = new byte[PngHeader.MIN_HEADER_BYTES];
        System.arraycopy(PngHeader.PNG_SIGNATURE, 0, data, 0, 8);
        data[8] = 0; data[9] = 0; data[10] = 0; data[11] = 13; // IHDR length
        data[12] = 'I'; data[13] = 'H'; data[14] = 'D'; data[15] = 'R';
        writeIntBE(data, 16, width);
        writeIntBE(data, 20, height);
        data[24] = 8; // bit depth
        data[25] = 0; // color type: grayscale
        // compression/filter/interlace stay 0
        return data;
    }

    private static void writeIntBE(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    /** Creates a temp directory for scan/extraction tests (never the live config folder). */
    private static Path createTempDir() throws IOException {
        return Files.createTempDirectory("sm_news_pictures_test");
    }

    /** Best-effort recursive cleanup of a temp directory. */
    private static void deleteRecursively(Path dir) {
        if (dir == null) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    /** Parses one event JSON string into a definition + report (schema-field tests). */
    private static NewsEventDefinition parseEvent(String json, ValidationReport report) {
        JsonObject obj = JsonUtilities.fromString(json).getAsJsonObject();
        return NewsEventDefinition.parse(obj, "test.json", report);
    }

    /** A minimal valid event JSON with the given extra fields spliced in after the id. */
    private static String minimalEvent(String extraFields) {
        return "{\"id\":\"test_event\"," + (extraFields.isEmpty() ? "" : extraFields + ",")
                + "\"headline\":\"Test headline\",\"text\":\"Test text\","
                + "\"impact\":{\"type\":\"shock\",\"peakFactor\":0.5},"
                + "\"markets\":[{\"item\":\"minecraft:diamond\",\"weightFactor\":1.0}]}";
    }

    // ========================================================================
    // PngHeader
    // ========================================================================

    /** A PNG produced by the shipped encoder must parse with the exact header values. */
    private TestResult test_pngHeader_validParse() {
        byte[] png = validPng(48, 32);
        TestResult r = assertTrue("encoder output must carry the PNG signature",
                PngHeader.hasPngSignature(png));
        if (!r.passed()) return r;
        PngHeader header = PngHeader.parse(png);
        r = assertNotNull("valid PNG must parse", header);
        if (!r.passed()) return r;
        r = assertEquals("width", 48, header.getWidth());
        if (!r.passed()) return r;
        r = assertEquals("height", 32, header.getHeight());
        if (!r.passed()) return r;
        r = assertEquals("bit depth", 8, header.getBitDepth());
        if (!r.passed()) return r;
        return assertEquals("color type (grayscale)", 0, header.getColorType());
    }

    /** Truncated data (below and above the signature length) must be rejected, never throw. */
    private TestResult test_pngHeader_truncatedRejected() {
        byte[] png = validPng(32, 32);
        TestResult r = assertNull("empty array must not parse", PngHeader.parse(new byte[0]));
        if (!r.passed()) return r;
        r = assertNull("null must not parse", PngHeader.parse(null));
        if (!r.passed()) return r;
        r = assertNull("signature-only bytes must not parse",
                PngHeader.parse(Arrays.copyOf(png, 8)));
        if (!r.passed()) return r;
        return assertNull("bytes truncated inside the IHDR must not parse",
                PngHeader.parse(Arrays.copyOf(png, PngHeader.MIN_HEADER_BYTES - 1)));
    }

    /** Non-PNG bytes (text, wrong first chunk) must be rejected. */
    private TestResult test_pngHeader_nonPngRejected() {
        byte[] text = "this is definitely not a png file, just some text bytes........"
                .getBytes(StandardCharsets.UTF_8);
        TestResult r = assertNull("text bytes must not parse", PngHeader.parse(text));
        if (!r.passed()) return r;
        r = assertFalse("text bytes must fail the signature check",
                PngHeader.hasPngSignature(text));
        if (!r.passed()) return r;
        // Valid signature but the first chunk is not IHDR
        byte[] wrongChunk = craftedHeader(32, 32);
        wrongChunk[12] = 'X';
        return assertNull("first chunk other than IHDR must not parse",
                PngHeader.parse(wrongChunk));
    }

    /**
     * Oversized dimensions still PARSE — the header parser reports what the file
     * says; the 16..512 policy caps are NewsPictureLibrary's job (tested below).
     */
    private TestResult test_pngHeader_oversizedDimensionsStillParse() {
        PngHeader header = PngHeader.parse(craftedHeader(100_000, 24));
        TestResult r = assertNotNull("oversized-dimension header must still parse", header);
        if (!r.passed()) return r;
        r = assertEquals("parsed width", 100_000, header.getWidth());
        if (!r.passed()) return r;
        return assertEquals("parsed height", 24, header.getHeight());
    }

    /** Zero and negative (> 2^31-1 unsigned) dimensions are structurally invalid. */
    private TestResult test_pngHeader_zeroDimensionRejected() {
        TestResult r = assertNull("zero width must not parse",
                PngHeader.parse(craftedHeader(0, 32)));
        if (!r.passed()) return r;
        r = assertNull("zero height must not parse",
                PngHeader.parse(craftedHeader(32, 0)));
        if (!r.passed()) return r;
        return assertNull("width above Integer.MAX_VALUE (reads negative) must not parse",
                PngHeader.parse(craftedHeader(0x80000001, 32)));
    }

    // ========================================================================
    // Hashing
    // ========================================================================

    /** SHA-1 must match the published test vector and be stable across calls. */
    private TestResult test_sha1_knownVectorAndStability() {
        byte[] abc = "abc".getBytes(StandardCharsets.UTF_8);
        String hex = NewsPictureLibrary.toHex(NewsPictureLibrary.sha1(abc));
        TestResult r = assertEquals("sha1(\"abc\") must match the RFC 3174 test vector",
                "a9993e364706816aba3e25717850c26c9cd0d89d", hex);
        if (!r.passed()) return r;
        r = assertEquals("digest length must be " + NewsPictureLibrary.SHA1_LENGTH + " bytes",
                NewsPictureLibrary.SHA1_LENGTH, NewsPictureLibrary.sha1(abc).length);
        if (!r.passed()) return r;
        return assertTrue("repeated hashing of the same bytes must be identical",
                Arrays.equals(NewsPictureLibrary.sha1(abc), NewsPictureLibrary.sha1(abc)));
    }

    // ========================================================================
    // Folder scan
    // ========================================================================

    /**
     * One valid + four differently-broken files: each broken file yields an ERROR,
     * the valid one still loads (skip-and-continue), non-PNG extensions are ignored.
     */
    private TestResult test_scan_invalidFilesError_othersContinue() {
        Path dir = null;
        try {
            dir = createTempDir();
            Files.write(dir.resolve("good.png"), validPng(64, 64));
            Files.write(dir.resolve("bad.png"), "not a png at all".getBytes(StandardCharsets.UTF_8));
            Files.write(dir.resolve("toobig.png"), noisePng(400, 400, 42)); // incompressible > 128 KiB
            Files.write(dir.resolve("huge_dims.png"), craftedHeader(1024, 1024)); // > 512 px cap
            Files.write(dir.resolve("tiny.png"), validPng(8, 8)); // < 16 px minimum
            Files.write(dir.resolve("notes.txt"), "ignored".getBytes(StandardCharsets.UTF_8));

            NewsPictureLibrary library = new NewsPictureLibrary();
            ValidationReport report = new ValidationReport();
            library.rescan(dir, report);

            TestResult r = assertEquals("only the valid picture must be loaded", 1, library.size());
            if (!r.passed()) return r;
            r = assertNotNull("good.png must be resolvable", library.get("good.png"));
            if (!r.passed()) return r;
            r = assertEquals("each broken png must add exactly one ERROR",
                    4, report.errorCount());
            if (!r.passed()) return r;
            return assertNull("the .txt file must be ignored entirely, not errored",
                    library.get("notes.txt"));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** A scanned entry must expose the file's own SHA-1, bytes and header dimensions. */
    private TestResult test_scan_filenameToHashResolution() {
        Path dir = null;
        try {
            dir = createTempDir();
            byte[] png = validPng(96, 48);
            Files.write(dir.resolve("pic.png"), png);

            NewsPictureLibrary library = new NewsPictureLibrary();
            ValidationReport report = new ValidationReport();
            library.rescan(dir, report);

            NewsPictureLibrary.Entry entry = library.get("pic.png");
            TestResult r = assertNotNull("pic.png must resolve to an entry", entry);
            if (!r.passed()) return r;
            r = assertTrue("entry hash must equal the SHA-1 of the raw file bytes",
                    Arrays.equals(NewsPictureLibrary.sha1(png), entry.getSha1()));
            if (!r.passed()) return r;
            r = assertTrue("entry bytes must equal the file bytes",
                    Arrays.equals(png, entry.getBytes()));
            if (!r.passed()) return r;
            r = assertEquals("entry width", 96, entry.getWidth());
            if (!r.passed()) return r;
            r = assertEquals("entry height", 48, entry.getHeight());
            if (!r.passed()) return r;
            return assertFalse("a clean scan must not report problems: " + report,
                    report.hasErrors() || report.hasWarnings());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Swapping a file's bytes and rescanning must yield a different content hash. */
    private TestResult test_scan_byteSwapRescan_changesHash() {
        Path dir = null;
        try {
            dir = createTempDir();
            NewsPictureLibrary library = new NewsPictureLibrary();

            Files.write(dir.resolve("pic.png"), validPng(64, 64));
            library.rescan(dir, new ValidationReport());
            byte[] hashBefore = library.get("pic.png").getSha1();

            Files.write(dir.resolve("pic.png"), validPng(128, 128)); // different content
            library.rescan(dir, new ValidationReport());
            byte[] hashAfter = library.get("pic.png").getSha1();

            return assertFalse("byte-swapped file must produce a new SHA-1 after rescan",
                    Arrays.equals(hashBefore, hashAfter));
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    // ========================================================================
    // File-name validation (traversal guard)
    // ========================================================================

    /** All traversal/format attack strings must be rejected; plain names accepted. */
    private TestResult test_pictureFileName_traversalRejected() {
        String[] rejected = {
                "../evil.png", "..\\evil.png", "a/b.png", "a\\b.png",
                "sub/../x.png", "C:evil.png", "c:\\x.png", ".hidden.png",
                "..png", "picture.jpg", "picture.png.txt", "picture", "", "   ", null
        };
        for (String name : rejected) {
            if (NewsPictureLibrary.validatePictureFileName(name) == null) {
                return fail("file name must be rejected: \"" + name + "\"");
            }
        }
        String[] accepted = { "picture.png", "PICTURE.PNG", "my_event-2.png", "x.PnG" };
        for (String name : accepted) {
            String problem = NewsPictureLibrary.validatePictureFileName(name);
            if (problem != null) {
                return fail("file name must be accepted: \"" + name + "\" — got: " + problem);
            }
        }
        return pass("traversal guard accepts bare *.png names and rejects everything else");
    }

    // ========================================================================
    // Schema field on NewsEventDefinition
    // ========================================================================

    /** A plain valid file name must land in getPictureFileName() without problems. */
    private TestResult test_parsePicture_validString() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent("\"picture\":\"img.png\""), report);
        TestResult r = assertNotNull("event must parse", def);
        if (!r.passed()) return r;
        r = assertEquals("picture file name", "img.png", def.getPictureFileName());
        if (!r.passed()) return r;
        return assertFalse("no problems expected: " + report,
                report.hasErrors() || report.hasWarnings());
    }

    /** Without a picture field the event is text-only (null file name). */
    private TestResult test_parsePicture_absentIsNull() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent(""), report);
        TestResult r = assertNotNull("event must parse", def);
        if (!r.passed()) return r;
        return assertNull("absent picture field must yield null", def.getPictureFileName());
    }

    /** Traversal value: ERROR recorded, field ignored — but the event MUST stay loaded. */
    private TestResult test_parsePicture_traversalError_eventKept() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent("\"picture\":\"../evil.png\""), report);
        TestResult r = assertNotNull("event must be KEPT despite the bad picture value", def);
        if (!r.passed()) return r;
        r = assertNull("bad picture value must be ignored", def.getPictureFileName());
        if (!r.passed()) return r;
        return assertEquals("exactly one ERROR expected: " + report, 1, report.errorCount());
    }

    /** Wrong JSON type: ERROR recorded, field ignored — event stays loaded. */
    private TestResult test_parsePicture_wrongTypeError_eventKept() {
        ValidationReport report = new ValidationReport();
        NewsEventDefinition def = parseEvent(minimalEvent("\"picture\":5"), report);
        TestResult r = assertNotNull("event must be KEPT despite the wrong-type picture", def);
        if (!r.passed()) return r;
        r = assertNull("wrong-type picture value must be ignored", def.getPictureFileName());
        if (!r.passed()) return r;
        return assertEquals("exactly one ERROR expected: " + report, 1, report.errorCount());
    }

    // ========================================================================
    // Defaults extraction + generator
    // ========================================================================

    /** Empty folder → one valid picture per shipped default event, all caps respected. */
    private TestResult test_defaultsExtraction_emptyFolder_createsAll() {
        Path dir = null;
        try {
            dir = createTempDir();
            Path picturesDir = dir.resolve("pictures");

            TestResult r = assertTrue("extraction must report that it wrote defaults",
                    DefaultNewsPictures.extractDefaultsIfEmpty(picturesDir));
            if (!r.passed()) return r;

            for (String eventId : DefaultNewsEvents.DEFAULT_EVENT_IDS) {
                Path file = picturesDir.resolve(eventId + ".png");
                if (!Files.exists(file)) {
                    return fail("default picture missing: " + file.getFileName());
                }
                byte[] bytes = Files.readAllBytes(file);
                if (bytes.length > NewsPictureLibrary.MAX_FILE_BYTES) {
                    return fail(eventId + ".png is " + bytes.length
                            + " bytes — exceeds the 128 KiB cap");
                }
                PngHeader header = PngHeader.parse(bytes);
                if (header == null) {
                    return fail(eventId + ".png does not pass PngHeader validation");
                }
                if (header.getWidth() < NewsPictureLibrary.MIN_DIMENSION
                        || header.getWidth() > NewsPictureLibrary.MAX_DIMENSION
                        || header.getHeight() < NewsPictureLibrary.MIN_DIMENSION
                        || header.getHeight() > NewsPictureLibrary.MAX_DIMENSION) {
                    return fail(eventId + ".png is " + header.getWidth() + "x"
                            + header.getHeight() + " — outside the 16..512 caps");
                }
            }
            return pass("all " + DefaultNewsEvents.DEFAULT_EVENT_IDS.size()
                    + " default pictures created, valid and within caps");
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** A folder already containing a .png must be left completely untouched. */
    private TestResult test_defaultsExtraction_nonEmptyFolder_untouched() {
        Path dir = null;
        try {
            dir = createTempDir();
            Path picturesDir = dir.resolve("pictures");
            Files.createDirectories(picturesDir);
            Files.write(picturesDir.resolve("custom.png"), validPng(32, 32));

            TestResult r = assertFalse("extraction must not write into a non-empty folder",
                    DefaultNewsPictures.extractDefaultsIfEmpty(picturesDir));
            if (!r.passed()) return r;
            try (Stream<Path> files = Files.list(picturesDir)) {
                return assertEquals("folder must still contain exactly the one admin file",
                        1L, files.count());
            }
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** Every shipped default event must reference its own <eventId>.png picture. */
    private TestResult test_defaults_referencedByDefaultEventsJson() {
        JsonObject root = JsonUtilities.fromString(
                DefaultNewsEvents.getDefaultEventsJson()).getAsJsonObject();
        JsonArray events = root.getAsJsonArray("events");
        TestResult r = assertEquals("defaults JSON must contain all default events",
                DefaultNewsEvents.DEFAULT_EVENT_IDS.size(), events.size());
        if (!r.passed()) return r;

        Set<String> expectedIds = new HashSet<>(DefaultNewsEvents.DEFAULT_EVENT_IDS);
        for (int i = 0; i < events.size(); i++) {
            JsonObject event = events.get(i).getAsJsonObject();
            String id = event.get("id").getAsString();
            if (!expectedIds.remove(id)) {
                return fail("unexpected/duplicate event id in defaults JSON: " + id);
            }
            if (!event.has("picture")) {
                return fail("default event '" + id + "' has no picture reference");
            }
            String picture = event.get("picture").getAsString();
            if (!(id + ".png").equals(picture)) {
                return fail("default event '" + id + "' must reference '" + id
                        + ".png', got: " + picture);
            }
            if (NewsPictureLibrary.validatePictureFileName(picture) != null) {
                return fail("default picture reference is not traversal-safe: " + picture);
            }
        }
        return assertTrue("all default event ids must appear in the JSON, missing: "
                + expectedIds, expectedIds.isEmpty());
    }

    /** The procedural generator must be fully deterministic per event id. */
    private TestResult test_generator_isDeterministic() {
        byte[] first = DefaultNewsPictures.generatePlaceholder("diamond_rush");
        byte[] second = DefaultNewsPictures.generatePlaceholder("diamond_rush");
        TestResult r = assertTrue("same id must generate byte-identical PNGs",
                Arrays.equals(first, second));
        if (!r.passed()) return r;
        byte[] other = DefaultNewsPictures.generatePlaceholder("ore_market_crash");
        return assertFalse("different ids must generate different PNGs",
                Arrays.equals(first, other));
    }

    // ========================================================================
    // NewsEventLibrary reload integration
    // ========================================================================

    /** Referencing a missing picture warns, but the event still loads (picture-less). */
    private TestResult test_libraryReload_missingPictureWarns_eventLoads() {
        Path dir = null;
        try {
            dir = createTempDir();
            Files.writeString(dir.resolve("events.json"),
                    "{\"events\":[" + minimalEvent("\"picture\":\"missing.png\"") + "]}");

            NewsEventLibrary library = new NewsEventLibrary();
            ValidationReport report = library.reload(dir);

            TestResult r = assertNotNull("event must load despite the missing picture",
                    library.getDefinition("test_event"));
            if (!r.passed()) return r;
            r = assertEquals("the picture file name must still be stored (resolved at publish)",
                    "missing.png", library.getDefinition("test_event").getPictureFileName());
            if (!r.passed()) return r;
            r = assertFalse("a missing picture must not be an ERROR: " + report,
                    report.hasErrors());
            if (!r.passed()) return r;
            boolean warned = report.getWarnings().stream()
                    .anyMatch(e -> e.message().contains("missing.png"));
            r = assertTrue("a WARNING naming missing.png is expected: " + report, warned);
            if (!r.passed()) return r;
            // The empty pictures folder must have been populated with the defaults.
            return assertEquals("defaults extraction must have filled the picture library",
                    DefaultNewsEvents.DEFAULT_EVENT_IDS.size(), library.getPictureLibrary().size());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /** An existing referenced picture resolves without warnings, defaults stay away. */
    private TestResult test_libraryReload_resolvesExistingPicture() {
        Path dir = null;
        try {
            dir = createTempDir();
            Path picturesDir = dir.resolve(NewsPictureLibrary.PICTURES_DIR_NAME);
            Files.createDirectories(picturesDir);
            byte[] png = validPng(64, 64);
            Files.write(picturesDir.resolve("pic.png"), png);
            Files.writeString(dir.resolve("events.json"),
                    "{\"events\":[" + minimalEvent("\"picture\":\"pic.png\"") + "]}");

            NewsEventLibrary library = new NewsEventLibrary();
            ValidationReport report = library.reload(dir);

            TestResult r = assertFalse("clean setup must produce no problems: " + report,
                    report.hasErrors() || report.hasWarnings());
            if (!r.passed()) return r;
            NewsPictureLibrary.Entry entry = library.getPictureLibrary().get(
                    library.getDefinition("test_event").getPictureFileName());
            r = assertNotNull("referenced picture must resolve through the library", entry);
            if (!r.passed()) return r;
            r = assertTrue("resolved entry must carry the file's hash",
                    Arrays.equals(NewsPictureLibrary.sha1(png), entry.getSha1()));
            if (!r.passed()) return r;
            // Folder already contained a .png → defaults must NOT have been extracted.
            return assertEquals("non-empty pictures folder must stay untouched",
                    1, library.getPictureLibrary().size());
        } catch (IOException e) {
            return fail("temp dir setup failed: " + e);
        } finally {
            deleteRecursively(dir);
        }
    }
}
