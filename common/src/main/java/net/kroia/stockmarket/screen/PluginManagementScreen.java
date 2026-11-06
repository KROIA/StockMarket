package net.kroia.stockmarket.screen;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.InputConstants;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.LayoutGrid;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.pluginsystem.plugin.core.GenericPluginData;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistry;
import net.kroia.stockmarket.pluginsystem.registry.PluginRegistryObject;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.kroia.stockmarket.pluginsystem.pluginmanager.ClientPluginManager;
import net.kroia.stockmarket.screen.uiElements.MarketItemButton;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.screen.widgets.OrderbookVolumeHistogram;
import net.kroia.stockmarket.screen.widgets.PluginPerformanceBar;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        public static final Component AUTO_SUBSCRIBE = Component.translatable(PREFIX + "auto_subscribe");
        public static final Component ADD_PLUGIN = Component.translatable(PREFIX + "add_plugin");
        public static final Component ADD_PLUGIN_TITLE = Component.translatable(PREFIX + "add_plugin_title");
        public static final Component DELETE_PLUGIN = Component.translatable(PREFIX + "delete_plugin");
        public static final Component CLOSE = Component.translatable(PREFIX + "subscribe_close");
        // Hover tooltips of the generic plugin controls
        public static final Component ENABLED_TOOLTIP = Component.translatable(PREFIX + "enabled.tooltip");
        public static final Component OPEN_PLUGIN_SCREEN_TOOLTIP = Component.translatable(PREFIX + "open_plugin_screen.tooltip");
        public static final Component AUTO_SUBSCRIBE_TOOLTIP = Component.translatable(PREFIX + "auto_subscribe.tooltip");
        public static final Component LOGGER_TOOLTIP = Component.translatable(PREFIX + "logger.tooltip");
        public static final Component ADD_PLUGIN_TOOLTIP = Component.translatable(PREFIX + "add_plugin.tooltip");
        public static final Component DELETE_PLUGIN_TOOLTIP = Component.translatable(PREFIX + "delete_plugin.tooltip");
        public static final Component MOVE_UP_TOOLTIP = Component.translatable(PREFIX + "move_up.tooltip");
        public static final Component MOVE_DOWN_TOOLTIP = Component.translatable(PREFIX + "move_down.tooltip");
        public static final Component SUBSCRIBE_MARKET_TOOLTIP = Component.translatable(PREFIX + "subscribe_market.tooltip");
        public static final Component MARKET_BUTTON_TOOLTIP = Component.translatable(PREFIX + "market_button.tooltip");
        public static final Component SUBSCRIBE_TOGGLE_TOOLTIP = Component.translatable(PREFIX + "subscribe_toggle.tooltip");
    }

    private final Label titleLabel;
    private final Button addPluginButton;
    /**
     * T-123 / T-125 (untrusted slave gate): red info banner shown across the
     * top of the plugin management screen on an untrusted slave. Every
     * mutating plugin button (add/delete/enable/subscribe/reorder/logger/
     * auto-subscribe) is also disabled — see {@link #applyUntrustedSlaveGate}.
     * T-125: rendered as three stacked labels because ModUtilities' Label
     * widget is single-line only and the full trust-explanation would overflow.
     */
    private final Label untrustedSlaveBanner1;
    private final Label untrustedSlaveBanner2;
    private final Label untrustedSlaveBanner3;
    final CandlestickChart candlestickChart;
    final OrderbookVolumeHistogram orderbookVolumeHistogram;
    /**
     * T-137: horizontal per-plugin performance bar shown across the top of the
     * screen, below the title and any warning banners. Its full width represents
     * one server tick (50&nbsp;ms). The widget subscribes to
     * {@link ClientPluginManager#getLatestTimingSnapshot()} — the stream itself
     * is started/stopped by this screen's lifecycle hooks.
     */
    private final PluginPerformanceBar performanceBar;
    private @Nullable ItemID selectedChartMarket = null;
    private @Nullable ClientMarket currentChartMarket = null;
    private final ListView listView;
    private final List<PluginEntryWidget> entryWidgets = new ArrayList<>();
    private List<PluginSyncData> allPlugins = new ArrayList<>();
    /**
     * Instance ID of the selected plugin entry (target of the Left/Right market
     * navigation), or null. Stored by UUID so the selection survives list rebuilds.
     */
    private @Nullable UUID selectedPluginID = null;

    /**
     * Creates the plugin management screen.
     *
     * @param parent the parent screen to return to on close
     */
    public PluginManagementScreen(StockMarketGuiScreen parent) {
        // Parent goes to the GuiScreen base so its onClose() returns to it and restores
        // the cursor. Closing with a null parent and then calling setScreen(parent)
        // re-grabs/releases the mouse, which re-centers the cursor.
        super(Texts.TITLE, parent);

        titleLabel = new Label(Texts.TITLE.getString());
        titleLabel.setAlignment(Label.Alignment.CENTER);

        addPluginButton = new Button(Texts.ADD_PLUGIN.getString(), this::onAddPluginClicked);
        PluginGuiElement.setSettingTooltip(Texts.ADD_PLUGIN_TOOLTIP, addPluginButton);
        // T-123: hide the "Add plugin" entry point on an untrusted slave.
        if (isUntrustedSlave()) {
            addPluginButton.setEnabled(false);
        }

        // T-123 / T-125: red info banner across the top of the screen when the
        // master does NOT trust this slave; every per-entry mutating widget is
        // also disabled inside PluginEntryWidget itself. Split into three
        // stacked labels so the long message fits on any screen width without
        // overflow (ModUtilities' Label widget renders a single line only).
        untrustedSlaveBanner1 = new Label(Component.translatable("gui.stockmarket.untrusted_slave.banner_line1").getString());
        untrustedSlaveBanner1.setAlignment(Label.Alignment.CENTER);
        untrustedSlaveBanner1.setTextColor(0xFFe8711c);
        untrustedSlaveBanner1.setTextFontScale(0.7f);
        untrustedSlaveBanner1.setEnabled(isUntrustedSlave());

        untrustedSlaveBanner2 = new Label(Component.translatable("gui.stockmarket.untrusted_slave.banner_line2").getString());
        untrustedSlaveBanner2.setAlignment(Label.Alignment.CENTER);
        untrustedSlaveBanner2.setTextColor(0xFFe8711c);
        untrustedSlaveBanner2.setTextFontScale(0.7f);
        untrustedSlaveBanner2.setEnabled(isUntrustedSlave());

        untrustedSlaveBanner3 = new Label(Component.translatable("gui.stockmarket.untrusted_slave.banner_line3").getString());
        untrustedSlaveBanner3.setAlignment(Label.Alignment.CENTER);
        untrustedSlaveBanner3.setTextColor(0xFFe8711c);
        untrustedSlaveBanner3.setTextFontScale(0.7f);
        untrustedSlaveBanner3.setEnabled(isUntrustedSlave());

        candlestickChart = new CandlestickChart();
        orderbookVolumeHistogram = new OrderbookVolumeHistogram(candlestickChart);
        performanceBar = new PluginPerformanceBar();

        listView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        listView.setLayout(layout);

        addElement(titleLabel);
        addElement(addPluginButton);
        addElement(untrustedSlaveBanner1);
        addElement(untrustedSlaveBanner2);
        addElement(untrustedSlaveBanner3);
        addElement(performanceBar);
        addElement(candlestickChart);
        addElement(orderbookVolumeHistogram);
        addElement(listView);

        // Request the plugin list from the server and rebuild the UI
        getPluginManager().requestPluginList().thenAccept(this::rebuildPluginList);

        // T-137: open the master-wide plugin timing stream so the performance
        // bar has live data to render. Idempotent — safe to call again during
        // updateLayout() when the screen is re-initialised after returning
        // from a child (PluginDetailScreen etc.).
        startTimingStream();
    }

    /**
     * T-137: opens the plugin performance timing stream on the client-side
     * plugin manager. Idempotent — a second call while already subscribed is
     * a no-op inside {@link ClientPluginManager#startTimingStream()}.
     */
    private void startTimingStream() {
        if (BACKEND_INSTANCES != null && BACKEND_INSTANCES.PLUGIN_MANAGER instanceof ClientPluginManager clientMgr) {
            clientMgr.startTimingStream();
        }
    }

    /**
     * T-137: closes the plugin performance timing stream. Called from
     * {@link #onClose()}; idempotent.
     */
    private void stopTimingStream() {
        if (BACKEND_INSTANCES != null && BACKEND_INSTANCES.PLUGIN_MANAGER instanceof ClientPluginManager clientMgr) {
            clientMgr.stopTimingStream();
        }
    }

    @Override
    public void onClose() {
        for (PluginEntryWidget entry : entryWidgets) {
            entry.cleanupPluginGuiElement();
        }
        if (currentChartMarket != null) {
            currentChartMarket.unsubscribeFromMarketPriceUpdate();
            currentChartMarket = null;
        }
        // T-137: tear down the timing stream so the server stops broadcasting
        // to this client once the management window is gone.
        stopTimingStream();
        super.onClose(); // returns to the parent (base class) with the cursor preserved
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = StockMarketGuiElement.padding;
        int spacing = StockMarketGuiElement.spacing;
        int width = getWidth() - 2 * padding;
        int height = getHeight() - 2 * padding;
        int eh = StockMarketGuiElement.defaultElementHeight;

        // Title label takes most of the width, add plugin button on the right
        int addBtnWidth = 80;
        titleLabel.setBounds(padding, padding, width - addBtnWidth - spacing, eh);
        addPluginButton.setBounds(titleLabel.getRight() + spacing, padding, addBtnWidth, eh);

        // T-123 / T-125 banner: three stacked lines sitting under the title
        // bar, above the chart+list area. Each line is a single Label because
        // ModUtilities' Label widget renders one line only.
        int bannerLineH = 12; // matches textFontScale=0.7f visible height
        int bannerHeight = untrustedSlaveBanner1.isEnabled() ? 3 * bannerLineH : 0;
        int bannerReserve = bannerHeight == 0 ? 0 : bannerHeight + spacing;
        int bannerTop = titleLabel.getBottom() + spacing;
        untrustedSlaveBanner1.setBounds(padding, bannerTop, width, bannerLineH);
        untrustedSlaveBanner2.setBounds(padding, bannerTop + bannerLineH, width, bannerLineH);
        untrustedSlaveBanner3.setBounds(padding, bannerTop + 2 * bannerLineH, width, bannerLineH);

        // T-137: performance bar sits directly below the untrusted-slave
        // banner (if any) and above the chart/list area. Reserves its own
        // vertical strip via the same bannerReserve-style pattern so the
        // chart, histogram and list still get all remaining space.
        int perfBarTop = bannerTop + bannerHeight + (bannerHeight == 0 ? 0 : spacing);
        int perfBarHeight = PluginPerformanceBar.TOTAL_HEIGHT;
        int perfBarReserve = perfBarHeight + spacing;
        performanceBar.setBounds(padding, perfBarTop, width, perfBarHeight);

        int contentTop = titleLabel.getBottom() + spacing + bannerReserve + perfBarReserve;
        int contentHeight = height - (titleLabel.getBottom() + spacing + bannerReserve + perfBarReserve) + padding;
        int histogramWidth = width / 20;
        int chartWidth = width / 2 - histogramWidth;
        int listWidth = width - chartWidth - histogramWidth - spacing;

        candlestickChart.setBounds(padding, contentTop, chartWidth, contentHeight);
        orderbookVolumeHistogram.setBounds(candlestickChart.getRight(), contentTop, histogramWidth, contentHeight);
        listView.setBounds(orderbookVolumeHistogram.getRight() + spacing, contentTop, listWidth, contentHeight);

        // Restart data streams (e.g. after returning from PluginDetailScreen)
        for (PluginEntryWidget entry : entryWidgets) {
            entry.startPluginDataStream();
        }
        // T-137: also restart the timing stream after a subscreen returns —
        // matches the runtime-data stream restart pattern above. Idempotent.
        startTimingStream();
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
     * Rebuilds the plugin entry list from the current data.
     */
    private void applyFilter() {
        // Save scroll position before clearing the list
        int savedScroll = listView.getScrollOffset();

        for (PluginEntryWidget entry : entryWidgets) {
            entry.cleanupPluginGuiElement();
            listView.removeChild(entry);
        }
        entryWidgets.clear();

        for (PluginSyncData data : allPlugins) {
            PluginEntryWidget entry = new PluginEntryWidget(data, this);
            entryWidgets.add(entry);
            listView.addChild(entry);
        }
        // Re-select by instance ID; clears the selection if the plugin no longer exists
        if (findEntry(selectedPluginID) == null) {
            selectedPluginID = null;
        }
        updatePluginSelection();
        updateMarketButtonSelection();

        // Restore scroll position after rebuilding the list
        listView.setScrollOffset(savedScroll);
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

    /**
     * Called when the "Add Plugin" button is clicked.
     * Opens a popup showing all registered plugin types for selection.
     */
    private void onAddPluginClicked() {
        setScreen(new PluginCreatePopup(this));
    }

    /**
     * Marks the given entry as the selected plugin (darker background).
     * Called on any click inside an entry widget.
     *
     * @param entry the entry that was clicked
     */
    void selectPlugin(PluginEntryWidget entry) {
        selectedPluginID = entry.pluginData.getInstanceID();
        updatePluginSelection();
    }

    /** Applies the selected/unselected background to every entry widget. */
    private void updatePluginSelection() {
        for (PluginEntryWidget entry : entryWidgets) {
            entry.setSelected(selectedPluginID != null && selectedPluginID.equals(entry.pluginData.getInstanceID()));
        }
    }

    /**
     * @param instanceID the plugin instance ID, may be null
     * @return the entry widget of that plugin instance, or null if not shown
     */
    private @Nullable PluginEntryWidget findEntry(@Nullable UUID instanceID) {
        if (instanceID == null) return null;
        for (PluginEntryWidget entry : entryWidgets) {
            if (instanceID.equals(entry.pluginData.getInstanceID())) return entry;
        }
        return null;
    }

    /**
     * Left/Right arrow: steps the selected market through the selected plugin's
     * subscribed-market list (wrapping around), exactly like clicking the icon.
     * Arrows are left alone while a text input has focus so cursor movement keeps
     * working; when handled, the key is consumed so no other widget reacts to it.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_RIGHT) {
            GuiElement focused = getFocusedElement();
            boolean textInputFocused = focused instanceof TextBox || focused instanceof MultiLineTextBox;
            PluginEntryWidget entry = findEntry(selectedPluginID);
            if (!textInputFocused && entry != null && !entry.marketItemViews.isEmpty()) {
                entry.stepMarket(keyCode == InputConstants.KEY_RIGHT ? 1 : -1, selectedChartMarket);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateMarketButtonSelection() {
        for (PluginEntryWidget entry : entryWidgets) {
            for (MarketItemButton btn : entry.marketItemViews) {
                btn.setSelected(selectedChartMarket != null && selectedChartMarket.equals(btn.getMarketID()));
            }
            entry.setActiveMarketFromScreen(selectedChartMarket);
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
        private final List<Label> descriptionLabels = new ArrayList<>();
        private static final float descriptionFontScale = 0.8f;
        private final List<MarketItemButton> marketItemViews = new ArrayList<>();
        private final VerticalListView marketItemListView;
        private final LayoutGrid marketItemGridLayout;
        private final Button subscribeButton;
        private final CheckBox enabledCheckBox;
        private final Button deleteButton;
        private final Button moveUpButton;
        private final Button moveDownButton;
        private final CheckBox autoSubscribeCheckBox;
        private final CheckBox loggerCheckBox;
        @SuppressWarnings("rawtypes")
        private final PluginGuiElement pluginGuiElement;  // may be null if registry lookup fails
        private final Button openPluginScreenButton;      // only used if needsCustomScreen() is true
        private final Label missingPluginLabel;            // shown when plugin mod is not installed on client

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

            // Description labels (split on newlines for multiline support)
            String desc = data.getDescription() != null ? data.getDescription() : "";
            for (String line : desc.split("\n")) {
                Label lineLabel = new Label(line);
                lineLabel.setTextFontScale(descriptionFontScale);
                descriptionLabels.add(lineLabel);
            }

            // Item icons for subscribed markets (each button has a built-in close area in the top-right corner)
            for (ItemID marketID : data.getSubscribedMarkets()) {
                ItemStack stack = marketID.getStack();
                if (stack != null) {
                    ItemID currentMarketID = marketID;
                    MarketItemButton itemBtn = new MarketItemButton(stack, marketID,
                            id -> parentScreen.selectMarketForChart(id),
                            () -> onUnsubscribeClicked(currentMarketID));
                    itemBtn.setSize(16, 16);
                    // Replace the plain item tooltip with item name + click/close hints
                    itemBtn.setShowTooltip(false);
                    String itemTooltip = stack.getHoverName().getString() + "\n"
                            + Texts.MARKET_BUTTON_TOOLTIP.getString();
                    itemBtn.setHoverTooltipSupplier(() -> itemTooltip);
                    itemBtn.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
                    itemBtn.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
                    marketItemViews.add(itemBtn);
                }
            }

            // Sort by item type (reversed registry path segments)
            marketItemViews.sort(Comparator.comparing(btn -> marketTypeSortKey(btn.getMarketID().getName())));

            // Scrollable grid container for market item icons
            marketItemListView = new VerticalListView();
            marketItemListView.setEnableBackground(true);
            marketItemGridLayout = new LayoutGrid(0, 2, false, false, 0, 1, GuiElement.Alignment.TOP);
            marketItemListView.setLayout(marketItemGridLayout);
            for (MarketItemButton iv : marketItemViews) {
                marketItemListView.addChild(iv);
            }

            // "+" button to subscribe to a new market
            subscribeButton = new Button("+", this::onSubscribeClicked);

            // Enabled checkbox — set state before attaching callback to avoid triggering a request loop
            enabledCheckBox = new CheckBox(Texts.ENABLED.getString());
            enabledCheckBox.setChecked(data.isEnabled());
            enabledCheckBox.setOnStateChanged(this::onEnabledChanged);

            // Delete button
            deleteButton = new Button(Texts.DELETE_PLUGIN.getString(), this::onDeleteClicked);

            // Move up/down buttons
            moveUpButton = new Button("▲", this::onMoveUp);
            moveDownButton = new Button("▼", this::onMoveDown);

            // Auto-subscribe checkbox — set state before attaching callback
            autoSubscribeCheckBox = new CheckBox(Texts.AUTO_SUBSCRIBE.getString());
            autoSubscribeCheckBox.setChecked(data.getGenericData().getAutoSubscribeNewMarkets());
            autoSubscribeCheckBox.setOnStateChanged(this::onAutoSubscribeChanged);

            // Logger checkbox — set state before attaching callback
            loggerCheckBox = new CheckBox(Component.translatable("gui.stockmarket.plugin_management_screen.logger").getString());
            loggerCheckBox.setChecked(data.isLoggerEnabled());
            loggerCheckBox.setOnStateChanged(this::onLoggerChanged);

            // Hover tooltips explaining every generic control (right-anchored: the
            // plugin list sits at the right side of the screen)
            PluginGuiElement.setSettingTooltip(Texts.SUBSCRIBE_MARKET_TOOLTIP, subscribeButton);
            PluginGuiElement.setSettingTooltip(Texts.ENABLED_TOOLTIP, enabledCheckBox);
            PluginGuiElement.setSettingTooltip(Texts.DELETE_PLUGIN_TOOLTIP, deleteButton);
            PluginGuiElement.setSettingTooltip(Texts.MOVE_UP_TOOLTIP, moveUpButton);
            PluginGuiElement.setSettingTooltip(Texts.MOVE_DOWN_TOOLTIP, moveDownButton);
            PluginGuiElement.setSettingTooltip(Texts.AUTO_SUBSCRIBE_TOOLTIP, autoSubscribeCheckBox);
            PluginGuiElement.setSettingTooltip(Texts.LOGGER_TOOLTIP, loggerCheckBox);

            // Add children
            this.addChild(nameLabel);
            for (Label dl : descriptionLabels) this.addChild(dl);
            this.addChild(marketItemListView);
            this.addChild(subscribeButton);
            this.addChild(enabledCheckBox);
            this.addChild(deleteButton);
            this.addChild(moveUpButton);
            this.addChild(moveDownButton);
            this.addChild(autoSubscribeCheckBox);
            this.addChild(loggerCheckBox);

            // T-123 (untrusted slave gate): every per-entry mutating input is
            // disabled on an untrusted slave. Also grays out the per-market
            // MarketItemButton instances that let admins unsubscribe.
            if (isUntrustedSlave()) {
                subscribeButton.setEnabled(false);
                enabledCheckBox.setEnabled(false);
                deleteButton.setEnabled(false);
                moveUpButton.setEnabled(false);
                moveDownButton.setEnabled(false);
                autoSubscribeCheckBox.setEnabled(false);
                loggerCheckBox.setEnabled(false);
                for (MarketItemButton iv : marketItemViews) {
                    iv.setEnabled(false);
                }
            }

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
                PluginGuiElement.setSettingTooltip(Texts.OPEN_PLUGIN_SCREEN_TOOLTIP, openPluginScreenButton);
                this.addChild(openPluginScreenButton);
                missingPluginLabel = null;
            } else if (pluginGuiElement != null) {
                // Embed inline
                openPluginScreenButton = null;
                missingPluginLabel = null;
                this.addChild(pluginGuiElement);
            } else {
                // Plugin mod not installed on client — show placeholder
                openPluginScreenButton = null;
                missingPluginLabel = new Label(Component.translatable("gui.stockmarket.plugin_management_screen.plugin_missing").getString());
                missingPluginLabel.setTextColor(0xFFFF4444);
                this.addChild(missingPluginLabel);
            }

            int descLineH = (int)(defaultElementHeight * descriptionFontScale);
            int descTotalH = descLineH * Math.max(1, descriptionLabels.size());
            int row5Y = padding                              // top padding
                    + (defaultElementHeight + spacing)       // row 1: name
                    + (descTotalH + spacing)                 // row 2: description line(s)
                    + (40 + spacing)                         // row 3: market icons grid
                    + (defaultElementHeight + spacing);      // row 4: auto-subscribe
            int totalHeight = row5Y;
            if (openPluginScreenButton != null) {
                totalHeight += defaultElementHeight + padding;
            } else if (pluginGuiElement != null) {
                totalHeight += pluginGuiElement.getHeight() + padding;
            } else if (missingPluginLabel != null) {
                totalHeight += defaultElementHeight + padding;
            }
            this.setHeight(totalHeight);

            // Enable background for visual separation
            this.setEnableBackground(true);
            unselectedBackgroundColor = getBackgroundColor();
        }

        /** Background color while not selected (the default entry background). */
        private final int unselectedBackgroundColor;

        /**
         * Switches between the default and a darker background for the selected plugin.
         *
         * @param selected true if this entry is the selected plugin
         */
        void setSelected(boolean selected) {
            setBackgroundColor(selected ? darken(unselectedBackgroundColor) : unselectedBackgroundColor);
        }

        /** Halves the RGB channels of an ARGB color, keeping alpha (opaque enough to read as "darker"). */
        private static int darken(int argb) {
            int a = Math.max((argb >>> 24) & 0xFF, 0xCC);
            int r = ((argb >> 16) & 0xFF) / 2;
            int g = ((argb >> 8) & 0xFF) / 2;
            int b = (argb & 0xFF) / 2;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        /**
         * Any click inside this entry (on a child or on empty space) selects the plugin.
         * Hooked at the internal dispatch level so children need no extra wiring;
         * {@code isOverParent} excludes clicks on parts scrolled out of the list view.
         */
        @Override
        public boolean mouseClickedInternal(int button, boolean isOverParent) {
            boolean consumed = super.mouseClickedInternal(button, isOverParent);
            if (isEnabled() && isOverParent && isMouseOver()) {
                parentScreen.selectPlugin(this);
            }
            return consumed;
        }

        /**
         * Selects the previous/next subscribed market (wrapping), through the same
         * code path as clicking its icon. If the current market is not in this
         * plugin's list, Right selects the first and Left the last market.
         *
         * @param direction     +1 = next, -1 = previous
         * @param currentMarket the currently selected chart market, may be null
         */
        void stepMarket(int direction, @Nullable ItemID currentMarket) {
            int n = marketItemViews.size();
            if (n == 0) return;
            int index = -1;
            for (int i = 0; i < n; i++) {
                if (marketItemViews.get(i).getMarketID().equals(currentMarket)) {
                    index = i;
                    break;
                }
            }
            int next = index < 0 ? (direction > 0 ? 0 : n - 1) : Math.floorMod(index + direction, n);
            MarketItemButton target = marketItemViews.get(next);
            // Re-clicking the current market would toggle it off (single-market list) — skip
            if (target.getMarketID().equals(currentMarket)) return;
            parentScreen.selectMarketForChart(target.getMarketID());
            scrollIntoView(target);
        }

        /** Scrolls the market icon list so the given icon is fully visible. */
        private void scrollIntoView(MarketItemButton button) {
            int offset = marketItemListView.getScrollOffset();
            int viewH = marketItemListView.getHeight();
            if (button.getTop() < offset) {
                marketItemListView.setScrollOffset(button.getTop());
            } else if (button.getBottom() > offset + viewH) {
                marketItemListView.setScrollOffset(button.getBottom() - viewH);
            }
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

            // Row 1: nameLabel (left), enabledCheckBox, deleteButton, moveUp and moveDown (far right)
            int nameLabelWidth = width - checkBoxWidth - 3 * btnWidth - 4 * spacing;
            nameLabel.setBounds(padding, padding, nameLabelWidth, defaultElementHeight);
            enabledCheckBox.setBounds(nameLabel.getRight() + spacing, padding, checkBoxWidth, defaultElementHeight);
            deleteButton.setBounds(enabledCheckBox.getRight() + spacing, padding, btnWidth, defaultElementHeight);
            moveUpButton.setBounds(deleteButton.getRight() + spacing, padding, btnWidth, defaultElementHeight);
            moveDownButton.setBounds(moveUpButton.getRight() + spacing, padding, btnWidth, defaultElementHeight);

            // Row 2: description labels (full width, smaller font)
            int descLineH = (int)(defaultElementHeight * descriptionFontScale);
            int descY = nameLabel.getBottom() + spacing;
            for (Label dl : descriptionLabels) {
                dl.setBounds(padding, descY, width, descLineH);
                descY = dl.getBottom();
            }

            // Row 3: scrollable grid of market item icons, plus subscribe button
            int iconY = descY + spacing;
            int subscribeBtnWidth = 20;
            int listViewWidth = width - subscribeBtnWidth - spacing;
            int listViewHeight = 40;
            marketItemGridLayout.columns = Math.max(1, (listViewWidth + 2) / (16 + 2));
            marketItemListView.setBounds(padding, iconY, listViewWidth, listViewHeight);
            subscribeButton.setBounds(marketItemListView.getRight() + spacing, iconY, subscribeBtnWidth, 16);

            // Row 4: auto-subscribe checkbox + logger checkbox
            int row4Y = iconY + listViewHeight + spacing;
            int loggerWidth = 60;
            int autoSubWidth = width - loggerWidth - spacing;
            autoSubscribeCheckBox.setBounds(padding, row4Y, autoSubWidth, defaultElementHeight);
            loggerCheckBox.setBounds(autoSubscribeCheckBox.getRight() + spacing, row4Y, loggerWidth, defaultElementHeight);

            // Row 5: PluginGuiElement (inline) or "Open Plugin" button
            int row5Y = row4Y + defaultElementHeight + spacing;
            if (openPluginScreenButton != null) {
                openPluginScreenButton.setBounds(padding, row5Y, width, defaultElementHeight);
            } else if (pluginGuiElement != null) {
                pluginGuiElement.setBounds(padding, row5Y, width, pluginGuiElement.getHeight());
            } else if (missingPluginLabel != null) {
                missingPluginLabel.setBounds(padding, row5Y, width, defaultElementHeight);
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
         * Called when the auto-subscribe checkbox state changes.
         * Sends a settings update request to the server.
         */
        private void onAutoSubscribeChanged(Boolean value) {
            GenericPluginData data = pluginData.getGenericData();
            data.setAutoSubscribeNewMarkets(value);
            getPluginManager().requestUpdateSettings(pluginData.getInstanceID(), data).thenAccept(result -> {
                if (result.success()) {
                    parentScreen.refreshPluginList();
                }
            });
        }

        /**
         * Called when the logger checkbox state changes.
         * Sends a settings update request to the server to enable or disable logging for this plugin.
         *
         * @param enabled whether logging should be enabled
         */
        private void onLoggerChanged(Boolean enabled) {
            GenericPluginData data = pluginData.getGenericData();
            data.setLoggerEnabled(enabled);
            getPluginManager().requestUpdateSettings(pluginData.getInstanceID(), data);
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
         * Called when the delete button is clicked.
         * Sends a delete request to remove this plugin instance from the server.
         */
        private void onDeleteClicked() {
            getPluginManager().requestDeletePlugin(pluginData.getInstanceID()).thenAccept(success -> {
                if (success) {
                    parentScreen.refreshPluginList();
                }
            });
        }

        /**
         * Called when the subscribe "+" button is clicked.
         * Opens a MarketSubscribeScreen popup showing all markets with toggle subscription.
         */
        private void onSubscribeClicked() {
            List<ItemID> allMarkets = getAvailableMarkets();
            List<ItemID> subscribedMarkets = pluginData.getSubscribedMarkets();
            setScreen(new MarketSubscribeScreen(parentScreen, pluginData.getInstanceID(), allMarkets, subscribedMarkets));
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

        void startPluginDataStream() {
            if (pluginGuiElement != null) {
                pluginGuiElement.startDataStream();
            }
        }

        void setActiveMarketFromScreen(@Nullable ItemID marketID) {
            if (pluginGuiElement != null) {
                pluginGuiElement.setActiveMarket(marketID);
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
     * Popup screen for toggling market subscriptions on a plugin.
     * Shows all available markets in a scrollable list — subscribed markets are
     * highlighted green. Clicking a market toggles its subscription state.
     */
    private static class MarketSubscribeScreen extends StockMarketGuiScreen {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".plugin_management_screen.";
        private static final Component SUBSCRIBE_TITLE = Component.translatable(PREFIX + "subscribe_market_title");
        private static final Component CLOSE_TEXT = Texts.CLOSE;

        private static final int COLOR_SUBSCRIBED = 0xFF2d8a4e;
        private static final int COLOR_UNSUBSCRIBED = Button.DEFAULT_BACKGROUND_COLOR;

        private final PluginManagementScreen parentScreen;
        private final UUID pluginInstanceID;
        private final Label titleLabel;
        private final ListView marketListView;
        private final Button closeButton;
        private final List<ItemID> subscribedMarkets;
        private final List<MarketToggleEntry> toggleEntries = new ArrayList<>();

        MarketSubscribeScreen(PluginManagementScreen parentScreen, UUID pluginInstanceID,
                              List<ItemID> allMarkets, List<ItemID> subscribedMarkets) {
            super(SUBSCRIBE_TITLE, parentScreen);
            this.parentScreen = parentScreen;
            this.pluginInstanceID = pluginInstanceID;
            this.subscribedMarkets = new ArrayList<>(subscribedMarkets);

            titleLabel = new Label(SUBSCRIBE_TITLE.getString());
            titleLabel.setAlignment(Label.Alignment.CENTER);

            marketListView = new VerticalListView();
            LayoutGrid gridLayout = new LayoutGrid(1, 1, true, false, 0, 4, GuiElement.Alignment.TOP);
            marketListView.setLayout(gridLayout);

            List<ItemID> sortedMarkets = new ArrayList<>(allMarkets);
            sortedMarkets.sort(StockMarketGuiElement.MARKET_TYPE_COMPARATOR);

            for (ItemID marketID : sortedMarkets) {
                ItemStack stack = marketID.getStack();
                if (stack == null) continue;
                boolean subscribed = this.subscribedMarkets.contains(marketID);
                MarketToggleEntry entry = new MarketToggleEntry(stack, marketID, subscribed);
                toggleEntries.add(entry);
                marketListView.addChild(entry);
            }

            closeButton = new Button(CLOSE_TEXT.getString(), this::onClose);

            addElement(titleLabel);
            addElement(marketListView);
            addElement(closeButton);
        }

        private void toggleSubscription(MarketToggleEntry entry) {
            boolean newState = !entry.subscribed;
            getPluginManager().requestUpdateSubscription(pluginInstanceID, entry.marketID, newState).thenAccept(result -> {
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (result.success()) {
                        entry.subscribed = newState;
                        entry.updateColor();
                        if (newState) {
                            subscribedMarkets.add(entry.marketID);
                        } else {
                            subscribedMarkets.remove(entry.marketID);
                        }
                        parentScreen.refreshPluginList();
                    }
                });
            });
        }

        @Override
        protected void updateLayout(Gui gui) {
            int p = StockMarketGuiElement.padding;
            int s = StockMarketGuiElement.spacing;
            int eh = StockMarketGuiElement.defaultElementHeight;

            // Center a smaller panel (60% width, 70% height)
            int panelW = (int)(getWidth() * 0.6);
            int panelH = (int)(getHeight() * 0.7);
            int panelX = (getWidth() - panelW) / 2;
            int panelY = (getHeight() - panelH) / 2;

            titleLabel.setBounds(panelX, panelY, panelW, eh);
            int listH = panelH - eh - eh - 3 * s;
            marketListView.setBounds(panelX, titleLabel.getBottom() + s, panelW, listH);
            closeButton.setBounds(panelX, marketListView.getBottom() + s, panelW, eh);
        }

        @Override
        public void onClose() {
            super.onClose();
        }

        // Entry widget for a single market in the toggle list
        private class MarketToggleEntry extends StockMarketGuiElement {
            private final ItemView itemView;
            private final Label nameLabel;
            private final ItemID marketID;
            boolean subscribed;

            MarketToggleEntry(ItemStack stack, ItemID marketID, boolean subscribed) {
                super();
                this.marketID = marketID;
                this.subscribed = subscribed;
                setEnableBackground(true);

                itemView = new ItemView(stack);
                itemView.setShowTooltip(true);
                nameLabel = new Label(stack.getHoverName().getString());
                // Tooltip on the name only: the item icon keeps its own item tooltip
                PluginGuiElement.setSettingTooltip(Texts.SUBSCRIBE_TOGGLE_TOOLTIP, nameLabel);

                addChild(itemView);
                addChild(nameLabel);
                setHeight(20);
                updateColor();
            }

            void updateColor() {
                setBackgroundColor(subscribed ? COLOR_SUBSCRIBED : COLOR_UNSUBSCRIBED);
            }

            @Override
            protected void render() {}

            @Override
            protected void layoutChanged() {
                int iconSize = 16;
                int h = getHeight();
                itemView.setBounds(padding, (h - iconSize) / 2, iconSize, iconSize);
                nameLabel.setBounds(itemView.getRight() + spacing, 0, getWidth() - iconSize - padding - spacing, h);
            }

            @Override
            protected boolean mouseClickedOverElement(int button) {
                if (button == net.kroia.modutilities.gui.InputConstants.MOUSE_BUTTON_LEFT) {
                    toggleSubscription(this);
                    return true;
                }
                return false;
            }
        }
    }

    /**
     * Popup screen for selecting a plugin type to create a new instance of.
     * Uses the same centered panel layout as MarketSubscribeScreen.
     */
    private static class PluginCreatePopup extends StockMarketGuiScreen {
        private final PluginManagementScreen parentScreen;
        private final Label titleLabel;
        private final ListView pluginTypeListView;
        private final Button closeButton;

        PluginCreatePopup(PluginManagementScreen parentScreen) {
            super(Texts.ADD_PLUGIN_TITLE, parentScreen);
            this.parentScreen = parentScreen;

            titleLabel = new Label(Texts.ADD_PLUGIN_TITLE.getString());
            titleLabel.setAlignment(Label.Alignment.CENTER);

            pluginTypeListView = new VerticalListView();
            LayoutVertical layout = new LayoutVertical();
            layout.stretchX = true;
            layout.stretchY = false;
            pluginTypeListView.setLayout(layout);

            Map<String, PluginRegistryObject> registryObjects = PluginRegistry.getRegistryObjects();
            for (Map.Entry<String, PluginRegistryObject> entry : registryObjects.entrySet()) {
                PluginRegistryObject regObj = entry.getValue();
                String typeID = regObj.getPluginTypeID();
                String displayName = regObj.getPluginName();
                String description = regObj.getPluginDescription();

                Button typeButton = new Button(displayName, () -> onPluginTypeSelected(typeID));
                typeButton.setHeight(StockMarketGuiElement.defaultElementHeight);
                if (description != null && !description.isEmpty()) {
                    typeButton.setHoverTooltipSupplier(() -> description);
                    typeButton.setHoverTooltipMousePositionAlignment(GuiElement.Alignment.TOP_RIGHT);
                    typeButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
                }
                pluginTypeListView.addChild(typeButton);
            }

            closeButton = new Button(Texts.CLOSE.getString(), this::onClose);

            addElement(titleLabel);
            addElement(pluginTypeListView);
            addElement(closeButton);
        }

        private void onPluginTypeSelected(String pluginTypeID) {
            getPluginManager().requestCreatePlugin(pluginTypeID).thenAccept(success -> {
                if (success) {
                    parentScreen.refreshPluginList();
                }
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    setScreen(parentScreen);
                });
            });
        }

        @Override
        protected void updateLayout(Gui gui) {
            int p = StockMarketGuiElement.padding;
            int s = StockMarketGuiElement.spacing;
            int eh = StockMarketGuiElement.defaultElementHeight;

            int panelW = (int)(getWidth() * 0.6);
            int panelH = (int)(getHeight() * 0.7);
            int panelX = (getWidth() - panelW) / 2;
            int panelY = (getHeight() - panelH) / 2;

            titleLabel.setBounds(panelX, panelY, panelW, eh);
            int listH = panelH - eh - eh - 3 * s;
            pluginTypeListView.setBounds(panelX, titleLabel.getBottom() + s, panelW, listH);
            closeButton.setBounds(panelX, pluginTypeListView.getBottom() + s, panelW, eh);
        }

        @Override
        public void onClose() {
            super.onClose();
        }
    }

}
