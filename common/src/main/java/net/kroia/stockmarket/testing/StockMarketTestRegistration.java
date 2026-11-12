package net.kroia.stockmarket.testing;

import net.kroia.modutilities.testing.TestRegistry;
import net.kroia.stockmarket.testing.tests.ActiveOrdersRequestTestSuite;
import net.kroia.stockmarket.testing.tests.ClientIntegrationTestSuite;
import net.kroia.stockmarket.testing.tests.ClientMarketTestSuite;
import net.kroia.stockmarket.testing.tests.ClientSettingsSyncTestSuite;
import net.kroia.stockmarket.testing.tests.ClientNewsCacheTestSuite;
import net.kroia.stockmarket.testing.tests.NewsChainsTestSuite;
import net.kroia.stockmarket.testing.tests.CreateOrderRequestTestSuite;
import net.kroia.stockmarket.testing.tests.DataFilterTestSuite;
import net.kroia.stockmarket.testing.tests.DatabaseTestSuite;
import net.kroia.stockmarket.testing.tests.DynamicIndexedArrayTestSuite;
import net.kroia.stockmarket.testing.tests.InterMarketExecutorTestSuite;
import net.kroia.stockmarket.testing.tests.InterMarketOrderTestSuite;
import net.kroia.stockmarket.testing.tests.MarketVisibilityTestSuite;
import net.kroia.stockmarket.testing.tests.MasterDownTestSuite;
import net.kroia.stockmarket.testing.tests.TradingPairTestSuite;
import net.kroia.stockmarket.testing.tests.MarketIntegrationTestSuite;
import net.kroia.stockmarket.testing.tests.MarketMergeConsolidationTestSuite;
import net.kroia.stockmarket.testing.tests.MarketPriceManagerTestSuite;
import net.kroia.stockmarket.testing.tests.MarketPresetTestSuite;
import net.kroia.stockmarket.testing.tests.MarketSettingsTestSuite;
import net.kroia.stockmarket.testing.tests.MatchingEngineTestSuite;
import net.kroia.stockmarket.testing.tests.ModSettingsTestSuite;
import net.kroia.stockmarket.testing.tests.NewsAdminTestSuite;
import net.kroia.stockmarket.testing.tests.NewsHistoryRequestTestSuite;
import net.kroia.stockmarket.testing.tests.NewsHistoryChunksTestSuite;
import net.kroia.stockmarket.testing.tests.NewsHistoryTestSuite;
import net.kroia.stockmarket.testing.tests.NewsPictureClientCacheTestSuite;
import net.kroia.stockmarket.testing.tests.NewsPictureLibraryTestSuite;
import net.kroia.stockmarket.testing.tests.NewsPictureRequestTestSuite;
import net.kroia.stockmarket.testing.tests.NewsPictureStoreTestSuite;
import net.kroia.stockmarket.testing.tests.NewsPluginTestSuite;
import net.kroia.stockmarket.testing.tests.NewsRegistryTestSuite;
import net.kroia.stockmarket.testing.tests.NewsRequirementTestSuite;
import net.kroia.stockmarket.testing.tests.NewsSchedulerTestSuite;
import net.kroia.stockmarket.testing.tests.NewsSystemTestSuite;
import net.kroia.stockmarket.testing.tests.OrderRecordManagerTestSuite;
import net.kroia.stockmarket.testing.tests.OrderTestSuite;
import net.kroia.stockmarket.testing.tests.OrderbookTestSuite;
import net.kroia.stockmarket.testing.tests.PIDTestSuite;
import net.kroia.stockmarket.testing.tests.PlaceholderNamingTestSuite;
import net.kroia.stockmarket.testing.tests.PortSmokeTestSuite;
import net.kroia.stockmarket.testing.tests.PluginTestSuite;
import net.kroia.stockmarket.testing.tests.PumpDumpDetectorTestSuite;
import net.kroia.stockmarket.testing.tests.PriceHistoryCacheTestSuite;
import net.kroia.stockmarket.testing.tests.PriceHistoryTestSuite;
import net.kroia.stockmarket.testing.tests.NormalizedRandomPriceGeneratorTestSuite;
import net.kroia.stockmarket.testing.tests.RandomWalkTestSuite;
import net.kroia.stockmarket.testing.tests.RequestFailureTestSuite;
import net.kroia.stockmarket.testing.tests.ServerMarketTestSuite;
import net.kroia.stockmarket.testing.tests.PlayerPreferencesTestSuite;
import net.kroia.stockmarket.testing.tests.AdminFlagSyncTestSuite;
import net.kroia.stockmarket.testing.tests.AdminFlagSyncLuckPermsTests;
import net.kroia.stockmarket.testing.tests.DiscordIntegrationTestSuite;
import net.kroia.stockmarket.testing.tests.StockMarketLuckPermsRemovedTests;
import net.kroia.stockmarket.testing.tests.UserTestSuite;
import net.kroia.stockmarket.testing.tests.VillagerMoneyPaymentTestSuite;
import net.kroia.stockmarket.testing.tests.VillagerTradePricingTestSuite;
import net.kroia.stockmarket.testing.tests.VillagerTradeRewriteTestSuite;
import net.kroia.stockmarket.testing.tests.VirtualOrderbookTestSuite;
import net.kroia.stockmarket.testing.tests.VolatilityPluginTestSuite;
import net.kroia.stockmarket.testing.tests.NetFlowPluginTestSuite;

/**
 * Registers every StockMarket test suite with the ModUtilities {@link TestRegistry}.
 * <p>
 * Each suite is one ModUtilities test topic, key {@code stockmarket/<category name>/<suite class simple name>}
 * (e.g. {@code stockmarket/sm_plugin/PluginTestSuite}). Which topics run is chosen by ModUtilities, not here: the
 * autotest GUI / {@code test-selection.json}, run_matrix {@code --only} / {@code --exclude}, or the JVM properties
 * {@code modutilities.tests.include} / {@code .exclude}. So always register everything, or a suite disappears from
 * the topic list.
 */
public class StockMarketTestRegistration {

    private static boolean registered = false;

    /** Registers all suites once; later calls do nothing. Must not touch platform code (also runs offline). */
    public static void register() {
        if (registered) return;
        registered = true;

        // Existing test suites
        TestRegistry.register(new OrderTestSuite());
        TestRegistry.register(new DynamicIndexedArrayTestSuite());
        TestRegistry.register(new PIDTestSuite());
        TestRegistry.register(new RandomWalkTestSuite());
        TestRegistry.register(new MarketIntegrationTestSuite());

        // New common test suites
        TestRegistry.register(new InterMarketOrderTestSuite());
        TestRegistry.register(new TradingPairTestSuite());
        TestRegistry.register(new VirtualOrderbookTestSuite());
        TestRegistry.register(new DataFilterTestSuite());
        TestRegistry.register(new PriceHistoryTestSuite());
        TestRegistry.register(new PriceHistoryCacheTestSuite());
        TestRegistry.register(new UserTestSuite());
        TestRegistry.register(new AdminFlagSyncTestSuite()); // admin flag / LuckPerms sync decisions
        TestRegistry.register(new PlayerPreferencesTestSuite());
        TestRegistry.register(new MarketSettingsTestSuite());
        TestRegistry.register(new MarketPresetTestSuite());
        TestRegistry.register(new ClientMarketTestSuite());
        TestRegistry.register(new NormalizedRandomPriceGeneratorTestSuite());
        TestRegistry.register(new VillagerTradePricingTestSuite());
        TestRegistry.register(new VillagerMoneyPaymentTestSuite());
        TestRegistry.register(new NetFlowPluginTestSuite());
        TestRegistry.register(new VolatilityPluginTestSuite());
        TestRegistry.register(new ModSettingsTestSuite());
        TestRegistry.register(new NewsSystemTestSuite());
        TestRegistry.register(new NewsPluginTestSuite());
        TestRegistry.register(new NewsAdminTestSuite());
        TestRegistry.register(new NewsSchedulerTestSuite());
        TestRegistry.register(new NewsHistoryTestSuite());
        TestRegistry.register(new NewsHistoryChunksTestSuite());
        TestRegistry.register(new ClientNewsCacheTestSuite());
        TestRegistry.register(new NewsPictureLibraryTestSuite());
        TestRegistry.register(new NewsPictureStoreTestSuite());
        TestRegistry.register(new NewsPictureClientCacheTestSuite());
        TestRegistry.register(new NewsRegistryTestSuite());
        TestRegistry.register(new NewsRequirementTestSuite());
        TestRegistry.register(new NewsChainsTestSuite());
        TestRegistry.register(new PlaceholderNamingTestSuite());
        TestRegistry.register(new RequestFailureTestSuite()); // T-227: ARRS timeouts / failed futures
        TestRegistry.register(new MarketVisibilityTestSuite()); // T-237: markets hidden on a slave that cannot resolve their item
        TestRegistry.register(new ClientSettingsSyncTestSuite()); // T-140: initial chart window sync (master + slave)
        TestRegistry.register(new MasterDownTestSuite()); // T-246: slave-masterdown cell only (T-245 screen refusal)
        TestRegistry.register(new PortSmokeTestSuite()); // SM-288: per-version port smoke checks
        TestRegistry.register(new ClientIntegrationTestSuite()); // SM-313: client API for BankSystem's company screen
        TestRegistry.register(new net.kroia.stockmarket.testing.tests.ItemPickerRegistrationTestSuite()); // SM-273
        TestRegistry.register(new net.kroia.stockmarket.testing.tests.DocumentationLinkTestSuite()); // SM-276

        // New master-only test suites
        TestRegistry.register(new MatchingEngineTestSuite());
        TestRegistry.register(new OrderbookTestSuite());
        TestRegistry.register(new CreateOrderRequestTestSuite());
        TestRegistry.register(new ActiveOrdersRequestTestSuite());
        TestRegistry.register(new DatabaseTestSuite());
        TestRegistry.register(new MarketPriceManagerTestSuite());
        TestRegistry.register(new OrderRecordManagerTestSuite());
        TestRegistry.register(new ServerMarketTestSuite());
        TestRegistry.register(new PluginTestSuite());
        TestRegistry.register(new InterMarketExecutorTestSuite());
        TestRegistry.register(new MarketMergeConsolidationTestSuite());
        TestRegistry.register(new VillagerTradeRewriteTestSuite());
        TestRegistry.register(new NewsHistoryRequestTestSuite());
        TestRegistry.register(new NewsPictureRequestTestSuite());
        TestRegistry.register(new PumpDumpDetectorTestSuite()); // T-192 pump-and-dump detector + alert toggle
        TestRegistry.register(new DiscordIntegrationTestSuite()); // T-217 Discord feed via the sink seam, never a real webhook
        // The long market simulation gates, one topic each; autotest-out/test-selection.json keeps them off
        TestRegistry.register(new PluginTestSuite.T207GateTestSuite());
        TestRegistry.register(new PluginTestSuite.T208GateTestSuite());
        TestRegistry.register(new PluginTestSuite.T213GateG1TestSuite());
        TestRegistry.register(new PluginTestSuite.T213GateG2TestSuite());
        TestRegistry.register(new PluginTestSuite.T213GateG3G4TestSuite());
        // Admin flag sync against a real LuckPerms (skip without it), then "LuckPerms removed" phase 1/2.
        // Last: the removed-suite's phase 1 leaves the sync setting on (autotest runs only).
        TestRegistry.register(new AdminFlagSyncLuckPermsTests());
        TestRegistry.register(new StockMarketLuckPermsRemovedTests());
    }
}
