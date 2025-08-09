package net.kroia.stockmarket.screen.uiElements.chart;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.OrderReadData;
import net.kroia.stockmarket.market.clientdata.OrderReadListData;
import net.kroia.stockmarket.market.server.ServerMarketManager;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.screen.uiElements.LimitOrderInChartDisplay;
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class CandleStickChartWidget extends StockMarketGuiElement {
    private static final Component BOT_TARGET_PRICE = Component.translatable("gui."+ StockMarketMod.MOD_ID + ".candle_stick_chart.bot_target_price");
    private final Function<Float, Integer> priceToYPosFunc;
    private final Function<Integer, Float> yPosToPriceFunc;
    private final int colorSell;
    private final int colorBuy;
    private final int minCandleWidth = 3; // Minimum width of a candle in pixels
    private float chartViewMinPrice;
    private float chartViewMaxPrice;
    private int maxLabelWidth = 0;
    private double scrollValue = 1;
    private int chartWidth = 0;
    private int candleWidth = 0;
    private boolean enableBotTargetPriceDisplay = false;
    private float botTargetPrice = -1;
    private int priceScaleFactor = 1;
    private PriceHistory priceHistory = null;
    private HashMap<Long, LimitOrderInChartDisplay> limitOrderDisplays = new HashMap<>();
    private BiConsumer<OrderReadData, Float> priceChangeCallback;
    public CandleStickChartWidget(Function<Float, Integer> priceToYPosFunc,
                                  Function<Integer, Float> yPosToPriceFunc,
                                  BiConsumer<OrderReadData, Float> priceChangeCallback,
                                  int colorBuy, int colorSell) {
        super();
        this.priceToYPosFunc = priceToYPosFunc;
        this.yPosToPriceFunc = yPosToPriceFunc;
        this.priceChangeCallback = priceChangeCallback;
        this.colorBuy = colorBuy;
        this.colorSell = colorSell;
    }
    public CandleStickChartWidget(Function<Float, Integer> priceToYPosFunc,
                                  Function<Integer, Float> yPosToPriceFunc,
                                  int colorBuy, int colorSell) {
        super();
        this.priceToYPosFunc = priceToYPosFunc;
        this.yPosToPriceFunc = yPosToPriceFunc;
        this.priceChangeCallback = null;
        this.colorBuy = colorBuy;
        this.colorSell = colorSell;
    }


    @Override
    protected void renderBackground()
    {
        super.renderBackground();
        if(priceHistory == null)
            return;
        float yAxisLabelIncrement = 0.1f;
        int labelWidth = 0;


        candleWidth = (int)Math.ceil((double)getWidth() / (double)priceHistory.size());
        candleWidth = candleWidth | 1; // Make sure it is odd
        if(candleWidth < minCandleWidth)
            candleWidth = minCandleWidth;


        float priceRange = chartViewMaxPrice - chartViewMinPrice;
        /*if(priceRange > 1)
        {
            if(priceRange < 5)
            {
                yAxisLabelIncrement = 0.2f;
            }
            if(priceRange < 10)
            {
                yAxisLabelIncrement = 1;
            }
            else if(priceRange < 100)
            {
                yAxisLabelIncrement = (float)Math.ceil((priceRange)/5.0);
            }
            else if(priceRange < 1000)
            {
                yAxisLabelIncrement = (float)Math.ceil((priceRange)/10.0);
            }
            else
            {*/
                int maxLableCount = getHeight()/getTextHeight();
                yAxisLabelIncrement = (priceRange)/(float)Math.min(maxLableCount, 20.0);
           // }
       // }
        if(yAxisLabelIncrement < ServerMarketManager.rawToRealPrice(1 ,priceScaleFactor))
        {
            yAxisLabelIncrement = ServerMarketManager.rawToRealPrice(1 ,priceScaleFactor);
        }

        int x = getChartRightEndPos();
        int labelXPos = x + 5;

        float startLabelPrice = chartViewMinPrice;
        float endLabelPrice = chartViewMaxPrice;

        /*if(startLabelPrice < 10)
        {
            startLabelPrice = (float)Math.floor(startLabelPrice / yAxisLabelIncrement) * yAxisLabelIncrement;
        }
        else
        {
            startLabelPrice = (float)Math.ceil(startLabelPrice / yAxisLabelIncrement) * yAxisLabelIncrement;
        }*/


        // Draw yAxis
        if(yAxisLabelIncrement > 0) {
            for (float i = startLabelPrice; i <= endLabelPrice; i += yAxisLabelIncrement) {
                int y = priceToYPosFunc(i);
                String label = priceHistory.getRealPriceString(i);
                drawText(label, labelXPos, y, 0xFFFFFFFF, Alignment.LEFT);

                drawRect(1, y, x, 1, 0xFF808080);
            }
        }


        long maxVolume = priceHistory.getMaxVolume();
        int lastIndex = priceHistory.size()-1;
        float currentPrice = priceHistory.getCurrentRealPrice();
        String labelText = priceHistory.getRealPriceString(currentPrice);
        int currentPriceYPos = priceToYPosFunc(currentPrice);
        drawText(labelText, x-candleWidth-3 ,currentPriceYPos,  Alignment.RIGHT);

        int currentPriceLineLeftPos = x - candleWidth;
        int currentPriceLineWidth = candleWidth + 2;



        for(int i=lastIndex; i>=0; i--)
        {
            x -= candleWidth;
            //long volume = priceHistory.getVolume(lastIndex-i);
            //drawRect(x, getHeight()-1, candleWidth, (int)-map(volume, 0, maxVolume, 0, (float) getHeight() /11+PADDING-2), 0xFF91a9b8);

            float low = priceHistory.getLowRealPrice(lastIndex-i);
            float high = priceHistory.getHighRealPrice(lastIndex-i);
            float close = priceHistory.getCloseRealPrice(lastIndex-i);
            float open = priceHistory.getOpenRealPrice(lastIndex-i);
            renderCandle(x, candleWidth, 0, 0, open, close, high, low);

            if(x <= candleWidth)
                break;
        }

        if(enableBotTargetPriceDisplay && botTargetPrice >= 0)
        {
            int tooltipWidth = getTextWidth(labelText);
            int yPos = priceToYPosFunc(botTargetPrice);
            drawRect(currentPriceLineLeftPos - tooltipWidth-10, yPos, tooltipWidth + currentPriceLineWidth+13, 1, 0xFF0000FF);

            String priceString = priceHistory.getRealPriceString(botTargetPrice);
            drawText(BOT_TARGET_PRICE.getString() + priceString, currentPriceLineLeftPos - tooltipWidth-10, yPos, Alignment.RIGHT);
        }
        drawRect(currentPriceLineLeftPos, currentPriceYPos, currentPriceLineWidth, 1, 0xFF555555);
        //getGraphics().popPose();



    }

    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        chartWidth = getWidth()-maxLabelWidth-5;

        for(LimitOrderInChartDisplay display : limitOrderDisplays.values())
        {
            display.setWidth(getWidth()/2-5);
            display.setPosition(getWidth()-display.getWidth(), priceToYPosFunc(display.getOrder().limitPrice));
        }
    }


    public void setPriceHistory(PriceHistory priceHistory) {
        this.priceHistory = priceHistory;
    }

    public void setMinMaxPrice(float minPrice, float maxPrice)
    {
        this.chartViewMinPrice = minPrice;
        this.chartViewMaxPrice = maxPrice;

        for(LimitOrderInChartDisplay display : limitOrderDisplays.values())
        {
            display.setY(priceToYPosFunc(display.getOrder().limitPrice));
        }
    }
    public void enableBotTargetPriceDisplay(boolean enabled) {
        enableBotTargetPriceDisplay = enabled;
    }
    public void setBotTargetPrice(float botTargetPrice) {
        this.botTargetPrice = botTargetPrice;
    }
    public void setPriceScaleFactor(int priceScaleFactor) {
        this.priceScaleFactor = priceScaleFactor;
    }
    public int getMaxCandleCount()
    {
        return Math.max((int)(chartWidth*scrollValue*2) / minCandleWidth,10);
    }
    public int getCandleWidth()
    {
        return candleWidth;
    }
    public int getChartRightEndPos()
    {
        if(priceHistory == null)
            maxLabelWidth = 0;
        else
            maxLabelWidth = getTextWidth(String.valueOf((int)chartViewMaxPrice) + priceHistory.getPriceScaleFactor());
        int labelXPos = getWidth() - maxLabelWidth;
        return labelXPos-5;
    }

    /*private String getPriceString(int rawPrice)
    {
        return Bank.getFormattedAmount(rawPrice, currencyCentScaleFactor);
    }*/

    @Override
    protected void mouseScrolled(double delta) {
        if(!isMouseOver())
            return;
        scrollValue -= delta*0.01;

        if(scrollValue < 0)
            scrollValue = 0;
        else if(scrollValue > 1)
            scrollValue = 1;
    }

    public void updateOrderDisplay(OrderReadListData orderList, TradingPair pair)
    {
        if(priceChangeCallback == null)
            return;
        List<OrderReadData> orders = orderList.orders;
        //ArrayList<Order> orders = BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.getOrders(TradeScreen.getItemID());
        HashMap<Long,Integer> stillActiveOrderIds = new HashMap<>();
        ArrayList<Long> forRemoval = new ArrayList<>();

        for(int j=0; j<orders.size(); ++j)
        {
            long orderID = orders.get(j).orderID;
            stillActiveOrderIds.put(orderID, 1);
        }

        for(LimitOrderInChartDisplay display : limitOrderDisplays.values())
        {
            if(!stillActiveOrderIds.containsKey(display.getOrder().orderID))
            {
                forRemoval.add(display.getOrder().orderID);
            }
            else
            {
                stillActiveOrderIds.put(display.getOrder().orderID, 2);
            }
        }
        for(Long orderID : forRemoval)
        {
            removeChild(limitOrderDisplays.get(orderID));
            limitOrderDisplays.remove(orderID);
        }
        for(int j=0; j<orders.size(); ++j)
        {
            OrderReadData order = orders.get(j);
            if(order.type == Order.Type.LIMIT)
            {
                long orderID = order.orderID;
                int case_ = stillActiveOrderIds.get(orderID);
                if(case_ == 1)
                {
                    LimitOrderInChartDisplay orderView = new LimitOrderInChartDisplay(yPosToPriceFunc, order, pair, priceChangeCallback, priceScaleFactor);
                    orderView.setWidth(getWidth()/2-5);
                    orderView.setPosition(getWidth()-orderView.getWidth(), priceToYPosFunc(order.limitPrice));
                    limitOrderDisplays.put(orderID, orderView);
                    addChild(orderView);
                }
                else if(case_ == 2)
                {
                    LimitOrderInChartDisplay orderView = limitOrderDisplays.get(orderID);
                    if(orderView != null)
                    {
                        orderView.setOrder(order);
                        orderView.setY(priceToYPosFunc(order.limitPrice));
                    }
                }
            }
        }
    }

    private void renderCandle(int x,int candleWidth, int xOffset, int yOffset,
                             float open, float close, float high, float low)
    {
        int color = open > close ? colorSell : colorBuy;

        // Draw wick
        int wickYMin = priceToYPosFunc(low);
        int wickYMax = priceToYPosFunc(high);

        // Draw body
        int bodyYMin = priceToYPosFunc(Math.min(open, close));
        int bodyYMax = priceToYPosFunc(Math.max(open, close));

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
            drawRect(xOffset + x + wickOffset, yOffset + bodyYMax,
                    wickWidth, wickYMax-bodyYMax, color);
        }

        if(wickYMin - (bodyYMin) > 0)
        {
            // Wick down
            drawRect(xOffset + x + wickOffset, yOffset + bodyYMin,
                    wickWidth, wickYMin-bodyYMin, color);
        }

        drawRect(xOffset + x, yOffset + bodyYMin,
                candleWidth, bodyYMax-bodyYMin, color);
    }

    private int priceToYPosFunc(float price) {
        return priceToYPosFunc.apply(price);
    }
}
