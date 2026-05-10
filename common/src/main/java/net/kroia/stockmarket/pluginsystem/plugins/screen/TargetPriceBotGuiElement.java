package net.kroia.stockmarket.pluginsystem.plugins.screen;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.stream.PluginRuntimeDataStream;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom GUI element for the TargetPriceBot plugin.
 * Provides a dedicated full-screen layout with a candlestick chart,
 * a market selection list, and a settings panel.
 *
 * <pre>
 * +-------------------+-------------------+
 * | CandlestickChart  |                   |
 * | (top-left)        |  Settings List    |
 * |                   |  (right side)     |
 * +-------------------+                   |
 * | ItemSelectionView |                   |
 * | (bottom-left)     |                   |
 * +-------------------+-------------------+
 * </pre>
 */
public class TargetPriceBotGuiElement extends PluginGuiElement {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".target_price_bot.";
        public static final Component SETTINGS_TITLE = Component.translatable(PREFIX + "settings_title");
        public static final Component SETTINGS_PLACEHOLDER = Component.translatable(PREFIX + "settings_placeholder");
    }

    private final CandlestickChart candlestickChart;
    private final ItemSelectionView marketSelectionView;
    private final ListView settingsListView;
    private final Label settingsTitle;
    private final Label settingsPlaceholder;
    private List<ItemID> subscribedMarkets = new ArrayList<>();
    private @Nullable ItemID selectedMarketID;
    private @Nullable ClientMarket currentMarket;
    private double targetPrice = 0;
    private boolean hasTargetPrice = false;

    /**
     * Creates the TargetPriceBot GUI element.
     * Market data is populated later via {@link #onPluginSyncDataReceived(PluginSyncData)}.
     */
    public TargetPriceBotGuiElement() {
        candlestickChart = new CandlestickChart();

        // Market selection using ItemSelectionView (same pattern as ManagementScreen)
        marketSelectionView = new ItemSelectionView(this::onMarketSelected);

        // Settings list with vertical layout
        settingsListView = new VerticalListView();
        LayoutVertical settingsLayout = new LayoutVertical();
        settingsLayout.stretchX = true;
        settingsLayout.stretchY = false;
        settingsListView.setLayout(settingsLayout);

        settingsTitle = new Label(Texts.SETTINGS_TITLE.getString());
        settingsTitle.setAlignment(Label.Alignment.CENTER);
        settingsListView.addChild(settingsTitle);

        settingsPlaceholder = new Label(Texts.SETTINGS_PLACEHOLDER.getString());
        settingsListView.addChild(settingsPlaceholder);

        addChild(candlestickChart);
        addChild(marketSelectionView);
        addChild(settingsListView);
    }

    @Override
    public boolean needsCustomScreen() {
        return true;
    }

    /**
     * Populates the market selection view from the plugin's sync data
     * and starts the runtime data stream for live target price updates.
     *
     * @param data the plugin sync data containing subscribed markets
     */
    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data) {
        this.subscribedMarkets = data.getSubscribedMarkets();

        // Populate the item selection view with subscribed market items
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemID id : subscribedMarkets) {
            ItemStack stack = id.getStack();
            if (stack != null) {
                stacks.add(stack);
            }
        }
        marketSelectionView.setItems(stacks);

        // Start the runtime data stream for live target price updates
        startDataStream();
    }

    /**
     * Called when a market item is selected from the ItemSelectionView.
     * Unsubscribes from the previous market and subscribes to the new one.
     *
     * @param item the selected item stack
     */
    private void onMarketSelected(ItemStack item) {
        // Unsubscribe from previous market
        if (currentMarket != null) {
            currentMarket.unsubscribeFromMarketPriceUpdate();
        }

        // Look up the ItemID from the stack and select the market
        ItemID.getOrRegisterFromItemStackClientSide(item).thenAccept(itemID -> {
            this.selectedMarketID = itemID;
            ClientMarket market = getMarket(itemID);
            if (market != null) {
                market.subscribeToMarketPriceUpdate();
                candlestickChart.setMarket(market);
                currentMarket = market;
            }
        });
    }

    /**
     * Decodes runtime data from the server plugin to extract target prices.
     * Format: [count:int] then for each market: [itemID:short, targetPrice:double].
     *
     * @param data the runtime data payload from the server
     */
    @Override
    protected void onRuntimeDataReceived(PluginRuntimeDataStream.RuntimeData data) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data.payload);
            DataInputStream dis = new DataInputStream(bais);
            int count = dis.readInt();
            for (int i = 0; i < count; i++) {
                short itemId = dis.readShort();
                double price = dis.readDouble();
                // Match against the currently selected market
                if (selectedMarketID != null && itemId == selectedMarketID.getShort()) {
                    targetPrice = price;
                    hasTargetPrice = true;
                }
            }
        } catch (Exception e) {
            // Ignore decode errors silently
        }
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int h = getHeight();
        int halfW = w / 2;
        int halfH = h / 2;

        candlestickChart.setBounds(0, 0, halfW, halfH);
        marketSelectionView.setBounds(0, halfH + spacing, halfW, h - halfH - spacing);
        settingsListView.setBounds(halfW + spacing, 0, w - halfW - spacing, h);
    }

    @Override
    protected void render() {
        // Draw target price line on top of the chart using the live-streamed target price.
        // The chart renders as a child (in renderBackground), so by the time this
        // parent render() runs, the chart is already drawn and we can overlay on it.
        //
        // Coordinate approach:
        //   - toCanvasSpaceY() returns Y relative to the chart's own local space
        //   - getLeft()/getTop() gives the chart's offset within this parent element
        //   - We combine both to get the correct screen-space position for drawing
        if (currentMarket != null && hasTargetPrice) {
            Rectangle canvasBounds = candlestickChart.getCanvasBounds();
            int chartX = candlestickChart.getLeft();
            int chartY = candlestickChart.getTop();

            int lineY = chartY + candlestickChart.toCanvasSpaceY(targetPrice);
            int lineX = chartX + canvasBounds.x;
            int lineWidth = canvasBounds.width;

            // Only draw if within canvas bounds
            int canvasTop = chartY + canvasBounds.y;
            int canvasBottom = canvasTop + canvasBounds.height;
            if (lineY >= canvasTop && lineY <= canvasBottom) {
                Rectangle scissorArea = new Rectangle(lineX, canvasTop, lineWidth, canvasBounds.height);
                enableScissor(scissorArea);
                drawRect(lineX, lineY - 1, lineWidth, 2, 0xFFFF6600); // Orange line
                drawText("Target", lineX + 4, lineY - getTextHeight() - 2);
                disableScissor();
            }
        }
    }
}
