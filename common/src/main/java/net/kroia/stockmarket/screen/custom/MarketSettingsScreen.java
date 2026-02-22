package net.kroia.stockmarket.screen.custom;

import dev.architectury.event.events.common.TickEvent;
import net.kroia.modutilities.TimerMillis;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.ServerMarketSettingsData;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.ClientMarketPluginGuiElement;
import net.kroia.stockmarket.screen.uiElements.TradingPairView;
import net.kroia.stockmarket.screen.uiElements.chart.TradingChartWidget;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MarketSettingsScreen extends StockMarketGuiScreen {
    public final float textFontSize = StockMarketGuiElement.hoverToolTipFontSize;
    public final int elementHeight = 15;
    public final int spacing = 3;
    public final int padding = 3;

    public static final class TEXTS
    {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".market_settings_screen.";

        public static final Component TITLE = Component.translatable(PREFIX + "title");
        public static final Component SAVE_BUTTON = Component.translatable(PREFIX + "save_button");
        public static final Component BACK_BUTTON = Component.translatable(PREFIX + "back_button");
        public static final Component OPEN_PLUGIN_BROWSER_BUTTON = Component.translatable(PREFIX + "open_plugin_browser_button");


        // GeneralGui
        public static final Component GENERAL_TITLE = Component.translatable(PREFIX + "general.title");
        public static final Component GENERAL_CHART_RESET = Component.translatable(PREFIX + "general.chart_reset");
        public static final Component GENERAL_CHART_RESET_TOOLTIP = Component.translatable(PREFIX + "general.chart_reset.tooltip");
        public static final Component GENERAL_IS_MARKET_OPEN = Component.translatable(PREFIX + "general.is_market_open");
        public static final Component GENERAL_CANDLE_TIME = Component.translatable(PREFIX + "general.candle_time_min");
        public static final Component ITEM_IMBALANCE = Component.translatable(PREFIX + "general.item_imbalance");
        // GeneralGui Tooltips
        public static final Component GENERAL_IS_MARKET_OPEN_TOOLTIP = Component.translatable(PREFIX + "general.is_market_open.tooltip");
        public static final Component ITEM_IMBALANCE_TOOLTIP = Component.translatable(PREFIX + "general.item_imbalance.tooltip");



        // Virtual Order Book
        public static final Component VIRTUAL_ORDER_BOOK_TITLE = Component.translatable(PREFIX + "virtual_order_book.title");
        public static final Component VIRTUAL_ORDER_BOOK_ENABLE = Component.translatable(PREFIX + "virtual_order_book.enable");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_SCALE = Component.translatable(PREFIX + "virtual_order_book.volume_scale");
        public static final Component VIRTUAL_ORDER_BOOK_NEAR_MARKET_VOLUME_SCALE = Component.translatable(PREFIX + "virtual_order_book.near_market_volume_scale");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_ACCUMULATION_RATE = Component.translatable(PREFIX + "virtual_order_book.volume_accumulation_rate");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_FAST_ACCUMULATION_RATE = Component.translatable(PREFIX + "virtual_order_book.volume_fast_accumulation_rate");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_DECUMULATION_RATE = Component.translatable(PREFIX + "virtual_order_book.volume_decumulation_rate");

        // Virtual Order Book Tooltips
        public static final Component VIRTUAL_ORDER_BOOK_ENABLE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.enable.tooltip");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_SCALE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.volume_scale.tooltip");
        public static final Component VIRTUAL_ORDER_BOOK_NEAR_MARKET_VOLUME_SCALE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.near_market_volume_scale.tooltip");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_ACCUMULATION_RATE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.volume_accumulation_rate.tooltip");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_FAST_ACCUMULATION_RATE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.volume_fast_accumulation_rate.tooltip");
        public static final Component VIRTUAL_ORDER_BOOK_VOLUME_DECUMULATION_RATE_TOOLTIP = Component.translatable(PREFIX + "virtual_order_book.volume_decumulation_rate.tooltip");


        // BotGui
        public static final Component BOT_SETTINGS_TITLE = Component.translatable(PREFIX + "bot_settings.title");
        public static final Component BOT_SETTINGS_ENABLE = Component.translatable(PREFIX + "bot_settings.enable");
        public static final Component BOT_SETTINGS_ENABLE_TARGET_PRICE = Component.translatable(PREFIX + "bot_settings.enable_target_price");
        public static final Component BOT_SETTINGS_ENABLE_VOLUME_TRACKING = Component.translatable(PREFIX + "bot_settings.enable_volume_tracking");
        public static final Component BOT_SETTINGS_ENABLE_RANDOM_WALK = Component.translatable(PREFIX + "bot_settings.enable_random_walk");
        public static final Component BOT_SETTINGS_DEFAULT_PRICE = Component.translatable(PREFIX + "bot_settings.default_price");
        public static final Component BOT_SETTINGS_UPDATE_INTERVAL_MS = Component.translatable(PREFIX + "bot_settings.update_interval_ms");
        public static final Component BOT_SETTINGS_VOLUME_SCALE = Component.translatable(PREFIX + "bot_settings.volume_scale");
        public static final Component BOT_SETTINGS_TARGET_PRICE_STEERING_FAC = Component.translatable(PREFIX + "bot_settings.target_price_steering_fac");
        public static final Component BOT_SETTINGS_VOLUME_STEERING_FAC = Component.translatable(PREFIX + "bot_settings.volume_steering_fac");
        public static final Component BOT_SETTINGS_VOLATILITY = Component.translatable(PREFIX + "bot_settings.volatility");

        // BotGui Tooltips
        public static final Component BOT_SETTINGS_ENABLE_TOOLTIP = Component.translatable(PREFIX + "bot_settings.enable.tooltip");
        public static final Component BOT_SETTINGS_DEFAULT_PRICE_TOOLTIP = Component.translatable(PREFIX + "bot_settings.default_price.tooltip");
        public static final Component BOT_SETTINGS_UPDATE_INTERVAL_MS_TOOLTIP = Component.translatable(PREFIX + "bot_settings.update_interval_ms.tooltip");
        public static final Component BOT_SETTINGS_VOLUME_SCALE_TOOLTIP = Component.translatable(PREFIX + "bot_settings.volume_scale.tooltip");
        public static final Component BOT_SETTINGS_ENABLE_TARGET_PRICE_TOOLTIP = Component.translatable(PREFIX + "bot_settings.enable_target_price.tooltip");
        public static final Component BOT_SETTINGS_TARGET_PRICE_STEERING_FAC_TOOLTIP = Component.translatable(PREFIX + "bot_settings.target_price_steering_fac.tooltip");
        public static final Component BOT_SETTINGS_ENABLE_VOLUME_TRACKING_TOOLTIP = Component.translatable(PREFIX + "bot_settings.enable_volume_tracking.tooltip");
        public static final Component BOT_SETTINGS_VOLUME_STEERING_FAC_TOOLTIP = Component.translatable(PREFIX + "bot_settings.volume_steering_fac.tooltip");
        public static final Component BOT_SETTINGS_ENABLE_RANDOM_WALK_TOOLTIP = Component.translatable(PREFIX + "bot_settings.enable_random_walk.tooltip");
        public static final Component BOT_SETTINGS_VOLATILITY_TOOLTIP = Component.translatable(PREFIX + "bot_settings.volatility.tooltip");
    }


    private class GeneralGuiElement extends StockMarketGuiElement
    {
        public final Label titleLabel;
        public final Button chartResetButton;
        public final CheckBox isMarketOpenCheckBox;
        public final Label candleTimeMinLabel;
        public final TextBox candleTimeMin;
        public final Label itemImbalanceLabel;
        public final TextBox itemImbalanceTextBox;


        public GeneralGuiElement() {
            super();
            //this.setEnableBackground(false);
            titleLabel = new Label(TEXTS.GENERAL_TITLE.getString());
            titleLabel.setAlignment(Alignment.CENTER);
            chartResetButton = new Button(TEXTS.GENERAL_CHART_RESET.getString(), () -> {
                if(getSelectedMarket() != null)
                {
                    getSelectedMarket().requestChartReset((result)->{});
                }
            });
            chartResetButton.setHoverTooltipSupplier(TEXTS.GENERAL_CHART_RESET_TOOLTIP::getString);
            chartResetButton.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
            isMarketOpenCheckBox = new CheckBox(TEXTS.GENERAL_IS_MARKET_OPEN.getString());
            isMarketOpenCheckBox.setTextAlignment(Alignment.RIGHT);
            candleTimeMinLabel = new Label(TEXTS.GENERAL_CANDLE_TIME.getString());
            candleTimeMinLabel.setAlignment(Alignment.RIGHT);
            candleTimeMin = new TextBox();
            candleTimeMin.setAllowNumbers(true,false);
            candleTimeMin.setAllowLetters(false);
            itemImbalanceLabel = new Label(TEXTS.ITEM_IMBALANCE.getString());
            itemImbalanceLabel.setAlignment(Alignment.RIGHT);
            itemImbalanceTextBox = new TextBox();
            itemImbalanceTextBox.setAllowNumbers(true,false);
            itemImbalanceTextBox.setAllowLetters(false);




            isMarketOpenCheckBox.setHoverTooltipSupplier(TEXTS.GENERAL_IS_MARKET_OPEN_TOOLTIP::getString);
            candleTimeMinLabel.setHoverTooltipSupplier(()-> StockMarketTextMessages.getMarketSettingsScreenCandleTimeTooltip(candleTimeMin.getInt()));
            candleTimeMin.setHoverTooltipSupplier(()-> StockMarketTextMessages.getMarketSettingsScreenCandleTimeTooltip(candleTimeMin.getInt()));
            itemImbalanceLabel.setHoverTooltipSupplier(TEXTS.ITEM_IMBALANCE_TOOLTIP::getString);
            itemImbalanceTextBox.setHoverTooltipSupplier(TEXTS.ITEM_IMBALANCE_TOOLTIP::getString);


            addChild(titleLabel);
            addChild(chartResetButton);
            addChild(isMarketOpenCheckBox);
            addChild(candleTimeMinLabel);
            addChild(candleTimeMin);
            addChild(itemImbalanceLabel);
            addChild(itemImbalanceTextBox);

            for(GuiElement child : getChilds())
            {
                child.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
                child.setHoverTooltipFontScale(textFontSize);
                child.setTextFontScale(textFontSize);
            }

            int targetHeight = (elementHeight) * 5 + padding*2;
            this.setHeight(targetHeight);
        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth() - padding * 2;

            //int height = getHeight() - padding * 2;

            int y = padding;
            titleLabel.setBounds(padding, y, width, elementHeight);
            y += elementHeight;
            chartResetButton.setBounds(padding, y, width, elementHeight);
            y += elementHeight;
            isMarketOpenCheckBox.setBounds(width/2+padding, y, width-width/2, elementHeight);
            y += elementHeight;
            candleTimeMinLabel.setBounds(padding, y, width/2, elementHeight);
            candleTimeMin.setBounds(candleTimeMinLabel.getRight(), candleTimeMinLabel.getTop(), width - candleTimeMinLabel.getWidth(), candleTimeMinLabel.getHeight());
            itemImbalanceLabel.setBounds(padding, y + elementHeight, width/2, elementHeight);
            itemImbalanceTextBox.setBounds(itemImbalanceLabel.getRight(), itemImbalanceLabel.getTop(), width - itemImbalanceLabel.getWidth(), itemImbalanceLabel.getHeight());
        }


        public void setMarketOpen(boolean isOpen) {
            isMarketOpenCheckBox.setChecked(isOpen);
        }
        public boolean isMarketOpen() {
            return isMarketOpenCheckBox.isChecked();
        }

        public void setShiftPriceCandleIntervalMS(long shiftPriceCandleIntervalMS)
        {
            if(shiftPriceCandleIntervalMS <= 0)
                candleTimeMin.setText("");
            else
                candleTimeMin.setText(String.valueOf(shiftPriceCandleIntervalMS/60000));
        }
        public long getShiftPriceCandleIntervalMS() {
            if(candleTimeMin.getText().isEmpty())
                return 0;
            long value = candleTimeMin.getLong();
            if(value < 1)
                value = 1;
            return value * 60000; // Convert minutes to milliseconds
        }
        public void setItemImbalance(long itemImbalance) {
            itemImbalanceTextBox.setText(String.valueOf(itemImbalance));
        }
        public long getItemImbalance() {
            if(itemImbalanceTextBox.getText().isEmpty())
                return 0;
            long value = itemImbalanceTextBox.getLong();
            return value;
        }
    }
/*
    public class VirtualOderBookGuiElement extends StockMarketGuiElement
    {
        VirtualOrderBook.Settings virtualOrderBookSettings;

        private final Label titleLabel;
        private final CheckBox enableCheckBox;
        private final Label volumeScaleLabel;
        private final TextBox volumeScaleTextBox;
        private final Label nearMarketVolumeScaleLabel;
        private final TextBox nearMarketVolumeScaleTextBox;
        private final Label volumeAccumulationRateLabel;
        private final TextBox volumeAccumulationRateTextBox;
        private final Label volumeFastAccumulationRateLabel;
        private final TextBox volumeFastAccumulationRateTextBox;
        private final Label volumeDecumulationRateLabel;
        private final TextBox volumeDecumulationRateTextBox;
        public VirtualOderBookGuiElement() {
            super();
            this.setEnableBackground(false);

            titleLabel = new Label(TEXTS.VIRTUAL_ORDER_BOOK_TITLE.getString());
            titleLabel.setAlignment(Alignment.CENTER);
            enableCheckBox = new CheckBox(TEXTS.VIRTUAL_ORDER_BOOK_ENABLE.getString());
            enableCheckBox.setTextAlignment(Alignment.RIGHT);
            volumeScaleLabel = new Label(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_SCALE.getString());
            volumeScaleLabel.setAlignment(Alignment.RIGHT);
            volumeScaleTextBox = new TextBox();
            volumeScaleTextBox.setAllowNumbers(true,true);
            volumeScaleTextBox.setAllowLetters(false);
            nearMarketVolumeScaleLabel = new Label(TEXTS.VIRTUAL_ORDER_BOOK_NEAR_MARKET_VOLUME_SCALE.getString());
            nearMarketVolumeScaleLabel.setAlignment(Alignment.RIGHT);
            nearMarketVolumeScaleTextBox = new TextBox();
            nearMarketVolumeScaleTextBox.setAllowNumbers(true,true);
            nearMarketVolumeScaleTextBox.setAllowLetters(false);
            volumeAccumulationRateLabel = new Label(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_ACCUMULATION_RATE.getString());
            volumeAccumulationRateLabel.setAlignment(Alignment.RIGHT);
            volumeAccumulationRateTextBox = new TextBox();
            volumeAccumulationRateTextBox.setAllowNumbers(true,true);
            volumeAccumulationRateTextBox.setAllowLetters(false);
            volumeFastAccumulationRateLabel = new Label(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_FAST_ACCUMULATION_RATE.getString());
            volumeFastAccumulationRateLabel.setAlignment(Alignment.RIGHT);
            volumeFastAccumulationRateTextBox = new TextBox();
            volumeFastAccumulationRateTextBox.setAllowNumbers(true,true);
            volumeFastAccumulationRateTextBox.setAllowLetters(false);
            volumeDecumulationRateLabel = new Label(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_DECUMULATION_RATE.getString());
            volumeDecumulationRateLabel.setAlignment(Alignment.RIGHT);
            volumeDecumulationRateTextBox = new TextBox();
            volumeDecumulationRateTextBox.setAllowNumbers(true,true);
            volumeDecumulationRateTextBox.setAllowLetters(false);

            enableCheckBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_ENABLE_TOOLTIP::getString);
            volumeScaleLabel.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_SCALE_TOOLTIP::getString);
            volumeScaleTextBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_SCALE_TOOLTIP::getString);
            nearMarketVolumeScaleLabel.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_NEAR_MARKET_VOLUME_SCALE_TOOLTIP::getString);
            nearMarketVolumeScaleTextBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_NEAR_MARKET_VOLUME_SCALE_TOOLTIP::getString);
            volumeAccumulationRateLabel.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_ACCUMULATION_RATE_TOOLTIP::getString);
            volumeAccumulationRateTextBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_ACCUMULATION_RATE_TOOLTIP::getString);
            volumeFastAccumulationRateLabel.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_FAST_ACCUMULATION_RATE_TOOLTIP::getString);
            volumeFastAccumulationRateTextBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_FAST_ACCUMULATION_RATE_TOOLTIP::getString);
            volumeDecumulationRateLabel.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_DECUMULATION_RATE_TOOLTIP::getString);
            volumeDecumulationRateTextBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_DECUMULATION_RATE_TOOLTIP::getString);

            enableCheckBox.setOnStateChanged((checked)->
            {
                volumeScaleLabel.setEnabled(checked);
                volumeScaleTextBox.setEnabled(checked);
                nearMarketVolumeScaleLabel.setEnabled(checked);
                nearMarketVolumeScaleTextBox.setEnabled(checked);
                volumeAccumulationRateLabel.setEnabled(checked);
                volumeAccumulationRateTextBox.setEnabled(checked);
                volumeFastAccumulationRateLabel.setEnabled(checked);
                volumeFastAccumulationRateTextBox.setEnabled(checked);
                volumeDecumulationRateLabel.setEnabled(checked);
                volumeDecumulationRateTextBox.setEnabled(checked);
            });

            addChild(titleLabel);
            addChild(enableCheckBox);
            addChild(volumeScaleLabel);
            addChild(volumeScaleTextBox);
            addChild(nearMarketVolumeScaleLabel);
            addChild(nearMarketVolumeScaleTextBox);
            addChild(volumeAccumulationRateLabel);
            addChild(volumeAccumulationRateTextBox);
            addChild(volumeFastAccumulationRateLabel);
            addChild(volumeFastAccumulationRateTextBox);
            addChild(volumeDecumulationRateLabel);
            addChild(volumeDecumulationRateTextBox);

            for(GuiElement child : getChilds())
            {
                child.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
                child.setHoverTooltipFontScale(textFontSize);
                child.setTextFontScale(textFontSize);
            }

            int targetHeight = (elementHeight+spacing) * 7 + 5;
            this.setHeight(targetHeight);
        }

        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth() - padding * 2;
            //int height = getHeight() - padding * 2;
            int elementHeight = 15;

            int y = padding;
            titleLabel.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            enableCheckBox.setBounds(width/2+padding, y, width-width/2, elementHeight);
            y += elementHeight + spacing;
            volumeScaleLabel.setBounds(padding, y, width/2, elementHeight);
            volumeScaleTextBox.setBounds(volumeScaleLabel.getRight(), volumeScaleLabel.getTop(), width-volumeScaleLabel.getWidth(), volumeScaleLabel.getHeight());
            y += elementHeight + spacing;
            nearMarketVolumeScaleLabel.setBounds(padding, y, width/2, elementHeight);
            nearMarketVolumeScaleTextBox.setBounds(nearMarketVolumeScaleLabel.getRight(), nearMarketVolumeScaleLabel.getTop(), width-nearMarketVolumeScaleLabel.getWidth(), nearMarketVolumeScaleLabel.getHeight());
            y += elementHeight + spacing;
            volumeAccumulationRateLabel.setBounds(padding, y, width/2, elementHeight);
            volumeAccumulationRateTextBox.setBounds(volumeAccumulationRateLabel.getRight(), volumeAccumulationRateLabel.getTop(), width-volumeAccumulationRateLabel.getWidth(), volumeAccumulationRateLabel.getHeight());
            y += elementHeight + spacing;
            volumeFastAccumulationRateLabel.setBounds(padding, y, width/2, elementHeight);
            volumeFastAccumulationRateTextBox.setBounds(volumeFastAccumulationRateLabel.getRight(), volumeFastAccumulationRateLabel.getTop(), width-volumeFastAccumulationRateLabel.getWidth(), volumeFastAccumulationRateLabel.getHeight());
            y += elementHeight + spacing;
            volumeDecumulationRateLabel.setBounds(padding, y, width/2, elementHeight);
            volumeDecumulationRateTextBox.setBounds(volumeDecumulationRateLabel.getRight(), volumeDecumulationRateLabel.getTop(), width-volumeDecumulationRateLabel.getWidth(), volumeDecumulationRateLabel.getHeight());
        }

        public void setVirtualOrderBookSettings(VirtualOrderBook.Settings settings)
        {
            this.virtualOrderBookSettings = settings;

            if(virtualOrderBookSettings == null)
            {
                enableCheckBox.setChecked(false);
            }
            else
            {
                enableCheckBox.setChecked(true);
                volumeScaleTextBox.setText(String.valueOf(virtualOrderBookSettings.volumeScale));
                nearMarketVolumeScaleTextBox.setText(String.valueOf(virtualOrderBookSettings.nearMarketVolumeScale));
                volumeAccumulationRateTextBox.setText(String.valueOf(virtualOrderBookSettings.volumeAccumulationRate));
                volumeFastAccumulationRateTextBox.setText(String.valueOf(virtualOrderBookSettings.volumeFastAccumulationRate));
                volumeDecumulationRateTextBox.setText(String.valueOf(virtualOrderBookSettings.volumeDecumulationRate));
            }
        }
        public VirtualOrderBook.Settings getVirtualOrderBookSettings() {
            if(enableCheckBox.isChecked())
            {
                virtualOrderBookSettings = new VirtualOrderBook.Settings();
                virtualOrderBookSettings.volumeScale = 100f;
                virtualOrderBookSettings.nearMarketVolumeScale = 2f;
                virtualOrderBookSettings.volumeAccumulationRate = 0.01f;
                virtualOrderBookSettings.volumeFastAccumulationRate = 0.5f;
                virtualOrderBookSettings.volumeDecumulationRate = 0.005f;
            }
            else
                virtualOrderBookSettings = null;
            if(virtualOrderBookSettings != null) {
                virtualOrderBookSettings.volumeScale = getInRange((float)volumeScaleTextBox.getDouble(), 0.f, 100000.f);
                volumeScaleTextBox.setText(String.valueOf(virtualOrderBookSettings.volumeScale));
                virtualOrderBookSettings.nearMarketVolumeScale = getInRange((float)nearMarketVolumeScaleTextBox.getDouble(), 0.f, 100000.f);
                nearMarketVolumeScaleTextBox.setText(String.valueOf(virtualOrderBookSettings.nearMarketVolumeScale));
                virtualOrderBookSettings.volumeAccumulationRate = getInRange((float)volumeAccumulationRateTextBox.getDouble(), 0.f, 100000.f);
                volumeAccumulationRateTextBox.setText(String.valueOf(virtualOrderBookSettings.volumeAccumulationRate));
                virtualOrderBookSettings.volumeFastAccumulationRate = getInRange((float)volumeFastAccumulationRateTextBox.getDouble(), 0.f, 100000.f);
                volumeFastAccumulationRateTextBox.setText(String.valueOf(virtualOrderBookSettings.volumeFastAccumulationRate));
                virtualOrderBookSettings.volumeDecumulationRate = getInRange((float)volumeDecumulationRateTextBox.getDouble(), 0.f, 100000.f);
                volumeDecumulationRateTextBox.setText(String.valueOf(virtualOrderBookSettings.volumeDecumulationRate));
            }
            return virtualOrderBookSettings;
        }
    }

    private class BotGuiElement extends StockMarketGuiElement
    {
        private ServerVolatilityBot.Settings botSettings;

        private final Label titleLabel;
        private final CheckBox enableCheckBox;
        private final Label defaultPriceLabel;
        private final TextBox defaultPriceTextBox;
        private final Label updateTimerInvervalMSLabel;
        private final TextBox updateTimerInvervalMSTextBox;
        private final Label volumeScaleLabel;
        private final TextBox volumeScaleTextBox;
        private final CheckBox enableTargetPriceCheckBox;
        private final Label targetPriceSteeringFactorLabel;
        private final TextBox targetPriceSteeringFactorTextBox;
        private final CheckBox enableVolumeTrackingCheckBox;
        private final Label volumeSteeringFactorLabel;
        private final TextBox volumeSteeringFactorTextBox;
        private final CheckBox enableRandomWalkCheckBox;
        private final Label volatilityLabel;
        private final TextBox volatilityTextBox;
        public BotGuiElement() {
            super();
            this.setEnableBackground(false);

            titleLabel = new Label(TEXTS.BOT_SETTINGS_TITLE.getString());
            titleLabel.setAlignment(Alignment.CENTER);
            enableCheckBox = new CheckBox(TEXTS.BOT_SETTINGS_ENABLE.getString());
            enableCheckBox.setTextAlignment(Alignment.RIGHT);
            defaultPriceLabel = new Label(TEXTS.BOT_SETTINGS_DEFAULT_PRICE.getString());
            defaultPriceLabel.setAlignment(Alignment.RIGHT);
            defaultPriceTextBox = new TextBox();
            defaultPriceTextBox.setAllowNumbers(true,true);
            defaultPriceTextBox.setAllowLetters(false);
            defaultPriceTextBox.setAllowNegativeNumbers(false);
            defaultPriceTextBox.setMaxDecimalChar(0);
            updateTimerInvervalMSLabel = new Label(TEXTS.BOT_SETTINGS_UPDATE_INTERVAL_MS.getString());
            updateTimerInvervalMSLabel.setAlignment(Alignment.RIGHT);
            updateTimerInvervalMSTextBox = new TextBox();
            updateTimerInvervalMSTextBox.setAllowNumbers(true,false);
            updateTimerInvervalMSTextBox.setAllowLetters(false);
            volumeScaleLabel = new Label(TEXTS.BOT_SETTINGS_VOLUME_SCALE.getString());
            volumeScaleLabel.setAlignment(Alignment.RIGHT);
            volumeScaleTextBox = new TextBox();
            volumeScaleTextBox.setAllowNumbers(true,false);
            volumeScaleTextBox.setAllowLetters(false);
            enableTargetPriceCheckBox = new CheckBox(TEXTS.BOT_SETTINGS_ENABLE_TARGET_PRICE.getString());
            enableTargetPriceCheckBox.setTextAlignment(Alignment.RIGHT);
            targetPriceSteeringFactorLabel = new Label(TEXTS.BOT_SETTINGS_TARGET_PRICE_STEERING_FAC.getString());
            targetPriceSteeringFactorLabel.setAlignment(Alignment.RIGHT);
            targetPriceSteeringFactorTextBox = new TextBox();
            targetPriceSteeringFactorTextBox.setAllowNumbers(true,true);
            targetPriceSteeringFactorTextBox.setAllowLetters(false);
            enableVolumeTrackingCheckBox = new CheckBox(TEXTS.BOT_SETTINGS_ENABLE_VOLUME_TRACKING.getString());
            enableVolumeTrackingCheckBox.setTextAlignment(Alignment.RIGHT);
            volumeSteeringFactorLabel = new Label(TEXTS.BOT_SETTINGS_VOLUME_STEERING_FAC.getString());
            volumeSteeringFactorLabel.setAlignment(Alignment.RIGHT);
            volumeSteeringFactorTextBox = new TextBox();
            volumeSteeringFactorTextBox.setAllowNumbers(true,true);
            volumeSteeringFactorTextBox.setAllowLetters(false);
            enableRandomWalkCheckBox = new CheckBox(TEXTS.BOT_SETTINGS_ENABLE_RANDOM_WALK.getString());
            enableRandomWalkCheckBox.setTextAlignment(Alignment.RIGHT);
            volatilityLabel = new Label(TEXTS.BOT_SETTINGS_VOLATILITY.getString());
            volatilityLabel.setAlignment(Alignment.RIGHT);
            volatilityTextBox = new TextBox();
            volatilityTextBox.setAllowNumbers(true,true);
            volatilityTextBox.setAllowLetters(false);


            enableCheckBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_ENABLE_TOOLTIP::getString);
            defaultPriceLabel.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_DEFAULT_PRICE_TOOLTIP::getString);
            defaultPriceTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_DEFAULT_PRICE_TOOLTIP::getString);
            updateTimerInvervalMSLabel.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_UPDATE_INTERVAL_MS_TOOLTIP::getString);
            updateTimerInvervalMSTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_UPDATE_INTERVAL_MS_TOOLTIP::getString);
            volumeScaleLabel.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_VOLUME_SCALE_TOOLTIP::getString);
            volumeScaleTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_VOLUME_SCALE_TOOLTIP::getString);
            enableTargetPriceCheckBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_ENABLE_TARGET_PRICE_TOOLTIP::getString);
            targetPriceSteeringFactorLabel.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_TARGET_PRICE_STEERING_FAC_TOOLTIP::getString);
            targetPriceSteeringFactorTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_TARGET_PRICE_STEERING_FAC_TOOLTIP::getString);
            enableVolumeTrackingCheckBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_ENABLE_VOLUME_TRACKING_TOOLTIP::getString);
            volumeSteeringFactorLabel.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_VOLUME_STEERING_FAC_TOOLTIP::getString);
            volumeSteeringFactorTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_VOLUME_STEERING_FAC_TOOLTIP::getString);
            enableRandomWalkCheckBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_ENABLE_RANDOM_WALK_TOOLTIP::getString);
            volatilityLabel.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_VOLATILITY_TOOLTIP::getString);
            volatilityTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_VOLATILITY_TOOLTIP::getString);


            enableTargetPriceCheckBox.setOnStateChanged((enabled)-> {
                targetPriceSteeringFactorLabel.setEnabled(enabled);
                targetPriceSteeringFactorTextBox.setEnabled(enabled);
            });
            enableVolumeTrackingCheckBox.setOnStateChanged((enabled)->{
                volumeSteeringFactorLabel.setEnabled(enabled);
                volumeSteeringFactorTextBox.setEnabled(enabled);
            });
            enableRandomWalkCheckBox.setOnStateChanged((enabled)->{
                volatilityLabel.setEnabled(enabled);
                volatilityTextBox.setEnabled(enabled);
            });

            enableCheckBox.setOnStateChanged((checked)->
            {
                defaultPriceLabel.setEnabled(checked);
                defaultPriceTextBox.setEnabled(checked);
                updateTimerInvervalMSLabel.setEnabled(checked);
                updateTimerInvervalMSTextBox.setEnabled(checked);
                volumeScaleLabel.setEnabled(checked);
                volumeScaleTextBox.setEnabled(checked);
                enableTargetPriceCheckBox.setEnabled(checked);
                targetPriceSteeringFactorLabel.setEnabled(checked && enableTargetPriceCheckBox.isChecked());
                targetPriceSteeringFactorTextBox.setEnabled(checked && enableTargetPriceCheckBox.isChecked());
                enableVolumeTrackingCheckBox.setEnabled(checked);
                volumeSteeringFactorLabel.setEnabled(checked && enableVolumeTrackingCheckBox.isChecked());
                volumeSteeringFactorTextBox.setEnabled(checked && enableVolumeTrackingCheckBox.isChecked());
                enableRandomWalkCheckBox.setEnabled(checked);
                volatilityLabel.setEnabled(checked && enableRandomWalkCheckBox.isChecked());
                volatilityTextBox.setEnabled(checked && enableRandomWalkCheckBox.isChecked());
            });



            addChild(titleLabel);
            addChild(enableCheckBox);
            addChild(defaultPriceLabel);
            addChild(defaultPriceTextBox);
            addChild(updateTimerInvervalMSLabel);
            addChild(updateTimerInvervalMSTextBox);
            addChild(volumeScaleLabel);
            addChild(volumeScaleTextBox);
            addChild(enableTargetPriceCheckBox);
            addChild(targetPriceSteeringFactorLabel);
            addChild(targetPriceSteeringFactorTextBox);
            addChild(enableVolumeTrackingCheckBox);
            addChild(volumeSteeringFactorLabel);
            addChild(volumeSteeringFactorTextBox);
            addChild(enableRandomWalkCheckBox);
            addChild(volatilityLabel);
            addChild(volatilityTextBox);

            for(GuiElement child : getChilds())
            {
                child.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
                child.setHoverTooltipFontScale(textFontSize);
                child.setTextFontScale(textFontSize);
            }

            int targetHeight = (elementHeight+spacing) * 11 + 5;
            this.setHeight(targetHeight);
        }

        public void setBotSettings(ServerVolatilityBot.Settings settings) {
            this.botSettings = settings;

            if(botSettings == null)
            {
                enableCheckBox.setChecked(false);
            }
            else
            {
                enableCheckBox.setChecked(botSettings.enabled);
                defaultPriceTextBox.setMaxDecimalChar(Bank.getMaxDecimalDigitsCount(priceScaleFactor));
                defaultPriceTextBox.setText(String.valueOf(botSettings.defaultPrice));
                updateTimerInvervalMSTextBox.setText(String.valueOf(botSettings.updateTimerIntervallMS));
                volumeScaleTextBox.setText(String.valueOf(botSettings.volumeScale));
                enableTargetPriceCheckBox.setChecked(botSettings.enableTargetPrice);
                targetPriceSteeringFactorTextBox.setText(String.valueOf(botSettings.targetPriceSteeringFactor));
                enableVolumeTrackingCheckBox.setChecked(botSettings.enableVolumeTracking);
                volumeSteeringFactorTextBox.setText(String.valueOf(botSettings.volumeSteeringFactor));
                enableRandomWalkCheckBox.setChecked(botSettings.enableRandomWalk);
                volatilityTextBox.setText(String.valueOf(botSettings.volatility));
            }
        }
        public ServerVolatilityBot.Settings getBotSettings() {
            if(enableCheckBox.isChecked())
            {
                botSettings = new ServerVolatilityBot.Settings();
            }
            else
                botSettings = null;
            if(botSettings != null) {
                botSettings.enabled = enableCheckBox.isChecked();
                botSettings.defaultPrice = getInRange((float)defaultPriceTextBox.getDouble(), 0.f, (float)Integer.MAX_VALUE);
                defaultPriceTextBox.setText(String.valueOf(botSettings.defaultPrice));
                botSettings.updateTimerIntervallMS = getInRange(updateTimerInvervalMSTextBox.getLong(), 10, Long.MAX_VALUE);
                updateTimerInvervalMSTextBox.setText(String.valueOf(botSettings.updateTimerIntervallMS));
                botSettings.volumeScale = getInRange((float)volumeScaleTextBox.getDouble(), 0.f, 100000.f);
                volumeScaleTextBox.setText(String.valueOf(botSettings.volumeScale));
                botSettings.enableTargetPrice = enableTargetPriceCheckBox.isChecked();
                botSettings.targetPriceSteeringFactor = getInRange((float)targetPriceSteeringFactorTextBox.getDouble(), 0.f, 100000.f);
                targetPriceSteeringFactorTextBox.setText(String.valueOf(botSettings.targetPriceSteeringFactor));
                botSettings.enableVolumeTracking = enableVolumeTrackingCheckBox.isChecked();
                botSettings.volumeSteeringFactor = getInRange((float)volumeSteeringFactorTextBox.getDouble(), 0.f, 100000.f);
                volumeSteeringFactorTextBox.setText(String.valueOf(botSettings.volumeSteeringFactor));
                botSettings.enableRandomWalk = enableRandomWalkCheckBox.isChecked();
                botSettings.volatility = getInRange((float)volatilityTextBox.getDouble(), 0.f, 100000.f);
                volatilityTextBox.setText(String.valueOf(botSettings.volatility));
            }
            return botSettings;
        }



        @Override
        protected void render() {


        }
        @Override
        protected void layoutChanged() {
            int width = getWidth() - padding * 2;
            //int height = getHeight() - padding * 2;
            int elementHeight = 15;

            int y = padding;
            titleLabel.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            enableCheckBox.setBounds(width/2+padding, y, width-width/2, elementHeight);
            y += elementHeight + spacing;
            defaultPriceLabel.setBounds(padding, y, width/2, elementHeight);
            defaultPriceTextBox.setBounds(defaultPriceLabel.getRight(), defaultPriceLabel.getTop(), width - defaultPriceLabel.getWidth(), defaultPriceLabel.getHeight());
            y += elementHeight + spacing;
            updateTimerInvervalMSLabel.setBounds(padding, y, width/2, elementHeight);
            updateTimerInvervalMSTextBox.setBounds(updateTimerInvervalMSLabel.getRight(), updateTimerInvervalMSLabel.getTop(), width - updateTimerInvervalMSLabel.getWidth(), updateTimerInvervalMSLabel.getHeight());
            y += elementHeight + spacing;
            volumeScaleLabel.setBounds(padding, y, width/2, elementHeight);
            volumeScaleTextBox.setBounds(volumeScaleLabel.getRight(), volumeScaleLabel.getTop(), width - volumeScaleLabel.getWidth(), volumeScaleLabel.getHeight());
            y += elementHeight + spacing;
            enableTargetPriceCheckBox.setBounds(width/2+padding, y, width-width/2, elementHeight);
            y += elementHeight + spacing;
            targetPriceSteeringFactorLabel.setBounds(padding, y, width/2, elementHeight);
            targetPriceSteeringFactorTextBox.setBounds(targetPriceSteeringFactorLabel.getRight(), targetPriceSteeringFactorLabel.getTop(), width - targetPriceSteeringFactorLabel.getWidth(), targetPriceSteeringFactorLabel.getHeight());
            y += elementHeight + spacing;
            enableVolumeTrackingCheckBox.setBounds(width/2+padding, y, width-width/2, elementHeight);
            y += elementHeight + spacing;
            volumeSteeringFactorLabel.setBounds(padding, y, width/2, elementHeight);
            volumeSteeringFactorTextBox.setBounds(volumeSteeringFactorLabel.getRight(), volumeSteeringFactorLabel.getTop(), width - volumeSteeringFactorLabel.getWidth(), volumeSteeringFactorLabel.getHeight());
            y += elementHeight + spacing;
            enableRandomWalkCheckBox.setBounds(width/2+padding, y, width-width/2, elementHeight);
            y += elementHeight + spacing;
            volatilityLabel.setBounds(padding, y, width/2, elementHeight);
            volatilityTextBox.setBounds(volatilityLabel.getRight(), volatilityLabel.getTop(), width - volatilityLabel.getWidth(), volatilityLabel.getHeight());
        }
    }
*/


    GuiScreen parentScreen;
    private static MarketSettingsScreen instance = null;
    //private ServerMarketSettingsData serverMarketSettingsData;
    private long oldItemImbalance = 0;
    private final TradingChartWidget tradingChart;
    private final TradingPairView tradingPairView;
    private final Button saveButton;
    private final Button backButton;
    private final Button openPluginBrowserButton;
    private final ListView pluginsListView;
    GeneralGuiElement generalGuiElement;
    PluginBrowserScreen  pluginBrowserScreen = null;
    //private final VirtualOderBookGuiElement virtualOrderBookGuiElement;
    //private final BotGuiElement botGuiElement;
    private final List<ClientMarketPlugin> plugins = new ArrayList<>();
    Consumer<ServerMarketSettingsData> onSaveCallback;

    private final TimerMillis updateTimer;
    public int priceScaleFactor = 1;
    public MarketSettingsScreen(GuiScreen parent, Consumer<ServerMarketSettingsData> onSave) {
        super(TEXTS.TITLE);
        setGuiScale(0.5f);
        this.parentScreen = parent;
        this.onSaveCallback = onSave;

        tradingChart = new TradingChartWidget();
        //tradingChart.enableBotTargetPriceDisplay(true);

        tradingPairView = new TradingPairView();
        tradingPairView.setClickable(false);

        saveButton = new Button(TEXTS.SAVE_BUTTON.getString(), this::onSaveButtonClicked);
        backButton = new Button(TEXTS.BACK_BUTTON.getString(), this::onClose);
        openPluginBrowserButton = new Button(TEXTS.OPEN_PLUGIN_BROWSER_BUTTON.getString(), this::onOpenPluginBrowserButtonClicked);


        pluginsListView = new VerticalListView();
        Layout layout = new LayoutVertical();
        layout.stretchX = true;
        pluginsListView.setLayout(layout);

        generalGuiElement = new GeneralGuiElement();
        //botGuiElement = new BotGuiElement();
        //virtualOrderBookGuiElement = new VirtualOderBookGuiElement();


        //pluginsListView.addChild(generalGuiElement);
        //listView.addChild(virtualOrderBookGuiElement);
        //listView.addChild(botGuiElement);


        addElement(generalGuiElement);
        addElement(tradingPairView);
        addElement(saveButton);
        addElement(backButton);
        addElement(openPluginBrowserButton);
        addElement(tradingChart);
        addElement(pluginsListView);

        instance = this;
        this.updateTimer = new TimerMillis(true); // Update every second
        updateTimer.start(100);
        TickEvent.PLAYER_POST.register(MarketSettingsScreen::onClientTick);
    }

    @Override
    public void onClose() {
        TickEvent.PLAYER_POST.unregister(MarketSettingsScreen::onClientTick);
        super.onClose();
        instance = null;
        for(ClientMarketPlugin plugin : plugins)
            plugin.close_internal();
        plugins.clear();
        if (parentScreen != null) {
            int mousePosX = getMouseX();
            int mousePosY = getMouseY();
            minecraft.setScreen(parentScreen);
            setMousePos(mousePosX, mousePosY);
        }
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = 5;
        int spacing = 5;
        int width = getWidth() - padding * 2;
        int height = getHeight() - padding * 2;

        int chartWidth = (width*2)/3;

        tradingChart.setBounds(padding, padding, chartWidth, height);
        tradingPairView.setBounds(tradingChart.getRight()+spacing, padding, (width-chartWidth)/3-spacing, 20);
        saveButton.setBounds(tradingPairView.getRight()+spacing, tradingPairView.getTop(), (width-chartWidth) / 3 - spacing, 20);
        backButton.setBounds(saveButton.getRight() + spacing, saveButton.getTop(), width - saveButton.getRight(), 20);


        generalGuiElement.setBounds(tradingPairView.getLeft(), tradingPairView.getBottom() + spacing, (width-chartWidth) - spacing, generalGuiElement.getHeight());

        openPluginBrowserButton.setBounds(generalGuiElement.getLeft(), generalGuiElement.getBottom() + spacing, generalGuiElement.getWidth(), 20);
        pluginsListView.setBounds(openPluginBrowserButton.getLeft(), openPluginBrowserButton.getBottom(), openPluginBrowserButton.getWidth(), getHeight() - openPluginBrowserButton.getBottom() - padding);
    }

    private void onSaveButtonClicked()
    {
        ServerMarketSettingsData settings = getSettings();
        if(settings == null)
            return;

        for(ClientMarketPlugin plugin : plugins)
        {
            plugin.saveSettings();
        }
        onSaveCallback.accept(settings);
    }
    private void onOpenPluginBrowserButtonClicked()
    {
        TradingPair pair = tradingPairView.getTradingPair();
        if(pair == null)
            return;
        pluginBrowserScreen = new PluginBrowserScreen(pair, this::onPluginBrowserChangesApplyed, this);
        setScreen(pluginBrowserScreen);
    }
    private void onPluginBrowserChangesApplyed()
    {
        getSelectedMarket().requestGetMarketSettings(
                (settingsData -> {
                    if (settingsData != null) {
                        setSettings(settingsData);
                    }
                }));
        if(pluginBrowserScreen != null)
        {
            pluginBrowserScreen.close();
            pluginBrowserScreen = null;
        }
    }

    public void setSettings(ServerMarketSettingsData settings)
    {
        //this.serverMarketSettingsData = settings;
        if(settings != null)
        {
            priceScaleFactor = settings.priceScaleFactor;
            if(settings.tradingPairData != null) {
                TradingPair tradingPair = settings.tradingPairData.toTradingPair();
                selectMarket(tradingPair);
                tradingPairView.setTradingPair(tradingPair);
                generalGuiElement.selectMarket(tradingPair);

                updatePluginsListView();
                //virtualOrderBookGuiElement.selectMarket(tradingPair);
                //botGuiElement.selectMarket(tradingPair);
            }
            generalGuiElement.setShiftPriceCandleIntervalMS(settings.shiftPriceCandleIntervalMS);
            generalGuiElement.setMarketOpen(settings.marketOpen);

           /* if(settings.botSettingsData != null)
                botGuiElement.setBotSettings(settings.botSettingsData.settings);
            else
                botGuiElement.setBotSettings(null);

            if(settings.virtualOrderBookSettingsData != null)
                virtualOrderBookGuiElement.setVirtualOrderBookSettings(settings.virtualOrderBookSettingsData.settings);
            else
                virtualOrderBookGuiElement.setVirtualOrderBookSettings(null);*/
            oldItemImbalance = settings.itemImbalance;
            generalGuiElement.setItemImbalance(settings.itemImbalance);




        }
        else {
            tradingPairView.setTradingPair(null);
            generalGuiElement.setShiftPriceCandleIntervalMS(0);
            generalGuiElement.setMarketOpen(false);
           // botGuiElement.setBotSettings(null);
           // virtualOrderBookGuiElement.setVirtualOrderBookSettings(null);
            oldItemImbalance = 0;
        }





    }
    public ServerMarketSettingsData getSettings()
    {
        if(getSelectedMarket() == null)
            return null;
        ServerMarketSettingsData settings = new ServerMarketSettingsData(
                getSelectedMarket().getTradingPair(),
                null,//botGuiElement.getBotSettings(),
                null,//virtualOrderBookGuiElement.getVirtualOrderBookSettings(),
                generalGuiElement.isMarketOpen(),
                0,
                generalGuiElement.getShiftPriceCandleIntervalMS(),
                priceScaleFactor
        );

        long newItemImbalance = generalGuiElement.getItemImbalance();
        if(oldItemImbalance != newItemImbalance)
        {
            settings.itemImbalance = newItemImbalance;
            settings.overwriteItemImbalance = true;
        }


        settings.doCreateBotIfNotExists = settings.botSettingsData != null;
        settings.doDestroyBotIfExists = settings.botSettingsData == null;
        settings.doCreateVirtualOrderBookIfNotExists = settings.virtualOrderBookSettingsData != null;
        settings.doDestroyVirtualOrderBookIfExists = settings.virtualOrderBookSettingsData == null;

        return settings;
    }

    private static void onClientTick(Player player)
    {
        if(instance == null)
            return;

        if(instance.updateTimer.check() && instance.getSelectedMarket() != null)
        {
            instance.getSelectedMarket().requestTradingViewData(0, instance.tradingChart.getMaxCandleCount(), 0,0,500,true ,instance.tradingChart::updateView);
        }
    }

    private void updatePluginsListView()
    {
        for(ClientMarketPlugin plugin : plugins)
            plugin.close_internal();
        plugins.clear();
        pluginsListView.removeChilds();
        TradingPair tradingPair = getSelectedMarket().getTradingPair();

        BACKEND_INSTANCES.CLIENT_PLUGIN_MANAGER.requestMarketPluginTypes(tradingPair, (pluginTypeList)->
        {
            List<ClientMarketPlugin> plugins = BACKEND_INSTANCES.CLIENT_PLUGIN_MANAGER.getMarketPlugins(tradingPair);
            for(ClientMarketPlugin plugin : plugins)
            {
                ClientMarketPluginGuiElement element = plugin.getSettingsGuiElement_internal();
                if(element != null)
                {
                    this.plugins.add(plugin);
                    pluginsListView.addChild(element);
                    element.setMoveUpDownCallbacks(this::movePluginUp, this::movePluginDown);
                    element.setChartWidget(tradingChart);
                    plugin.setup_interal();
                }
            }
        });
    }

    private void movePluginUp(ClientMarketPluginGuiElement guiElement)
    {
        TradingPair tradingPair = getSelectedMarket().getTradingPair();

        List<GuiElement> elements = pluginsListView.getChilds();
        int currentIndex = elements.indexOf(guiElement);
        if(currentIndex == -1)
            return;

        List<String> sortedPluginIDs = new ArrayList<>();
        for(int i = 0; i<elements.size(); i++)
        {
            if(i == currentIndex-1 && currentIndex > 0)
            {
                sortedPluginIDs.add(guiElement.getPlugin().getPluginTypeID());
                sortedPluginIDs.add(((ClientMarketPluginGuiElement)elements.get(i)).getPlugin().getPluginTypeID());
                i++;
            }
            else
                sortedPluginIDs.add(((ClientMarketPluginGuiElement)elements.get(i)).getPlugin().getPluginTypeID());
        }

        BACKEND_INSTANCES.CLIENT_PLUGIN_MANAGER.requestSetMarketPluginTypes(tradingPair, sortedPluginIDs, (success)->
        {
            if(!success)
                error("Can't update order of market plugins");
            else
                updatePluginsListView();
        });

        //updatePluginsListView();



        /*
        List<GuiElement> elements = pluginsListView.getChilds();
        List<GuiElement> newElements = new ArrayList<>();
        int currentIndex = elements.indexOf(guiElement);

        if(currentIndex == -1)
            return;
        elements.remove(currentIndex);
        for(int i = 0; i<elements.size(); i++)
        {
            if(i == currentIndex-1)
            {
                newElements.add(guiElement);
            }
            newElements.add(elements.get(i));
        }
        pluginsListView.removeChilds();
        for(GuiElement element : newElements)
        {
            pluginsListView.addChild(element);
        }
        */
    }
    private void movePluginDown(ClientMarketPluginGuiElement guiElement)
    {
        TradingPair tradingPair = getSelectedMarket().getTradingPair();

        List<GuiElement> elements = pluginsListView.getChilds();
        int currentIndex = elements.indexOf(guiElement);
        if(currentIndex == -1)
            return;

        List<String> sortedPluginIDs = new ArrayList<>();
        for(int i = 0; i<elements.size(); i++)
        {
            if(i == currentIndex && currentIndex < elements.size() - 1)
            {
                sortedPluginIDs.add(((ClientMarketPluginGuiElement)elements.get(i+1)).getPlugin().getPluginTypeID());
                sortedPluginIDs.add(guiElement.getPlugin().getPluginTypeID());
                i++;
            }
            else
                sortedPluginIDs.add(((ClientMarketPluginGuiElement)elements.get(i)).getPlugin().getPluginTypeID());
        }

        BACKEND_INSTANCES.CLIENT_PLUGIN_MANAGER.requestSetMarketPluginTypes(tradingPair, sortedPluginIDs, (success)->
        {
            if(!success)
                error("Can't update order of market plugins");
            else
                updatePluginsListView();
        });
    }

    public static float getInRange(float value, float min, float max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        }
        return value;
    }
    public static int getInRange(int value, int min, int max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        }
        return value;
    }
    public static long getInRange(long value, long min, long max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        }
        return value;
    }
}
