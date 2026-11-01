package net.kroia.stockmarket.screen.custom;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.stockmarket.networking.entity.UpdateStockMarketDisplayConfigPacket;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration screen for the StockMarket display block.
 * Allows the player to select a display type and a target market.
 */
public class DisplayConfigScreen extends StockMarketGuiScreen {

    private final BlockPos blockPos;
    private StockMarketDisplayBlockEntity.DisplayType selectedType;
    private ItemID selectedItemID;
    private ItemID selectedSecondItemID;

    private final Label titleLabel;
    private final Label typeLabel;
    private final Button priceChartButton;
    private final Label marketLabel;
    private final Label searchLabel;
    private final TextBox searchField;
    private final VerticalListView marketListView;
    private final Label selectedLabel;
    private final Label secondMarketLabel;
    private final Label secondSearchLabel;
    private final TextBox secondSearchField;
    private final VerticalListView secondMarketListView;
    private final Label secondSelectedLabel;
    private final Button clearSecondButton;
    private final Button applyButton;

    /** All primary market row elements, kept for search filtering. */
    private List<GuiElement> allMarketRows = new ArrayList<>();
    /** All secondary market row elements, kept for search filtering. */
    private List<GuiElement> allSecondMarketRows = new ArrayList<>();

    private static final int ACTIVE_COLOR = ColorUtilities.getRGB(40, 120, 80);
    private static final int INACTIVE_COLOR = ColorUtilities.getRGB(60, 60, 60);
    private static final int HOVER_COLOR = ColorUtilities.getRGB(60, 140, 100);

    public DisplayConfigScreen(BlockPos blockPos, StockMarketDisplayBlockEntity.DisplayType currentType, ItemID currentItemID, ItemID currentSecondItemID) {
        super(Component.literal("Configure Display"));
        this.blockPos = blockPos;
        this.selectedType = (currentType != null && currentType != StockMarketDisplayBlockEntity.DisplayType.NONE)
                ? currentType : StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART;
        this.selectedItemID = currentItemID;
        this.selectedSecondItemID = currentSecondItemID;

        titleLabel = new Label("Configure Display");
        titleLabel.setAlignment(GuiElement.Alignment.CENTER);
        addElement(titleLabel);

        typeLabel = new Label("Display Type:");
        addElement(typeLabel);

        priceChartButton = new Button("Price Chart");
        priceChartButton.setOnFallingEdge(() -> {
            selectedType = StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART;
            updateTypeButtons();
        });
        addElement(priceChartButton);

        marketLabel = new Label("I want (item to display):");
        addElement(marketLabel);

        searchLabel = new Label("Search");
        addElement(searchLabel);

        searchField = new TextBox();
        searchField.setTextFontScale(0.7f);
        searchField.setMaxChars(40);
        searchField.setOnTextChanged(text -> applyMarketFilter());
        addElement(searchField);

        marketListView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        marketListView.setLayout(layout);
        addElement(marketListView);

        selectedLabel = new Label("");
        addElement(selectedLabel);

        secondMarketLabel = new Label("I have / currency (optional):");
        addElement(secondMarketLabel);

        secondSearchLabel = new Label("Search");
        addElement(secondSearchLabel);

        secondSearchField = new TextBox();
        secondSearchField.setTextFontScale(0.7f);
        secondSearchField.setMaxChars(40);
        secondSearchField.setOnTextChanged(text -> applySecondMarketFilter());
        addElement(secondSearchField);

        secondMarketListView = new VerticalListView();
        LayoutVertical secondLayout = new LayoutVertical();
        secondLayout.stretchX = true;
        secondMarketListView.setLayout(secondLayout);
        addElement(secondMarketListView);

        secondSelectedLabel = new Label("");
        addElement(secondSelectedLabel);

        clearSecondButton = new Button("Clear pair");
        clearSecondButton.setOnFallingEdge(() -> {
            selectedSecondItemID = null;
            loadSecondMarkets();
            updateSelectedLabel();
        });
        addElement(clearSecondButton);

        applyButton = new Button("Apply");
        applyButton.setOnFallingEdge(this::onApply);
        addElement(applyButton);

        updateTypeButtons();
        loadMarkets();
        loadSecondMarkets();
    }

    @Override
    protected void updateLayout(Gui gui) {
        int w = getWidth();
        int h = getHeight();
        int p = 8;
        int centerW = Math.min(300, w - p * 2);
        int x = (w - centerW) / 2;
        int y = p;

        // Fixed-height elements above the lists
        titleLabel.setBounds(x, y, centerW, 14);
        y += 18;

        typeLabel.setBounds(x, y, centerW, 12);
        y += 14;

        priceChartButton.setBounds(x, y, centerW, 18);
        y += 22;

        marketLabel.setBounds(x, y, centerW, 12);
        y += 14;

        // Search row for primary market list
        int searchLabelW = centerW / 4;
        int searchFieldX = x + searchLabelW + 2;
        int searchFieldW = centerW - searchLabelW - 2;
        searchLabel.setBounds(x, y, searchLabelW, 14);
        searchField.setBounds(searchFieldX, y, searchFieldW, 14);
        y += 16;

        // Fixed-height elements below the lists:
        // selectedLabel(14+2) + secondMarketLabel(12+2) + secondSearch(14+2)
        // + secondList + secondSelectedLabel(14+2) + clearSecondButton(18+4) + applyButton(20) + bottom padding(p)
        int bottomFixedH = 14 + 2 + 12 + 2 + 14 + 2 + 14 + 2 + 18 + 4 + 20 + p;

        // Split remaining height between the two market lists
        int availableForLists = h - y - bottomFixedH;
        int listH = Math.max(20, availableForLists / 2);

        marketListView.setBounds(x, y, centerW, listH);
        y += listH + 2;

        selectedLabel.setBounds(x, y, centerW, 14);
        y += 16;

        secondMarketLabel.setBounds(x, y, centerW, 12);
        y += 14;

        // Search row for secondary market list
        secondSearchLabel.setBounds(x, y, searchLabelW, 14);
        secondSearchField.setBounds(searchFieldX, y, searchFieldW, 14);
        y += 16;

        secondMarketListView.setBounds(x, y, centerW, listH);
        y += listH + 2;

        secondSelectedLabel.setBounds(x, y, centerW, 14);
        y += 16;

        int halfW = (centerW - p) / 2;
        clearSecondButton.setBounds(x, y, halfW, 18);
        applyButton.setBounds(x + halfW + p, y, halfW, 18);
    }

    private void updateTypeButtons() {
        boolean isPriceChart = selectedType == StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART;
        priceChartButton.setBackgroundColor(isPriceChart ? ACTIVE_COLOR : INACTIVE_COLOR);
        priceChartButton.setHoverColor(isPriceChart ? ACTIVE_COLOR : HOVER_COLOR);
    }

    private void updateSelectedLabel() {
        if (selectedItemID != null) {
            String name = selectedItemID.getStack().getHoverName().getString();
            selectedLabel.setText("Selected: " + name);
        } else {
            selectedLabel.setText("No market selected");
        }

        if (selectedSecondItemID != null) {
            String firstName = selectedItemID != null ? selectedItemID.getStack().getHoverName().getString() : "?";
            String secondName = selectedSecondItemID.getStack().getHoverName().getString();
            secondSelectedLabel.setText("Pair: " + firstName + " / " + secondName);
        } else {
            secondSelectedLabel.setText("No pair (Item/Money mode)");
        }
    }

    private void loadMarkets() {
        List<ItemID> markets = new ArrayList<>(getAvailableMarkets());
        markets.sort(StockMarketGuiElement.MARKET_TYPE_COMPARATOR);
        marketListView.removeChilds();
        allMarketRows.clear();

        if (markets == null || markets.isEmpty()) {
            Label noMarkets = new Label("No markets available");
            noMarkets.setAlignment(GuiElement.Alignment.CENTER);
            marketListView.addChild(noMarkets);
            updateSelectedLabel();
            return;
        }

        for (ItemID itemID : markets) {
            GuiElement row = new GuiElement(0, 0, 100, 22) {
                @Override protected void render() {}
                @Override protected void layoutChanged() {
                    for (var child : getChilds()) {
                        if (child instanceof ItemView iv) {
                            iv.setBounds(2, 2, 18, 18);
                        } else if (child instanceof Button btn) {
                            btn.setBounds(22, 0, getWidth() - 22, 22);
                        }
                    }
                }
            };
            row.setEnableBackground(false);
            row.setEnableOutline(false);

            ItemView icon = new ItemView();
            icon.setItemStack(itemID.getStack());
            icon.setShowTooltip(true);
            row.addChild(icon);

            boolean isSelected = selectedItemID != null && selectedItemID.getShort() == itemID.getShort();
            String displayName = itemID.getStack().getHoverName().getString();
            Button btn = new Button(displayName);
            btn.setBackgroundColor(isSelected ? ACTIVE_COLOR : INACTIVE_COLOR);
            btn.setHoverColor(HOVER_COLOR);
            final ItemID capturedItemID = itemID;
            btn.setOnFallingEdge(() -> {
                selectedItemID = capturedItemID;
                // Clear second selection if it matches the new first selection
                if (selectedSecondItemID != null && selectedSecondItemID.getShort() == capturedItemID.getShort()) {
                    selectedSecondItemID = null;
                }
                refreshMarketHighlights();
                loadSecondMarkets();
                updateSelectedLabel();
            });
            row.addChild(btn);

            allMarketRows.add(row);
        }
        applyMarketFilter();
        updateSelectedLabel();
    }

    private void refreshMarketHighlights() {
        for (var child : marketListView.getChilds()) {
            for (var sub : child.getChilds()) {
                if (sub instanceof Button btn) {
                    // Check if any sibling ItemView matches selected
                    boolean isThis = false;
                    for (var sibling : child.getChilds()) {
                        if (sibling instanceof ItemView iv && selectedItemID != null) {
                            ItemID rowItemID = ItemID.getFromItemStack(iv.getItemStack());
                            if (rowItemID != null && rowItemID.getShort() == selectedItemID.getShort()) {
                                isThis = true;
                            }
                        }
                    }
                    btn.setBackgroundColor(isThis ? ACTIVE_COLOR : INACTIVE_COLOR);
                }
            }
        }
    }

    /**
     * Populates the second market list with all available markets except the
     * currently selected first market (can't pair with yourself).
     */
    private void loadSecondMarkets() {
        List<ItemID> markets = new ArrayList<>(getAvailableMarkets());
        markets.sort(StockMarketGuiElement.MARKET_TYPE_COMPARATOR);
        secondMarketListView.removeChilds();
        allSecondMarketRows.clear();

        if (markets == null || markets.isEmpty()) {
            Label noMarkets = new Label("No markets available");
            noMarkets.setAlignment(GuiElement.Alignment.CENTER);
            secondMarketListView.addChild(noMarkets);
            return;
        }

        for (ItemID itemID : markets) {
            // Exclude the first selected market from the second list
            if (selectedItemID != null && itemID.getShort() == selectedItemID.getShort()) {
                continue;
            }

            GuiElement row = new GuiElement(0, 0, 100, 22) {
                @Override protected void render() {}
                @Override protected void layoutChanged() {
                    for (var child : getChilds()) {
                        if (child instanceof ItemView iv) {
                            iv.setBounds(2, 2, 18, 18);
                        } else if (child instanceof Button btn) {
                            btn.setBounds(22, 0, getWidth() - 22, 22);
                        }
                    }
                }
            };
            row.setEnableBackground(false);
            row.setEnableOutline(false);

            ItemView icon = new ItemView();
            icon.setItemStack(itemID.getStack());
            icon.setShowTooltip(true);
            row.addChild(icon);

            boolean isSelected = selectedSecondItemID != null && selectedSecondItemID.getShort() == itemID.getShort();
            String displayName = itemID.getStack().getHoverName().getString();
            Button btn = new Button(displayName);
            btn.setBackgroundColor(isSelected ? ACTIVE_COLOR : INACTIVE_COLOR);
            btn.setHoverColor(HOVER_COLOR);
            final ItemID capturedItemID = itemID;
            btn.setOnFallingEdge(() -> {
                selectedSecondItemID = capturedItemID;
                refreshSecondMarketHighlights();
                updateSelectedLabel();
            });
            row.addChild(btn);

            allSecondMarketRows.add(row);
        }
        applySecondMarketFilter();
    }

    /**
     * Updates button highlight colors in the second market list to reflect selection.
     */
    private void refreshSecondMarketHighlights() {
        for (var child : secondMarketListView.getChilds()) {
            for (var sub : child.getChilds()) {
                if (sub instanceof Button btn) {
                    boolean isThis = false;
                    for (var sibling : child.getChilds()) {
                        if (sibling instanceof ItemView iv && selectedSecondItemID != null) {
                            ItemID rowItemID = ItemID.getFromItemStack(iv.getItemStack());
                            if (rowItemID != null && rowItemID.getShort() == selectedSecondItemID.getShort()) {
                                isThis = true;
                            }
                        }
                    }
                    btn.setBackgroundColor(isThis ? ACTIVE_COLOR : INACTIVE_COLOR);
                }
            }
        }
    }

    /**
     * Filters the primary market list based on search text.
     * Matches against item display name and tooltip text.
     */
    private void applyMarketFilter() {
        marketListView.removeChilds();
        String filter = searchField.getText().toLowerCase().trim();
        for (GuiElement row : allMarketRows) {
            if (filter.isEmpty() || matchesFilter(row, filter)) {
                marketListView.addChild(row);
            }
        }
    }

    /**
     * Filters the secondary market list based on search text.
     * Matches against item display name and tooltip text.
     */
    private void applySecondMarketFilter() {
        secondMarketListView.removeChilds();
        String filter = secondSearchField.getText().toLowerCase().trim();
        for (GuiElement row : allSecondMarketRows) {
            if (filter.isEmpty() || matchesFilter(row, filter)) {
                secondMarketListView.addChild(row);
            }
        }
    }

    /**
     * Checks if a market row matches the given filter text by comparing
     * against the item's display name and full tooltip text (includes
     * enchantment names, potion effects, component text, etc.).
     *
     * @param row    the market row element containing an ItemView child
     * @param filter the lowercase search text to match against
     * @return true if any child ItemView's name or tooltip contains the filter
     */
    private boolean matchesFilter(GuiElement row, String filter) {
        for (var child : row.getChilds()) {
            if (child instanceof ItemView iv) {
                String name = iv.getItemStack().getHoverName().getString().toLowerCase();
                if (name.contains(filter)) return true;
                String displayText = ClientPlayerUtilities.getItemDisplayText(iv.getItemStack()).toLowerCase();
                if (displayText.contains(filter)) return true;
            }
        }
        return false;
    }

    private void onApply() {
        if (selectedItemID == null || selectedType == null
                || selectedType == StockMarketDisplayBlockEntity.DisplayType.NONE) {
            return;
        }
        UpdateStockMarketDisplayConfigPacket.sendToServer(
                blockPos, selectedType.getId(),
                selectedItemID.getShort(),
                selectedSecondItemID != null ? selectedSecondItemID.getShort() : (short) -1);
        onClose();
    }
}
