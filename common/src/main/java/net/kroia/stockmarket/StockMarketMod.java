package net.kroia.stockmarket;

import net.kroia.stockmarket.api.StockMarketAPI;

public final class StockMarketMod {
    public static final String MOD_ID = "stockmarket";
    public static final String VERSION = "2.0.0_ALPHA";

    private static StockMarketModBackend backend;

    public static void init() {
        if(backend == null)
            backend = new StockMarketModBackend();
    }


    public static StockMarketAPI getAPI() {
        if(backend == null)
            backend = new StockMarketModBackend();
        return backend;
    }


}
