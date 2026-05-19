package net.kroia.stockmarket.screen;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.gui.elements.*;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tab content element for creating markets from preset categories.
 * Shows category buttons along the top, a grid of selectable items on the left,
 * and a list of selected items with a "Create All" button on the right.
 *
 * <pre>
 * +-Categories-+--Search: ________--+-Selected Items--+
 * | CommonBlock|  [item] [item]     | - Iron Ingot    |
 * | Wood       |  [item] [item]     |   price: 10.0   |
 * | Ore        |  [item] [item]     | - Gold Ingot    |
 * | Food       |  [item] [item]     |   price: 50.0   |
 * | ...        |                    |                  |
 * |            |                    | [Clear]          |
 * |            |                    | [Create All]     |
 * +------------+--------------------+------------------+
 * </pre>
 */
public class CreateMarketTab extends StockMarketGuiElement {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".create_market_tab.";
        public static final Component TITLE = Component.translatable(PREFIX + "title");
        public static final Component CREATE_ALL = Component.translatable(PREFIX + "create_all");
        public static final Component SELECTED_ITEMS = Component.translatable(PREFIX + "selected_items");
        public static final Component SEARCH = Component.translatable(PREFIX + "search");
        public static final Component NO_PRESETS = Component.translatable(PREFIX + "no_presets");
        public static final Component PRICE_LABEL = Component.translatable(PREFIX + "price");
        public static final Component ABUNDANCE_LABEL = Component.translatable(PREFIX + "abundance");
        public static final Component ALREADY_EXISTS = Component.translatable(PREFIX + "already_exists");
        public static final Component CLEAR_SELECTION = Component.translatable(PREFIX + "clear_selection");
    }

    // Scrollable vertical list of category buttons (left panel)
    private final ListView categoryListView;
    private final List<Button> categoryButtons = new ArrayList<>();
    // Search box for filtering items within a category
    private final TextBox searchField;
    private final Label searchLabel;
    // Grid of item icons from the selected category
    private final ListView itemGridView;
    private final LayoutGrid itemGridLayout;
    // Right panel: list of selected items
    private final Label selectedLabel;
    private final ListView selectedItemsView;
    private final Button createButton;
    private final Button clearButton;

    // Currently selected category name (null = none)
    private @Nullable String selectedCategory;
    // Map of unique preset keys to preset references for batch creation
    private final LinkedHashMap<String, MarketPreset> selectedPresets = new LinkedHashMap<>();
    // Cached list of existing market ItemStacks for component-aware "already exists" detection
    private final List<ItemStack> existingMarketStacks = new ArrayList<>();
    // Dirty flags — rebuilt in render() to avoid modifying child lists during event handling
    private boolean selectedListDirty = false;
    private boolean itemGridDirty = false;
    private @Nullable Runnable onMarketsChanged;
    // Cached categories fetched asynchronously from the server
    private final List<MarketPresetCategory> cachedCategories = new ArrayList<>();

    public void setOnMarketsChanged(@Nullable Runnable callback) {
        this.onMarketsChanged = callback;
    }

    public CreateMarketTab() {
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
        searchField.setOnTextChanged(s -> rebuildItemGrid());

        // Item grid (scrollable)
        itemGridView = new VerticalListView();
        itemGridLayout = new LayoutGrid(0, 0, false, false, 0, 1, Alignment.TOP);
        itemGridView.setLayout(itemGridLayout);

        // Right panel
        selectedLabel = new Label(Texts.SELECTED_ITEMS.getString());
        selectedLabel.setAlignment(Label.Alignment.CENTER);

        selectedItemsView = new VerticalListView();
        LayoutVertical selectedLayout = new LayoutVertical();
        selectedLayout.stretchX = true;
        selectedLayout.stretchY = false;
        selectedItemsView.setLayout(selectedLayout);

        createButton = new Button(Texts.CREATE_ALL.getString(), this::onCreateAllClicked);
        createButton.setBackgroundColor(0xFF2d8a4e);
        createButton.setHoverColor(0xFF238b40);
        createButton.setPressedColor(0xFF1a6b30);

        clearButton = new Button(Texts.CLEAR_SELECTION.getString(), this::onClearSelectionClicked);

        // Add children
        addChild(categoryListView);
        addChild(searchLabel);
        addChild(searchField);
        addChild(itemGridView);
        addChild(selectedLabel);
        addChild(selectedItemsView);
        addChild(createButton);
        addChild(clearButton);

        // Fetch categories from server asynchronously
        loadCategoriesFromServer();

        // Cache existing markets
        refreshExistingMarkets();
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
     * Builds the category buttons from the cached categories.
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
     * Caches existing market ItemStacks so we can do component-aware "already exists" detection.
     * Uses full ItemStack comparison instead of just item ID strings, so that e.g.
     * "Enchanted Book (Infinity)" and "Enchanted Book (Mending)" are treated as distinct items.
     */
    private void refreshExistingMarkets() {
        existingMarketStacks.clear();
        List<ItemID> markets = getAvailableMarkets();
        if (markets != null) {
            for (ItemID id : markets) {
                ItemStack stack = id.getStack();
                if (stack != null && !stack.isEmpty()) {
                    existingMarketStacks.add(stack);
                }
            }
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
     * Rebuilds the item grid for the currently selected category, applying search filter.
     */
    private void rebuildItemGrid() {
        itemGridView.removeChilds();

        if (selectedCategory == null) return;

        MarketPresetCategory category = findCategory(selectedCategory);
        if (category == null) return;

        String searchText = searchField.getText().toLowerCase().trim();

        // Refresh existing markets cache
        refreshExistingMarkets();

        for (MarketPreset preset : category.getPresets()) {
            ItemStack stack = preset.toItemStack();
            if (stack.isEmpty()) continue;

            // Apply search filter
            String displayName = stack.getHoverName().getString().toLowerCase();
            if (!searchText.isEmpty() && !displayName.contains(searchText) && !preset.getItemId().toLowerCase().contains(searchText)) {
                continue;
            }

            String key = preset.getUniqueKey();
            // Component-aware comparison: matches item type AND components (e.g. enchantments)
            boolean alreadyExists = existingMarketStacks.stream()
                    .anyMatch(existing -> ItemStack.isSameItemSameComponents(existing, stack));
            boolean isSelected = selectedPresets.containsKey(key);

            SelectableItemView itemView = new SelectableItemView(stack, key, isSelected, alreadyExists, preset);
            itemGridView.addChild(itemView);
        }
    }

    /**
     * Rebuilds the right-side panel showing selected items with their preset info.
     */
    private void rebuildSelectedList() {
        selectedItemsView.removeChilds();

        for (Map.Entry<String, MarketPreset> entry : selectedPresets.entrySet()) {
            MarketPreset preset = entry.getValue();
            ItemStack stack = preset.toItemStack();
            if (stack.isEmpty()) continue;

            SelectedItemEntry selectedEntry = new SelectedItemEntry(stack, preset, entry.getKey());
            selectedItemsView.addChild(selectedEntry);
        }
    }

    /**
     * Called when "Create All" is clicked. Creates markets for all selected items.
     */
    private void onCreateAllClicked() {
        if (selectedPresets.isEmpty()) return;

        // Copy since we'll modify during iteration
        List<Map.Entry<String, MarketPreset>> toCreate = new ArrayList<>(selectedPresets.entrySet());

        // Collect all creation futures so we fire the callback only once when all complete
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicBoolean anyCreated = new AtomicBoolean(false);

        for (Map.Entry<String, MarketPreset> entry : toCreate) {
            String key = entry.getKey();
            MarketPreset preset = entry.getValue();
            ItemStack stack = preset.toItemStack();
            if (stack.isEmpty()) continue;

            CompletableFuture<Void> future = ItemID.getOrRegisterFromItemStackClientSide(stack).thenCompose(registeredID ->
                getMarketManager().requestCreateMarket(registeredID).thenAccept(success -> {
                    if (success) {
                        info("Created market for " + preset.getItemId());
                        anyCreated.set(true);
                        Minecraft.getInstance().execute(() -> {
                            selectedPresets.remove(key);
                            itemGridDirty = true;
                            selectedListDirty = true;
                        });
                    } else {
                        warn("Failed to create market for " + preset.getItemId());
                    }
                })
            );
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            if (anyCreated.get()) {
                Minecraft.getInstance().execute(() -> {
                    refreshExistingMarkets();
                    if (onMarketsChanged != null) onMarketsChanged.run();
                });
            }
        });
    }

    /**
     * Called when "Clear Selection" is clicked.
     */
    private void onClearSelectionClicked() {
        selectedPresets.clear();
        itemGridDirty = true;
        selectedListDirty = true;
    }

    /**
     * Toggles selection state of an item in the grid.
     */
    private void toggleItemSelection(String key, MarketPreset preset, SelectableItemView view) {
        if (selectedPresets.containsKey(key)) {
            selectedPresets.remove(key);
            view.selected = false;
        } else {
            selectedPresets.put(key, preset);
            view.selected = true;
        }
        selectedListDirty = true;
    }

    private @Nullable MarketPresetCategory findCategory(String name) {
        for (MarketPresetCategory cat : cachedCategories) {
            if (cat.getCategory().equals(name)) return cat;
        }
        return null;
    }

    @Override
    protected void render() {
        // Process deferred rebuilds safely outside event propagation
        if (itemGridDirty) {
            itemGridDirty = false;
            rebuildItemGrid();
        }
        if (selectedListDirty) {
            selectedListDirty = false;
            rebuildSelectedList();
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

        // 3-column layout: [categories | search+items | selected]
        int catWidth = width / 6;
        int rightWidth = width / 4;
        int midWidth = width - catWidth - rightWidth - 2 * spacing;

        int midX = padding + catWidth + spacing;
        int rightX = midX + midWidth + spacing;

        // Left column: category list (full height)
        categoryListView.setBounds(padding, padding, catWidth, height);

        // Middle column: search bar on top, item grid below
        int searchLabelWidth = 50;
        searchLabel.setBounds(midX, padding, searchLabelWidth, 15);
        searchField.setBounds(searchLabel.getRight() + spacing, padding, midWidth - searchLabelWidth - spacing, 15);

        int gridTop = searchLabel.getBottom() + spacing;
        int gridHeight = height - (gridTop - padding);
        itemGridView.setBounds(midX, gridTop, midWidth, gridHeight);
        int containerWidth = itemGridView.getContainerWidth();
        itemGridLayout.columns = Math.max(1, containerWidth / ItemView.DEFAULT_WIDTH);

        // Right column: selected items label, list, buttons
        selectedLabel.setBounds(rightX, padding, rightWidth, eh);

        int btnHeight = eh;
        int bottomBtnsHeight = btnHeight * 2 + spacing;
        int listTop = selectedLabel.getBottom() + spacing;
        int listHeight = height - eh - spacing - bottomBtnsHeight - spacing;
        selectedItemsView.setBounds(rightX, listTop, rightWidth, listHeight);

        clearButton.setBounds(rightX, selectedItemsView.getBottom() + spacing, rightWidth, btnHeight);
        createButton.setBounds(rightX, clearButton.getBottom() + spacing, rightWidth, btnHeight);
    }

    // ---- Inner classes ----

    /**
     * An item icon in the grid that can be toggled for selection.
     * Shows a green overlay when selected, and a dark overlay when a market already exists.
     */
    private class SelectableItemView extends ItemView {
        private final String presetKey;
        private boolean selected;
        private final boolean alreadyExists;
        private final MarketPreset preset;

        SelectableItemView(ItemStack stack, String presetKey, boolean selected, boolean alreadyExists, MarketPreset preset) {
            super(stack);
            this.presetKey = presetKey;
            this.selected = selected;
            this.alreadyExists = alreadyExists;
            this.preset = preset;

            // Build tooltip (includes enchantment/potion names via ClientPlayerUtilities)
            String tooltip = ClientPlayerUtilities.getItemDisplayText(stack);
            tooltip += "\n" + Texts.PRICE_LABEL.getString() + ": " + String.format("%.1f", preset.getDefaultPrice());
            tooltip += "\n" + Texts.ABUNDANCE_LABEL.getString() + ": " + String.format("%.1f", preset.getNaturalAbundance());
            if (alreadyExists) {
                tooltip += "\n" + Texts.ALREADY_EXISTS.getString();
            }
            String finalTooltip = tooltip;
            setHoverTooltipSupplier(() -> finalTooltip);
            setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);
            setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
            setShowTooltip(false); // Disable item tooltip, use custom one
        }

        @Override
        public void renderBackground() {
            super.renderBackground();
            if (alreadyExists) {
                // Dark overlay for existing markets
                drawRect(0, 0, getWidth(), getHeight(), 0x80000000);
            } else if (selected) {
                // Green overlay for selected items
                drawRect(0, 0, getWidth(), getHeight(), 0x6000FF00);
            }
            if (isMouseOver() && !alreadyExists) {
                drawRect(0, 0, getWidth(), getHeight(), 0x40FFFFFF);
            }
        }

        @Override
        protected boolean mouseClickedOverElement(int button) {
            if (button == 0 && !alreadyExists) {
                toggleItemSelection(presetKey, preset, this);
                return true;
            }
            return false;
        }
    }

    /**
     * Entry in the selected items list showing the item icon, name, price, and abundance.
     */
    private class SelectedItemEntry extends StockMarketGuiElement {
        private final ItemView itemView;
        private final Label nameLabel;
        private final Label infoLabel;
        private final Button removeButton;

        SelectedItemEntry(ItemStack stack, MarketPreset preset, String presetKey) {
            super();
            setEnableBackground(true);

            itemView = new ItemView(stack);
            nameLabel = new Label(stack.getHoverName().getString());
            infoLabel = new Label(
                    Texts.PRICE_LABEL.getString() + ": " + String.format("%.1f", preset.getDefaultPrice())
                    + "  " + Texts.ABUNDANCE_LABEL.getString() + ": " + String.format("%.1f", preset.getNaturalAbundance()));

            removeButton = new Button("x", () -> {
                selectedPresets.remove(presetKey);
                itemGridDirty = true;
                selectedListDirty = true;
            });
            removeButton.setBackgroundColor(0xFFe8711c);
            removeButton.setHoverColor(0xFFe04c12);

            addChild(itemView);
            addChild(nameLabel);
            addChild(infoLabel);
            addChild(removeButton);

            setHeight(2 * (defaultElementHeight + spacing));
        }

        @Override
        protected void render() {
            // No dynamic rendering
        }

        @Override
        protected void layoutChanged() {
            int w = getWidth() - 2 * padding;
            int btnSize = 16;

            itemView.setBounds(padding, padding, 16, 16);
            nameLabel.setBounds(itemView.getRight() + spacing, padding, w - 16 - btnSize - 2 * spacing, defaultElementHeight);
            removeButton.setBounds(nameLabel.getRight() + spacing, padding, btnSize, btnSize);
            infoLabel.setBounds(padding, nameLabel.getBottom() + spacing, w, defaultElementHeight);
        }
    }
}
