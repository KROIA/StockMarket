package net.kroia.stockmarket.pluginsystem;

import net.kroia.stockmarket.pluginsystem.plugin.ClientPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.DefaultOrderbookVolumeDistributionPlugin;
import net.kroia.stockmarket.pluginsystem.plugins.TargetPriceBot;
import net.kroia.stockmarket.pluginsystem.plugins.VolatilityPlugin;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistry;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;

public class Plugins {

    public static final PluginRegistryObject VOLATILITY_PLUGIN = PluginRegistry.registerPlugin(
            VolatilityPlugin.class.getName(),
            "VolatilityPlugin",
            "A plugin that adds random walk based volatility to the target price.",
            VolatilityPlugin::new,
            ClientPlugin::new,
            PluginGuiElement::new
    );

    public static final PluginRegistryObject TARGET_PRICE_BOT_PLUGIN = PluginRegistry.registerPlugin(
            TargetPriceBot.class.getName(),
            "TargetPriceBot",
            "A bot that tries to move the price towards a target price using a PID controller.",
            TargetPriceBot::new,
            ClientPlugin::new,
            PluginGuiElement::new
    );

    public static final PluginRegistryObject DEFAULT_ORDERBOOK_VOLUME_DISTRIBUTION_PLUGIN = PluginRegistry.registerPlugin(
            DefaultOrderbookVolumeDistributionPlugin.class.getName(),
            "DefaultOrderbookVolumeDistributionPlugin",
            "Automatically fills the orderbook with a reasonable volume distribution.",
            DefaultOrderbookVolumeDistributionPlugin::new,
            ClientPlugin::new,
            PluginGuiElement::new
    );

    public static void serverSetup()
    {

    }

    public static void clientSetup()
    {

    }
}
