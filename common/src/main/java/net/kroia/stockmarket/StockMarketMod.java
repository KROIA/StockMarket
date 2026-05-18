package net.kroia.stockmarket;

import com.mojang.logging.LogUtils;
import net.kroia.stockmarket.api.StockMarketAPI;
import org.slf4j.Logger;


public final class StockMarketMod {
    public static final String MOD_ID = "stockmarket";
    public static final String VERSION = "2.0.1";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Set to false for release builds to hide dev-only commands (exportrecipes, devTestScreen, etc.)
    public static final boolean ENABLE_DEV_FEATURES = true;

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
