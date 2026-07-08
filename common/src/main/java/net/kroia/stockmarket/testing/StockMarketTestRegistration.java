package net.kroia.stockmarket.testing;

import net.kroia.modutilities.testing.TestRegistry;
import net.kroia.stockmarket.testing.tests.ActiveOrdersRequestTestSuite;
import net.kroia.stockmarket.testing.tests.ClientMarketTestSuite;
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
import net.kroia.stockmarket.testing.tests.OrderRecordManagerTestSuite;
import net.kroia.stockmarket.testing.tests.OrderTestSuite;
import net.kroia.stockmarket.testing.tests.OrderbookTestSuite;
import net.kroia.stockmarket.testing.tests.PIDTestSuite;
import net.kroia.stockmarket.testing.tests.PluginTestSuite;
import net.kroia.stockmarket.testing.tests.PriceHistoryTestSuite;
import net.kroia.stockmarket.testing.tests.NormalizedRandomPriceGeneratorTestSuite;
import net.kroia.stockmarket.testing.tests.RandomWalkTestSuite;
import net.kroia.stockmarket.testing.tests.ServerMarketTestSuite;
import net.kroia.stockmarket.testing.tests.PlayerPreferencesTestSuite;
import net.kroia.stockmarket.testing.tests.UserTestSuite;
import net.kroia.stockmarket.testing.tests.VirtualOrderbookTestSuite;

public class StockMarketTestRegistration {

    private static boolean registered = false;

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
        TestRegistry.register(new UserTestSuite());
        TestRegistry.register(new PlayerPreferencesTestSuite());
        TestRegistry.register(new MarketSettingsTestSuite());
        TestRegistry.register(new MarketPresetTestSuite());
        TestRegistry.register(new ClientMarketTestSuite());
        TestRegistry.register(new NormalizedRandomPriceGeneratorTestSuite());

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
    }
}
