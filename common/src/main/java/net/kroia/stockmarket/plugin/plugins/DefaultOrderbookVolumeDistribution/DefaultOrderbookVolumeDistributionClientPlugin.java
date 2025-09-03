package net.kroia.stockmarket.plugin.plugins.DefaultOrderbookVolumeDistribution;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.ClientMarketPluginGuiElement;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Tuple;

import java.util.ArrayList;
import java.util.List;

public class DefaultOrderbookVolumeDistributionClientPlugin extends ClientMarketPlugin {

    private final DefaultOrderbookVolumeDistributionPluginGuiElement guiElement;
    private DefaultOrderbookVolumeDistributionPlugin.Settings settings = new DefaultOrderbookVolumeDistributionPlugin.Settings();
    public DefaultOrderbookVolumeDistributionClientPlugin(TradingPair tradingPair, String pluginTypeID) {
        super(tradingPair, pluginTypeID);
        guiElement = new DefaultOrderbookVolumeDistributionPluginGuiElement(this);
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
        if(guiElement == null)
            return;
        int pointCount = buf.readInt();
        List<Tuple<Float, Float>> volumeDistributionChart = new ArrayList<>(pointCount);
        for(int i = 0; i < pointCount; i++)
        {
            float price = buf.readFloat();
            float volume = buf.readFloat();
            volumeDistributionChart.add(new Tuple<>(price, volume));
        }
        guiElement.setVolumeDistributionChart(volumeDistributionChart);
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
