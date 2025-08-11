// TradeScreen.java
package net.kroia.stockmarket.screen.custom;

import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.item.BankSystemItems;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.gui.Gui;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.OrderReadData;
import net.kroia.stockmarket.market.clientdata.TradingViewData;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateStockMarketBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.entity.SyncStockMarketBlockEntityPacket;
import net.kroia.stockmarket.screen.uiElements.OrderListView;
import net.kroia.stockmarket.screen.uiElements.TradePanel;
import net.kroia.stockmarket.screen.uiElements.chart.TradingChartWidget;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;


public class TradeScreen extends StockMarketGuiScreen {
    private static final String PREFIX = "gui."+StockMarketMod.MOD_ID + ".trade_screen.";

    private static final Component TITLE = Component.translatable(PREFIX +"title");
    public static final Component PREVIOUS_PAIR_TOOLTIP = Component.translatable(PREFIX+"previous_pair.tooltip");
    public static final Component NEXT_PAIR_TOOLTIP  = Component.translatable(PREFIX+"next_pair.tooltip");
    public static final Component YOUR_BALANCE_LABEL = Component.translatable(PREFIX+"your_balance");
    public static final Component CHANGE_ITEM_BUTTON = Component.translatable(PREFIX+"change_item");
    public static final Component AMOUNT_LABEL = Component.translatable(PREFIX+"amount");
    public static final Component MARKET_ORDER_LABEL = Component.translatable(PREFIX+"market_order");
    public static final Component LIMIT_ORDER_LABEL = Component.translatable(PREFIX+"limit_order");
    public static final Component LIMIT_PRICE_LABEL = Component.translatable(PREFIX+"limit_price");
    public static final Component PRICE_LABEL = Component.translatable(PREFIX+"price");
    public static final Component BUY = Component.translatable(PREFIX+"buy");
    public static final Component SELL = Component.translatable(PREFIX+"sell");
    public static final Component MARKET_CLOSED = Component.translatable(PREFIX+"market_closed");
    public static final Component CANCEL = Component.translatable(PREFIX+ "cancel");
    public static final Component DIRECTION_LABEL = Component.translatable(PREFIX+ "direction");
    public static final Component FILLED_LABEL = Component.translatable(PREFIX+ "filled");

    public static final int colorGreen = 0x7F00FF00;
    public static final int colorRed = 0x7FFF0000;

    private TradingPair tradingPair;
    private boolean marketWasOpen = false;

    static long lastTickCount = 0;
    private StockMarketBlockEntity blockEntity;


    // Gui Elements
    //private final CandleStickChartOld candleStickChart;
    //private final OrderbookVolumeChartWidget orderbookVolumeChart;
    private final TradingChartWidget tradingChart;

    private final OrderListView activeOrderListView;

    private final TradePanel tradingPanel;
    private static TradeScreen instance;

    private final TimerMillis updateTimer;
    private List<TradingPair> tradingPairsCarusel;
    private int currentTradingPairCaruselIndex = 0;

    public TradeScreen(StockMarketBlockEntity blockEntity) {
        this(blockEntity.getTradringPair(), blockEntity.getAmount(), blockEntity.getPrice());
        this.blockEntity = blockEntity;



    }
    public TradeScreen(TradingPair currentPair, float currentAmount, float currentPrice) {
        super(TITLE);
        this.updateTimer = new TimerMillis(true); // Update every second
        updateTimer.start(100);
        //getMarketManager().init();
        blockEntity = null;
        instance = this;

        this.tradingPair = currentPair;

        // Create Gui Elements
        tradingChart = new TradingChartWidget(this::onOrderChange);
        this.activeOrderListView = new OrderListView(this::cancelOrder);
        this.tradingPanel = new TradePanel(this::onSelectItemButtonPressed,
                this::onBuyMarketButtonPressed,
                this::onSellMarketButtonPressed,
                this::onBuyLimitButtonPressed,
                this::onSellLimitButtonPressed,
                this::onSelectNextMarketButtonPressed,
                this::onSelectPreviousMarketButtonPressed);

        tradingPanel.setAmount(currentAmount);
        tradingPanel.setLimitPrice(currentPrice);
        tradingPanel.setMarketOpen(marketWasOpen);

        // Add Gui Elements

        addElement(tradingChart);
        addElement(activeOrderListView);
        addElement(tradingPanel);

        getMarketManager().requestTradingPairs(
                (tradingPairs) -> {
                    tradingPairsCarusel = tradingPairs;
                    setPreviousNextMarket();
                });

        TickEvent.PLAYER_POST.register(TradeScreen::onClientTick);
    }
    public TradeScreen() {
        this(new TradingPair(new ItemID("minecraft:diamond"), new ItemID(BankSystemItems.MONEY.get().getDefaultInstance())), 0, 0.f);


    }

    public static void openScreen(StockMarketBlockEntity blockEntity)
    {
        TradeScreen screen = new TradeScreen(blockEntity);
        Minecraft.getInstance().setScreen(screen);
    }
    public static void openScreen()
    {
        TradeScreen screen = new TradeScreen();
        Minecraft.getInstance().setScreen(screen);
    }


    @Override
    protected void updateLayout(Gui gui) {
        tradingPanel.setTradingPair(tradingPair);

        int padding = 5;
        int spacing = 5;
        int width = (getWidth())-2*padding;
        int height = (getHeight())-2*padding;

        int x = padding;

        tradingChart.setBounds(x, padding, (((width*3)/4)-spacing), ((height*2) / 3));
        tradingPanel.setBounds(tradingChart.getRight()+spacing, padding, width - tradingChart.getRight(), height);

        //candleStickChart.setBounds(x, padding, (width * 5) / 8-spacing/2, height/2);
        //orderbookVolumeChart.setBounds(candleStickChart.getRight(), padding, width / 8, candleStickChart.getHeight());
        //tradePanel.setBounds(orderbookVolumeChart.getRight()+spacing, padding, width/4, height);
//
        activeOrderListView.setBounds(tradingChart.getLeft(), tradingChart.getBottom()+spacing, tradingChart.getWidth(), height - tradingChart.getHeight() - spacing);
    }

    @Override
    public void onClose() {
        super.onClose();
        instance = null;
        // Unregister the event listener when the screen is closed
        TickEvent.PLAYER_POST.unregister(TradeScreen::onClientTick);
        if(blockEntity != null)
        {
            blockEntity.setTradingPair(tradingPair);
            blockEntity.setAmount(tradingPanel.getAmount());
            blockEntity.setPrice(tradingPanel.getLimitPrice());
            UpdateStockMarketBlockEntityPacket.sendPacketToServer(blockEntity.getBlockPos(), blockEntity);
        }
    }


    public static void handlePacket(SyncStockMarketBlockEntityPacket packet) {
        if (instance != null) {
            instance.tradingPair = packet.getTradingPair();
            instance.selectMarket(instance.tradingPair);
            instance.tradingPanel.setTradingPair(instance.tradingPair);
            instance.tradingPanel.setAmount(packet.getAmount());
            instance.tradingPanel.setLimitPrice(packet.getPrice());
        }
    }

    public static TradingPair getTradingPair() {
        if(instance != null)
            return instance.tradingPair;
        return null;
    }


    private static void onClientTick(Player player) {
        if (Minecraft.getInstance().screen != instance || instance == null)
            return;


        if(instance.updateTimer.check() && instance.getSelectedMarket() != null)
        {
            instance.getSelectedMarket().requestTradingViewData(instance.tradingChart.getMaxCandleCount(), 0,0,500, false ,instance::updateView);
        }
    }

    private void updateView(TradingViewData data)
    {
        if(data == null)
            return;

        tradingChart.updateView(data);
        //candleStickChart.setMinMaxPrice(data.orderBookVolumeData.minPrice, data.orderBookVolumeData.maxPrice);

        //candleStickChart.setPriceHistory(history);
        //orderbookVolumeChart.setOrderBookVolume(data.orderBookVolumeData);
        tradingPanel.updateView(data);


        activeOrderListView.updateActiveOrders(data.openOrdersData, data.itemBankData.itemFractionScaleFactor);
        //candleStickChart.updateOrderDisplay(data.openOrdersData);

        if(marketWasOpen != data.marketIsOpen)
        {
            marketWasOpen = data.marketIsOpen;
            tradingPanel.setMarketOpen(data.marketIsOpen);
        }
    }

    private void onItemSelected(TradingPair tradingPair) {
        //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.unsubscribeMarketUpdate(tradingPair);
        this.tradingPair = tradingPair;
        selectMarket(tradingPair);
        tradingPanel.setTradingPair(tradingPair);
        minecraft.setScreen(this);
    }


    private void onSellMarketButtonPressed() {
        float amount = tradingPanel.getAmount();
        if(amount > 0)
            getSelectedMarket().requestCreateMarketOrder(-amount, (success) -> {
                if(success)
                {
                    debug("Market sell order created successfully.");
                }
                else
                {
                    warn("Failed to create market sell order.");
                }
            });
    }

    private void onBuyMarketButtonPressed() {
        float amount = tradingPanel.getAmount();
        if(amount > 0)
            getSelectedMarket().requestCreateMarketOrder(amount, (success) -> {
                if(success)
                {
                    debug("Market buy order created successfully.");
                }
                else
                {
                    warn("Failed to create market buy order.");
                }
            });
    }

    private void onSellLimitButtonPressed() {
        float amount = tradingPanel.getAmount();
        float price = tradingPanel.getLimitPrice();
        if(amount > 0 && price >= 0)
            getSelectedMarket().requestCreateLimitOrder(-amount, price, (success) -> {
                if(success)
                {
                    debug("Limit sell order created successfully.");
                }
                else
                {
                    warn("Failed to create limit sell order.");
                }
            });
    }

    private void onBuyLimitButtonPressed() {
        float amount = tradingPanel.getAmount();
        float price = tradingPanel.getLimitPrice();
        if(amount > 0 && price >= 0)
            getSelectedMarket().requestCreateLimitOrder(amount, price, (success) -> {
                if(success)
                {
                    debug("Limit buy order created successfully.");
                }
                else
                {
                    warn("Failed to create limit buy order.");
                }
            });
    }

    private void onSelectItemButtonPressed() {

        MarketSelectionScreen screen = new MarketSelectionScreen(this, this::onItemSelected);
        getMarketManager().requestTradingPairs(
                (tradingPairs) -> {
                    screen.setAvailableTradingPairs(tradingPairs);
                    tradingPairsCarusel = tradingPairs;
                    setPreviousNextMarket();
                });
        Minecraft.getInstance().setScreen(screen);
    }
    private void onSelectNextMarketButtonPressed()
    {
        TradingPair nextPair = tradingPanel.getNextTradingPair();
        if(nextPair != null)
            onItemSelected(nextPair);
        currentTradingPairCaruselIndex++;
        setPreviousNextMarket();
    }
    private void onSelectPreviousMarketButtonPressed()
    {
        TradingPair previousTradingPair = tradingPanel.getPreviousTradingPair();
        if(previousTradingPair != null)
            onItemSelected(previousTradingPair);
        currentTradingPairCaruselIndex--;
        setPreviousNextMarket();
    }
    private void setPreviousNextMarket()
    {
        if(tradingPairsCarusel == null || tradingPairsCarusel.isEmpty())
        {
            tradingPanel.setPreviousTradingPair(null);
            tradingPanel.setNextTradingPair(null);
            return;
        }
        if(tradingPairsCarusel.size() == 1)
        {
            tradingPanel.setPreviousTradingPair(tradingPairsCarusel.get(0));
            tradingPanel.setNextTradingPair(null);
            return;
        }
        if(tradingPairsCarusel.size() == 2)
        {
            tradingPanel.setPreviousTradingPair(tradingPairsCarusel.get(0));
            tradingPanel.setNextTradingPair(tradingPairsCarusel.get(1));
            return;
        }


        int size = tradingPairsCarusel.size();
        currentTradingPairCaruselIndex = (size+currentTradingPairCaruselIndex) % size;
        TradingPair previousPair = tradingPairsCarusel.get(currentTradingPairCaruselIndex);
        TradingPair nextPair = tradingPairsCarusel.get((currentTradingPairCaruselIndex + 2) % size);
        tradingPanel.setPreviousTradingPair(previousPair);
        tradingPanel.setNextTradingPair(nextPair);
    }


    private void cancelOrder(OrderReadData order)
    {
        getSelectedMarket().requestCancelOrder(order.orderID, (success) -> {
            if(success)
            {
                debug("Order cancelled: " + order.orderID);
            }
            else
            {
                warn("Failed to cancel order: " + order.orderID);
            }
        });
    }
    private void onOrderChange(OrderReadData order, Float newPrice)
    {
        if(newPrice != null && newPrice >= 0)
        {
            getSelectedMarket().requestChangeOrder(order.orderID, newPrice, (success) -> {
                if(success)
                {
                    debug("Order price changed successfully: " + order.orderID + " to " + newPrice);
                }
                else
                {
                    warn("Failed to change order: " + order.orderID+ " to " + newPrice);
                }
            });
        }
        else
        {
            warn("Invalid new price for order: " + order.orderID);
        }
    }
    
}
