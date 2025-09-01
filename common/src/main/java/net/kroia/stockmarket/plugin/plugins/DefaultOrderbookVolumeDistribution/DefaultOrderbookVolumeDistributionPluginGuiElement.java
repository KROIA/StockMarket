package net.kroia.stockmarket.plugin.plugins.DefaultOrderbookVolumeDistribution;

import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.ClientMarketPluginGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;

public class DefaultOrderbookVolumeDistributionPluginGuiElement extends ClientMarketPluginGuiElement {

    public static final class TEXTS
    {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".market_settings_screen.";

        public static final Component TITLE = Component.translatable(PREFIX + "title");
        public static final Component SAVE_BUTTON = Component.translatable(PREFIX + "save_button");
        public static final Component BACK_BUTTON = Component.translatable(PREFIX + "back_button");


        // GeneralGui
        public static final Component GENERAL_TITLE = Component.translatable(PREFIX + "general.title");
        public static final Component GENERAL_CHART_RESET = Component.translatable(PREFIX + "general.chart_reset");
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

    public class VirtualOderBookGuiElement extends StockMarketGuiElement
    {
        //DefaultOrderbookVolumeDistributionPlugin.Settings virtualOrderBookSettings;

       //private final Label titleLabel;
        //private final CheckBox enableCheckBox;
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

            //titleLabel = new Label(TEXTS.VIRTUAL_ORDER_BOOK_TITLE.getString());
            //titleLabel.setAlignment(Alignment.CENTER);
            //enableCheckBox = new CheckBox(TEXTS.VIRTUAL_ORDER_BOOK_ENABLE.getString());
            //enableCheckBox.setTextAlignment(Alignment.RIGHT);
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

            //enableCheckBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_ENABLE_TOOLTIP::getString);
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

            /*enableCheckBox.setOnStateChanged((checked)->
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
            });*/

            //addChild(titleLabel);
            //addChild(enableCheckBox);
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
                //child.setHoverTooltipFontScale(textFontSize);
                //child.setTextFontScale(textFontSize);
            }

            int targetHeight = (20+spacing) * 5 + 5;
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
            int labelWidthPercent = 70;

            int y = padding;
            //titleLabel.setBounds(padding, y, width, elementHeight);
            //y += elementHeight + spacing;
            int labelWidth = (width*labelWidthPercent)/100;
            //enableCheckBox.setBounds(width/2+padding, y, width-width/2, elementHeight);
            //y += elementHeight + spacing;
            volumeScaleLabel.setBounds(padding, y, labelWidth, elementHeight);
            volumeScaleTextBox.setBounds(volumeScaleLabel.getRight(), volumeScaleLabel.getTop(), width-volumeScaleLabel.getWidth(), volumeScaleLabel.getHeight());
            y += elementHeight + spacing;
            nearMarketVolumeScaleLabel.setBounds(padding, y, labelWidth, elementHeight);
            nearMarketVolumeScaleTextBox.setBounds(nearMarketVolumeScaleLabel.getRight(), nearMarketVolumeScaleLabel.getTop(), width-nearMarketVolumeScaleLabel.getWidth(), nearMarketVolumeScaleLabel.getHeight());
            y += elementHeight + spacing;
            volumeAccumulationRateLabel.setBounds(padding, y, labelWidth, elementHeight);
            volumeAccumulationRateTextBox.setBounds(volumeAccumulationRateLabel.getRight(), volumeAccumulationRateLabel.getTop(), width-volumeAccumulationRateLabel.getWidth(), volumeAccumulationRateLabel.getHeight());
            y += elementHeight + spacing;
            volumeFastAccumulationRateLabel.setBounds(padding, y, labelWidth, elementHeight);
            volumeFastAccumulationRateTextBox.setBounds(volumeFastAccumulationRateLabel.getRight(), volumeFastAccumulationRateLabel.getTop(), width-volumeFastAccumulationRateLabel.getWidth(), volumeFastAccumulationRateLabel.getHeight());
            y += elementHeight + spacing;
            volumeDecumulationRateLabel.setBounds(padding, y, labelWidth, elementHeight);
            volumeDecumulationRateTextBox.setBounds(volumeDecumulationRateLabel.getRight(), volumeDecumulationRateLabel.getTop(), width-volumeDecumulationRateLabel.getWidth(), volumeDecumulationRateLabel.getHeight());
        }

        public void setVirtualOrderBookSettings(DefaultOrderbookVolumeDistributionPlugin.Settings settings)
        {
            volumeScaleTextBox.setText(String.valueOf(settings.volumeScale));
            nearMarketVolumeScaleTextBox.setText(String.valueOf(settings.nearMarketVolumeScale));
            volumeAccumulationRateTextBox.setText(String.valueOf(settings.volumeAccumulationRate));
            volumeFastAccumulationRateTextBox.setText(String.valueOf(settings.volumeFastAccumulationRate));
            volumeDecumulationRateTextBox.setText(String.valueOf(settings.volumeDecumulationRate));
        }
        public DefaultOrderbookVolumeDistributionPlugin.Settings getVirtualOrderBookSettings() {
            DefaultOrderbookVolumeDistributionPlugin.Settings settings = new DefaultOrderbookVolumeDistributionPlugin.Settings();
            settings.volumeScale = getInRange((float)volumeScaleTextBox.getDouble(), 0.f, 100000.f);
            volumeScaleTextBox.setText(String.valueOf(settings.volumeScale));
            settings.nearMarketVolumeScale = getInRange((float)nearMarketVolumeScaleTextBox.getDouble(), 0.f, 100000.f);
            nearMarketVolumeScaleTextBox.setText(String.valueOf(settings.nearMarketVolumeScale));
            settings.volumeAccumulationRate = getInRange((float)volumeAccumulationRateTextBox.getDouble(), 0.f, 100000.f);
            volumeAccumulationRateTextBox.setText(String.valueOf(settings.volumeAccumulationRate));
            settings.volumeFastAccumulationRate = getInRange((float)volumeFastAccumulationRateTextBox.getDouble(), 0.f, 100000.f);
            volumeFastAccumulationRateTextBox.setText(String.valueOf(settings.volumeFastAccumulationRate));
            settings.volumeDecumulationRate = getInRange((float)volumeDecumulationRateTextBox.getDouble(), 0.f, 100000.f);
            volumeDecumulationRateTextBox.setText(String.valueOf(settings.volumeDecumulationRate));
            return settings;
        }
    }

    private VirtualOderBookGuiElement customPluginWidget;

    public DefaultOrderbookVolumeDistributionPluginGuiElement(ClientMarketPlugin plugin) {
        super(plugin);
    }

    @Override
    protected GuiElement getCustomPluginWidget() {
        if(customPluginWidget == null)
        {
            customPluginWidget = new VirtualOderBookGuiElement();
        }
        return customPluginWidget;
    }

    public void setSettings(DefaultOrderbookVolumeDistributionPlugin.Settings settings)
    {
        customPluginWidget.setVirtualOrderBookSettings(settings);
    }

    public DefaultOrderbookVolumeDistributionPlugin.Settings getSettings()
    {
        return customPluginWidget.getVirtualOrderBookSettings();
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
