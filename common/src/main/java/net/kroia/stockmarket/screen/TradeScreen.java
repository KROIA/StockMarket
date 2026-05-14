package net.kroia.stockmarket.screen;

import net.kroia.banksystem.banking.clientdata.BankData;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TabElement;
import net.kroia.modutilities.networking.client_server.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.screen.uiElements.FavoritesBar;
import net.kroia.stockmarket.screen.uiElements.OrderHistoryPanel;
import net.kroia.stockmarket.screen.uiElements.PendingOrdersPanel;
import net.kroia.stockmarket.screen.uiElements.TransactionHistoryPanel;
import net.kroia.stockmarket.screen.uiElements.trading_panel.TradingPanel;
import net.kroia.stockmarket.screen.widgets.OrderbookVolumeHistogram;
import net.kroia.stockmarket.screen.widgets.OrderMarkerOverlay;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.networking.request.CancelOrderRequest;
import net.kroia.stockmarket.stockmarket.market.core.order.Order;
import net.kroia.stockmarket.stockmarket.marketmanager.MarketManager;
import net.kroia.stockmarket.stockmarket.marketmanager.PlayerPreferences;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;


public class TradeScreen extends StockMarketGuiScreen {

    private static class Texts{
        private static final String PREFIX = "gui."+ StockMarketMod.MOD_ID + ".trade_screen.";
        private static final Component TITLE = Component.translatable(PREFIX +"title");
        private static final Component MARKET_CLOSED = Component.translatable(PREFIX +"market_closed");
    }



    private final FavoritesBar favoritesBar;
    private final CandlestickChart candlestickChart;
    private final OrderMarkerOverlay orderMarkerOverlay;
    private final OrderbookVolumeHistogram orderbookVolumeHistogram;
    private final TradingPanel tradingPanel;
    private final TabElement ordersTabElement;
    private final PendingOrdersPanel pendingOrdersPanel;
    private final OrderHistoryPanel orderHistoryPanel;
    private final TransactionHistoryPanel transactionHistoryPanel;
    private final Label marketClosedLabel;
    private @Nullable UUID activeOrdersStreamID = null;
    private @Nullable ItemID currentMarketID = null;
    private int selectedBankAccountNr = -1;

    // Trading currency ID cached for balance lookups
    private @Nullable ItemID tradingCurrencyID = null;

    // Timer for periodic balance and price refresh
    private long lastBalanceRefreshMs = 0;
    private static final long BALANCE_REFRESH_INTERVAL_MS = 2000;
    private long lastPriceRefreshMs = 0;
    private static final long PRICE_REFRESH_INTERVAL_MS = 500;

    public TradeScreen()
    {
        super(Texts.TITLE);

        favoritesBar = new FavoritesBar(this::switchMarket);
        candlestickChart = new CandlestickChart();
        candlestickChart.setMarket(null);

        // Order marker overlay for draggable limit order lines on the chart
        orderMarkerOverlay = new OrderMarkerOverlay();
        candlestickChart.addOverlay(orderMarkerOverlay);
        orderMarkerOverlay.setOnCancelOrder(this::onCancelOrderFromChart);
        orderMarkerOverlay.setOnMoveOrder(this::onMoveOrderFromChart);

        orderbookVolumeHistogram = new OrderbookVolumeHistogram(candlestickChart);
        tradingPanel = new TradingPanel(this::onBuyMarket, this::onSellMarket, this::onBuyLimit, this::onSellLimit);

        marketClosedLabel = new Label(Texts.MARKET_CLOSED.getString());
        marketClosedLabel.setAlignment(Label.Alignment.CENTER);
        marketClosedLabel.setTextColor(UI_Colors.sellColorRed);
        marketClosedLabel.setTextFontScale(1.5f);
        marketClosedLabel.setEnabled(false);

        // Orders tab element with pending orders, order history, and market trades
        pendingOrdersPanel = new PendingOrdersPanel();
        pendingOrdersPanel.setOnMarketSwitch(this::switchMarket);
        orderHistoryPanel = new OrderHistoryPanel();
        transactionHistoryPanel = new TransactionHistoryPanel();
        ordersTabElement = new TabElement();
        ordersTabElement.addTab("Pending Orders", pendingOrdersPanel);
        ordersTabElement.addTab("My History", orderHistoryPanel);
        ordersTabElement.addTab("Market Trades", transactionHistoryPanel);

        addElement(favoritesBar);
        addElement(candlestickChart);
        addElement(orderbookVolumeHistogram);
        addElement(tradingPanel);
        addElement(marketClosedLabel);
        addElement(ordersTabElement);

        // Determine initial market from preferences, fall back to first available
        List<ItemID> markets = getAvailableMarkets();
        PlayerPreferences prefs = StockMarketGuiElement.getPlayerPreferences();

        ItemID initialMarket = prefs.getLastMarketID();
        if (initialMarket == null || !markets.contains(initialMarket)) {
            initialMarket = markets.isEmpty() ? null : markets.getFirst();
        }

        if (initialMarket != null) {
            currentMarketID = initialMarket;
            ClientMarket market = getMarket(currentMarketID);
            if (market != null) {
                market.subscribeToMarketPriceUpdate();
                candlestickChart.setMarket(market);
                orderMarkerOverlay.setCurrentMarket(currentMarketID);
                tradingPanel.setItemName(ClientPlayerUtilities.getItemDisplayText(currentMarketID.getStack()));
                tradingPanel.setLimitPrice(market.getCurrentMarketRealPrice());
            }
            // Update last market in preferences
            prefs.setLastMarketID(currentMarketID);
            StockMarketGuiElement.updatePlayerPreferences(prefs);
        }

        // Build favorites bar with all markets and current selection
        favoritesBar.rebuild(markets, prefs.getFavoriteMarketIDs(), currentMarketID);

        // Load initial history data
        orderHistoryPanel.setCurrentMarketID(currentMarketID);
        orderHistoryPanel.refresh();
        if (currentMarketID != null) {
            transactionHistoryPanel.refresh(currentMarketID);
        }

        getMarketManager().getTradingCurrencyIDAsync().thenAccept(currencyID -> {
            tradingCurrencyID = currencyID;
            if(currentMarketID == null)
                tradingPanel.setCurrencyName("?");
            else
                tradingPanel.setCurrencyName(ClientPlayerUtilities.getItemDisplayText(currencyID.getStack()));
        });

        // Set initial market price on the trading panel
        if (currentMarketID != null) {
            ClientMarket initMarket = getMarket(currentMarketID);
            if (initMarket != null) {
                tradingPanel.setCurrentMarketPrice(initMarket.getCurrentMarketRealPrice());
            }
        }

        getBankManager().getPersonalBankAccountDataAsync(getThisPlayerUUID()).thenAccept(bankAccountData -> {
            if(bankAccountData != null) {
                selectedBankAccountNr = bankAccountData.accountNumber;
            }
        });

        // Subscribe to active orders stream for real-time updates of pending orders
        activeOrdersStreamID = StreamSystem.startServerToClientStream(
                BACKEND_INSTANCES.NETWORKING.ACTIVE_ORDERS_STREAM,
                (byte) 0,
                data -> {
                    pendingOrdersPanel.updateOrders(data.orders);
                    orderMarkerOverlay.updateOrders(data.orders);
                    orderHistoryPanel.refresh();
                    if (currentMarketID != null)
                        transactionHistoryPanel.refresh(currentMarketID);
                },
                () -> {
                    activeOrdersStreamID = null;
                }
        );
    }


    public static void openScreen()
    {
        TradeScreen screen = new TradeScreen();
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();

        // Periodically update the market price on the trading panel (~500ms)
        if (now - lastPriceRefreshMs > PRICE_REFRESH_INTERVAL_MS) {
            lastPriceRefreshMs = now;
            if (currentMarketID != null) {
                ClientMarket market = getMarket(currentMarketID);
                if (market != null) {
                    tradingPanel.setCurrentMarketPrice(market.getCurrentMarketRealPrice());
                }
            }
        }

        // Periodically refresh balances for the trading panel (~2s)
        if (now - lastBalanceRefreshMs > BALANCE_REFRESH_INTERVAL_MS) {
            lastBalanceRefreshMs = now;
            refreshTradingPanelBalances();
        }
    }

    /**
     * Fetches current bank balances and updates the trading panel with money and item balances.
     */
    private void refreshTradingPanelBalances() {
        if (currentMarketID == null) return;
        UUID playerUUID = getThisPlayerUUID();
        if (playerUUID == null) return;

        getBankManager().getPersonalBankAccountDataAsync(playerUUID).thenAccept(bankAccountData -> {
            if (bankAccountData == null) return;

            // Money balance (trading currency)
            if (tradingCurrencyID != null) {
                BankData currencyBalance = bankAccountData.bankData.get(tradingCurrencyID);
                double moneyBal = currencyBalance != null ? currencyBalance.getRealBalance() : 0.0;
                tradingPanel.setMoneyBalance(moneyBal);
            }

            // Item balance (current market item)
            if (currentMarketID != null) {
                BankData itemBalance = bankAccountData.bankData.get(currentMarketID);
                double itemBal = itemBalance != null ? itemBalance.getRealBalance() : 0.0;
                tradingPanel.setItemBalance(itemBal);
            }
        });
    }

    /**
     * Switches the displayed market: unsubscribes old, subscribes new, updates all widgets.
     */
    private void switchMarket(ItemID newMarketID) {
        // Unsubscribe from old market
        if (currentMarketID != null) {
            ClientMarket oldMarket = getMarket(currentMarketID);
            if (oldMarket != null) {
                oldMarket.unsubscribeFromMarketPriceUpdate();
            }
        }

        currentMarketID = newMarketID;
        ClientMarket market = getMarket(newMarketID);
        if (market != null) {
            market.subscribeToMarketPriceUpdate();
            candlestickChart.setMarket(market);
            orderMarkerOverlay.setCurrentMarket(newMarketID);
            tradingPanel.setItemName(ClientPlayerUtilities.getItemDisplayText(newMarketID.getStack()));
            tradingPanel.setLimitPrice(market.getCurrentMarketRealPrice());
            tradingPanel.setCurrentMarketPrice(market.getCurrentMarketRealPrice());

            market.getSettings().thenAccept(settings -> {
                Minecraft.getInstance().execute(() -> {
                    tradingPanel.setMarketOpen(settings.marketOpen);
                    marketClosedLabel.setEnabled(!settings.marketOpen);
                });
            });
        }

        // Refresh balances for the new market
        refreshTradingPanelBalances();

        // Update history panels for the new market
        orderHistoryPanel.setCurrentMarketID(newMarketID);
        transactionHistoryPanel.refresh(newMarketID);

        // Update preferences
        PlayerPreferences prefs = StockMarketGuiElement.getPlayerPreferences();
        prefs.setLastMarketID(newMarketID);
        StockMarketGuiElement.updatePlayerPreferences(prefs);

        // Deferred rebuild — switchMarket is called from click handlers while the
        // GUI framework is still iterating the children list.
        favoritesBar.scheduleRebuild(getAvailableMarkets(), prefs.getFavoriteMarketIDs(), currentMarketID);
    }

    @Override
    public void onClose()
    {
        // Unsubscribe from active orders stream
        if (activeOrdersStreamID != null) {
            StreamSystem.stopStream(activeOrdersStreamID);
            activeOrdersStreamID = null;
        }

        if(currentMarketID != null)
        {
            ClientMarket market = getMarket(currentMarketID);
            if(market != null)
            {
                market.unsubscribeFromMarketPriceUpdate();
            }

            candlestickChart.setMarket(null);
            currentMarketID = null;
        }
        super.onClose();
    }


    @Override
    protected void updateLayout(Gui gui) {
        int padding = StockMarketGuiElement.padding;
        int spacing = StockMarketGuiElement.spacing;
        int width = getWidth() - padding * 2;
        int height = getHeight() - padding * 2;

        int orderbookVolumeWidth = width / 10;

        // Top-left: candlestick chart
        candlestickChart.setBounds(padding, padding, (width * 3) / 4 - orderbookVolumeWidth, (height * 2) / 3);
        // Right of chart: orderbook volume histogram
        orderbookVolumeHistogram.setBounds(candlestickChart.getRight(), candlestickChart.getTop(), orderbookVolumeWidth, candlestickChart.getHeight());
        // Top-right: favorites bar / market navigation
        favoritesBar.setBounds(orderbookVolumeHistogram.getRight() + spacing, padding, width - (orderbookVolumeHistogram.getRight() - padding + spacing), (height - spacing) / 2);
        // Bottom-right: trading panel
        tradingPanel.setBounds(orderbookVolumeHistogram.getRight() + spacing, favoritesBar.getBottom() + spacing, favoritesBar.getWidth(), height - (favoritesBar.getBottom() - padding + spacing));
        // Market closed label overlays the trading panel area
        marketClosedLabel.setBounds(tradingPanel.getLeft(), tradingPanel.getTop(), tradingPanel.getWidth(), 20);
        // Bottom-left: pending orders tab element (below chart + OB volume)
        ordersTabElement.setBounds(padding, candlestickChart.getBottom() + spacing,
                tradingPanel.getLeft() - spacing - padding,
                height - (candlestickChart.getBottom() - padding + spacing));
    }

    private @Nullable ClientMarket getValidMarketForOrder()
    {
        if(currentMarketID == null)
        {
            warn("No market selected");
            return null;
        }
        if(selectedBankAccountNr == -1)
        {
            warn("No bank account selected yet");
            return null;
        }
        ClientMarket market = getMarket(currentMarketID);
        if(market == null)
        {
            warn("Market is no longer available: " + currentMarketID);
            return null;
        }
        return market;
    }
    private void onBuyMarket(double quantity)
    {
        ClientMarket market = getValidMarketForOrder();
        if(market == null) return;
        market.createMarketOrder(selectedBankAccountNr, quantity).thenAccept(result->{
            info("Order creation response: "+result.status);
        });
    }
    private void onSellMarket(double quantity)
    {
        ClientMarket market = getValidMarketForOrder();
        if(market == null) return;
        market.createMarketOrder(selectedBankAccountNr, -quantity).thenAccept(result->{
            info("Order creation response: "+result.status);
        });
    }
    private void onBuyLimit(double quantity, double price)
    {
        ClientMarket market = getValidMarketForOrder();
        if(market == null) return;
        market.createLimitOrder(selectedBankAccountNr, quantity, price).thenAccept(result->{
            info("Order creation response: "+result.status);
        });
    }
    private void onSellLimit(double quantity, double price)
    {
        ClientMarket market = getValidMarketForOrder();
        if(market == null) return;
        market.createLimitOrder(selectedBankAccountNr, -quantity, price).thenAccept(result->{
            info("Order creation response: "+result.status);
        });
    }

    /**
     * Cancels an order directly from the chart overlay cancel button.
     */
    private void onCancelOrderFromChart(Order order) {
        CancelOrderRequest.InputData input = new CancelOrderRequest.InputData(
                order.getItemID(),
                order.getTime(),
                order.getType().ordinal(),
                order.getStartPrice(),
                order.getTargetVolume()
        );
        BACKEND_INSTANCES.NETWORKING.CANCEL_ORDER_REQUEST.sendRequestToServer(input);
    }

    /**
     * Moves an order by cancelling the old one and creating a new limit order
     * at the new price with the remaining volume.
     */
    private void onMoveOrderFromChart(Order order, double newPrice) {
        CancelOrderRequest.InputData cancelInput = new CancelOrderRequest.InputData(
                order.getItemID(),
                order.getTime(),
                order.getType().ordinal(),
                order.getStartPrice(),
                order.getTargetVolume()
        );
        BACKEND_INSTANCES.NETWORKING.CANCEL_ORDER_REQUEST.sendRequestToServer(cancelInput)
                .thenAccept(success -> {
                    if (success) {
                        // Re-create the order at the new price with remaining volume
                        double remainingVolume = MarketManager.convertToRealAmountStatic(order.getRemainingVolume());
                        ClientMarket market = getMarket(order.getItemID());
                        if (market != null && selectedBankAccountNr != -1) {
                            market.createLimitOrder(selectedBankAccountNr, remainingVolume, newPrice)
                                    .thenAccept(result -> {
                                        info("Move order response: " + result.status);
                                    });
                        }
                    } else {
                        warn("Failed to cancel order for move operation");
                    }
                });
    }

}
