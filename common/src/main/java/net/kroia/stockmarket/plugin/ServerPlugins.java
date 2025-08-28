package net.kroia.stockmarket.plugin;

import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.api.IServerMarket;
import net.kroia.stockmarket.plugin.base.MarketPlugin;
import net.kroia.stockmarket.plugin.plugins.DefaultOrderbookVolumeDistributionPlugin;

public class ServerPlugins {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        MarketPlugin.setBackend(backend);
    }

    public static final ServerPluginRegistry.MarketPluginRegistrationObject DEFAULT_ORDERBOOK_VOLUME_DISTRIBUTION =
            ServerPluginRegistry.registerPlugin(DefaultOrderbookVolumeDistributionPlugin.class.getName(), DefaultOrderbookVolumeDistributionPlugin::new);

    public static void serverSetup()
    {
        BACKEND_INSTANCES.SERVER_EVENTS.MARKET_CREATED.addListener(ServerPlugins::onMarketCreated);
    }

    public static void onMarketCreated(IServerMarket market)
    {
        BACKEND_INSTANCES.SERVER_PLUGIN_MANAGER.createMarketPlugin(market.getTradingPair(), DEFAULT_ORDERBOOK_VOLUME_DISTRIBUTION, "DefaultOrderbookVolumeDistribution" );
    }
}
