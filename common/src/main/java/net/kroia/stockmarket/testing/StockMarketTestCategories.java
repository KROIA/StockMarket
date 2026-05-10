package net.kroia.stockmarket.testing;

import net.kroia.modutilities.testing.TestCategory;

import static net.kroia.modutilities.testing.TestCategory.ServerType.BOTH;
import static net.kroia.modutilities.testing.TestCategory.ServerType.MASTER_ONLY;

public class StockMarketTestCategories {

    // Common tests (ServerType.BOTH, needsMinecraftContext=false)
    public static final TestCategory ORDER = new TestCategory(
            "sm_order", "Order class tests", BOTH, false);

    public static final TestCategory ARRAY = new TestCategory(
            "sm_array", "DynamicIndexedArray tests", BOTH, false);

    public static final TestCategory PID = new TestCategory(
            "sm_pid", "PID controller tests", BOTH, false);

    public static final TestCategory RANDOM = new TestCategory(
            "sm_random", "Random walk and price generator tests", BOTH, false);

    public static final TestCategory PRICE = new TestCategory(
            "sm_price", "Price history data tests", BOTH, false);

    public static final TestCategory FILTER = new TestCategory(
            "sm_filter", "Data filter tests", BOTH, false);

    public static final TestCategory UTIL = new TestCategory(
            "sm_util", "Utility tests", BOTH, false);

    public static final TestCategory INTER_MARKET_ORDER = new TestCategory(
            "sm_inter_market_order", "InterMarketOrder tests", BOTH, false);

    public static final TestCategory VIRTUAL_ORDERBOOK = new TestCategory(
            "sm_virtual_orderbook", "VirtualOrderbook tests", BOTH, false);

    public static final TestCategory PRICE_HISTORY = new TestCategory(
            "sm_price_history", "Price history data tests", BOTH, false);

    public static final TestCategory USER = new TestCategory(
            "sm_user", "User class tests", BOTH, false);

    public static final TestCategory MARKET_SETTINGS = new TestCategory(
            "sm_market_settings", "MarketSettings tests", BOTH, false);

    public static final TestCategory CLIENT_MARKET = new TestCategory(
            "sm_client_market", "ClientMarket tests", BOTH, false);

    // Master-only tests (ServerType.MASTER_ONLY, needsMinecraftContext=true)
    public static final TestCategory MARKET = new TestCategory(
            "sm_market", "Server market tests", MASTER_ONLY, true);

    public static final TestCategory MATCHING_ENGINE = new TestCategory(
            "sm_matching_engine", "Matching engine tests", MASTER_ONLY, true);

    public static final TestCategory ORDERBOOK_TEST = new TestCategory(
            "sm_orderbook", "Orderbook tests", MASTER_ONLY, true);

    public static final TestCategory CREATE_ORDER_REQUEST = new TestCategory(
            "sm_create_order_request", "CreateOrderRequest tests", MASTER_ONLY, true);

    public static final TestCategory ACTIVE_ORDERS_REQUEST = new TestCategory(
            "sm_active_orders_request", "ActiveOrdersRequest tests", MASTER_ONLY, true);

    public static final TestCategory DATABASE_TEST = new TestCategory(
            "sm_database", "Database tests", MASTER_ONLY, true);

    public static final TestCategory MARKET_PRICE_MANAGER = new TestCategory(
            "sm_market_price_manager", "MarketPriceManager tests", MASTER_ONLY, true);

    public static final TestCategory ORDER_RECORD_MANAGER = new TestCategory(
            "sm_order_record_manager", "OrderRecordManager tests", MASTER_ONLY, true);

    public static final TestCategory SERVER_MARKET = new TestCategory(
            "sm_server_market", "ServerMarket tests", MASTER_ONLY, true);

    public static final TestCategory PLUGIN_TEST = new TestCategory(
            "sm_plugin", "Plugin system tests", MASTER_ONLY, true);

    public static final TestCategory INTEGRATION = new TestCategory(
            "sm_integration", "Integration tests", MASTER_ONLY, true);
}
