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

    public static final TestCategory NEWS = new TestCategory(
            "stockmarket", "sm_news", "News event system data model tests (envelope, definitions, library, records)", BOTH, false);

    public static final TestCategory NEWS_PLUGIN = new TestCategory(
            "stockmarket", "sm_news_plugin", "NewsPlugin scheduler, announce-delay, price-influence and persistence tests", BOTH, false);

    public static final TestCategory NEWS_HISTORY = new TestCategory(
            "stockmarket", "sm_news_history", "NewsHistory capped buffer, pagination and NBT persistence tests", BOTH, false);

    // Chunked history storage (T-110): chunk rotation at 100, cap-driven oldest-chunk
    // drop, sidecar equivalence for picture GC, pre-T-110 single-file migration and
    // lazy older-chunk loading across pagination boundaries. Pure filesystem + NBT
    // logic — no MC context needed.
    public static final TestCategory NEWS_HISTORY_CHUNKS = new TestCategory(
            "stockmarket", "sm_news_history_chunks", "NewsHistoryChunkStore chunk rotation, cap-drop, sidecar equivalence, single-file migration and lazy-load pagination tests", BOTH, false);

    public static final TestCategory NEWS_CLIENT_CACHE = new TestCategory(
            "stockmarket", "sm_news_client_cache", "ClientNewsCache append/cap/newest-first/dedupe/clear/seed and join-time toast catch-up tests", BOTH, false);

    public static final TestCategory NEWS_ADMIN = new TestCategory(
            "stockmarket", "sm_news_admin", "News admin enable/disable gating, cooldown reset, persistence, INFO rendering and EventDetails payload tests", BOTH, false);

    public static final TestCategory NEWS_SCHEDULER = new TestCategory(
            "stockmarket", "sm_news_scheduler", "News scheduler override precedence/validation and pre-scheduled queue (timeline) tests", BOTH, false);

    public static final TestCategory NEWS_PICTURES = new TestCategory(
            "stockmarket", "sm_news_pictures", "News picture library: PNG header parsing, SHA-1 hashing, folder scan validation, picture schema field and defaults extraction tests", BOTH, false);

    public static final TestCategory NEWS_PICTURE_STORE = new TestCategory(
            "stockmarket", "sm_news_picture_store", "Published news-picture store: put/get idempotency, hash verification, retainOnly GC, NewsRecord hash NBT/codec round-trips and publish-time snapshot tests", BOTH, false);

    // World-event registry (T-096): pure in-memory store + NBT round-trips, no MC
    // context needed (the DataManager file wiring is exercised in-game).
    public static final TestCategory NEWS_REGISTRY = new TestCategory(
            "stockmarket", "sm_news_registry", "News world-event registry: fire-record create/update, custom key/value caps, clear ops, unmodifiable views and NBT round-trip tests", BOTH, false);

    // Trigger requirements (T-097): pure predicate engine against an in-memory
    // registry + requires[]/records{} JSON parsing, no MC context needed (same
    // designation as sm_news_registry).
    public static final TestCategory NEWS_REQUIREMENTS = new TestCategory(
            "stockmarket", "sm_news_requirements", "News trigger requirements: fired/count/key predicate semantics, allMet/unmet composition, describe() rendering and requires[]/records{} parse validation tests", BOTH, false);

    // Client picture cache (T-090): pure state-machine/queue/backoff/LRU logic driven
    // through injected fake fetcher+sink (no GL, no networking) plus the newsprint
    // pixel-conversion math — safe to run on BOTH server types without MC context.
    public static final TestCategory NEWS_PICTURE_CLIENT = new TestCategory(
            "stockmarket", "sm_news_picture_client", "Client news-picture cache: fetch state machine, two-priority batching, single in-flight rule, backoff/give-up, LRU texture eviction, releaseAll and newsprint conversion tests", BOTH, false);

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

    public static final TestCategory NEWS_HISTORY_REQUEST = new TestCategory(
            "stockmarket", "sm_news_history_request", "NewsHistoryRequest server-side page answering tests", MASTER_ONLY, true);

    public static final TestCategory NEWS_PICTURE_REQUEST = new TestCategory(
            "stockmarket", "sm_news_picture_request", "NewsPictureRequest hash-batch serving: budget truncation, malformed-hash skipping, wire caps and per-player sliding-window rate limiting tests", MASTER_ONLY, true);

    // Chain runtime (T-098): eligibility filter, publish records, chain parse+validation,
    // chance roll, delay+maturity, caps bypass, depth guard, ancestry cycle, NBT round-trip,
    // admin stop discard, step-start chains.
    public static final TestCategory NEWS_CHAINS = new TestCategory(
            "stockmarket", "sm_news_chains", "News event chain runtime: requirement filter, publish records, chain parse/validation, chance/delay/maturity, caps bypass, depth/ancestry guards, NBT round-trip, admin stop discard and step-start moment tests", BOTH, false);
}
