# Register a Plugin
Inside this mod there is a class called **Plugins**. This class contains the registry objects for each plugin.
Each plugin needs one registration object.

This class subscribes to the **SERVER_EVENTS.MARKET_CREATED** to get notified once a market gets created.
When a market gets created, the plugin gets added to that market. 


``` Java
/**
 * Registers all built-in plugins
 */
public class Plugins {
    private static StockMarketModBackend.Instances BACKEND_INSTANCES;
    public static void setBackend(StockMarketModBackend.Instances backend) {
        BACKEND_INSTANCES = backend;
        MarketPlugin.setBackend(backend);
    }

    // Creating a registration object
    public static final PluginRegistry.MarketPluginRegistrationObject EXAMPLE_PLUGIN =
            PluginRegistry.registerPlugin(
                    ExamplePlugin.class.getName(),
                    ExamplePlugin::new,
                    ExampleClientPlugin::new,
                    ExamplePlugin.getNameStatic(),
                    ExamplePlugin.getDescriptionStatic());


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

        // Instantiate a plugin for the specified trading pair
        mngr.createMarketPlugin(pair, EXAMPLE_PLUGIN);
    }
}
```