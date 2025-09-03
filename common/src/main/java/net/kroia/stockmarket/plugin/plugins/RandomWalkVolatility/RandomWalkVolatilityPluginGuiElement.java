package net.kroia.stockmarket.plugin.plugins.RandomWalkVolatility;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.ClientMarketPluginGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;

public class RandomWalkVolatilityPluginGuiElement extends ClientMarketPluginGuiElement {

    public static final class TEXTS {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".random_walk_volatility_plugin_gui_element.";
        public static final Component VOLATILITY = Component.translatable(PREFIX + "volatility");
        public static final Component UPDATE_TIMER_MS = Component.translatable(PREFIX + "update_timer_ms");
    }

    public class RandomWalkGuiElement extends StockMarketGuiElement
    {
        private final Label volatilityLabel;
        private final TextBox volatilityTextBox;

        private final Label updateTimerMSLabel;
        private final TextBox updateTimerMSTextBox;

        public RandomWalkGuiElement()
        {
            super();
            this.setEnableBackground(false);

            volatilityLabel = new Label(TEXTS.VOLATILITY.getString());
            volatilityLabel.setAlignment(Alignment.RIGHT);
            volatilityTextBox = new TextBox();
            volatilityTextBox.setAllowNumbers(true,true);
            volatilityTextBox.setAllowLetters(false);
            volatilityTextBox.setAllowNegativeNumbers(false);

            updateTimerMSLabel = new Label(TEXTS.UPDATE_TIMER_MS.getString());
            updateTimerMSLabel.setAlignment(Alignment.RIGHT);

            updateTimerMSTextBox = new TextBox();
            updateTimerMSTextBox.setAllowNumbers(true,false);
            updateTimerMSTextBox.setAllowLetters(false);
            updateTimerMSTextBox.setAllowNegativeNumbers(false);

            this.addChild(volatilityLabel);
            this.addChild(volatilityTextBox);

            this.addChild(updateTimerMSLabel);
            this.addChild(updateTimerMSTextBox);

            this.setHeight(15*2 + padding * 2);
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
            int labelWidth = (width*labelWidthPercent)/100;

            volatilityLabel.setBounds(padding, y, labelWidth, elementHeight);
            volatilityTextBox.setBounds(volatilityLabel.getRight(), volatilityLabel.getTop(), width- volatilityLabel.getWidth(), volatilityLabel.getHeight());

            y += elementHeight + padding;
            updateTimerMSLabel.setBounds(padding, y, labelWidth, elementHeight);
            updateTimerMSTextBox.setBounds(updateTimerMSLabel.getRight(), updateTimerMSLabel.getTop(), width- updateTimerMSLabel.getWidth(), updateTimerMSLabel.getHeight());
        }

        public void setSettings(RandomWalkVolatilityPlugin.Settings settings)
        {
            volatilityTextBox.setText(Float.toString(settings.volatility));
            updateTimerMSTextBox.setText(Integer.toString(settings.updateTimerIntervallMS));
        }

        public RandomWalkVolatilityPlugin.Settings getSettings()
        {
            RandomWalkVolatilityPlugin.Settings settings = new RandomWalkVolatilityPlugin.Settings();
            settings.volatility = Math.max(0,(float) volatilityTextBox.getDouble());
            settings.updateTimerIntervallMS = Math.max(1,updateTimerMSTextBox.getInt());
            return settings;
        }
    }

    private RandomWalkGuiElement guiElement;
    private float targetPrice = 0;
    private int lineWidth = 30;
    private int markerColor = ColorUtilities.getRGB(0,0,255);
    public RandomWalkVolatilityPluginGuiElement(ClientMarketPlugin plugin) {
        super(plugin);
    }

    @Override
    protected GuiElement getCustomPluginWidget() {
        if(guiElement == null)
        {
            this.guiElement = new RandomWalkGuiElement();
        }
        return guiElement;
    }

    public void setTargetPrice(float targetPrice)
    {
        this.targetPrice = targetPrice;
    }

    public void setSettings(RandomWalkVolatilityPlugin.Settings settings)
    {
        guiElement.setSettings(settings);
    }

    public RandomWalkVolatilityPlugin.Settings getSettings()
    {
        return guiElement.getSettings();
    }

    @Override
    protected void drawInCandlestickChartArea(int chartWidth, int chartHeight) {
        int targetPriceYPos = getCandlestickYPosForPrice(targetPrice);


        drawRect(chartWidth-lineWidth, targetPriceYPos, chartWidth, 1, markerColor);
    }

    @Override
    protected void drawInOrderbookChartArea(int chartWidth, int chartHeight) {

    }

}
