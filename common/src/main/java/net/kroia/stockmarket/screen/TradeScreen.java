package net.kroia.stockmarket.screen;

import net.kroia.banksystem.banking.clientdata.BankData;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TabElement;
import net.kroia.modutilities.networking.client_server.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.screen.uiElements.FavoritesBar;
import net.kroia.stockmarket.screen.uiElements.OrderHistoryPanel;
import net.kroia.stockmarket.screen.uiElements.PairSelectorWidget;
import net.kroia.stockmarket.screen.uiElements.PendingOrdersPanel;
import net.kroia.stockmarket.screen.uiElements.TransactionHistoryPanel;
import net.kroia.stockmarket.screen.uiElements.trading_panel.InterMarketTradingPanel;
import net.kroia.stockmarket.screen.uiElements.trading_panel.TradingPanel;
import net.kroia.stockmarket.screen.widgets.OrderbookVolumeHistogram;
import net.kroia.stockmarket.screen.widgets.OrderMarkerOverlay;
import net.kroia.stockmarket.api.market.IPriceDataProvider;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.networking.request.CancelOrderRequest;
import net.kroia.stockmarket.networking.request.CreateOrderRequest;
import net.kroia.stockmarket.networking.request.PlaceInterMarketOrderRequest;
import net.kroia.stockmarket.stockmarket.market.core.order.InterMarketOrder;
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
        private static final String MONEY_MODE = Component.translatable(PREFIX + "mode_money").getString();
        private static final String PAIR_MODE = Component.translatable(PREFIX + "mode_pair").getString();
    }

    // Color for the active mode toggle button
    private static final int MODE_BUTTON_SELECTED_COLOR = 0xFF4a6fa5;
    // Color for the inactive mode toggle button (default button color)
    private static final int MODE_BUTTON_DEFAULT_COLOR = Button.DEFAULT_BACKGROUND_COLOR;



    private final FavoritesBar favoritesBar;
    private final CandlestickChart candlestickChart;
    private final OrderMarkerOverlay orderMarkerOverlay;
    private final OrderbookVolumeHistogram orderbookVolumeHistogram;
    private final TradingPanel tradingPanel;
    private final InterMarketTradingPanel interMarketTradingPanel;
    private final TabElement ordersTabElement;
    private final PendingOrdersPanel pendingOrdersPanel;
    private final OrderHistoryPanel orderHistoryPanel;
    private final TransactionHistoryPanel transactionHistoryPanel;
    private final Label marketClosedLabel;
    private @Nullable UUID activeOrdersStreamID = null;
    private @Nullable ItemID currentMarketID = null;
    private int selectedBankAccountNr = -1;

    // Mode toggle: Item/Money vs Item/Item
    private final Button moneyModeButton;
    private final Button pairModeButton;
    private final PairSelectorWidget pairSelectorWidget;
    private boolean isPairMode = false;

    // Pair mode state: tracks the "have" and "want" markets for cross-rate calculation
    private @Nullable ItemID pairHaveMarketID = null;
    private @Nullable ItemID pairWantMarketID = null;

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

        // Mode toggle buttons
        moneyModeButton = new Button(Texts.MONEY_MODE, () -> setMode(false));
        moneyModeButton.setTextFontScale(0.8f);
        moneyModeButton.setBackgroundColor(MODE_BUTTON_SELECTED_COLOR);

        pairModeButton = new Button(Texts.PAIR_MODE, () -> setMode(true));
        pairModeButton.setTextFontScale(0.8f);
        pairModeButton.setBackgroundColor(MODE_BUTTON_DEFAULT_COLOR);

        // Pair selector widget (hidden by default in money mode)
        pairSelectorWidget = new PairSelectorWidget(this::onPairSelected);
        pairSelectorWidget.setEnabled(false);

        favoritesBar = new FavoritesBar(this::switchMarket);
        candlestickChart = new CandlestickChart();
        candlestickChart.setMarket(null);

        // Order marker overlay for draggable limit order lines on the chart
        orderMarkerOverlay = new OrderMarkerOverlay();
        candlestickChart.addOverlay(orderMarkerOverlay);
        orderMarkerOverlay.setOnCancelOrder(this::onCancelOrderFromChart);
        orderMarkerOverlay.setOnMoveOrder(this::onMoveOrderFromChart);
        orderMarkerOverlay.setOnCancelInterMarketOrder(this::onCancelInterMarketOrderFromChart);
        orderMarkerOverlay.setOnMoveInterMarketOrder(this::onMoveInterMarketOrderFromChart);

        orderbookVolumeHistogram = new OrderbookVolumeHistogram(candlestickChart);
        tradingPanel = new TradingPanel(this::onBuyMarket, this::onSellMarket, this::onBuyLimit, this::onSellLimit);

        // Inter-market trading panel for pair mode (replaces regular trading panel)
        interMarketTradingPanel = new InterMarketTradingPanel(
                this::onInterMarketBuy, this::onInterMarketSell,
                this::onInterMarketBuyLimit, this::onInterMarketSellLimit);
        interMarketTradingPanel.setEnabled(false);

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

        addElement(moneyModeButton);
        addElement(pairModeButton);
        addElement(favoritesBar);
        addElement(pairSelectorWidget);
        addElement(candlestickChart);
        addElement(orderbookVolumeHistogram);
        addElement(tradingPanel);
        addElement(interMarketTradingPanel);
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

        // Restore pair selections from preferences
        if (prefs.getLastPairHaveMarketID() != null) {
            pairHaveMarketID = prefs.getLastPairHaveMarketID();
            pairSelectorWidget.setHaveMarketID(pairHaveMarketID);
        }
        if (prefs.getLastPairWantMarketID() != null) {
            pairWantMarketID = prefs.getLastPairWantMarketID();
            pairSelectorWidget.setWantMarketID(pairWantMarketID);
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
                    pendingOrdersPanel.updateInterMarketOrders(data.interMarketOrders);
                    orderMarkerOverlay.updateOrders(data.orders);
                    orderMarkerOverlay.updateInterMarketOrders(data.interMarketOrders);
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

        // Update cross-rate and inter-market trading panel when in pair mode
        if (isPairMode && pairHaveMarketID != null && pairWantMarketID != null) {
            ClientMarket haveM = getMarket(pairHaveMarketID);
            ClientMarket wantM = getMarket(pairWantMarketID);
            if (haveM != null && wantM != null) {
                double havePrice = haveM.getCurrentMarketRealPrice();
                double wantPrice = wantM.getCurrentMarketRealPrice();
                if (havePrice > 0) {
                    interMarketTradingPanel.setCurrentRate(wantPrice / havePrice);
                }
            }
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

            // "Have" item balance for inter-market trading panel
            if (isPairMode && pairHaveMarketID != null) {
                BankData haveBalance = bankAccountData.bankData.get(pairHaveMarketID);
                double haveBal = haveBalance != null ? haveBalance.getRealBalance() : 0.0;
                interMarketTradingPanel.setHaveBalance(haveBal);
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

            market.isMarketOpenAsync().thenAccept(isOpen -> {
                Minecraft.getInstance().execute(() -> {
                    tradingPanel.setMarketOpen(isOpen);
                    marketClosedLabel.setEnabled(!isOpen);
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

        // Unsubscribe from the "want" market price stream if in pair mode
        if (pairWantMarketID != null) {
            ClientMarket wantMarket = getMarket(pairWantMarketID);
            if (wantMarket != null) {
                wantMarket.unsubscribeFromMarketPriceUpdate();
            }
            pairWantMarketID = null;
        }
        pairHaveMarketID = null;

        super.onClose();
    }


    @Override
    protected void updateLayout(Gui gui) {
        int padding = StockMarketGuiElement.padding;
        int spacing = StockMarketGuiElement.spacing;
        int width = getWidth() - padding * 2;
        int height = getHeight() - padding * 2;

        int orderbookVolumeWidth = width / 10;
        int modeButtonHeight = 14;
        int modeButtonWidth = 60;

        // Right panel area (where favorites bar / pair selector / trading panel live)
        int rightPanelX = padding + (width * 3) / 4 + spacing;
        int rightPanelWidth = width - ((width * 3) / 4 + spacing);

        // Mode toggle buttons at the top of the right panel
        moneyModeButton.setBounds(rightPanelX, padding, modeButtonWidth, modeButtonHeight);
        pairModeButton.setBounds(rightPanelX + modeButtonWidth + spacing, padding, modeButtonWidth, modeButtonHeight);

        int selectorTop = padding + modeButtonHeight + spacing;

        // Top-left: candlestick chart (orderbook histogram always visible next to it)
        int chartWidth = (width * 3) / 4 - orderbookVolumeWidth;
        candlestickChart.setBounds(padding, padding, chartWidth, (height * 2) / 3);
        // Right of chart: orderbook volume histogram (shows have-market depth in pair mode)
        orderbookVolumeHistogram.setBounds(candlestickChart.getRight(), candlestickChart.getTop(), orderbookVolumeWidth, candlestickChart.getHeight());

        // Market selector area height (favorites bar or pair selector)
        int selectorHeight = (height - spacing) / 2 - (modeButtonHeight + spacing);

        // Top-right: favorites bar or pair selector (below mode buttons)
        favoritesBar.setBounds(rightPanelX, selectorTop, rightPanelWidth, selectorHeight);
        pairSelectorWidget.setBounds(rightPanelX, selectorTop, rightPanelWidth, selectorHeight);

        // Bottom-right: trading panel (below the selector area)
        int tradingPanelTop = selectorTop + selectorHeight + spacing;
        tradingPanel.setBounds(rightPanelX, tradingPanelTop, rightPanelWidth, height - (tradingPanelTop - padding));
        // Inter-market trading panel same bounds as regular trading panel
        interMarketTradingPanel.setBounds(tradingPanel.getLeft(), tradingPanel.getTop(),
                tradingPanel.getWidth(), tradingPanel.getHeight());
        // Market closed label overlays the trading panel area
        marketClosedLabel.setBounds(tradingPanel.getLeft(), tradingPanel.getTop(), tradingPanel.getWidth(), 20);
        // Bottom-left: pending orders tab element (below chart + OB volume)
        ordersTabElement.setBounds(padding, candlestickChart.getBottom() + spacing,
                tradingPanel.getLeft() - spacing - padding,
                height - (candlestickChart.getBottom() - padding + spacing));
    }

    /**
     * Toggles between Item/Money mode and Item/Item (pair) mode.
     * Shows/hides the FavoritesBar and PairSelectorWidget accordingly.
     * In pair mode the CandlestickChart switches to cross-rate mode (synthetic OHLC candles),
     * and the orderbook histogram is hidden (no meaningful depth for synthetic pairs).
     */
    private void setMode(boolean pairMode) {
        isPairMode = pairMode;
        favoritesBar.setEnabled(!pairMode);
        pairSelectorWidget.setEnabled(pairMode);
        orderMarkerOverlay.setPairMode(pairMode);
        moneyModeButton.setBackgroundColor(pairMode ? MODE_BUTTON_DEFAULT_COLOR : MODE_BUTTON_SELECTED_COLOR);
        pairModeButton.setBackgroundColor(pairMode ? MODE_BUTTON_SELECTED_COLOR : MODE_BUTTON_DEFAULT_COLOR);

        // CandlestickChart and orderbook histogram stay visible in both modes
        tradingPanel.setEnabled(!pairMode);
        interMarketTradingPanel.setEnabled(pairMode);

        if (pairMode) {
            // Restore pair selections from preferences, or initialize from current market
            PlayerPreferences prefs = StockMarketGuiElement.getPlayerPreferences();
            if (pairWantMarketID == null && prefs.getLastPairWantMarketID() != null) {
                pairWantMarketID = prefs.getLastPairWantMarketID();
                pairSelectorWidget.setWantMarketID(pairWantMarketID);
            }
            if (pairHaveMarketID == null && prefs.getLastPairHaveMarketID() != null) {
                pairHaveMarketID = prefs.getLastPairHaveMarketID();
                pairSelectorWidget.setHaveMarketID(pairHaveMarketID);
            }
            if (currentMarketID != null && pairWantMarketID == null) {
                pairWantMarketID = currentMarketID;
                pairSelectorWidget.setWantMarketID(pairWantMarketID);
            }

            // If both pair sides are already selected, configure cross-rate mode on the chart
            if (pairHaveMarketID != null && pairWantMarketID != null) {
                // Ensure both markets are subscribed to price updates — restored
                // selections from preferences skip onPairSelected() which normally does this
                ClientMarket haveMarket = getMarket(pairHaveMarketID);
                ClientMarket wantMarket = getMarket(pairWantMarketID);
                if (haveMarket != null) haveMarket.subscribeToMarketPriceUpdate();
                if (wantMarket != null) wantMarket.subscribeToMarketPriceUpdate();

                IPriceDataProvider crossRate = getMarketManager().getCrossRateMarket(pairHaveMarketID, pairWantMarketID);
                if (crossRate != null) {
                    candlestickChart.setPriceDataProvider(crossRate);
                }
                // Set pair direction on the overlay so order markers display at the correct rate
                orderMarkerOverlay.setPairDirection(pairHaveMarketID, pairWantMarketID);
                pendingOrdersPanel.setPairDirection(pairHaveMarketID);
            }
        } else {
            pendingOrdersPanel.setPairDirection(null);
            // When returning to money mode, switch back to the "want" market (primary slot)
            if (pairWantMarketID != null && !pairWantMarketID.equals(currentMarketID)) {
                switchMarket(pairWantMarketID);
            } else if (currentMarketID != null) {
                // Restore the current market on the chart
                ClientMarket market = getMarket(currentMarketID);
                if (market != null) {
                    candlestickChart.setMarket(market);
                }
            }
        }

        // Trigger re-layout so chart/histogram bounds update for the new mode
        updateLayout(null);
    }

    /**
     * Called when the pair selector widget fires a pair change event.
     * Subscribes to both market price streams and updates the chart to show
     * the "have" market as a fallback (pair-specific chart is IMT-15).
     */
    private void onPairSelected(PairSelectorWidget.PairSelection selection) {
        pairHaveMarketID = selection.haveMarketID;
        pairWantMarketID = selection.wantMarketID;

        // Persist pair selections to player preferences
        PlayerPreferences prefs = StockMarketGuiElement.getPlayerPreferences();
        prefs.setLastPairHaveMarketID(pairHaveMarketID);
        prefs.setLastPairWantMarketID(pairWantMarketID);
        StockMarketGuiElement.updatePlayerPreferences(prefs);

        // Update the overlay's pair direction so limit order markers display at the correct rate
        orderMarkerOverlay.setPairDirection(pairHaveMarketID, pairWantMarketID);

        // In pair mode, switch the chart/trading panel to show the "have" market as fallback
        if (pairHaveMarketID != null && !pairHaveMarketID.equals(currentMarketID)) {
            switchMarketInternal(pairHaveMarketID);
        }

        // Subscribe to the "want" market price updates as well
        if (pairWantMarketID != null) {
            ClientMarket wantMarket = getMarket(pairWantMarketID);
            if (wantMarket != null) {
                wantMarket.subscribeToMarketPriceUpdate();
            }
        }

        // Configure candlestick chart for cross-rate mode via the market manager
        if (pairHaveMarketID != null && pairWantMarketID != null) {
            IPriceDataProvider crossRate = getMarketManager().getCrossRateMarket(pairHaveMarketID, pairWantMarketID);
            if (crossRate != null) {
                candlestickChart.setPriceDataProvider(crossRate);
            }
        }

        // Update inter-market trading panel item names
        if (pairHaveMarketID != null) {
            interMarketTradingPanel.setHaveItemName(ClientPlayerUtilities.getItemDisplayText(pairHaveMarketID.getStack()));
        }
        if (pairWantMarketID != null) {
            interMarketTradingPanel.setWantItemName(ClientPlayerUtilities.getItemDisplayText(pairWantMarketID.getStack()));
        }
    }

    /**
     * Internal market switch that updates chart and trading panel without triggering
     * a FavoritesBar rebuild. Used by pair mode to switch the fallback display market.
     */
    private void switchMarketInternal(ItemID newMarketID) {
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

            market.isMarketOpenAsync().thenAccept(isOpen -> {
                Minecraft.getInstance().execute(() -> {
                    tradingPanel.setMarketOpen(isOpen);
                    marketClosedLabel.setEnabled(!isOpen);
                });
            });
        }

        refreshTradingPanelBalances();
        orderHistoryPanel.setCurrentMarketID(newMarketID);
        transactionHistoryPanel.refresh(newMarketID);
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
     * BUY want-items at market rate: spend have-items to acquire want-items.
     * Converts want-quantity to have-volume using current market prices, since
     * PlaceInterMarketOrderRequest takes the have-item volume.
     */
    private void onInterMarketBuy(double wantQuantity) {
        if (pairHaveMarketID == null || pairWantMarketID == null || selectedBankAccountNr == -1) return;
        ClientMarket haveM = getMarket(pairHaveMarketID);
        ClientMarket wantM = getMarket(pairWantMarketID);
        if (haveM == null || wantM == null) return;
        double havePrice = haveM.getCurrentMarketRealPrice();
        double wantPrice = wantM.getCurrentMarketRealPrice();
        double haveVolume = (havePrice > 0) ? wantQuantity * wantPrice / havePrice : 0;
        if (haveVolume <= 0) return;
        long targetBuyRaw = Math.round(wantQuantity * getItemFractionScaleFactor());
        PlaceInterMarketOrderRequest.InputData input = new PlaceInterMarketOrderRequest.InputData(
                pairHaveMarketID, pairWantMarketID, selectedBankAccountNr, haveVolume, 0L, targetBuyRaw);
        BACKEND_INSTANCES.NETWORKING.PLACE_INTER_MARKET_ORDER_REQUEST.sendRequestToServer(input)
                .thenAccept(result -> info("Inter-market buy: " + (result.success ? "OK" : result.errorMessage)));
    }

    /**
     * SELL want-items at market rate: sell want-items to receive have-items.
     * Reverses the direction so haveItemID=WANT (selling want-items) and
     * wantItemID=HAVE (receiving have-items).
     */
    private void onInterMarketSell(double wantQuantity) {
        if (pairHaveMarketID == null || pairWantMarketID == null || selectedBankAccountNr == -1) return;
        // Reverse direction: selling want-items to receive have-items
        PlaceInterMarketOrderRequest.InputData input = new PlaceInterMarketOrderRequest.InputData(
                pairWantMarketID, pairHaveMarketID, selectedBankAccountNr, wantQuantity, 0L, 0L);
        BACKEND_INSTANCES.NETWORKING.PLACE_INTER_MARKET_ORDER_REQUEST.sendRequestToServer(input)
                .thenAccept(result -> info("Inter-market sell: " + (result.success ? "OK" : result.errorMessage)));
    }

    /**
     * BUY LIMIT want-items: buy want-items with a rate limit.
     * Converts want-quantity to have-volume using the rate limit, since
     * PlaceInterMarketOrderRequest takes the have-item volume.
     */
    private void onInterMarketBuyLimit(double wantQuantity, double rateLimit) {
        if (pairHaveMarketID == null || pairWantMarketID == null || selectedBankAccountNr == -1) return;
        // haveVolume = wantQuantity * rateLimit (rate is have-per-want)
        double haveVolume = wantQuantity * rateLimit;
        if (haveVolume <= 0) return;
        long rawRateLimit = Math.round(rateLimit * getItemFractionScaleFactor());
        long targetBuyRaw = Math.round(wantQuantity * getItemFractionScaleFactor());
        PlaceInterMarketOrderRequest.InputData input = new PlaceInterMarketOrderRequest.InputData(
                pairHaveMarketID, pairWantMarketID, selectedBankAccountNr, haveVolume, rawRateLimit, targetBuyRaw);
        BACKEND_INSTANCES.NETWORKING.PLACE_INTER_MARKET_ORDER_REQUEST.sendRequestToServer(input)
                .thenAccept(result -> info("Inter-market buy limit: " + (result.success ? "OK" : result.errorMessage)));
    }

    /**
     * SELL LIMIT want-items: sell want-items with a rate limit.
     * Reverses the direction so haveItemID=WANT and wantItemID=HAVE.
     * The rate limit is inverted (1/rateLimit) since the direction is reversed.
     */
    private void onInterMarketSellLimit(double wantQuantity, double rateLimit) {
        if (pairHaveMarketID == null || pairWantMarketID == null || selectedBankAccountNr == -1) return;
        // Reverse direction: selling want-items to receive have-items
        // Invert rate: original rateLimit is have-per-want, reversed is want-per-have = 1/rateLimit
        long rawRateLimit = (rateLimit > 0) ? Math.round((1.0 / rateLimit) * getItemFractionScaleFactor()) : 0L;
        PlaceInterMarketOrderRequest.InputData input = new PlaceInterMarketOrderRequest.InputData(
                pairWantMarketID, pairHaveMarketID, selectedBankAccountNr, wantQuantity, rawRateLimit, 0L);
        BACKEND_INSTANCES.NETWORKING.PLACE_INTER_MARKET_ORDER_REQUEST.sendRequestToServer(input)
                .thenAccept(result -> info("Inter-market sell limit: " + (result.success ? "OK" : result.errorMessage)));
    }

    private long getItemFractionScaleFactor() {
        return net.kroia.banksystem.BankSystemModSettings.ITEM_FRACTION_SCALE_FACTOR;
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
     * Cancels an inter-market order directly from the chart overlay cancel button.
     */
    private void onCancelInterMarketOrderFromChart(InterMarketOrder order) {
        BACKEND_INSTANCES.NETWORKING.CANCEL_INTER_MARKET_ORDER_REQUEST.sendRequestToServer(order.getInterMarketGroupID());
    }

    /**
     * Moves an inter-market order by cancelling the old one and placing a new limit order
     * at the new rate with the same volume. The newRate is in the pair view's direction
     * (have-per-want).
     */
    private void onMoveInterMarketOrderFromChart(InterMarketOrder order, double newRate) {
        if (pairHaveMarketID == null || pairWantMarketID == null || selectedBankAccountNr == -1) return;
        if (newRate <= 0) return;

        boolean orderMatchesPairDirection = order.getSellItemID().equals(pairHaveMarketID);
        // Capture original parameters for rollback if the new order placement fails
        double fallbackHaveVolume = MarketManager.convertToRealAmountStatic(-order.getSellOrder().getRemainingVolume());
        long fallbackRateLimit = order.getCrossRateLimit();
        long fallbackTargetBuy = order.getTargetBuyVolume();

        BACKEND_INSTANCES.NETWORKING.CANCEL_INTER_MARKET_ORDER_REQUEST.sendRequestToServer(order.getInterMarketGroupID())
                .thenAccept(success -> {
                    Minecraft.getInstance().execute(() -> {
                        if (!success) {
                            warn("Failed to cancel inter-market order for move");
                            return;
                        }

                        PlaceInterMarketOrderRequest.InputData input;
                        if (orderMatchesPairDirection) {
                            // Order sells have-items → buys want-items (buy direction in pair view)
                            // Compute haveVolume in integer space to match the server's formula exactly:
                            // server: buyVol = rawHaveVol * SF / rawRate → must equal rawBuyVol
                            long rawBuyVolume = order.getTargetBuyVolume();
                            long SF = getItemFractionScaleFactor();
                            long rawRateLimit = Math.round(newRate * SF);
                            long rawHaveVolume = rawBuyVolume * rawRateLimit / SF;
                            double haveVolume = MarketManager.convertToRealAmountStatic(rawHaveVolume);
                            input = new PlaceInterMarketOrderRequest.InputData(
                                    pairHaveMarketID, pairWantMarketID, selectedBankAccountNr, haveVolume, rawRateLimit, rawBuyVolume);
                        } else {
                            // Order sells want-items → buys have-items (sell direction in pair view)
                            double wantVolume = MarketManager.convertToRealAmountStatic(order.getTargetSellVolume());
                            double invertedRate = 1.0 / newRate;
                            long rawRateLimit = Math.round(invertedRate * getItemFractionScaleFactor());
                            input = new PlaceInterMarketOrderRequest.InputData(
                                    pairWantMarketID, pairHaveMarketID, selectedBankAccountNr, wantVolume, rawRateLimit, 0L);
                        }

                        BACKEND_INSTANCES.NETWORKING.PLACE_INTER_MARKET_ORDER_REQUEST.sendRequestToServer(input)
                                .thenAccept(result -> {
                                    if (!result.success) {
                                        warn("Move inter-market order failed (" + result.errorMessage + "), restoring original");
                                        PlaceInterMarketOrderRequest.InputData fallback = new PlaceInterMarketOrderRequest.InputData(
                                                order.getSellItemID(), order.getBuyItemID(), selectedBankAccountNr,
                                                fallbackHaveVolume, fallbackRateLimit, fallbackTargetBuy);
                                        BACKEND_INSTANCES.NETWORKING.PLACE_INTER_MARKET_ORDER_REQUEST.sendRequestToServer(fallback)
                                                .thenAccept(fb -> {
                                                    if (!fb.success) {
                                                        error("Failed to restore original inter-market order: " + fb.errorMessage);
                                                    }
                                                });
                                    }
                                });
                    });
                });
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
        double originalPrice = MarketManager.convertToRealAmountStatic(order.getStartPrice());
        BACKEND_INSTANCES.NETWORKING.CANCEL_ORDER_REQUEST.sendRequestToServer(cancelInput)
                .thenAccept(success -> {
                    if (!success) {
                        warn("Failed to cancel order for move operation");
                        return;
                    }
                    double remainingVolume = MarketManager.convertToRealAmountStatic(order.getRemainingVolume());
                    ClientMarket market = getMarket(order.getItemID());
                    if (market == null || selectedBankAccountNr == -1) {
                        warn("Cannot place moved order: market or bank account unavailable");
                        return;
                    }
                    market.createLimitOrder(selectedBankAccountNr, remainingVolume, newPrice)
                            .thenAccept(result -> {
                                if (result.status != CreateOrderRequest.Status.CREATED) {
                                    warn("Move order failed (" + result.status + "), restoring at original price");
                                    market.createLimitOrder(selectedBankAccountNr, remainingVolume, originalPrice)
                                            .thenAccept(fallback -> {
                                                if (fallback.status != CreateOrderRequest.Status.CREATED) {
                                                    error("Failed to restore original order: " + fallback.status);
                                                }
                                            });
                                }
                            });
                });
    }

}
