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
import net.kroia.stockmarket.util.StockMarketGuiElement;
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
        public static final Component P_GAIN_TOOLTIP = Component.translatable(PREFIX + "p_gain.tooltip");
        public static final Component I_GAIN_TOOLTIP = Component.translatable(PREFIX + "i_gain.tooltip");
        public static final Component D_GAIN_TOOLTIP = Component.translatable(PREFIX + "d_gain.tooltip");
        public static final Component RATE_TOOLTIP = Component.translatable(PREFIX + "rate.tooltip");
        public static final Component LABEL_MAX_ORDER_FACTOR = Component.translatable(PREFIX + "max_order_factor");
        public static final Component MAX_ORDER_FACTOR_TOOLTIP = Component.translatable(PREFIX + "max_order_factor.tooltip");
        public static final Component LABEL_MAX_TARGET_MOVE = Component.translatable(PREFIX + "max_target_move");
        public static final Component MAX_TARGET_MOVE_TOOLTIP = Component.translatable(PREFIX + "max_target_move.tooltip");
        public static final Component LABEL_REVERSION_TIME = Component.translatable(PREFIX + "reversion_time");
        public static final Component REVERSION_TIME_TOOLTIP = Component.translatable(PREFIX + "reversion_time.tooltip");
        public static final Component LABEL_OSCILLATION_PERIOD = Component.translatable(PREFIX + "oscillation_period");
        public static final Component OSCILLATION_PERIOD_TOOLTIP = Component.translatable(PREFIX + "oscillation_period.tooltip");
        public static final Component LABEL_OSCILLATION_DAMPING = Component.translatable(PREFIX + "oscillation_damping");
        public static final Component OSCILLATION_DAMPING_TOOLTIP = Component.translatable(PREFIX + "oscillation_damping.tooltip");
        public static final Component LABEL_OSCILLATION_AMPLITUDE = Component.translatable(PREFIX + "oscillation_amplitude");
        public static final Component OSCILLATION_AMPLITUDE_TOOLTIP = Component.translatable(PREFIX + "oscillation_amplitude.tooltip");
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
    /** Bot order limit as a multiple of the market's player order limit (TargetPriceBot.Settings#maxOrderVolumeFactor). */
    private final Label maxOrderFactorLabel;
    private final TextBox maxOrderFactorTextBox;
    /** #89: target rate limit in % per second (TargetPriceBot.Settings#maxTargetMovePerSecond x 100). */
    private final Label maxTargetMoveLabel;
    private final TextBox maxTargetMoveTextBox;
    /** T-208: reference reversion time in seconds (TargetPriceBot.Settings#reversionTimeSeconds, 0 = off). */
    private final Label reversionTimeLabel;
    private final TextBox reversionTimeTextBox;
    /** T-208: reference oscillation period in seconds. */
    private final Label oscillationPeriodLabel;
    private final TextBox oscillationPeriodTextBox;
    /** T-208: reference oscillation damping ratio. */
    private final Label oscillationDampingLabel;
    private final TextBox oscillationDampingTextBox;
    /** T-208: initial swing towards the target as a fraction of the spike. */
    private final Label oscillationAmplitudeLabel;
    private final TextBox oscillationAmplitudeTextBox;
    private final Button applyButton;
    private final Button applyAllButton;
    /** Holds Apply + Apply-to-all side by side as one row of the vertical settings list. */
    private final StockMarketGuiElement applyButtonRow;
    private List<ItemID> subscribedMarkets = new ArrayList<>();
    private Map<ItemID, TargetPriceBot.Settings> allSettings = new HashMap<>();
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

        // Bot order volume limit factor (non-negative, 0 = unlimited)
        maxOrderFactorLabel = new Label(Texts.LABEL_MAX_ORDER_FACTOR.getString());
        maxOrderFactorTextBox = new TextBox();
        maxOrderFactorTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 3));

        // Target rate limit in % per second (non-negative, 0 = no limit)
        maxTargetMoveLabel = new Label(Texts.LABEL_MAX_TARGET_MOVE.getString());
        maxTargetMoveTextBox = new TextBox();
        maxTargetMoveTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 4));

        // T-208 reference model (all non-negative)
        reversionTimeLabel = new Label(Texts.LABEL_REVERSION_TIME.getString());
        reversionTimeTextBox = new TextBox();
        reversionTimeTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 1));
        oscillationPeriodLabel = new Label(Texts.LABEL_OSCILLATION_PERIOD.getString());
        oscillationPeriodTextBox = new TextBox();
        oscillationPeriodTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 10, 1));
        oscillationDampingLabel = new Label(Texts.LABEL_OSCILLATION_DAMPING.getString());
        oscillationDampingTextBox = new TextBox();
        oscillationDampingTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 4, 3));
        oscillationAmplitudeLabel = new Label(Texts.LABEL_OSCILLATION_AMPLITUDE.getString());
        oscillationAmplitudeTextBox = new TextBox();
        oscillationAmplitudeTextBox.setMatchRegex(TextBox.createRegex_onlyNumerical(true, false, 4, 3));

        applyButton = new Button(Texts.APPLY_BUTTON.getString(), this::onApplySettings);
        applyAllButton = new Button(ApplyAllTexts.APPLY_ALL.getString(), () -> confirmApplyToAllMarkets(readSettings()));
        setSettingTooltip(ApplyAllTexts.APPLY_ALL_TOOLTIP, applyAllButton);
        applyButtonRow = new StockMarketGuiElement() {
            @Override
            protected void render() {}

            @Override
            protected void layoutChanged() {
                int halfW = (getWidth() - spacing) / 2;
                applyButton.setBounds(0, 0, halfW, getHeight());
                applyAllButton.setBounds(halfW + spacing, 0, getWidth() - halfW - spacing, getHeight());
            }
        };
        applyButtonRow.setEnableBackground(false);
        applyButtonRow.setEnableOutline(false);
        applyButtonRow.addChild(applyButton);
        applyButtonRow.addChild(applyAllButton);
        applyButtonRow.setHeight(20);

        setSettingTooltip(Texts.P_GAIN_TOOLTIP, pGainLabel, pGainTextBox);
        setSettingTooltip(Texts.I_GAIN_TOOLTIP, iGainLabel, iGainTextBox);
        setSettingTooltip(Texts.D_GAIN_TOOLTIP, dGainLabel, dGainTextBox);
        setSettingTooltip(Texts.RATE_TOOLTIP, rateLabel, rateTextBox);
        setSettingTooltip(Texts.MAX_ORDER_FACTOR_TOOLTIP, maxOrderFactorLabel, maxOrderFactorTextBox);
        setSettingTooltip(Texts.MAX_TARGET_MOVE_TOOLTIP, maxTargetMoveLabel, maxTargetMoveTextBox);
        setSettingTooltip(Texts.REVERSION_TIME_TOOLTIP, reversionTimeLabel, reversionTimeTextBox);
        setSettingTooltip(Texts.OSCILLATION_PERIOD_TOOLTIP, oscillationPeriodLabel, oscillationPeriodTextBox);
        setSettingTooltip(Texts.OSCILLATION_DAMPING_TOOLTIP, oscillationDampingLabel, oscillationDampingTextBox);
        setSettingTooltip(Texts.OSCILLATION_AMPLITUDE_TOOLTIP, oscillationAmplitudeLabel, oscillationAmplitudeTextBox);

        settingsListView.addChild(pGainLabel);
        settingsListView.addChild(pGainTextBox);
        settingsListView.addChild(iGainLabel);
        settingsListView.addChild(iGainTextBox);
        settingsListView.addChild(dGainLabel);
        settingsListView.addChild(dGainTextBox);
        settingsListView.addChild(rateLabel);
        settingsListView.addChild(rateTextBox);
        settingsListView.addChild(maxOrderFactorLabel);
        settingsListView.addChild(maxOrderFactorTextBox);
        settingsListView.addChild(maxTargetMoveLabel);
        settingsListView.addChild(maxTargetMoveTextBox);
        settingsListView.addChild(reversionTimeLabel);
        settingsListView.addChild(reversionTimeTextBox);
        settingsListView.addChild(oscillationPeriodLabel);
        settingsListView.addChild(oscillationPeriodTextBox);
        settingsListView.addChild(oscillationDampingLabel);
        settingsListView.addChild(oscillationDampingTextBox);
        settingsListView.addChild(oscillationAmplitudeLabel);
        settingsListView.addChild(oscillationAmplitudeTextBox);
        settingsListView.addChild(applyButtonRow);

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
    protected void onPluginSyncDataReceived(PluginSyncData data, @Nullable Map<ItemID, TargetPriceBot.Settings> customSettingsMap) {
        this.subscribedMarkets = data.getSubscribedMarkets();

        // Populate the item selection view with subscribed market items, sorted by market type
        List<ItemID> sortedMarkets = new ArrayList<>(subscribedMarkets);
        sortedMarkets.sort(MARKET_TYPE_COMPARATOR);
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemID id : sortedMarkets) {
            ItemStack stack = id.getStack();
            if (stack != null) {
                stacks.add(stack);
            }
        }
        marketSelectionView.setItems(stacks);

        // Store per-market settings
        if (customSettingsMap != null) {
            allSettings = new HashMap<>(customSettingsMap);
        }
        // Populate textboxes if a market is already selected
        if (selectedMarketID != null && allSettings.containsKey(selectedMarketID)) {
            populateSettingsFromMarket(selectedMarketID);
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
            populateSettingsFromMarket(itemID);
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
            chart.drawText(Component.translatable("gui.stockmarket.news_plugin.details_table_target").getString(), canvasBounds.x + 4, lineY - chart.getTextHeight() - 2);
        }
    }

    /**
     * Populates the PID textboxes from the stored settings for the given market.
     *
     * @param marketID the market whose settings should be displayed
     */
    private void populateSettingsFromMarket(ItemID marketID) {
        TargetPriceBot.Settings s = allSettings.get(marketID);
        if (s != null) {
            pGainTextBox.setText(s.pidP(), 6);
            iGainTextBox.setText(s.pidI(), 6);
            dGainTextBox.setText(s.pidD(), 6);
            rateTextBox.setText(s.pidRate(), 6);
            maxOrderFactorTextBox.setText(s.maxOrderVolumeFactor(), 3);
            maxTargetMoveTextBox.setText(s.maxTargetMovePerSecond() * 100, 4);
            reversionTimeTextBox.setText(s.reversionTimeSeconds(), 1);
            oscillationPeriodTextBox.setText(s.oscillationPeriodSeconds(), 1);
            oscillationDampingTextBox.setText(s.oscillationDamping(), 3);
            oscillationAmplitudeTextBox.setText(s.oscillationAmplitude(), 3);
        }
    }

    /**
     * Sends the current PID gain input values to the server as typed settings
     * for the currently selected market.
     */
    @Override
    protected void onCustomSettingsResponse(boolean success, @Nullable ItemID marketID, @Nullable TargetPriceBot.Settings confirmedSettings) {
        if (success && marketID != null && confirmedSettings != null) {
            allSettings.put(marketID, confirmedSettings);
        }
    }

    /** Builds the bot settings (PID gains, limits, T-208 reference model) from the current input values. */
    private TargetPriceBot.Settings readSettings() {
        return new TargetPriceBot.Settings(
                (float) pGainTextBox.getDouble(),
                (float) iGainTextBox.getDouble(),
                (float) dGainTextBox.getDouble(),
                (float) rateTextBox.getDouble(),
                (float) Math.max(0, maxOrderFactorTextBox.getDouble()),
                (float) Math.max(0, maxTargetMoveTextBox.getDouble() / 100),
                (float) Math.max(0, reversionTimeTextBox.getDouble()),
                (float) Math.max(0, oscillationPeriodTextBox.getDouble()),
                (float) Math.max(0, oscillationDampingTextBox.getDouble()),
                (float) Math.max(0, oscillationAmplitudeTextBox.getDouble())
        );
    }

    private void onApplySettings() {
        if (selectedMarketID == null) return;
        sendCustomSettings(selectedMarketID, readSettings());
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
