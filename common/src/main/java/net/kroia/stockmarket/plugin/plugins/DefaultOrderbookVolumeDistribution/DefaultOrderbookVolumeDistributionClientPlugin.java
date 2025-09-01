package net.kroia.stockmarket.plugin.plugins.DefaultOrderbookVolumeDistribution;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.ClientMarketPluginGuiElement;
import net.minecraft.network.FriendlyByteBuf;

public class DefaultOrderbookVolumeDistributionClientPlugin extends ClientMarketPlugin {

    private final DefaultOrderbookVolumeDistributionPluginGuiElement guiElement;
    private DefaultOrderbookVolumeDistributionPlugin.Settings settings = new DefaultOrderbookVolumeDistributionPlugin.Settings();
    public DefaultOrderbookVolumeDistributionClientPlugin(TradingPair tradingPair, String pluginTypeID) {
        super(tradingPair, pluginTypeID);
        guiElement = new DefaultOrderbookVolumeDistributionPluginGuiElement(this);
    }

    @Override
    protected void setup() {

    }

    @Override
    protected void onStreamPacketReceived(FriendlyByteBuf buf) {

    }

    @Override
    protected ClientMarketPluginGuiElement getSettingsGuiElement() {
        return guiElement;
    }

    @Override
    protected void setSettingsToGuiElement() {
        guiElement.setSettings(settings);
    }

    @Override
    protected void applySettingsFromGuiElement() {
        settings = guiElement.getSettings();
    }


    @Override
    protected void encodeSettings(FriendlyByteBuf buf) {
        settings.encode(buf);
    }

    @Override
    protected void decodeSettings(FriendlyByteBuf buf) {
        settings.decode(buf);
    }




}
