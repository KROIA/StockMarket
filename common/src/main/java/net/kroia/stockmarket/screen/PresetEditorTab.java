package net.kroia.stockmarket.screen;

import com.google.gson.JsonObject;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.ItemSelectionView;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.LayoutGrid;
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
 * +-Categories-+--Search: ___________________-+
 * | CommonBlock| [Icon] [name] [Price] [Abund]|
 * | Wood       | [Icon] [name] [Price] [Abund]|
 * | Ore        | ...                          |
 * | Food       |                              |
 * | ...        |                              |
 * |            | [Save Changes]               |
 * +------------+------------------------------+
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

        // Category/item management labels (hardcoded until translations are added)
        public static final String NEW_CATEGORY = "+ New";
        public static final String RENAME = "Rename";
        public static final String DELETE = "Delete";
        public static final String ADD_ITEM = "+ Add Item";
        public static final String CANCEL = "Cancel";
        public static final String CONFIRM = "OK";
    }

    // Scrollable vertical list of category buttons (left panel)
    private final ListView categoryListView;
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

    // Category management buttons (below category list)
    private final Button newCategoryButton;
    private final Button renameCategoryButton;
    private final Button deleteCategoryButton;

    // Inline text input for new/rename category (shown/hidden dynamically)
    private final TextBox categoryNameInput;
    private final Button categoryNameConfirmButton;
    private boolean showCategoryNameInput = false;
    private boolean isCategoryRename = false; // true = rename mode, false = new category mode

    // Add item mode: toggles between preset list and item picker
    private final ItemSelectionView itemSelectionView;
    private final Button addItemButton;
    private final Button cancelAddItemButton;
    private boolean addItemMode = false;

    // Track whether the current category has been modified (items added/removed)
    private boolean categoryModified = false;

    public PresetEditorTab() {
        super();
        setEnableBackground(false);

        // Category list (scrollable vertical list on the left)
        categoryListView = new VerticalListView();
        LayoutVertical catLayout = new LayoutVertical();
        catLayout.stretchX = true;
        catLayout.stretchY = false;
        categoryListView.setLayout(catLayout);

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

        // Category management buttons
        newCategoryButton = new Button(Texts.NEW_CATEGORY, this::onNewCategoryClicked);
        renameCategoryButton = new Button(Texts.RENAME, this::onRenameCategoryClicked);
        deleteCategoryButton = new Button(Texts.DELETE, this::onDeleteCategoryClicked);
        deleteCategoryButton.setBackgroundColor(0xFFc0392b);
        deleteCategoryButton.setHoverColor(0xFFe74c3c);

        // Inline category name input (hidden by default)
        categoryNameInput = new TextBox();
        categoryNameInput.setEnabled(false);
        categoryNameConfirmButton = new Button(Texts.CONFIRM, this::onCategoryNameConfirmed);
        categoryNameConfirmButton.setEnabled(false);

        // Item selection view for adding items (hidden by default)
        itemSelectionView = new ItemSelectionView(this::onItemSelectedFromPicker);
        itemSelectionView.setEnabled(false);

        // Add/Cancel item buttons
        addItemButton = new Button(Texts.ADD_ITEM, this::onAddItemClicked);
        addItemButton.setBackgroundColor(0xFF2980b9);
        addItemButton.setHoverColor(0xFF3498db);
        cancelAddItemButton = new Button(Texts.CANCEL, this::onCancelAddItemClicked);
        cancelAddItemButton.setEnabled(false);

        // Add children
        addChild(categoryListView);
        addChild(searchLabel);
        addChild(searchField);
        addChild(itemGridView);
        addChild(saveButton);
        addChild(newCategoryButton);
        addChild(renameCategoryButton);
        addChild(deleteCategoryButton);
        addChild(categoryNameInput);
        addChild(categoryNameConfirmButton);
        addChild(itemSelectionView);
        addChild(addItemButton);
        addChild(cancelAddItemButton);

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
        categoryListView.removeChilds();
        categoryButtons.clear();

        for (MarketPresetCategory category : cachedCategories) {
            Button btn = new Button(category.getCategory(), () -> onCategorySelected(category.getCategory()));
            btn.setHeight(defaultElementHeight);
            categoryButtons.add(btn);
            categoryListView.addChild(btn);
        }

        // Auto-select first category if available
        if (!cachedCategories.isEmpty()) {
            selectedCategory = cachedCategories.get(0).getCategory();
            rebuildItemGrid();
        }
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

            // Apply search filter on display name, registry ID, and component text (enchantments, potions, etc.)
            String displayName = stack.getHoverName().getString().toLowerCase();
            if (!searchText.isEmpty()
                    && !displayName.contains(searchText)
                    && !preset.getItemId().toLowerCase().contains(searchText)
                    && !ClientPlayerUtilities.getItemDisplayText(stack).toLowerCase().contains(searchText)) {
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

    // ---- Category management ----

    private void onNewCategoryClicked() {
        isCategoryRename = false;
        categoryNameInput.setText("");
        showCategoryNameInput(true);
    }

    private void onRenameCategoryClicked() {
        if (selectedCategory == null) return;
        isCategoryRename = true;
        categoryNameInput.setText(selectedCategory);
        showCategoryNameInput(true);
    }

    private void onDeleteCategoryClicked() {
        if (selectedCategory == null) return;
        IAsyncPresetManager pm = getPresetManager();
        if (pm == null) return;

        String toDelete = selectedCategory;
        pm.deleteCategoryAsync(toDelete).thenAccept(success -> {
            Minecraft.getInstance().execute(() -> {
                if (success) {
                    cachedCategories.removeIf(c -> c.getCategory().equals(toDelete));
                    if (selectedCategory != null && selectedCategory.equals(toDelete)) {
                        selectedCategory = cachedCategories.isEmpty() ? null : cachedCategories.get(0).getCategory();
                    }
                    editedPresets.clear();
                    categoryModified = false;
                    buildCategoryButtons();
                    rebuildItemGrid();
                    info("Deleted category: " + toDelete);
                } else {
                    warn("Failed to delete category: " + toDelete);
                }
            });
        });
    }

    private void onCategoryNameConfirmed() {
        String name = categoryNameInput.getText().trim();
        if (name.isEmpty()) return;

        IAsyncPresetManager pm = getPresetManager();
        if (pm == null) return;

        if (isCategoryRename) {
            if (selectedCategory == null) return;
            String oldName = selectedCategory;
            pm.renameCategoryAsync(oldName, name).thenAccept(success -> {
                Minecraft.getInstance().execute(() -> {
                    if (success) {
                        MarketPresetCategory cat = findCategory(oldName);
                        if (cat != null) cat.setCategory(name);
                        selectedCategory = name;
                        buildCategoryButtons();
                        info("Renamed category to: " + name);
                    } else {
                        warn("Failed to rename category");
                    }
                    showCategoryNameInput(false);
                });
            });
        } else {
            MarketPresetCategory newCat = new MarketPresetCategory(name, new ArrayList<>());
            pm.saveCategoryAsync(newCat).thenAccept(success -> {
                Minecraft.getInstance().execute(() -> {
                    if (success) {
                        cachedCategories.add(newCat);
                        selectedCategory = name;
                        editedPresets.clear();
                        categoryModified = false;
                        buildCategoryButtons();
                        rebuildItemGrid();
                        info("Created category: " + name);
                    } else {
                        warn("Failed to create category");
                    }
                    showCategoryNameInput(false);
                });
            });
        }
    }

    private void showCategoryNameInput(boolean show) {
        showCategoryNameInput = show;
        categoryNameInput.setEnabled(show);
        categoryNameConfirmButton.setEnabled(show);
        newCategoryButton.setEnabled(!show);
        renameCategoryButton.setEnabled(!show);
        deleteCategoryButton.setEnabled(!show);
        layoutChanged();
    }

    // ---- Item add/remove ----

    private void onAddItemClicked() {
        addItemMode = true;
        itemSelectionView.setEnabled(true);
        cancelAddItemButton.setEnabled(true);
        itemGridView.setEnabled(false);
        addItemButton.setEnabled(false);
        searchLabel.setEnabled(false);
        searchField.setEnabled(false);
        layoutChanged();
    }

    private void onCancelAddItemClicked() {
        exitAddItemMode();
    }

    private void exitAddItemMode() {
        addItemMode = false;
        itemSelectionView.setEnabled(false);
        cancelAddItemButton.setEnabled(false);
        itemGridView.setEnabled(true);
        addItemButton.setEnabled(true);
        searchLabel.setEnabled(true);
        searchField.setEnabled(true);
        layoutChanged();
    }

    private void onItemSelectedFromPicker(ItemStack stack) {
        if (selectedCategory == null || stack == null || stack.isEmpty()) return;

        MarketPresetCategory category = findCategory(selectedCategory);
        if (category == null) return;

        // Build a MarketPreset from the selected ItemStack.
        // serializeItemStack() internally normalizes the stack via BankSystem's
        // VolatileItemComponents.normalize(), so volatile components (e.g. TFC's
        // tfc:food creation date on creative-tab/JEI picker stacks) are stripped
        // before they can be frozen into the persisted preset JSON.
        JsonObject serialized = MarketPreset.serializeItemStack(stack);
        String itemId = serialized.get("id").getAsString();
        JsonObject components = serialized.has("components") ? serialized.getAsJsonObject("components") : null;
        MarketPreset newPreset = new MarketPreset(itemId, components, 10.0f, 10.0f);

        // Add to the category's preset list
        category.getPresets().add(newPreset);
        categoryModified = true;

        exitAddItemMode();
        rebuildItemGrid();
    }

    private void removePresetFromCategory(MarketPreset preset) {
        if (selectedCategory == null) return;
        MarketPresetCategory category = findCategory(selectedCategory);
        if (category == null) return;

        category.getPresets().removeIf(p -> p.getUniqueKey().equals(preset.getUniqueKey()));
        editedPresets.remove(preset.getUniqueKey());
        categoryModified = true;
        itemGridDirty = true;
    }

    // ---- Save ----

    /**
     * Saves the current category with any price/abundance edits and structural changes
     * (added/removed items) to the server.
     */
    private void onSaveClicked() {
        if (selectedCategory == null) return;

        // Nothing to save if no edits and no structural changes
        if (editedPresets.isEmpty() && !categoryModified) return;

        MarketPresetCategory category = findCategory(selectedCategory);
        if (category == null) return;

        // Apply any pending price/abundance edits to the cached category
        for (Map.Entry<String, MarketPreset> entry : editedPresets.entrySet()) {
            List<MarketPreset> presets = category.getPresets();
            for (int i = 0; i < presets.size(); i++) {
                if (presets.get(i).getUniqueKey().equals(entry.getKey())) {
                    presets.set(i, entry.getValue());
                    break;
                }
            }
        }

        IAsyncPresetManager pm = getPresetManager();
        if (pm == null) return;

        pm.saveCategoryAsync(category).thenAccept(success -> {
            Minecraft.getInstance().execute(() -> {
                if (success) {
                    editedPresets.clear();
                    categoryModified = false;
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

        // 2-column layout: [categories + buttons | content]
        int catWidth = width / 6;
        int rightWidth = width - catWidth - spacing;
        int rightX = padding + catWidth + spacing;

        // Left column: category list + management buttons at bottom
        int catBtnHeight = eh;
        int catBtnAreaHeight;
        if (showCategoryNameInput) {
            catBtnAreaHeight = catBtnHeight + spacing + catBtnHeight; // text input + confirm
            int catListHeight = height - catBtnAreaHeight - spacing;
            categoryListView.setBounds(padding, padding, catWidth, catListHeight);

            int btnY = categoryListView.getBottom() + spacing;
            categoryNameInput.setBounds(padding, btnY, catWidth, catBtnHeight);
            categoryNameConfirmButton.setBounds(padding, categoryNameInput.getBottom() + spacing, catWidth, catBtnHeight);
        } else {
            catBtnAreaHeight = catBtnHeight * 3 + spacing * 2; // new + rename + delete
            int catListHeight = height - catBtnAreaHeight - spacing;
            categoryListView.setBounds(padding, padding, catWidth, catListHeight);

            int btnY = categoryListView.getBottom() + spacing;
            newCategoryButton.setBounds(padding, btnY, catWidth, catBtnHeight);
            renameCategoryButton.setBounds(padding, newCategoryButton.getBottom() + spacing, catWidth, catBtnHeight);
            deleteCategoryButton.setBounds(padding, renameCategoryButton.getBottom() + spacing, catWidth, catBtnHeight);
        }

        // Right column
        if (addItemMode) {
            // Item picker mode: full area for ItemSelectionView + cancel button
            int cancelHeight = eh;
            cancelAddItemButton.setBounds(rightX, padding, rightWidth, cancelHeight);
            itemSelectionView.setBounds(rightX, cancelAddItemButton.getBottom() + spacing, rightWidth, height - cancelHeight - spacing);
        } else {
            // Normal mode: search bar + item grid + add item + save
            int searchLabelWidth = 50;
            searchLabel.setBounds(rightX, padding, searchLabelWidth, 15);
            searchField.setBounds(searchLabel.getRight() + spacing, padding, rightWidth - searchLabelWidth - spacing, 15);

            int btnHeight = eh;
            int bottomBtnsHeight = btnHeight * 2 + spacing; // add item + save
            saveButton.setBounds(rightX, padding + height - btnHeight, rightWidth, btnHeight);
            addItemButton.setBounds(rightX, saveButton.getTop() - spacing - btnHeight, rightWidth, btnHeight);

            int gridTop = searchLabel.getBottom() + spacing;
            int gridHeight = addItemButton.getTop() - spacing - gridTop;
            itemGridView.setBounds(rightX, gridTop, rightWidth, gridHeight);
        }
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
        private final Button removeButton;
        private final MarketPreset originalPreset;
        private final String presetKey;

        PresetEntryWidget(ItemStack stack, MarketPreset preset, float currentPrice, float currentAbundance) {
            super();
            this.originalPreset = preset;
            this.presetKey = preset.getUniqueKey();
            setEnableBackground(true);

            itemView = new ItemView(stack);
            itemView.setShowTooltip(false);
            // Custom tooltip with enchantment/potion names via ClientPlayerUtilities
            String itemTooltip = ClientPlayerUtilities.getItemDisplayText(stack);
            itemView.setHoverTooltipSupplier(() -> itemTooltip);
            itemView.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
            itemView.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);

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

            removeButton = new Button("x", () -> removePresetFromCategory(originalPreset));
            removeButton.setBackgroundColor(0xFFe8711c);
            removeButton.setHoverColor(0xFFe04c12);

            addChild(itemView);
            addChild(nameLabel);
            addChild(priceLabel);
            addChild(priceTextBox);
            addChild(abundanceLabel);
            addChild(abundanceTextBox);
            addChild(removeButton);

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
            int removeBtnSize = 16;

            int nameLabelWidth = w / 4;
            int priceLabelWidth = 50;
            int abundanceLabelWidth = 65;
            int fieldWidth = (w - iconSize - nameLabelWidth - priceLabelWidth - abundanceLabelWidth - removeBtnSize - 6 * spacing) / 2;

            itemView.setBounds(padding, (h - iconSize) / 2, iconSize, iconSize);
            nameLabel.setBounds(itemView.getRight() + spacing, 1, nameLabelWidth, fieldHeight);

            priceLabel.setBounds(nameLabel.getRight() + spacing, 1, priceLabelWidth, fieldHeight);
            priceTextBox.setBounds(priceLabel.getRight() + spacing, 1, fieldWidth, fieldHeight);

            abundanceLabel.setBounds(priceTextBox.getRight() + spacing, 1, abundanceLabelWidth, fieldHeight);
            abundanceTextBox.setBounds(abundanceLabel.getRight() + spacing, 1, fieldWidth, fieldHeight);

            removeButton.setBounds(abundanceTextBox.getRight() + spacing, (h - removeBtnSize) / 2, removeBtnSize, removeBtnSize);
        }
    }

    // ---- Utility ----

    /**
     * Formats a float for display in a text field, stripping trailing zeros.
     */
    private static String formatFloat(float value) {
        // Use up to 4 decimal places, strip trailing zeros
        String s = String.format(Locale.ROOT, "%.4f", value);
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
