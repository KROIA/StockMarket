package net.kroia.stockmarket.screen.custom;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.stockmarket.minecraft.entity.custom.StockMarketDisplayBlockEntity;
import net.kroia.stockmarket.networking.entity.UpdateDisplayViewportPacket;
import net.kroia.stockmarket.screen.widgets.DisplayCandlestickChart;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics; // mc<=1.21.11
//import net.minecraft.client.gui.GuiGraphicsExtractor; // mc>=26.1
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

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
        super(Component.translatable("gui.stockmarket.display_config_screen.price_chart"));
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) { // mc<=1.21.11
    //public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) { // mc>=26.1
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        syncViewportToDisplay();
    }

    @Override
    public void onClose() {
        if (chart != null) {
            // SM-364: skip the write-back once the display shows another market (re-pointed meanwhile,
            // e.g. by another player), or this market's view would hide the new market's candles.
            if (displayShowingThisMarket() != null) {
                syncViewportToDisplay();
                // Send viewport to server for NBT persistence
                UpdateDisplayViewportPacket.sendToServer(controllerPos, chart.getViewportState());
            }
            chart.disconnect();
        }
        super.onClose();
    }

    private void syncViewportToDisplay() {
        StockMarketDisplayBlockEntity entity = displayShowingThisMarket();
        if (entity != null) {
            entity.applyViewport(chart.getViewportState());
        }
    }

    /**
     * @return the client's display controller at {@link #controllerPos} if it still shows this screen's
     *         market (and pair), else null (no chart, no world, no display there, or another market)
     */
    private @Nullable StockMarketDisplayBlockEntity displayShowingThisMarket() {
        if (chart == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        BlockEntity be = mc.level.getBlockEntity(controllerPos);
        if (be instanceof StockMarketDisplayBlockEntity entity
                && Objects.equals(entity.getSelectedItemID(), itemID)
                && Objects.equals(entity.getSecondItemID(), secondItemID)) {
            return entity;
        }
        return null;
    }
}
