package net.kroia.stockmarket.minecraft.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.architectury.platform.Platform;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.SharedConstants;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * SM-363: item icons of the web interface ({@link StockMarketWebInterface}).
 * <p>
 * An icon is the item's texture PNG: {@code textures/item/<path>.png}, else {@code textures/block/<path>.png}, else
 * the texture its item model names (parents followed, {@code #references} resolved). Every resource is looked up in
 * the texture packs first, then on the class path (mod jars; in singleplayer also the game's own textures). A pack
 * is any {@code .jar} / {@code .zip} in {@code config/StockMarket/web/}: a Minecraft client jar or a resource pack an
 * admin put there, or the client jar of the opt-in download ({@code Integrations.WEB_INTERFACE_DOWNLOAD_TEXTURES}).
 * A dedicated server has no game textures of its own, so vanilla items need such a pack.
 * <p>
 * Security: every resource name is built from {@link #RESOURCE_LOCATION}-checked parts (no "..", no absolute path),
 * model JSON is only read here, and only {@code assets/<ns>/textures/**.png} files with a PNG signature are returned.
 * Threading: lookups run on the HTTP threads and never touch the game state; the download runs on its own thread.
 */
public final class WebIconTextures {

    /** Pack folder below the config folder. */
    public static final String PACK_DIR = "StockMarket/web";
    private static final int MAX_RESOURCE_BYTES = 512 * 1024;
    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
    private static final long MAX_CLIENT_JAR_BYTES = 128L * 1024 * 1024;
    private static final int MAX_MODEL_DEPTH = 8;
    private static final int ICON_CACHE_MAX = 2048;
    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    /** A vanilla texture: present on the class path means the game's textures are there (singleplayer). */
    private static final String VANILLA_PROBE = "assets/minecraft/textures/item/diamond.png";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final long DOWNLOAD_DEADLINE_MS = TimeUnit.MINUTES.toMillis(10);
    /** Texture keys tried in this order: the item layer, the faces a block icon shows most, then the particle. */
    private static final String[] TEXTURE_KEYS = {"layer0", "all", "side", "texture", "top", "end", "front", "wall", "cross", "particle"};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    /** {@code [namespace:]path}: lowercase segments, none starting with '.', so no ".." and no absolute path. */
    private static final Pattern RESOURCE_LOCATION = Pattern.compile(
            "(?:([a-z0-9_-][a-z0-9_.-]*):)?([a-z0-9_-][a-z0-9_.-]*(?:/[a-z0-9_-][a-z0-9_.-]*)*)");
    private static final Pattern SHA1 = Pattern.compile("[0-9a-fA-F]{40}");

    /** item -> PNG, empty = no icon. Cleared when the packs change and when it grows past {@link #ICON_CACHE_MAX}. */
    private static final Map<String, byte[]> ICONS = new ConcurrentHashMap<>();
    /** Counts pack changes, so a lookup that ran across a change is not cached. */
    private static final AtomicInteger PACK_GENERATION = new AtomicInteger();
    private static final AtomicBoolean DOWNLOAD_RUNNING = new AtomicBoolean();
    private static volatile boolean downloadDone;
    private static volatile List<ZipFile> packs = List.of();

    private WebIconTextures() {}

    /**
     * A file to download: URL, SHA-1 (40 lowercase hex digits) and size (-1 = unknown). The download only uses the ones
     * {@link #versionJson} and {@link #clientJar} built, which accept only https URLs on Mojang hosts.
     */
    public record Download(String url, String sha1, long size) {}

    // ------------------------------------------------------------------ icons

    /**
     * The icon of an item, cached.
     *
     * @param item a registry name accepted by {@link StockMarketWebInterface#iconResourcePaths}
     * @return the PNG, or null if there is none
     */
    static byte @Nullable [] icon(String item) {
        byte[] png = ICONS.get(item);
        if (png == null) {
            int generation = PACK_GENERATION.get();
            png = findIcon(item);
            if (png == null)
                png = new byte[0];
            if (ICONS.size() > ICON_CACHE_MAX)
                ICONS.clear();
            if (generation == PACK_GENERATION.get())
                ICONS.put(item, png);
        }
        return png.length == 0 ? null : png;
    }

    /**
     * Uncached icon lookup: the item texture, the block texture of the same name, then the item model's texture.
     *
     * @param item item registry name, e.g. {@code minecraft:oak_stairs}
     * @return the PNG, or null if none was found or the name is not allowed
     */
    public static byte @Nullable [] findIcon(String item) {
        List<String> paths = StockMarketWebInterface.iconResourcePaths(item);
        if (paths == null)
            return null;
        for (String path : paths) {
            byte[] png = png(read(path));
            if (png != null)
                return png;
        }
        String path = textureResourcePath(modelTexture(item));
        return path == null ? null : png(read(path));
    }

    /**
     * Resource name of a texture reference as used in model files.
     *
     * @param texture e.g. {@code minecraft:block/stone}; without a namespace {@code minecraft}
     * @return e.g. {@code assets/minecraft/textures/block/stone.png}, or null if the reference is not allowed
     */
    public static @Nullable String textureResourcePath(@Nullable String texture) {
        return resourcePath(texture, "textures/", ".png");
    }

    private static @Nullable String resourcePath(@Nullable String location, String folder, String extension) {
        if (location == null)
            return null;
        Matcher m = RESOURCE_LOCATION.matcher(location);
        if (!m.matches())
            return null;
        return "assets/" + (m.group(1) == null ? "minecraft" : m.group(1)) + "/" + folder + m.group(2) + extension;
    }

    /**
     * The texture an item model shows: {@code models/item/<path>.json} and its parents (at most
     * {@link #MAX_MODEL_DEPTH}), a child's textures winning; the first of {@link #TEXTURE_KEYS} that resolves, else
     * any texture of the model.
     *
     * @param item item registry name ({@code namespace:path})
     * @return a texture reference such as {@code minecraft:block/oak_planks}, or null
     */
    private static @Nullable String modelTexture(String item) {
        int colon = item.indexOf(':');
        String model = item.substring(0, colon) + ":item/" + item.substring(colon + 1);
        Map<String, String> textures = new LinkedHashMap<>();
        for (int depth = 0; model != null && depth < MAX_MODEL_DEPTH; depth++) {
            String path = resourcePath(model, "models/", ".json");
            byte[] json = path == null ? null : read(path);
            if (json == null)
                break;
            JsonObject o;
            try {
                o = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException e) {
                break; // not a JSON object
            }
            if (o.get("textures") instanceof JsonObject t)
                for (Map.Entry<String, JsonElement> e : t.entrySet())
                    if (e.getValue() instanceof JsonPrimitive p && p.isString())
                        textures.putIfAbsent(e.getKey(), p.getAsString());
            model = o.get("parent") instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
        }
        List<String> keys = new ArrayList<>(List.of(TEXTURE_KEYS));
        keys.addAll(textures.keySet());
        for (String key : keys) {
            String value = textures.get(key);
            for (int i = 0; value != null && value.startsWith("#") && i < MAX_MODEL_DEPTH; i++)
                value = textures.get(value.substring(1));
            if (value != null && !value.startsWith("#"))
                return value;
        }
        return null;
    }

    /** @return {@code bytes} if they start with the PNG signature, else null */
    private static byte @Nullable [] png(byte @Nullable [] bytes) {
        return bytes != null && bytes.length > PNG_SIGNATURE.length
                && Arrays.equals(bytes, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length) ? bytes : null;
    }

    /** A resource from the packs (in order), else the class path; null if missing, unreadable or too large. */
    private static byte @Nullable [] read(String path) {
        for (ZipFile pack : packs) {
            try {
                ZipEntry entry = pack.getEntry(path);
                if (entry != null && !entry.isDirectory())
                    try (InputStream in = pack.getInputStream(entry)) {
                        return capped(in);
                    }
            } catch (IOException | RuntimeException e) {
                // closed or broken pack: try the next source
            }
        }
        try (InputStream in = WebIconTextures.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : capped(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte @Nullable [] capped(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(MAX_RESOURCE_BYTES + 1);
        return bytes.length > MAX_RESOURCE_BYTES ? null : bytes;
    }

    // ------------------------------------------------------------------ packs

    /** @return {@code config/StockMarket/web/} */
    public static Path packDir() {
        return Platform.getConfigFolder().resolve(PACK_DIR);
    }

    /** Opens the packs of {@link #packDir()} (creating the folder); called when the web interface starts. Never throws. */
    static void openPacks() {
        Path dir = packDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            StockMarketMod.LOGGER.warn("[StockMarket Web] could not create {}: {}", dir, e.toString());
        }
        usePacks(dir);
    }

    /** Closes the packs and empties the icon cache; called when the web interface stops. */
    static void closePacks() {
        usePacks(null);
    }

    /** Opens the packs again if the web interface runs, else closes them (after a download, or a test's own packs). */
    public static void reopenPacks() {
        if (StockMarketWebInterface.getRunningPort() != -1)
            openPacks();
        else
            closePacks();
    }

    /**
     * Replaces the open packs with every {@code .jar} / {@code .zip} file in {@code dir} (file name order) and
     * empties the icon cache. Unreadable files are skipped with a warning. Never throws.
     *
     * @param dir the pack folder, or null for no packs
     */
    public static synchronized void usePacks(@Nullable Path dir) {
        List<ZipFile> opened = new ArrayList<>();
        if (dir != null && Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(WebIconTextures::isPackFile).sorted().toList()) {
                    try {
                        opened.add(new ZipFile(file.toFile()));
                    } catch (IOException e) {
                        StockMarketMod.LOGGER.warn("[StockMarket Web] skipped texture pack {}: {}", file.getFileName(), e.toString());
                    }
                }
            } catch (IOException e) {
                StockMarketMod.LOGGER.warn("[StockMarket Web] could not list {}: {}", dir, e.toString());
            }
        }
        List<ZipFile> old = packs;
        packs = List.copyOf(opened);
        PACK_GENERATION.incrementAndGet();
        ICONS.clear();
        for (ZipFile pack : old) {
            try {
                pack.close();
            } catch (IOException ignored) {
                // closing a read-only file
            }
        }
        if (!opened.isEmpty())
            StockMarketMod.LOGGER.info("[StockMarket Web] {} texture pack(s) in {}", opened.size(), dir);
    }

    private static boolean isPackFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.endsWith(".jar") || name.endsWith(".zip")) && Files.isRegularFile(file);
    }

    // ------------------------------------------------------------------ download

    /**
     * Starts the client jar download in the background unless it already succeeded or runs. Called by
     * {@link StockMarketWebInterface#apply} on the master while the setting is on, so a failed attempt is retried at
     * the next server start or settings change. Never blocks, never throws.
     */
    static void startDownload() {
        if (downloadDone || !DOWNLOAD_RUNNING.compareAndSet(false, true))
            return;
        Thread thread = new Thread(() -> {
            try {
                downloadDone = download();
            } finally {
                DOWNLOAD_RUNNING.set(false);
            }
        }, "StockMarket-Web-Textures");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Downloads the client jar of the running Minecraft version from Mojang into {@link #packDir()}: version
     * manifest, then the version JSON (SHA-1 checked), then the jar (size and SHA-1 checked, written to a
     * {@code .part} file first). A stored jar whose SHA-1 matches is kept. Not needed (and skipped) when the game's
     * textures are on the class path. Failures only log.
     *
     * @return true when done (jar present and current, or not needed)
     */
    private static boolean download() {
        String version = SharedConstants.getCurrentVersion().getName(); // mc<=1.21.5
        //String version = SharedConstants.getCurrentVersion().name(); // mc>=1.21.6
        Path jar = packDir().resolve("minecraft-client-" + version + ".jar");
        Path part = jar.resolveSibling(jar.getFileName() + ".part");
        try {
            if (WebIconTextures.class.getClassLoader().getResource(VANILLA_PROBE) != null) {
                StockMarketMod.LOGGER.info("[StockMarket Web] the game's textures are available; no client download needed");
                return true;
            }
            HttpClient http = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
            Download versionJson = versionJson(json(fetch(http, MANIFEST_URL)), version);
            if (versionJson == null)
                throw new IOException("Minecraft " + version + " is not in Mojang's version manifest");
            byte[] versionBytes = fetch(http, versionJson.url());
            if (!hex(sha1Digest().digest(versionBytes)).equals(versionJson.sha1()))
                throw new IOException("SHA-1 mismatch of the version file");
            Download client = clientJar(json(versionBytes));
            if (client == null)
                throw new IOException("no valid client download in the version file");
            if (Files.isRegularFile(jar) && sha1(jar).equals(client.sha1())) {
                StockMarketMod.LOGGER.info("[StockMarket Web] {} is up to date", jar.getFileName());
                return true;
            }
            Files.createDirectories(jar.getParent());
            StockMarketMod.LOGGER.info("[StockMarket Web] downloading the Minecraft {} client ({} MB) from Mojang for the item icons",
                    version, client.size() / (1024 * 1024));
            downloadTo(http, client, part);
            synchronized (WebIconTextures.class) {
                usePacks(null); // the old jar may be open as a pack, and Windows can't replace an open file
                try {
                    Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    reopenPacks();
                }
            }
            StockMarketMod.LOGGER.info("[StockMarket Web] stored {}", jar);
            return true;
        } catch (IOException | RuntimeException e) {
            StockMarketMod.LOGGER.warn("[StockMarket Web] item texture download failed: {}; vanilla items keep their initials", e.toString());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // left for the next attempt, which overwrites it
            }
        }
    }

    /**
     * The manifest entry of a version.
     *
     * @param manifest Mojang's {@code version_manifest_v2.json}
     * @param version  e.g. {@code 1.21.1}
     * @return its version JSON as a download (size unknown), or null if missing or not an https URL on a Mojang host
     */
    public static @Nullable Download versionJson(JsonObject manifest, String version) {
        if (!(manifest.get("versions") instanceof JsonArray versions))
            return null;
        for (JsonElement v : versions)
            if (v instanceof JsonObject o && version.equals(string(o, "id")))
                return download(string(o, "url"), string(o, "sha1"), -1);
        return null;
    }

    /**
     * The client jar of a version JSON ({@code downloads.client}).
     *
     * @return the download, or null if missing, not an https URL on a Mojang host, without a valid SHA-1, or not
     *         1 byte to {@link #MAX_CLIENT_JAR_BYTES} large
     */
    public static @Nullable Download clientJar(JsonObject versionJson) {
        if (!(versionJson.get("downloads") instanceof JsonObject downloads) || !(downloads.get("client") instanceof JsonObject client))
            return null;
        long size = client.get("size") instanceof JsonPrimitive p && p.isNumber() ? p.getAsLong() : -1;
        if (size <= 0 || size > MAX_CLIENT_JAR_BYTES)
            return null;
        return download(string(client, "url"), string(client, "sha1"), size);
    }

    private static @Nullable Download download(@Nullable String url, @Nullable String sha1, long size) {
        if (url == null || sha1 == null || !SHA1.matcher(sha1).matches())
            return null;
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(uri.getScheme()) || !(host.equals("mojang.com") || host.endsWith(".mojang.com")))
            return null;
        return new Download(url, sha1.toLowerCase(Locale.ROOT), size);
    }

    /**
     * Streams a download into {@code target}, checking its size against {@link Download#size()} while reading and its
     * SHA-1 at the end, within {@link #DOWNLOAD_DEADLINE_MS}.
     *
     * @throws IOException on an HTTP error, a size or SHA-1 mismatch or a timeout ({@code target} may be left behind)
     */
    public static void downloadTo(HttpClient http, Download d, Path target) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = http.send(request(d.url()), HttpResponse.BodyHandlers.ofInputStream());
        MessageDigest sha1 = sha1Digest();
        // ponytail: the deadline is checked between reads; a read that stalls forever keeps the (daemon) thread and blocks
        // retries until the server restarts. Add a watchdog if that is ever seen.
        long deadline = System.currentTimeMillis() + DOWNLOAD_DEADLINE_MS;
        long total = 0;
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            if (response.statusCode() != 200)
                throw new IOException("HTTP " + response.statusCode());
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) != -1; ) {
                total += n;
                if (total > d.size())
                    throw new IOException("larger than the announced " + d.size() + " bytes");
                if (System.currentTimeMillis() > deadline)
                    throw new IOException("took longer than " + TimeUnit.MILLISECONDS.toMinutes(DOWNLOAD_DEADLINE_MS) + " minutes");
                sha1.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        }
        if (total != d.size())
            throw new IOException("got " + total + " of " + d.size() + " bytes");
        if (!hex(sha1.digest()).equals(d.sha1()))
            throw new IOException("SHA-1 mismatch");
    }

    /** GETs a JSON file of at most {@link #MAX_JSON_BYTES}. */
    private static byte[] fetch(HttpClient http, String url) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = http.send(request(url), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() != 200)
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            byte[] bytes = in.readNBytes(MAX_JSON_BYTES + 1);
            if (bytes.length > MAX_JSON_BYTES)
                throw new IOException("larger than " + MAX_JSON_BYTES + " bytes: " + url);
            return bytes;
        }
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT).GET().build();
    }

    private static JsonObject json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static @Nullable String string(JsonObject o, String key) {
        return o.get(key) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    /** @return the SHA-1 of a file as 40 lowercase hex digits */
    public static String sha1(Path file) throws IOException {
        MessageDigest sha1 = sha1Digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) != -1; )
                sha1.update(buffer, 0, n);
        }
        return hex(sha1.digest());
    }

    private static MessageDigest sha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every JDK has SHA-1
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
