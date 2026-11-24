package net.kroia.stockmarket.testing;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.testkit.TestCommandRegistration;
import net.kroia.testkit.TestRegistry;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.minecraft.command.StockMarketCommands;
import net.kroia.stockmarket.testing.tests.*;

/**
 * The only way release code reaches dev-only server code (unit tests, test commands).
 * <p>
 * Release builds ({@code enable_dev_features=false} in gradle.properties) do not compile the
 * {@code testing} package at all, so this class does not exist there. {@link StockMarketMod}
 * detects it and {@code StockMarketModBackend} calls it reflectively, which keeps release code
 * compiling without it. The client-only {@link DevTestScreen} is opened separately so this class
 * never touches client classes.
 */
public final class DevBootstrap {
    private DevBootstrap() {}

    /** Called once from {@code StockMarketModBackend} during mod setup: registers the test commands. */
    public static void init() {
        AsynchronousRequestResponseSystem.register(FixtureTestRequest.INSTANCE); // BS-269: test-only fixture steps from a trusted slave
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) ->
                TestCommandRegistration.register(dispatcher, "stockmarket", "StockMarket", "stockmarket",
                        StockMarketCommands.isSlaveServer()));
        // TK-3: GUI suites run on the game client only; a dedicated server must never load their client classes.
        if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) {
            TestRegistry.register(new TradeScreenGuiTestSuite());
            TestRegistry.register(new ScreenTradeClientScenarioSuite()); // SM-352: client part of the two-client trade scenario
            TestRegistry.register(new DocsScreenshotSuite()); // SM-358: documentation screenshots (opt-in, --only)
            TestRegistry.register(new DocsWorldShotsSuite()); // SM-361: in-world documentation shots (opt-in, --only)
        }
    }

    /**
     * Called on every server setup (after BankSystem is ready): hands the server instances to the
     * test suites and registers them.
     *
     * @param instances the server instances of the starting server
     * @param isMaster  whether this server is the master; the master-only suites get the instances only then
     */
    public static void onServerSetup(StockMarketModBackend.ServerInstances instances, boolean isMaster) {
        if (!TestRegistry.ENABLE_TESTS) {
            return;
        }
        MarketIntegrationTestSuite.setBackend(instances);
        InterMarketExecutorTestSuite.setBackend(instances);
        MarketVisibilityTestSuite.setBackend(instances); // T-237: runs on master and slave
        WebInterfaceTestSuite.setBackend(instances); // SM-347: master tests + the slave check
        UserTestSuite.setBackend(instances); // SM-266/SM-287 admin command gate; master tests skip on a slave (no sync manager)
        StockMarketTestRegistration.register();
        if (isMaster) {
            MatchingEngineTestSuite.setBackend(instances);
            OrderbookTestSuite.setBackend(instances);
            CreateOrderRequestTestSuite.setBackend(instances);
            ActiveOrdersRequestTestSuite.setBackend(instances);
            DatabaseTestSuite.setBackend(instances);
            MarketPriceManagerTestSuite.setBackend(instances);
            OrderRecordManagerTestSuite.setBackend(instances);
            ServerMarketTestSuite.setBackend(instances);
            PluginTestSuite.setBackend(instances);
            MarketMergeConsolidationTestSuite.setBackend(instances);
            VillagerTradeRewriteTestSuite.setBackend(instances); // T-243 market-impact test
            NewsHistoryRequestTestSuite.setBackend(instances);
            DiscordIntegrationTestSuite.setBackend(instances);
            PumpDumpDetectorTestSuite.setBackend(instances); // T-192
            AdminFlagSyncLuckPermsTests.setBackend(instances);
            StockMarketLuckPermsRemovedTests.setBackend(instances);
        }
    }
}
