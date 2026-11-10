package net.kroia.stockmarket.testing;

import net.kroia.modutilities.testing.TestRegistry;
import net.kroia.modutilities.testing.TestSuite;
import net.kroia.stockmarket.testing.tests.ActiveOrdersRequestTestSuite;
import net.kroia.stockmarket.testing.tests.ClientMarketTestSuite;
import net.kroia.stockmarket.testing.tests.ClientNewsCacheTestSuite;
import net.kroia.stockmarket.testing.tests.NewsChainsTestSuite;
import net.kroia.stockmarket.testing.tests.CreateOrderRequestTestSuite;
import net.kroia.stockmarket.testing.tests.DataFilterTestSuite;
import net.kroia.stockmarket.testing.tests.DatabaseTestSuite;
import net.kroia.stockmarket.testing.tests.DynamicIndexedArrayTestSuite;
import net.kroia.stockmarket.testing.tests.InterMarketExecutorTestSuite;
import net.kroia.stockmarket.testing.tests.InterMarketOrderTestSuite;
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
import net.kroia.stockmarket.testing.tests.PluginTestSuite;
import net.kroia.stockmarket.testing.tests.PriceHistoryCacheTestSuite;
import net.kroia.stockmarket.testing.tests.PriceHistoryTestSuite;
import net.kroia.stockmarket.testing.tests.NormalizedRandomPriceGeneratorTestSuite;
import net.kroia.stockmarket.testing.tests.RandomWalkTestSuite;
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

import java.util.regex.Pattern;

/**
 * Registers the StockMarket test suites with the ModUtilities {@link TestRegistry}.
 * <p>
 * Suite filter: the JVM property {@code -Dstockmarket.tests=<regex>} registers only the suites whose class simple
 * name the regex {@link java.util.regex.Matcher#find() finds} (e.g. {@code PluginTestSuite|NetFlowPluginTestSuite}).
 * Unset or blank = every suite (default).
 */
public class StockMarketTestRegistration {

    /** JVM property holding the optional suite filter regex. */
    public static final String SUITE_FILTER_PROPERTY = "stockmarket.tests";

    private static boolean registered = false;
    /** The suite filter of the current {@link #register()} call, null = every suite. */
    private static Pattern suiteFilter;

    public static void register() {
        if (registered) return;
        registered = true;
        String filter = System.getProperty(SUITE_FILTER_PROPERTY, "");
        suiteFilter = filter.isBlank() ? null : Pattern.compile(filter);

        // Existing test suites
        add(new OrderTestSuite());
        add(new DynamicIndexedArrayTestSuite());
        add(new PIDTestSuite());
        add(new RandomWalkTestSuite());
        add(new MarketIntegrationTestSuite());

        // New common test suites
        add(new InterMarketOrderTestSuite());
        add(new TradingPairTestSuite());
        add(new VirtualOrderbookTestSuite());
        add(new DataFilterTestSuite());
        add(new PriceHistoryTestSuite());
        add(new PriceHistoryCacheTestSuite());
        add(new UserTestSuite());
        add(new AdminFlagSyncTestSuite()); // admin flag / LuckPerms sync decisions
        add(new PlayerPreferencesTestSuite());
        add(new MarketSettingsTestSuite());
        add(new MarketPresetTestSuite());
        add(new ClientMarketTestSuite());
        add(new NormalizedRandomPriceGeneratorTestSuite());
        add(new VillagerTradePricingTestSuite());
        add(new VillagerMoneyPaymentTestSuite());
        add(new NetFlowPluginTestSuite());
        add(new VolatilityPluginTestSuite());
        add(new ModSettingsTestSuite());
        add(new NewsSystemTestSuite());
        add(new NewsPluginTestSuite());
        add(new NewsAdminTestSuite());
        add(new NewsSchedulerTestSuite());
        add(new NewsHistoryTestSuite());
        add(new NewsHistoryChunksTestSuite());
        add(new ClientNewsCacheTestSuite());
        add(new NewsPictureLibraryTestSuite());
        add(new NewsPictureStoreTestSuite());
        add(new NewsPictureClientCacheTestSuite());
        add(new NewsRegistryTestSuite());
        add(new NewsRequirementTestSuite());
        add(new NewsChainsTestSuite());
        add(new PlaceholderNamingTestSuite());

        // New master-only test suites
        add(new MatchingEngineTestSuite());
        add(new OrderbookTestSuite());
        add(new CreateOrderRequestTestSuite());
        add(new ActiveOrdersRequestTestSuite());
        add(new DatabaseTestSuite());
        add(new MarketPriceManagerTestSuite());
        add(new OrderRecordManagerTestSuite());
        add(new ServerMarketTestSuite());
        add(new PluginTestSuite());
        add(new InterMarketExecutorTestSuite());
        add(new MarketMergeConsolidationTestSuite());
        add(new VillagerTradeRewriteTestSuite());
        add(new NewsHistoryRequestTestSuite());
        add(new NewsPictureRequestTestSuite());
        add(new DiscordIntegrationTestSuite()); // T-217 Discord feed via the sink seam, never a real webhook
        // Admin flag sync against a real LuckPerms (skip without it), then "LuckPerms removed" phase 1/2.
        // Last: the removed-suite's phase 1 leaves the sync setting on (autotest runs only).
        add(new AdminFlagSyncLuckPermsTests());
        add(new StockMarketLuckPermsRemovedTests());
    }

    /**
     * Registers one suite unless the {@link #SUITE_FILTER_PROPERTY} filter is set and does not find the suite's
     * class simple name.
     *
     * @param suite the test suite
     */
    private static void add(TestSuite suite) {
        if (suiteFilter == null || suiteFilter.matcher(suite.getClass().getSimpleName()).find())
            TestRegistry.register(suite);
    }
}
