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
import net.kroia.stockmarket.util.PriceHistory;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;


public class TradeScreen extends StockMarketGuiScreen {
    private static final String PREFIX = "gui.";
    private static final String NAME = "trade_screen";

    private static final Component TITLE = Component.translatable(PREFIX + StockMarketMod.MOD_ID + "."+NAME+".stock_market_block_screen");
    public static final Component YOUR_BALANCE_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".your_balance");
    public static final Component CHANGE_ITEM_BUTTON = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".change_item");
    public static final Component AMOUNT_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".amount");
    public static final Component MARKET_ORDER_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".market_order");
    public static final Component LIMIT_ORDER_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".limit_order");
    public static final Component LIMIT_PRICE_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".limit_price");
    public static final Component PRICE_LABEL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".price");
    public static final Component BUY = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".buy");
    public static final Component SELL = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".sell");
    public static final Component MARKET_CLOSED = Component.translatable(PREFIX+StockMarketMod.MOD_ID + "."+NAME+".market_closed");
    public static final Component CANCEL = Component.translatable(PREFIX+ StockMarketMod.MOD_ID + "."+NAME+".cancel");
    public static final Component DIRECTION_LABEL = Component.translatable(PREFIX+ StockMarketMod.MOD_ID + "."+NAME+".direction");
    public static final Component FILLED_LABEL = Component.translatable(PREFIX+ StockMarketMod.MOD_ID + "."+NAME+".filled");

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

    private final TradePanel tradePanel;
    private static TradeScreen instance;

    private final TimerMillis updateTimer;

    public TradeScreen(StockMarketBlockEntity blockEntity) {
        this(blockEntity.getTradringPair(), blockEntity.getAmount(), blockEntity.getPrice());
        this.blockEntity = blockEntity;



    }
    public TradeScreen(TradingPair currentPair, int currentAmount, int currentPrice) {
        super(TITLE);
        this.updateTimer = new TimerMillis(true); // Update every second
        updateTimer.start(100);
        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.init();
        blockEntity = null;
        instance = this;

        this.tradingPair = currentPair;

        // Create Gui Elements
        //this.candleStickChart = new CandleStickChartOld(this::onOrderChange);
        tradingChart = new TradingChartWidget(this::onOrderChange);
        //this.orderbookVolumeChart = new OrderbookVolumeChartWidget();
        //this.orderbookVolumeChart.setTooltipMousePositionAlignment(GuiElement.Alignment.TOP);
        //this.orderbookVolumeChart.setHoverTooltipSupplier(StockMarketTextMessages::getCandlestickChartTooltipOrderBookVolume);
        this.activeOrderListView = new OrderListView(this::cancelOrder);
        this.tradePanel = new TradePanel(this::onSelectItemButtonPressed,
                this::onBuyMarketButtonPressed,
                this::onSellMarketButtonPressed,
                this::onBuyLimitButtonPressed,
                this::onSellLimitButtonPressed);

        tradePanel.setAmount(currentAmount);
        tradePanel.setLimitPrice(currentPrice);
        tradePanel.setMarketOpen(marketWasOpen);

        // Add Gui Elements
        //addElement(candleStickChart);
        //addElement(orderbookVolumeChart);
        addElement(tradingChart);
        addElement(activeOrderListView);
        addElement(tradePanel);


        TickEvent.PLAYER_POST.register(TradeScreen::onClientTick);
    }
    public TradeScreen() {
        this(new TradingPair(new ItemID("minecraft:diamond"), new ItemID(BankSystemItems.MONEY.get().getDefaultInstance())), 0, 0);


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
        tradePanel.setTradingPair(tradingPair);

        int padding = 5;
        int spacing = 5;
        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;

        int x = padding;

        tradingChart.setBounds(x, padding, (width*2/3)-spacing, height / 2);
        tradePanel.setBounds(tradingChart.getRight()+spacing, padding, width/3, height);

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
            blockEntity.setAmount(tradePanel.getAmount());
            blockEntity.setPrice(tradePanel.getLimitPrice());
            UpdateStockMarketBlockEntityPacket.sendPacketToServer(blockEntity.getBlockPos(), blockEntity);
        }
    }


    public static void handlePacket(SyncStockMarketBlockEntityPacket packet) {
        if (instance != null) {
            instance.tradingPair = packet.getTradingPair();
            instance.selectMarket(instance.tradingPair);
            instance.tradePanel.setTradingPair(instance.tradingPair);
            instance.tradePanel.setAmount(packet.getAmount());
            instance.tradePanel.setLimitPrice(packet.getPrice());
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
        PriceHistory history = data.priceHistoryData.toHistory();
        //candleStickChart.setPriceHistory(history);
        //orderbookVolumeChart.setOrderBookVolume(data.orderBookVolumeData);
        tradePanel.setCurrentItemBalance(data.itemBankData.balance);
        tradePanel.setCurrentMoneyBalance(data.currencyBankData.balance);


        tradePanel.setCurrentPrice(history.getCurrentPrice());
        activeOrderListView.updateActiveOrders(data.openOrdersData);
        //candleStickChart.updateOrderDisplay(data.openOrdersData);

        if(marketWasOpen != data.marketIsOpen)
        {
            marketWasOpen = data.marketIsOpen;
            tradePanel.setMarketOpen(data.marketIsOpen);
        }
    }

    private void onItemSelected(TradingPair tradingPair) {
        //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.unsubscribeMarketUpdate(tradingPair);
        this.tradingPair = tradingPair;
        selectMarket(tradingPair);
        tradePanel.setTradingPair(tradingPair);
        minecraft.setScreen(this);
    }


    private void onSellMarketButtonPressed() {
        int amount = tradePanel.getAmount();
        if(amount > 0)
            getSelectedMarket().requestCreateMarketOrder(-amount, (success) -> {
                if(success)
                {
                    BACKEND_INSTANCES.LOGGER.debug("Market sell order created successfully.");
                }
                else
                {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to create market sell order.");
                }
            });
    }

    private void onBuyMarketButtonPressed() {
        int amount = tradePanel.getAmount();
        if(amount > 0)
            getSelectedMarket().requestCreateMarketOrder(amount, (success) -> {
                if(success)
                {
                    BACKEND_INSTANCES.LOGGER.debug("Market buy order created successfully.");
                }
                else
                {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to create market buy order.");
                }
            });
    }

    private void onSellLimitButtonPressed() {
        int amount = tradePanel.getAmount();
        int price = tradePanel.getLimitPrice();
        if(amount > 0 && price >= 0)
            getSelectedMarket().requestCreateLimitOrder(-amount, price, (success) -> {
                if(success)
                {
                    BACKEND_INSTANCES.LOGGER.debug("Limit sell order created successfully.");
                }
                else
                {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to create limit sell order.");
                }
            });
    }

    private void onBuyLimitButtonPressed() {
        int amount = tradePanel.getAmount();
        int price = tradePanel.getLimitPrice();
        if(amount > 0 && price >= 0)
            getSelectedMarket().requestCreateLimitOrder(amount, price, (success) -> {
                if(success)
                {
                    BACKEND_INSTANCES.LOGGER.debug("Limit buy order created successfully.");
                }
                else
                {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to create limit buy order.");
                }
            });
    }

    private void onSelectItemButtonPressed() {

        MarketSelectionScreen screen = new MarketSelectionScreen(this, this::onItemSelected);
        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestTradingPairs(
                screen::setAvailableTradingPairs);
        Minecraft.getInstance().setScreen(screen);
    }

    private void cancelOrder(OrderReadData order)
    {
        getSelectedMarket().requestCancelOrder(order.orderID, (success) -> {
            if(success)
            {
                BACKEND_INSTANCES.LOGGER.debug("Order cancelled: " + order.orderID);
            }
            else
            {
                BACKEND_INSTANCES.LOGGER.warn("Failed to cancel order: " + order.orderID);
            }
        });
    }
    private void onOrderChange(OrderReadData order, Integer newPrice)
    {
        if(newPrice != null && newPrice >= 0)
        {
            getSelectedMarket().requestChangeOrder(order.orderID, newPrice, (success) -> {
                if(success)
                {
                    BACKEND_INSTANCES.LOGGER.debug("Order price changed successfully: " + order.orderID + " to " + newPrice);
                }
                else
                {
                    BACKEND_INSTANCES.LOGGER.warn("Failed to change order: " + order.orderID+ " to " + newPrice);
                }
            });
        }
        else
        {
            BACKEND_INSTANCES.LOGGER.warn("Invalid new price for order: " + order.orderID);
        }
    }
    
}
