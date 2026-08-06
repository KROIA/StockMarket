package net.kroia.stockmarket.api.integration;

/**
 * Configuration DTO passed to {@link IStockMarketIntegration#openMarket} to
 * describe how a newly opened market should be initialized. Decoupled from
 * {@code MarketPreset} on purpose: callers of the integration SPI (other mods,
 * scripting bridges, admin tooling) construct markets directly from a
 * lightweight config without having to know about the preset system.
 * <p>
 * This record may gain additive fields in future versions; consumers should
 * always build instances via the canonical constructor or {@link #defaults()}
 * rather than assuming a fixed field count.
 *
 * @param virtualOrderbookEnabled   whether the market's virtual orderbook is
 *                                  active (matches
 *                                  {@code MarketSettings.virtualOrderbookEnabled}).
 *                                  Disabled markets behave as empty in the
 *                                  matching engine.
 * @param defaultPrice              initial market price, in display units (the
 *                                  same units used by {@code MarketPreset.defaultPrice}).
 *                                  Converted to raw amounts internally.
 * @param naturalAbundance          natural abundance value used by the default
 *                                  orderbook volume distribution plugin.
 * @param ignorePluginAutosubscribe when true, the newly created market is
 *                                  skipped by the plugin auto-subscribe pass;
 *                                  admins can still subscribe plugins manually
 *                                  later.
 */
public record MarketConfig(
        boolean virtualOrderbookEnabled,
        float defaultPrice,
        float naturalAbundance,
        boolean ignorePluginAutosubscribe
) {
    /**
     * Returns a sensible default config: virtual orderbook enabled, price 1.0,
     * natural abundance 10.0 (matches {@code ServerMarketManager.createMarket}'s
     * preset-less fallback), plugin auto-subscribe active.
     */
    public static MarketConfig defaults() {
        return new MarketConfig(true, 1.0f, 10.0f, false);
    }
}
