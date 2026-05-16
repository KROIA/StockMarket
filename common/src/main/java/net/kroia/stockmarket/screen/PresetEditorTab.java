package net.kroia.stockmarket.screen;

import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.api.preset.IAsyncPresetManager;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPreset;
import net.kroia.stockmarket.stockmarket.market.preset.MarketPresetCategory;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Tab content element for editing market preset prices and abundance values.
 * Shows category buttons along the top, a search bar, a scrollable list of
 * preset entries with editable text fields, and a save button at the bottom.
 *
 * <pre>
 * +--[ Ore ]--[ Food ]--[ CommonBlock ]--[ ... ]-----+
 * +--[ Search: _____________________ ]---------------+
 * +--Item Grid (scrollable)-------------------------+
 * | [Icon] [item name]    [Price: ___] [Abund: ___] |
 * | [Icon] [item name]    [Price: ___] [Abund: ___] |
 * | ...                                              |
 * +--[ Save Changes ]-------------------------------+
 * </pre>
 */
public class PresetEditorTab extends StockMarketGuiElement {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".preset_editor_tab.";
        public static final Component SEARCH = Component.translatable(PREFIX + "search");
        public static final Component SAVE = Component.translatable(PREFIX + "save");
        public static final Component PRICE = Component.translatable(PREFIX + "price");
        public static final Component ABUNDANCE = Component.translatable(PREFIX + "abundance");
        public static final Component ABUNDANCE_TOOLTIP = Component.translatable(PREFIX + "abundance_tooltip");
        public static final Component SAVED_OK = Component.translatable(PREFIX + "saved_ok");
        public static final Component SAVE_FAILED = Component.translatable(PREFIX + "save_failed");
    }

    // Category buttons along the top row
    private final List<Button> categoryButtons = new ArrayList<>();
    // Search bar
    private final Label searchLabel;
    private final TextBox searchField;
    // Scrollable list of preset entry widgets
    private final ListView itemGridView;
    // Save button at the bottom
    private final Button saveButton;

    // Currently selected category name (null = none)
    private @Nullable String selectedCategory;
    // Dirty flag for deferred grid rebuild
    private boolean itemGridDirty = false;

    // Tracks edited presets: uniqueKey -> edited MarketPreset with updated price/abundance
    private final Map<String, MarketPreset> editedPresets = new HashMap<>();
    // Cached categories fetched asynchronously from the server
    private final List<MarketPresetCategory> cachedCategories = new ArrayList<>();

    public PresetEditorTab() {
        super();
        setEnableBackground(false);

        // Search bar
        searchLabel = new Label(Texts.SEARCH.getString());
        searchLabel.setAlignment(Label.Alignment.RIGHT);
        searchField = new TextBox();
        searchField.setOnTextChanged(s -> itemGridDirty = true);

        // Scrollable item list
        itemGridView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        itemGridView.setLayout(layout);

        // Save button
        saveButton = new Button(Texts.SAVE.getString(), this::onSaveClicked);
        saveButton.setBackgroundColor(0xFF2d8a4e);
        saveButton.setHoverColor(0xFF238b40);
        saveButton.setPressedColor(0xFF1a6b30);

        // Add children
        addChild(searchLabel);
        addChild(searchField);
        addChild(itemGridView);
        addChild(saveButton);

        // Fetch categories from server asynchronously
        loadCategoriesFromServer();
    }

    /**
     * Fetches categories from the server asynchronously and builds the UI when data arrives.
     */
    private void loadCategoriesFromServer() {
        IAsyncPresetManager pm = getPresetManager();
        if (pm == null) return;
        pm.getCategoriesAsync().thenAccept(categories -> {
            Minecraft.getInstance().execute(() -> {
                cachedCategories.clear();
                cachedCategories.addAll(categories);
                buildCategoryButtons();
            });
        });
    }

    /**
     * Builds category buttons from the cached categories.
     */
    private void buildCategoryButtons() {
        // Remove old category buttons
        for (Button btn : categoryButtons) {
            removeChild(btn);
        }
        categoryButtons.clear();

        for (MarketPresetCategory category : cachedCategories) {
            Button btn = new Button(category.getCategory(), () -> onCategorySelected(category.getCategory()));
            categoryButtons.add(btn);
            addChild(btn);
        }

        // Auto-select first category if available
        if (!cachedCategories.isEmpty()) {
            selectedCategory = cachedCategories.get(0).getCategory();
            rebuildItemGrid();
        }
        layoutChanged();
    }

    /**
     * Called when a category button is clicked.
     */
    private void onCategorySelected(String category) {
        selectedCategory = category;
        searchField.setText("");
        rebuildItemGrid();
    }

    /**
     * Rebuilds the item grid for the currently selected category, applying the search filter.
     */
    private void rebuildItemGrid() {
        itemGridView.removeChilds();

        if (selectedCategory == null) return;

        MarketPresetCategory category = findCategory(selectedCategory);
        if (category == null) return;

        String searchText = searchField.getText().toLowerCase().trim();

        for (MarketPreset preset : category.getPresets()) {
            ItemStack stack = preset.toItemStack();
            if (stack.isEmpty()) continue;

            // Apply search filter on display name and registry ID
            String displayName = stack.getHoverName().getString().toLowerCase();
            if (!searchText.isEmpty()
                    && !displayName.contains(searchText)
                    && !preset.getItemId().toLowerCase().contains(searchText)) {
                continue;
            }

            // Use edited values if available, otherwise use original preset values
            String key = preset.getUniqueKey();
            float price = preset.getDefaultPrice();
            float abundance = preset.getNaturalAbundance();
            MarketPreset edited = editedPresets.get(key);
            if (edited != null) {
                price = edited.getDefaultPrice();
                abundance = edited.getNaturalAbundance();
            }

            PresetEntryWidget entry = new PresetEntryWidget(stack, preset, price, abundance);
            itemGridView.addChild(entry);
        }
    }

    /**
     * Collects all edited preset values and sends them to the server for persistence.
     */
    private void onSaveClicked() {
        if (editedPresets.isEmpty()) return;

        List<MarketPreset> allPresets = new ArrayList<>(editedPresets.values());
        if (allPresets.isEmpty()) return;

        IAsyncPresetManager pm = getPresetManager();
        if (pm == null) return;

        pm.updatePresetsAsync(allPresets).thenAccept(success -> {
            Minecraft.getInstance().execute(() -> {
                if (success) {
                    // Update cached categories with the new values
                    for (MarketPreset updated : allPresets) {
                        String key = updated.getUniqueKey();
                        for (MarketPresetCategory cat : cachedCategories) {
                            List<MarketPreset> presets = cat.getPresets();
                            for (int i = 0; i < presets.size(); i++) {
                                if (presets.get(i).getUniqueKey().equals(key)) {
                                    presets.set(i, updated);
                                    break;
                                }
                            }
                        }
                    }
                    editedPresets.clear();
                    info(Texts.SAVED_OK.getString());
                } else {
                    warn(Texts.SAVE_FAILED.getString());
                }
            });
        });
    }

    private @Nullable MarketPresetCategory findCategory(String name) {
        for (MarketPresetCategory cat : cachedCategories) {
            if (cat.getCategory().equals(name)) return cat;
        }
        return null;
    }

    @Override
    protected void render() {
        // Process deferred grid rebuild safely outside event propagation
        if (itemGridDirty) {
            itemGridDirty = false;
            rebuildItemGrid();
        }

        // Highlight the selected category button
        for (Button btn : categoryButtons) {
            if (btn.getText().equals(selectedCategory)) {
                btn.setBackgroundColor(0xFF4a6fa5);
            } else {
                btn.setBackgroundColor(Button.DEFAULT_BACKGROUND_COLOR);
            }
        }
    }

    @Override
    protected void layoutChanged() {
        int width = getWidth() - 2 * padding;
        int height = getHeight() - 2 * padding;
        int eh = defaultElementHeight;

        // Row 0: Category buttons (horizontally arranged)
        int catX = padding;
        int catY = padding;
        int catBtnWidth = categoryButtons.isEmpty() ? 0
                : Math.min(80, (width - spacing * (categoryButtons.size() - 1)) / categoryButtons.size());
        for (Button btn : categoryButtons) {
            btn.setBounds(catX, catY, catBtnWidth, eh);
            catX += catBtnWidth + spacing;
        }

        // Row 1: Search bar below categories
        int searchY = catY + eh + spacing;
        int searchLabelWidth = width / 6;
        searchLabel.setBounds(padding, searchY, searchLabelWidth, 15);
        searchField.setBounds(searchLabel.getRight() + spacing, searchY, width - searchLabelWidth - spacing, 15);

        // Bottom: Save button
        int btnHeight = eh;
        saveButton.setBounds(padding, padding + height - btnHeight, width, btnHeight);

        // Content area: scrollable item grid between search bar and save button
        int contentTop = searchY + 15 + spacing;
        int contentHeight = (padding + height - btnHeight - spacing) - contentTop;
        itemGridView.setBounds(padding, contentTop, width, contentHeight);
    }

    // ---- Inner class: single preset entry with editable price and abundance ----

    /**
     * A row in the preset editor grid showing an item icon, name, and editable
     * price/abundance text fields.
     */
    private class PresetEntryWidget extends StockMarketGuiElement {
        private final ItemView itemView;
        private final Label nameLabel;
        private final Label priceLabel;
        private final TextBox priceTextBox;
        private final Label abundanceLabel;
        private final TextBox abundanceTextBox;
        private final MarketPreset originalPreset;
        private final String presetKey;

        PresetEntryWidget(ItemStack stack, MarketPreset preset, float currentPrice, float currentAbundance) {
            super();
            this.originalPreset = preset;
            this.presetKey = preset.getUniqueKey();
            setEnableBackground(true);

            itemView = new ItemView(stack);
            itemView.setShowTooltip(false);

            nameLabel = new Label(stack.getHoverName().getString());

            priceLabel = new Label(Texts.PRICE.getString());
            priceLabel.setAlignment(Label.Alignment.RIGHT);

            priceTextBox = new TextBox();
            priceTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));
            priceTextBox.setText(formatFloat(currentPrice));
            priceTextBox.setOnTextChanged(this::onPriceChanged);

            abundanceLabel = new Label(Texts.ABUNDANCE.getString());
            abundanceLabel.setAlignment(Label.Alignment.RIGHT);
            abundanceLabel.setHoverTooltipSupplier(Texts.ABUNDANCE_TOOLTIP::getString);
            abundanceLabel.setHoverTooltipMousePositionAlignment(Alignment.BOTTOM);
            abundanceLabel.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

            abundanceTextBox = new TextBox();
            abundanceTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 6));
            abundanceTextBox.setText(formatFloat(currentAbundance));
            abundanceTextBox.setOnTextChanged(this::onAbundanceChanged);

            addChild(itemView);
            addChild(nameLabel);
            addChild(priceLabel);
            addChild(priceTextBox);
            addChild(abundanceLabel);
            addChild(abundanceTextBox);

            setHeight(24);
        }

        private void onPriceChanged(String text) {
            float price = parseFloat(text, -1f);
            if (price < 0) return;
            float abundance = parseFloat(abundanceTextBox.getText(), originalPreset.getNaturalAbundance());
            editedPresets.put(presetKey, new MarketPreset(
                    originalPreset.getItemId(), originalPreset.components(), price, abundance));
        }

        private void onAbundanceChanged(String text) {
            float abundance = parseFloat(text, -1f);
            if (abundance < 0) return;
            float price = parseFloat(priceTextBox.getText(), originalPreset.getDefaultPrice());
            editedPresets.put(presetKey, new MarketPreset(
                    originalPreset.getItemId(), originalPreset.components(), price, abundance));
        }

        @Override
        protected void render() {
        }

        @Override
        protected void layoutChanged() {
            int w = getWidth() - 2 * padding;
            int h = getHeight();
            int iconSize = 16;
            int fieldHeight = h - 2;

            int nameLabelWidth = w / 4;
            int priceLabelWidth = 50;
            int abundanceLabelWidth = 65;
            int fieldWidth = (w - iconSize - nameLabelWidth - priceLabelWidth - abundanceLabelWidth - 5 * spacing) / 2;

            itemView.setBounds(padding, (h - iconSize) / 2, iconSize, iconSize);
            nameLabel.setBounds(itemView.getRight() + spacing, 1, nameLabelWidth, fieldHeight);

            priceLabel.setBounds(nameLabel.getRight() + spacing, 1, priceLabelWidth, fieldHeight);
            priceTextBox.setBounds(priceLabel.getRight() + spacing, 1, fieldWidth, fieldHeight);

            abundanceLabel.setBounds(priceTextBox.getRight() + spacing, 1, abundanceLabelWidth, fieldHeight);
            abundanceTextBox.setBounds(abundanceLabel.getRight() + spacing, 1, fieldWidth, fieldHeight);
        }
    }

    // ---- Utility ----

    /**
     * Formats a float for display in a text field, stripping trailing zeros.
     */
    private static String formatFloat(float value) {
        // Use up to 4 decimal places, strip trailing zeros
        String s = String.format("%.4f", value);
        // Remove trailing zeros after decimal point
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        return s;
    }

    /**
     * Parses a float from a string, returning defaultVal on failure.
     */
    private static float parseFloat(String text, float defaultVal) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
