package net.kroia.stockmarket.pluginsystem.plugins.screen;

import io.netty.buffer.ByteBuf;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.plugins.TargetPriceBot;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.screen.widgets.OrderbookVolumeHistogram;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class TargetPriceBotGuiElement extends PluginGuiElement<TargetPriceBot.Settings, TargetPriceBot.RuntimeStreamData> {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".target_price_bot.";
        public static final Component SETTINGS_TITLE = Component.translatable(PREFIX + "settings_title");
        public static final Component LABEL_P_GAIN = Component.translatable(PREFIX + "p_gain");
        public static final Component LABEL_I_GAIN = Component.translatable(PREFIX + "i_gain");
        public static final Component LABEL_D_GAIN = Component.translatable(PREFIX + "d_gain");
        public static final Component LABEL_RATE = Component.translatable(PREFIX + "rate");
        public static final Component APPLY_BUTTON = Component.translatable(PREFIX + "apply");
    }

    private final CandlestickChart candlestickChart;
    private final OrderbookVolumeHistogram orderbookVolumeHistogram;
    private final ItemSelectionView marketSelectionView;
    private final ListView settingsListView;
    private final Label settingsTitle;
    private final Label pGainLabel;
    private final TextBox pGainTextBox;
    private final Label iGainLabel;
    private final TextBox iGainTextBox;
    private final Label dGainLabel;
    private final TextBox dGainTextBox;
    private final Label rateLabel;
    private final TextBox rateTextBox;
    private final Button applyButton;
    private List<ItemID> subscribedMarkets = new ArrayList<>();
    private final Map<Short, Double> marketTargetPrices = new HashMap<>();
    private @Nullable ItemID selectedMarketID;
    private @Nullable ClientMarket currentMarket;
    private @Nullable CandlestickChart sharedChart;
    private @Nullable CandlestickChart.Overlay chartOverlay;

    /**
     * Creates the TargetPriceBot GUI element.
     * Market data is populated later via {@link #onPluginSyncDataReceived(PluginSyncData)}.
     */
    public TargetPriceBotGuiElement() {
        candlestickChart = new CandlestickChart();
        candlestickChart.addOverlay(this::renderChartOverlay);
        orderbookVolumeHistogram = new OrderbookVolumeHistogram(candlestickChart);

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

        // PID gain inputs
        pGainLabel = new Label(Texts.LABEL_P_GAIN.getString());
        pGainTextBox = new TextBox();
        pGainTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, true, 10, 6));

        iGainLabel = new Label(Texts.LABEL_I_GAIN.getString());
        iGainTextBox = new TextBox();
        iGainTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, true, 10, 6));

        dGainLabel = new Label(Texts.LABEL_D_GAIN.getString());
        dGainTextBox = new TextBox();
        dGainTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, true, 10, 6));

        rateLabel = new Label(Texts.LABEL_RATE.getString());
        rateTextBox = new TextBox();
        rateTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, true, 10, 6));

        applyButton = new Button(Texts.APPLY_BUTTON.getString(), this::onApplySettings);
        applyButton.setHeight(20);

        settingsListView.addChild(pGainLabel);
        settingsListView.addChild(pGainTextBox);
        settingsListView.addChild(iGainLabel);
        settingsListView.addChild(iGainTextBox);
        settingsListView.addChild(dGainLabel);
        settingsListView.addChild(dGainTextBox);
        settingsListView.addChild(rateLabel);
        settingsListView.addChild(rateTextBox);
        settingsListView.addChild(applyButton);

        addChild(candlestickChart);
        addChild(orderbookVolumeHistogram);
        addChild(marketSelectionView);
        addChild(settingsListView);
    }

    @Override
    public void setCandlestickChart(@Nullable CandlestickChart chart) {
        if (sharedChart != null && chartOverlay != null) {
            sharedChart.removeOverlay(chartOverlay);
        }
        sharedChart = chart;
        if (chart != null) {
            if (chartOverlay == null) chartOverlay = this::renderChartOverlay;
            chart.addOverlay(chartOverlay);
        }
    }

    @Override
    public boolean needsCustomScreen() {
        return true;
    }

    @Override
    protected StreamCodec<ByteBuf, TargetPriceBot.Settings> customSettingsCodec() {
        return TargetPriceBot.Settings.CODEC;
    }

    @Override
    protected StreamCodec<ByteBuf, TargetPriceBot.RuntimeStreamData> runtimeDataCodec() {
        return TargetPriceBot.RuntimeStreamData.CODEC;
    }

    /**
     * Populates the market selection view from the plugin's sync data
     * and starts the runtime data stream for live target price updates.
     *
     * @param data           the plugin sync data containing subscribed markets
     * @param customSettings the decoded PID settings, or null if not available
     */
    @Override
    protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable TargetPriceBot.Settings customSettings) {
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

        // Populate settings from decoded custom settings
        if (customSettings != null) {
            pGainTextBox.setText(customSettings.pidP());
            iGainTextBox.setText(customSettings.pidI());
            dGainTextBox.setText(customSettings.pidD());
            rateTextBox.setText(customSettings.pidRate());
        }

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
     * Processes decoded runtime data to extract target prices.
     *
     * @param data the decoded runtime data containing target prices per market
     */
    @Override
    protected void onRuntimeDataReceived(TargetPriceBot.RuntimeStreamData data) {
        marketTargetPrices.clear();
        for (TargetPriceBot.RuntimeStreamData.MarketTargetPrice entry : data.entries()) {
            marketTargetPrices.put(entry.itemId(), entry.targetPrice());
        }
    }

    private void renderChartOverlay(CandlestickChart chart) {
        ClientMarket market = chart.getMarket();
        if (market == null) return;

        Double target = marketTargetPrices.get(market.getItemID().getShort());
        if (target == null) return;

        Rectangle canvasBounds = chart.getCanvasBounds();
        int lineY = chart.toCanvasSpaceY(target);

        if (lineY >= canvasBounds.y && lineY <= canvasBounds.y + canvasBounds.height) {
            chart.drawRect(canvasBounds.x, lineY - 1, canvasBounds.width, 2, 0xFFFF6600);
            chart.drawText("Target", canvasBounds.x + 4, lineY - chart.getTextHeight() - 2);
        }
    }

    /**
     * Sends the current PID gain input values to the server as typed settings.
     */
    private void onApplySettings() {
        sendCustomSettings(new TargetPriceBot.Settings(
                (float) pGainTextBox.getDouble(),
                (float) iGainTextBox.getDouble(),
                (float) dGainTextBox.getDouble(),
                (float) rateTextBox.getDouble()
        ));
    }

    @Override
    protected void layoutChanged() {
        int w = getWidth();
        int h = getHeight();
        int halfW = w / 2;
        int halfH = h / 2;
        int histW = halfW / 10;

        candlestickChart.setBounds(0, 0, halfW - histW, halfH);
        orderbookVolumeHistogram.setBounds(candlestickChart.getRight(), 0, histW, halfH);
        marketSelectionView.setBounds(0, halfH + spacing, halfW, h - halfH - spacing);
        settingsListView.setBounds(halfW + spacing, 0, w - halfW - spacing, h);
    }

    @Override
    protected void render() {
        // Overlays are now rendered via CandlestickChart.Overlay callbacks
    }
}
