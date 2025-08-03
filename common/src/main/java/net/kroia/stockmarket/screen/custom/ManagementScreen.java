package net.kroia.stockmarket.screen.custom;


import net.kroia.banksystem.screen.uiElements.AskPopupScreen;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.ServerMarketSettingsData;
import net.kroia.stockmarket.screen.uiElements.TradingPairSelectionView;
import net.kroia.stockmarket.screen.uiElements.TradingPairView;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ManagementScreen extends StockMarketGuiScreen {

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
        public static final Component TOOLTIP_NEW_CUSTOM_MARKET = Component.translatable(PREFIX + "tooltip_new_custom_market");
        public static final Component TOOLTIP_NEW_MARKET_BY_CATEGORY = Component.translatable(PREFIX + "tooltip_new_market_by_category");
        public static final Component TOOLTIP_REMOVE_SELECTED_TRADING_PAIR = Component.translatable(PREFIX + "tooltip_remove_selected_trading_pair");
        public static final Component TOOLTIP_SELECTED_TRADING_PAIR = Component.translatable(PREFIX + "tooltip_selected_trading_pair");
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
            parentScreen = parent;
            selectedMarketLabel = new Label(TEXT.SELECTED_MARKET_TITLE.getString());
            selectedMarketLabel.setAlignment(Label.Alignment.CENTER);


            removeTradingPairButton = new Button(TEXT.REMOVE_MARKET_BUTTON.getString(), this::onRemoveTradingPairButtonClicked);
            removeTradingPairButton.setIdleColor(0xFFe8711c);
            removeTradingPairButton.setHoverColor(0xFFe04c12);
            removeTradingPairButton.setPressedColor(0xFFe04c12);
            removeTradingPairButton.setTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
            removeTradingPairButton.setHoverTooltipSupplier(TEXT.TOOLTIP_REMOVE_SELECTED_TRADING_PAIR::getString);

            currentTradingItemView = new TradingPairView();
            currentTradingItemView.setTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
            currentTradingItemView.setHoverTooltipSupplier(()-> TEXT.TOOLTIP_SELECTED_TRADING_PAIR.getString() + (tradingPair != null ? tradingPair.getShortDescription() : "None"));

            marketSettingsButton = new Button(TEXT.MARKET_SETTINGS.getString(), this::onMarketSettingsButtonClicked);

            marketOpenCheckBox = new CheckBox(TEXT.MARKET_OPEN.getString(),this::onMarketOpenCheckBoxChanged);
            marketOpenCheckBox.setTextAlignment(GuiElement.Alignment.CENTER);


            this.addChild(selectedMarketLabel);
            this.addChild(removeTradingPairButton);
            this.addChild(currentTradingItemView);
            this.addChild(marketSettingsButton);
            this.addChild(marketOpenCheckBox);

            this.setHeight(4*25);
        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int padding = 5;
            int spacing = 5;
            int width = getWidth()-2*padding;
            //int height = getHeight()-2*padding;

            selectedMarketLabel.setBounds(padding, padding, width, 15);

            currentTradingItemView.setBounds(padding, selectedMarketLabel.getBottom()+spacing, width/2, 20);
            removeTradingPairButton.setBounds(currentTradingItemView.getRight()+spacing, currentTradingItemView.getTop(), width/2-spacing, currentTradingItemView.getHeight());


            marketOpenCheckBox.setBounds(currentTradingItemView.getLeft(), currentTradingItemView.getBottom()+spacing, width, currentTradingItemView.getHeight());
            marketSettingsButton.setBounds(marketOpenCheckBox.getLeft(), marketOpenCheckBox.getBottom()+spacing, width, currentTradingItemView.getHeight());


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
                    BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestRemoveMarket(tradingPair, (success)->{
                        setCurrentTradingPair(null);
                        updateTradingItems();
                    });
                    //BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestRemoveTradingItem(tradingPair);

                }, () -> {}, TEXT.ASK_TITLE.getString() + " "+tradingPair.getShortDescription() + "?", TEXT.ASK_MSG.getString());
                popup.setSize(400,100);
                popup.setColors(0xFFe8711c, 0xFFe04c12, 0xFFf22718, 0xFF70e815);
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
                getSelectedMarket().requestSetMarketSettings(currentMarketSettingsData, (success) -> {
                    if (success) {
                        BACKEND_INSTANCES.LOGGER.debug("Market open status updated for trading pair: " + tradingPair.getShortDescription() + " to " + isOpen);
                    } else {
                        BACKEND_INSTANCES.LOGGER.warn("Failed to update market open status for trading pair: " + tradingPair.getShortDescription());
                    }
                });
            }
        }
    }

    public final class NewMarketWidget extends StockMarketGuiElement
    {

        private final Label titelLabel;
        private final Button newCustomMarketButton;
        private final Button newMarketByCategoryButton;

        private final ManagementScreen parentScreen;
        public NewMarketWidget(ManagementScreen parent) {
            super();
            this.parentScreen = parent;

            titelLabel = new Label(TEXT.NEW_MARKET_TITLE.getString());
            titelLabel.setAlignment(Label.Alignment.CENTER);

            newCustomMarketButton = new Button(TEXT.NEW_CUSTOM_MARKET_BUTTON.getString(), this::onNewCustomMarketButtonClicked);
            newCustomMarketButton.setTooltipMousePositionAlignment(GuiElement.Alignment.BOTTOM_RIGHT);
            newCustomMarketButton.setHoverTooltipSupplier(TEXT.TOOLTIP_NEW_CUSTOM_MARKET::getString);


            newMarketByCategoryButton = new Button(TEXT.NEW_MARKET_BY_CATEGORY.getString(), this::onNewMarketByCategoryButtonClicked);
            newMarketByCategoryButton.setTooltipMousePositionAlignment(Alignment.BOTTOM_RIGHT);
            newMarketByCategoryButton.setHoverTooltipSupplier(TEXT.TOOLTIP_NEW_MARKET_BY_CATEGORY::getString);

            this.addChild(titelLabel);
            this.addChild(newCustomMarketButton);
            this.addChild(newMarketByCategoryButton);

            this.setHeight(3*25);
        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int padding = 5;
            int spacing = 5;
            int width = getWidth()-2*padding;

            titelLabel.setBounds(padding, padding, width, 15);
            newCustomMarketButton.setBounds(padding, titelLabel.getBottom()+spacing, width, 20);
            newMarketByCategoryButton.setBounds(newCustomMarketButton.getLeft(), newCustomMarketButton.getBottom()+spacing, width, newCustomMarketButton.getHeight());
        }

        private void onNewCustomMarketButtonClicked() {
            tradingPairCreationScreen = new MarketCreationScreen(parentScreen);
            minecraft.setScreen(tradingPairCreationScreen);
        }
        private void onNewMarketByCategoryButtonClicked()
        {

        }
    }

    private TradingPair tradingPair;
    private ServerMarketSettingsData currentMarketSettingsData;


    private final CurrentPairManagerWidget currentPairManagerWidget;
    private final NewMarketWidget newMarketWidget;


    private final TradingPairSelectionView tradableItemsView;



    private final Screen parentScreen;
    private MarketSettingsScreen marketSettingsScreen;
    private MarketCreationScreen tradingPairCreationScreen;


    protected ManagementScreen(Screen parent) {
        super(TEXT.TITLE);
        this.parentScreen = parent;
        tradableItemsView = new TradingPairSelectionView(this::setCurrentTradingPair);
        updateTradingItems();


        currentPairManagerWidget = new CurrentPairManagerWidget(this);
        newMarketWidget = new NewMarketWidget(this);



        addElement(tradableItemsView);
        addElement(currentPairManagerWidget);
        addElement(newMarketWidget);

        setCurrentTradingPair(null);
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = 5;
        int spacing = 5;
        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;



        tradableItemsView.setBounds(padding, padding, (width*2)/3, height);

        currentPairManagerWidget.setPosition(tradableItemsView.getRight()+spacing, padding);
        currentPairManagerWidget.setWidth(width - tradableItemsView.getRight());
        newMarketWidget.setPosition(tradableItemsView.getRight()+spacing, height - newMarketWidget.getHeight() + spacing);
        newMarketWidget.setWidth(currentPairManagerWidget.getWidth());
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
        super.onClose();
        if(parentScreen != null)
            Minecraft.getInstance().setScreen(parentScreen);
    }


    public void updateTradingItems()
    {
        BACKEND_INSTANCES.CLIENT_STOCKMARKET_MANAGER.requestTradingPairs((tradableItemsView::setAvailableTradingPairs));
    }


    public void setCurrentTradingPair(TradingPair tradingPair) {
        if(tradingPair == null) {
            this.tradingPair = null;
            selectMarket(tradingPair);
            currentMarketSettingsData = null;
            currentPairManagerWidget.setCurrentTradingPair(null);
            currentPairManagerWidget.setEnabled(false);
            setCurrentTradingPairMarketSettings(null);
        }
        else
        {
            this.tradingPair = tradingPair;
            selectMarket(tradingPair);
            currentPairManagerWidget.setCurrentTradingPair(tradingPair);
            currentPairManagerWidget.setEnabled(true);
            if(getSelectedMarket() != null)
            {
                getSelectedMarket().requestGetMarketSettings(this::setCurrentTradingPairMarketSettings);
            }
        }
    }

    private void setCurrentTradingPairMarketSettings(ServerMarketSettingsData settingsData) {
        currentMarketSettingsData = settingsData;
        if(settingsData != null)
            currentPairManagerWidget.setMarketOpenCheckBoxChecked(settingsData.marketOpen);
    }
}
