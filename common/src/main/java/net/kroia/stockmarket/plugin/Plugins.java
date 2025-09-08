package net.kroia.stockmarket.plugin;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.MarketPlugin;
import net.kroia.stockmarket.plugin.plugins.DefaultOrderbookVolumeDistribution.DefaultOrderbookVolumeDistributionClientPlugin;
import net.kroia.stockmarket.plugin.plugins.DefaultOrderbookVolumeDistribution.DefaultOrderbookVolumeDistributionPlugin;
import net.kroia.stockmarket.plugin.plugins.RandomWalkVolatility.RandomWalkVolatilityPlugin;
import net.kroia.stockmarket.plugin.plugins.RandomWalkVolatility.RandomWalkVolatilityClientPlugin;
import net.kroia.stockmarket.plugin.plugins.TargetPriceBot.TargetPriceBotClientPlugin;
import net.kroia.stockmarket.plugin.plugins.TargetPriceBot.TargetPriceBotPlugin;

/**
 * Registers all built-in plugins
 */
public class Plugins {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        MarketPlugin.setBackend(backend);
    }

    public static final PluginRegistry.MarketPluginRegistrationObject DEFAULT_ORDERBOOK_VOLUME_DISTRIBUTION =
            PluginRegistry.registerPlugin(
                    DefaultOrderbookVolumeDistributionPlugin.class.getName(),
                    DefaultOrderbookVolumeDistributionPlugin::new,
                    DefaultOrderbookVolumeDistributionClientPlugin::new,
                    DefaultOrderbookVolumeDistributionPlugin.getNameStatic(),
                    DefaultOrderbookVolumeDistributionPlugin.getDescriptionStatic());


    public static final PluginRegistry.MarketPluginRegistrationObject TARGET_PRICE_BOT =
            PluginRegistry.registerPlugin(
                    TargetPriceBotPlugin.class.getName(),
                    TargetPriceBotPlugin::new,
                    TargetPriceBotClientPlugin::new,
                    TargetPriceBotPlugin.getNameStatic(),
                    TargetPriceBotPlugin.getDescriptionStatic());


    public static final PluginRegistry.MarketPluginRegistrationObject RANDOM_WALK_VOLATILITY =
            PluginRegistry.registerPlugin(
                    RandomWalkVolatilityPlugin.class.getName(),
                    RandomWalkVolatilityPlugin::new,
                    RandomWalkVolatilityClientPlugin::new,
                    RandomWalkVolatilityPlugin.getNameStatic(),
                    RandomWalkVolatilityPlugin.getDescriptionStatic());

    public static void serverSetup()
    {
        BACKEND_INSTANCES.SERVER_EVENTS.MARKET_CREATED.addListener(Plugins::onMarketCreated);
    }


    /**
     * Called when a new market is created, to add default plugins to it
     * @param market
     */
    public static void onMarketCreated(IServerMarket market)
    {
        TradingPair pair = market.getTradingPair();
        ServerPluginManager mngr = BACKEND_INSTANCES.SERVER_PLUGIN_MANAGER;
        mngr.createMarketPlugin(pair, DEFAULT_ORDERBOOK_VOLUME_DISTRIBUTION);
        mngr.createMarketPlugin(pair, TARGET_PRICE_BOT);
        mngr.createMarketPlugin(pair, RANDOM_WALK_VOLATILITY);
    }
}
