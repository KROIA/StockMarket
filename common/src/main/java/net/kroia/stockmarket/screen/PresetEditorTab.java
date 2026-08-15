package net.kroia.stockmarket.screen;

import com.google.gson.JsonObject;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.ItemSelectionView;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.api.preset.IAsyncPresetManager;
import net.kroia.modutilities.gui.layout.LayoutGrid;
import net.kroia.modutilities.gui.elements.base.GuiElement;
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

        // T-152: label for the "+ From Inventory" button — opens the inventory picker mode.
        public static final Component ADD_FROM_INVENTORY = Component.translatable(
                "gui." + StockMarketMod.MOD_ID + ".preset_editor.add_from_inventory");

        // T-154: tooltip for the small X close button on picker headers.
        public static final Component CLOSE_PICKER = Component.translatable(
                "gui." + StockMarketMod.MOD_ID + ".preset_editor.close_picker");
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

    // T-152: "Add from inventory" mode — picks any ItemStack the player is currently
    // holding (with real components) and adds it to the selected category. Mirrors
    // addItemMode's enter/exit contract.
    private final VerticalListView inventoryPickerView;
    private final Button addFromInventoryButton;
    private final Button cancelAddFromInventoryButton;
    private boolean addFromInventoryMode = false;

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
        // T-154: Cancel became a small square "X" button in the picker header row.
        cancelAddItemButton = new Button("X", this::onCancelAddItemClicked);
        cancelAddItemButton.setHoverTooltipSupplier(Texts.CLOSE_PICKER::getString);
        cancelAddItemButton.setEnabled(false);

        // T-152: inventory picker. Container stacks two sub-grids vertically:
        // 3x9 main inventory on top, 1x9 hotbar below, with a small vanilla-style
        // vertical gap between them. Each sub-grid is built with its own LayoutGrid
        // in rebuildInventoryPicker().
        inventoryPickerView = new VerticalListView();
        inventoryPickerView.setEnableBackground(true);
        inventoryPickerView.setLayout(new LayoutVertical(0, 4, false, false));
        inventoryPickerView.setEnabled(false);

        addFromInventoryButton = new Button(Texts.ADD_FROM_INVENTORY.getString(), this::onAddFromInventoryClicked);
        addFromInventoryButton.setBackgroundColor(0xFF2980b9);
        addFromInventoryButton.setHoverColor(0xFF3498db);
        // T-154: Cancel became a small square "X" button in the picker header row.
        cancelAddFromInventoryButton = new Button("X", this::onCancelAddFromInventoryClicked);
        cancelAddFromInventoryButton.setHoverTooltipSupplier(Texts.CLOSE_PICKER::getString);
        cancelAddFromInventoryButton.setEnabled(false);

        // T-155 fix B: red background on the close "X" buttons so the destructive
        // close action stands out. Matches deleteCategoryButton's red palette.
        cancelAddItemButton.setBackgroundColor(0xFFc0392b);
        cancelAddItemButton.setHoverColor(0xFFe74c3c);
        cancelAddFromInventoryButton.setBackgroundColor(0xFFc0392b);
        cancelAddFromInventoryButton.setHoverColor(0xFFe74c3c);

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
        addChild(inventoryPickerView);
        addChild(addFromInventoryButton);
        addChild(cancelAddFromInventoryButton);

        // T-123 (untrusted slave gate): every editing input on this tab writes
        // to the master via PresetUpdateRequest / preset category mutations.
        // Disable them all so a user on an untrusted slave sees the presets
        // read-only (they can still browse categories and see current values).
        if (isUntrustedSlave()) {
            saveButton.setEnabled(false);
            newCategoryButton.setEnabled(false);
            renameCategoryButton.setEnabled(false);
            deleteCategoryButton.setEnabled(false);
            addItemButton.setEnabled(false);
            addFromInventoryButton.setEnabled(false);
        }

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
            Minecraft.getInstance().tell(() -> {
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
            Minecraft.getInstance().tell(() -> {
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
                Minecraft.getInstance().tell(() -> {
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
                Minecraft.getInstance().tell(() -> {
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
        // T-154 mutual exclusion: never allow both pickers open at once.
        if (addFromInventoryMode) exitAddFromInventoryMode();
        addItemMode = true;
        itemSelectionView.setEnabled(true);
        cancelAddItemButton.setEnabled(true);
        itemGridView.setEnabled(false);
        addItemButton.setEnabled(false);
        addFromInventoryButton.setEnabled(false);
        searchLabel.setEnabled(false);
        searchField.setEnabled(false);
        saveButton.setEnabled(false);
        layoutChanged();
    }

    private void onCancelAddItemClicked() {
        // T-154 fix #1: defer mutation. Clicking Cancel dispatches through
        // GuiElement.mouseClickedInternal which is iterating child list; exiting
        // the mode calls removeChilds() on the picker which would CME.
        // tell() unconditionally enqueues for the next tick — execute() would
        // run inline on the render thread and still CME.
        Minecraft.getInstance().tell(this::exitAddItemMode);
    }

    private void exitAddItemMode() {
        addItemMode = false;
        itemSelectionView.setEnabled(false);
        cancelAddItemButton.setEnabled(false);
        itemGridView.setEnabled(true);
        addItemButton.setEnabled(true);
        addFromInventoryButton.setEnabled(true);
        searchLabel.setEnabled(true);
        searchField.setEnabled(true);
        saveButton.setEnabled(true);
        layoutChanged();
    }

    /**
     * Shared logic for adding a stack to the selected category — used by both
     * the registry picker and the inventory picker. Callers are responsible for
     * deferring via {@code Minecraft.getInstance().tell(...)} so that they
     * don't mutate widget trees while a click iterator is still active.
     */
    private void addStackToSelectedCategory(ItemStack stack) {
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

        category.getPresets().add(newPreset);
        categoryModified = true;
    }

    private void onItemSelectedFromPicker(ItemStack stack) {
        // T-154 fix #1: defer — ItemSelectionView dispatches this from inside
        // its child-list mouse iteration; exitAddItemMode() clears children →
        // ConcurrentModificationException without this deferral. tell() enqueues
        // for the next tick (execute() would run inline on the render thread).
        Minecraft.getInstance().tell(() -> {
            addStackToSelectedCategory(stack);
            exitAddItemMode();
            rebuildItemGrid();
        });
    }

    // ---- T-152: Add from inventory mode ----

    /**
     * Enters the "add from inventory" mode: the right panel switches from the
     * preset list to a grid of the player's current inventory items. The Save
     * button and other normal-mode controls are disabled while the picker is open.
     */
    private void onAddFromInventoryClicked() {
        // T-154 mutual exclusion: close the registry picker if it was open.
        if (addItemMode) exitAddItemMode();
        addFromInventoryMode = true;
        inventoryPickerView.setEnabled(true);
        cancelAddFromInventoryButton.setEnabled(true);
        itemGridView.setEnabled(false);
        addItemButton.setEnabled(false);
        addFromInventoryButton.setEnabled(false);
        searchLabel.setEnabled(false);
        searchField.setEnabled(false);
        saveButton.setEnabled(false);
        // layoutChanged() will size the picker view; rebuildInventoryPicker()
        // needs those bounds to compute cell sizes so we call it after.
        layoutChanged();
        rebuildInventoryPicker();
    }

    /**
     * Cancel button handler for the inventory picker — just exits the mode.
     */
    private void onCancelAddFromInventoryClicked() {
        // T-154 fix #1: defer — same CME reason as onCancelAddItemClicked.
        // tell() enqueues for next tick; execute() would run inline.
        Minecraft.getInstance().tell(this::exitAddFromInventoryMode);
    }

    /**
     * Restores the normal preset-list view after the inventory picker was open.
     * Re-enables Save and the other normal-mode controls, and clears the picker
     * grid so a subsequent open rebuilds it from the current inventory.
     */
    private void exitAddFromInventoryMode() {
        addFromInventoryMode = false;
        inventoryPickerView.setEnabled(false);
        cancelAddFromInventoryButton.setEnabled(false);
        itemGridView.setEnabled(true);
        addItemButton.setEnabled(true);
        addFromInventoryButton.setEnabled(true);
        searchLabel.setEnabled(true);
        searchField.setEnabled(true);
        saveButton.setEnabled(true);
        inventoryPickerView.removeChilds();
        layoutChanged();
    }

    /**
     * Populates the picker from the local player's inventory in a vanilla-style
     * layout: a 3x9 main-inventory grid on top and a 1x9 hotbar grid below,
     * separated by a small vertical gap. Each cell is a plain {@link ItemView}
     * (no chrome, no close button) that fires {@link #onInventoryItemSelected}
     * on left-click. Empty slots are rendered as invisible spacer elements so
     * grid positions still mirror the real inventory. Duplicates are
     * intentionally NOT collapsed — one cell per slot.
     */
    private void rebuildInventoryPicker() {
        inventoryPickerView.removeChilds();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int pickerW = inventoryPickerView.getWidth();
        int pickerH = inventoryPickerView.getHeight();
        if (pickerW <= 0 || pickerH <= 0) return;

        // Task 1 (slot-size fix): vanilla inventory slots are 18px for a 16px
        // item — a 1px border on each side. Previously the cell was clamped to
        // 16..32 and ballooned to ~24px (≈1.5x the icon). Pin the cell to a
        // vanilla-like 18px so the slot hugs the item with just a thin frame.
        final int cellSize = 18;

        List<ItemStack> items = mc.player.getInventory().items;

        // Main inventory: 3 rows x 9 cols, slots 9..35 in row-major order.
        inventoryPickerView.addChild(buildSlotGrid(items, 9, 36, 3, cellSize));
        // Hotbar: 1 row x 9 cols, slots 0..8.
        inventoryPickerView.addChild(buildSlotGrid(items, 0, 9, 1, cellSize));
    }

    /**
     * Builds one inventory sub-grid (either the 3x9 main area or the 1x9
     * hotbar). Cells are stretched to {@code cellSize} by the grid layout.
     *
     * @param items     player's inventory backing list
     * @param startSlot inclusive start index into {@code items}
     * @param endSlot   exclusive end index into {@code items}
     * @param rows      number of rows in this sub-grid (always 9 columns)
     * @param cellSize  desired cell size in pixels
     */
    private GuiElement buildSlotGrid(List<ItemStack> items, int startSlot, int endSlot,
                                     int rows, int cellSize) {
        final int spacing = 1;
        final int columns = 9;
        GuiElement grid = new StockMarketGuiElement() {
            @Override protected void render() { }
            @Override protected void layoutChanged() { }
        };
        grid.setEnableBackground(false);
        // LayoutGrid signature: (padding, spacing, stretchX, stretchY, rows, columns, alignment).
        // stretchX/Y=true so LayoutGrid sizes each child to exactly one cell.
        grid.setLayout(new LayoutGrid(0, spacing, true, true, rows, columns, Alignment.TOP));
        grid.setSize(columns * cellSize + (columns - 1) * spacing,
                     rows * cellSize + (rows - 1) * spacing);

        for (int slot = startSlot; slot < endSlot; slot++) {
            ItemStack raw = slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
            if (raw == null || raw.isEmpty()) {
                // Invisible spacer: keeps the grid cell reserved so non-empty
                // slots stay in their vanilla positions. Renders nothing.
                GuiElement spacer = new StockMarketGuiElement() {
                    @Override protected void render() { }
                    @Override protected void layoutChanged() { }
                };
                // Task 2: draw empty slots with the same background + frame as
                // occupied ones so the picker reads as a full vanilla-style grid
                // (empty cells are just an empty slot, not a hole).
                spacer.setEnableBackground(true);
                spacer.setEnableOutline(true);
                spacer.setSize(cellSize, cellSize);
                grid.addChild(spacer);
            } else {
                final ItemStack captured = raw.copy();
                // Plain ItemView cell with click-to-pick behavior. No close
                // button, no selection overlay — just the icon and a click
                // that forwards to the picker's selection handler.
                ItemView cell = new ItemView(captured) {
                    @Override
                    protected boolean mouseClickedOverElement(int button) {
                        if (button == 0) {
                            // T-154 fix (resurfaced after inventory-picker rewrite):
                            // defer the selection callback so that no ancestor's
                            // child list is mutated while GuiElement.mouseClickedInternal
                            // is still iterating it (CME at GuiElement.java:1114).
                            // onInventoryItemSelected itself also defers, but wrapping
                            // here as well ensures the click dispatch returns cleanly
                            // before any mode/exit/rebuild logic runs. tell() enqueues
                            // for the next tick — execute() short-circuits and runs
                            // inline on the render thread, so it would still CME.
                            Minecraft.getInstance().tell(() -> onInventoryItemSelected(captured));
                            return true;
                        }
                        return false;
                    }
                };
                cell.setSize(cellSize, cellSize);
                // Task 2 (slot-border fix): ItemView disables its background and
                // outline in its constructor, so a filled slot showed only the
                // bare icon with no frame. Re-enable both here. The framework
                // draws the whole tree's backgrounds/outlines in a separate pass
                // (renderBackgroundInternal) BEFORE the foreground/item pass
                // (renderInternal), so the slot fill + frame always render behind
                // the item — even for occupied slots. ItemView centers the 16px
                // icon within the 18px cell (1px inset), leaving the outline
                // visible around the item rather than covered by it.
                cell.setEnableBackground(true);
                cell.setEnableOutline(true);
                grid.addChild(cell);
            }
        }
        return grid;
    }

    /**
     * Click handler for a MarketItemButton in the inventory picker grid.
     * Adds the picked stack to the currently selected category via the same
     * code path used by the registry picker, then exits the picker mode.
     */
    private void onInventoryItemSelected(ItemStack stack) {
        // T-154 fix #1: defer mutation — MarketItemButton dispatches this from
        // inside GuiElement.mouseClickedInternal's child iteration.
        // exitAddFromInventoryMode() clears the picker's children (36 buttons)
        // and CMEs the iterator without this deferral. tell() enqueues for the
        // next tick (execute() would run inline on the render thread).
        Minecraft.getInstance().tell(() -> {
            if (stack == null || stack.isEmpty()) return;
            addStackToSelectedCategory(stack);
            exitAddFromInventoryMode();
            rebuildItemGrid();
        });
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
            Minecraft.getInstance().tell(() -> {
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
            // T-154: Cancel is now a small square "X" button in the top-right of
            // the picker header. The picker list starts BELOW the header (same
            // row as X) and only reserves one button row at the bottom for Save.
            int btnHeight = eh;
            int xSize = 16;
            cancelAddItemButton.setBounds(rightX + rightWidth - xSize, padding, xSize, xSize);
            int listTop = padding + xSize + spacing;
            int bottomReserved = btnHeight + spacing;
            int listHeight = Math.max(eh, padding + height - bottomReserved - listTop);
            itemSelectionView.setBounds(rightX, listTop, rightWidth, listHeight);
            // T-154: hide the bottom-row Add buttons while picking (setBounds off-panel).
            addItemButton.setBounds(0, 0, 0, 0);
            addFromInventoryButton.setBounds(0, 0, 0, 0);
        } else if (addFromInventoryMode) {
            int btnHeight = eh;
            int xSize = 16;
            cancelAddFromInventoryButton.setBounds(rightX + rightWidth - xSize, padding, xSize, xSize);
            int listTop = padding + xSize + spacing;
            int bottomReserved = btnHeight + spacing;
            int listHeight = Math.max(eh, padding + height - bottomReserved - listTop);
            inventoryPickerView.setBounds(rightX, listTop, rightWidth, listHeight);
            addItemButton.setBounds(0, 0, 0, 0);
            addFromInventoryButton.setBounds(0, 0, 0, 0);
            // T-154 CME fix (option c): do NOT rebuild the inventory picker from
            // layoutChanged(). This method can be re-entered during a click's
            // mouseClickedInternal dispatch (any callback that triggers a layout
            // pass on the tab tree cascades here), and rebuildInventoryPicker()
            // calls inventoryPickerView.removeChilds() which mutates
            // ScrollContainer.childs while GuiElement.mouseClickedInternal is
            // still iterating that same list at GuiElement.java:1114 →
            // ConcurrentModificationException. The picker is built exactly once
            // per mode entry in onAddFromInventoryClicked(). Cost: cell size
            // does not reflow if the parent tab resizes while the picker is
            // open — an acceptable trade for guaranteed no-CME.
        } else {
            // Normal mode: search bar + item grid + [add item | + from inventory] + save
            int searchLabelWidth = 50;
            searchLabel.setBounds(rightX, padding, searchLabelWidth, 15);
            searchField.setBounds(searchLabel.getRight() + spacing, padding, rightWidth - searchLabelWidth - spacing, 15);

            int btnHeight = eh;
            saveButton.setBounds(rightX, padding + height - btnHeight, rightWidth, btnHeight);
            // T-152: split the add-item row into two side-by-side buttons — registry
            // picker on the left, inventory picker on the right.
            int addRowY = saveButton.getTop() - spacing - btnHeight;
            int halfWidth = (rightWidth - spacing) / 2;
            addItemButton.setBounds(rightX, addRowY, halfWidth, btnHeight);
            addFromInventoryButton.setBounds(rightX + halfWidth + spacing, addRowY,
                    rightWidth - halfWidth - spacing, btnHeight);

            int gridTop = searchLabel.getBottom() + spacing;
            int gridHeight = addRowY - spacing - gridTop;
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
        // Toggles MarketPreset.orderbookEnabled — propagates into MarketSettings.virtualOrderbookEnabled
        // when a market is created from this preset.
        private final CheckBox orderbookEnabledCheckBox;
        // Toggles MarketPreset.ignorePluginAutosubscribe — propagates into MarketSettings so the
        // ServerPluginManager skips this market in autoSubscribeNewMarket.
        private final CheckBox ignoreAutosubscribeCheckBox;
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

            orderbookEnabledCheckBox = new CheckBox(Component.translatable(
                    "gui." + StockMarketMod.MOD_ID + ".preset_editor_tab.orderbook_enabled").getString(),
                    this::onOrderbookEnabledChanged);
            orderbookEnabledCheckBox.setHoverTooltipSupplier(() ->
                    Component.translatable("gui." + StockMarketMod.MOD_ID + ".preset_editor_tab.orderbook_enabled.tooltip").getString());
            orderbookEnabledCheckBox.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
            // Anchor the tooltip's TOP-RIGHT corner at the mouse so it renders to the LEFT
            // of the cursor — the checkboxes sit near the right edge of the row and a
            // default (top-left) anchor would push the tooltip off the visible area.
            orderbookEnabledCheckBox.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);

            ignoreAutosubscribeCheckBox = new CheckBox(Component.translatable(
                    "gui." + StockMarketMod.MOD_ID + ".preset_editor_tab.ignore_plugin_autosubscribe").getString(),
                    this::onIgnoreAutosubscribeChanged);
            // Initialize both checked states only after both fields are non-null:
            // setChecked fires the change listener → writeBack() reads both boxes.
            orderbookEnabledCheckBox.setChecked(preset.isOrderbookEnabled());
            ignoreAutosubscribeCheckBox.setChecked(preset.isIgnorePluginAutosubscribe());
            ignoreAutosubscribeCheckBox.setHoverTooltipSupplier(() ->
                    Component.translatable("gui." + StockMarketMod.MOD_ID + ".preset_editor_tab.ignore_plugin_autosubscribe.tooltip").getString());
            ignoreAutosubscribeCheckBox.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
            ignoreAutosubscribeCheckBox.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);

            removeButton = new Button("x", () -> removePresetFromCategory(originalPreset));
            removeButton.setBackgroundColor(0xFFe8711c);
            removeButton.setHoverColor(0xFFe04c12);
            // Tooltip clarifies the destructive action — anchor at TOP_RIGHT so it
            // renders to the LEFT of the cursor (the button sits at the row's far right).
            removeButton.setHoverTooltipSupplier(() ->
                    Component.translatable("gui." + StockMarketMod.MOD_ID + ".preset_editor_tab.remove_preset.tooltip").getString());
            removeButton.setHoverTooltipFontScale(StockMarketGuiElement.hoverToolTipFontSize);
            removeButton.setHoverTooltipMousePositionAlignment(Alignment.TOP_RIGHT);

            addChild(itemView);
            addChild(nameLabel);
            addChild(priceLabel);
            addChild(priceTextBox);
            addChild(abundanceLabel);
            addChild(abundanceTextBox);
            addChild(orderbookEnabledCheckBox);
            addChild(ignoreAutosubscribeCheckBox);
            addChild(removeButton);

            setHeight(24);
        }

        private void writeBack(float price, float abundance) {
            editedPresets.put(presetKey, new MarketPreset(
                    originalPreset.getItemId(), originalPreset.components(), price, abundance,
                    orderbookEnabledCheckBox.isChecked(),
                    ignoreAutosubscribeCheckBox.isChecked()));
        }

        private void onPriceChanged(String text) {
            float price = parseFloat(text, -1f);
            if (price < 0) return;
            float abundance = parseFloat(abundanceTextBox.getText(), originalPreset.getNaturalAbundance());
            writeBack(price, abundance);
        }

        private void onAbundanceChanged(String text) {
            float abundance = parseFloat(text, -1f);
            if (abundance < 0) return;
            float price = parseFloat(priceTextBox.getText(), originalPreset.getDefaultPrice());
            writeBack(price, abundance);
        }

        private void onOrderbookEnabledChanged(Boolean enabled) {
            float price = parseFloat(priceTextBox.getText(), originalPreset.getDefaultPrice());
            float abundance = parseFloat(abundanceTextBox.getText(), originalPreset.getNaturalAbundance());
            writeBack(price, abundance);
        }

        private void onIgnoreAutosubscribeChanged(Boolean ignored) {
            float price = parseFloat(priceTextBox.getText(), originalPreset.getDefaultPrice());
            float abundance = parseFloat(abundanceTextBox.getText(), originalPreset.getNaturalAbundance());
            writeBack(price, abundance);
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
            int baseCheckboxWidth = 20;

            // Task 1 shrink pass: item name / price / abundance were dominating the row,
            // leaving the two flag checkboxes squashed against each other. Halve the name
            // label and cut the price / abundance text boxes to 1/3 and 1/4 of their
            // previous share, then hand the freed pixels to the checkbox slots so their
            // hit targets grow proportionally.
            //
            // "Previous share" for the text boxes is what the old formula would have
            // produced (see the pre-shrink baseline in fieldWidthOld below); computing it
            // explicitly avoids drift if the surrounding widths change again later.
            // Bumped from w/8 → w/5: gives the item name a bit more breathing room
            // now that the two checkbox labels are longer ("Virtual Orderbook" /
            // "No Plugin Autosubscribe") and the checkbox widths shrunk accordingly.
            int nameLabelWidth = w / 5;
            int priceLabelWidth = 50;
            int abundanceLabelWidth = 65;

            int fieldWidthOld = (w - iconSize - (w / 4) - priceLabelWidth - abundanceLabelWidth
                    - 2 * baseCheckboxWidth - removeBtnSize - 8 * spacing) / 2;
            int priceFieldWidth = Math.max(20, fieldWidthOld / 3);         // 1/3 of previous
            int abundanceFieldWidth = Math.max(20, fieldWidthOld / 4);     // 1/4 of previous

            int reservedFixed = iconSize + nameLabelWidth + priceLabelWidth + priceFieldWidth
                    + abundanceLabelWidth + abundanceFieldWidth + removeBtnSize + 8 * spacing;
            int checkboxTotal = Math.max(2 * baseCheckboxWidth, w - reservedFixed);
            // 40/60 split: "Virtual Orderbook" is shorter than "No Plugin Autosubscribe",
            // so give the ignore-autosubscribe box the larger slot.
            int orderbookCheckboxWidth = Math.max(baseCheckboxWidth, Math.round(checkboxTotal * 0.4f));
            int autosubCheckboxWidth = Math.max(baseCheckboxWidth, checkboxTotal - orderbookCheckboxWidth);

            itemView.setBounds(padding, (h - iconSize) / 2, iconSize, iconSize);
            nameLabel.setBounds(itemView.getRight() + spacing, 1, nameLabelWidth, fieldHeight);

            priceLabel.setBounds(nameLabel.getRight() + spacing, 1, priceLabelWidth, fieldHeight);
            priceTextBox.setBounds(priceLabel.getRight() + spacing, 1, priceFieldWidth, fieldHeight);

            abundanceLabel.setBounds(priceTextBox.getRight() + spacing, 1, abundanceLabelWidth, fieldHeight);
            abundanceTextBox.setBounds(abundanceLabel.getRight() + spacing, 1, abundanceFieldWidth, fieldHeight);

            // Fix 2: checkbox HEIGHT must match the row's fieldHeight (same as price /
            // abundance textboxes). Passing checkboxWidth for both dimensions made the
            // widget interpret the box height as proportional to its width, which blew
            // up the whole row when the freed pixels went to the width. Only the WIDTH
            // is the variable dimension here — height stays fixed to the row.
            orderbookEnabledCheckBox.setBounds(abundanceTextBox.getRight() + spacing, 1,
                    orderbookCheckboxWidth, fieldHeight);

            ignoreAutosubscribeCheckBox.setBounds(orderbookEnabledCheckBox.getRight() + spacing, 1,
                    autosubCheckboxWidth, fieldHeight);

            removeButton.setBounds(ignoreAutosubscribeCheckBox.getRight() + spacing, (h - removeBtnSize) / 2, removeBtnSize, removeBtnSize);
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
