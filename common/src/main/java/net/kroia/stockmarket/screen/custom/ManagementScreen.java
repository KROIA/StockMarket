package net.kroia.stockmarket.screen.custom;


import dev.architectury.event.events.common.TickEvent;
import net.kroia.banksystem.screen.uiElements.AskPopupScreen;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.ServerMarketSettingsData;
import net.kroia.stockmarket.screen.uiElements.TradingPairSelectionView;
import net.kroia.stockmarket.screen.uiElements.TradingPairView;
import net.kroia.stockmarket.screen.uiElements.chart.TradingChartWidget;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class ManagementScreen extends StockMarketGuiScreen {
    public static final int padding = 5;
    public static final int spacing = 5;
    public static final int elementHeight = 20;

    public static final class COLOR
    {
        public static final int BACKGROUND = GuiElement.DEFAULT_BACKGROUND_COLOR;
        public static final int RED = 0xFFcf1204;
        public static final int RED_HOVER = 0xFF8a2019;
        public static final int RED_CLICKED = 0xFF8c0c03;
        public static final int ORANGE = 0xFFdb5704;
        public static final int ORANGE_HOVER = 0xFF82431b;
        public static final int ORANGE_CLICKED = 0xFF8c3904;
        public static final int GREEN = 0xFF1f9c04;
        public static final int GREEN_HOVER = 0xFF367530;
        public static final int GREEN_CLICKED = 0xFF0c6b03;
    }
    public static final class TEXT {
        private static final String NAME = "management_screen";
        public static final String PREFIX = "gui." + StockMarketMod.MOD_ID + "." + NAME + ".";

        private static final Component TITLE = Component.translatable(PREFIX + "title");
        public static final Component NEW_MARKET_TITLE = Component.translatable(PREFIX + "new_market_title");
        public static final Component NEW_CUSTOM_MARKET_BUTTON = Component.translatable(PREFIX + "new_custom_market");
        public static final Component NEW_MARKET_BY_CATEGORY = Component.translatable(PREFIX + "new_market_by_category");
        public static final Component SELECTED_MARKET_TITLE = Component.translatable(PREFIX + "selected_market_title");
        public static final Component REMOVE_MARKET_BUTTON = Component.translatable(PREFIX + "remove_market");

        public static final Component ASK_TITLE = Component.translatable(PREFIX + "ask_remove_title");
        public static final Component ASK_MSG = Component.translatable(PREFIX + "ask_remove_message");
        public static final Component MARKET_SETTINGS = Component.translatable(PREFIX + "market_settings");
        public static final Component MARKET_OPEN = Component.translatable(PREFIX + "market_open");
        public static final Component TOOLTIP_NEW_CUSTOM_MARKET = Component.translatable(PREFIX + "new_custom_market.tooltip");
        public static final Component TOOLTIP_NEW_MARKET_BY_CATEGORY = Component.translatable(PREFIX + "new_market_by_category.tooltip");
        public static final Component TOOLTIP_REMOVE_SELECTED_TRADING_PAIR = Component.translatable(PREFIX + "remove_selected_trading_pair.tooltip");
        //public static final Component TOOLTIP_SELECTED_TRADING_PAIR = Component.translatable(PREFIX + "selected_trading_pair.tooltip");


        public static final Component GENERAL_TITLE = Component.translatable(PREFIX + "general.title");
        public static final Component GENERAL_CLOSE_ALL_MARKETS = Component.translatable(PREFIX + "general.close_all_markets");
        public static final Component GENERAL_CLOSE_ALL_MARKETS_TOOLTIP = Component.translatable(PREFIX + "general.close_all_markets.tooltip");
        public static final Component GENERAL_OPEN_ALL_MARKETS = Component.translatable(PREFIX + "general.open_all_markets");
        public static final Component GENERAL_OPEN_ALL_MARKETS_TOOLTIP = Component.translatable(PREFIX + "general.open_all_markets.tooltip");
        public static final Component GENERAL_RESET_ALL_MARKETS_PRICE_CHART = Component.translatable(PREFIX + "general.reset_all_markets_price_chart");
        public static final Component GENERAL_RESET_ALL_MARKETS_PRICE_CHART_TOOLTIP = Component.translatable(PREFIX + "general.reset_all_markets_price_chart.tooltip");
        public static final Component GENERAL_RESET_ALL_MARKETS_PRICE_CHART_POPUP_ASK = Component.translatable(PREFIX + "general.reset_all_markets_price_chart_popup_ask");
        public static final Component GENERAL_RESET_ALL_MARKETS_PRICE_CHART_POPUP_MSG = Component.translatable(PREFIX + "general.reset_all_markets_price_chart_popup_msg");
        public static final Component GENERAL_DELETE_ALL_MARKETS = Component.translatable(PREFIX + "general.delete_all_markets");
        public static final Component GENERAL_DELETE_ALL_MARKETS_TOOLTIP = Component.translatable(PREFIX + "general.delete_all_markets.tooltip");
        public static final Component GENERAL_DELETE_ALL_MARKETS_POPUP_ASK = Component.translatable(PREFIX + "general.delete_all_markets_popup_ask");
        public static final Component GENERAL_DELETE_ALL_MARKETS_POPUP_MSG = Component.translatable(PREFIX + "general.delete_all_markets_popup_msg");
    }

    public final class GeneralManagerWidget extends StockMarketGuiElement
    {
        private final Label titleLabel;
        private final Button closeAllMarketsButton;
        private final Button openAllMarketsButton;
        private final Button resetAllMarketsPriceChartButton;
        private final Button deleteAllMarketsButton;

        private final ManagementScreen parentScreen;

        public GeneralManagerWidget(ManagementScreen parent)
        {
            super();
            this.parentScreen = parent;
            this.setEnableBackground(false);

            titleLabel = new Label(TEXT.GENERAL_TITLE.getString());
            titleLabel.setAlignment(Label.Alignment.CENTER);
            closeAllMarketsButton = new Button(TEXT.GENERAL_CLOSE_ALL_MARKETS.getString(), this::onCloseAllMarketsButtonClicked);
            closeAllMarketsButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.BOTTOM_RIGHT);
            closeAllMarketsButton.setHoverTooltipSupplier(TEXT.GENERAL_CLOSE_ALL_MARKETS_TOOLTIP::getString);
            //closeAllMarketsButton.setIdleColor(0xFFe8711c);
            //closeAllMarketsButton.setHoverColor(0xFFe04c12);
            //closeAllMarketsButton.setPressedColor(0xFFe04c12);
            openAllMarketsButton = new Button(TEXT.GENERAL_OPEN_ALL_MARKETS.getString(), this::onOpenAllMarketsButtonClicked);
            openAllMarketsButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.BOTTOM_RIGHT);
            openAllMarketsButton.setHoverTooltipSupplier(TEXT.GENERAL_OPEN_ALL_MARKETS_TOOLTIP::getString);
            //openAllMarketsButton.setIdleColor(0xFF70e815);
            //openAllMarketsButton.setHoverColor(0xFF4cd700);
            //openAllMarketsButton.setPressedColor(0xFF4cd700);
            resetAllMarketsPriceChartButton = new Button(TEXT.GENERAL_RESET_ALL_MARKETS_PRICE_CHART.getString(), this::onResetAllMarketsPriceChartButtonClicked);
            resetAllMarketsPriceChartButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.BOTTOM_RIGHT);
            resetAllMarketsPriceChartButton.setHoverTooltipSupplier(TEXT.GENERAL_RESET_ALL_MARKETS_PRICE_CHART_TOOLTIP::getString);
            resetAllMarketsPriceChartButton.setIdleColor(COLOR.ORANGE);
            resetAllMarketsPriceChartButton.setHoverColor(COLOR.ORANGE_HOVER);
            resetAllMarketsPriceChartButton.setPressedColor(COLOR.ORANGE_CLICKED);
            deleteAllMarketsButton = new Button(TEXT.GENERAL_DELETE_ALL_MARKETS.getString(), this::onDeleteAllMarketsButtonClicked);
            deleteAllMarketsButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.BOTTOM_RIGHT);
            deleteAllMarketsButton.setHoverTooltipSupplier(TEXT.GENERAL_DELETE_ALL_MARKETS_TOOLTIP::getString);
            deleteAllMarketsButton.setIdleColor(COLOR.RED);
            deleteAllMarketsButton.setHoverColor(COLOR.RED_HOVER);
            deleteAllMarketsButton.setPressedColor(COLOR.RED_CLICKED);

            this.addChild(titleLabel);
            this.addChild(closeAllMarketsButton);
            this.addChild(openAllMarketsButton);
            this.addChild(resetAllMarketsPriceChartButton);
            this.addChild(deleteAllMarketsButton);

            this.setHeight(5*(elementHeight+spacing));

        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth()-2*padding;

            titleLabel.setBounds(padding, padding, width, elementHeight-spacing);
            closeAllMarketsButton.setBounds(padding, titleLabel.getBottom()+spacing, width, elementHeight);
            openAllMarketsButton.setBounds(closeAllMarketsButton.getLeft(), closeAllMarketsButton.getBottom()+spacing, width, elementHeight);
            resetAllMarketsPriceChartButton.setBounds(openAllMarketsButton.getLeft(), openAllMarketsButton.getBottom()+spacing, width, elementHeight);
            deleteAllMarketsButton.setBounds(resetAllMarketsPriceChartButton.getLeft(), resetAllMarketsPriceChartButton.getBottom()+spacing, width, elementHeight);
        }

        private void onCloseAllMarketsButtonClicked()
        {
            getMarketManager().requestTradingPairs((tradingPairs)->
            {
                getMarketManager().requestSetMarketOpen(tradingPairs, false, (success)->{
                    for(int i=0; i<success.size(); i++)
                    {
                        if(!success.get(i)) {
                            PlayerUtilities.printToClientConsole("Failed to close market " + tradingPairs.get(i).getShortDescription() + ".");
                        }
                    }
                    parentScreen.updateLoadCurrentSettings();
                });
            });
        }
        private void onOpenAllMarketsButtonClicked()
        {
            getMarketManager().requestTradingPairs((tradingPairs)->
            {
                getMarketManager().requestSetMarketOpen(tradingPairs, true, (success)->{
                    for(int i=0; i<success.size(); i++)
                    {
                        if(!success.get(i)) {
                            PlayerUtilities.printToClientConsole("Failed to open market " + tradingPairs.get(i).getShortDescription() + ".");
                        }
                    }
                    parentScreen.updateLoadCurrentSettings();
                });
            });
        }
        private void onResetAllMarketsPriceChartButtonClicked()
        {
            getMarketManager().requestTradingPairs((tradingPairs)->
            {
                AskPopupScreen popup = new AskPopupScreen(parentScreen, () ->
                {
                    getMarketManager().requestChartReset(tradingPairs, (success)->{
                        int resetted = 0;
                        int notResetted = 0;
                        for(int i=0; i<success.size(); i++)
                        {
                            if(success.get(i)) {
                                resetted++;
                            }
                            else
                            {
                                notResetted++;
                            }
                        }
                        String msg = "Resetted " + resetted + " markets.";
                        if(notResetted > 0) {
                            msg += " Failed to reset " + notResetted + " markets.";
                        }
                        PlayerUtilities.printToClientConsole(msg);
                    });
                }, () -> {}, TEXT.GENERAL_RESET_ALL_MARKETS_PRICE_CHART_POPUP_ASK.getString(), TEXT.GENERAL_RESET_ALL_MARKETS_PRICE_CHART_POPUP_MSG.getString());
                popup.setSize(400,100);
                popup.setColors(COLOR.BACKGROUND, COLOR.BACKGROUND, COLOR.ORANGE, COLOR.GREEN);
                minecraft.setScreen(popup);
            });
        }
        private void onDeleteAllMarketsButtonClicked()
        {
            getMarketManager().requestTradingPairs((tradingPairs)->
            {
                AskPopupScreen popup = new AskPopupScreen(parentScreen, () ->
                {
                    getMarketManager().requestRemoveMarket(tradingPairs, (success)->{
                        int removedCount = 0;
                        int notRemovedCount = 0;
                        for(int i=0; i<success.size(); i++)
                        {
                            if(success.get(i)) {
                                removedCount++;
                            }
                            else
                            {
                                notRemovedCount++;
                            }
                        }
                        String msg = "Removed " + removedCount + " markets.";
                        if(notRemovedCount > 0) {
                            msg += " Failed to remove " + notRemovedCount + " markets.";
                        }
                        PlayerUtilities.printToClientConsole(msg);
                        setCurrentTradingPair(null);
                        updateTradingItems();
                    });
                }, () -> {}, TEXT.GENERAL_DELETE_ALL_MARKETS_POPUP_ASK.getString(), TEXT.GENERAL_DELETE_ALL_MARKETS_POPUP_MSG.getString());
                popup.setSize(400,100);
                popup.setColors(COLOR.ORANGE, COLOR.ORANGE_HOVER, COLOR.RED, COLOR.GREEN);
                minecraft.setScreen(popup);
            });




        }
    }
    public final class CurrentPairManagerWidget extends StockMarketGuiElement{

        private final TradingPairView currentTradingItemView;
        private final Label selectedMarketLabel;
        private final Button removeTradingPairButton;
        private final Button marketSettingsButton;
        private final CheckBox marketOpenCheckBox;
        private final ManagementScreen parentScreen;

        public CurrentPairManagerWidget(ManagementScreen parent)
        {
            super();
            this.setEnableBackground(false);
            parentScreen = parent;
            selectedMarketLabel = new Label(TEXT.SELECTED_MARKET_TITLE.getString());
            selectedMarketLabel.setAlignment(Label.Alignment.CENTER);


            removeTradingPairButton = new Button(TEXT.REMOVE_MARKET_BUTTON.getString(), this::onRemoveTradingPairButtonClicked);
            removeTradingPairButton.setIdleColor(0xFFe8711c);
            removeTradingPairButton.setHoverColor(0xFFe04c12);
            removeTradingPairButton.setPressedColor(0xFFe04c12);
            removeTradingPairButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
            removeTradingPairButton.setHoverTooltipSupplier(TEXT.TOOLTIP_REMOVE_SELECTED_TRADING_PAIR::getString);
            removeTradingPairButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

            currentTradingItemView = new TradingPairView();
            currentTradingItemView.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
            currentTradingItemView.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
            //currentTradingItemView.setHoverTooltipSupplier(()-> TEXT.TOOLTIP_SELECTED_TRADING_PAIR.getString() + (tradingPair != null ? tradingPair.getShortDescription() : "None"));
            currentTradingItemView.setClickable(false);

            marketSettingsButton = new Button(TEXT.MARKET_SETTINGS.getString(), this::onMarketSettingsButtonClicked);

            marketOpenCheckBox = new CheckBox(TEXT.MARKET_OPEN.getString(),this::onMarketOpenCheckBoxChanged);
            marketOpenCheckBox.setTextAlignment(GuiElement.Alignment.CENTER);


            this.addChild(selectedMarketLabel);
            this.addChild(removeTradingPairButton);
            this.addChild(currentTradingItemView);
            this.addChild(marketSettingsButton);
            this.addChild(marketOpenCheckBox);

            this.setHeight(4*(elementHeight+spacing));
        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth()-2*padding;
            //int height = getHeight()-2*padding;

            selectedMarketLabel.setBounds(padding, padding, width, elementHeight-spacing);

            currentTradingItemView.setBounds(padding, selectedMarketLabel.getBottom()+spacing, width/2, elementHeight);
            removeTradingPairButton.setBounds(currentTradingItemView.getRight()+spacing, currentTradingItemView.getTop(), width/2-spacing, elementHeight);


            marketOpenCheckBox.setBounds(currentTradingItemView.getLeft(), currentTradingItemView.getBottom()+spacing, width, elementHeight);
            marketSettingsButton.setBounds(marketOpenCheckBox.getLeft(), marketOpenCheckBox.getBottom()+spacing, width, elementHeight);


        }
        public void setCurrentTradingPair(TradingPair tradingPair) {
            currentTradingItemView.setTradingPair(tradingPair);
            selectMarket(tradingPair);
        }
        public void setMarketOpenCheckBoxChecked(boolean isOpen) {
            marketOpenCheckBox.setChecked(isOpen);
        }

        private void onRemoveTradingPairButtonClicked()
        {
            if(tradingPair != null) {
                AskPopupScreen popup = new AskPopupScreen(parentScreen, () ->
                {
                    getMarketManager().requestRemoveMarket(tradingPair, (success)->{
                        parentScreen.setCurrentTradingPair(null);
                        updateTradingItems();
                    });
                    //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestRemoveTradingItem(tradingPair);

                }, () -> {}, TEXT.ASK_TITLE.getString() + " "+tradingPair.getShortDescription() + "?", TEXT.ASK_MSG.getString());
                popup.setSize(400,100);
                popup.setColors(COLOR.ORANGE, COLOR.ORANGE_HOVER, COLOR.RED, COLOR.GREEN);
                minecraft.setScreen(popup);
            }
        }
        private void onMarketSettingsButtonClicked()
        {
            if(tradingPair != null) {
                marketSettingsScreen = new MarketSettingsScreen(parentScreen, (settings)->
                {
                    if(settings != null) {
                        currentMarketSettingsData = settings;
                        getSelectedMarket().requestSetMarketSettings(settings, (success) -> {
                            if (success) {
                                getSelectedMarket().requestGetMarketSettings(
                                        (settingsData -> {
                                            if (settingsData != null) {
                                                setCurrentTradingPairMarketSettings(settingsData);
                                                marketSettingsScreen.setSettings(settingsData);
                                            }
                                        }));
                            }
                        });
                    }
                });
                if (currentMarketSettingsData != null) {
                    if(getSelectedMarket() != null)
                        getSelectedMarket().requestGetMarketSettings(
                                (settingsData -> {
                                    if (settingsData != null) {
                                        marketSettingsScreen.setSettings(settingsData);
                                        minecraft.setScreen(marketSettingsScreen);
                                    }
                                }));
                }
                else
                {
                    marketSettingsScreen.setSettings(currentMarketSettingsData);
                    minecraft.setScreen(marketSettingsScreen);
                }
            }
        }
        private void onMarketOpenCheckBoxChanged(Boolean isOpen)
        {
            if(tradingPair == null || currentMarketSettingsData == null)
                return;
            if(currentMarketSettingsData.marketOpen != isOpen) {
                currentMarketSettingsData.marketOpen = isOpen;
                getSelectedMarket().requestMarketOpen(isOpen, (success) -> {
                    if (success) {
                        debug("Market open status updated for trading pair: " + tradingPair.getShortDescription() + " to " + isOpen);
                    } else {
                        warn("Failed to update market open status for trading pair: " + tradingPair.getShortDescription());
                    }
                });
            }
        }
        public TradingPair getCurrentTradingPair() {
            return currentTradingItemView.getTradingPair();
        }
    }
    public final class NewMarketWidget extends StockMarketGuiElement
    {

        private final Label titleLabel;
        private final Button newCustomMarketButton;
        private final Button newMarketByCategoryButton;

        private final ManagementScreen parentScreen;
        public NewMarketWidget(ManagementScreen parent) {
            super();
            this.setEnableBackground(false);
            this.parentScreen = parent;

            titleLabel = new Label(TEXT.NEW_MARKET_TITLE.getString());
            titleLabel.setAlignment(Label.Alignment.CENTER);

            newCustomMarketButton = new Button(TEXT.NEW_CUSTOM_MARKET_BUTTON.getString(), this::onNewCustomMarketButtonClicked);
            newCustomMarketButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.BOTTOM_RIGHT);
            newCustomMarketButton.setHoverTooltipSupplier(TEXT.TOOLTIP_NEW_CUSTOM_MARKET::getString);


            newMarketByCategoryButton = new Button(TEXT.NEW_MARKET_BY_CATEGORY.getString(), this::onNewMarketByCategoryButtonClicked);
            newMarketByCategoryButton.setHoverTooltipMousePositionAlignment(Alignment.BOTTOM_RIGHT);
            newMarketByCategoryButton.setHoverTooltipSupplier(TEXT.TOOLTIP_NEW_MARKET_BY_CATEGORY::getString);

            this.addChild(titleLabel);
            this.addChild(newCustomMarketButton);
            this.addChild(newMarketByCategoryButton);

            this.setHeight(3*(elementHeight+spacing));
        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth()-2*padding;

            titleLabel.setBounds(padding, padding, width, elementHeight-spacing);
            newCustomMarketButton.setBounds(padding, titleLabel.getBottom()+spacing, width, elementHeight);
            newMarketByCategoryButton.setBounds(newCustomMarketButton.getLeft(), newCustomMarketButton.getBottom()+spacing, width, elementHeight);
        }

        private void onNewCustomMarketButtonClicked() {
            tradingPairCreationScreen = new MarketCreationScreen(parentScreen);
            minecraft.setScreen(tradingPairCreationScreen);
        }
        private void onNewMarketByCategoryButtonClicked()
        {
            marketCreationByCategoryScreen = new MarketCreationByCategoryScreen(parentScreen);
            minecraft.setScreen(marketCreationByCategoryScreen);
        }
    }

    private TradingPair tradingPair;
    private ServerMarketSettingsData currentMarketSettingsData;


    private final ListView listView;
    private final GeneralManagerWidget generalManagerWidget;
    private final CurrentPairManagerWidget currentPairManagerWidget;
    private final NewMarketWidget newMarketWidget;


    private final TradingPairSelectionView tradableItemsView;



    private final Screen parentScreen;


    private MarketSettingsScreen marketSettingsScreen;
    private MarketCreationScreen tradingPairCreationScreen;
    private MarketCreationByCategoryScreen marketCreationByCategoryScreen;
    private final TradingChartWidget tradingChart;
    private static ManagementScreen instance;
    private final TimerMillis updateTimer;


    protected ManagementScreen(Screen parent) {
        super(TEXT.TITLE);
        instance = this;
        updateTimer = new TimerMillis(true);
        updateTimer.start(500);
        this.parentScreen = parent;
        tradableItemsView = new TradingPairSelectionView(this::setCurrentTradingPair);
        updateTradingItems();

        tradingChart = new TradingChartWidget();
        tradingChart.enableBotTargetPriceDisplay(true);

        listView = new VerticalListView();
        Layout layout = new LayoutVertical();
        layout.stretchX = true;
        listView.setLayout(layout);
        generalManagerWidget = new GeneralManagerWidget(this);
        currentPairManagerWidget = new CurrentPairManagerWidget(this);
        newMarketWidget = new NewMarketWidget(this);

        listView.addChild(generalManagerWidget);
        listView.addChild(currentPairManagerWidget);
        listView.addChild(newMarketWidget);



        addElement(tradableItemsView);
        addElement(listView);
        addElement(tradingChart);


        setCurrentTradingPair(null);
        TickEvent.PLAYER_POST.register(ManagementScreen::onClientTick);
    }

    @Override
    protected void updateLayout(Gui gui) {
        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;

        int leftSideWidth = (width*2)/3;
        int chartHeight = (height/3);
        tradingChart.setBounds(padding, padding, leftSideWidth, chartHeight);
        tradableItemsView.setBounds(tradingChart.getLeft(), tradingChart.getBottom()+spacing, tradingChart.getWidth(), height - tradingChart.getBottom());
        listView.setBounds(tradableItemsView.getRight()+spacing, padding, width - tradableItemsView.getRight(), height);

        /*generalManagerWidget.setPosition(tradableItemsView.getRight()+spacing, padding);
        generalManagerWidget.setWidth(width - tradableItemsView.getRight());

        currentPairManagerWidget.setPosition(generalManagerWidget.getLeft(), generalManagerWidget.getBottom()+spacing);
        currentPairManagerWidget.setWidth(generalManagerWidget.getWidth());

        newMarketWidget.setPosition(generalManagerWidget.getLeft(), height - newMarketWidget.getHeight() + spacing);
        newMarketWidget.setWidth(generalManagerWidget.getWidth());*/
    }


    public static void openScreen()
    {
        openScreen(null);
    }
    public static void openScreen(Screen parent)
    {
        ManagementScreen screen = new ManagementScreen(parent);
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    public void onClose() {
        TickEvent.PLAYER_POST.unregister(ManagementScreen::onClientTick);
        instance = null;
        super.onClose();
        if(parentScreen != null)
            Minecraft.getInstance().setScreen(parentScreen);
    }


    public void updateTradingItems()
    {
        getMarketManager().requestTradingPairs((tradingPairs -> {
            tradableItemsView.setAvailableTradingPairs(tradingPairs);
            if(tradingPair != null) {
                for (TradingPair pair : tradingPairs) {
                    if (pair.equals(tradingPair)) {
                        setCurrentTradingPair(pair);
                        return; // Stop searching once we find the current trading pair
                    }
                }
            }
            setCurrentTradingPair(null); // If current trading pair is not found, set it to null
        }));
    }


    public void setCurrentTradingPair(TradingPair tradingPair) {
        if(tradingPair == null) {
            this.tradingPair = null;
            selectMarket(tradingPair);
            currentMarketSettingsData = null;
            currentPairManagerWidget.setCurrentTradingPair(null);
            currentPairManagerWidget.setEnabled(false);
            setCurrentTradingPairMarketSettings(null);
            tradingChart.updateView(null);
        }
        else
        {
            this.tradingPair = tradingPair;
            selectMarket(tradingPair);
            currentPairManagerWidget.setCurrentTradingPair(tradingPair);
            currentPairManagerWidget.setEnabled(true);
            updateLoadCurrentSettings();
        }
    }

    public void updateLoadCurrentSettings()
    {
        if(getSelectedMarket() != null)
        {
            getSelectedMarket().requestGetMarketSettings(this::setCurrentTradingPairMarketSettings);
        }
    }

    private void setCurrentTradingPairMarketSettings(ServerMarketSettingsData settingsData) {
        currentMarketSettingsData = settingsData;
        if(settingsData != null)
            currentPairManagerWidget.setMarketOpenCheckBoxChecked(settingsData.marketOpen);
    }

    private static void onClientTick(Player player) {
        if (Minecraft.getInstance().screen != instance || instance == null)
            return;


        if(instance.updateTimer.check() && instance.getSelectedMarket() != null)
        {
            instance.getSelectedMarket().requestTradingViewData(instance.tradingChart.getMaxCandleCount(),
                    0,0,500, true ,instance.tradingChart::updateView);
        }
    }
}
