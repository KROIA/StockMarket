package net.kroia.stockmarket.screen.widgets;

import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;

import java.util.List;

public class CandlestickChart extends StockMarketGuiElement
{

    public static final int colorGreen = 0x7F00FF00;
    public static final int colorRed = 0x7FFF0000;

    private PriceHistoryData data;
    int canvasWidth = 100;
    int canvasHeight = 100;
    int canvasX = 0;
    int canvasY = 0;

    int elementWidth = 100;
    int elementHeight = 100;
    int candleWidth = 12;


    long lastMillis = System.currentTimeMillis();
    long candleTimeInterval = 1000;

    public CandlestickChart()
    {

    }

    public void setData(PriceHistoryData data)
    {
        this.data = data;
    }

    @Override
    protected void render()
    {
        if(data==null)
            return;


        List<PriceHistoryData.Candle> candles =  data.getCandles();
        if(!candles.isEmpty())
        {
            long closePrice = data.getCurrentMarketPrice();
            int cancleCount = candles.size();
            for(int i=cancleCount-1; i>=0;i--)
            {
                PriceHistoryData.Candle candle = candles.get(i);
                renderCandlestick(cancleCount - i, candleWidth, candle.open, candle.high, candle.low, closePrice);
                closePrice = candle.open;
            }
        }

        long currentMillis = System.currentTimeMillis();
        if(currentMillis-lastMillis>candleTimeInterval)
        {
            lastMillis = currentMillis;
            data.startNewCandle();
        }
    }

    @Override
    protected void layoutChanged() {
        elementWidth = getWidth();
        elementHeight = getHeight();
    }


    private void renderCandlestick(int candleIndex, int candleWidth, long openPrice, long highPrice, long lowPrice, long closePrice)
    {
        int color = openPrice > closePrice ? colorRed : colorGreen;


        // Draw wick
        int wickYMin = elementHeight - toCanvasSpaceY(lowPrice);
        int wickYMax = elementHeight - toCanvasSpaceY(highPrice);

        // Draw body
        int bodyYMin = elementHeight - toCanvasSpaceY(Math.min(openPrice, closePrice));
        int bodyYMax = elementHeight - toCanvasSpaceY(Math.max(openPrice, closePrice));

        if(bodyYMin == bodyYMax)
        {
            bodyYMin++;
            bodyYMax--;
        }

        int wickWidth = candleWidth / 4;
        if(wickWidth < 1)
        {
            wickWidth = 1;
        }
        else if(wickWidth > 11)
        {
            wickWidth = 11;
        }
        wickWidth |= 1; // Make sure it is odd

        int wickOffset = (candleWidth - wickWidth) / 2;
        if(wickOffset <= 0)
        {
            wickOffset = 0;
        }

        int xOffset = elementWidth - candleIndex * candleWidth;


        if((bodyYMax) - wickYMax > 0) {
            // Wick up
            drawRect(xOffset + wickOffset, bodyYMax,
                    wickWidth, wickYMax-bodyYMax, color);
        }

        if(wickYMin - (bodyYMin) > 0)
        {
            // Wick down
            drawRect(xOffset + wickOffset, bodyYMin,
                    wickWidth, wickYMin-bodyYMin, color);
        }

        drawRect(xOffset, bodyYMin,
                candleWidth, bodyYMax-bodyYMin, color);
    }

    private int toCanvasSpaceY(long input)
    {
        return (int)(input * canvasHeight) / elementHeight;
    }
    private int toCanvasSpaceX(long input)
    {
        return (int)(input * canvasWidth) / elementWidth;
    }
    private long fromCanvasSpaceY(int input)
    {
        return ((long) input * elementHeight) / canvasHeight;
    }
    private long fromCanvasSpaceX(int input)
    {
        return ((long) (input) * canvasWidth) / elementWidth;
    }

    long map(long x, long in_min, long in_max, long out_min, long out_max)
    {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }
}
