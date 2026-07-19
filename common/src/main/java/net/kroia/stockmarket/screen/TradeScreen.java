package net.kroia.stockmarket.screen;

import net.kroia.banksystem.banking.BankPermission;
import net.kroia.banksystem.banking.clientdata.BankAccountData;
import net.kroia.banksystem.banking.clientdata.BankData;
import net.kroia.banksystem.screen.custom.BankAccountSelectionScreen;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TabElement;
import net.kroia.modutilities.gui.elements.base.GuiElement;
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
import java.util.concurrent.CompletableFuture;


public class TradeScreen extends StockMarketGuiScreen {

    private static class Texts{
        private static final String PREFIX = "gui."+ StockMarketMod.MOD_ID + ".trade_screen.";
        private static final Component TITLE = Component.translatable(PREFIX +"title");
        private static final Component MARKET_CLOSED = Component.translatable(PREFIX +"market_closed");
        private static final String MONEY_MODE = Component.translatable(PREFIX + "mode_money").getString();
        private static final String PAIR_MODE = Component.translatable(PREFIX + "mode_pair").getString();
        private static final String NEWS = Component.translatable(PREFIX + "news").getString();
        private static final Component NEWS_TOOLTIP = Component.translatable(PREFIX + "news.tooltip");
        // T-131: tooltip for the bank-account selector button in the top row.
        private static final Component SELECT_ACCOUNT_TOOLTIP = Component.translatable(PREFIX + "select_account.tooltip");
        // T-123 (untrusted slave gate) — shared with every other mutating screen.
        // T-125: the banner text is split into three lang keys so ModUtilities'
        // (single-line) Label widget can render the message across three stacked
        // rows and fit narrow trading-panel widths without overflow.
        static final Component UNTRUSTED_SLAVE_BANNER_LINE1 = Component.translatable("gui.stockmarket.untrusted_slave.banner_line1");
        static final Component UNTRUSTED_SLAVE_BANNER_LINE2 = Component.translatable("gui.stockmarket.untrusted_slave.banner_line2");
        static final Component UNTRUSTED_SLAVE_BANNER_LINE3 = Component.translatable("gui.stockmarket.untrusted_slave.banner_line3");
        static final Component UNTRUSTED_SLAVE_TOOLTIP = Component.translatable("gui.stockmarket.untrusted_slave.tooltip");
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
    /**
     * T-123 / T-125 (untrusted slave gate): banner shown across the top of the
     * trading area when the client is connected to a slave that the master
     * does NOT currently trust. The banner text explains the situation and
     * points at {@code /banksystem trust <slaveID>}. Enabled iff
     * {@link StockMarketGuiScreen#isUntrustedSlave()} is true at construction —
     * the trust flag is only refreshed at player join, so no live update
     * needed (see {@code ClientSettings.slaveTrusted} Javadoc).
     * <p>
     * T-125: ModUtilities' {@link Label} widget renders a single line only.
     * The banner is therefore rendered as three stacked labels sharing the
     * width of the (hidden) {@code tradingPanel} so the long text fits inside
     * the narrow buy/sell button column without horizontal overflow.
     */
    private final Label untrustedSlaveBanner1;
    private final Label untrustedSlaveBanner2;
    private final Label untrustedSlaveBanner3;
    private @Nullable UUID activeOrdersStreamID = null;
    private @Nullable ItemID currentMarketID = null;
    private int selectedBankAccountNr = -1;

    /**
     * T-131 (Bug B fix): cache of the most recent order lists pushed by the
     * {@code ActiveOrdersStream}. That stream is scoped server-side ONLY by the
     * executor player's UUID, so it delivers every pending order the player owns
     * across ALL of their bank accounts. This screen must show only the orders that
     * belong to the currently selected trading account ({@link #selectedBankAccountNr}).
     * Caching the raw lists lets us re-apply the per-account filter immediately when
     * the selected account changes (see {@link #applySelectedBankAccount}) instead of
     * waiting for the next stream push (which only fires when the order set changes).
     */
    private List<Order> lastStreamedOrders = new java.util.ArrayList<>();
    private List<InterMarketOrder> lastStreamedInterMarketOrders = new java.util.ArrayList<>();

    // Mode toggle: Item/Money vs Item/Item
    private final Button moneyModeButton;
    private final Button pairModeButton;
    // Opens the newspaper screen (T-074)
    private final Button newsButton;
    /**
     * T-131: bank-account selector button on its own dedicated full-width row at
     * the top of the right panel. Reuses
     * BankSystem's {@link BankAccountSelectionScreen.AccountButton} widget (shows
     * the selected account's icon + name). Clicking opens the shared
     * {@link BankAccountSelectionScreen} popup so the player can choose which bank
     * account funds their orders (money spent on buys, items received/sold). The
     * chosen account drives both the order pipeline ({@link #selectedBankAccountNr})
     * and the balances shown in the trading panel. Disabled on untrusted slaves
     * (T-123) — no order can be placed there anyway.
     */
    private final BankAccountSelectionScreen.AccountButton selectAccountButton;
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

        // News button: opens the newspaper screen (returns here on close)
        newsButton = new Button(Texts.NEWS, () -> setScreen(new NewsScreen(this)));
        newsButton.setTextFontScale(0.8f);
        newsButton.setHoverTooltipSupplier(Texts.NEWS_TOOLTIP::getString);
        newsButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
        // Fix 2 (T-120 idiom): anchor the tooltip at the cursor's TOP_RIGHT corner so
        // the tooltip body extends down-and-to-the-LEFT of the mouse (mouse cursor =
        // tooltip's top-right corner). Matches NewsScreen / PluginManagementScreen.
        newsButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);

        // T-131: bank-account selector. Reuses BankSystem's AccountButton (icon +
        // account name). Clicking opens the shared BankAccountSelectionScreen popup.
        // FR-002 (wired 2026-07-19): the popup now uses BankSystem's 6-arg
        // "gray-out locked accounts" constructor. It lists EVERY account the player
        // owns, but accounts where the player lacks BOTH DEPOSIT and WITHDRAW are
        // rendered grayed-out and non-clickable with an "insufficient rights" tooltip
        // (BankSystem-side lang key), instead of the previous WITHDRAW-only list +
        // selection-time rejection. The DEPOSIT|WITHDRAW AND-filter is expressed with
        // requireAllPermissions=true, showLockedAccounts=true (see the setOnFallingEdge
        // call below). This depends on BankSystem's FR-002 API at the unchanged runtime
        // version 1.21.1-2.0.4; the Loom remap cache was cleared so the fresh jar
        // carrying that constructor is picked up at runtime.
        selectAccountButton = new BankAccountSelectionScreen.AccountButton();
        // Fix 1 (T-131 follow-up): the reused BankSystem AccountButton renders the
        // account name in an internal (private) Label child at the default 1.0 font
        // scale. ModUtilities exposes no auto-fit / truncate API, and
        // AccountButton.setTextFontScale() only retargets the button's own (empty,
        // orphaned) label — NOT the account-name label — so it has no visible effect.
        // Reach the account-name Label through the public getChilds() API and scale
        // its text. The selector now occupies its own dedicated full-width top row
        // (see updateLayout), so a modest 0.9 scale leaves plenty of room for typical
        // account names while keeping a small safety margin for longer ones. The icon
        // child is an ItemView (not a Label), so it is left untouched and stays
        // visible. The Label instance is stable across setAccountData() calls (which
        // only call setText), so scaling it once here is sufficient.
        for (GuiElement child : selectAccountButton.getChilds()) {
            if (child instanceof Label) {
                child.setTextFontScale(0.9f);
            }
        }
        selectAccountButton.setHoverTooltipSupplier(Texts.SELECT_ACCOUNT_TOOLTIP::getString);
        selectAccountButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
        // Fix 2 (T-120 idiom): anchor the tooltip at the cursor's TOP_RIGHT corner so
        // the tooltip body extends down-and-to-the-LEFT of the mouse (mouse cursor =
        // tooltip's top-right corner). Matches NewsScreen / PluginManagementScreen.
        selectAccountButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
        // FR-002 6-arg overload: gray-out mode with a DEPOSIT|WITHDRAW AND-filter.
        //  - permissionFilter      = DEPOSIT | WITHDRAW bit mask. Combining the two
        //                            enum bit values with OR is the idiomatic way to
        //                            build a multi-bit mask in BankSystem (mirrors how
        //                            the old call passed BankPermission.WITHDRAW.getValue()
        //                            and how BankPermission itself ORs bits, e.g.
        //                            getSelfOwnerPermissions()). No magic numbers.
        //  - requireAllPermissions = true  → AND semantics: an account is "tradeable"
        //                            only if the player holds BOTH bits.
        //  - showLockedAccounts    = true  → list every account, but grays out + makes
        //                            non-clickable the ones failing the AND-filter, with
        //                            an "insufficient rights" hover tooltip.
        // Because locked accounts are non-clickable, onBankAccountSelected now only ever
        // fires for tradeable accounts; its playerCanTrade re-check is retained as
        // defense-in-depth (see that method's Javadoc).
        selectAccountButton.setOnFallingEdge(() ->
                setScreen(new BankAccountSelectionScreen(this, getThisPlayerUUID(),
                        this::onBankAccountSelected,
                        BankPermission.DEPOSIT.getValue() | BankPermission.WITHDRAW.getValue(),
                        true,   // requireAllPermissions: AND (needs BOTH deposit+withdraw)
                        true))); // showLockedAccounts: gray out non-tradeable accounts

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

        // T-123 / T-125 (untrusted slave gate): a red info banner shown across
        // the top of the trading area when the master does NOT trust this
        // slave. Renders in the same "market blocked" idiom as the market-closed
        // label (red, centered). T-125 splits the message into three stacked
        // labels because ModUtilities' Label is single-line only and the long
        // trust-explanation would otherwise overflow the narrow trading panel.
        untrustedSlaveBanner1 = new Label(Texts.UNTRUSTED_SLAVE_BANNER_LINE1.getString());
        untrustedSlaveBanner1.setAlignment(Label.Alignment.CENTER);
        untrustedSlaveBanner1.setTextColor(UI_Colors.sellColorRed);
        untrustedSlaveBanner1.setTextFontScale(0.7f);
        untrustedSlaveBanner1.setEnabled(isUntrustedSlave());

        untrustedSlaveBanner2 = new Label(Texts.UNTRUSTED_SLAVE_BANNER_LINE2.getString());
        untrustedSlaveBanner2.setAlignment(Label.Alignment.CENTER);
        untrustedSlaveBanner2.setTextColor(UI_Colors.sellColorRed);
        untrustedSlaveBanner2.setTextFontScale(0.7f);
        untrustedSlaveBanner2.setEnabled(isUntrustedSlave());

        untrustedSlaveBanner3 = new Label(Texts.UNTRUSTED_SLAVE_BANNER_LINE3.getString());
        untrustedSlaveBanner3.setAlignment(Label.Alignment.CENTER);
        untrustedSlaveBanner3.setTextColor(UI_Colors.sellColorRed);
        untrustedSlaveBanner3.setTextFontScale(0.7f);
        untrustedSlaveBanner3.setEnabled(isUntrustedSlave());

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
        addElement(newsButton);
        addElement(selectAccountButton);
        addElement(favoritesBar);
        addElement(pairSelectorWidget);
        addElement(candlestickChart);
        addElement(orderbookVolumeHistogram);
        addElement(tradingPanel);
        addElement(interMarketTradingPanel);
        addElement(marketClosedLabel);
        addElement(untrustedSlaveBanner1);
        addElement(untrustedSlaveBanner2);
        addElement(untrustedSlaveBanner3);
        addElement(ordersTabElement);

        // T-123 (untrusted slave gate): fully lock down every mutating input
        // path on this screen. The server also refuses these operations via
        // NetworkGate — this is the UX side of the same rule.
        if (isUntrustedSlave()) {
            // TradingPanel (money mode) and InterMarketTradingPanel (pair mode)
            // both use setEnabled(false) — ModUtilities hides + skips input
            // dispatch, so the buy/sell/limit buttons become inert.
            tradingPanel.setEnabled(false);
            interMarketTradingPanel.setEnabled(false);
            // Drop the drag/cancel callbacks on the chart overlay so a dragged
            // order line can't be moved and the on-chart cancel button no-ops.
            orderMarkerOverlay.setOnCancelOrder(null);
            orderMarkerOverlay.setOnMoveOrder(null);
            orderMarkerOverlay.setOnCancelInterMarketOrder(null);
            orderMarkerOverlay.setOnMoveInterMarketOrder(null);
            // T-131: no orders can be placed on an untrusted slave, so lock the
            // account selector too (same UX-side gate as the mutating panels).
            selectAccountButton.setEnabled(false);
        }

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
                tradingPanel.setItemName(currentMarketID.getStack().getHoverName().getString());
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
                tradingPanel.setCurrencyName(currencyID.getStack().getHoverName().getString());
        });

        // Set initial market price on the trading panel
        if (currentMarketID != null) {
            ClientMarket initMarket = getMarket(currentMarketID);
            if (initMarket != null) {
                tradingPanel.setCurrentMarketPrice(initMarket.getCurrentMarketRealPrice());
            }
        }

        // T-131: initialize the selected trading account. Prefer the persisted
        // preference (if it still exists and the player can use it), otherwise fall
        // back to the personal account.
        initializeSelectedBankAccount();

        // Subscribe to active orders stream for real-time updates of pending orders
        activeOrdersStreamID = StreamSystem.startServerToClientStream(
                BACKEND_INSTANCES.NETWORKING.ACTIVE_ORDERS_STREAM,
                (byte) 0,
                data -> {
                    // T-131 (Bug B fix): the stream is scoped only by player UUID, so it
                    // carries orders from EVERY account the player owns. Cache the raw
                    // lists and push them through the per-account filter so the panel /
                    // chart markers only show the currently selected account's orders.
                    lastStreamedOrders = (data.orders != null) ? data.orders : new java.util.ArrayList<>();
                    lastStreamedInterMarketOrders = (data.interMarketOrders != null) ? data.interMarketOrders : new java.util.ArrayList<>();
                    applyOrdersToPanels();
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
                } else {
                    // Safety net: the market vanished from the client cache (deleted on
                    // the server) but this screen was not notified directly — e.g. the
                    // MarketRemovedPacket arrived while a popup covered this screen.
                    // Re-validate and fall back gracefully.
                    handleMarketRemoved(currentMarketID);
                }
            }
            // Same validation for the pair-mode legs
            if (pairHaveMarketID != null && getMarket(pairHaveMarketID) == null) {
                handleMarketRemoved(pairHaveMarketID);
            }
            if (pairWantMarketID != null && getMarket(pairWantMarketID) == null) {
                handleMarketRemoved(pairWantMarketID);
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

        // T-131: show the balances of the SELECTED account (not always the personal
        // one). Fall back to the personal account only while no account has been
        // resolved yet (selectedBankAccountNr == -1).
        CompletableFuture<BankAccountData> accountFuture = (selectedBankAccountNr == -1)
                ? getBankManager().getPersonalBankAccountDataAsync(playerUUID)
                : getBankManager().getBankAccountDataAsync(selectedBankAccountNr);
        accountFuture.thenAccept(bankAccountData -> {
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
     * Determines the initially selected trading bank account (T-131).
     * <p>
     * First tries the persisted {@link PlayerPreferences#getLastTradingBankAccountNr()}
     * preference: if that account still exists and the player holds both the
     * {@link BankPermission#DEPOSIT} and {@link BankPermission#WITHDRAW} permissions
     * on it (validated via {@link #playerCanTrade(BankAccountData, UUID)}, which uses
     * only pre-existing runtime API), it is used. Otherwise (never set, deleted, or
     * permissions revoked) the player's personal account is used as a fallback.
     * Results are applied on the main render thread via
     * {@link #applySelectedBankAccount(BankAccountData)}, which sets both
     * {@link #selectedBankAccountNr} and the selector button's account data — so the
     * button reflects the resolved default account immediately on screen open, not
     * only after a manual selection.
     */
    private void initializeSelectedBankAccount() {
        UUID playerUUID = getThisPlayerUUID();
        if (playerUUID == null) return;
        int preferred = StockMarketGuiElement.getPlayerPreferences().getLastTradingBankAccountNr();
        if (preferred == -1) {
            fallBackToPersonalBankAccount(playerUUID);
            return;
        }
        getBankManager().getBankAccountDataAsync(preferred).thenAccept(accountData -> {
            // Validate the persisted account still exists and is still usable
            // (owned / permitted). Permissions may have changed since the choice was
            // saved last session, so re-check that the player still holds BOTH
            // DEPOSIT and WITHDRAW via playerCanTrade. This validation is independent
            // of the FR-002 gray-out popup (which only gates interactive selection),
            // so it must stay here to keep a stale/revoked persisted preference from
            // silently loading a non-tradeable account on open.
            // Fall back to the personal account when the preference is no longer valid.
            if (accountData != null && playerCanTrade(accountData, playerUUID)) {
                Minecraft.getInstance().execute(() -> applySelectedBankAccount(accountData));
            } else {
                fallBackToPersonalBankAccount(playerUUID);
            }
        });
    }

    /**
     * Resolves the player's personal bank account and applies it as the active
     * trading account (T-131). Used when no valid preference is available.
     * @param playerUUID the local player's UUID
     */
    private void fallBackToPersonalBankAccount(UUID playerUUID) {
        getBankManager().getPersonalBankAccountDataAsync(playerUUID).thenAccept(accountData -> {
            if (accountData != null) {
                Minecraft.getInstance().execute(() -> applySelectedBankAccount(accountData));
            }
        });
    }

    /**
     * Applies a resolved bank account as the active trading account (T-131):
     * updates {@link #selectedBankAccountNr}, reflects the account icon/name on the
     * {@link #selectAccountButton}, and refreshes the displayed balances so money /
     * item balances match the newly selected account. Must run on the main thread.
     * @param accountData the account to activate (non-null)
     */
    private void applySelectedBankAccount(BankAccountData accountData) {
        selectedBankAccountNr = accountData.accountNumber;
        selectAccountButton.setAccountData(accountData);
        refreshTradingPanelBalances();
        // T-131 (balance-display fix): the FavoritesBar "Bank Balance" frame (money
        // mode) and the PairSelectorWidget "Bal:" labels (pair mode) have their OWN
        // balance-fetch paths that previously always read the personal account. Mirror
        // the selected account into them so EVERY balance shown in the trade screen
        // follows the chosen account, then their labels refresh immediately.
        favoritesBar.setSelectedBankAccountNr(selectedBankAccountNr);
        pairSelectorWidget.setSelectedBankAccountNr(selectedBankAccountNr);
        // T-131 (Bug B fix): re-filter the cached pending orders / chart markers for the
        // newly selected account so the list reflects the switch immediately, without
        // waiting for the next ActiveOrdersStream push (which only fires when the order
        // set actually changes).
        applyOrdersToPanels();
    }

    /**
     * T-131 (Bug B fix): pushes the cached pending orders to the orders panel and the
     * chart overlay, filtered to the currently selected trading account
     * ({@link #selectedBankAccountNr}).
     * <p>
     * The {@code ActiveOrdersStream} is scoped only by the player's UUID server-side, so
     * it delivers orders from every account the player owns. Without this filter the
     * panel would keep displaying the previously active account's orders after the
     * player switches accounts. Called both when fresh stream data arrives and when the
     * selected account changes.
     */
    private void applyOrdersToPanels() {
        List<Order> orders = filterOrdersBySelectedAccount(lastStreamedOrders);
        List<InterMarketOrder> interMarketOrders =
                filterInterMarketOrdersBySelectedAccount(lastStreamedInterMarketOrders);
        pendingOrdersPanel.updateOrders(orders);
        pendingOrdersPanel.updateInterMarketOrders(interMarketOrders);
        orderMarkerOverlay.updateOrders(orders);
        orderMarkerOverlay.updateInterMarketOrders(interMarketOrders);
    }

    /**
     * Filters the given orders down to those placed on the currently selected trading
     * account. While no account has been resolved yet ({@link #selectedBankAccountNr}
     * == -1, a transient startup window) the full list is returned unchanged, matching
     * the personal-account fallback semantics used by the balance paths.
     * @param source the unfiltered order list from the stream
     * @return a new list containing only the selected account's orders
     */
    private List<Order> filterOrdersBySelectedAccount(List<Order> source) {
        if (selectedBankAccountNr == -1) return new java.util.ArrayList<>(source);
        List<Order> filtered = new java.util.ArrayList<>();
        for (Order order : source) {
            if (order.getBankAccountNr() == selectedBankAccountNr) {
                filtered.add(order);
            }
        }
        return filtered;
    }

    /**
     * Inter-market variant of {@link #filterOrdersBySelectedAccount(List)}. An
     * inter-market order's account is taken from its buy leg
     * ({@link InterMarketOrder#getBankAccountNr()}), which equals the account the whole
     * order was placed on.
     * @param source the unfiltered inter-market order list from the stream
     * @return a new list containing only the selected account's inter-market orders
     */
    private List<InterMarketOrder> filterInterMarketOrdersBySelectedAccount(List<InterMarketOrder> source) {
        if (selectedBankAccountNr == -1) return new java.util.ArrayList<>(source);
        List<InterMarketOrder> filtered = new java.util.ArrayList<>();
        for (InterMarketOrder order : source) {
            if (order.getBankAccountNr() == selectedBankAccountNr) {
                filtered.add(order);
            }
        }
        return filtered;
    }

    /**
     * Callback fired when the player picks an account in the
     * {@link BankAccountSelectionScreen} popup (T-131).
     * <p>
     * FR-002: the popup now opens in gray-out mode with a DEPOSIT|WITHDRAW AND-filter
     * (requireAllPermissions=true, showLockedAccounts=true), so non-tradeable accounts
     * are rendered grayed-out and non-clickable — this callback can therefore only fire
     * for accounts the player is allowed to trade on. The
     * {@link #playerCanTrade(BankAccountData, UUID)} re-check below is kept as
     * defense-in-depth (belt-and-suspenders): a rejected account is neither applied nor
     * persisted. A valid account is applied, persisted to the player's preferences, and
     * mirrored on the selector button.
     * @param accountNr the account number selected in the popup
     */
    private void onBankAccountSelected(int accountNr) {
        UUID playerUUID = getThisPlayerUUID();
        if (playerUUID == null) return;
        getBankManager().getBankAccountDataAsync(accountNr).thenAccept(accountData -> {
            if (accountData == null) return;
            Minecraft.getInstance().execute(() -> {
                // Defense-in-depth (FR-002): the popup already grays out and blocks
                // clicks on accounts missing DEPOSIT or WITHDRAW, so this should always
                // pass. Kept as a belt-and-suspenders guard so the trading account is
                // guaranteed to support both buying (WITHDRAW) and selling (DEPOSIT).
                // Do not apply or persist a rejected choice.
                if (!playerCanTrade(accountData, playerUUID)) {
                    warn("Selected account lacks the required deposit/withdraw permissions for trading");
                    return;
                }
                applySelectedBankAccount(accountData);
                // Persist so the choice is restored the next time the screen opens.
                PlayerPreferences prefs = StockMarketGuiElement.getPlayerPreferences();
                prefs.setLastTradingBankAccountNr(accountNr);
                StockMarketGuiElement.updatePlayerPreferences(prefs);
            });
        });
    }

    /**
     * Permission guard for a trading account: returns true only when
     * {@code playerUUID} holds BOTH {@link BankPermission#DEPOSIT} and
     * {@link BankPermission#WITHDRAW} on {@code accountData}, so the account can fund
     * buys (WITHDRAW currency) and receive sells (DEPOSIT items).
     * <p>
     * Two use sites: (1) {@link #initializeSelectedBankAccount()} validates the
     * persisted {@code lastTradingBankAccountNr} on screen open (permissions may have
     * changed since it was saved), falling back to the personal account when it is no
     * longer tradeable; (2) {@link #onBankAccountSelected(int)} re-checks the picked
     * account as FR-002 defense-in-depth (the gray-out popup already blocks
     * non-tradeable picks). Implemented with two single-bit
     * {@link BankAccountData#hasAnyPermission(UUID, int)} checks (for a single bit,
     * "any" equals "has that bit").
     *
     * @param accountData the account to validate (non-null)
     * @param playerUUID  the local player's UUID
     * @return true iff the player holds both DEPOSIT and WITHDRAW on the account
     */
    private boolean playerCanTrade(BankAccountData accountData, UUID playerUUID) {
        return accountData.hasAnyPermission(playerUUID, BankPermission.DEPOSIT.getValue())
                && accountData.hasAnyPermission(playerUUID, BankPermission.WITHDRAW.getValue());
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
            tradingPanel.setItemName(newMarketID.getStack().getHoverName().getString());
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

    /**
     * {@inheritDoc}
     * Server broadcast: a market was deleted. The client caches are already purged
     * (getMarket returns null for it), so this only has to fix up the screen state.
     */
    @Override
    public void onMarketRemoved(ItemID marketID) {
        handleMarketRemoved(marketID);
    }

    /**
     * Removes every reference this screen holds to a market that no longer exists.
     * Idempotent — safe to call again for an already-handled market.
     * <ul>
     *   <li>Pair mode: clears the deleted "have"/"want" leg and the (now dead)
     *       cross-rate chart so the user can pick a new pair.</li>
     *   <li>Money mode: if the deleted market was selected, switches to the first
     *       remaining market, or clears the chart when no markets are left.</li>
     *   <li>Always rebuilds the favorites bar so the deleted entry disappears.</li>
     * </ul>
     *
     * @param deletedMarketID the market that was deleted on the server
     */
    private void handleMarketRemoved(ItemID deletedMarketID) {
        // ── Pair-mode legs ──
        boolean pairBroken = false;
        if (deletedMarketID.equals(pairHaveMarketID)) {
            pairHaveMarketID = null;
            pairSelectorWidget.setHaveMarketID(null);
            pairBroken = true;
        }
        if (deletedMarketID.equals(pairWantMarketID)) {
            pairWantMarketID = null;
            pairSelectorWidget.setWantMarketID(null);
            pairBroken = true;
        }
        if (pairBroken) {
            // The cross-rate provider was built on the deleted market and is dead.
            orderMarkerOverlay.setPairDirection(pairHaveMarketID, pairWantMarketID);
            pendingOrdersPanel.setPairDirection(isPairMode ? pairHaveMarketID : null);
            if (isPairMode) {
                // Clear the dead cross-rate chart; a new one is set once the user
                // selects a replacement pair via the pair selector.
                candlestickChart.setPriceDataProvider(null);
            }
        }

        // ── Money-mode selection ──
        if (deletedMarketID.equals(currentMarketID)) {
            List<ItemID> markets = getAvailableMarkets();
            ItemID fallback = markets.isEmpty() ? null : markets.getFirst();
            if (!isPairMode && fallback != null) {
                // Graceful fallback: show the first remaining market
                // (also rebuilds the favorites bar).
                switchMarket(fallback);
                return;
            }
            // No market left (or pair mode active): clear the dead selection.
            currentMarketID = null;
            orderHistoryPanel.setCurrentMarketID(null);
            if (!isPairMode) {
                candlestickChart.setMarket(null);
                orderMarkerOverlay.setCurrentMarket(null);
            }
        }

        // Rebuild the market list so the deleted entry disappears immediately.
        favoritesBar.scheduleRebuild(getAvailableMarkets(),
                StockMarketGuiElement.getPlayerPreferences().getFavoriteMarketIDs(), currentMarketID);
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

        // T-131 (follow-up): the bank-account selector gets its own dedicated,
        // full-width row at the very top of the right panel. The account name is
        // variable-length and needs the maximum available width, so it no longer
        // shares the crowded mode-button row. Everything below is shifted down by
        // one button row to make room.
        int accountRowY = padding;
        selectAccountButton.setBounds(rightPanelX, accountRowY, rightPanelWidth, modeButtonHeight);

        // Mode toggle buttons in the SECOND row of the right panel (shifted down
        // by one row to sit below the full-width account selector).
        int modeRowY = accountRowY + modeButtonHeight + spacing;
        moneyModeButton.setBounds(rightPanelX, modeRowY, modeButtonWidth, modeButtonHeight);
        pairModeButton.setBounds(rightPanelX + modeButtonWidth + spacing, modeRowY, modeButtonWidth, modeButtonHeight);
        // News button right-aligned in the same (mode-button) row
        int newsButtonWidth = 40;
        newsButton.setBounds(rightPanelX + rightPanelWidth - newsButtonWidth, modeRowY, newsButtonWidth, modeButtonHeight);

        int selectorTop = modeRowY + modeButtonHeight + spacing;

        // Top-left: candlestick chart (orderbook histogram always visible next to it)
        int chartWidth = (width * 3) / 4 - orderbookVolumeWidth;
        candlestickChart.setBounds(padding, padding, chartWidth, (height * 2) / 3);
        // Right of chart: orderbook volume histogram (shows have-market depth in pair mode)
        orderbookVolumeHistogram.setBounds(candlestickChart.getRight(), candlestickChart.getTop(), orderbookVolumeWidth, candlestickChart.getHeight());

        // Market selector area height (favorites bar or pair selector). Two button
        // rows now sit above it (the account selector row + the mode-button row), so
        // reserve vertical space for both; this keeps the trading panel top at the
        // same position it had with a single top row.
        int selectorHeight = (height - spacing) / 2 - 2 * (modeButtonHeight + spacing);

        // Top-right: favorites bar or pair selector (below mode buttons)
        favoritesBar.setBounds(rightPanelX, selectorTop, rightPanelWidth, selectorHeight);
        pairSelectorWidget.setBounds(rightPanelX, selectorTop, rightPanelWidth, selectorHeight);

        // Bottom-right: trading panel (below the selector area). The account
        // selector now lives in its own dedicated full-width top row (placed above),
        // so no extra vertical reservation is needed here.
        int tradingPanelTop = selectorTop + selectorHeight + spacing;
        tradingPanel.setBounds(rightPanelX, tradingPanelTop, rightPanelWidth, height - (tradingPanelTop - padding));
        // Inter-market trading panel same bounds as regular trading panel
        interMarketTradingPanel.setBounds(tradingPanel.getLeft(), tradingPanel.getTop(),
                tradingPanel.getWidth(), tradingPanel.getHeight());
        // Market closed label overlays the trading panel area
        marketClosedLabel.setBounds(tradingPanel.getLeft(), tradingPanel.getTop(), tradingPanel.getWidth(), 20);
        // T-123 / T-125 banner: three stacked labels centered horizontally
        // across the (now-hidden) trading panel. Split into three rows because
        // ModUtilities' Label is single-line only and the full trust-explanation
        // text overflows the narrow trading-panel width otherwise. Only visible
        // when isUntrustedSlave() was true at construction (enable flag is set
        // once and never toggled at runtime).
        int bannerLineHeight = 12; // matches textFontScale=0.7f visible height
        int bannerX = tradingPanel.getLeft();
        int bannerW = tradingPanel.getWidth();
        int bannerY = tradingPanel.getTop() + spacing;
        untrustedSlaveBanner1.setBounds(bannerX, bannerY, bannerW, bannerLineHeight);
        untrustedSlaveBanner2.setBounds(bannerX, bannerY + bannerLineHeight, bannerW, bannerLineHeight);
        untrustedSlaveBanner3.setBounds(bannerX, bannerY + 2 * bannerLineHeight, bannerW, bannerLineHeight);
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
        // T-123 (untrusted slave gate): override the enable — the user can
        // still switch tabs to view the pair chart, but the mutating panels
        // stay hidden behind the info banner.
        if (isUntrustedSlave()) {
            tradingPanel.setEnabled(false);
            interMarketTradingPanel.setEnabled(false);
        }

        if (pairMode) {
            // Clear the overlay's market association — regular money-market limit orders
            // must not be drawn on the cross-rate chart (wrong price scale), so no stale
            // market ID may leak into pair mode.
            orderMarkerOverlay.setCurrentMarket(null);

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
                // Restore the current market on the chart and the order marker overlay
                // (the overlay's market was cleared when pair mode was entered)
                ClientMarket market = getMarket(currentMarketID);
                if (market != null) {
                    candlestickChart.setMarket(market);
                }
                orderMarkerOverlay.setCurrentMarket(currentMarketID);
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
            interMarketTradingPanel.setHaveItemName(pairHaveMarketID.getStack().getHoverName().getString());
        }
        if (pairWantMarketID != null) {
            interMarketTradingPanel.setWantItemName(pairWantMarketID.getStack().getHoverName().getString());
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
            // Only bind the overlay to the market in money mode — in pair mode the overlay
            // must stay unbound so money-market limit orders are never drawn on the
            // cross-rate chart (their prices are money-denominated, not cross rates).
            if (!isPairMode) {
                orderMarkerOverlay.setCurrentMarket(newMarketID);
            }
            tradingPanel.setItemName(newMarketID.getStack().getHoverName().getString());
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
    /**
     * T-131 (Bug A diagnostics): reports the outcome of a create-order request.
     * Previously the buy/sell/limit callbacks logged the status at INFO regardless of
     * outcome, so a server rejection (e.g. NOT_ENOUGH_MONEY, or NO_BANK_USER on the
     * selected account) produced no signal at the default log level and the order
     * simply never appeared. A non-CREATED status is now logged at WARN so the actual
     * rejection reason is visible when diagnosing "the order was not placed".
     * @param result the server response for the create-order request
     */
    private void logOrderResult(CreateOrderRequest.OutputData result) {
        if (result.status == CreateOrderRequest.Status.CREATED) {
            info("Order created");
        } else {
            warn("Order rejected: " + result.status);
        }
    }

    private void onBuyMarket(double quantity)
    {
        ClientMarket market = getValidMarketForOrder();
        if(market == null) return;
        market.createMarketOrder(selectedBankAccountNr, quantity).thenAccept(this::logOrderResult);
    }
    private void onSellMarket(double quantity)
    {
        ClientMarket market = getValidMarketForOrder();
        if(market == null) return;
        market.createMarketOrder(selectedBankAccountNr, -quantity).thenAccept(this::logOrderResult);
    }
    private void onBuyLimit(double quantity, double price)
    {
        ClientMarket market = getValidMarketForOrder();
        if(market == null) return;
        market.createLimitOrder(selectedBankAccountNr, quantity, price).thenAccept(this::logOrderResult);
    }
    private void onSellLimit(double quantity, double price)
    {
        ClientMarket market = getValidMarketForOrder();
        if(market == null) return;
        market.createLimitOrder(selectedBankAccountNr, -quantity, price).thenAccept(this::logOrderResult);
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
