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
import net.kroia.stockmarket.market.server.VirtualOrderBook;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.screen.uiElements.chart.TradingChartWidget;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

public class MarketSettingsScreen extends StockMarketGuiScreen {
    public static final class TEXTS
    {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".market_settings_screen.";

        public static final Component TITLE = Component.translatable(PREFIX + "title");
        public static final Component SAVE_BUTTON = Component.translatable(PREFIX + "save_button");
        public static final Component CANCEL_BUTTON = Component.translatable(PREFIX + "cancel_button");


        // GeneralGui
        public static final Component GENERAL_TITLE = Component.translatable(PREFIX + "general.title");
        public static final Component GENERAL_CHART_RESET = Component.translatable(PREFIX + "general.chart_reset");
        public static final Component GENERAL_IS_MARKET_OPEN = Component.translatable(PREFIX + "general.is_market_open");
        // GeneralGui Tooltips
        public static final Component GENERAL_IS_MARKET_OPEN_TOOLTIP = Component.translatable(PREFIX + "general.is_market_open.tooltip");



        // Virtual Order Book
        public static final Component VIRTUAL_ORDER_BOOK_TITLE = Component.translatable(PREFIX + "virtual_order_book.title");
        public static final Component VIRTUAL_ORDER_BOOK_ENABLE = Component.translatable(PREFIX + "virtual_order_book.enable");
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


    private static class GeneralGuiElement extends StockMarketGuiElement
    {
        public final Label titleLabel;
        public final Button chartResetButton;
        public final CheckBox isMarketOpenCheckBox;
        public final TextBox candleTimeMin;

        public GeneralGuiElement() {
            super();
            titleLabel = new Label(TEXTS.GENERAL_TITLE.getString());
            titleLabel.setAlignment(Alignment.CENTER);
            chartResetButton = new Button(TEXTS.GENERAL_CHART_RESET.getString(), () -> {
                if(getSelectedMarket() != null)
                {
                    getSelectedMarket().requestChartReset((result)->{});
                }
            });
            isMarketOpenCheckBox = new CheckBox(TEXTS.GENERAL_IS_MARKET_OPEN.getString());
            candleTimeMin = new TextBox();
            candleTimeMin.setAllowNumbers(true,false);
            candleTimeMin.setAllowLetters(false);

            isMarketOpenCheckBox.setHoverTooltipSupplier(TEXTS.GENERAL_IS_MARKET_OPEN_TOOLTIP::getString);
            candleTimeMin.setHoverTooltipSupplier(()-> StockMarketTextMessages.getMarketSettingsScreenCandleTimeTooltip(candleTimeMin.getInt()));


            addChild(titleLabel);
            addChild(chartResetButton);
            addChild(isMarketOpenCheckBox);
            addChild(candleTimeMin);

            for(GuiElement child : getChilds())
            {
                child.setTooltipMousePositionAlignment(Alignment.RIGHT);
            }

            int targetHeight = 25 * getChilds().size() + 5;
            this.setHeight(targetHeight);
        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int padding = 5;
            int spacing = 5;
            int width = getWidth() - padding * 2;
            //int height = getHeight() - padding * 2;
            int elementHeight = 20;

            int y = padding;
            titleLabel.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            chartResetButton.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            isMarketOpenCheckBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            candleTimeMin.setBounds(padding, y, width, elementHeight);
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
    }

    public static class VirtualOderBookGuiElement extends StockMarketGuiElement
    {
        VirtualOrderBook.Settings virtualOrderBookSettings;

        private final Label titleLabel;
        private final CheckBox enableCheckBox;
        private final TextBox volumeScaleTextBox;
        private final TextBox nearMarketVolumeScaleTextBox;
        private final TextBox volumeAccumulationRateTextBox;
        private final TextBox volumeFastAccumulationRateTextBox;
        private final TextBox volumeDecumulationRateTextBox;
        public VirtualOderBookGuiElement() {
            super();

            titleLabel = new Label(TEXTS.VIRTUAL_ORDER_BOOK_TITLE.getString());
            titleLabel.setAlignment(Alignment.CENTER);
            enableCheckBox = new CheckBox(TEXTS.VIRTUAL_ORDER_BOOK_ENABLE.getString());
            volumeScaleTextBox = new TextBox();
            volumeScaleTextBox.setAllowNumbers(true,true);
            volumeScaleTextBox.setAllowLetters(false);
            nearMarketVolumeScaleTextBox = new TextBox();
            nearMarketVolumeScaleTextBox.setAllowNumbers(true,true);
            nearMarketVolumeScaleTextBox.setAllowLetters(false);
            volumeAccumulationRateTextBox = new TextBox();
            volumeAccumulationRateTextBox.setAllowNumbers(true,true);
            volumeAccumulationRateTextBox.setAllowLetters(false);
            volumeFastAccumulationRateTextBox = new TextBox();
            volumeFastAccumulationRateTextBox.setAllowNumbers(true,true);
            volumeFastAccumulationRateTextBox.setAllowLetters(false);
            volumeDecumulationRateTextBox = new TextBox();
            volumeDecumulationRateTextBox.setAllowNumbers(true,true);
            volumeDecumulationRateTextBox.setAllowLetters(false);

            enableCheckBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_ENABLE_TOOLTIP::getString);
            volumeScaleTextBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_SCALE_TOOLTIP::getString);
            nearMarketVolumeScaleTextBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_NEAR_MARKET_VOLUME_SCALE_TOOLTIP::getString);
            volumeAccumulationRateTextBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_ACCUMULATION_RATE_TOOLTIP::getString);
            volumeFastAccumulationRateTextBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_FAST_ACCUMULATION_RATE_TOOLTIP::getString);
            volumeDecumulationRateTextBox.setHoverTooltipSupplier(TEXTS.VIRTUAL_ORDER_BOOK_VOLUME_DECUMULATION_RATE_TOOLTIP::getString);

            enableCheckBox.setOnStateChanged((checked)->
            {
                volumeScaleTextBox.setEnabled(checked);
                nearMarketVolumeScaleTextBox.setEnabled(checked);
                volumeAccumulationRateTextBox.setEnabled(checked);
                volumeFastAccumulationRateTextBox.setEnabled(checked);
                volumeDecumulationRateTextBox.setEnabled(checked);
            });

            addChild(titleLabel);
            addChild(enableCheckBox);
            addChild(volumeScaleTextBox);
            addChild(nearMarketVolumeScaleTextBox);
            addChild(volumeAccumulationRateTextBox);
            addChild(volumeFastAccumulationRateTextBox);
            addChild(volumeDecumulationRateTextBox);

            for(GuiElement child : getChilds())
            {
                child.setTooltipMousePositionAlignment(Alignment.RIGHT);
            }

            int targetHeight = 25 * getChilds().size() + 5;
            this.setHeight(targetHeight);
        }

        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int padding = 5;
            int spacing = 5;
            int width = getWidth() - padding * 2;
            //int height = getHeight() - padding * 2;
            int elementHeight = 20;

            int y = padding;
            titleLabel.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            enableCheckBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            volumeScaleTextBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            nearMarketVolumeScaleTextBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            volumeAccumulationRateTextBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            volumeFastAccumulationRateTextBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            volumeDecumulationRateTextBox.setBounds(padding, y, width, elementHeight);

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

    private static class BotGuiElement extends StockMarketGuiElement
    {
        private ServerVolatilityBot.Settings botSettings;

        private final Label titleLabel;
        private final CheckBox enableCheckBox;
        private final TextBox defaultPriceTextBox;
        private final TextBox updateTimerInvervalMSTextBox;
        private final TextBox volumeScaleTextBox;
        private final CheckBox enableTargetPriceCheckBox;
        private final TextBox targetPriceSteeringFactorTextBox;
        private final CheckBox enableVolumeTrackingCheckBox;
        private final TextBox volumeSteeringFactorTextBox;
        private final CheckBox enableRandomWalkCheckBox;
        private final TextBox volatilityTextBox;
        public BotGuiElement() {
            super();

            titleLabel = new Label(TEXTS.BOT_SETTINGS_TITLE.getString());
            titleLabel.setAlignment(Alignment.CENTER);
            enableCheckBox = new CheckBox(TEXTS.BOT_SETTINGS_ENABLE.getString());
            defaultPriceTextBox = new TextBox();
            defaultPriceTextBox.setAllowNumbers(true,false);
            defaultPriceTextBox.setAllowLetters(false);
            updateTimerInvervalMSTextBox = new TextBox();
            updateTimerInvervalMSTextBox.setAllowNumbers(true,false);
            updateTimerInvervalMSTextBox.setAllowLetters(false);
            volumeScaleTextBox = new TextBox();
            volumeScaleTextBox.setAllowNumbers(true,false);
            volumeScaleTextBox.setAllowLetters(false);
            enableTargetPriceCheckBox = new CheckBox(TEXTS.BOT_SETTINGS_ENABLE_TARGET_PRICE.getString());
            targetPriceSteeringFactorTextBox = new TextBox();
            targetPriceSteeringFactorTextBox.setAllowNumbers(true,true);
            targetPriceSteeringFactorTextBox.setAllowLetters(false);
            enableVolumeTrackingCheckBox = new CheckBox(TEXTS.BOT_SETTINGS_ENABLE_VOLUME_TRACKING.getString());
            volumeSteeringFactorTextBox = new TextBox();
            volumeSteeringFactorTextBox.setAllowNumbers(true,true);
            volumeSteeringFactorTextBox.setAllowLetters(false);
            enableRandomWalkCheckBox = new CheckBox(TEXTS.BOT_SETTINGS_ENABLE_RANDOM_WALK.getString());
            volatilityTextBox = new TextBox();
            volatilityTextBox.setAllowNumbers(true,true);
            volatilityTextBox.setAllowLetters(false);


            enableCheckBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_ENABLE_TOOLTIP::getString);
            defaultPriceTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_DEFAULT_PRICE_TOOLTIP::getString);
            updateTimerInvervalMSTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_UPDATE_INTERVAL_MS_TOOLTIP::getString);
            volumeScaleTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_VOLUME_SCALE_TOOLTIP::getString);
            enableTargetPriceCheckBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_ENABLE_TARGET_PRICE_TOOLTIP::getString);
            targetPriceSteeringFactorTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_TARGET_PRICE_STEERING_FAC_TOOLTIP::getString);
            enableVolumeTrackingCheckBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_ENABLE_VOLUME_TRACKING_TOOLTIP::getString);
            volumeSteeringFactorTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_VOLUME_STEERING_FAC_TOOLTIP::getString);
            enableRandomWalkCheckBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_ENABLE_RANDOM_WALK_TOOLTIP::getString);
            volatilityTextBox.setHoverTooltipSupplier(TEXTS.BOT_SETTINGS_VOLATILITY_TOOLTIP::getString);


            enableTargetPriceCheckBox.setOnStateChanged(targetPriceSteeringFactorTextBox::setEnabled);
            enableVolumeTrackingCheckBox.setOnStateChanged(volumeSteeringFactorTextBox::setEnabled);
            enableRandomWalkCheckBox.setOnStateChanged(volatilityTextBox::setEnabled);

            enableCheckBox.setOnStateChanged((checked)->
            {
                defaultPriceTextBox.setEnabled(checked);
                updateTimerInvervalMSTextBox.setEnabled(checked);
                volumeScaleTextBox.setEnabled(checked);
                enableTargetPriceCheckBox.setEnabled(checked);
                targetPriceSteeringFactorTextBox.setEnabled(checked && enableTargetPriceCheckBox.isChecked());
                enableVolumeTrackingCheckBox.setEnabled(checked);
                volumeSteeringFactorTextBox.setEnabled(checked && enableVolumeTrackingCheckBox.isChecked());
                enableRandomWalkCheckBox.setEnabled(checked);
                volatilityTextBox.setEnabled(checked && enableRandomWalkCheckBox.isChecked());
            });



            addChild(titleLabel);
            addChild(enableCheckBox);
            addChild(defaultPriceTextBox);
            addChild(updateTimerInvervalMSTextBox);
            addChild(volumeScaleTextBox);
            addChild(enableTargetPriceCheckBox);
            addChild(targetPriceSteeringFactorTextBox);
            addChild(enableVolumeTrackingCheckBox);
            addChild(volumeSteeringFactorTextBox);
            addChild(enableRandomWalkCheckBox);
            addChild(volatilityTextBox);

            for(GuiElement child : getChilds())
            {
                child.setTooltipMousePositionAlignment(Alignment.RIGHT);
            }

            int targetHeight = 25 * getChilds().size() + 5;
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
                botSettings.defaultPrice = getInRange(defaultPriceTextBox.getInt(), 0, Integer.MAX_VALUE);
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
            int padding = 5;
            int spacing = 5;
            int width = getWidth() - padding * 2;
            //int height = getHeight() - padding * 2;
            int elementHeight = 20;

            int y = padding;
            titleLabel.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            enableCheckBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            defaultPriceTextBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            updateTimerInvervalMSTextBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            volumeScaleTextBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            enableTargetPriceCheckBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            targetPriceSteeringFactorTextBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            enableVolumeTrackingCheckBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            volumeSteeringFactorTextBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            enableRandomWalkCheckBox.setBounds(padding, y, width, elementHeight);
            y += elementHeight + spacing;
            volatilityTextBox.setBounds(padding, y, width, elementHeight);

        }
    }



    GuiScreen parentScreen;
    private static MarketSettingsScreen instance = null;
    //private ServerMarketSettingsData serverMarketSettingsData;
    private final TradingChartWidget tradingChart;
    private final Button saveButton;
    private final Button cancelButton;
    private final ListView listView;
    GeneralGuiElement generalGuiElement;
    private final VirtualOderBookGuiElement virtualOrderBookGuiElement;
    private final BotGuiElement botGuiElement;

    private final TimerMillis updateTimer;
    public MarketSettingsScreen(GuiScreen parent, Consumer<ServerMarketSettingsData> onSave) {
        super(TEXTS.TITLE);
        this.parentScreen = parent;

        tradingChart = new TradingChartWidget();
        tradingChart.enableBotTargetPriceDisplay(true);
        saveButton = new Button(TEXTS.SAVE_BUTTON.getString(), () -> {
            ServerMarketSettingsData settings = getSettings();
            if(settings != null)
            {
                onSave.accept(settings);
            }
            //onClose();
        });
        cancelButton = new Button(TEXTS.CANCEL_BUTTON.getString(), this::onClose);



        listView = new VerticalListView();
        Layout layout = new LayoutVertical();
        layout.stretchX = true;
        listView.setLayout(layout);

        generalGuiElement = new GeneralGuiElement();
        botGuiElement = new BotGuiElement();
        virtualOrderBookGuiElement = new VirtualOderBookGuiElement();


        listView.addChild(generalGuiElement);
        listView.addChild(virtualOrderBookGuiElement);
        listView.addChild(botGuiElement);


        addElement(saveButton);
        addElement(cancelButton);
        addElement(tradingChart);
        addElement(listView);

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
        if (parentScreen != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = 5;
        int spacing = 5;
        int width = getWidth() - padding * 2;
        int height = getHeight() - padding * 2;

        int chartWidth = width*2 / 3;

        tradingChart.setBounds(padding, padding, chartWidth, height);
        saveButton.setBounds(tradingChart.getRight()+spacing, padding, (width-chartWidth) / 2 - spacing, 20);
        cancelButton.setBounds(saveButton.getRight() + spacing, saveButton.getTop(), saveButton.getWidth(), 20);
        listView.setBounds(saveButton.getLeft(), saveButton.getBottom() + spacing, (width-chartWidth) - spacing, height - saveButton.getBottom());
    }

    public void setSettings(ServerMarketSettingsData settings)
    {
        //this.serverMarketSettingsData = settings;
        if(settings != null)
        {
            if(settings.tradingPairData != null) {
                TradingPair tradingPair = settings.tradingPairData.toTradingPair();
                selectMarket(tradingPair);
                generalGuiElement.selectMarket(tradingPair);
                virtualOrderBookGuiElement.selectMarket(tradingPair);
                botGuiElement.selectMarket(tradingPair);
            }
        }


        generalGuiElement.setShiftPriceCandleIntervalMS(settings.shiftPriceCandleIntervalMS);
        generalGuiElement.setMarketOpen(settings.marketOpen);

        if(settings.botSettingsData != null)
            botGuiElement.setBotSettings(settings.botSettingsData.settings);
        else
            botGuiElement.setBotSettings(null);

        if(settings.virtualOrderBookSettingsData != null)
            virtualOrderBookGuiElement.setVirtualOrderBookSettings(settings.virtualOrderBookSettingsData.settings);
        else
            virtualOrderBookGuiElement.setVirtualOrderBookSettings(null);


    }
    public ServerMarketSettingsData getSettings()
    {
        if(getSelectedMarket() == null)
            return null;
        ServerMarketSettingsData settings = new ServerMarketSettingsData(
                getSelectedMarket().getTradingPair(),
                botGuiElement.getBotSettings(),
                virtualOrderBookGuiElement.getVirtualOrderBookSettings(),
                generalGuiElement.isMarketOpen(),
                0,
                generalGuiElement.getShiftPriceCandleIntervalMS()
        );
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
            instance.getSelectedMarket().requestTradingViewData(instance.tradingChart.getMaxCandleCount(), 0,0,500,true ,instance.tradingChart::updateView);
        }
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
