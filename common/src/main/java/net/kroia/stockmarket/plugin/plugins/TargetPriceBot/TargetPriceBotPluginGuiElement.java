package net.kroia.stockmarket.plugin.plugins.TargetPriceBot;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.ClientMarketPluginGuiElement;
import net.kroia.stockmarket.plugin.base.IPluginSettings;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;

public class TargetPriceBotPluginGuiElement extends ClientMarketPluginGuiElement {

    public static final class TEXTS {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".target_price_bot_plugin_gui_element.";


        //public static final Component PLUGIN_NAME = Component.translatable(PREFIX + "name");
        //public static final Component PLUGIN_DESCRIPTION = Component.translatable(PREFIX + "description");

        public static final Component VOLUME_SCALE = Component.translatable(PREFIX + "volume_scale");
        public static final Component VOLUME_SCALE_TOOLTIP = Component.translatable(PREFIX + "volume_scale.tooltip");

        public static final Component KP = Component.translatable(PREFIX + "kp");
        public static final Component KP_TOOLTIP = Component.translatable(PREFIX + "kp.tooltip");

        public static final Component KI = Component.translatable(PREFIX + "ki");
        public static final Component KI_TOOLTIP = Component.translatable(PREFIX + "ki.tooltip");

        public static final Component IBOUND = Component.translatable(PREFIX + "ibound");
        public static final Component IBOUND_TOOLTIP = Component.translatable(PREFIX + "ibound.tooltip");

        public static final Component KD = Component.translatable(PREFIX + "kd");
        public static final Component KD_TOOLTIP = Component.translatable(PREFIX + "kd.tooltip");
    }

    public static class TargetPriceBotGuiElement extends StockMarketGuiElement
    {
        private final Label volumeScaleLabel;
        private final TextBox volumeScaleTextBox;

        private final Label kpLabel;
        private final TextBox kpTextBox;

        private final Label kiLabel;
        private final TextBox kiTextBox;

        private final Label iBoundLabel;
        private final TextBox iBoundTextBox;

        private final Label kdLabel;
        private final TextBox kdTextBox;


        public TargetPriceBotGuiElement()
        {
            super();
            this.setEnableBackground(false);
            this.setEnableOutline(false);

            volumeScaleLabel = new Label(TEXTS.VOLUME_SCALE.getString());
            volumeScaleLabel.setAlignment(Alignment.RIGHT);
            volumeScaleLabel.setHoverTooltipSupplier(TEXTS.VOLUME_SCALE_TOOLTIP::getString);
            volumeScaleLabel.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
            volumeScaleTextBox = new TextBox();
            volumeScaleTextBox.setAllowNumbers(true,true);
            volumeScaleTextBox.setAllowLetters(false);


            kpLabel = new Label(TEXTS.KP.getString());
            kpLabel.setAlignment(Alignment.RIGHT);
            kpLabel.setHoverTooltipSupplier(TEXTS.KP_TOOLTIP::getString);
            kpLabel.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
            kpTextBox = new TextBox();
            kpTextBox.setAllowNumbers(true,true);
            kpTextBox.setAllowLetters(false);


            kiLabel = new Label(TEXTS.KI.getString());
            kiLabel.setAlignment(Alignment.RIGHT);
            kiLabel.setHoverTooltipSupplier(TEXTS.KI_TOOLTIP::getString);
            kiLabel.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
            kiTextBox = new TextBox();
            kiTextBox.setAllowNumbers(true,true);
            kiTextBox.setAllowLetters(false);


            iBoundLabel = new Label(TEXTS.IBOUND.getString());
            iBoundLabel.setAlignment(Alignment.RIGHT);
            iBoundLabel.setHoverTooltipSupplier(TEXTS.IBOUND_TOOLTIP::getString);
            iBoundLabel.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
            iBoundTextBox = new TextBox();
            iBoundTextBox.setAllowNumbers(true,true);
            iBoundTextBox.setAllowLetters(false);


            kdLabel = new Label(TEXTS.KD.getString());
            kdLabel.setAlignment(Alignment.RIGHT);
            kdLabel.setHoverTooltipSupplier(TEXTS.KD_TOOLTIP::getString);
            kdLabel.setHoverTooltipMousePositionAlignment(Alignment.RIGHT);
            kdTextBox = new TextBox();
            kdTextBox.setAllowNumbers(true,true);
            kdTextBox.setAllowLetters(false);

            this.addChild(volumeScaleLabel);
            this.addChild(volumeScaleTextBox);

            this.addChild(kpLabel);
            this.addChild(kpTextBox);

            this.addChild(kiLabel);
            this.addChild(kiTextBox);

            this.addChild(iBoundLabel);
            this.addChild(iBoundTextBox);

            this.addChild(kdLabel);
            this.addChild(kdTextBox);

            this.setHeight(15*5);
        }

        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth();
            //int height = getHeight() - padding * 2;
            int elementHeight = 15;
            int labelWidthPercent = 70;

            int y = 0;
            int labelWidth = (width*labelWidthPercent)/100;

            volumeScaleLabel.setBounds(0, y, labelWidth, elementHeight);
            volumeScaleTextBox.setBounds(volumeScaleLabel.getRight(), volumeScaleLabel.getTop(), width-volumeScaleLabel.getWidth(), volumeScaleLabel.getHeight());
            y  += elementHeight;

            kpLabel.setBounds(volumeScaleLabel.getLeft(), y, labelWidth, elementHeight);
            kpTextBox.setBounds(kpLabel.getRight(), kpLabel.getTop(), width-kpLabel.getWidth(), kpLabel.getHeight());
            y  += elementHeight;

            kiLabel.setBounds(volumeScaleLabel.getLeft(), y, labelWidth, elementHeight);
            kiTextBox.setBounds(kiLabel.getRight(), kiLabel.getTop(), width-kiLabel.getWidth(), kiLabel.getHeight());
            y  += elementHeight;

            iBoundLabel.setBounds(volumeScaleLabel.getLeft(), y, labelWidth, elementHeight);
            iBoundTextBox.setBounds(iBoundLabel.getRight(), iBoundLabel.getTop(), width-iBoundLabel.getWidth(), iBoundLabel.getHeight());
            y  += elementHeight;

            kdLabel.setBounds(volumeScaleLabel.getLeft(), y, labelWidth, elementHeight);
            kdTextBox.setBounds(kdLabel.getRight(), kdLabel.getTop(), width-kdLabel.getWidth(), kdLabel.getHeight());
        }

        public void setSettings(TargetPriceBotPlugin.Settings settings)
        {
            volumeScaleTextBox.setText(Float.toString(settings.volumeScale));
            kpTextBox.setText(Float.toString(settings.kp));
            kiTextBox.setText(Float.toString(settings.ki));
            iBoundTextBox.setText(Float.toString(settings.iBound));
            kdTextBox.setText(Float.toString(settings.kd));
        }

        public void getSettings(TargetPriceBotPlugin.Settings settings)
        {
            settings.volumeScale = Math.max(0,(float)volumeScaleTextBox.getDouble());
            settings.kp = Math.min(10000, Math.max(-10000,(float)kpTextBox.getDouble()));
            settings.ki = Math.min(10000, Math.max(-10000,(float)kiTextBox.getDouble()));
            settings.iBound = Math.min(10000, Math.max(-10000,(float)iBoundTextBox.getDouble()));
            settings.kd = Math.min(10000, Math.max(-10000,(float)kdTextBox.getDouble()));
        }
    }

    private TargetPriceBotGuiElement guiElement;
    private float targetPrice = 0;
    private int lineWidth = 30;
    private final int markerColor = ColorUtilities.getRGB(0,0,255);
    public TargetPriceBotPluginGuiElement(ClientMarketPlugin plugin) {
        super(plugin);
        this.guiElement = new TargetPriceBotGuiElement();
        setCustomPluginWidget(guiElement);
    }

    /*@Override
    protected GuiElement getCustomPluginWidget() {
        if(guiElement == null)
        {
            this.guiElement = new TargetPriceBotGuiElement();
        }
        return guiElement;
    }*/

    public void setTargetPrice(float targetPrice)
    {
        this.targetPrice = targetPrice;
    }


    @Override
    public void setCustomSettings(IPluginSettings settings) {
        guiElement.setSettings((TargetPriceBotPlugin.Settings)settings);
    }

    @Override
    public void getCustomSettings(IPluginSettings settings) {
        guiElement.getSettings((TargetPriceBotPlugin.Settings)settings);
    }

    /*public void setSettings(TargetPriceBotPlugin.Settings settings)
    {
        guiElement.setSettings(settings);
    }

    public TargetPriceBotPlugin.Settings getSettings()
    {
        return guiElement.getSettings();
    }*/

    /**
     * Custom draw methode to draw stuff onto the candlestick chart in the management window
     * @param chartWidth defines the width of the visible area
     * @param chartHeight defines the hight of the visible area
     */
    @Override
    protected void drawInCandlestickChartArea(int chartWidth, int chartHeight) {
        // Get the Y-position of the specified price
        int targetPriceYPos = getCandlestickYPosForPrice(targetPrice);

        if(targetPriceYPos < 0 || targetPriceYPos > chartHeight)
            return; //out of bounds

        // Draw a horizontal line where the target price is located at
        drawRect(chartWidth-lineWidth, targetPriceYPos, lineWidth, 1, markerColor);

        // Draw a text with the current target price next to the horizontal line
        String text = "Bot Target Price: "+String.format("%.2f", targetPrice);
        int textWidth = getTextWidth(text);
        drawText(text, chartWidth-lineWidth-textWidth-20, targetPriceYPos-getTextHeight()/2);
    }

    @Override
    protected void drawInOrderbookChartArea(int chartWidth, int chartHeight) {
        // yPos = getOrderbookvolumeYPosForPrice(price)
    }



}
