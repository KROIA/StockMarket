package net.kroia.stockmarket.screen.widgets;

import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.modutilities.gui.geometry.RectangleF;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.List;

/**
 * todo:
 * - Scaling the price data is currently done very simple
 *   Improvement is needed in order to be able to translate and scale the chart view
 *
 * - Vertical and maybe horizontal axis is not implemented yet
 */
public class CandlestickChart extends StockMarketGuiElement
{

    public static final int colorGreen          = 0x7F00FF00;
    public static final int colorRed            = 0x7FFF0000;
    public static final int colorHorizontalLine = 0x20202020;
    public static final int colorZeroLine       = 0x05000000;

    private @Nullable PriceHistoryData data;
    //int canvasWidth = 100;
    //int canvasHeight = 100;
    //int elementWidth = 100;
    //int elementHeight = 100;

    int candleWidth = 12;
    private final Rectangle canvasRect = new Rectangle(0,0,0,0);
    private final RectangleF chartviewRect = new RectangleF(200,0,-200,500);
    private final Point lastDragMousePos = new Point();
    private int maxPriceLabelTextWidth = 0;
    private int firstVisibleCandleIndex = 0;
    private int lastVisibleCandleIndex = 0;


    public CandlestickChart()
    {

    }

    /**
     * Sets the reference to the client markets price history data to visualize as candle stick chart
     * @param data
     */
    public void setData(@Nullable PriceHistoryData data)
    {
        this.data = data;
    }

    @Override
    protected void layoutChanged() {
        //canvasRect.width = getWidth() - maxPriceLabelTextWidth;
        canvasRect.height = -getHeight();

        //chartviewRect.width = (double) canvasRect.width /candleWidth;
    }

    @Override
    protected void renderBackground()
    {
        super.renderBackground();
        drawRect(canvasRect.x, toCanvasSpaceY(0)-1 , canvasRect.width, 2, colorZeroLine); // Horizontal zero line


        int targetLineCount = 8; // how many lines you want roughly visible

        double visiblePriceRange = chartviewRect.height;

        // Raw step if we just divided evenly
        double rawStep = visiblePriceRange / targetLineCount;

        // Round to a "nice" number: nearest 1, 2, or 5 times a power of 10
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double normalized = rawStep / magnitude; // will be between 1 and 10

        double niceStep;
        if      (normalized < 1.5) niceStep = 1  * magnitude;
        else if (normalized < 3.5) niceStep = 2  * magnitude;
        else if (normalized < 7.5) niceStep = 5  * magnitude;
        else                       niceStep = 10 * magnitude;

        // Find the first price level just below the visible bottom
        double firstLine = Math.floor(chartviewRect.y / niceStep) * niceStep;

        // Draw all lines within the visible range
        double topPrice = chartviewRect.y + chartviewRect.height;
        maxPriceLabelTextWidth = 0;
        for (double price = firstLine; price <= topPrice; price += niceStep)
        {
            renderHorizontalValueLabel(price);
        }
        maxPriceLabelTextWidth += 10;
        canvasRect.width = getWidth() - maxPriceLabelTextWidth;
        for (double price = firstLine; price <= topPrice; price += niceStep)
        {
            renderHorizontalValueLine(price);
        }
    }

    @Override
    protected void render()
    {
        if(data==null)
            return;

        candleWidth = ((int) Math.abs((canvasRect.width*2 / chartviewRect.width)))/2;


        List<PriceHistoryData.Candle> candles =  data.getCandles();
        if(!candles.isEmpty())
        {
            double closePrice = data.getCurrentMarketRealPrice();
            int candleCount = candles.size();

            boolean startedVisible = false;
            //for(int i=candleCount-1; i>=0;i--)
            firstVisibleCandleIndex = 0;
            for(int i=candleCount-1; i>=0; i--)
            {
                PriceHistoryData.Candle candle = candles.get(i);
                boolean isVisible = renderCandlestick(candleCount-1 - i,
                        data.toRealPrice(candle.open),
                        data.toRealPrice(candle.high),
                        data.toRealPrice(candle.low),
                        closePrice);
                if(isVisible && !startedVisible) {
                    lastVisibleCandleIndex = i;
                    startedVisible = true;
                }
                if(startedVisible && !isVisible)
                {
                    firstVisibleCandleIndex = i+1;
                    break;
                }
                closePrice = data.toRealPrice(candle.open);
            }
        }
    }


    private void renderHorizontalValueLabel(double price)
    {
        if(price < 0)
            return; // Ignore negative prices
        int yPos = toCanvasSpaceY(price);
        String priceLabel = formatPrice(price);
        int textWidth = getTextWidth(priceLabel);
        int textHeight = getTextHeight();
        drawText(priceLabel, canvasRect.x + canvasRect.width + 5, yPos - textHeight/2);

        maxPriceLabelTextWidth = Math.max(maxPriceLabelTextWidth, textWidth);
    }
    private void renderHorizontalValueLine(double price)
    {
        if(price <= 0)
            return; // Ignore negative prices
        int yPos = toCanvasSpaceY(price);
        drawRect(canvasRect.x, yPos-1, canvasRect.width, 2, colorHorizontalLine);
    }

    private String formatPrice(double price)
    {
        if      (price >= 1_000_000_000) return formatCompact(price / 1_000_000_000, "B");
        else if (price >= 1_000_000)     return formatCompact(price / 1_000_000,     "M");
        else if (price >= 1_000)         return formatCompact(price / 1_000,         "k");
        else /*if (price >= 1)*/          return String.format("%6.2f",  price);
        //else if (price >= 0.01)      return String.format("%6.4f",  price);
        //else                             return String.format("%6.6f",  price);
    }

    private String formatCompact(double value, String suffix)
    {
        // Use decimals only if needed (1k vs 1.5k)
        if      (value >= 100) return String.format("%3.0f%s", value, suffix);  // 100k
        else if (value >= 10)  return String.format("%4.1f%s", value, suffix);  // 10.5k
        else                   return String.format("%4.2f%s", value, suffix);  // 1.50k
    }

    private boolean renderCandlestick(int candleIndex, double openPrice, double highPrice, double lowPrice, double closePrice)
    {
        int xOffset = toCanvasSpaceX(candleIndex) - candleWidth;
        if(xOffset < canvasRect.x || xOffset > canvasRect.x + canvasRect.width)
            return false; // candle outside canvas

        int color = openPrice > closePrice ? colorRed : colorGreen;


        // Draw wick
        int wickYMin = toCanvasSpaceY(lowPrice);
        int wickYMax = toCanvasSpaceY(highPrice);

        // Draw body
        int bodyYMin = toCanvasSpaceY(Math.min(openPrice, closePrice));
        int bodyYMax = toCanvasSpaceY(Math.max(openPrice, closePrice));

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

        return true;
    }


    @Override
    protected boolean mouseScrolledOverElement(double delta) {
        double zoomFactor = (delta > 0) ? 0.9 : 1.1; // scroll up = zoom in (shrink rect)
        // Get mouse position in world space BEFORE zooming
        double mouseWorldX = fromCanvasSpaceX(getMouseX());
        double mouseWorldY = fromCanvasSpaceY(getMouseY());

        if (isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT))
        {
            // Scale the rect
            chartviewRect.width  *= zoomFactor;
            //chartviewRect.height  *= zoomFactor;

            // Shift the rect so the point under the mouse stays fixed:
            // mouseWorldX must satisfy: mouseWorldX = chartviewRect.x + mouseNormX * newWidth
            // where mouseNormX = (mouseWorldX - oldX) / oldWidth  (preserved)
            double mouseNormX = (mouseWorldX - chartviewRect.x) / (chartviewRect.width / zoomFactor);
            //double mouseNormY = (mouseWorldY - chartviewRect.y) / (chartviewRect.height / zoomFactor);

            chartviewRect.x = mouseWorldX - mouseNormX * chartviewRect.width;
            //chartviewRect.y = mouseWorldY - mouseNormY * chartviewRect.height;

            if(chartviewRect.x < -chartviewRect.width)
                chartviewRect.x = -chartviewRect.width;
            return true;
        }
        else  if (isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL))
        {
            // Scale the rect
            //chartviewRect.width  *= zoomFactor;
            chartviewRect.height  *= zoomFactor;

            // Shift the rect so the point under the mouse stays fixed:
            // mouseWorldX must satisfy: mouseWorldX = chartviewRect.x + mouseNormX * newWidth
            // where mouseNormX = (mouseWorldX - oldX) / oldWidth  (preserved)
            //double mouseNormX = (mouseWorldX - chartviewRect.x) / (chartviewRect.width / zoomFactor);
            double mouseNormY = (mouseWorldY - chartviewRect.y) / (chartviewRect.height / zoomFactor);

            //chartviewRect.x = mouseWorldX - mouseNormX * chartviewRect.width;
            chartviewRect.y = mouseWorldY - mouseNormY * chartviewRect.height;
            return true;
        }else
        {
            // Scale the rect
            chartviewRect.width  *= zoomFactor;
            chartviewRect.height  *= zoomFactor;

            // Shift the rect so the point under the mouse stays fixed:
            // mouseWorldX must satisfy: mouseWorldX = chartviewRect.x + mouseNormX * newWidth
            // where mouseNormX = (mouseWorldX - oldX) / oldWidth  (preserved)
            double mouseNormX = (mouseWorldX - chartviewRect.x) / (chartviewRect.width / zoomFactor);
            double mouseNormY = (mouseWorldY - chartviewRect.y) / (chartviewRect.height / zoomFactor);

            chartviewRect.x = mouseWorldX - mouseNormX * chartviewRect.width;
            chartviewRect.y = mouseWorldY - mouseNormY * chartviewRect.height;

            if(chartviewRect.x < -chartviewRect.width)
                chartviewRect.x = -chartviewRect.width;
            return true;
        }
        //return false;
    }

    @Override
    protected boolean mouseClickedOverElement(int button) {
        lastDragMousePos.x = getMouseX();
        lastDragMousePos.y = getMouseY();
        return false;
    }
    @Override
    protected boolean mouseDragged(int button, double deltaX, double deltaY) {
        int mouseX = getMouseX();
        int mouseY = getMouseY();

        if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (mouseX != lastDragMousePos.x || mouseY != lastDragMousePos.y))
        {
            int dx = lastDragMousePos.x - mouseX;
            int dy = lastDragMousePos.y - mouseY;
            lastDragMousePos.x = mouseX;
            lastDragMousePos.y = mouseY;

            // Convert pixel delta to world space delta
            double worldDeltaX = dx * chartviewRect.width  / canvasRect.width;
            double worldDeltaY = dy * chartviewRect.height / canvasRect.height;

            // Subtract because dragging right should move the view left
            chartviewRect.x += worldDeltaX;
            chartviewRect.y += worldDeltaY; // Y is flipped (pixel Y is inverted vs world Y)

            if(chartviewRect.x < -chartviewRect.width)
                chartviewRect.x = -chartviewRect.width;
            return true;
        }
        return false;
    }

    @Override
    protected boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrlKeyDown = isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL);
        if(keyCode == GLFW.GLFW_KEY_SPACE && !ctrlKeyDown)
        {
            if(data != null) {
                // Recenter view based on the horizontal time
                double maxPrice = data.toRealPrice(data.getMaxPrice(firstVisibleCandleIndex, lastVisibleCandleIndex));
                double minPrice = data.toRealPrice(data.getMinPrice(firstVisibleCandleIndex, lastVisibleCandleIndex));

                chartviewRect.y = minPrice * 0.9;
                chartviewRect.height = maxPrice * 1.1;
                return true;
            }
        }else if(keyCode == GLFW.GLFW_KEY_SPACE)
        {
            if(data != null) {
                // Recenter view based on the horizontal time
                int lastIndex = data.getCandles().size();
                int firstIndex = lastIndex - (lastVisibleCandleIndex - firstVisibleCandleIndex);

                double maxPrice = data.toRealPrice(data.getMaxPrice(firstIndex, lastIndex));
                double minPrice = data.toRealPrice(data.getMinPrice(firstIndex, lastIndex));

                chartviewRect.y = minPrice * 0.9;
                chartviewRect.height = maxPrice * 1.1;
                if(chartviewRect.width <- lastIndex)
                {
                    chartviewRect.width = -lastIndex;
                }
                chartviewRect.x = -chartviewRect.width; // Move to the newest candle
                return true;
            }
        }
        return false;
    }

    private int toCanvasSpaceX(long time)
    {
        return (int)map(time, (long) chartviewRect.x, (long) (chartviewRect.x+chartviewRect.width), canvasRect.x, canvasRect.x+canvasRect.width);
        //return canvasRect.x + (int)(((time - (long)chartviewRect.x) * (long)canvasRect.width) / ((long)chartviewRect.width)) * candleWidth;
    }

    private int toCanvasSpaceY(double price)
    {
        return canvasRect.y + (int)(((price - (chartviewRect.y + chartviewRect.height)) * canvasRect.height) / (chartviewRect.height));
    }

    private double fromCanvasSpaceX(int time)
    {
        return chartviewRect.x + (double)(time - canvasRect.x) * (chartviewRect.width) / canvasRect.width;
    }

    private double fromCanvasSpaceY(int yPos)
    {
        return chartviewRect.y+chartviewRect.height + (double)(yPos - canvasRect.y) * (chartviewRect.height) / canvasRect.height;
    }

    long map(long x, long in_min, long in_max, long out_min, long out_max)
    {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }
}
