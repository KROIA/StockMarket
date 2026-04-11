package net.kroia.stockmarket;

import com.mojang.logging.LogUtils;
import dev.architectury.event.events.common.LifecycleEvent;
import net.kroia.stockmarket.api.StockMarketAPI;
import net.kroia.stockmarket.data.DatabaseManager;
import org.slf4j.Logger;


public final class StockMarketMod {
    public static final String MOD_ID = "stockmarket";
    public static final String VERSION = "2.0.0_ALPHA";
    public static final Logger LOGGER = LogUtils.getLogger();

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
