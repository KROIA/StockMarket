package net.kroia.stockmarket.villagertrading;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.NbtCompat;
import net.kroia.modutilities.persistence.DataPersistence;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.ISyncServerMarketManager;
import net.kroia.stockmarket.data.DataManager;
import net.kroia.stockmarket.networking.packet.VillagerTradePriceTablePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-server holder of the villager-trade repricing state (field in
 * {@code StockMarketModBackend.ServerInstances}).
 * <p>
 * <b>Master:</b> computes the {@link VillagerTradePriceTable} from the live
 * markets and settings ({@link #recomputeTable()}), refreshes it on a
 * wall-clock timer ({@link #tickMaster()}) and broadcasts it to all connected
 * slave servers ({@link #broadcastTable()}). In a single-server setup the
 * broadcast is a guarded no-op.
 * <p>
 * <b>Slave:</b> receives tables from the master via
 * {@link VillagerTradePriceTablePacket} and stores them with
 * {@link #applyTable(VillagerTradePriceTable)} (volatile reference swap — the
 * packet may arrive off the main thread while the rewriter reads on the main
 * thread).
 * <p>
 * Also owns the {@link VillagerTradeData} sidecar and its file
 * {@code <world>/data/StockMarket/villager_trades.nbt} (compressed NBT in the
 * DataManager folder). Unlike the master-only DataManager files it is kept on
 * master <b>and</b> slave servers, since every server rewrites its own villagers.
 * Loaded on server start ({@link #load}), saved with the other StockMarket data
 * ({@link #save}), with a one-time stale-entry prune per server lifetime.
 */
public class VillagerTradeManager {

    private final StockMarketModBackend.ServerInstances backend;

    /**
     * The current price table. Volatile: written by the network thread on
     * slaves, read by the server main thread in the rewriter. {@code null}
     * until the first recompute (master) / first broadcast received (slave).
     */
    private volatile @Nullable VillagerTradePriceTable table = null;

    /** Wall-clock timestamp of the last master-side refresh. */
    private long refreshTimer_lastMs = System.currentTimeMillis();

    /** One-time prune of stale sidecar entries per server lifetime. */
    private boolean savedDataPruned = false;

    /** Sidecar file name inside the DataManager folder {@code <world>/data/StockMarket/}. */
    public static final String DATA_FILE = "villager_trades.nbt";

    /** File of releases up to 2.1.x (vanilla SavedData in {@code <world>/data/}); migrated once. */
    private static final String LEGACY_FILE = "stockmarket_villager_trades.dat";

    /** File access in the DataManager folder, same format (compressed NBT) as the DataManager files. */
    private final DataPersistence persistence = new DataPersistence(
            DataPersistence.JsonFormat.PRETTY, DataPersistence.NbtFormat.COMPRESSED, DataManager.BASE_PATH);

    /** The loaded sidecar; {@code null} until {@link #load} ran. */
    private @Nullable VillagerTradeData savedData = null;

    /** Warn only once when the configured currency cannot be resolved. */
    private boolean warnedCurrencyUnresolvable = false;

    /**
     * Creates the manager for the given server backend. Whether this server is
     * master or slave is derived at runtime from the market manager's sync
     * access (master-only), so no explicit flag is needed.
     *
     * @param backend the per-server instances this manager belongs to
     */
    public VillagerTradeManager(StockMarketModBackend.ServerInstances backend) {
        this.backend = backend;
    }

    /**
     * Returns the current price table for the rewriter.
     *
     * @return the table, or {@code null} when none is available yet
     */
    public @Nullable VillagerTradePriceTable getTable() {
        return table;
    }

    /**
     * Slave side: stores a table received from the master (volatile swap, no
     * locking needed).
     *
     * @param table the received table
     */
    public void applyTable(VillagerTradePriceTable table) {
        this.table = table;
    }

    /**
     * Master side: recomputes the price table from the current settings and
     * market prices. No-op on slaves (no sync market manager).
     * <p>
     * Uses {@code getCurrentMarketPrice()} per market — never the
     * candle-resetting bulk getter.
     */
    public void recomputeTable() {
        ISyncServerMarketManager marketManager = backend.MARKET_MANAGER != null
                ? backend.MARKET_MANAGER.getSync() : null;
        if (marketManager == null || backend.SERVER_SETTINGS == null) {
            return; // slave, or not fully set up yet
        }
        StockMarketModSettings.VillagerTrading settings = backend.SERVER_SETTINGS.VILLAGER_TRADING;

        // The currency rides inside the table so slaves (which never read
        // settings.json) know what to pay with.
        ItemStack configuredCurrency = backend.SERVER_SETTINGS.MARKET.CURRENCY.get();
        ItemStack currency = configuredCurrency != null ? configuredCurrency.copy() : ItemStack.EMPTY;
        boolean enabled = settings.ENABLED.get();
        if (enabled && currency.isEmpty()) {
            if (!warnedCurrencyUnresolvable) {
                warnedCurrencyUnresolvable = true;
                backend.LOGGER.warn("[VillagerTradeManager] VillagerTrading is ENABLED but the configured"
                        + " market CURRENCY item is empty/unresolvable — villager trade repricing stays disabled.");
            }
            enabled = false; // force-disabled; broadcast still carries enabled=false
        }

        Map<Short, Long> prices = new HashMap<>();
        for (ItemID marketID : marketManager.getAvailableMarketIDs()) {
            IServerMarket market = marketManager.getMarket(marketID);
            if (market == null) {
                continue;
            }
            long priceRaw = market.getCurrentMarketPrice();
            if (priceRaw <= 0) {
                continue; // fresh/broken market — affected offers keep their emerald form
            }
            prices.put(marketID.getShort(), priceRaw);
        }

        this.table = new VillagerTradePriceTable(System.currentTimeMillis(), enabled, currency,
                settings.VILLAGER_BUY_MARGIN.get(), settings.VILLAGER_SELL_MARGIN.get(),
                settings.REPLACE_EMERALDS_FOR_UNLISTED.get(), prices);
    }

    /**
     * Master side: called every server tick. Recomputes and broadcasts the
     * table when the configured refresh interval elapsed.
     * <p>
     * The broadcast fires even when the feature is disabled so slaves learn
     * about the disabling and restore their villagers' offers.
     */
    public void tickMaster() {
        long intervalMs = 0;
        if (backend.SERVER_SETTINGS != null) {
            intervalMs = backend.SERVER_SETTINGS.VILLAGER_TRADING.PRICE_REFRESH_INTERVAL_MINUTES.get() * 60_000L;
        }
        if (intervalMs <= 0) {
            return; // interval disabled — only the startup table is ever used
        }
        long now = System.currentTimeMillis();
        if (now - refreshTimer_lastMs < intervalMs) {
            return;
        }
        refreshTimer_lastMs = now;
        recomputeTable();
        broadcastTable();
    }

    /**
     * Master side: pushes the current table to all connected slave servers.
     * No-op when no table exists yet or when not running as multi-server master.
     */
    public void broadcastTable() {
        VillagerTradePriceTable current = table;
        if (current != null) {
            VillagerTradePriceTablePacket.broadcast(current);
        }
    }

    /**
     * Returns the sidecar store, pruning stale entries once per server lifetime.
     *
     * @param server the running server
     * @return the sidecar
     */
    public VillagerTradeData getSavedData(MinecraftServer server) {
        if (savedData == null) {
            load(server);
        }
        if (!savedDataPruned) {
            savedDataPruned = true;
            savedData.pruneStale(server.overworld().getGameTime());
        }
        return savedData;
    }

    /**
     * Loads the sidecar from {@code <world>/data/StockMarket/villager_trades.nbt},
     * after moving a pre-2.2.0 file there once (see {@link #migrateLegacyFile}).
     * A missing or unreadable file yields an empty sidecar.
     *
     * @param server the starting server (master or slave)
     */
    public void load(MinecraftServer server) {
        Path file = dataFile(server);
        StockMarketMod.LOGGER.info("[VillagerTradeManager] Villager trade data file: {}", file.toAbsolutePath().normalize());
        migrateLegacyFile(server.getWorldPath(LevelResource.ROOT), file);
        CompoundTag tag = Files.exists(file) ? persistence.readDataCompound(file) : null;
        savedData = tag != null ? VillagerTradeData.load(tag, server.registryAccess()) : new VillagerTradeData();
        savedDataPruned = false;
    }

    /**
     * Writes the sidecar if it changed, atomically (temp file, then move).
     * Called wherever the other StockMarket data is saved.
     *
     * @param server the running server (master or slave)
     */
    public void save(MinecraftServer server) {
        VillagerTradeData data = savedData;
        if (data == null || !data.isDirty()) {
            return;
        }
        if (writeAtomic(dataFile(server), data.save(new CompoundTag(), server.registryAccess()))) {
            data.clearDirty();
        }
    }

    /**
     * @param server the running server
     * @return the absolute sidecar file path inside the server's world
     */
    private Path dataFile(MinecraftServer server) {
        persistence.setLevelSavePath(server.getWorldPath(LevelResource.ROOT));
        return persistence.getAbsoluteSavePath(DATA_FILE);
    }

    /**
     * Writes {@code tag} to a temp file next to {@code target}, then moves it over the target.
     *
     * @param target the file to write
     * @param tag    the content
     * @return {@code true} on success; failures are logged and leave the previous file untouched
     */
    private boolean writeAtomic(Path target, CompoundTag tag) {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            if (!persistence.saveDataCompound(temp, tag)) {
                throw new IOException("could not write " + temp);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            StockMarketMod.LOGGER.error("[VillagerTradeManager] Failed to save villager trade data to {}", target, e);
            return false;
        }
    }

    /**
     * One-time move of the villager trade file of releases up to 2.1.x. Those used vanilla
     * SavedData at {@code <world>/data/stockmarket_villager_trades.dat}: a gzip NBT whose
     * {@code data} compound has the same format as {@link #DATA_FILE}. Minecraft 26.1's world
     * upgrade leaves that file where it is; the {@code data/minecraft/} candidate covers a vanilla
     * move into the namespaced folder. Only runs while {@code target} is missing, never overwrites,
     * and keeps the old file when reading or writing fails.
     *
     * @param world  the world root folder
     * @param target the new sidecar file
     */
    private void migrateLegacyFile(Path world, Path target) {
        if (Files.exists(target)) {
            return;
        }
        Path[] candidates = {
                world.resolve("data").resolve(LEGACY_FILE),
                //world.resolve("data").resolve("minecraft").resolve(LEGACY_FILE), // mc>=26.1
        };
        for (Path source : candidates) {
            if (!Files.isRegularFile(source)) {
                continue;
            }
            CompoundTag root = persistence.readDataCompound(source);
            if (root == null || !writeAtomic(target, NbtCompat.getCompound(root, "data"))) {
                StockMarketMod.LOGGER.error("[VillagerTradeManager] Could not migrate villager trade data from {}; the old file is kept", source);
                return;
            }
            try {
                Files.delete(source);
            } catch (IOException e) {
                StockMarketMod.LOGGER.error("[VillagerTradeManager] Migrated villager trade data, but could not delete the old file {}", source, e);
            }
            StockMarketMod.LOGGER.info("[VillagerTradeManager] Migrated villager trade data from {} to {}", source, target);
            return;
        }
    }
}
