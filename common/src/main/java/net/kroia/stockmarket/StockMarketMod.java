package net.kroia.stockmarket;

import com.mojang.logging.LogUtils;
import net.kroia.stockmarket.api.StockMarketAPI;
import org.slf4j.Logger;


public final class StockMarketMod {
    public static final String MOD_ID = "stockmarket";
    public static final String VERSION = "2.2.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Dev-only code (unit tests, test commands, dev test screen) is switched by enable_dev_features in
    // gradle.properties: Gradle compiles the testing package only when it is true, and this flag follows
    // from its presence. It also gates the dev-only commands and debug performance logging.
    public static final String DEV_BOOTSTRAP = "net.kroia.stockmarket.testing.DevBootstrap";
    public static final boolean ENABLE_DEV_FEATURES = devClassesPresent();

    /**
     * @return {@code true} if the testing package was compiled into this build (enable_dev_features=true)
     */
    private static boolean devClassesPresent() {
        try {
            Class.forName(DEV_BOOTSTRAP, false, StockMarketMod.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Set by NeoForgeSetup's mod-bus handlers when they run (SM-288 port smoke test). Unused on Fabric/Quilt. */
    public static volatile boolean neoForgeCommonSetupFired = false;
    public static volatile boolean neoForgeClientSetupFired = false;

    private static StockMarketModBackend backend;

    public static void init() {
        // Unconditional (idempotent): getAPI() may already have created the backend before init().
        net.kroia.stockmarket.util.StockMarketPermissions.register();
        if(backend == null)
            backend = new StockMarketModBackend();
    }


    public static StockMarketAPI getAPI() {
        if(backend == null)
            backend = new StockMarketModBackend();
        return backend;
    }

}
