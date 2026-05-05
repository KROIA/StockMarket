package net.kroia.stockmarket.testing;

import net.kroia.modutilities.testing.TestCategory;

public class StockMarketTestCategories {

    public static final TestCategory ORDER = new TestCategory(
            "sm_order", "Order class tests",
            TestCategory.ServerType.BOTH, false);

    public static final TestCategory ARRAY = new TestCategory(
            "sm_array", "DynamicIndexedArray tests",
            TestCategory.ServerType.BOTH, false);

    public static final TestCategory PID = new TestCategory(
            "sm_pid", "PID controller tests",
            TestCategory.ServerType.BOTH, false);

    public static final TestCategory RANDOM = new TestCategory(
            "sm_random", "Random walk and price generator tests",
            TestCategory.ServerType.BOTH, false);

    public static final TestCategory PRICE = new TestCategory(
            "sm_price", "Price history data tests",
            TestCategory.ServerType.BOTH, false);

    public static final TestCategory FILTER = new TestCategory(
            "sm_filter", "Data filter tests",
            TestCategory.ServerType.BOTH, false);

    public static final TestCategory UTIL = new TestCategory(
            "sm_util", "Utility tests",
            TestCategory.ServerType.BOTH, false);

    public static final TestCategory MARKET = new TestCategory(
            "sm_market", "Server market tests",
            TestCategory.ServerType.MASTER_ONLY, true);

    public static final TestCategory ORDERBOOK = new TestCategory(
            "sm_orderbook", "Orderbook tests",
            TestCategory.ServerType.MASTER_ONLY, true);

    public static final TestCategory MATCHING = new TestCategory(
            "sm_matching", "Matching engine tests",
            TestCategory.ServerType.MASTER_ONLY, true);

    public static final TestCategory DATABASE = new TestCategory(
            "sm_database", "Database tests",
            TestCategory.ServerType.MASTER_ONLY, true);

    public static final TestCategory REQUEST = new TestCategory(
            "sm_request", "Network request tests",
            TestCategory.ServerType.MASTER_ONLY, true);

    public static final TestCategory PLUGIN = new TestCategory(
            "sm_plugin", "Plugin system tests",
            TestCategory.ServerType.MASTER_ONLY, true);

    public static final TestCategory INTEGRATION = new TestCategory(
            "sm_integration", "Integration tests",
            TestCategory.ServerType.MASTER_ONLY, true);
}
