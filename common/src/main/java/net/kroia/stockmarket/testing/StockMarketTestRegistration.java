package net.kroia.stockmarket.testing;

import net.kroia.modutilities.testing.TestRegistry;
import net.kroia.stockmarket.testing.tests.DynamicIndexedArrayTestSuite;
import net.kroia.stockmarket.testing.tests.MarketIntegrationTestSuite;
import net.kroia.stockmarket.testing.tests.OrderTestSuite;
import net.kroia.stockmarket.testing.tests.PIDTestSuite;
import net.kroia.stockmarket.testing.tests.RandomWalkTestSuite;

public class StockMarketTestRegistration {

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        TestRegistry.register(new OrderTestSuite());
        TestRegistry.register(new DynamicIndexedArrayTestSuite());
        TestRegistry.register(new PIDTestSuite());
        TestRegistry.register(new RandomWalkTestSuite());
        TestRegistry.register(new MarketIntegrationTestSuite());
    }
}
