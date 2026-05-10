package net.kroia.stockmarket.screen;


import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ManagementScreen extends StockMarketGuiScreen {

    private static class Texts{
        private static final String PREFIX = "gui."+ StockMarketMod.MOD_ID + ".management_screen.";
        private static final Component TITLE = Component.translatable(PREFIX +"title");


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

        public static final Component PLUGINS_TITLE = Component.translatable(PREFIX + "plugins.title");
        public static final Component PLUGINS_ENABLED = Component.translatable(PREFIX + "plugins.enabled");
        public static final Component PLUGINS_MANAGE = Component.translatable(PREFIX + "plugins.manage");

    }
    public static final int elementHeight = 20;


    public final class CurrentMarketManagerWidget extends StockMarketGuiElement{

        private final ItemView currentItemView;
        private final Label selectedMarketLabel;
        private final Button removeTradingPairButton;
        private final Button marketSettingsButton;
        private final CheckBox marketOpenCheckBox;
        private final ManagementScreen parentScreen;

        public CurrentMarketManagerWidget(ManagementScreen parent)
        {
            super();
            this.setEnableBackground(false);
            parentScreen = parent;
            selectedMarketLabel = new Label(Texts.SELECTED_MARKET_TITLE.getString());
            selectedMarketLabel.setAlignment(Label.Alignment.CENTER);


            removeTradingPairButton = new Button(Texts.REMOVE_MARKET_BUTTON.getString(), this::onRemoveTradingPairButtonClicked);
            removeTradingPairButton.setBackgroundColor(0xFFe8711c);
            removeTradingPairButton.setHoverColor(0xFFe04c12);
            removeTradingPairButton.setPressedColor(0xFFe04c12);
            removeTradingPairButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
            removeTradingPairButton.setHoverTooltipSupplier(Texts.TOOLTIP_REMOVE_SELECTED_TRADING_PAIR::getString);
            removeTradingPairButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

            currentItemView = new ItemView();
            currentItemView.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
            currentItemView.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
            //currentTradingItemView.setHoverTooltipSupplier(()-> TEXT.TOOLTIP_SELECTED_TRADING_PAIR.getString() + (tradingPair != null ? tradingPair.getShortDescription() : "None"));
            //currentItemView.setClickable(false);

            marketSettingsButton = new Button(Texts.MARKET_SETTINGS.getString(), this::onMarketSettingsButtonClicked);

            marketOpenCheckBox = new CheckBox(Texts.MARKET_OPEN.getString(),this::onMarketOpenCheckBoxChanged);
            marketOpenCheckBox.setTextAlignment(GuiElement.Alignment.CENTER);


            this.addChild(selectedMarketLabel);
            this.addChild(removeTradingPairButton);
            this.addChild(currentItemView);
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

            currentItemView.setBounds(padding, selectedMarketLabel.getBottom()+spacing, width/2, elementHeight);
            removeTradingPairButton.setBounds(currentItemView.getRight()+spacing, currentItemView.getTop(), width/2-spacing, elementHeight);


            marketOpenCheckBox.setBounds(currentItemView.getLeft(), currentItemView.getBottom()+spacing, width, elementHeight);
            marketSettingsButton.setBounds(marketOpenCheckBox.getLeft(), marketOpenCheckBox.getBottom()+spacing, width, elementHeight);


        }
        public void setCurrentMarket(ItemID marketID) {
            if(marketID == null)
                currentItemView.setItemStack(ItemStack.EMPTY);
            else
                currentItemView.setItemStack(marketID.getStack());
            //selectMarket(marketID);
        }
        public void setMarketOpenCheckBoxChecked(boolean isOpen) {
            marketOpenCheckBox.setChecked(isOpen);
        }

        private void onRemoveTradingPairButtonClicked()
        {
            ClientMarket currentMarket = getSelectedMarket();
            /*if(currentMarket != null) {
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
            }*/
        }
        private void onMarketSettingsButtonClicked()
        {
            ClientMarket currentMarket = getSelectedMarket();
            if(currentMarket == null)
                return;

            MarketManagementScreen managementScreen = new MarketManagementScreen(parentScreen, currentMarket.getItemID());
            setScreen(managementScreen);

            /*marketSettingsScreen = new MarketSettingsScreen(parentScreen, (settings)->
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
            }*/

        }
        private void onMarketOpenCheckBoxChanged(Boolean isOpen)
        {
            ClientMarket currentMarket = getSelectedMarket();
            /*if(currentMarket == null || currentMarketSettingsData == null)
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
            }*/
        }
    }

    /**
     * Widget that provides a button to open the {@link CreateMarketScreen}
     * where the player can pick an item and create a new market for it.
     *
     * <pre>
     * +---------------------------+
     * |     Create new Market     |  (title label, centered)
     * |          [ + ]            |  (button, opens item selection)
     * +---------------------------+
     * </pre>
     */
    public final class CreateMarketWidget extends StockMarketGuiElement {
        private final Label titleLabel;
        private final Button createButton;
        private final ManagementScreen parentScreen;

        public CreateMarketWidget(ManagementScreen parent) {
            super();
            setEnableBackground(false);
            parentScreen = parent;

            titleLabel = new Label(Texts.NEW_MARKET_TITLE.getString());
            titleLabel.setAlignment(Label.Alignment.CENTER);

            createButton = new Button(Texts.NEW_CUSTOM_MARKET_BUTTON.getString(), this::onCreateClicked);
            createButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
            createButton.setHoverTooltipSupplier(Texts.TOOLTIP_NEW_CUSTOM_MARKET::getString);
            createButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

            addChild(titleLabel);
            addChild(createButton);

            setHeight(2 * (defaultElementHeight + spacing));
        }

        @Override
        protected void render() {
            // No dynamic rendering needed
        }

        @Override
        protected void layoutChanged() {
            int width = getWidth() - 2 * padding;
            titleLabel.setBounds(padding, padding, width, defaultElementHeight - spacing);
            createButton.setBounds(padding, titleLabel.getBottom() + spacing, width, defaultElementHeight);
        }

        private void onCreateClicked() {
            CreateMarketScreen screen = new CreateMarketScreen(parentScreen);
            setScreen(screen);
        }
    }

    /**
     * Widget that shows a summary of the plugin system and provides
     * a button to open the plugin management screen.
     *
     * <pre>
     * ┌─────────────────────────┐
     * │       Plugins           │  ← title label (centered)
     * │   2 / 3 enabled         │  ← status label (live-updated)
     * │  [ Manage Plugins ]     │  ← button → opens PluginManagementScreen
     * └─────────────────────────┘
     * </pre>
     */
    public final class PluginOverviewWidget extends StockMarketGuiElement {
        // Title label displaying "Plugins"
        private final Label titleLabel;
        // Status label showing "X / Y enabled", updated every render tick
        private final Label statusLabel;
        // Button that will open the PluginManagementScreen
        private final Button managePluginsButton;
        // Reference to the parent ManagementScreen
        private final ManagementScreen parentScreen;

        public PluginOverviewWidget(ManagementScreen parent) {
            super();
            this.setEnableBackground(false);
            parentScreen = parent;

            titleLabel = new Label(Texts.PLUGINS_TITLE.getString());
            titleLabel.setAlignment(Label.Alignment.CENTER);

            statusLabel = new Label("");
            statusLabel.setAlignment(Label.Alignment.CENTER);

            managePluginsButton = new Button(Texts.PLUGINS_MANAGE.getString(), this::onManagePluginsButtonClicked);

            this.addChild(titleLabel);
            this.addChild(statusLabel);
            this.addChild(managePluginsButton);

            this.setHeight(3 * (defaultElementHeight + spacing));
        }

        @Override
        protected void render() {
            int enabled = getPluginManager().getEnabledPluginCount();
            int total = getPluginManager().getPluginCount();
            statusLabel.setText(enabled + " / " + total + " " + Texts.PLUGINS_ENABLED.getString());
        }

        @Override
        protected void layoutChanged() {
            int width = getWidth() - 2 * padding;

            titleLabel.setBounds(padding, padding, width, defaultElementHeight - spacing);
            statusLabel.setBounds(padding, titleLabel.getBottom() + spacing, width, defaultElementHeight);
            managePluginsButton.setBounds(padding, statusLabel.getBottom() + spacing, width, defaultElementHeight);
        }

        private void onManagePluginsButtonClicked() {
            PluginManagementScreen pluginScreen = new PluginManagementScreen(parentScreen);
            setScreen(pluginScreen);
        }
    }

    private final CandlestickChart candlestickChart;
    private final ItemSelectionView marketSelectionView;

    private final ListView listView;
    private final CreateMarketWidget createMarketWidget;
    private final CurrentMarketManagerWidget currentMarketManagerWidget;
    private final PluginOverviewWidget pluginOverviewWidget;

    public ManagementScreen() {
        super(Texts.TITLE);

        candlestickChart = new CandlestickChart();
        marketSelectionView = new ItemSelectionView(this::onMarketSelected);

        listView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        listView.setLayout(layout);

        createMarketWidget = new CreateMarketWidget(this);
        listView.addChild(createMarketWidget);

        currentMarketManagerWidget = new CurrentMarketManagerWidget(this);
        listView.addChild(currentMarketManagerWidget);

        pluginOverviewWidget = new PluginOverviewWidget(this);
        listView.addChild(pluginOverviewWidget);


        addElement(candlestickChart);
        addElement(marketSelectionView);
        addElement(listView);

        marketSelectionView.clearItems();
        getMarketManager().requestMarkets().thenAccept(markets -> {
            List<ItemStack> stacks = new ArrayList<>();
            for(ItemID id : markets)
            {
                ItemStack stack = id.getStack();
                if(stack != null)
                    stacks.add(stack);
            }
            marketSelectionView.setItems(stacks);
        });
        getPluginManager().requestPluginList();
    }
    public static void openScreen()
    {
        ManagementScreen screen = new ManagementScreen();
        Minecraft.getInstance().setScreen(screen);
    }

    @Override
    public void onClose()
    {
        ClientMarket currentMarket = StockMarketGuiElement.getSelectedMarket();
        if(currentMarket != null)
        {
            currentMarket.unsubscribeFromMarketPriceUpdate();
            StockMarketGuiElement.selectMarket(null);
        }
        super.onClose();
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = StockMarketGuiElement.padding;
        int spacing = StockMarketGuiElement.spacing;
        int width = getWidth()- 2 * padding;
        int height = getHeight()- 2 * padding;

        candlestickChart.setBounds(padding,padding,width/2,height/2);
        marketSelectionView.setBounds(padding,candlestickChart.getBottom()+spacing,width/2, height - (candlestickChart.getBottom()+spacing)+padding);

        listView.setBounds(candlestickChart.getRight()+spacing, padding, width-(candlestickChart.getRight()+spacing)+padding, height);
    }

    private void onMarketSelected(ItemStack item)
    {
        ClientMarket currentMarket = StockMarketGuiElement.getSelectedMarket();
        if(currentMarket != null)
        {
            currentMarket.unsubscribeFromMarketPriceUpdate();
            StockMarketGuiElement.selectMarket(null);
            currentMarketManagerWidget.setCurrentMarket(null);
        }
        ItemID.getOrRegisterFromItemStackClientSide(item).thenAccept(itemID ->
        {
            ClientMarket market = getMarket(itemID);
            if(market != null) {
                market.subscribeToMarketPriceUpdate();
                StockMarketGuiElement.selectMarket(itemID);
                candlestickChart.setMarket(market);
                currentMarketManagerWidget.setCurrentMarket(itemID);
            }
        });
    }
}
