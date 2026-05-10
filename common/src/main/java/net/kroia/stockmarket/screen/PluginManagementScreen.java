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
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
    }

    private final StockMarketGuiScreen parent;
    private final Label titleLabel;
    private final ListView listView;
    private final List<PluginEntryWidget> entryWidgets = new ArrayList<>();

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

        listView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        listView.setLayout(layout);

        addElement(titleLabel);
        addElement(listView);

        // Request the plugin list from the server and rebuild the UI
        getPluginManager().requestPluginList().thenAccept(this::rebuildPluginList);
    }

    @Override
    public void onClose() {
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

        titleLabel.setBounds(padding, padding, width, StockMarketGuiElement.defaultElementHeight);
        listView.setBounds(padding, titleLabel.getBottom() + spacing, width,
                height - (titleLabel.getBottom() + spacing) + padding);
    }

    /**
     * Rebuilds the scrollable plugin list from the given data.
     * Removes all existing entries and creates new PluginEntryWidgets.
     *
     * @param plugins the list of plugin sync data from the server
     */
    public void rebuildPluginList(List<PluginSyncData> plugins) {
        // Remove existing entry widgets from the listView
        for (PluginEntryWidget entry : entryWidgets) {
            listView.removeChild(entry);
        }
        entryWidgets.clear();

        // Create new entries for each plugin
        for (PluginSyncData data : plugins) {
            PluginEntryWidget entry = new PluginEntryWidget(data, this);
            entryWidgets.add(entry);
            listView.addChild(entry);
        }
    }

    /**
     * Re-requests the plugin list from the server and rebuilds the UI.
     */
    public void refreshPluginList() {
        getPluginManager().requestPluginList().thenAccept(this::rebuildPluginList);
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
        private final List<ItemView> marketItemViews = new ArrayList<>();
        private final CheckBox enabledCheckBox;
        private final Button moveUpButton;
        private final Button moveDownButton;
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

            // Item icons for subscribed markets (positioned manually in layoutChanged)
            for (ItemID marketID : data.getSubscribedMarkets()) {
                ItemStack stack = marketID.getStack();
                if (stack != null) {
                    ItemView itemView = new ItemView();
                    itemView.setItemStack(stack);
                    itemView.setSize(16, 16);
                    marketItemViews.add(itemView);
                }
            }

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
            for (ItemView iv : marketItemViews) {
                this.addChild(iv);
            }
            this.addChild(enabledCheckBox);
            this.addChild(moveUpButton);
            this.addChild(moveDownButton);

            // Create the plugin GUI element from the registry (no ClientPlugin intermediary needed)
            this.pluginGuiElement = PluginRegistry.createGuiElement(data.getPluginTypeID());

            // Pass sync data (subscribed markets, metadata) to the GUI element
            if (pluginGuiElement != null) {
                pluginGuiElement.setPluginSyncData(data);
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
                baseHeight += 60 + spacing;
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

            // Row 3: market item icons (positioned horizontally)
            int iconX = padding;
            int iconY = descriptionLabel.getBottom() + spacing;
            for (ItemView iv : marketItemViews) {
                iv.setBounds(iconX, iconY, 16, 16);
                iconX += 16 + 2; // 16px icon + 2px spacing
            }

            // Row 4: PluginGuiElement (inline) or "Open Plugin" button
            int row4Y = iconY + 18 + spacing;
            if (openPluginScreenButton != null) {
                openPluginScreenButton.setBounds(padding, row4Y, width, defaultElementHeight);
            } else if (pluginGuiElement != null) {
                pluginGuiElement.setBounds(padding, row4Y, width, 60);
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
    }
}
