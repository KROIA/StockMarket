package net.kroia.stockmarket.plugin.plugins.DefaultOrderbookVolumeDistribution;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.ClientMarketPluginGuiElement;
import net.minecraft.network.FriendlyByteBuf;

public class DefaultOrderbookVolumeDistributionClientPlugin extends ClientMarketPlugin {

    public DefaultOrderbookVolumeDistributionClientPlugin(TradingPair tradingPair, String pluginTypeID) {
        super(tradingPair, pluginTypeID);
    }

    @Override
    protected void setup() {

    }

    @Override
    protected void onStreamPacketReceived(FriendlyByteBuf buf) {

    }

    @Override
    protected ClientMarketPluginGuiElement getSettingsGuiElement() {
        return null;
    }

    @Override
    protected void setSettingsToGuiElement(ClientMarketPluginGuiElement element) {

    }

    @Override
    protected void applySettingsFromGuiElement(ClientMarketPluginGuiElement element) {

    }


    @Override
    protected void encodeSettings(FriendlyByteBuf buf) {

    }

    @Override
    protected void decodeSettings(FriendlyByteBuf buf) {

    }




}
