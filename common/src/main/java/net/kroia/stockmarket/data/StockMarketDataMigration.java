package net.kroia.stockmarket.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kroia.modutilities.persistence.DataMigration;
import net.kroia.modutilities.persistence.DataPersistence;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.news.NewsHistoryChunkStore;
import net.kroia.stockmarket.villagertrading.VillagerTradeManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SM-325: the data version of StockMarket's world data, upgraded with ModUtilities' {@link DataMigration} at
 * SERVER_BEFORE_START, before anything opens a StockMarket file (master and slave).
 * <ul>
 *   <li>Marker: {@code <world>/data/StockMarket/data_version.json}; the backup zip goes into {@link #BACKUP_DIR}.</li>
 *   <li>Data: everything under {@code <world>/data/StockMarket/} (databases, settings.json, NBT chunks, news,
 *       villager trades) plus the villager trade file of releases up to 2.1.x. {@code config/StockMarket/} is
 *       configuration and not versioned.</li>
 *   <li>Step 1 (2.2.0) converts saves of releases before 2.2.0. Released steps are never changed; a new step is
 *       appended with the next version.</li>
 * </ul>
 * Two conversions of step 1 need live plugin objects and run at load in {@code ServerPluginManager.load}
 * (NewsPlugin auto-create, NetFlow plugin split), guarded by their own NBT flags.
 */
public final class StockMarketDataMigration {
    /** Marker file name in {@link DataManager#BASE_PATH}. */
    public static final String MARKER_FILE = "data_version.json";

    /**
     * SM-355: backup zips go next to data/StockMarket, not into it (same layout as BankSystem's
     * {@code data/BankSystem_backups}): a backup never sits inside the data it backs up.
     */
    public static final Path BACKUP_DIR = Path.of("data", "StockMarket_backups");

    /** T-208: marker of the old runtime half-life migration; a save that has it already ran that change. */
    public static final String NET_FLOW_HALF_LIFE_MARKER = "NetFlowHalfLife360.marker";

    // settings.json names as written by releases before 2.2.0. Frozen: step 1 must keep reading old files.
    private static final String UTILITIES_GROUP = "Utilities";
    private static final String MARKET_GROUP = "ServerMarket";
    private static final String INTEGRATIONS_GROUP = "Integrations";
    private static final String SYNC_ADMIN_FLAG_KEY = "SYNC_ADMIN_FLAG_WITH_PERMISSION";
    private static final String HALF_LIFE_KEY = "NET_FLOW_HALF_LIFE_MINUTES";
    private static final float OLD_HALF_LIFE_MINUTES = 10f;
    private static final float NEW_HALF_LIFE_MINUTES = 360f;

    private StockMarketDataMigration() {}

    /**
     * Brings the StockMarket data of a world to the current data version (see the class Javadoc).
     *
     * @param worldRoot the world folder
     * @throws DataMigration.DataVersionTooNewException if the world was saved by a newer StockMarket
     * @throws IOException if the marker is unreadable, the backup fails or a step fails
     */
    public static void migrate(Path worldRoot) throws IOException {
        Path smDir = worldRoot.resolve(DataManager.BASE_PATH);
        List<Path> dataPaths = new ArrayList<>();
        dataPaths.add(smDir);
        dataPaths.addAll(VillagerTradeManager.legacyFiles(worldRoot));
        DataMigration.migrate(StockMarketMod.MOD_ID, StockMarketMod.VERSION, worldRoot, smDir.resolve(MARKER_FILE),
                worldRoot.resolve(BACKUP_DIR), dataPaths,
                List.of(new DataMigration.Step(1, "2.2.0", () -> step1(worldRoot))));
    }

    /**
     * The backup zips {@link DataMigration} wrote into {@code <world>/}{@link #BACKUP_DIR}
     * ({@code stockmarket-data-v<N>-backup-<stamp>.zip}). Compare the sets from before and after a failed
     * {@link #migrate} to find the zip that start made; DataMigration names it only in the exception message.
     *
     * @param worldRoot the world folder
     * @return the zip files, empty if none or the folder is unreadable
     */
    public static java.util.Set<Path> backupZips(Path worldRoot) {
        Path dir = worldRoot.resolve(BACKUP_DIR);
        java.util.Set<Path> zips = new java.util.HashSet<>();
        if (!Files.isDirectory(dir))
            return zips;
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith(StockMarketMod.MOD_ID + "-data-v") && n.contains("-backup-") && n.endsWith(".zip");
            }).forEach(zips::add);
        } catch (IOException ignored) {
        }
        return zips;
    }

    /**
     * Step 0 -> 1 (pre-2.2.0 -> 2.2.0), file level only. Every part is safe to run again on partly converted data.
     *
     * Public so tests can run it again on converted data.
     *
     * @param worldRoot the world folder
     * @throws Exception if a part fails; the save then stays at version 0 and the step runs again at the next start
     */
    public static void step1(Path worldRoot) throws Exception {
        Path smDir = worldRoot.resolve(DataManager.BASE_PATH);
        DatabaseManager.migrateSchema(worldRoot.resolve(DatabaseManager.DATABASE_PATH).resolve(DatabaseManager.FILE_NAME));
        migrateSettingsFile(smDir.resolve(DataManager.SETTINGS_FILE), smDir.resolve(NET_FLOW_HALF_LIFE_MARKER));
        VillagerTradeManager.migrateLegacyFile(worldRoot, smDir.resolve(VillagerTradeManager.DATA_FILE));
        Path news = smDir.resolve(DataManager.NEWS_FOLDER);
        NewsHistoryChunkStore.migrateLegacyFile(news.resolve(DataManager.NEWS_HISTORY_FOLDER),
                news.resolve(DataManager.NEWS_HISTORY_LEGACY_FILE));
    }

    /**
     * Step 1 part for settings.json, on the raw JSON (the old keys are no longer registered settings):
     * <ul>
     *   <li>T-217: {@code Utilities.SYNC_ADMIN_FLAG_WITH_PERMISSION} moves into a new {@code Integrations} group,
     *       only while the file has no Integrations group.</li>
     *   <li>T-208: {@code ServerMarket.NET_FLOW_HALF_LIFE_MINUTES} of exactly the old default 10 becomes 360,
     *       unless {@code halfLifeMarker} exists (the old runtime migration already ran, so a 10 there was
     *       chosen later and is kept).</li>
     * </ul>
     * Writes the file atomically and only when something changed. A missing file is skipped.
     *
     * @param settingsFile   {@code <world>/data/StockMarket/settings.json}
     * @param halfLifeMarker the T-208 marker file; left in place
     * @throws IOException if the file cannot be read, parsed or written
     */
    static void migrateSettingsFile(Path settingsFile, Path halfLifeMarker) throws IOException {
        if (!Files.isRegularFile(settingsFile))
            return;
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(settingsFile)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("unreadable settings file " + settingsFile, e);
        }
        boolean changed = false;
        if (!root.has(INTEGRATIONS_GROUP) && root.get(UTILITIES_GROUP) instanceof JsonObject utilities
                && utilities.has(SYNC_ADMIN_FLAG_KEY)) {
            JsonObject integrations = new JsonObject();
            integrations.add(SYNC_ADMIN_FLAG_KEY, utilities.remove(SYNC_ADMIN_FLAG_KEY));
            root.add(INTEGRATIONS_GROUP, integrations);
            changed = true;
        }
        if (!Files.exists(halfLifeMarker) && root.get(MARKET_GROUP) instanceof JsonObject market) {
            JsonElement v = market.get(HALF_LIFE_KEY);
            if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()
                    && Math.abs(v.getAsFloat() - OLD_HALF_LIFE_MINUTES) < 1e-3f) {
                market.addProperty(HALF_LIFE_KEY, NEW_HALF_LIFE_MINUTES);
                changed = true;
            }
        }
        if (changed)
            DataPersistence.writeJsonAtomically(settingsFile, new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }
}
