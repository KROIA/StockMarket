package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.NbtCompat;
import net.kroia.modutilities.persistence.DataMigration;
import net.kroia.testkit.TestCategory;
import net.kroia.testkit.TestResult;
import net.kroia.testkit.TestSuite;
import net.kroia.stockmarket.data.DatabaseManager;
import net.kroia.stockmarket.data.StockMarketDataMigration;
import net.kroia.stockmarket.news.NewsHistory;
import net.kroia.stockmarket.news.NewsRecord;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * SM-325: the StockMarket save-data version ({@link StockMarketDataMigration}) on throw-away world folders:
 * a pre-2.2.0 layout is converted by step 1 (database columns, settings.json, villager file, news history) and a
 * second run changes nothing; a fresh world only gets the marker; a newer save is refused untouched.
 */
public class DataMigrationTestSuite extends TestSuite {

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.DATA_MIGRATION;
    }

    @Override
    public boolean needsGame() {
        return false; // file level only; runs offline in gradlew :common:test
    }

    @Override
    public void registerTests() {
        addTest("v0_save_step1_converts_and_reruns", this::test_v0Save);
        addTest("half_life_marker_keeps_10", this::test_halfLifeMarkerKeeps10);
        addTest("fresh_world_marker_only", this::test_freshWorld);
        addTest("newer_save_refused_untouched", this::test_newerSave);
    }

    private static final String OLD_SETTINGS =
            "{\"Utilities\":{\"SAVE_INTERVAL_MINUTES\":5,\"SYNC_ADMIN_FLAG_WITH_PERMISSION\":true},"
                    + "\"ServerMarket\":{\"NET_FLOW_HALF_LIFE_MINUTES\":10.0}}";

    /** A pre-2.2.0 world: every file-level item of step 1 converted, then step 1 again changes nothing. */
    private TestResult test_v0Save() {
        Path world = null;
        try {
            world = Files.createTempDirectory("sm_data_migration");
            Path sm = world.resolve("data/StockMarket");
            Files.createDirectories(sm);
            Files.writeString(sm.resolve("settings.json"), OLD_SETTINGS);
            Path db = sm.resolve("Database/stockdata.db");
            writeLegacyDatabase(db);
            CompoundTag villager = new CompoundTag();
            CompoundTag villagerData = new CompoundTag();
            villagerData.putInt("probe", 42);
            villager.put("data", villagerData);
            NbtIo.writeCompressed(villager, world.resolve("data/stockmarket_villager_trades.dat"));
            writeLegacyNews(sm.resolve("News/history.nbt"), 3);
            // leftover of a crashed earlier conversion, must be ignored
            Files.createDirectories(sm.resolve("News/history.migrating"));
            Files.writeString(sm.resolve("News/history.migrating/000.nbt"), "partial");

            StockMarketDataMigration.migrate(world);

            TestResult r = assertEquals("marker at data version 1", 1, markerVersion(sm));
            if (!r.passed()) return r;
            List<Path> zips = backups(sm);
            r = assertEquals("one backup zip", 1, zips.size());
            if (!r.passed()) return r;
            try (ZipFile zip = new ZipFile(zips.get(0).toFile())) {
                r = assertTrue("backup holds the database", zip.getEntry("data/StockMarket/Database/stockdata.db") != null);
                if (!r.passed()) return r;
                r = assertTrue("backup holds the old villager file", zip.getEntry("data/stockmarket_villager_trades.dat") != null);
                if (!r.passed()) return r;
            }
            r = checkConverted(world, sm, db);
            if (!r.passed()) return r;

            String settingsBefore = Files.readString(sm.resolve("settings.json"));
            StockMarketDataMigration.step1(world); // as after a crash before the marker write
            r = assertEquals("second run leaves settings.json unchanged", settingsBefore, Files.readString(sm.resolve("settings.json")));
            if (!r.passed()) return r;
            r = checkConverted(world, sm, db);
            if (!r.passed()) return r;
            StockMarketDataMigration.migrate(world);
            return assertEquals("migrate at the current version makes no backup", 1, backups(sm).size());
        } catch (Exception e) {
            return fail("Exception: " + e);
        } finally {
            deleteRecursive(world);
        }
    }

    /** Checks every file-level conversion of step 1. */
    private TestResult checkConverted(Path world, Path sm, Path db) throws Exception {
        JsonObject settings = JsonParser.parseString(Files.readString(sm.resolve("settings.json"))).getAsJsonObject();
        TestResult r = assertTrue("SYNC_ADMIN_FLAG_WITH_PERMISSION moved to Integrations",
                settings.getAsJsonObject("Integrations").get("SYNC_ADMIN_FLAG_WITH_PERMISSION").getAsBoolean());
        if (!r.passed()) return r;
        r = assertFalse("old Utilities key removed", settings.getAsJsonObject("Utilities").has("SYNC_ADMIN_FLAG_WITH_PERMISSION"));
        if (!r.passed()) return r;
        r = assertEquals("half-life 10 -> 360", 360f, settings.getAsJsonObject("ServerMarket").get("NET_FLOW_HALF_LIFE_MINUTES").getAsFloat());
        if (!r.passed()) return r;

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            Set<String> price = columns(c, "MarketPrice");
            Set<String> orders = columns(c, "OrderHistory");
            r = assertTrue("MarketPrice has traded_volume and game_time: " + price,
                    price.containsAll(List.of("traded_volume", "game_time")));
            if (!r.passed()) return r;
            r = assertTrue("OrderHistory has the new columns: " + orders, orders.containsAll(List.of("intermarket_group_one",
                    "intermarket_group_two", "order_id", "finish_time", "limit_price", "target_volume", "final_status")));
            if (!r.passed()) return r;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM MarketPrice")) {
                r = assertEquals("old candle row kept", 1, rs.next() ? rs.getInt(1) : -1);
                if (!r.passed()) return r;
            }
        }

        r = assertFalse("old villager file removed", Files.exists(world.resolve("data/stockmarket_villager_trades.dat")));
        if (!r.passed()) return r;
        CompoundTag villager = NbtIo.readCompressed(sm.resolve("villager_trades.nbt"), NbtAccounter.unlimitedHeap());
        r = assertEquals("villager data moved", 42, NbtCompat.getInt(villager, "probe"));
        if (!r.passed()) return r;

        r = assertFalse("history.nbt removed", Files.exists(sm.resolve("News/history.nbt")));
        if (!r.passed()) return r;
        r = assertFalse("staging folder gone", Files.exists(sm.resolve("News/history.migrating")));
        if (!r.passed()) return r;
        NewsHistory history = new NewsHistory();
        history.setDirectory(sm.resolve("News/history"), null);
        return assertEquals("news records in chunks", 3, history.size());
    }

    /** T-208: a save that already ran the old runtime half-life migration keeps a 10 set later. */
    private TestResult test_halfLifeMarkerKeeps10() {
        Path world = null;
        try {
            world = Files.createTempDirectory("sm_data_migration");
            Path sm = world.resolve("data/StockMarket");
            Files.createDirectories(sm);
            Files.writeString(sm.resolve("settings.json"), OLD_SETTINGS);
            Files.writeString(sm.resolve(StockMarketDataMigration.NET_FLOW_HALF_LIFE_MARKER), "x");
            StockMarketDataMigration.migrate(world);
            JsonObject settings = JsonParser.parseString(Files.readString(sm.resolve("settings.json"))).getAsJsonObject();
            return assertEquals("half-life kept", 10f, settings.getAsJsonObject("ServerMarket").get("NET_FLOW_HALF_LIFE_MINUTES").getAsFloat());
        } catch (Exception e) {
            return fail("Exception: " + e);
        } finally {
            deleteRecursive(world);
        }
    }

    /** No StockMarket data: the current version is written, no step and no backup. */
    private TestResult test_freshWorld() {
        Path world = null;
        try {
            world = Files.createTempDirectory("sm_data_migration");
            Files.createDirectories(world.resolve("data"));
            StockMarketDataMigration.migrate(world);
            Path sm = world.resolve("data/StockMarket");
            TestResult r = assertEquals("marker at data version 1", 1, markerVersion(sm));
            if (!r.passed()) return r;
            r = assertEquals("no backup", 0, backups(sm).size());
            if (!r.passed()) return r;
            try (Stream<Path> files = Files.list(sm)) {
                return assertEquals("only the marker", 1L, files.count());
            }
        } catch (Exception e) {
            return fail("Exception: " + e);
        } finally {
            deleteRecursive(world);
        }
    }

    /** A save from a newer StockMarket throws and is not changed. */
    private TestResult test_newerSave() {
        Path world = null;
        try {
            world = Files.createTempDirectory("sm_data_migration");
            Path sm = world.resolve("data/StockMarket");
            Files.createDirectories(sm);
            Files.writeString(sm.resolve("settings.json"), OLD_SETTINGS);
            Files.writeString(sm.resolve(StockMarketDataMigration.MARKER_FILE), "{\"dataVersion\":99,\"modVersion\":\"9.9.9\"}");
            try {
                StockMarketDataMigration.migrate(world);
                return fail("no exception for data version 99");
            } catch (DataMigration.DataVersionTooNewException e) {
                TestResult r = assertEquals("saved version in the exception", 99, e.savedVersion);
                if (!r.passed()) return r;
            }
            TestResult r = assertEquals("settings.json untouched", OLD_SETTINGS, Files.readString(sm.resolve("settings.json")));
            if (!r.passed()) return r;
            return assertEquals("no backup", 0, backups(sm).size());
        } catch (Exception e) {
            return fail("Exception: " + e);
        } finally {
            deleteRecursive(world);
        }
    }

    // --- helpers ---

    /** Writes stockdata.db with the tables of an old release (none of the later columns) and one row each. */
    private static void writeLegacyDatabase(Path db) throws Exception {
        Files.createDirectories(db.getParent());
        if (!DatabaseManager.registerDriver())
            throw new IllegalStateException("SQLite driver missing");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath()); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE MarketPrice (id INTEGER PRIMARY KEY AUTOINCREMENT, marketid INTEGER NOT NULL, "
                    + "open INTEGER NOT NULL, low INTEGER NOT NULL, high INTEGER NOT NULL, time INTEGER NOT NULL)");
            st.execute("INSERT INTO MarketPrice (marketid, open, low, high, time) VALUES (7, 100, 90, 110, 1000)");
            st.execute("CREATE TABLE OrderHistory (id INTEGER PRIMARY KEY AUTOINCREMENT, itemid INTEGER NOT NULL, "
                    + "accountid INTEGER NOT NULL, userid_one INTEGER NOT NULL, userid_two INTEGER NOT NULL, "
                    + "type INTEGER NOT NULL, amount INTEGER NOT NULL, price INTEGER NOT NULL, time DATETIME NOT NULL)");
        }
    }

    /** Writes a pre-T-110 single-file news history with {@code count} records. */
    private static void writeLegacyNews(Path file, int count) throws Exception {
        ListTag list = new ListTag();
        for (long uid = 1; uid <= count; uid++) {
            NewsRecord record = new NewsRecord(uid, "event_" + uid, 1_720_000_000_000L + uid, uid,
                    Map.of("en_us", "Headline " + uid), Map.of("en_us", "Text " + uid),
                    List.of(new NewsRecord.AffectedMarket(new ItemID((short) 3), "minecraft:diamond", 1.0f)),
                    "shock", 0.25f, "ramp", 60);
            CompoundTag tag = new CompoundTag();
            record.save(tag);
            list.add(tag);
        }
        CompoundTag root = new CompoundTag();
        root.put("records", list);
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(root, file);
    }

    private static Set<String> columns(Connection c, String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next())
                names.add(rs.getString("name"));
        }
        return names;
    }

    private static int markerVersion(Path sm) throws Exception {
        return JsonParser.parseString(Files.readString(sm.resolve(StockMarketDataMigration.MARKER_FILE)))
                .getAsJsonObject().get("dataVersion").getAsInt();
    }

    private static List<Path> backups(Path sm) throws Exception {
        if (!Files.isDirectory(sm))
            return List.of();
        try (Stream<Path> files = Files.list(sm)) {
            return files.filter(p -> p.getFileName().toString().startsWith("stockmarket-data-v")
                    && p.getFileName().toString().endsWith(".zip")).toList();
        }
    }

    private static void deleteRecursive(Path dir) {
        if (dir == null) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {
        }
    }
}
