package net.kroia.stockmarket.testing.tests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.NbtCompat;
import net.kroia.modutilities.testing.TestCategory;
import net.kroia.modutilities.testing.TestRegistry;
import net.kroia.modutilities.testing.TestResult;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.data.DataManager;
import net.kroia.stockmarket.data.StockMarketDataMigration;
import net.kroia.stockmarket.pluginsystem.Plugins;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ServerPluginManager;
import net.kroia.stockmarket.pluginsystem.plugins.NetFlowPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin;
import net.kroia.stockmarket.testing.StockMarketTestCategories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * SM-328 (opt-in, run with {@code --only stockmarket/sm_upgrade_212 --world <2.1.2 world>}): checks the save-data
 * upgrade ({@link StockMarketDataMigration}) and the NetFlow split ({@code ServerPluginManager.migrateNetFlowPlugins})
 * on a real world saved by the last release. The "before" state is the backup zip the upgrade wrote before step 1:
 * its {@code data/StockMarket/MarketManager} and {@code PluginManager} folders are unpacked to a temp folder and read
 * with the same {@code readDataCompoundListMap} the server load uses. Nothing about the world is hard-coded.
 */
public class Upgrade212TestSuite extends TestSuite {
    private static final String TOPIC = "stockmarket/sm_upgrade_212";
    /** The data version StockMarketDataMigration's last step writes (step 1, 2.2.0). */
    private static final int CURRENT_DATA_VERSION = 1;

    /** The upgraded world's "before" data, read once from the backup zip. */
    private record Before(Map<String, ListTag> markets, Map<String, ListTag> plugins, Set<String> folders) {}
    private Before before;

    @Override
    public TestCategory getCategory() {
        return StockMarketTestCategories.UPGRADE_212;
    }

    @Override
    public void registerTests() {
        addTest("marker_and_one_backup", this::test_markerAndBackup);
        addTest("markets_kept", this::test_marketsKept);
        addTest("netflow_split_per_volatility", this::test_netFlowSplit);
        addTest("netflow_flag_saved_done", this::test_flagSaved);
    }

    // ==== tests ======================================================================================================

    /** data_version.json holds the current data version and this mod version; the upgrade wrote exactly one zip. */
    private TestResult test_markerAndBackup() {
        TestResult gate = gate();
        if (gate != null) return gate;
        Path world = world();
        Path marker = world.resolve(DataManager.BASE_PATH).resolve(StockMarketDataMigration.MARKER_FILE);
        TestResult r = assertTrue("marker exists: " + marker, Files.isRegularFile(marker));
        if (!r.passed()) return r;
        JsonObject o;
        try {
            o = JsonParser.parseString(Files.readString(marker)).getAsJsonObject();
        } catch (Exception e) {
            return fail("unreadable marker " + marker + ": " + e);
        }
        r = assertEquals("dataVersion", CURRENT_DATA_VERSION, o.get("dataVersion").getAsInt());
        if (!r.passed()) return r;
        r = assertEquals("modVersion", StockMarketMod.VERSION, o.get("modVersion").getAsString());
        if (!r.passed()) return r;
        Set<Path> zips = StockMarketDataMigration.backupZips(world);
        r = assertEquals("stockmarket backup zips " + zips, 1, zips.size());
        if (!r.passed()) return r;
        return pass("marker v" + CURRENT_DATA_VERSION + ", backup " + zips.iterator().next().getFileName());
    }

    /** The markets after load are exactly the markets in the backup's MarketManager data. */
    private TestResult test_marketsKept() {
        TestResult gate = gate();
        if (gate != null) return gate;
        Before b;
        try {
            b = before();
        } catch (Exception e) {
            return fail("backup unreadable: " + e);
        }
        ListTag list = b.markets().get("markets");
        if (list == null || list.isEmpty())
            return fail("backup holds no MarketManager/markets data; StockMarket folders in the backup: " + b.folders());
        Set<ItemID> old = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            ItemID id = ItemID.createFromTag(NbtCompat.getCompound(list, i));
            if (id == null || !id.isValid()) return fail("backup market " + i + " has no valid ItemID");
            old.add(id);
        }
        Set<ItemID> now = new HashSet<>(StockMarketModBackend.getServerInstances().MARKET_MANAGER.getSync().getAvailableMarketIDs());
        Set<ItemID> missing = new HashSet<>(old);
        missing.removeAll(now);
        Set<ItemID> extra = new HashSet<>(now);
        extra.removeAll(old);
        if (!missing.isEmpty() || !extra.isEmpty())
            return fail("markets differ: " + missing.size() + " missing " + names(missing) + ", "
                    + extra.size() + " new " + names(extra) + " (backup " + old.size() + ", now " + now.size() + ")");
        return pass(now.size() + " markets, same as the backup");
    }

    /**
     * For every VolatilityPlugin in the backup: a NetFlowPlugin directly before it, same markets, enabled state and
     * auto-subscribe, per market the old flow settings decoded as the upgrade does them plus moveOldDefaults.
     */
    private TestResult test_netFlowSplit() {
        TestResult gate = gate();
        if (gate != null) return gate;
        Before b;
        try {
            b = before();
        } catch (Exception e) {
            return fail("backup unreadable: " + e);
        }
        ListTag list = b.plugins().get("plugins");
        if (list == null || list.isEmpty())
            return fail("backup holds no PluginManager/plugins data; StockMarket folders in the backup: " + b.folders());
        if (!(StockMarketModBackend.getServerInstances().PLUGIN_MANAGER.getSync() instanceof ServerPluginManager pm))
            return fail("no ServerPluginManager on this server");
        List<UUID> order = new ArrayList<>(pm.getPlugins().keySet());

        String volType = Plugins.VOLATILITY_PLUGIN.getPluginTypeID();
        Map<String, Integer> typesInBackup = new HashMap<>();
        int vols = 0, markets = 0, movedExpected = 0, movedSeen = 0, movedSens = 0, movedMax = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = NbtCompat.getCompound(list, i);
            String type = NbtCompat.getString(tag, "pluginTypeID");
            typesInBackup.merge(type, 1, Integer::sum);
            if (!volType.equals(type)) continue;
            vols++;
            if (!tag.contains("instanceID")) return fail("backup VolatilityPlugin " + i + " has no instanceID");
            UUID volID = NbtCompat.getUUID(tag, "instanceID");
            int at = order.indexOf(volID);
            if (at < 0 || !(pm.getPlugins().get(volID) instanceof VolatilityPlugin vol))
                return fail("VolatilityPlugin " + volID + " of the backup is not loaded");
            if (at == 0 || !(pm.getPlugins().get(order.get(at - 1)) instanceof NetFlowPlugin flow))
                return fail("no NetFlowPlugin directly before VolatilityPlugin " + volID + " (position " + at + ")");

            Set<ItemID> oldMarkets = new HashSet<>();
            if (tag.contains("subscribedMarkets")) {
                ListTag ml = NbtCompat.getList(tag, "subscribedMarkets", 10);
                for (int j = 0; j < ml.size(); j++) {
                    ItemID id = ItemID.createFromTag(NbtCompat.getCompound(ml, j));
                    if (id != null && id.isValid()) oldMarkets.add(id);
                }
            }
            TestResult r = assertEquals("NetFlow markets of " + volID, oldMarkets, new HashSet<>(flow.getSubscribedMarkets()));
            if (!r.passed()) return r;
            r = assertEquals("NetFlow enabled of " + volID, tag.contains("enabled") && NbtCompat.getBoolean(tag, "enabled"), flow.isEnabled());
            if (!r.passed()) return r;
            r = assertEquals("NetFlow enabled = Volatility enabled of " + volID, vol.isEnabled(), flow.isEnabled());
            if (!r.passed()) return r;
            if (tag.contains("autoSubscribeNewMarkets")) {
                r = assertEquals("NetFlow auto-subscribe of " + volID, NbtCompat.getBoolean(tag, "autoSubscribeNewMarkets"), flow.getAutoSubscribeNewMarkets());
                if (!r.passed()) return r;
            }

            // the old flow settings, picked exactly as migrateNetFlowPlugins picks them
            Map<ItemID, byte[]> bytes = new HashMap<>();
            if (tag.contains("customSettingsMap")) {
                ListTag sl = NbtCompat.getList(tag, "customSettingsMap", 10);
                for (int j = 0; j < sl.size(); j++) {
                    CompoundTag e = NbtCompat.getCompound(sl, j);
                    ItemID id = ItemID.createFromTag(e);
                    if (id != null && id.isValid() && e.contains("settingsData")) bytes.put(id, NbtCompat.getByteArray(e, "settingsData"));
                }
            }
            byte[] legacyAll = tag.contains("customSettings") ? NbtCompat.getByteArray(tag, "customSettings") : null;
            Map<ItemID, CompoundTag> nbt = new HashMap<>();
            CompoundTag data = tag.contains("pluginData") ? NbtCompat.getCompound(tag, "pluginData") : new CompoundTag();
            if (data.contains("marketData")) {
                ListTag dl = NbtCompat.getList(data, "marketData", 10);
                for (int j = 0; j < dl.size(); j++) {
                    CompoundTag e = NbtCompat.getCompound(dl, j);
                    ItemID id = ItemID.createFromTag(e);
                    if (id != null && id.isValid()) nbt.put(id, e);
                }
            }

            for (ItemID m : oldMarkets) {
                markets++;
                byte[] raw = bytes.getOrDefault(m, legacyAll);
                NetFlowPlugin.Settings old = raw != null ? decodeLegacy(raw) : NetFlowPlugin.Settings.createDefault();
                CompoundTag mt = nbt.get(m);
                if (mt != null) old = NetFlowPlugin.applyLegacyVolatilityNbt(old, mt);
                NetFlowPlugin.Settings expected = NetFlowPlugin.moveOldDefaults(old);
                NetFlowPlugin.Settings actual = flow.getCustomSettings(m);
                r = assertEquals("NetFlow settings of " + m.getName() + " (plugin " + volID + ")", expected, actual);
                if (!r.passed()) return r;
                if (!expected.equals(old)) {
                    movedExpected++;
                    if (expected.flowSensitivity() != old.flowSensitivity()) movedSens++;
                    if (expected.maxPriceMult() != old.maxPriceMult()) movedMax++;
                    if (actual != null && !actual.equals(old)) movedSeen++;
                }
            }
        }
        if (vols == 0)
            return fail("backup holds no VolatilityPlugin; plugin types in the backup: " + typesInBackup);
        TestResult r = assertEquals("markets moved off the 2.1.2 defaults", movedExpected, movedSeen);
        if (!r.passed()) return r;
        return pass(vols + " VolatilityPlugin(s), " + markets + " market settings checked; " + movedExpected
                + " moved off the 2.1.2 defaults (sensitivity " + movedSens + ", max multiplier " + movedMax + ")");
    }

    /** After a save, the PluginManager's managerData on disk marks the NetFlow split as done. */
    private TestResult test_flagSaved() {
        TestResult gate = gate();
        if (gate != null) return gate;
        StockMarketModBackend.ServerInstances backend = StockMarketModBackend.getServerInstances();
        if (!backend.DATA_MANAGER.save(getServer())) return fail("DataManager.save failed");
        ListTag manager = backend.DATA_MANAGER.readDataCompoundList(
                world().resolve(DataManager.BASE_PATH).resolve("PluginManager").resolve("managerData"));
        TestResult r = assertFalse("managerData saved", manager.isEmpty());
        if (!r.passed()) return r;
        return assertTrue("netFlowPluginMigrationDone saved as true",
                NbtCompat.getBoolean(NbtCompat.getCompound(manager, 0), "netFlowPluginMigrationDone"));
    }

    // ==== helpers ====================================================================================================

    /** Opt-in (only with --only naming this topic) and master only; null = run. */
    private TestResult gate() {
        String include = System.getProperty(TestRegistry.INCLUDE_PROPERTY);
        if (include == null || include.isBlank() || TestRegistry.isExcluded(TestRegistry.topicKey(this), include, null))
            return skip("opt-in: --only " + TOPIC + " with --world <2.1.2 world>");
        StockMarketModBackend.ServerInstances backend = StockMarketModBackend.getServerInstances();
        if (backend == null || getServer() == null) return skip("no server in this JVM");
        if (backend.MARKET_MANAGER.isSlave()) return skip("master only");
        return null;
    }

    private Path world() {
        return getServer().getWorldPath(LevelResource.ROOT);
    }

    /** Unpacks the backup's MarketManager and PluginManager folders and reads them like the server load does. */
    private Before before() throws IOException {
        if (before != null) return before;
        Set<Path> zips = StockMarketDataMigration.backupZips(world());
        if (zips.size() != 1) throw new IOException("expected one stockmarket backup zip, found " + zips);
        String base = DataManager.BASE_PATH.toString().replace('\\', '/') + "/";
        Path tmp = Files.createTempDirectory("sm_upgrade_212");
        Set<String> folders = new TreeSet<>();
        try {
            try (ZipFile zip = new ZipFile(zips.iterator().next().toFile())) {
                for (ZipEntry e : zip.stream().toList()) {
                    if (e.isDirectory() || !e.getName().startsWith(base)) continue;
                    String rel = e.getName().substring(base.length());
                    int slash = rel.lastIndexOf('/');
                    folders.add(slash < 0 ? "." : rel.substring(0, slash));
                    if (!rel.startsWith("MarketManager/") && !rel.startsWith("PluginManager/")) continue;
                    Path out = tmp.resolve(rel).normalize();
                    if (!out.startsWith(tmp)) throw new IOException("bad zip entry " + e.getName());
                    Files.createDirectories(out.getParent());
                    try (InputStream in = zip.getInputStream(e)) {
                        Files.copy(in, out);
                    }
                }
            }
            DataManager dm = StockMarketModBackend.getServerInstances().DATA_MANAGER;
            Map<String, ListTag> markets = Files.isDirectory(tmp.resolve("MarketManager"))
                    ? dm.readDataCompoundListMap(tmp.resolve("MarketManager")) : Map.of();
            Map<String, ListTag> plugins = Files.isDirectory(tmp.resolve("PluginManager"))
                    ? dm.readDataCompoundListMap(tmp.resolve("PluginManager")) : Map.of();
            before = new Before(markets, plugins, folders);
            return before;
        } finally {
            try (Stream<Path> walk = Files.walk(tmp)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** NetFlowPlugin.LEGACY_VOLATILITY_CODEC, wrapped as ServerPluginManager.decodeLegacyVolatility does it. */
    private static NetFlowPlugin.Settings decodeLegacy(byte[] bytes) {
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.wrappedBuffer(bytes);
        try {
            return NetFlowPlugin.LEGACY_VOLATILITY_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    private static List<String> names(Set<ItemID> ids) {
        return ids.stream().limit(10).map(ItemID::getName).toList();
    }
}
