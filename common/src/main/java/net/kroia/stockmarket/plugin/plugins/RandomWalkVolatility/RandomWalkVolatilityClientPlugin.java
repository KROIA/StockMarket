package net.kroia.stockmarket.plugin.plugins.RandomWalkVolatility;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.ClientMarketPluginGuiElement;
import net.minecraft.network.FriendlyByteBuf;

public class RandomWalkVolatilityClientPlugin extends ClientMarketPlugin {


    private final RandomWalkVolatilityPluginGuiElement guiElement;
    private RandomWalkVolatilityPlugin.Settings settings = new RandomWalkVolatilityPlugin.Settings();

    public RandomWalkVolatilityClientPlugin(TradingPair tradingPair, String pluginTypeID) {
        super(tradingPair, pluginTypeID);
        guiElement = new RandomWalkVolatilityPluginGuiElement(this);
    }

    @Override
    protected void setup() {
        //startStream();
    }
    @Override
    protected void close() {
        //stopStream();
    }

    @Override
    protected void onStreamPacketReceived(FriendlyByteBuf buf) {
        float targetPrice = buf.readFloat();
        guiElement.setTargetPrice(targetPrice);
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
