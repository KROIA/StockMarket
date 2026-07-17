package net.kroia.stockmarket.pluginsystem;

import net.kroia.stockmarket.pluginsystem.plugins.DefaultOrderbookVolumeDistributionPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.NewsPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.TargetPriceBot;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistry;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;

/**
 * Registers all built-in plugins.
 * Server factories are set at class load time (safe for dedicated server).
 * GUI element factories are set in clientSetup() to avoid loading client classes on server.
 */
public class Plugins {

    public static final PluginRegistryObject VOLATILITY_PLUGIN = PluginRegistry.registerPlugin(
            VolatilityPlugin.class.getName(),
            "VolatilityPlugin",
            "A plugin that adds random walk based volatility to the target price.",
            VolatilityPlugin::new
    );

    public static final PluginRegistryObject TARGET_PRICE_BOT_PLUGIN = PluginRegistry.registerPlugin(
            TargetPriceBot.class.getName(),
            "TargetPriceBot",
            "A bot that tries to move the price towards a target price using a PID controller.",
            TargetPriceBot::new
    );

    public static final PluginRegistryObject DEFAULT_ORDERBOOK_VOLUME_DISTRIBUTION_PLUGIN = PluginRegistry.registerPlugin(
            DefaultOrderbookVolumeDistributionPlugin.class.getName(),
            "DefaultOrderbookVolumeDistributionPlugin",
            "Automatically fills the orderbook with a reasonable volume distribution.",
            DefaultOrderbookVolumeDistributionPlugin::new
    );

    public static final PluginRegistryObject NEWS_PLUGIN = PluginRegistry.registerPlugin(
            NewsPlugin.class.getName(),
            "NewsPlugin",
            "Randomly publishes news events (defined in config/StockMarket/news/) that temporarily or permanently move market prices.",
            NewsPlugin::new
    );

    public static void serverSetup() {
    }

    /**
     * Registers GUI element factories for plugins that have custom UI.
     * Called only on the client side — never on a dedicated server.
     */
    public static void clientSetup() {
        // Import is safe here because clientSetup() only runs on client
        TARGET_PRICE_BOT_PLUGIN.setGuiElementFactory(
                net.kroia.stockmarket.pluginsystem.plugins.screen.TargetPriceBotGuiElement::new
        );
        VOLATILITY_PLUGIN.setGuiElementFactory(
                net.kroia.stockmarket.pluginsystem.plugins.screen.VolatilityPluginGuiElement::new
        );
        DEFAULT_ORDERBOOK_VOLUME_DISTRIBUTION_PLUGIN.setGuiElementFactory(
                net.kroia.stockmarket.pluginsystem.plugins.screen.VolumeDistributionGuiElement::new
        );
        NEWS_PLUGIN.setGuiElementFactory(
                net.kroia.stockmarket.pluginsystem.plugins.screen.NewsPluginGuiElement::new
        );
    }
}
