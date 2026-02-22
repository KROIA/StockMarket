package net.kroia.stockmarket.plugin.plugins.TargetPriceBot;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.minecraft.network.FriendlyByteBuf;

public class TargetPriceBotClientPlugin extends ClientMarketPlugin {


    private final TargetPriceBotPluginGuiElement guiElement;
    private TargetPriceBotPlugin.Settings settings = new TargetPriceBotPlugin.Settings();

    public TargetPriceBotClientPlugin(TradingPair tradingPair, String pluginTypeID) {
        super(tradingPair, pluginTypeID);
        guiElement = new TargetPriceBotPluginGuiElement(this);
        setCustomSettings(settings);
        setCustomSettingsGuiElement(guiElement);
    }

    @Override
    protected void setup() {
        startStream();
    }
    @Override
    protected void close() {
        stopStream();
    }

    @Override
    protected void onStreamPacketReceived(FriendlyByteBuf buf) {
        float targetPrice = buf.readFloat();
        guiElement.setTargetPrice(targetPrice);
    }

    /*@Override
    protected ClientMarketPluginGuiElement getSettingsGuiElement() {
        return guiElement;
    }*/
/*
    @Override
    protected void setCustomSettingsToGuiElement() {
        guiElement.setCustomSettings(settings);
    }

    @Override
    protected void applyCustomSettingsFromGuiElement() {
        guiElement.getCustomSettings(settings);
    }*/



    /*@Override
    protected void encodeSettings(FriendlyByteBuf buf) {
        settings.encode(buf);
    }

    @Override
    protected void decodeSettings(FriendlyByteBuf buf) {
        settings.decode(buf);
    }*/
}
