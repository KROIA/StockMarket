package net.kroia.stockmarket.screen.custom;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.stockmarket.networking.entity.UpdateStockMarketDisplayConfigPacket;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Configuration screen for the StockMarket display block.
 * Allows the player to select a display type and a target market.
 */
public class DisplayConfigScreen extends StockMarketGuiScreen {

    private final BlockPos blockPos;
    private StockMarketDisplayBlockEntity.DisplayType selectedType;
    private ItemID selectedItemID;

    private final Label titleLabel;
    private final Label typeLabel;
    private final Button priceChartButton;
    private final Label marketLabel;
    private final VerticalListView marketListView;
    private final Label selectedLabel;
    private final Button applyButton;

    private static final int ACTIVE_COLOR = ColorUtilities.getRGB(40, 120, 80);
    private static final int INACTIVE_COLOR = ColorUtilities.getRGB(60, 60, 60);
    private static final int HOVER_COLOR = ColorUtilities.getRGB(60, 140, 100);

    public DisplayConfigScreen(BlockPos blockPos, StockMarketDisplayBlockEntity.DisplayType currentType, ItemID currentItemID) {
        super(Component.literal("Configure Display"));
        this.blockPos = blockPos;
        this.selectedType = (currentType != null && currentType != StockMarketDisplayBlockEntity.DisplayType.NONE)
                ? currentType : StockMarketDisplayBlockEntity.DisplayType.PRICE_CHART;
        this.selectedItemID = currentItemID;

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

        marketLabel = new Label("Select Market:");
        addElement(marketLabel);

        marketListView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        marketListView.setLayout(layout);
        addElement(marketListView);

        selectedLabel = new Label("");
        addElement(selectedLabel);

        applyButton = new Button("Apply");
        applyButton.setOnFallingEdge(this::onApply);
        addElement(applyButton);

        updateTypeButtons();
        loadMarkets();
    }

    @Override
    protected void updateLayout(Gui gui) {
        int w = getWidth();
        int h = getHeight();
        int p = 8;
        int centerW = Math.min(300, w - p * 2);
        int x = (w - centerW) / 2;
        int y = p;

        titleLabel.setBounds(x, y, centerW, 18);
        y += 24;

        typeLabel.setBounds(x, y, centerW, 14);
        y += 18;

        // Single type button for now (structured for future types)
        priceChartButton.setBounds(x, y, centerW, 20);
        y += 28;

        marketLabel.setBounds(x, y, centerW, 14);
        y += 18;

        int listH = h - y - 60;
        marketListView.setBounds(x, y, centerW, Math.max(40, listH));
        y += Math.max(40, listH) + p;

        selectedLabel.setBounds(x, y, centerW, 14);
        y += 20;

        applyButton.setBounds(x, y, centerW, 22);
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
    }

    private void loadMarkets() {
        List<ItemID> markets = getAvailableMarkets();
        marketListView.removeChilds();

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
                refreshMarketHighlights();
                updateSelectedLabel();
            });
            row.addChild(btn);

            marketListView.addChild(row);
        }
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

    private void onApply() {
        if (selectedItemID == null || selectedType == null
                || selectedType == StockMarketDisplayBlockEntity.DisplayType.NONE) {
            return;
        }
        UpdateStockMarketDisplayConfigPacket.sendToServer(
                blockPos, selectedType.getId(),
                selectedItemID.getShort());
        onClose();
    }
}
