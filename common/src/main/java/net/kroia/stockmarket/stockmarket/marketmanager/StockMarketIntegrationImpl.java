package net.kroia.stockmarket.stockmarket.marketmanager;

import net.kroia.banksystem.api.bankmanager.IServerBankManager;
import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.integration.IStockMarketIntegration;
import net.kroia.stockmarket.api.integration.MarketConfig;
import net.kroia.stockmarket.api.integration.MarketOpenResult;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.ISyncServerMarketManager;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Default {@link IStockMarketIntegration} implementation. Delegates every call
 * to the live {@link StockMarketModBackend.ServerInstances}. Intentionally
 * placed outside the {@code api} package — the API package only exposes the
 * contract, not the wiring.
 * <p>
 * Instantiated once per server lifetime by
 * {@link StockMarketModBackend#getIntegration()} and re-used until server stop.
 */
public final class StockMarketIntegrationImpl implements IStockMarketIntegration {

    /**
     * Server-instances supplier — indirection so the impl reads the CURRENT
     * {@code SERVER_INSTANCES} on every call instead of capturing a stale
     * reference from a server that has since stopped.
     */
    private final Supplier<StockMarketModBackend.ServerInstances> instancesSupplier;

    public StockMarketIntegrationImpl(Supplier<StockMarketModBackend.ServerInstances> instancesSupplier) {
        this.instancesSupplier = instancesSupplier;
    }

    private ISyncServerMarketManager sync() {
        StockMarketModBackend.ServerInstances inst = instancesSupplier.get();
        if (inst == null || inst.MARKET_MANAGER == null) return null;
        return inst.MARKET_MANAGER.getSync();
    }

    @Override
    public @NotNull MarketOpenResult.Result openMarket(@NotNull ItemID subject, @NotNull MarketConfig cfg) {
        ISyncServerMarketManager mgr = sync();
        if (mgr == null) {
            return new MarketOpenResult.Result(MarketOpenResult.Status.FAILED,
                    "StockMarket server not initialized");
        }
        if (mgr.marketExists(subject)) {
            return new MarketOpenResult.Result(MarketOpenResult.Status.ALREADY_EXISTS);
        }

        StockMarketModBackend.ServerInstances inst = instancesSupplier.get();
        if (inst != null && inst.BANK_SYSTEM_API != null) {
            IServerBankManager bankManager = inst.BANK_SYSTEM_API.getServerBankManager().getSync();
            if (bankManager != null && bankManager.isItemIDBlacklisted(subject)) {
                return new MarketOpenResult.Result(MarketOpenResult.Status.ITEM_BLACKLISTED,
                        "Item is blacklisted in the banking system");
            }
        }

        IServerMarket created = mgr.createMarketWithConfig(subject, cfg);
        if (created == null) {
            return new MarketOpenResult.Result(MarketOpenResult.Status.FAILED,
                    "Market creation returned null (see server log)");
        }
        return new MarketOpenResult.Result(MarketOpenResult.Status.SUCCESS);
    }

    @Override
    public boolean marketExistsFor(@NotNull ItemID subject) {
        ISyncServerMarketManager mgr = sync();
        return mgr != null && mgr.marketExists(subject);
    }

    @Override
    public void closeMarket(@NotNull ItemID subject) {
        ISyncServerMarketManager mgr = sync();
        if (mgr == null) return;
        // deleteMarket returns false when the market didn't exist — treat as no-op.
        mgr.deleteMarket(subject);
    }

    @Override
    public void setMarketOpen(@NotNull ItemID subject, boolean open) {
        ISyncServerMarketManager mgr = sync();
        if (mgr == null) return;
        IServerMarket market = mgr.getMarket(subject);
        // No market for this subject → nothing to open/close.
        if (market == null) return;
        // Sync accessor: the SPI is documented server-thread-only, so the
        // synchronous variant is correct here (no async marshalling needed).
        market.setMarketOpen(open);
    }

    @Override
    public boolean isMarketOpen(@NotNull ItemID subject) {
        ISyncServerMarketManager mgr = sync();
        if (mgr == null) return false;
        IServerMarket market = mgr.getMarket(subject);
        // Closed OR no market → not open.
        return market != null && market.isMarketOpen();
    }
}
