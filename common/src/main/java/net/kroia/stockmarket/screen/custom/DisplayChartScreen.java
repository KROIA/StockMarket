package net.kroia.stockmarket.screen.custom;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.stockmarket.networking.entity.UpdateDisplayViewportPacket;
import net.kroia.stockmarket.screen.widgets.DisplayCandlestickChart;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Fullscreen chart screen opened from a configured display block.
 * Creates its own chart connected to the same market and using the same
 * display-specific viewport key. On close, writes the viewport state
 * back to the block entity so the display surface reflects the changes.
 */
public class DisplayChartScreen extends StockMarketGuiScreen {

    private final BlockPos controllerPos;
    private final ItemID itemID;
    private final ItemID secondItemID;
    private final long blockKey;
    private DisplayCandlestickChart chart;

    public DisplayChartScreen(BlockPos controllerPos, ItemID itemID, ItemID secondItemID, long blockKey, CompoundTag initialViewport) {
        super(Component.literal("Price Chart"));
        this.controllerPos = controllerPos;
        this.itemID = itemID;
        this.secondItemID = secondItemID;
        this.blockKey = blockKey;

        chart = new DisplayCandlestickChart(itemID, secondItemID, blockKey);
        if (initialViewport != null && !initialViewport.isEmpty()) {
            chart.setInitialViewport(initialViewport);
        }
        addElement(chart);
    }

    @Override
    protected void updateLayout(Gui gui) {
        int p = 4;
        chart.setBounds(p, p, getWidth() - p * 2, getHeight() - p * 2);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        syncViewportToDisplay();
    }

    @Override
    public void onClose() {
        if (chart != null) {
            syncViewportToDisplay();
            // Send viewport to server for NBT persistence
            UpdateDisplayViewportPacket.sendToServer(controllerPos, chart.getViewportState());
            chart.disconnect();
        }
        super.onClose();
    }

    private void syncViewportToDisplay() {
        if (chart == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockEntity be = mc.level.getBlockEntity(controllerPos);
        if (be instanceof StockMarketDisplayBlockEntity entity) {
            entity.applyViewport(chart.getViewportState());
        }
    }
}
