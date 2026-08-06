package net.kroia.stockmarket.api;

import net.kroia.stockmarket.api.integration.IStockMarketIntegration;
import org.jetbrains.annotations.Nullable;

public interface StockMarketAPI {

    /**
     * Returns the mod ID of the Stock ServerMarket mod.
     *
     * @return The mod ID as a String.
     */
    String getModID();


    /**
     * Returns the version of the Stock ServerMarket mod.
     *
     * @return The mod version as a String.
     */
    String getModVersion();

    /**
     * Returns the in-process integration SPI for creating, querying, and closing
     * markets from outside the StockMarket mod.
     * <p>
     * Returns a non-null instance on the server side (dedicated or integrated)
     * once the server has started. Returns {@code null} on a pure-client JVM
     * where no server exists. Callers must invoke the returned instance's
     * methods on the server thread only.
     *
     * @return the integration SPI, or {@code null} when no server is running in
     *         this JVM
     */
    @Nullable
    IStockMarketIntegration getIntegration();
}
