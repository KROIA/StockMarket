package net.kroia.stockmarket.screen;


import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.LayoutGrid;
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

/**
 * Management screen using a TabElement to organize:
 * - Overview tab: market selector, candlestick chart, market management widgets, plugin overview
 * - Create Markets tab: category-based preset browser for batch market creation
 */

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

    // Tab names
    private static final String TAB_OVERVIEW = "Overview";
    private static final String TAB_CREATE_MARKETS = "Create Markets";
    private static final String TAB_PRESETS = "Presets";

    // ---- Inner widget classes (used inside OverviewTab) ----

    /**
     * Widget for managing the currently selected market.
     * Shows the selected item icon, remove button, open/close toggle, and settings button.
     */
    public final class CurrentMarketManagerWidget extends StockMarketGuiElement{

        private final ItemView currentItemView;
        private final Label selectedMarketLabel;
        private final Button removeTradingPairButton;
        private final Button marketSettingsButton;
        private final CheckBox marketOpenCheckBox;
        private final Button openAllMarketsButton;
        private final Button closeAllMarketsButton;
        private final ManagementScreen parentScreen;
        private boolean loadingCheckBox = false;

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

            marketSettingsButton = new Button(Texts.MARKET_SETTINGS.getString(), this::onMarketSettingsButtonClicked);

            marketOpenCheckBox = new CheckBox(Texts.MARKET_OPEN.getString(),this::onMarketOpenCheckBoxChanged);
            marketOpenCheckBox.setTextAlignment(GuiElement.Alignment.CENTER);

            openAllMarketsButton = new Button(Texts.GENERAL_OPEN_ALL_MARKETS.getString(), this::onOpenAllMarkets);
            openAllMarketsButton.setHoverTooltipSupplier(Texts.GENERAL_OPEN_ALL_MARKETS_TOOLTIP::getString);
            openAllMarketsButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
            openAllMarketsButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

            closeAllMarketsButton = new Button(Texts.GENERAL_CLOSE_ALL_MARKETS.getString(), this::onCloseAllMarkets);
            closeAllMarketsButton.setHoverTooltipSupplier(Texts.GENERAL_CLOSE_ALL_MARKETS_TOOLTIP::getString);
            closeAllMarketsButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
            closeAllMarketsButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

            this.addChild(selectedMarketLabel);
            this.addChild(removeTradingPairButton);
            this.addChild(currentItemView);
            this.addChild(marketSettingsButton);
            this.addChild(marketOpenCheckBox);
            this.addChild(openAllMarketsButton);
            this.addChild(closeAllMarketsButton);

            this.setHeight(5*(elementHeight+spacing));
        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth()-2*padding;

            selectedMarketLabel.setBounds(padding, padding, width, elementHeight-spacing);

            currentItemView.setBounds(padding, selectedMarketLabel.getBottom()+spacing, width/2, elementHeight);
            removeTradingPairButton.setBounds(currentItemView.getRight()+spacing, currentItemView.getTop(), width/2-spacing, elementHeight);

            marketOpenCheckBox.setBounds(currentItemView.getLeft(), currentItemView.getBottom()+spacing, width, elementHeight);
            marketSettingsButton.setBounds(marketOpenCheckBox.getLeft(), marketOpenCheckBox.getBottom()+spacing, width, elementHeight);

            int btnW = (width - spacing) / 2;
            int btnY = marketSettingsButton.getBottom() + spacing;
            openAllMarketsButton.setBounds(padding, btnY, btnW, elementHeight);
            closeAllMarketsButton.setBounds(openAllMarketsButton.getRight() + spacing, btnY, btnW, elementHeight);
        }
        public void setCurrentMarket(ItemID marketID) {
            if(marketID == null)
                currentItemView.setItemStack(ItemStack.EMPTY);
            else
                currentItemView.setItemStack(marketID.getStack());
        }
        public void setMarketOpenCheckBoxChecked(boolean isOpen) {
            loadingCheckBox = true;
            marketOpenCheckBox.setChecked(isOpen);
            loadingCheckBox = false;
        }

        private void onRemoveTradingPairButtonClicked()
        {
            ClientMarket currentMarket = getSelectedMarket();
            if (currentMarket == null) return;
            setScreen(new ConfirmDeletePopup(parentScreen, currentMarket.getItemID()));
        }
        private void onMarketSettingsButtonClicked()
        {
            ClientMarket currentMarket = getSelectedMarket();
            if(currentMarket == null)
                return;

            MarketManagementScreen managementScreen = new MarketManagementScreen(parentScreen, currentMarket.getItemID());
            setScreen(managementScreen);
        }
        private void onMarketOpenCheckBoxChanged(Boolean isOpen)
        {
            if (loadingCheckBox) return;
            ClientMarket currentMarket = getSelectedMarket();
            if (currentMarket == null) return;
            currentMarket.setMarketOpenAsync(isOpen).thenAccept(success -> {
                if (success) {
                    Minecraft.getInstance().execute(() -> overviewTab.refreshMarketStates());
                }
            });
        }
        private void setAllMarketsOpen(boolean open) {
            List<ItemID> markets = getAvailableMarkets();
            int total = markets.size();
            final int[] completed = {0};
            for (ItemID marketID : markets) {
                ClientMarket market = getMarket(marketID);
                if (market == null) { completed[0]++; continue; }
                market.setMarketOpenAsync(open).thenAccept(success -> {
                    completed[0]++;
                    if (completed[0] >= total) {
                        Minecraft.getInstance().execute(() -> {
                            setMarketOpenCheckBoxChecked(open);
                            overviewTab.refreshMarketStates();
                        });
                    }
                });
            }
        }
        private void onOpenAllMarkets() {
            setAllMarketsOpen(true);
        }
        private void onCloseAllMarkets() {
            setAllMarketsOpen(false);
        }
    }

    /**
     * Widget that shows a summary of the plugin system and provides
     * a button to open the plugin management screen.
     *
     * <pre>
     * +---------------------------+
     * |       Plugins             |  (title label, centered)
     * |   2 / 3 enabled           |  (status label, live-updated)
     * |  [ Manage Plugins ]       |  (button, opens PluginManagementScreen)
     * +---------------------------+
     * </pre>
     */
    public final class PluginOverviewWidget extends StockMarketGuiElement {
        private final Label titleLabel;
        private final Label statusLabel;
        private final Button managePluginsButton;
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

    // ---- Overview tab ----

    /**
     * Container element for the "Overview" tab content.
     * Holds the candlestick chart, market selector, and the settings/widget list view
     * (current market manager + plugin overview).
     */
    public final class OverviewTab extends StockMarketGuiElement {
        private final CandlestickChart candlestickChart;
        private final Label searchLabel;
        private final TextBox searchField;
        private final ListView marketGridView;
        private final LayoutGrid marketGridLayout;
        private final List<MarketItemView> allMarketItems = new ArrayList<>();
        private final ListView listView;
        private final CurrentMarketManagerWidget currentMarketManagerWidget;
        private final PluginOverviewWidget pluginOverviewWidget;

        public OverviewTab(ManagementScreen parent) {
            super();
            setEnableBackground(false);

            candlestickChart = new CandlestickChart();

            searchLabel = new Label(Component.translatable("gui.stockmarket.create_market_tab.search").getString());
            searchLabel.setAlignment(Label.Alignment.RIGHT);

            searchField = new TextBox();
            searchField.setOnTextChanged(text -> applySearchFilter());

            marketGridView = new VerticalListView();
            marketGridLayout = new LayoutGrid(0, 0, false, false, 0, 1, Alignment.TOP);
            marketGridView.setLayout(marketGridLayout);

            listView = new VerticalListView();
            LayoutVertical layout = new LayoutVertical();
            layout.stretchX = true;
            layout.stretchY = false;
            listView.setLayout(layout);

            currentMarketManagerWidget = new CurrentMarketManagerWidget(parent);
            listView.addChild(currentMarketManagerWidget);

            pluginOverviewWidget = new PluginOverviewWidget(parent);
            listView.addChild(pluginOverviewWidget);

            addChild(candlestickChart);
            addChild(searchLabel);
            addChild(searchField);
            addChild(marketGridView);
            addChild(listView);

            loadMarketGrid();
            getPluginManager().requestPluginList();
        }

        private void loadMarketGrid() {
            allMarketItems.clear();
            marketGridView.removeChilds();
            getMarketManager().requestMarkets().thenAccept(markets -> {
                Minecraft.getInstance().execute(() -> {
                    List<ItemID> sorted = new ArrayList<>(markets);
                    sorted.sort(MARKET_TYPE_COMPARATOR);
                    for (ItemID id : sorted) {
                        ItemStack stack = id.getStack();
                        if (stack == null) continue;
                        MarketItemView view = new MarketItemView(stack, id);
                        allMarketItems.add(view);
                        marketGridView.addChild(view);
                    }
                    refreshMarketStates();
                });
            });
        }

        /**
         * Filters the market grid based on the search field text.
         * Matches against the item display name first, then falls back to the full
         * tooltip text (includes enchantment names, potion effects, etc.).
         */
        private void applySearchFilter() {
            String filter = searchField.getText().toLowerCase();
            marketGridView.removeChilds();
            for (MarketItemView view : allMarketItems) {
                if (filter.isEmpty()
                        || view.itemName.toLowerCase().contains(filter)
                        || ClientPlayerUtilities.getItemDisplayText(view.getItemStack()).toLowerCase().contains(filter)) {
                    marketGridView.addChild(view);
                }
            }
        }

        public void refreshMarketList() {
            loadMarketGrid();
        }

        public void refreshMarketStates() {
            for (MarketItemView view : allMarketItems) {
                ClientMarket market = getMarket(view.marketID);
                if (market != null) {
                    market.isMarketOpenAsync().thenAccept(isOpen ->
                        Minecraft.getInstance().execute(() -> view.setMarketOpen(isOpen))
                    );
                }
            }
        }

        @Override
        protected void render() {}

        @Override
        protected void layoutChanged() {
            int w = getWidth() - 2 * padding;
            int h = getHeight() - 2 * padding;

            candlestickChart.setBounds(padding, padding, w / 2, h / 2);

            int searchY = candlestickChart.getBottom() + spacing;
            int searchLabelW = w / 8;
            searchLabel.setBounds(padding, searchY, searchLabelW, elementHeight);
            searchField.setBounds(searchLabel.getRight() + spacing, searchY, w / 2 - searchLabelW - spacing, elementHeight);

            int gridY = searchField.getBottom() + spacing;
            marketGridView.setBounds(padding, gridY, w / 2, h - (gridY - padding));

            int containerWidth = marketGridView.getContainerWidth();
            marketGridLayout.columns = Math.max(1, containerWidth / ItemView.DEFAULT_WIDTH);

            listView.setBounds(candlestickChart.getRight() + spacing, padding,
                    w - (candlestickChart.getRight() + spacing) + padding, h);
        }

        public CurrentMarketManagerWidget getCurrentMarketManagerWidget() {
            return currentMarketManagerWidget;
        }

        public CandlestickChart getCandlestickChart() {
            return candlestickChart;
        }

        /**
         * Item icon in the market grid with a green/red overlay indicating open/closed state.
         */
        private class MarketItemView extends ItemView {
            private final ItemID marketID;
            private final String itemName;
            private boolean marketOpen = true;

            MarketItemView(ItemStack stack, ItemID marketID) {
                super(stack);
                this.marketID = marketID;
                this.itemName = stack.getHoverName().getString();
            }

            void setMarketOpen(boolean open) {
                this.marketOpen = open;
            }

            @Override
            public void renderBackground() {
                super.renderBackground();
                int overlayColor = marketOpen ? 0x3000FF00 : 0x40FF0000;
                drawRect(0, 0, getWidth(), getHeight(), overlayColor);
                if (isMouseOver()) {
                    drawRect(0, 0, getWidth(), getHeight(), 0x40FFFFFF);
                }
            }

            @Override
            protected boolean mouseClickedOverElement(int button) {
                if (button == 0) {
                    onMarketSelected(marketID.getStack());
                    return true;
                }
                return false;
            }
        }
    }

    // ---- ManagementScreen fields ----

    private final TabElement tabElement;
    private final OverviewTab overviewTab;
    private final CreateMarketTab createMarketTab;
    private final PresetEditorTab presetEditorTab;

    public ManagementScreen() {
        super(Texts.TITLE);

        tabElement = new TabElement();

        // Overview tab -- wraps existing chart/selector/widgets
        overviewTab = new OverviewTab(this);
        tabElement.addTab(TAB_OVERVIEW, overviewTab);

        // Create Markets tab -- category-based preset browser
        createMarketTab = new CreateMarketTab();
        createMarketTab.setOnMarketsChanged(overviewTab::refreshMarketList);
        tabElement.addTab(TAB_CREATE_MARKETS, createMarketTab);

        // Presets tab -- edit preset prices and abundance values
        presetEditorTab = new PresetEditorTab();
        tabElement.addTab(TAB_PRESETS, presetEditorTab);

        addElement(tabElement);
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
        int p = StockMarketGuiElement.padding;
        int w = getWidth() - 2 * p;
        int h = getHeight() - 2 * p;

        // TabElement fills the entire screen area
        tabElement.setBounds(p, p, w, h);
    }

    /**
     * Called when a market item is selected in the overview tab's market selector.
     */
    private void onMarketSelected(ItemStack item)
    {
        ClientMarket currentMarket = StockMarketGuiElement.getSelectedMarket();
        if(currentMarket != null)
        {
            currentMarket.unsubscribeFromMarketPriceUpdate();
            StockMarketGuiElement.selectMarket(null);
            overviewTab.getCurrentMarketManagerWidget().setCurrentMarket(null);
        }
        ItemID.getOrRegisterFromItemStackClientSide(item).thenAccept(itemID ->
        {
            ClientMarket market = getMarket(itemID);
            if(market != null) {
                market.subscribeToMarketPriceUpdate();
                StockMarketGuiElement.selectMarket(itemID);
                overviewTab.getCandlestickChart().setMarket(market);
                overviewTab.getCurrentMarketManagerWidget().setCurrentMarket(itemID);
                market.isMarketOpenAsync().thenAccept(isOpen ->
                    Minecraft.getInstance().execute(() ->
                        overviewTab.getCurrentMarketManagerWidget().setMarketOpenCheckBoxChecked(isOpen)
                    )
                );
            }
        });
    }

    /**
     * Confirmation popup shown before deleting a market.
     * Displays the market name, a warning message, and Yes/No buttons.
     * On confirm, sends the delete request to the server and returns to a fresh ManagementScreen.
     */
    private static class ConfirmDeletePopup extends StockMarketGuiScreen {
        private final ManagementScreen parentScreen;
        private final ItemID marketID;

        private final Label titleLabel;
        private final Label msgLabel;
        private final Button confirmButton;
        private final Button cancelButton;

        ConfirmDeletePopup(ManagementScreen parent, ItemID marketID) {
            super(Texts.ASK_TITLE, parent);
            this.parentScreen = parent;
            this.marketID = marketID;

            titleLabel = new Label(Texts.ASK_TITLE.getString());
            titleLabel.setAlignment(Label.Alignment.CENTER);

            msgLabel = new Label(Texts.ASK_MSG.getString());
            msgLabel.setAlignment(Label.Alignment.CENTER);

            confirmButton = new Button("Yes", this::onConfirm);
            confirmButton.setBackgroundColor(0xFFe8711c);
            confirmButton.setHoverColor(0xFFe04c12);
            confirmButton.setPressedColor(0xFFe04c12);

            cancelButton = new Button("No", this::onCancel);

            addElement(titleLabel);
            addElement(msgLabel);
            addElement(confirmButton);
            addElement(cancelButton);
        }

        @Override
        protected void updateLayout(Gui gui) {
            int p = StockMarketGuiElement.padding;
            int s = StockMarketGuiElement.spacing;
            int eh = StockMarketGuiElement.defaultElementHeight;

            int panelW = (int)(getWidth() * 0.4);
            int panelH = 4 * eh + 5 * s;
            int panelX = (getWidth() - panelW) / 2;
            int panelY = (getHeight() - panelH) / 2;

            titleLabel.setBounds(panelX, panelY, panelW, eh);
            msgLabel.setBounds(panelX, titleLabel.getBottom() + s, panelW, eh * 2);

            int btnW = (panelW - s) / 2;
            int btnY = msgLabel.getBottom() + s;
            confirmButton.setBounds(panelX, btnY, btnW, eh);
            cancelButton.setBounds(confirmButton.getRight() + s, btnY, btnW, eh);
        }

        private void onConfirm() {
            getMarketManager().requestDeleteMarket(marketID).thenAccept(success -> {
                Minecraft.getInstance().execute(() -> {
                    if (success) {
                        // Unsubscribe from price updates and deselect
                        ClientMarket market = getMarket(marketID);
                        if (market != null) {
                            market.unsubscribeFromMarketPriceUpdate();
                        }
                        StockMarketGuiElement.selectMarket(null);
                    }
                    // Return to a fresh ManagementScreen so the market list is refreshed
                    setScreen(new ManagementScreen());
                });
            });
        }

        private void onCancel() {
            setScreen(parentScreen);
        }
    }
}
