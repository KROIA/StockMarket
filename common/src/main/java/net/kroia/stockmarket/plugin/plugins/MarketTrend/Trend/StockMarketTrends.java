package net.kroia.stockmarket.plugin.plugins.MarketTrend.Trend;

public class StockMarketTrends {
    public static final MarketTrend UPTURN = new MarketTrend(new float[]{.6F, .3F, .1F}, .5F, 1.0F);
    public static final MarketTrend DOWNTURN = new MarketTrend(new float[]{.1F, .3F, .6F}, .5F, 1.0F);
    public static final MarketTrend EXPLOSION = new MarketTrend(new float[]{.85F, .10F, .05F}, 2.0F, .33F);
    public static final MarketTrend CRASH = new MarketTrend(new float[]{.05F, .10F, .85F}, 2.0F, .33F);
    public static final MarketTrend FLAT = new MarketTrend(new float[]{.10F, .80F, .10F}, .1F, 2.0F);
    public static final MarketTrend ERRATIC = new MarketTrend(new float[]{.33F, .33F, .33F}, 1.0F, .5F);
}
