package net.kroia.stockmarket.plugin.plugins.MarketTrend;

import net.kroia.stockmarket.plugin.base.IMarketPluginInterface;
import net.kroia.stockmarket.plugin.base.MarketPlugin;
import net.kroia.stockmarket.plugin.plugins.MarketTrend.Trend.MarketTrend;
import net.kroia.stockmarket.plugin.plugins.MarketTrend.Trend.StockMarketTrends;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Random;

public class MarketTrendPlugin extends MarketPlugin {

    protected MarketTrend marketTrend;


    @Override
    public void encodeClientStreamData(FriendlyByteBuf buf) {

    }


    @Override
    protected void setup() {
        marketTrend = StockMarketTrends.FLAT;
    }

    @Override
    protected void update() {
        marketTrend.applyImpulse(getPluginInterface());
    }



}
