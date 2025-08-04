package net.kroia.stockmarket.screen.uiElements.chart;

import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.market.clientdata.OrderReadData;
import net.kroia.stockmarket.market.clientdata.TradingViewData;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketTextMessages;

import java.util.function.BiConsumer;

public class TradingChartWidget extends StockMarketGuiElement {


    public static final int colorGreen = 0x7F00FF00;
    public static final int colorRed = 0x7FFF0000;
    int chartViewMinPrice = 0;
    int chartViewMaxPrice = 1000;
    private int yPadding = 5;
    //private int xPadding = 0;


    private final CandleStickChartWidget candleStickChart;
    private final TradingVolumeHistoryChart tradingVolumeHistoryChart;
    private final OrderbookVolumeChartWidget orderbookVolumeChart;
    public TradingChartWidget(BiConsumer<OrderReadData, Integer> priceChangeCallback) {
        super();

        if(priceChangeCallback == null)
        {
            candleStickChart = new CandleStickChartWidget(this::getChartYPos, this::getPriceFromYPos, colorGreen, colorRed);
        }
        else
        {
            candleStickChart = new CandleStickChartWidget(this::getChartYPos, this::getPriceFromYPos,
                    priceChangeCallback, colorGreen, colorRed);
        }
        //candleStickChart.setTextFontScale(0.5f);

        tradingVolumeHistoryChart = new TradingVolumeHistoryChart();
        tradingVolumeHistoryChart.setEnableBackground(false);
        tradingVolumeHistoryChart.setEnableOutline(false);
        tradingVolumeHistoryChart.setHoverTooltipSupplier(StockMarketTextMessages::getCandlestickChartTooltipTradeVolume);
        tradingVolumeHistoryChart.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP);
        tradingVolumeHistoryChart.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
        orderbookVolumeChart = new OrderbookVolumeChartWidget(this::getChartYPos, colorGreen, colorRed);
        orderbookVolumeChart.setEnableBackground(false);
        orderbookVolumeChart.setEnableOutline(false);
        orderbookVolumeChart.setHoverTooltipSupplier(StockMarketTextMessages::getCandlestickChartTooltipOrderBookVolume);
        orderbookVolumeChart.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP);
        orderbookVolumeChart.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

        addChild(candleStickChart);
        addChild(tradingVolumeHistoryChart);
        addChild(orderbookVolumeChart);
    }
    public TradingChartWidget() {
        this(null);
    }


    @Override
    protected void render() {
        tradingVolumeHistoryChart.setCandleWidth(candleStickChart.getCandleWidth());
        //tradingVolumeHistoryChart.setXPaddingRight(candleStickChart.getChartRightEndPos() - candleStickChart.getWidth());
        tradingVolumeHistoryChart.setXPaddingRight(candleStickChart.getWidth() - candleStickChart.getChartRightEndPos());
    }

    @Override
    protected void layoutChanged() {
        int width = getWidth();
        int height = getHeight();

        int orderBookWidth = Math.min(40, width / 5);
        int volumeHistoryHeight = Math.min(30, height / 5);
        int chartHeight = height - volumeHistoryHeight;
        int chartWidth = width - orderBookWidth;

        candleStickChart.setBounds(0, 0, chartWidth, chartHeight);
        tradingVolumeHistoryChart.setBounds(0, chartHeight, chartWidth, volumeHistoryHeight);
        orderbookVolumeChart.setBounds(chartWidth, 0, orderBookWidth, chartHeight);
    }


    private void setMinMaxPrice(int minPrice, int maxPrice)
    {
        this.chartViewMinPrice = minPrice;
        this.chartViewMaxPrice = maxPrice;

        candleStickChart.setMinMaxPrice(minPrice, maxPrice);
        orderbookVolumeChart.setMinMaxPrice(minPrice, maxPrice);
    }

    public void enableBotTargetPriceDisplay(boolean enabled)
    {
        candleStickChart.enableBotTargetPriceDisplay(enabled);
    }
    public void updateView(TradingViewData data)
    {
        if(data == null)
            return;

        this.setMinMaxPrice(data.orderBookVolumeData.minPrice, data.orderBookVolumeData.maxPrice);
        PriceHistory history = data.priceHistoryData.toHistory();
        candleStickChart.setPriceHistory(history);
        tradingVolumeHistoryChart.setPriceHistory(history);
        orderbookVolumeChart.setOrderBookVolume(data.orderBookVolumeData);
        candleStickChart.setBotTargetPrice(data.botTargetPrice);
        candleStickChart.updateOrderDisplay(data.openOrdersData, data.tradingPairData.toTradingPair());
    }

    /*@Override
    public void renderBackgroundInternal()
    {
        getGraphics().pushPose();
        getGraphics().scale(0.5f,0.5f,1);
        super.renderBackgroundInternal();
        getGraphics().popPose();
    }
    @Override
    public void renderInternal()
    {
        getGraphics().pushPose();
        getGraphics().scale(0.5f,0.5f,1);
        super.renderInternal();
        getGraphics().popPose();
    }*/

    public int getMaxCandleCount()
    {
        return candleStickChart.getMaxCandleCount();
    }
    private static int map(int value, int start1, int stop1, int start2, int stop2)
    {
        return start2 + (int)((float)((stop2 - start2) * ((value - start1)) / (float)(stop1 - start1)));
    }
    private static float mapF(float value, float start1, float stop1, float start2, float stop2)
    {
        return start2 + (float)((stop2 - start2) * ((value - start1)) / (float)(stop1 - start1));
    }

    public int getChartYPos(int price)
    {
        return map(price, chartViewMinPrice, chartViewMaxPrice, candleStickChart.getHeight()-yPadding, yPadding);
    }
    public int getPriceFromYPos(int y)
    {
        return Math.round(mapF(y, candleStickChart.getHeight()-yPadding, yPadding, chartViewMinPrice, chartViewMaxPrice));
    }


}
