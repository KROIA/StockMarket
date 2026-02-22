# ExamplePluginGuiElement





## Example code


``` Java
public class ExamplePluginGuiElement extends ClientMarketPluginGuiElement {

    public static final class TEXTS {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".example_bot_plugin_gui_element.";
        public static final Component VOLUME_SCALE = Component.translatable(PREFIX + "volume_scale");
        public static final Component VOLUME_SCALE_TOOLTIP = Component.translatable(PREFIX + "volume_scale.tooltip");
    }

    public static class ExampleGuiElement extends StockMarketGuiElement
    {
        private final Label volumeScaleLabel;
        private final TextBox volumeScaleTextBox;

        public ExampleGuiElement()
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

        public void setSettings(ExamplePlugin.Settings settings)
        {
            volumeScaleTextBox.setText(Float.toString(settings.volumeScale));
        }

        public void getSettings(ExamplePlugin.Settings settings)
        {
            settings.volumeScale = Math.max(0,(float)volumeScaleTextBox.getDouble());
        }
    }

    private ExampleGuiElement guiElement;
    private float targetPrice = 0;
    private int lineWidth = 30;
    private final int markerColor = ColorUtilities.getRGB(0,0,255);
    public ExamplePluginGuiElement(ClientMarketPlugin plugin) {
        super(plugin);
        guiElement = new TargetPriceBotGuiElement();
        setCustomPluginWidget(guiElement);                                  // << Don't forget this!
    }


    public void setTargetPrice(float targetPrice)
    {
        this.targetPrice = targetPrice;
    }


    /**
     * Cast the settings to the specific Settings class used for the serverside Plugin and read the settings on to the UI
     */ 
    @Override
    public void setCustomSettings(IPluginSettings settings) {
        guiElement.setSettings((ExamplePlugin.Settings)settings);
    }


    /**
     * Read the settings from the UI and write them to the casted settings instance
     */ 
    @Override
    public void getCustomSettings(IPluginSettings settings) {
        guiElement.getSettings((ExamplePlugin.Settings)settings);
    }


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

    /**
     * Custom draw methode to draw stuff onto the orderbook chart area
     */
    @Override
    protected void drawInOrderbookChartArea(int chartWidth, int chartHeight) {
        // yPos = getOrderbookvolumeYPosForPrice(price) 
    }



}
```