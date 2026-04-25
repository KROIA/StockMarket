package net.kroia.stockmarket.screen.widgets;

import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class OrderbookVolumeHistogram extends StockMarketGuiElement {


    private final CandlestickChart parentChart;

    private int updateRequestTickCounter = 0;
    private int updateRequestTickCounts = 20;
    private double startPrice;
    private double endPrice;
    private float[] volumeChunks;

    private final int greenColor = UI_Colors.buyColorGreen_dark;
    private final int redColor = UI_Colors.sellColorRed;
    private final Rectangle scissorRect = new Rectangle(0,0,0,0);

    public OrderbookVolumeHistogram(CandlestickChart parentChart)
    {
        this.parentChart = parentChart;
    }

    @Override
    protected void renderBackground()
    {
        super.renderBackground();
        @Nullable ClientMarket market = parentChart.getMarket();
        if (market == null)
            return;
        renderHistogram();
    }
    @Override
    protected void render() {
        @Nullable ClientMarket market = parentChart.getMarket();
        if (market == null)
            return;

        updateRequestTickCounter++;
        if(updateRequestTickCounter > updateRequestTickCounts)
        {
            updateRequestTickCounter = 0;
            double startPrice = parentChart.getMinVisiblePrice();
            double endPrice = parentChart.getMaxVisiblePrice();
            market.requestOrderbookVolume(startPrice, endPrice, 50).thenAccept(response -> {
                this.startPrice = response.startPrice();
                this.endPrice = response.endPrice();
                //this.startPrice = startPrice;
                //this.endPrice = endPrice;
                this.volumeChunks = response.volumes();
            });
        }
    }
    @Override
    protected void layoutChanged() {
        setHeight(parentChart.getHeight());

        scissorRect.x=padding;
        scissorRect.y=padding;
        scissorRect.width=getWidth()-2*padding;
        //scissorRect.height=getHeight()-2*padding;
    }

    protected void renderHistogram()
    {
        if(volumeChunks == null)
            return;

        int chunkCount = volumeChunks.length;
        if(chunkCount == 0)
            return;

        //double startPrice = parentChart.getMinVisiblePrice();
        //double endPrice = parentChart.getMaxVisiblePrice();
        double priceDelta = (endPrice - startPrice) / (double)(chunkCount);


        float maxAbsValue = 0;
        for(int i = 0; i < chunkCount; i++)
        {
            maxAbsValue =  Math.max(maxAbsValue, Math.abs(volumeChunks[i]));
        }
        if(maxAbsValue <= 0)
            return;


        float norm = 1/maxAbsValue;
        int width = scissorRect.width;
        float widthF = width;

        scissorRect.height = parentChart.toCanvasSpaceY(parentChart.getMinVisiblePrice())-padding;

        enableScissor(scissorRect);

        int currentY = parentChart.toCanvasSpaceY(startPrice);
        for(int i = 0; i < chunkCount; i++)
        {
            float volume = volumeChunks[i];
            float absVolume = Math.abs(volume);
            int chunkWidth = (int)(absVolume * widthF * norm);
            double price = startPrice + priceDelta*(i+1);
            int endY = parentChart.toCanvasSpaceY(price);

            drawRect(scissorRect.x + width - chunkWidth, currentY, chunkWidth, endY-currentY, (volume>0?greenColor:redColor));

            currentY = endY;
        }

        disableScissor();
    }
}
