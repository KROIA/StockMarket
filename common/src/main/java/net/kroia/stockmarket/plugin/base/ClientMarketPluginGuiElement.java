package net.kroia.stockmarket.plugin.base;

import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.modutilities.gui.geometry.Point;
import net.kroia.modutilities.gui.geometry.Rectangle;
import net.kroia.stockmarket.screen.uiElements.chart.TradingChartWidget;
import net.kroia.stockmarket.util.StockMarketGuiElement;

public abstract class ClientMarketPluginGuiElement extends StockMarketGuiElement {

    private class GenericPluginSettingsWidget extends GuiElement
    {
        private final Button saveButton;
        private final Label nameLabel;
        private final CheckBox enableCheckBox;
        private final CheckBox loggerCheckBox;
        public GenericPluginSettingsWidget()
        {
            super();
            this.setEnableBackground(false);
            this.setEnableOutline(false);
            saveButton = new Button("Save", plugin::saveSettings);
            nameLabel = new Label();
            nameLabel.setAlignment(Alignment.CENTER);
            enableCheckBox = new CheckBox("Enable Plugin");
            enableCheckBox.setTextAlignment(Alignment.RIGHT);
            loggerCheckBox = new CheckBox("Debug");
            loggerCheckBox.setTextAlignment(Alignment.RIGHT);

            addChild(saveButton);
            addChild(nameLabel);
            addChild(enableCheckBox);
            addChild(loggerCheckBox);

            setHeight(15*3);
        }

        public void setSettings(Plugin.Settings settings)
        {
            nameLabel.setText(settings.name);
            enableCheckBox.setChecked(settings.pluginEnabled);
            loggerCheckBox.setChecked(settings.loggerEnabled);
        }
        public void getSettings(Plugin.Settings settings)
        {
            settings.name = nameLabel.getText();
            settings.pluginEnabled = enableCheckBox.isChecked();
            settings.loggerEnabled = loggerCheckBox.isChecked();
        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            //int padding = StockMarketGuiElement.padding;
            int width = getWidth();
            int height = getHeight();

            int elementHeight = height/3;
            int saveButtonWidth = Math.min(50, width/3);

            nameLabel.setBounds(0, 0, width, elementHeight);
            saveButton.setBounds((width-saveButtonWidth)/2, nameLabel.getBottom(), saveButtonWidth, elementHeight);
            enableCheckBox.setBounds(0, saveButton.getBottom(), width/2, elementHeight);
            loggerCheckBox.setBounds(enableCheckBox.getRight(), saveButton.getBottom(), width-width/2, elementHeight);
        }
    }


    private final ClientMarketPlugin plugin;

    private final GenericPluginSettingsWidget genericSettingsWidget;
    private GuiElement customPluginWidget;


    private TradingChartWidget chartWidget;
    private Rectangle lastCandlestickChartArea = new Rectangle(0,0,0,0);
    private Rectangle lastOrderbookvolumeChartArea = new Rectangle(0,0,0,0);

    public ClientMarketPluginGuiElement(ClientMarketPlugin plugin) {
        super();
        this.plugin = plugin;

        genericSettingsWidget = new GenericPluginSettingsWidget();
        genericSettingsWidget.setSettings(plugin.getSettings());
        addChild(genericSettingsWidget);

       // customPluginWidget = getCustomPluginWidget();
       // if(customPluginWidget == null)
       //     throw new IllegalStateException("Custom plugin widget cannot be null");
       // addChild(customPluginWidget);

        setHeight(genericSettingsWidget.getHeight() + customPluginWidget.getHeight()+ padding*2);
    }


    public void setCustomPluginWidget(GuiElement customPluginWidget) {
        if(this.customPluginWidget != null)
            return;
        this.customPluginWidget  = customPluginWidget;
        addChild(this.customPluginWidget);
    }
    //protected abstract GuiElement getCustomPluginWidget();

    public void setChartWidget(TradingChartWidget chartWidget)
    {
        this.chartWidget = chartWidget;
    }


    @Override
    protected void render() {
        lastCandlestickChartArea = getGlobalCandlestickChartBounds();
        lastOrderbookvolumeChartArea = getGlobalOrderbookvolumeChartBounds();
        Point globalPos = getGlobalPositon();
        scissorPause();

        graphicsPushPose();
        graphicsTranslate(lastCandlestickChartArea.x - globalPos.x, lastCandlestickChartArea.y - globalPos.y, 0);
        drawInCandlestickChartArea(lastCandlestickChartArea.width, lastCandlestickChartArea.height);
        graphicsPopPose();

        graphicsPushPose();
        graphicsTranslate(lastOrderbookvolumeChartArea.x - globalPos.x, lastOrderbookvolumeChartArea.y - globalPos.y, 0);
        drawInOrderbookChartArea(lastOrderbookvolumeChartArea.width, lastOrderbookvolumeChartArea.height);
        graphicsPopPose();

        scissorResume();
    }

    protected abstract void drawInCandlestickChartArea(int chartWidth, int chartHeight);
    protected abstract void drawInOrderbookChartArea(int chartWidth, int chartHeight);

    @Override
    protected void layoutChanged() {
        int width = getWidth() - padding * 2;
        int height = getHeight() - padding * 2;


        genericSettingsWidget.setBounds(padding,padding, width, genericSettingsWidget.getHeight());
        customPluginWidget.setBounds(padding, genericSettingsWidget.getBottom(), width, height - genericSettingsWidget.getHeight());
    }


    /**
     * Used to set the settings for a specialized plugin
     * @param settings
     */
    public abstract void setCustomSettings(IPluginSettings settings);

    /**
     * Used to read the settings for a specialized plugin from the GUI-Element back to the container class
     */
    public abstract void getCustomSettings(IPluginSettings settings);

    /**
     * Used to set the generic settings that is the same for each plugin
     * @param settings
     */
    public void setPluginSettings_internal(Plugin.Settings settings)
    {
        if(settings != null)
        {
            genericSettingsWidget.setSettings(settings);
        }
    }
    public void getPluginSettings_internal(Plugin.Settings settings)
    {
        genericSettingsWidget.getSettings(settings);
    }


    public Rectangle getGlobalCandlestickChartBounds()
    {
        if(chartWidget == null)
        {
            return new Rectangle(0,0,0,0);
        }
        return chartWidget.getGlobalCandlestickChartBounds();
    }
    public int getGlobalCandlestickYPosForPrice(float price)
    {
        if(chartWidget == null)
        {
            return 0;
        }
        return chartWidget.getGlobalCandlestickYPosForPrice(price);
    }
    public int getCandlestickYPosForPrice(float price)
    {
        if(chartWidget == null)
        {
            return 0;
        }
        return getGlobalCandlestickYPosForPrice(price) - lastCandlestickChartArea.y;
    }


    public Rectangle getGlobalOrderbookvolumeChartBounds()
    {
        if(chartWidget == null)
        {
            return new Rectangle(0,0,0,0);
        }
        return chartWidget.getGlobalOrderbookvolumeChartBounds();
    }
    public int getGlobalOrderbookvolumeYPosForPrice(float price)
    {
        if(chartWidget == null)
        {
            return 0;
        }
        return chartWidget.getGlobalOrderbookvolumeYPosForPrice(price);
    }

    public int getOrderbookvolumeYPosForPrice(float price)
    {
        if(chartWidget == null)
        {
            return 0;
        }
        return getGlobalOrderbookvolumeYPosForPrice(price) - lastOrderbookvolumeChartArea.y;
    }
    public int getVolumeBarWidth(float volume)
    {
        if(chartWidget == null)
        {
            return 0;
        }
        return chartWidget.getVolumeBarWidth(volume);
    }
}
