package net.kroia.stockmarket.plugin.plugins.TargetPriceBot;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.ClientMarketPluginGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;

public class TargetPriceBotPluginGuiElement extends ClientMarketPluginGuiElement {

    public static final class TEXTS {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".target_price_bot_plugin_gui_element.";
        public static final Component VOLUME_SCALE = Component.translatable(PREFIX + "volume_scale");
        public static final Component VOLUME_SCALE_TOOLTIP = Component.translatable(PREFIX + "volume_scale.tooltip");
    }

    public class TargetPriceBotGuiElement extends StockMarketGuiElement
    {
        private final Label volumeScaleLabel;
        private final TextBox volumeScaleTextBox;

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

            this.addChild(volumeScaleLabel);
            this.addChild(volumeScaleTextBox);

            this.setHeight(15*1);
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

        }

        public void setSettings(TargetPriceBotPlugin.Settings settings)
        {
            volumeScaleTextBox.setText(Float.toString(settings.volumeScale));
        }

        public TargetPriceBotPlugin.Settings getSettings()
        {
            TargetPriceBotPlugin.Settings settings = new TargetPriceBotPlugin.Settings();
            settings.volumeScale = Math.max(0,(float)volumeScaleTextBox.getDouble());
            return settings;
        }
    }

    private TargetPriceBotGuiElement guiElement;
    private float targetPrice = 0;
    private int lineWidth = 30;
    private int markerColor = ColorUtilities.getRGB(0,0,255);
    public TargetPriceBotPluginGuiElement(ClientMarketPlugin plugin) {
        super(plugin);
    }

    @Override
    protected GuiElement getCustomPluginWidget() {
        if(guiElement == null)
        {
            this.guiElement = new TargetPriceBotGuiElement();
        }
        return guiElement;
    }

    public void setTargetPrice(float targetPrice)
    {
        this.targetPrice = targetPrice;
    }

    public void setSettings(TargetPriceBotPlugin.Settings settings)
    {
        guiElement.setSettings(settings);
    }

    public TargetPriceBotPlugin.Settings getSettings()
    {
        return guiElement.getSettings();
    }

    @Override
    protected void drawInCandlestickChartArea(int chartWidth, int chartHeight) {
        int targetPriceYPos = getCandlestickYPosForPrice(targetPrice);

        if(targetPriceYPos < 0 || targetPriceYPos > chartHeight)
            return; //out of bounds

        drawRect(chartWidth-lineWidth, targetPriceYPos, lineWidth, 1, markerColor);
        String text = "Bot Target Price: "+String.format("%.2f", targetPrice);
        int textWidth = getTextWidth(text);
        drawText(text, chartWidth-lineWidth-textWidth-20, targetPriceYPos-getTextHeight()/2);
    }

    @Override
    protected void drawInOrderbookChartArea(int chartWidth, int chartHeight) {

    }

}
