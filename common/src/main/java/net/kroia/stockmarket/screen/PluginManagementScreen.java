package net.kroia.stockmarket.screen;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistry;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.kroia.stockmarket.screen.uiElements.MarketItemButton;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.screen.widgets.OrderbookVolumeHistogram;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dedicated screen for managing all plugin instances.
 * Shows a scrollable list of plugins with their settings and subscribed markets.
 * Accessible from the PluginOverviewWidget in ManagementScreen.
 */
public class PluginManagementScreen extends StockMarketGuiScreen {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".plugin_management_screen.";
        private static final Component TITLE = Component.translatable(PREFIX + "title");
        public static final Component ENABLED = Component.translatable(PREFIX + "enabled");
        public static final Component OPEN_PLUGIN_SCREEN = Component.translatable(PREFIX + "open_plugin_screen");
        public static final Component ALL_MARKETS = Component.translatable(PREFIX + "all_markets");
    }

    private final StockMarketGuiScreen parent;
    private final Label titleLabel;
    private final Button filterButton;
    private final ItemView filterItemView;
    private @Nullable ItemID selectedFilterMarket = null;
    final CandlestickChart candlestickChart;
    final OrderbookVolumeHistogram orderbookVolumeHistogram;
    private @Nullable ItemID selectedChartMarket = null;
    private @Nullable ClientMarket currentChartMarket = null;
    private final ListView listView;
    private final List<PluginEntryWidget> entryWidgets = new ArrayList<>();
    private List<PluginSyncData> allPlugins = new ArrayList<>();

    /**
     * Creates the plugin management screen.
     *
     * @param parent the parent screen to return to on close
     */
    public PluginManagementScreen(StockMarketGuiScreen parent) {
        super(Texts.TITLE);
        this.parent = parent;

        titleLabel = new Label(Texts.TITLE.getString());
        titleLabel.setAlignment(Label.Alignment.CENTER);

        // Market filter — button opens a popup with ItemSelectionView
        filterButton = new Button(Texts.ALL_MARKETS.getString(), this::onFilterButtonClicked);
        filterItemView = new ItemView();
        filterItemView.setSize(16, 16);

        candlestickChart = new CandlestickChart();
        orderbookVolumeHistogram = new OrderbookVolumeHistogram(candlestickChart);

        listView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        listView.setLayout(layout);

        addElement(titleLabel);
        addElement(filterItemView);
        addElement(filterButton);
        addElement(candlestickChart);
        addElement(orderbookVolumeHistogram);
        addElement(listView);

        // Request the plugin list from the server and rebuild the UI
        getPluginManager().requestPluginList().thenAccept(this::rebuildPluginList);
    }

    @Override
    public void onClose() {
        if (currentChartMarket != null) {
            currentChartMarket.unsubscribeFromMarketPriceUpdate();
            currentChartMarket = null;
        }
        super.onClose();
        if (parent != null) {
            setScreen(parent);
        }
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = StockMarketGuiElement.padding;
        int spacing = StockMarketGuiElement.spacing;
        int width = getWidth() - 2 * padding;
        int height = getHeight() - 2 * padding;
        int eh = StockMarketGuiElement.defaultElementHeight;

        int filterWidth = width / 3;
        int iconSize = eh;
        boolean hasFilter = selectedFilterMarket != null;

        titleLabel.setBounds(padding, padding, width - filterWidth - (hasFilter ? iconSize + spacing : 0) - spacing, eh);
        if (hasFilter) {
            filterItemView.setBounds(titleLabel.getRight() + spacing, padding + (eh - 16) / 2, 16, 16);
            filterButton.setBounds(filterItemView.getRight() + spacing, padding, filterWidth - 16 - spacing, eh);
        } else {
            filterButton.setBounds(titleLabel.getRight() + spacing, padding, filterWidth, eh);
        }

        int contentTop = titleLabel.getBottom() + spacing;
        int contentHeight = height - (titleLabel.getBottom() + spacing) + padding;
        int histogramWidth = width / 20;
        int chartWidth = width / 2 - histogramWidth;
        int listWidth = width - chartWidth - histogramWidth - 2 * spacing;

        candlestickChart.setBounds(padding, contentTop, chartWidth, contentHeight);
        orderbookVolumeHistogram.setBounds(candlestickChart.getRight(), contentTop, histogramWidth, contentHeight);
        listView.setBounds(orderbookVolumeHistogram.getRight() + spacing, contentTop, listWidth, contentHeight);
    }

    /**
     * Rebuilds the scrollable plugin list from the given data.
     * Stores the full plugin list, rebuilds the market filter dropdown options,
     * and applies the current filter.
     *
     * @param plugins the list of plugin sync data from the server
     */
    public void rebuildPluginList(List<PluginSyncData> plugins) {
        this.allPlugins = new ArrayList<>(plugins);
        applyFilter();
    }

    /**
     * Re-requests the plugin list from the server and rebuilds the UI.
     */
    public void refreshPluginList() {
        getPluginManager().requestPluginList().thenAccept(this::rebuildPluginList);
    }

    /**
     * Filters the displayed plugin list based on the currently selected market filter.
     * If selectedFilterMarket is null, all plugins are shown.
     * Otherwise, only plugins subscribed to the selected market are shown.
     */
    private void applyFilter() {
        for (PluginEntryWidget entry : entryWidgets) {
            entry.cleanupPluginGuiElement();
            listView.removeChild(entry);
        }
        entryWidgets.clear();

        for (PluginSyncData data : allPlugins) {
            if (selectedFilterMarket != null) {
                if (!data.getSubscribedMarkets().contains(selectedFilterMarket)) {
                    continue;
                }
            }
            PluginEntryWidget entry = new PluginEntryWidget(data, this);
            entryWidgets.add(entry);
            listView.addChild(entry);
        }
        updateMarketButtonSelection();
    }

    /**
     * Opens the MarketFilterScreen popup to select a market filter.
     * Collects unique market ItemStacks from all plugins and passes them to the popup.
     */
    private void onFilterButtonClicked() {
        List<ItemStack> marketStacks = new ArrayList<>();
        List<ItemID> seenMarkets = new ArrayList<>();
        for (PluginSyncData data : allPlugins) {
            for (ItemID marketID : data.getSubscribedMarkets()) {
                if (!seenMarkets.contains(marketID)) {
                    seenMarkets.add(marketID);
                    ItemStack stack = marketID.getStack();
                    if (stack != null) {
                        marketStacks.add(stack);
                    }
                }
            }
        }
        setScreen(new MarketFilterScreen(this, marketStacks));
    }

    /**
     * Called by MarketFilterScreen when a market is selected.
     * Sets the filter and updates the button text and icon.
     *
     * @param marketID the selected market, or null for "all markets"
     */
    void setMarketFilter(@Nullable ItemID marketID) {
        this.selectedFilterMarket = marketID;
        if (marketID != null) {
            ItemStack stack = marketID.getStack();
            if (stack != null) {
                filterButton.setText(stack.getDisplayName().getString());
                filterItemView.setItemStack(stack);
            }
        } else {
            filterButton.setText(Texts.ALL_MARKETS.getString());
            filterItemView.setItemStack(null);
        }
        applyFilter();
    }

    /**
     * Selects a market for candlestick chart display, or deselects if the same market is clicked again.
     * Manages ClientMarket subscriptions for price data updates.
     *
     * @param marketID the market to select for chart display
     */
    void selectMarketForChart(ItemID marketID) {
        // Toggle off if same market clicked again
        if (marketID.equals(selectedChartMarket)) {
            if (currentChartMarket != null) {
                currentChartMarket.unsubscribeFromMarketPriceUpdate();
                currentChartMarket = null;
            }
            selectedChartMarket = null;
            candlestickChart.setMarket(null);
            updateMarketButtonSelection();
            return;
        }

        // Unsubscribe from old market
        if (currentChartMarket != null) {
            currentChartMarket.unsubscribeFromMarketPriceUpdate();
        }

        selectedChartMarket = marketID;
        ClientMarket market = getMarket(marketID);
        if (market != null) {
            market.subscribeToMarketPriceUpdate();
            candlestickChart.setMarket(market);
            currentChartMarket = market;
        }
        updateMarketButtonSelection();
    }

    private void updateMarketButtonSelection() {
        for (PluginEntryWidget entry : entryWidgets) {
            for (MarketItemButton btn : entry.marketItemViews) {
                btn.setSelected(selectedChartMarket != null && selectedChartMarket.equals(btn.getMarketID()));
            }
        }
    }

    /**
     * Popup screen for selecting a market to filter the plugin list by.
     * Shows an ItemSelectionView with all available markets and a "Show All" button.
     */
    private static class MarketFilterScreen extends StockMarketGuiScreen {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".plugin_management_screen.";
        private static final Component FILTER_TITLE = Component.translatable(PREFIX + "filter_title");

        private final PluginManagementScreen parentScreen;
        private final Label titleLabel;
        private final Button showAllButton;
        private final ItemSelectionView itemSelectionView;

        MarketFilterScreen(PluginManagementScreen parentScreen, List<ItemStack> marketStacks) {
            super(FILTER_TITLE, parentScreen);
            this.parentScreen = parentScreen;

            titleLabel = new Label(FILTER_TITLE.getString());
            titleLabel.setAlignment(Label.Alignment.CENTER);

            showAllButton = new Button(Texts.ALL_MARKETS.getString(), this::onShowAll);

            itemSelectionView = new ItemSelectionView(this::onMarketSelected);
            itemSelectionView.setItems(marketStacks);

            addElement(titleLabel);
            addElement(showAllButton);
            addElement(itemSelectionView);
        }

        private void onMarketSelected(ItemStack item) {
            ItemID.getOrRegisterFromItemStackClientSide(item).thenAccept(itemID -> {
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    parentScreen.setMarketFilter(itemID);
                    setScreen(parentScreen);
                });
            });
        }

        private void onShowAll() {
            parentScreen.setMarketFilter(null);
            setScreen(parentScreen);
        }

        @Override
        protected void updateLayout(Gui gui) {
            int p = StockMarketGuiElement.padding;
            int s = StockMarketGuiElement.spacing;
            int w = getWidth() - 2 * p;
            int eh = StockMarketGuiElement.defaultElementHeight;

            titleLabel.setBounds(p, p, w, eh);
            showAllButton.setBounds(p, titleLabel.getBottom() + s, w, eh);
            itemSelectionView.setBounds(p, showAllButton.getBottom() + s, w, getHeight() - showAllButton.getBottom() - s - p);
        }

        @Override
        public void onClose() {
            super.onClose();
        }
    }

    /**
     * UI widget for a single plugin entry in the management screen.
     * Shows the plugin name, description, subscribed markets as item icons,
     * enabled checkbox, and up/down reorder buttons.
     */
    public static final class PluginEntryWidget extends StockMarketGuiElement {

        private final PluginSyncData pluginData;
        private final PluginManagementScreen parentScreen;

        private final Label nameLabel;
        private final Label descriptionLabel;
        private final List<MarketItemButton> marketItemViews = new ArrayList<>();
        private final Button subscribeButton;
        private final CheckBox enabledCheckBox;
        private final Button moveUpButton;
        private final Button moveDownButton;
        @SuppressWarnings("rawtypes")
        private final PluginGuiElement pluginGuiElement;  // may be null if registry lookup fails
        private final Button openPluginScreenButton;      // only used if needsCustomScreen() is true

        /**
         * Creates a plugin entry widget for the given plugin data.
         *
         * @param data         the plugin sync data to display
         * @param parentScreen the parent PluginManagementScreen for callbacks
         */
        public PluginEntryWidget(PluginSyncData data, PluginManagementScreen parentScreen) {
            super();
            this.pluginData = data;
            this.parentScreen = parentScreen;

            // Name label (registry ID shown as tooltip on hover)
            nameLabel = new Label(data.getName());
            if (data.getPluginTypeID() != null) {
                nameLabel.setHoverTooltipSupplier(data::getPluginTypeID);
                nameLabel.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.BOTTOM_LEFT);
                nameLabel.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
            }

            // Description label
            descriptionLabel = new Label(data.getDescription() != null ? data.getDescription() : "");

            // Item icons for subscribed markets (each button has a built-in close area in the top-right corner)
            for (ItemID marketID : data.getSubscribedMarkets()) {
                ItemStack stack = marketID.getStack();
                if (stack != null) {
                    ItemID currentMarketID = marketID;
                    MarketItemButton itemBtn = new MarketItemButton(stack, marketID,
                            id -> parentScreen.selectMarketForChart(id),
                            () -> onUnsubscribeClicked(currentMarketID));
                    itemBtn.setSize(16, 16);
                    marketItemViews.add(itemBtn);
                }
            }

            // "+" button to subscribe to a new market
            subscribeButton = new Button("+", this::onSubscribeClicked);

            // Enabled checkbox — set state before attaching callback to avoid triggering a request loop
            enabledCheckBox = new CheckBox(Texts.ENABLED.getString());
            enabledCheckBox.setChecked(data.isEnabled());
            enabledCheckBox.setOnStateChanged(this::onEnabledChanged);

            // Move up/down buttons
            moveUpButton = new Button("▲", this::onMoveUp);
            moveDownButton = new Button("▼", this::onMoveDown);

            // Add children
            this.addChild(nameLabel);
            this.addChild(descriptionLabel);
            for (MarketItemButton iv : marketItemViews) {
                this.addChild(iv);
            }
            this.addChild(subscribeButton);
            this.addChild(enabledCheckBox);
            this.addChild(moveUpButton);
            this.addChild(moveDownButton);

            // Create the plugin GUI element from the registry (no ClientPlugin intermediary needed)
            this.pluginGuiElement = PluginRegistry.createGuiElement(data.getPluginTypeID());

            // Pass sync data (subscribed markets, metadata) to the GUI element
            if (pluginGuiElement != null) {
                pluginGuiElement.setPluginSyncData(data);
                pluginGuiElement.setCandlestickChart(parentScreen.candlestickChart);
                pluginGuiElement.setOrderbookVolumeHistogram(parentScreen.orderbookVolumeHistogram);
            }

            if (pluginGuiElement != null && pluginGuiElement.needsCustomScreen()) {
                // Show button to open dedicated screen
                openPluginScreenButton = new Button(Texts.OPEN_PLUGIN_SCREEN.getString(), this::onOpenPluginScreen);
                this.addChild(openPluginScreenButton);
            } else if (pluginGuiElement != null) {
                // Embed inline
                openPluginScreenButton = null;
                this.addChild(pluginGuiElement);
            } else {
                openPluginScreenButton = null;
            }

            // Set entry height: base 4 rows + optional extra row for button or inline GUI element
            int baseHeight = 4 * (defaultElementHeight + spacing) + padding;
            if (openPluginScreenButton != null) {
                baseHeight += defaultElementHeight + spacing;
            } else if (pluginGuiElement != null) {
                baseHeight += 80 + spacing;
            }
            this.setHeight(baseHeight);

            // Enable background for visual separation
            this.setEnableBackground(true);
        }

        @Override
        protected void render() {
            // No dynamic rendering needed
        }

        @Override
        protected void layoutChanged() {
            int width = getWidth() - 2 * padding;
            int btnWidth = 20;
            int checkBoxWidth = 100;

            // Row 1: nameLabel (left), enabledCheckBox (right of name), moveUp and moveDown (far right)
            int nameLabelWidth = width - checkBoxWidth - 2 * btnWidth - 3 * spacing;
            nameLabel.setBounds(padding, padding, nameLabelWidth, defaultElementHeight);
            enabledCheckBox.setBounds(nameLabel.getRight() + spacing, padding, checkBoxWidth, defaultElementHeight);
            moveUpButton.setBounds(enabledCheckBox.getRight() + spacing, padding, btnWidth, defaultElementHeight);
            moveDownButton.setBounds(moveUpButton.getRight() + spacing, padding, btnWidth, defaultElementHeight);

            // Row 2: description label (full width)
            descriptionLabel.setBounds(padding, nameLabel.getBottom() + spacing, width, defaultElementHeight);

            // Row 3: market item icons, plus subscribe button
            int iconX = padding;
            int iconY = descriptionLabel.getBottom() + spacing;
            for (int idx = 0; idx < marketItemViews.size(); idx++) {
                MarketItemButton iv = marketItemViews.get(idx);
                iv.setBounds(iconX, iconY, 16, 16);
                iconX += 16 + 2;
            }
            subscribeButton.setBounds(iconX, iconY, 20, 16);

            // Row 4: PluginGuiElement (inline) or "Open Plugin" button
            int row4Y = iconY + 18 + spacing;
            if (openPluginScreenButton != null) {
                openPluginScreenButton.setBounds(padding, row4Y, width, defaultElementHeight);
            } else if (pluginGuiElement != null) {
                pluginGuiElement.setBounds(padding, row4Y, width, 80);
            }
        }

        /**
         * Called when the "Open Plugin" button is clicked.
         * Opens a dedicated PluginDetailScreen for this plugin.
         */
        private void onOpenPluginScreen() {
            PluginDetailScreen screen = new PluginDetailScreen(parentScreen, pluginData, pluginGuiElement);
            setScreen(screen);
        }

        /**
         * Called when the enabled checkbox state changes.
         * Sends a settings update request to the server.
         */
        private void onEnabledChanged(Boolean enabled) {
            GenericPluginData data = pluginData.getGenericData();
            data.setEnabled(enabled);
            getPluginManager().requestUpdateSettings(pluginData.getInstanceID(), data).thenAccept(result -> {
                if (result.success()) {
                    parentScreen.refreshPluginList();
                }
            });
        }

        /**
         * Called when the move-up button is clicked.
         * Sends a reorder request to move this plugin up in the execution order.
         */
        private void onMoveUp() {
            getPluginManager().requestReorderPlugin(pluginData.getInstanceID(), -1).thenAccept(list -> {
                parentScreen.rebuildPluginList(list);
            });
        }

        /**
         * Called when the move-down button is clicked.
         * Sends a reorder request to move this plugin down in the execution order.
         */
        private void onMoveDown() {
            getPluginManager().requestReorderPlugin(pluginData.getInstanceID(), 1).thenAccept(list -> {
                parentScreen.rebuildPluginList(list);
            });
        }

        /**
         * Called when the subscribe "+" button is clicked.
         * Opens a MarketSubscribeScreen popup showing available markets not yet subscribed.
         */
        private void onSubscribeClicked() {
            List<ItemStack> marketStacks = new ArrayList<>();
            for (ItemID marketID : getAvailableMarkets()) {
                // Skip markets already subscribed
                if (!pluginData.getSubscribedMarkets().contains(marketID)) {
                    ItemStack stack = marketID.getStack();
                    if (stack != null) {
                        marketStacks.add(stack);
                    }
                }
            }
            setScreen(new MarketSubscribeScreen(parentScreen, pluginData.getInstanceID(), marketStacks));
        }

        /**
         * Called when an unsubscribe "x" button is clicked for a specific market.
         * Sends an unsubscribe request to the server and refreshes the plugin list on success.
         */
        void cleanupPluginGuiElement() {
            if (pluginGuiElement != null) {
                pluginGuiElement.setCandlestickChart(null);
                pluginGuiElement.setOrderbookVolumeHistogram(null);
                pluginGuiElement.stopDataStream();
            }
        }

        private void onUnsubscribeClicked(ItemID marketID) {
            getPluginManager().requestUpdateSubscription(pluginData.getInstanceID(), marketID, false).thenAccept(result -> {
                if (result.success()) {
                    parentScreen.refreshPluginList();
                }
            });
        }
    }

    /**
     * Popup screen for selecting a market to subscribe a plugin to.
     * Shows an ItemSelectionView with available markets that the plugin is not yet subscribed to.
     */
    private static class MarketSubscribeScreen extends StockMarketGuiScreen {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".plugin_management_screen.";
        private static final Component SUBSCRIBE_TITLE = Component.translatable(PREFIX + "subscribe_market_title");

        private final PluginManagementScreen parentScreen;
        private final UUID pluginInstanceID;
        private final Label titleLabel;
        private final ItemSelectionView itemSelectionView;

        MarketSubscribeScreen(PluginManagementScreen parentScreen, UUID pluginInstanceID, List<ItemStack> marketStacks) {
            super(SUBSCRIBE_TITLE, parentScreen);
            this.parentScreen = parentScreen;
            this.pluginInstanceID = pluginInstanceID;

            titleLabel = new Label(SUBSCRIBE_TITLE.getString());
            titleLabel.setAlignment(Label.Alignment.CENTER);

            itemSelectionView = new ItemSelectionView(this::onMarketSelected);
            itemSelectionView.setItems(marketStacks);

            addElement(titleLabel);
            addElement(itemSelectionView);
        }

        /**
         * Called when a market item is selected from the ItemSelectionView.
         * Sends a subscribe request to the server and returns to the parent screen.
         */
        private void onMarketSelected(ItemStack item) {
            ItemID.getOrRegisterFromItemStackClientSide(item).thenAccept(itemID -> {
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    getPluginManager().requestUpdateSubscription(pluginInstanceID, itemID, true).thenAccept(result -> {
                        if (result.success()) {
                            parentScreen.refreshPluginList();
                        }
                        net.minecraft.client.Minecraft.getInstance().execute(() -> {
                            setScreen(parentScreen);
                        });
                    });
                });
            });
        }

        @Override
        protected void updateLayout(Gui gui) {
            int p = StockMarketGuiElement.padding;
            int s = StockMarketGuiElement.spacing;
            int w = getWidth() - 2 * p;
            int eh = StockMarketGuiElement.defaultElementHeight;

            titleLabel.setBounds(p, p, w, eh);
            itemSelectionView.setBounds(p, titleLabel.getBottom() + s, w, getHeight() - titleLabel.getBottom() - s - p);
        }

        @Override
        public void onClose() {
            super.onClose();
        }
    }

}
