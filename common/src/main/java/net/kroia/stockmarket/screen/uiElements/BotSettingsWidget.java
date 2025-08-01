package net.kroia.stockmarket.screen.uiElements;

import com.mojang.datafixers.util.Pair;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class BotSettingsWidget extends StockMarketGuiElement {

    private static final String NAME = "bot_settings_screen";
    public static final String PREFIX = "gui."+ StockMarketMod.MOD_ID+"."+NAME+".";

    public static final Component SETTING_ENABLED = Component.translatable(PREFIX+"setting_enabled");
    public static final Component SETTINGS_UPDATE_INTERVAL = Component.translatable(PREFIX+"settings_update_interval");
    public static final Component SETTINGS_DEFAULT_PRICE = Component.translatable(PREFIX+"settings_default_price");
    public static final Component SETTINGS_ORDER_BOOK_VOLUME_SCALE = Component.translatable(PREFIX+"settings_order_book_volume_scale");
    public static final Component SETTINGS_NEAR_MARKET_VOLUME_SCALE = Component.translatable(PREFIX+"settings_near_market_volume_scale");
    public static final Component SETTINGS_VOLUME_ACCUMULATION_RATE = Component.translatable(PREFIX+"settings_volume_accumulation_rate");
    public static final Component SETTINGS_VOLUME_FAST_ACCUMULATION_RATE = Component.translatable(PREFIX+"settings_volume_fast_accumulation_rate");
    public static final Component SETTINGS_VOLUME_DECUMULATION_RATE = Component.translatable(PREFIX+"settings_volume_decumulation_rate");
    public static final Component SETTINGS_VOLUME_SCALE = Component.translatable(PREFIX+"settings_volume_scale");

    public static final Component SETTINGS_ENABLE_TARGET_PRICE = Component.translatable(PREFIX+"settings_enable_target_price");
    public static final Component SETTINGS_TARGET_PRICE_STEERING_FACTOR = Component.translatable(PREFIX+"settings_target_price_steering_factor");
    public static final Component SETTINGS_ENABLE_VOLUME_TRACKING = Component.translatable(PREFIX+"settings_enable_volume_tracking");
    public static final Component SETTINGS_VOLUME_STEERING_FACTOR = Component.translatable(PREFIX+"settings_volume_steering_factor");
    public static final Component SETTINGS_ENABLE_RANDOM_WALK = Component.translatable(PREFIX+"settings_enable_random_walk");
    public static final Component SETTINGS_VOLATILITY = Component.translatable(PREFIX+"settings_volatility");


    ServerVolatilityBot.Settings settings;
    final Runnable onSettingsChanged;


    final Pair<Label, CheckBox> enabled;
    final Pair<Label, HorizontalSlider> updateInterval;
    final Pair<Label, TextBox> defaultPrice;

    // Ghost orderbook settings
    final Pair<Label, TextBox> orderBookVolumeScale;
    final Pair<Label, TextBox> nearMarketVolumeScale;
    final Pair<Label, TextBox> volumeAccumulationRate;
    final Pair<Label, TextBox> volumeFastAccumulationRate;
    final Pair<Label, TextBox> volumeDecumulationRate;


    // Bot Feratures
    final Pair<Label, TextBox> volumeScale;
    final Pair<Label, CheckBox> enableTargetPrice;
    final Pair<Label, TextBox> targetPriceSteeringFactor;


    final Pair<Label, CheckBox> enableVolumeTracking;
    final Pair<Label, TextBox> volumeSteeringFactor;

    final Pair<Label, CheckBox> enableRandomWalk;
    final Pair<Label, HorizontalSlider> volatility;


    int maxLabelWidth = 0;
    final ArrayList<Pair<Label, GuiElement>> elements;


    public BotSettingsWidget(ServerVolatilityBot.Settings settings, Runnable onSettingsChanged)
    {
        super();
        this.onSettingsChanged = onSettingsChanged;
        this.settings = settings;

        enabled = new Pair<>(new Label(SETTING_ENABLED.getString()), new CheckBox("", this::onEnableCheckBoxChanged));
        enabled.getSecond().setTextAlignment(GuiElement.Alignment.RIGHT);
        updateInterval = new Pair<>(new Label(SETTINGS_UPDATE_INTERVAL.getString()), new HorizontalSlider());
        updateInterval.getSecond().setTooltipSupplier(this::getMarketSpeedTooltip);

        defaultPrice = new Pair<>(new Label(SETTINGS_DEFAULT_PRICE.getString()), new TextBox());
        orderBookVolumeScale = new Pair<>(new Label(SETTINGS_ORDER_BOOK_VOLUME_SCALE.getString()), new TextBox());
        nearMarketVolumeScale = new Pair<>(new Label(SETTINGS_NEAR_MARKET_VOLUME_SCALE.getString()), new TextBox());
        volumeAccumulationRate = new Pair<>(new Label(SETTINGS_VOLUME_ACCUMULATION_RATE.getString()), new TextBox());
        volumeFastAccumulationRate = new Pair<>(new Label(SETTINGS_VOLUME_FAST_ACCUMULATION_RATE.getString()), new TextBox());
        volumeDecumulationRate = new Pair<>(new Label(SETTINGS_VOLUME_DECUMULATION_RATE.getString()), new TextBox());

        volumeScale = new Pair<>(new Label(SETTINGS_VOLUME_SCALE.getString()), new TextBox());
        enableTargetPrice = new Pair<>(new Label(SETTINGS_ENABLE_TARGET_PRICE.getString()), new CheckBox("", (b)->{if(onSettingsChanged!=null)onSettingsChanged.run();}));
        targetPriceSteeringFactor = new Pair<>(new Label(SETTINGS_TARGET_PRICE_STEERING_FACTOR.getString()), new TextBox());

        enableVolumeTracking = new Pair<>(new Label(SETTINGS_ENABLE_VOLUME_TRACKING.getString()), new CheckBox("", (b)->{if(onSettingsChanged!=null)onSettingsChanged.run();}));
        volumeSteeringFactor = new Pair<>(new Label(SETTINGS_VOLUME_STEERING_FACTOR.getString()), new TextBox());

        enableRandomWalk = new Pair<>(new Label(SETTINGS_ENABLE_RANDOM_WALK.getString()), new CheckBox("", (b)->{if(onSettingsChanged!=null)onSettingsChanged.run();}));
        volatility = new Pair<>(new Label(SETTINGS_VOLATILITY.getString()), new HorizontalSlider());
        volatility.getSecond().setTooltipSupplier(()->String.format("%.2f", volatility.getSecond().getSliderValue()*100)+"%");


        elements = new ArrayList<>();
        addElement(enabled);
        addElement(updateInterval);
        addElement(defaultPrice);
        addElement(orderBookVolumeScale);
        addElement(nearMarketVolumeScale);
        addElement(volumeAccumulationRate);
        addElement(volumeFastAccumulationRate);
        addElement(volumeDecumulationRate);

        addElement(volumeScale);
        addElement(enableTargetPrice);
        addElement(targetPriceSteeringFactor);

        addElement(enableVolumeTracking);
        addElement(volumeSteeringFactor);

        addElement(enableRandomWalk);
        addElement(volatility);

        for(Pair<Label, GuiElement> pair : elements)
        {
            GuiElement element = pair.getSecond();
            if(element instanceof TextBox textBox)
            {
                textBox.setAllowLetters(false);
                textBox.setOnTextChanged(this::onTextChanged);
            }
            else if(element instanceof Slider slider)
            {
                slider.setOnValueChanged(this::onSliderChanged);
            }
        }

        setSettings(settings);
    }
    private <A extends Label, B extends GuiElement> void addElement(Pair<A, B> pair)
    {
        addChild(pair.getFirst());
        addChild(pair.getSecond());
        maxLabelWidth = Math.max(maxLabelWidth, getTextWidth(pair.getFirst().getText()));
        elements.add(new Pair<>(pair.getFirst(), pair.getSecond()));
    }

    public void setSettings(ServerVolatilityBot.Settings settings)
    {
        if(settings == null)
        {
            clear();
            return;
        }
        this.settings = settings;
        enabled.getSecond().setChecked(settings.enabled);
        updateInterval.getSecond().setSliderValue((double)(settings.updateTimerIntervallMS-100)/9900.0);
        defaultPrice.getSecond().setText(Integer.toString(settings.defaultPrice));
        orderBookVolumeScale.getSecond().setText(Float.toString(settings.orderBookVolumeScale));
        nearMarketVolumeScale.getSecond().setText(Float.toString(settings.nearMarketVolumeScale));
        volumeAccumulationRate.getSecond().setText(Float.toString(settings.volumeAccumulationRate));
        volumeFastAccumulationRate.getSecond().setText(Float.toString(settings.volumeFastAccumulationRate));
        volumeDecumulationRate.getSecond().setText(Float.toString(settings.volumeDecumulationRate));

        volumeScale.getSecond().setText(Float.toString(settings.volumeScale));
        enableTargetPrice.getSecond().setChecked(settings.enableTargetPrice);
        targetPriceSteeringFactor.getSecond().setText(Float.toString(settings.targetPriceSteeringFactor));

        enableVolumeTracking.getSecond().setChecked(settings.enableVolumeTracking);
        volumeSteeringFactor.getSecond().setText(Float.toString(settings.volumeSteeringFactor));

        enableRandomWalk.getSecond().setChecked(settings.enableRandomWalk);
        volatility.getSecond().setSliderValue(settings.volatility);
    }
    public ServerVolatilityBot.Settings getSettings()
    {
        validateUserInput();
        return settings;
    }
    public void clear()
    {
        for(Pair<Label, GuiElement> pair : elements)
        {
            GuiElement element = pair.getSecond();
            if(element instanceof TextBox textBox)
                textBox.setText("");
            else if(element instanceof CheckBox checkBox)
                checkBox.setChecked(false);
        }
    }

    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int padding = 5;
        int spacing = 5;
        int width = getWidth()-2*padding;
        int elementHeight = 20;


        int labelWidth = maxLabelWidth;
        int elementWidth = width-labelWidth-spacing;

        int y = padding;
        for(Pair<Label, GuiElement> pair : elements)
        {
            Label label = pair.getFirst();
            GuiElement element = pair.getSecond();
            label.setBounds(padding, y, labelWidth, elementHeight);
            element.setBounds(label.getRight() + spacing, y, elementWidth, elementHeight);
            y += elementHeight + spacing;
        }
        setHeight(y);
    }

    private void onEnableCheckBoxChanged(Boolean isSet)
    {
        settings.enabled = isSet;
        onSettingsChanged.run();
    }

    private void onEnableTargetPriceCheckBoxChanged()
    {
        boolean isEnabled = enableTargetPrice.getSecond().isChecked();
        targetPriceSteeringFactor.getSecond().setEnabled(isEnabled);
        onSettingsChanged.run();
    }
    private void onEnableVolumeTrackingCheckBoxChanged()
    {
        boolean isEnabled = enableVolumeTracking.getSecond().isChecked();
        volumeSteeringFactor.getSecond().setEnabled(isEnabled);
        onSettingsChanged.run();
    }
    private void onEnableRandomWalkCheckBoxChanged()
    {
        boolean isEnabled = enableRandomWalk.getSecond().isChecked();
        volatility.getSecond().setEnabled(isEnabled);
        onSettingsChanged.run();
    }
    private void onTextChanged(String text)
    {
        //validateUserInput();
        onSettingsChanged.run();
    }
    private void onSliderChanged(double value)
    {
        //validateUserInput();
        onSettingsChanged.run();
    }

    private void validateUserInput()
    {
        settings.enabled = enabled.getSecond().isChecked();
        settings.updateTimerIntervallMS = getValidated((long)((updateInterval.getSecond().getSliderValue())*9900)+100, 100, 10000);
        settings.defaultPrice = getValidated((int)defaultPrice.getSecond().getDouble(), 0, Integer.MAX_VALUE);
        settings.orderBookVolumeScale = getValidated((float) orderBookVolumeScale.getSecond().getDouble(), 0.0f, Float.MAX_VALUE);
        settings.nearMarketVolumeScale = getValidated((float) nearMarketVolumeScale.getSecond().getDouble(), 0.0f, Float.MAX_VALUE);
        settings.volumeAccumulationRate = getValidated((float)volumeAccumulationRate.getSecond().getDouble(), 0.0f, Float.MAX_VALUE);
        settings.volumeFastAccumulationRate = getValidated((float)volumeFastAccumulationRate.getSecond().getDouble(), 0.0f, Float.MAX_VALUE);
        settings.volumeDecumulationRate = getValidated((float)volumeDecumulationRate.getSecond().getDouble(), 0.0f, Float.MAX_VALUE);

        settings.volumeScale = getValidated((float)volumeScale.getSecond().getDouble(), 0.0f, Float.MAX_VALUE);
        settings.enableTargetPrice = enableTargetPrice.getSecond().isChecked();
        settings.targetPriceSteeringFactor = getValidated((float) targetPriceSteeringFactor.getSecond().getDouble(), 0.0f, Float.MAX_VALUE);

        settings.enableVolumeTracking = enableVolumeTracking.getSecond().isChecked();
        settings.volumeSteeringFactor = getValidated((float)volumeSteeringFactor.getSecond().getDouble(), 0.0f, Float.MAX_VALUE);

        settings.enableRandomWalk = enableRandomWalk.getSecond().isChecked();
        settings.volatility = getValidated((float)volatility.getSecond().getSliderValue(), 0.0f, 1.0f);
        setSettings(settings);
    }

    private int getValidated(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }
    private long getValidated(long value, long min, long max)
    {
        return Math.max(min, Math.min(max, value));
    }
    private double getValidated(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }
    private float getValidated(float value, float min, float max)
    {
        return Math.max(min, Math.min(max, value));
    }


    public String getMarketSpeedTooltip()
    {
        double speed = updateInterval.getSecond().getSliderValue();
        long ms = getMarketSpeedMS();
        if(speed > 0.8) {
            return "Slow "+ms+"ms";
        }
        else if(speed > 0.6) {
            return "Medium "+ms+"ms";
        }
        else if(speed > 0.4) {
            return "Fast "+ms+"ms";
        }
        else if(speed > 0.2) {
            return "Very Fast "+ms+"ms";
        }
        else {
            return "Steroids "+ms+"ms";
        }
    }
    public long getMarketSpeedMS()
    {
        double speed = updateInterval.getSecond().getSliderValue();
        // map from 100 to 10000
        return (long)((speed)*9900+100);
    }
}
