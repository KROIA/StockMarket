package net.kroia.stockmarket.plugin.plugins.MarketTrend.Trend;

import net.kroia.stockmarket.plugin.base.IMarketPluginInterface;

public interface IMarketTrend {
    public void applyImpulse(IMarketPluginInterface market);

    public int getAction(float random);

    public float[] getTrendWeights();
}
