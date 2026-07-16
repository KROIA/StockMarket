package net.kroia.stockmarket.testing;

import net.kroia.modutilities.testing.TestCategory;

import static net.kroia.modutilities.testing.TestCategory.ServerType.BOTH;
import static net.kroia.modutilities.testing.TestCategory.ServerType.MASTER_ONLY;

public class StockMarketTestCategories {

    // Common tests (ServerType.BOTH, needsMinecraftContext=false)
    public static final TestCategory ORDER = new TestCategory(
            "stockmarket", "sm_order", "Order class tests", BOTH, false);

    public static final TestCategory ARRAY = new TestCategory(
            "stockmarket", "sm_array", "DynamicIndexedArray tests", BOTH, false);

    public static final TestCategory PID = new TestCategory(
            "stockmarket", "sm_pid", "PID controller tests", BOTH, false);

    public static final TestCategory RANDOM = new TestCategory(
            "stockmarket", "sm_random", "Random walk and price generator tests", BOTH, false);

    public static final TestCategory PRICE = new TestCategory(
            "stockmarket", "sm_price", "Price history data tests", BOTH, false);

    public static final TestCategory FILTER = new TestCategory(
            "stockmarket", "sm_filter", "Data filter tests", BOTH, false);

    public static final TestCategory UTIL = new TestCategory(
            "stockmarket", "sm_util", "Utility tests", BOTH, false);

    public static final TestCategory INTER_MARKET_ORDER = new TestCategory(
            "stockmarket", "sm_inter_market_order", "InterMarketOrder tests", BOTH, false);

    public static final TestCategory TRADING_PAIR = new TestCategory(
            "stockmarket", "sm_trading_pair", "TradingPair record tests", BOTH, false);

    public static final TestCategory VIRTUAL_ORDERBOOK = new TestCategory(
            "stockmarket", "sm_virtual_orderbook", "VirtualOrderbook tests", BOTH, false);

    public static final TestCategory PRICE_HISTORY = new TestCategory(
            "stockmarket", "sm_price_history", "Price history data tests", BOTH, false);

    public static final TestCategory USER = new TestCategory(
            "stockmarket", "sm_user", "User class tests", BOTH, false);

    public static final TestCategory PLAYER_PREFERENCES = new TestCategory(
            "stockmarket", "sm_player_preferences", "PlayerPreferences tests", BOTH, false);

    public static final TestCategory MARKET_SETTINGS = new TestCategory(
            "stockmarket", "sm_market_settings", "MarketSettings tests", BOTH, false);

    public static final TestCategory CLIENT_MARKET = new TestCategory(
            "stockmarket", "sm_client_market", "ClientMarket tests", BOTH, false);

    public static final TestCategory MARKET_PRESET = new TestCategory(
            "stockmarket", "sm_market_preset", "Market preset system tests", BOTH, false);

    public static final TestCategory VILLAGER_PRICING = new TestCategory(
            "stockmarket", "sm_villager_pricing", "Villager trade pricing / currency fitting tests", BOTH, false);

    public static final TestCategory VILLAGER_MONEY_PAYMENT = new TestCategory(
            "stockmarket", "sm_villager_money_payment", "Value-based merchant money payment tests", BOTH, false);

    public static final TestCategory VOLATILITY_PLUGIN = new TestCategory(
            "stockmarket", "sm_volatility_plugin", "VolatilityPlugin flow-equilibrium price tests", BOTH, false);

    // Master-only tests (ServerType.MASTER_ONLY, needsMinecraftContext=true)
    public static final TestCategory MARKET = new TestCategory(
            "stockmarket", "sm_market", "Server market tests", MASTER_ONLY, true);

    public static final TestCategory MATCHING_ENGINE = new TestCategory(
            "stockmarket", "sm_matching_engine", "Matching engine tests", MASTER_ONLY, true);

    public static final TestCategory ORDERBOOK_TEST = new TestCategory(
            "stockmarket", "sm_orderbook", "Orderbook tests", MASTER_ONLY, true);

    public static final TestCategory CREATE_ORDER_REQUEST = new TestCategory(
            "stockmarket", "sm_create_order_request", "CreateOrderRequest tests", MASTER_ONLY, true);

    public static final TestCategory ACTIVE_ORDERS_REQUEST = new TestCategory(
            "stockmarket", "sm_active_orders_request", "ActiveOrdersRequest tests", MASTER_ONLY, true);

    public static final TestCategory DATABASE_TEST = new TestCategory(
            "stockmarket", "sm_database", "Database tests", MASTER_ONLY, true);

    public static final TestCategory MARKET_PRICE_MANAGER = new TestCategory(
            "stockmarket", "sm_market_price_manager", "MarketPriceManager tests", MASTER_ONLY, true);

    public static final TestCategory ORDER_RECORD_MANAGER = new TestCategory(
            "stockmarket", "sm_order_record_manager", "OrderRecordManager tests", MASTER_ONLY, true);

    public static final TestCategory SERVER_MARKET = new TestCategory(
            "stockmarket", "sm_server_market", "ServerMarket tests", MASTER_ONLY, true);

    public static final TestCategory PLUGIN_TEST = new TestCategory(
            "stockmarket", "sm_plugin", "Plugin system tests", MASTER_ONLY, true);

    public static final TestCategory INTEGRATION = new TestCategory(
            "stockmarket", "sm_integration", "Integration tests", MASTER_ONLY, true);

    public static final TestCategory DEPTH_SIMULATION = new TestCategory(
            "stockmarket", "sm_depth_simulation", "DepthSimulation read-only walk tests", MASTER_ONLY, true);

    public static final TestCategory INTER_MARKET_EXECUTOR = new TestCategory(
            "stockmarket", "sm_inter_market_executor", "InterMarketExecutor two-leg execution tests", MASTER_ONLY, true);

    public static final TestCategory MERGE_CONSOLIDATION = new TestCategory(
            "stockmarket", "sm_merge_consolidation", "BankSystem ItemID-merge market consolidation tests", MASTER_ONLY, true);

    public static final TestCategory VILLAGER_REWRITE = new TestCategory(
            "stockmarket", "sm_villager_rewrite", "Villager trade offer rewrite tests", MASTER_ONLY, true);

    public static final TestCategory MOD_SETTINGS = new TestCategory(
            "stockmarket", "sm_mod_settings", "Mod settings JSON round-trip and sanitize-bounds tests", BOTH, false);
}
