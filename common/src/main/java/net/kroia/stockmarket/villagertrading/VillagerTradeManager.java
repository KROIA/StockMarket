package net.kroia.stockmarket.villagertrading;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.StockMarketModSettings;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.ISyncServerMarketManager;
import net.kroia.stockmarket.networking.packet.VillagerTradePriceTablePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
 * Also owns access to the {@link VillagerTradeSavedData} sidecar (with a
 * one-time stale-entry prune per server lifetime).
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
                settings.VILLAGER_BUY_MARGIN.get(), settings.VILLAGER_SELL_MARGIN.get(), prices);
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
    public VillagerTradeSavedData getSavedData(MinecraftServer server) {
        VillagerTradeSavedData savedData = VillagerTradeSavedData.get(server);
        if (!savedDataPruned) {
            savedDataPruned = true;
            savedData.pruneStale(server.overworld().getGameTime());
        }
        return savedData;
    }
}
