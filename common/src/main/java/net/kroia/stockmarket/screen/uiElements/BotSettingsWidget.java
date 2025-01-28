package net.kroia.stockmarket.screen.uiElements;

import com.mojang.datafixers.util.Pair;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.screen.custom.BotSettingsScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class BotSettingsWidget extends GuiElement {

    private static final String PREFIX = BotSettingsScreen.PREFIX;

    public static final Component SETTING_ENABLED = Component.translatable(PREFIX+"setting_enabled");
    public static final Component SETTING_MAX_ORDER_COUNT = Component.translatable(PREFIX+"settings_max_order_count");
    public static final Component SETTINGS_VOLUME_SCALE = Component.translatable(PREFIX+"settings_volume_scale");
    public static final Component SETTINGS_VOLUME_SPREAD = Component.translatable(PREFIX+"settings_volume_spread");
    public static final Component SETTINGS_VOLUME_RANDOMNESS = Component.translatable(PREFIX+"settings_volume_randomness");
    public static final Component SETTINGS_UPDATE_INTERVAL = Component.translatable(PREFIX+"settings_update_interval");
    public static final Component SETTINGS_ORDER_RANDOMNESS = Component.translatable(PREFIX+"settings_order_randomness");
    public static final Component SETTINGS_VOLATILITY = Component.translatable(PREFIX+"settings_volatility");
    public static final Component SETTINGS_VOLATILITY_TIMER = Component.translatable(PREFIX+"settings_volatility_timer");
    public static final Component SETTINGS_VOLATILITY_TIMER_MIN = Component.translatable(PREFIX+"settings_volatility_timer_min");
    public static final Component SETTINGS_VOLATILITY_TIMER_MAX = Component.translatable(PREFIX+"settings_volatility_timer_max");
    public static final Component SETTINGS_TARGET_ITEM_BALANCE = Component.translatable(PREFIX+"settings_target_item_balance");
    public static final Component SETTINGS_IMBALANCE_PRICE_RANGE = Component.translatable(PREFIX+"settings_imbalance_price_range");
    public static final Component SETTINGS_IMBALANCE_PRICE_CHANGE_FAC_LINEAR = Component.translatable(PREFIX+"settings_imbalance_price_change_fac_linear");
    public static final Component SETTINGS_IMBALANCE_PRICE_CHANGE_FAC_QUAD = Component.translatable(PREFIX+"settings_imbalance_price_change_fac_quad");
    public static final Component SETTINGS_PID_P = Component.translatable(PREFIX+"settings_pid_p");
    public static final Component SETTINGS_PID_I = Component.translatable(PREFIX+"settings_pid_i");
    public static final Component SETTINGS_PID_D = Component.translatable(PREFIX+"settings_pid_d");
    public static final Component SETTINGS_PID_IBOUNDS = Component.translatable(PREFIX+"settings_pid_ibounds");
    public static final Component SETTINGS_PID_INTEGRATED_ERR = Component.translatable(PREFIX+"settings_pid_integrated_err");





    ServerVolatilityBot.Settings settings;
    final Runnable onSettingsChanged;


    final Pair<Label, CheckBox> enabled;
    final Pair<Label, TextBox> maxOrderCount;
    final Pair<Label, TextBox> volumeScale;
    final Pair<Label, TextBox> volumeSpread;
    final Pair<Label, TextBox> volumeRandomness;
    final Pair<Label, HorizontalSlider> updateInterval;


    final Pair<Label, TextBox> orderRandomnes;
    final Pair<Label, HorizontalSlider> volatility;
    final Pair<Label, TextBox> volatilityTimer;
    final Pair<Label, TextBox> volatilityTimerMin;
    final Pair<Label, TextBox> volatilityTimerMax;
    final Pair<Label, TextBox> targetItemBalance;
    final Pair<Label, TextBox> imbalancePriceRange;
    final Pair<Label, TextBox> imbalancePriceChangeFactorLinear;
    final Pair<Label, TextBox> imbalancePriceChangeFactorQuadratic;

    final Pair<Label, TextBox> pidP;
    final Pair<Label, TextBox> pidI;
    final Pair<Label, TextBox> pidD;
    final Pair<Label, TextBox> pidIBounds;
    final Pair<Label, TextBox> pidIntegratedError;

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
        maxOrderCount = new Pair<>(new Label(SETTING_MAX_ORDER_COUNT.getString()), new TextBox());
        volumeScale = new Pair<>(new Label(SETTINGS_VOLUME_SCALE.getString()), new TextBox());
        volumeSpread = new Pair<>(new Label(SETTINGS_VOLUME_SPREAD.getString()), new TextBox());
        volumeRandomness = new Pair<>(new Label(SETTINGS_VOLUME_RANDOMNESS.getString()), new TextBox());
        orderRandomnes = new Pair<>(new Label(SETTINGS_ORDER_RANDOMNESS.getString()), new TextBox());
        volatility = new Pair<>(new Label(SETTINGS_VOLATILITY.getString()), new HorizontalSlider());
        volatility.getSecond().setTooltipSupplier(()->String.format("%.2f", volatility.getSecond().getSliderValue()*100)+"%");
        volatilityTimer = new Pair<>(new Label(SETTINGS_VOLATILITY_TIMER.getString()), new TextBox());
        volatilityTimerMin = new Pair<>(new Label(SETTINGS_VOLATILITY_TIMER_MIN.getString()), new TextBox());
        volatilityTimerMax = new Pair<>(new Label(SETTINGS_VOLATILITY_TIMER_MAX.getString()), new TextBox());
        targetItemBalance = new Pair<>(new Label(SETTINGS_TARGET_ITEM_BALANCE.getString()), new TextBox());
        imbalancePriceRange = new Pair<>(new Label(SETTINGS_IMBALANCE_PRICE_RANGE.getString()), new TextBox());
        imbalancePriceChangeFactorLinear = new Pair<>(new Label(SETTINGS_IMBALANCE_PRICE_CHANGE_FAC_LINEAR.getString()), new TextBox());
        imbalancePriceChangeFactorQuadratic = new Pair<>(new Label(SETTINGS_IMBALANCE_PRICE_CHANGE_FAC_QUAD.getString()), new TextBox());

        pidP = new Pair<>(new Label(SETTINGS_PID_P.getString()), new TextBox());
        pidI = new Pair<>(new Label(SETTINGS_PID_I.getString()), new TextBox());
        pidD = new Pair<>(new Label(SETTINGS_PID_D.getString()), new TextBox());
        pidIBounds = new Pair<>(new Label(SETTINGS_PID_IBOUNDS.getString()), new TextBox());
        pidIntegratedError = new Pair<>(new Label(SETTINGS_PID_INTEGRATED_ERR.getString()), new TextBox());

        elements = new ArrayList<>();
        addElement(enabled);
        addElement(updateInterval);
        addElement(maxOrderCount);
        addElement(targetItemBalance);
        addElement(volumeScale);
        addElement(volumeSpread);
        addElement(volumeRandomness);
        addElement(orderRandomnes);
        addElement(volatility);
        addElement(volatilityTimer);
        addElement(volatilityTimerMin);
        addElement(volatilityTimerMax);
        addElement(imbalancePriceRange);
        addElement(imbalancePriceChangeFactorLinear);
        addElement(imbalancePriceChangeFactorQuadratic);
        addElement(pidP);
        addElement(pidI);
        addElement(pidD);
        addElement(pidIBounds);
        addElement(pidIntegratedError);

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
        maxOrderCount.getSecond().setText(Integer.toString(settings.maxOrderCount));
        volumeScale.getSecond().setText(Double.toString(settings.volumeScale));
        volumeSpread.getSecond().setText(Double.toString(settings.volumeSpread));
        volumeRandomness.getSecond().setText(Double.toString(settings.volumeRandomness));
        updateInterval.getSecond().setSliderValue(1-(double)(settings.updateTimerIntervallMS-100)/9900.0);
        orderRandomnes.getSecond().setText(Double.toString(settings.orderRandomness));
        volatility.getSecond().setSliderValue(settings.volatility/100.0);
        volatilityTimer.getSecond().setText(Long.toString(settings.timerMillis));
        volatilityTimerMin.getSecond().setText(Long.toString(settings.minTimerMillis));
        volatilityTimerMax.getSecond().setText(Long.toString(settings.maxTimerMillis));
        targetItemBalance.getSecond().setText(Long.toString(settings.targetItemBalance));
        imbalancePriceRange.getSecond().setText(Integer.toString(settings.imbalancePriceRange));
        imbalancePriceChangeFactorLinear.getSecond().setText(Double.toString(settings.imbalancePriceChangeFactor));
        imbalancePriceChangeFactorQuadratic.getSecond().setText(Double.toString(settings.imbalancePriceChangeQuadFactor));
        pidP.getSecond().setText(Double.toString(settings.pid_p));
        pidI.getSecond().setText(Double.toString(settings.pid_i));
        pidD.getSecond().setText(Double.toString(settings.pid_d));
        pidIBounds.getSecond().setText(Double.toString(settings.pid_iBound));
        pidIntegratedError.getSecond().setText(Double.toString(settings.integratedError));
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
        settings.maxOrderCount = getValidated(maxOrderCount.getSecond().getInt(), 1, Integer.MAX_VALUE);
        settings.volumeScale = getValidated(volumeScale.getSecond().getDouble(), 0.0, Double.MAX_VALUE);
        settings.volumeSpread = getValidated(volumeSpread.getSecond().getDouble(), 0.0, Double.MAX_VALUE);
        settings.volumeRandomness = getValidated(volumeRandomness.getSecond().getDouble(), 0.0, Double.MAX_VALUE);
        settings.updateTimerIntervallMS = getValidated((long)((1-updateInterval.getSecond().getSliderValue())*9900)+100, 100, 10000);
        settings.orderRandomness = getValidated(orderRandomnes.getSecond().getDouble(), 0.0, Double.MAX_VALUE);
        settings.volatility = getValidated(volatility.getSecond().getSliderValue()*100, 0.0, 100.0);
        settings.timerMillis = getValidated(volatilityTimer.getSecond().getLong(), 1, Long.MAX_VALUE);
        settings.minTimerMillis = getValidated(volatilityTimerMin.getSecond().getLong(), 1, Long.MAX_VALUE);
        settings.maxTimerMillis = getValidated(volatilityTimerMax.getSecond().getLong(), 1, Long.MAX_VALUE);
        settings.targetItemBalance = getValidated(targetItemBalance.getSecond().getLong(), 1, Long.MAX_VALUE);
        settings.imbalancePriceRange = getValidated(imbalancePriceRange.getSecond().getInt(), 1, Integer.MAX_VALUE);
        settings.imbalancePriceChangeFactor = getValidated(imbalancePriceChangeFactorLinear.getSecond().getDouble(), 0.0, Double.MAX_VALUE);
        settings.imbalancePriceChangeQuadFactor = getValidated(imbalancePriceChangeFactorQuadratic.getSecond().getDouble(), 0.0, Double.MAX_VALUE);
        settings.pid_p = getValidated(pidP.getSecond().getDouble(), -Double.MAX_VALUE, Double.MAX_VALUE);
        settings.pid_i = getValidated(pidI.getSecond().getDouble(), -Double.MAX_VALUE, Double.MAX_VALUE);
        settings.pid_d = getValidated(pidD.getSecond().getDouble(), -Double.MAX_VALUE, Double.MAX_VALUE);
        settings.pid_iBound = getValidated(pidIBounds.getSecond().getDouble(), -Double.MAX_VALUE, Double.MAX_VALUE);
        settings.integratedError = getValidated(pidIntegratedError.getSecond().getDouble(), -Double.MAX_VALUE, Double.MAX_VALUE);

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
