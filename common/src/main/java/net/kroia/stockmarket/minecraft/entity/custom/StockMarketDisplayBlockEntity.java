package net.kroia.stockmarket.minecraft.entity.custom;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.display.AbstractDisplayBlockEntity;
import net.kroia.modutilities.gui.display.ContentBuilder;
import net.kroia.modutilities.gui.display.DisplayConfig;
import net.kroia.modutilities.gui.elements.ItemView;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.minecraft.entity.StockMarketEntities;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.screen.widgets.DisplayCandlestickChart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StockMarketDisplayBlockEntity extends AbstractDisplayBlockEntity {

    // ── DisplayType enum ──

    public enum DisplayType {
        NONE("none", "Select Display"),
        PRICE_CHART("price_chart", "Price Chart");

        private final String id;
        private final String displayName;

        DisplayType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }

        public static DisplayType fromId(String id) {
            for (DisplayType type : values()) {
                if (type.id.equals(id)) return type;
            }
            return NONE;
        }
    }

    // ── Display config ──

    private static final DisplayConfig DISPLAY_CONFIG = new DisplayConfig(
            256, 256, 2, 4096,
            -14.0f / 16.0f + 0.001f,
            facing -> switch (facing) {
                case NORTH -> Block.box(0, 0, 14, 16, 16, 16);
                case SOUTH -> Block.box(0, 0, 0, 16, 16, 2);
                case EAST  -> Block.box(0, 0, 0, 2, 16, 16);
                case WEST  -> Block.box(14, 0, 0, 16, 16, 16);
                default -> Block.box(0, 0, 14, 16, 16, 16);
            },
            1, 0
    );

    // ── Configuration state ──

    private DisplayType displayType = DisplayType.NONE;
    private ItemID selectedItemID = null;
    private ItemID secondItemID = null;

    // ── GUI widget references ──

    private CandlestickChart chart;

    // ── Viewport persistence ──
    // Stored per block entity so each display keeps its own zoom/position,
    // independent of the TradeScreen or other displays.
    private CompoundTag pendingViewport = null;

    // ── Constructor ──

    public StockMarketDisplayBlockEntity(BlockPos pos, BlockState blockState) {
        super(StockMarketEntities.STOCKMARKET_DISPLAY_BLOCK_ENTITY.get(), pos, blockState);
    }

    // ── Public API ──

    public DisplayType getDisplayType() { return displayType; }
    public ItemID getSelectedItemID() { return selectedItemID; }
    public ItemID getSecondItemID() { return secondItemID; }
    public CandlestickChart getChart() { return chart; }

    /**
     * Convenience overload for Item/Money mode (no second item).
     */
    public void setConfig(DisplayType type, ItemID itemID) {
        setConfig(type, itemID, null);
    }

    /**
     * Sets the display configuration. Propagates the config to all displays in
     * the group so they stay connected, then recalculates groups if the type
     * changed and rebuilds the GUI.
     *
     * @param type         the display type
     * @param itemID       the primary market item
     * @param secondItemID optional second market item for cross-rate (Item/Item) display; null for Item/Money
     */
    public void setConfig(DisplayType type, ItemID itemID, ItemID secondItemID) {
        DisplayType oldType = this.displayType;
        this.displayType = type;
        this.selectedItemID = itemID;
        this.secondItemID = secondItemID;

        if (level != null && !level.isClientSide()) {
            propagateConfigToGroup();
            if (oldType != type) {
                net.minecraft.core.Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
                AbstractDisplayBlockEntity.recalculateGroups(level, getBlockPos(), facing);
            }
            rebuildGui();
        }
    }

    /**
     * Propagates displayType and selectedItemID to all display block entities
     * in this controller's group so they share the same channel and stay merged.
     */
    private void propagateConfigToGroup() {
        if (level == null || !isController()) return;

        BlockPos myPos = getBlockPos();
        int radius = getGroupWidth() + getGroupHeight();

        for (BlockPos bp : BlockPos.betweenClosed(
                myPos.offset(-radius, -radius, -radius),
                myPos.offset(radius, radius, radius))) {
            if (bp.equals(myPos)) continue;
            BlockEntity be = level.getBlockEntity(bp);
            if (be instanceof StockMarketDisplayBlockEntity other) {
                if (myPos.equals(other.getControllerPos())) {
                    other.displayType = this.displayType;
                    other.selectedItemID = this.selectedItemID;
                    other.secondItemID = this.secondItemID;
                    other.setChanged();
                }
            }
        }
    }

    // ── AbstractDisplayBlockEntity overrides ──

    @Override
    public DisplayConfig getDisplayConfig() {
        return DISPLAY_CONFIG;
    }

    @Override
    public ContentBuilder getContentBuilder() {
        return switch (displayType) {
            case NONE -> StockMarketDisplayBlockEntity::buildUnconfiguredUI;
            case PRICE_CHART -> {
                final ItemID itemID = selectedItemID;
                final ItemID secondID = secondItemID;
                final long blockKey = getBlockPos().asLong();
                yield (gui, w, h) -> buildPriceChart(gui, w, h, itemID, secondID, blockKey);
            }
        };
    }

    @Override
    public String getChannelId() {
        return switch (displayType) {
            case NONE -> "sm_none";
            case PRICE_CHART -> {
                if (selectedItemID != null && secondItemID != null) {
                    yield "sm_pair_" + selectedItemID.getShort() + "_" + secondItemID.getShort();
                }
                yield "sm_chart_" + (selectedItemID != null ? selectedItemID.getShort() : "none");
            }
        };
    }

    @Override
    public boolean opensSyncedScreenOnUse() {
        // Don't use the built-in DisplayInteractionScreen — it creates a GUI copy
        // whose viewport changes are never written back. We use DisplayChartScreen instead.
        return false;
    }

    /**
     * Applies viewport state from an external source (e.g. the DisplayChartScreen
     * writing back after the player closes it). Updates both the live chart and
     * the pending state for persistence across rebuilds.
     */
    public void applyViewport(CompoundTag viewport) {
        this.pendingViewport = viewport;
        if (chart instanceof DisplayCandlestickChart dcc) {
            dcc.setInitialViewport(viewport);
        }
        setChanged();
    }

    @Override
    protected void wireCallbacks(Gui gui) {
        // Save viewport from old chart before discarding it
        if (chart instanceof DisplayCandlestickChart dcc) {
            pendingViewport = dcc.getViewportState();
            dcc.disconnect();
        }
        chart = null;
        for (var el : gui.getElements()) {
            if (el instanceof CandlestickChart c) {
                chart = c;
                break;
            }
        }
        // Pass saved viewport to new chart (applied once it connects to market)
        if (chart instanceof DisplayCandlestickChart dcc && pendingViewport != null) {
            dcc.setInitialViewport(pendingViewport);
        }
    }

    @Override
    protected void onControllerTick() {
        // No server-side data fetching needed.
        // The DisplayCandlestickChart lazily connects to the client-side market cache.
    }

    @Override
    protected void saveCustomData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("displayType", displayType.getId());
        if (selectedItemID != null) {
            tag.putShort("selectedItemID", selectedItemID.getShort());
        }
        if (secondItemID != null) {
            tag.putShort("secondItemID", secondItemID.getShort());
        }
        // Only save viewport if it contains real user-set state (connected chart
        // or previously saved pending state). The server-side chart is never connected,
        // so its default viewport values won't be saved and won't prevent auto-centering.
        if (chart instanceof DisplayCandlestickChart dcc && dcc.isConnected()) {
            tag.put("viewport", dcc.getViewportState());
        } else if (pendingViewport != null) {
            tag.put("viewport", pendingViewport);
        }
    }

    @Override
    protected void loadCustomData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("displayType")) {
            displayType = DisplayType.fromId(tag.getString("displayType"));
        }
        if (tag.contains("selectedItemID")) {
            selectedItemID = new ItemID(tag.getShort("selectedItemID"));
        } else {
            selectedItemID = null;
        }
        if (tag.contains("secondItemID")) {
            secondItemID = new ItemID(tag.getShort("secondItemID"));
        } else {
            secondItemID = null;
        }
        if (tag.contains("viewport")) {
            pendingViewport = tag.getCompound("viewport");
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        DisplayType oldType = this.displayType;
        ItemID oldItemID = this.selectedItemID;
        ItemID oldSecondID = this.secondItemID;
        super.loadAdditional(tag, registries);

        if (level != null && level.isClientSide() && isController()) {
            if (oldType != this.displayType || !itemIDEquals(oldItemID, this.selectedItemID) || !itemIDEquals(oldSecondID, this.secondItemID)) {
                // Disconnect old chart before rebuilding
                if (chart instanceof DisplayCandlestickChart dcc) {
                    dcc.disconnect();
                }
                rebuildGui();
            }
            if (gui != null) {
                wireCallbacks(gui);
            }
        }
    }

    @Override
    public void onInputSynced() {
        // No interactive input to sync for the display block.
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide()) {
            if (chart instanceof DisplayCandlestickChart dcc) {
                dcc.disconnect();
            }
        }
        super.setRemoved();
    }

    // ── Unconfigured UI ──

    private static void buildUnconfiguredUI(Gui gui, int w, int h) {
        Label title = new Label("Stock Market Display");
        title.setBounds(0, h / 2 - 16, w, 14);
        title.setAlignment(GuiElement.Alignment.CENTER);
        title.setTextColor(ColorUtilities.getRGB(200, 220, 255));
        gui.addElement(title);

        Label hint = new Label("Right-click to configure");
        hint.setBounds(0, h / 2 + 2, w, 12);
        hint.setAlignment(GuiElement.Alignment.CENTER);
        hint.setTextColor(ColorUtilities.getRGB(140, 140, 160));
        gui.addElement(hint);
    }

    // ── Price Chart UI ──

    private static void buildPriceChart(Gui gui, int w, int h, ItemID itemID, ItemID secondItemID, long blockKey) {
        int margin = 4;
        int iconSize = 16;
        int iconAreaWidth = iconSize + margin;

        // Chart fills display area, with right margin for the icon(s)
        DisplayCandlestickChart chart = new DisplayCandlestickChart(itemID, secondItemID, blockKey);
        chart.setBounds(margin, margin, w - margin * 2 - iconAreaWidth, h - margin * 2);
        gui.addElement(chart);

        // Show market icon(s) on the right edge
        if (itemID != null) {
            if (secondItemID != null) {
                // Pair mode: show both icons vertically centered
                int totalHeight = iconSize * 2 + 2;
                int iconX = w - margin - iconSize;
                int startY = (h - totalHeight) / 2;

                ItemView haveIcon = new ItemView(iconX, startY, iconSize, iconSize);
                haveIcon.setItemStack(itemID.getStack());
                haveIcon.setShowTooltip(true);
                gui.addElement(haveIcon);

                ItemView wantIcon = new ItemView(iconX, startY + iconSize + 2, iconSize, iconSize);
                wantIcon.setItemStack(secondItemID.getStack());
                wantIcon.setShowTooltip(true);
                gui.addElement(wantIcon);
            } else {
                // Single mode: one icon vertically centered
                int iconX = w - margin - iconSize;
                int iconY = (h - iconSize) / 2;
                ItemView icon = new ItemView(iconX, iconY, iconSize, iconSize);
                icon.setItemStack(itemID.getStack());
                icon.setShowTooltip(true);
                gui.addElement(icon);
            }
        }
    }

    // ── Utility ──

    private static boolean itemIDEquals(ItemID a, ItemID b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.getShort() == b.getShort();
    }
}
