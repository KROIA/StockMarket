package net.kroia.stockmarket.screen.widgets;

import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.modutilities.gui.geometry.RectangleF;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * todo:
 * - Scaling the price data is currently done very simple
 *   Improvement is needed in order to be able to translate and scale the chart view
 *
 * - Vertical and maybe horizontal axis is not implemented yet
 */
public class CandlestickChart extends StockMarketGuiElement
{

    public static final int colorGreen          = UI_Colors.buyColorGreen;
    public static final int colorRed            = UI_Colors.sellColorRed;
    public static final int colorHorizontalLine = UI_Colors.candlestickChart_horizontalLine;
    public static final int colorZeroLine       = UI_Colors.candlestickChart_zeroLine;
    public final static SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
    public final static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private @Nullable PriceHistoryData data;

    int candleWidth = 12;
    private final Rectangle canvasRect = new Rectangle(1,1,0,0);
    private final RectangleF chartviewRect = new RectangleF(200,0,200,500);
    private final Point lastDragMousePos = new Point();
    private int maxPriceLabelTextWidth = 0;
    private int firstVisibleCandleIndex = 0;
    private int lastVisibleCandleIndex = 0;
    private int maxTimeDateLabelHeight = 0;
    private final Quaternionf rotation90ccl = new Quaternionf(0,0, -Math.sin(Math.PI/4), Math.sin(Math.PI/4));
    private boolean firstDraw = false;
    private boolean dragging = false;

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
        firstDraw = true;
        /*if(data != null) {
            if(data.getCandles().size() > 10) {
                canvasRect.width = Math.max(2, ((getWidth())/2)*2);
                canvasRect.height = Math.max(1, (getHeight()));
                candleWidth = Math.max(1, Math.abs((canvasRect.width/ (int)chartviewRect.width)));
                lastVisibleCandleIndex = Math.min(canvasRect.width / candleWidth, data.getCandles().size() - 1);
                firstVisibleCandleIndex = 0;
                autoCenterView();
            }
        }*/
    }

    @Override
    protected void layoutChanged() {
        //canvasRect.width = getWidth() - maxPriceLabelTextWidth;



        //chartviewRect.width = (double) canvasRect.width /candleWidth;
    }

    @Override
    protected void renderBackground()
    {
        super.renderBackground();
        if(data == null || data.getCandles().isEmpty())
            return;
        lastVisibleCandleIndex = Math.min(data.getCandles().size() - 1, lastVisibleCandleIndex);
        firstVisibleCandleIndex = Math.min(data.getCandles().size() - 1, firstVisibleCandleIndex);
        drawFrame(canvasRect, colorZeroLine, 1);
        enableScissor(canvasRect);
        drawRect(canvasRect.x, toCanvasSpaceY(0)-2 , canvasRect.width, 2, colorZeroLine); // Horizontal zero line


        renderChartHorizontalBackground();
        disableScissor();
        renderChartVerticalBackground();


    }

    @Override
    protected void render()
    {
        if(data==null || data.getCandles().isEmpty())
            return;
        enableScissor(canvasRect);
        candleWidth = (Math.abs((canvasRect.width/ (int)chartviewRect.width)));


        lastVisibleCandleIndex = 0;
        firstVisibleCandleIndex = 0;
        List<PriceHistoryData.Candle> candles =  data.getCandles();
        if(!candles.isEmpty())
        {
            double closePrice = data.getCurrentMarketRealPrice();
            int candleCount = candles.size();

            boolean startedVisible = false;
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
        disableScissor();
        if(firstDraw)
        {
            firstDraw = false;
            autoCenterView();
        }
    }

    private void renderChartHorizontalBackground()
    {
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
        scissorPause();
        for (double price = firstLine; price <= topPrice; price += niceStep)
        {
            renderHorizontalValueLabel(price);
        }
        scissorResume();
        maxPriceLabelTextWidth += 10;
        canvasRect.width = Math.max(2, ((getWidth() - maxPriceLabelTextWidth)/2)*2);
        canvasRect.height = Math.max(1, (getHeight()- maxTimeDateLabelHeight));
        for (double price = firstLine; price <= topPrice; price += niceStep)
        {
            renderHorizontalValueLine(price);
        }
    }
    private void renderChartVerticalBackground()
    {
        graphicsPushPose();
        //rotation90ccl.setAngleAxis(System.currentTimeMillis()/1000.0, 0,0,1);

        graphicsRotateAround(rotation90ccl, canvasRect.x, canvasRect.y, 0);


        int targetLineCount = 10; // how many lines you want roughly visible

        int visibleTimeStamps = (int)chartviewRect.width;

        // Raw step if we just divided evenly
        double rawStep = (double) visibleTimeStamps / targetLineCount;

        // Round to a "nice" number: nearest 1, 2, or 5 times a power of 10
        double magnitude = Math.min(1,Math.pow(10, Math.floor(Math.log10(rawStep))));
        double normalized = rawStep / magnitude; // will be between 1 and 10

        int niceStep;
        if      (normalized < 1.5) niceStep = (int)(1  * magnitude);
        else if (normalized < 3.5) niceStep = (int)(2  * magnitude);
        else if (normalized < 7.5) niceStep = (int)(5  * magnitude);
        else                       niceStep = (int)(10 * magnitude);

        // Find the first price level just below the visible bottom
        int firstTime = (int)Math.floor(chartviewRect.x / niceStep) * niceStep;

        // Draw all lines within the visible range
        //double lastTime = chartviewRect.x + chartviewRect.height;
        maxPriceLabelTextWidth = 0;
        /*for (int time = firstTime; time <= lastTime; time += niceStep)
        {
            int canvasX = toCanvasSpaceX(time);
            int canvasY = toCanvasSpaceY(0);
            drawText(""+time,  canvasY, -canvasX);
        }*/
        List<PriceHistoryData.Candle> candles =  data.getCandles();
        int candleCount = candles.size();
        int offset = Math.max(1,(lastVisibleCandleIndex-firstVisibleCandleIndex) /  targetLineCount);
        //long currentTime = System.currentTimeMillis();

        long lastTime = 0;
        int xOffset = (candleWidth+getTextHeight())/2;
        maxTimeDateLabelHeight = 0;
        int canvasY = toCanvasSpaceY(0);
        canvasY = canvasRect.y + canvasRect.height;
        int i=firstVisibleCandleIndex;
        for(; i<=lastVisibleCandleIndex; i+=offset)
        {
            PriceHistoryData.Candle candle = candles.get(i);
            int canvasX = toCanvasSpaceX(candleCount-i-1);

            long currentTime = candle.openTimestamp;
            boolean useDate = currentTime / 86400000 != lastTime / 86400000;
            String timeDateStr = timestampToTimeDate(currentTime, useDate);
            lastTime = currentTime;
            int textWidth = getTextWidth(timeDateStr);
            maxTimeDateLabelHeight = Math.max(maxTimeDateLabelHeight, textWidth);
            int yPos = Math.max(canvasRect.x,  canvasX - (xOffset+(useDate?getTextHeight()/2:0)));
            drawText(timeDateStr, -canvasY-textWidth -5, yPos);
        }
        if(i != lastVisibleCandleIndex)
        {
            i = lastVisibleCandleIndex;
            if(i>=candles.size())
            {
                int wait = 0;
            }
            PriceHistoryData.Candle candle = candles.get(i);
            int canvasX = toCanvasSpaceX(candleCount-i-1);

            long currentTime = candle.openTimestamp;
            boolean useDate = currentTime / 86400000 != lastTime / 86400000;
            String timeDateStr = timestampToTimeDate(currentTime, useDate);
            int textWidth = getTextWidth(timeDateStr);
            maxTimeDateLabelHeight = Math.max(maxTimeDateLabelHeight, textWidth);
            int yPos = Math.max(canvasRect.x,  canvasX - (xOffset+(useDate?getTextHeight()/2:0)));
            drawText(timeDateStr, -canvasY-textWidth -5, yPos);
        }
        maxTimeDateLabelHeight += 10;
        graphicsPopPose();
    }
    @Override
    public int getTextWidth(String text)
    {
        String[] el = text.split("\n");
        int longestIndex = 0;
        int longestWidth = 0;
        for(int i=0;i<el.length;i++)
        {
            if(el[i].length() > longestWidth)
            {
                longestWidth =  el[i].length();
                longestIndex = i;
            }
        }
        return (int)((float)getFont().width(el[longestIndex]) * getTextFontScale());
    }
    private void renderHorizontalValueLabel(double price)
    {
        if(price < 0)
            return; // Ignore negative prices
        int textHeight = getTextHeight();
        int yPos = toCanvasSpaceY(price);
        if(yPos > canvasRect.y + canvasRect.height ||
           yPos - textHeight/2 < canvasRect.y)
            return;
        String priceLabel = formatPrice(price);
        int textWidth = getTextWidth(priceLabel);

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

    private String timestampToTimeDate(long time, boolean useDate) {
        Date date = new Date(time);
        String timeDate = timeFormat.format(date);
        if(useDate)
            timeDate = dateFormat.format(date) + "\n" + timeDate;
        return timeDate;
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
        if(xOffset < canvasRect.x || (xOffset+candleWidth) > canvasRect.x + canvasRect.width)
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

        boolean consumed = false;
        if (!isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL))
        {
            // Scale the rect
            double newWidth = chartviewRect.width*zoomFactor;
            if(newWidth < Long.MAX_VALUE && newWidth > 1)
            {
                int newCandleWidth = ((Math.abs((canvasRect.width/ (int)newWidth))));
                if(newCandleWidth > 0)
                    chartviewRect.width = (double)((long)(newWidth*newCandleWidth))/newCandleWidth;
                else
                    chartviewRect.width = (int)newWidth;

                // Shift the rect so the point under the mouse stays fixed:
                // mouseWorldX must satisfy: mouseWorldX = chartviewRect.x + mouseNormX * newWidth
                // where mouseNormX = (mouseWorldX - oldX) / oldWidth  (preserved)
                double mouseNormX = (mouseWorldX - chartviewRect.x) / (chartviewRect.width / zoomFactor);
                //double mouseNormY = (mouseWorldY - chartviewRect.y) / (chartviewRect.height / zoomFactor);

                chartviewRect.x = mouseWorldX - mouseNormX * chartviewRect.width;
                //chartviewRect.y = mouseWorldY - mouseNormY * chartviewRect.height;

                if(chartviewRect.x < chartviewRect.width)
                    chartviewRect.x = chartviewRect.width;
            }
            else
            {
                chartviewRect.width = Math.max(Math.min(newWidth, Long.MAX_VALUE), 1);
            }
            consumed = true;
        }
        if (!isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT))
        {
            double newHeight = chartviewRect.height*zoomFactor;
            if(newHeight < Long.MAX_VALUE && newHeight > 1)
            {
                chartviewRect.height = newHeight;

                // Shift the rect so the point under the mouse stays fixed:
                // mouseWorldX must satisfy: mouseWorldX = chartviewRect.x + mouseNormX * newWidth
                // where mouseNormX = (mouseWorldX - oldX) / oldWidth  (preserved)
                //double mouseNormX = (mouseWorldX - chartviewRect.x) / (chartviewRect.width / zoomFactor);
                double mouseNormY = (mouseWorldY - chartviewRect.y) / (chartviewRect.height / zoomFactor);

                //chartviewRect.x = mouseWorldX - mouseNormX * chartviewRect.width;
                chartviewRect.y = Math.max(0, mouseWorldY - mouseNormY * chartviewRect.height);
            }
            else
            {
                chartviewRect.height =  Math.max(Math.min(newHeight, Long.MAX_VALUE), 1);
            }
            //return true;
            consumed = true;
        }
        return consumed;
        /*else
        {
            // Scale the rect
            //chartviewRect.width  *= zoomFactor;
            //chartviewRect.height  *= zoomFactor;

            chartviewRect.width = Math.min(Math.max(chartviewRect.width*zoomFactor, -Long.MAX_VALUE), -1);
            chartviewRect.height = Math.max(Math.min(chartviewRect.height*zoomFactor, Long.MAX_VALUE), 1);

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
        }*/
        //return false;
    }

    @Override
    protected boolean mouseClickedOverElement(int button) {
        lastDragMousePos.x = getMouseX();
        lastDragMousePos.y = getMouseY();
        dragging = true;
        return true;
    }
    @Override
    protected void mouseReleased(int button) {
        dragging = false;
    }
    @Override
    protected boolean mouseDragged(int button, double deltaX, double deltaY) {
        if(!dragging)
            return false;
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
            chartviewRect.x -= worldDeltaX;
            chartviewRect.y = Math.max(0, chartviewRect.y - worldDeltaY); // Y is flipped (pixel Y is inverted vs world Y)

            if(chartviewRect.x < chartviewRect.width)
                chartviewRect.x = chartviewRect.width;
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

                double priceDifference = maxPrice - minPrice;
                chartviewRect.y = Math.max(0, minPrice - priceDifference * 0.1) /*- labelOffset*/;
                chartviewRect.height = (maxPrice + priceDifference * 0.1) - chartviewRect.y;
                return true;
            }
        }else if(keyCode == GLFW.GLFW_KEY_SPACE)
        {
            if(data != null) {
                autoCenterView();
                return true;
            }
        }
        return false;
    }
    public void autoCenterView()
    {
        if(data != null) {
            // Recenter view based on the horizontal time
            int lastIndex = data.getCandles().size();
            int firstIndex = lastIndex - (lastVisibleCandleIndex - firstVisibleCandleIndex);

            double maxPrice = data.toRealPrice(data.getMaxPrice(firstIndex, lastIndex));
            double minPrice = data.toRealPrice(data.getMinPrice(firstIndex, lastIndex));

            double priceDifference = maxPrice - minPrice;
            //double labelOffset = (fromCanvasSpaceY(maxTimeDateLabelHight)-fromCanvasSpaceY(0));
            chartviewRect.y = Math.max(0, minPrice - priceDifference * 0.1) /*- labelOffset*/;
            chartviewRect.height = (maxPrice + priceDifference * 0.1) - chartviewRect.y;
            if(chartviewRect.width > lastIndex)
            {
                chartviewRect.width = lastIndex;
            }
            chartviewRect.x = chartviewRect.width; // Move to the newest candle
        }
    }

    private int toCanvasSpaceX(long time)
    {
        long in2 = ((long)chartviewRect.x - (long)chartviewRect.width);
        long in1 = (long)(chartviewRect.x);
        //if(candleWidth > 0) {
        //    in2 = (in2 * candleWidth) / candleWidth;
        //    in1 = (in1 * candleWidth) / candleWidth;
        //}
        return (int)map(time, in1, in2, canvasRect.x, canvasRect.x+canvasRect.width);
        //return canvasRect.x + (int)(((time - (long)chartviewRect.x) * (long)canvasRect.width) / ((long)chartviewRect.width)) * candleWidth;
    }

    private int toCanvasSpaceY(double price)
    {
        return canvasRect.y - (int)(((price - (chartviewRect.y + chartviewRect.height)) * canvasRect.height) / (chartviewRect.height));
    }

    private double fromCanvasSpaceX(int time)
    {
        return chartviewRect.x - (double)(time - canvasRect.x) * (chartviewRect.width) / canvasRect.width;
    }

    private double fromCanvasSpaceY(int yPos)
    {
        return chartviewRect.y+chartviewRect.height - (double)(yPos - canvasRect.y) * (chartviewRect.height) / canvasRect.height;
    }

    long map(long x, long in_min, long in_max, long out_min, long out_max)
    {
        if(in_min == in_max)
            return Long.MAX_VALUE;
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }
}
