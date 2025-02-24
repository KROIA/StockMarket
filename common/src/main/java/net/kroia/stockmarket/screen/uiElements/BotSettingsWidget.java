package net.kroia.stockmarket.screen.uiElements;

import com.mojang.datafixers.util.Pair;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.screen.custom.BotSettingsScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class BotSettingsWidget extends GuiElement {

    private static final String PREFIX = BotSettingsScreen.PREFIX;

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


    //public static final Component SETTING_ENABLED = Component.translatable(PREFIX+"setting_enabled");
    //public static final Component SETTING_MAX_ORDER_COUNT = Component.translatable(PREFIX+"settings_max_order_count");
    //public static final Component SETTINGS_VOLUME_SCALE = Component.translatable(PREFIX+"settings_volume_scale");
    //public static final Component SETTINGS_VOLUME_SPREAD = Component.translatable(PREFIX+"settings_volume_spread");
    //public static final Component SETTINGS_VOLUME_RANDOMNESS = Component.translatable(PREFIX+"settings_volume_randomness");

    //public static final Component SETTINGS_ORDER_RANDOMNESS = Component.translatable(PREFIX+"settings_order_randomness");
    //public static final Component SETTINGS_VOLATILITY = Component.translatable(PREFIX+"settings_volatility");
    //public static final Component SETTINGS_VOLATILITY_TIMER = Component.translatable(PREFIX+"settings_volatility_timer");
    //public static final Component SETTINGS_VOLATILITY_TIMER_MIN = Component.translatable(PREFIX+"settings_volatility_timer_min");
    //public static final Component SETTINGS_VOLATILITY_TIMER_MAX = Component.translatable(PREFIX+"settings_volatility_timer_max");
    //public static final Component SETTINGS_TARGET_ITEM_BALANCE = Component.translatable(PREFIX+"settings_target_item_balance");
    //public static final Component SETTINGS_IMBALANCE_PRICE_RANGE = Component.translatable(PREFIX+"settings_imbalance_price_range");
    //public static final Component SETTINGS_IMBALANCE_PRICE_CHANGE_FAC_LINEAR = Component.translatable(PREFIX+"settings_imbalance_price_change_fac_linear");
    //public static final Component SETTINGS_IMBALANCE_PRICE_CHANGE_FAC_QUAD = Component.translatable(PREFIX+"settings_imbalance_price_change_fac_quad");
    //public static final Component SETTINGS_PID_P = Component.translatable(PREFIX+"settings_pid_p");
    //public static final Component SETTINGS_PID_I = Component.translatable(PREFIX+"settings_pid_i");
    //public static final Component SETTINGS_PID_D = Component.translatable(PREFIX+"settings_pid_d");
    //public static final Component SETTINGS_PID_IBOUNDS = Component.translatable(PREFIX+"settings_pid_ibounds");
    //public static final Component SETTINGS_PID_INTEGRATED_ERR = Component.translatable(PREFIX+"settings_pid_integrated_err");





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




    //final Pair<Label, TextBox> maxOrderCount;
    //final Pair<Label, TextBox> volumeScale;
    //final Pair<Label, TextBox> volumeSpread;
    //final Pair<Label, TextBox> volumeRandomness;



    //final Pair<Label, TextBox> orderRandomnes;
    //final Pair<Label, TextBox> volatilityTimer;
    //final Pair<Label, TextBox> volatilityTimerMin;
    //final Pair<Label, TextBox> volatilityTimerMax;
    //final Pair<Label, TextBox> targetItemBalance;
    //final Pair<Label, TextBox> imbalancePriceRange;
    //final Pair<Label, TextBox> imbalancePriceChangeFactorLinear;
    //final Pair<Label, TextBox> imbalancePriceChangeFactorQuadratic;

    //final Pair<Label, TextBox> pidP;
    //final Pair<Label, TextBox> pidI;
    //final Pair<Label, TextBox> pidD;
    //final Pair<Label, TextBox> pidIBounds;
    //final Pair<Label, TextBox> pidIntegratedError;

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
        enableTargetPrice = new Pair<>(new Label(SETTINGS_ENABLE_TARGET_PRICE.getString()), new CheckBox("", onSettingsChanged));
        targetPriceSteeringFactor = new Pair<>(new Label(SETTINGS_TARGET_PRICE_STEERING_FACTOR.getString()), new TextBox());

        enableVolumeTracking = new Pair<>(new Label(SETTINGS_ENABLE_VOLUME_TRACKING.getString()), new CheckBox("", onSettingsChanged));
        volumeSteeringFactor = new Pair<>(new Label(SETTINGS_VOLUME_STEERING_FACTOR.getString()), new TextBox());

        enableRandomWalk = new Pair<>(new Label(SETTINGS_ENABLE_RANDOM_WALK.getString()), new CheckBox("", onSettingsChanged));
        volatility = new Pair<>(new Label(SETTINGS_VOLATILITY.getString()), new HorizontalSlider());
        volatility.getSecond().setTooltipSupplier(()->String.format("%.2f", volatility.getSecond().getSliderValue()*100)+"%");

        //maxOrderCount = new Pair<>(new Label(SETTING_MAX_ORDER_COUNT.getString()), new TextBox());
        //volumeScale = new Pair<>(new Label(SETTINGS_VOLUME_SCALE.getString()), new TextBox());
        //volumeSpread = new Pair<>(new Label(SETTINGS_VOLUME_SPREAD.getString()), new TextBox());
        //volumeRandomness = new Pair<>(new Label(SETTINGS_VOLUME_RANDOMNESS.getString()), new TextBox());
        //orderRandomnes = new Pair<>(new Label(SETTINGS_ORDER_RANDOMNESS.getString()), new TextBox());

        //volatilityTimer = new Pair<>(new Label(SETTINGS_VOLATILITY_TIMER.getString()), new TextBox());
        //volatilityTimerMin = new Pair<>(new Label(SETTINGS_VOLATILITY_TIMER_MIN.getString()), new TextBox());
        //volatilityTimerMax = new Pair<>(new Label(SETTINGS_VOLATILITY_TIMER_MAX.getString()), new TextBox());
        //targetItemBalance = new Pair<>(new Label(SETTINGS_TARGET_ITEM_BALANCE.getString()), new TextBox());
        //imbalancePriceRange = new Pair<>(new Label(SETTINGS_IMBALANCE_PRICE_RANGE.getString()), new TextBox());
        //imbalancePriceChangeFactorLinear = new Pair<>(new Label(SETTINGS_IMBALANCE_PRICE_CHANGE_FAC_LINEAR.getString()), new TextBox());
        //imbalancePriceChangeFactorQuadratic = new Pair<>(new Label(SETTINGS_IMBALANCE_PRICE_CHANGE_FAC_QUAD.getString()), new TextBox());
//
        //pidP = new Pair<>(new Label(SETTINGS_PID_P.getString()), new TextBox());
        //pidI = new Pair<>(new Label(SETTINGS_PID_I.getString()), new TextBox());
        //pidD = new Pair<>(new Label(SETTINGS_PID_D.getString()), new TextBox());
        //pidIBounds = new Pair<>(new Label(SETTINGS_PID_IBOUNDS.getString()), new TextBox());
        //pidIntegratedError = new Pair<>(new Label(SETTINGS_PID_INTEGRATED_ERR.getString()), new TextBox());

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
        //addElement(maxOrderCount);
        //addElement(maxOrderCount);
        //addElement(targetItemBalance);
        //addElement(volumeScale);
        //addElement(volumeSpread);
        //addElement(volumeRandomness);
        //addElement(orderRandomnes);

        //addElement(volatilityTimer);
        //addElement(volatilityTimerMin);
        //addElement(volatilityTimerMax);
        //addElement(imbalancePriceRange);
        //addElement(imbalancePriceChangeFactorLinear);
        //addElement(imbalancePriceChangeFactorQuadratic);
        //addElement(pidP);
        //addElement(pidI);
        //addElement(pidD);
        //addElement(pidIntegratedError);
        //addElement(pidIBounds);


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
        updateInterval.getSecond().setSliderValue(1-(double)(settings.updateTimerIntervallMS-100)/9900.0);
        defaultPrice.getSecond().setText(Integer.toString(settings.defaultPrice));
        orderBookVolumeScale.getSecond().setText(Float.toString(settings.orderBookVolumeScale));
        nearMarketVolumeScale.getSecond().setText(Float.toString(settings.nearMarketVolumeStrength));
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

        //maxOrderCount.getSecond().setText(Integer.toString(settings.maxOrderCount));
        //volumeScale.getSecond().setText(Double.toString(settings.volumeScale));
        //volumeSpread.getSecond().setText(Double.toString(settings.volumeSpread));
        //volumeRandomness.getSecond().setText(Double.toString(settings.volumeRandomness));

        //orderRandomnes.getSecond().setText(Double.toString(settings.orderRandomness));

        //volatilityTimer.getSecond().setText(Long.toString(settings.timerMillis));
        //volatilityTimerMin.getSecond().setText(Long.toString(settings.minTimerMillis));
        //volatilityTimerMax.getSecond().setText(Long.toString(settings.maxTimerMillis));
        //targetItemBalance.getSecond().setText(Long.toString(settings.targetItemBalance));
        //imbalancePriceRange.getSecond().setText(Integer.toString(settings.imbalancePriceRange));
        //imbalancePriceChangeFactorLinear.getSecond().setText(Double.toString(settings.imbalancePriceChangeFactor));
        //imbalancePriceChangeFactorQuadratic.getSecond().setText(Double.toString(settings.imbalancePriceChangeQuadFactor));
        //pidP.getSecond().setText(Double.toString(settings.pid_p));
        //pidI.getSecond().setText(Double.toString(settings.pid_i));
        //pidD.getSecond().setText(Double.toString(settings.pid_d));
        //pidIBounds.getSecond().setText(Double.toString(settings.pid_iBound));
        //pidIntegratedError.getSecond().setText(Double.toString(settings.integratedError));
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

    private void onEnableCheckBoxChanged()
    {
        settings.enabled = enabled.getSecond().isChecked();
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
        settings.updateTimerIntervallMS = getValidated((long)((1-updateInterval.getSecond().getSliderValue())*9900)+100, 100, 10000);
        settings.defaultPrice = getValidated((int)defaultPrice.getSecond().getDouble(), 0, Integer.MAX_VALUE);
        settings.orderBookVolumeScale = getValidated((float) orderBookVolumeScale.getSecond().getDouble(), 0.0f, Float.MAX_VALUE);
        settings.nearMarketVolumeStrength = getValidated((float) nearMarketVolumeScale.getSecond().getDouble(), 0.0f, Float.MAX_VALUE);
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

        //settings.maxOrderCount = getValidated(maxOrderCount.getSecond().getInt(), 1, Integer.MAX_VALUE);
        //settings.volumeScale = getValidated(volumeScale.getSecond().getDouble(), 0.0, Double.MAX_VALUE);
        //settings.volumeSpread = getValidated(volumeSpread.getSecond().getDouble(), 0.0, Double.MAX_VALUE);
        //settings.volumeRandomness = getValidated(volumeRandomness.getSecond().getDouble(), 0.0, Double.MAX_VALUE);

        //settings.orderRandomness = getValidated(orderRandomnes.getSecond().getDouble(), 0.0, Double.MAX_VALUE);

        //settings.timerMillis = getValidated(volatilityTimer.getSecond().getLong(), 1, Long.MAX_VALUE);
        //settings.minTimerMillis = getValidated(volatilityTimerMin.getSecond().getLong(), 1, Long.MAX_VALUE);
        //settings.maxTimerMillis = getValidated(volatilityTimerMax.getSecond().getLong(), 1, Long.MAX_VALUE);
        //settings.targetItemBalance = getValidated(targetItemBalance.getSecond().getLong(), Long.MIN_VALUE, Long.MAX_VALUE);
        //settings.imbalancePriceRange = getValidated(imbalancePriceRange.getSecond().getInt(), 1, Integer.MAX_VALUE);
        //settings.imbalancePriceChangeFactor = getValidated(imbalancePriceChangeFactorLinear.getSecond().getDouble(), 0.0, Double.MAX_VALUE);
        //settings.imbalancePriceChangeQuadFactor = getValidated(imbalancePriceChangeFactorQuadratic.getSecond().getDouble(), 0.0, Double.MAX_VALUE);
        //settings.pid_p = getValidated(pidP.getSecond().getDouble(), -Double.MAX_VALUE, Double.MAX_VALUE);
        //settings.pid_i = getValidated(pidI.getSecond().getDouble(), -Double.MAX_VALUE, Double.MAX_VALUE);
        //settings.pid_d = getValidated(pidD.getSecond().getDouble(), -Double.MAX_VALUE, Double.MAX_VALUE);
        //settings.pid_iBound = getValidated(pidIBounds.getSecond().getDouble(), -Double.MAX_VALUE, Double.MAX_VALUE);
        //settings.integratedError = getValidated(pidIntegratedError.getSecond().getDouble(), -Double.MAX_VALUE, Double.MAX_VALUE);

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
        if(speed < 0.2) {
            return "Slow "+ms+"ms";
        }
        else if(speed < 0.4) {
            return "Medium "+ms+"ms";
        }
        else if(speed < 0.6) {
            return "Fast "+ms+"ms";
        }
        else if(speed < 0.8) {
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
        return (long)((1-speed)*9900+100);
    }
}
