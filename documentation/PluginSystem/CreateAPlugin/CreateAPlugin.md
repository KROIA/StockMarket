# Create a Plugin


## Content


---

## Example Plugin
### Creating 
To create a plugin, 3 files are needed. Check out each file to find out how a simple plugin gets implemented.
- [ExampleClientPlugin](ExamplePlugin/ExampleClientPlugin.md) is a client side manager for a plugin instance running on the server
- [ExamplePlugin](ExamplePlugin/ExamplePlugin.md) is the plugin instance, running on the server
- [ExamplePluginGuiElement](ExamplePlugin/ExamplePluginGuiElement.md) is the graphical user inrerface widget that contains the setting elements for the management UI

---
### Register
[Register a Plugin](RegisterAPlugin.md)


---
### Instantiating a Plugin
``` Java
/**
 * Called when a new market is created, to add default plugins to it
 * @param market
 */
public static void onMarketCreated(IServerMarket market)
{
    TradingPair pair = market.getTradingPair();
    ServerPluginManager mngr = BACKEND_INSTANCES.SERVER_PLUGIN_MANAGER; // The ServerPluginManager is needed

    // Instantiate a plugin for the specified trading pair
    // EXAMPLE_PLUGIN is the PluginRegistry.MarketPluginRegistrationObject
    mngr.createMarketPlugin(pair, Plugins.EXAMPLE_PLUGIN); 
}
```