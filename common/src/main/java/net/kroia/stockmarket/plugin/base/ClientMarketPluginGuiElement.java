package net.kroia.stockmarket.plugin.base;

import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.base.GuiElement;
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
            saveButton = new Button("Save", plugin::saveSettings);
            nameLabel = new Label();
            enableCheckBox = new CheckBox("Enable Plugin");
            loggerCheckBox = new CheckBox("Debug");

            addChild(saveButton);
            addChild(nameLabel);
            addChild(enableCheckBox);
            addChild(loggerCheckBox);

            setHeight(20*4);
        }

        public void setSettings(Plugin.Settings settings)
        {
            nameLabel.setText(settings.name);
            enableCheckBox.setChecked(settings.pluginEnabled);
            loggerCheckBox.setChecked(settings.loggerEnabled);
        }
        public Plugin.Settings getSettings()
        {
            Plugin.Settings settings = new Plugin.Settings();
            settings.name = nameLabel.getText();
            settings.pluginEnabled = enableCheckBox.isChecked();
            settings.loggerEnabled = loggerCheckBox.isChecked();
            return settings;
        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width = getWidth();
            int height = getHeight();

            int elementHeight = height/3;
            saveButton.setBounds(0, 0, width, elementHeight);
            nameLabel.setBounds(0, saveButton.getBottom(), width, elementHeight);
            enableCheckBox.setBounds(0, nameLabel.getBottom(), width, elementHeight);
            loggerCheckBox.setBounds(0, enableCheckBox.getBottom(), width, elementHeight);
        }
    }


    private final ClientMarketPlugin plugin;

    private final GenericPluginSettingsWidget genericSettingsWidget;
    private final GuiElement customPluginWidget;



    public ClientMarketPluginGuiElement(ClientMarketPlugin plugin) {
        super();
        this.plugin = plugin;

        genericSettingsWidget = new GenericPluginSettingsWidget();
        genericSettingsWidget.setSettings(plugin.getSettings());
        addChild(genericSettingsWidget);

        customPluginWidget = getCustomPluginWidget();
        if(customPluginWidget == null)
            throw new IllegalStateException("Custom plugin widget cannot be null");
        addChild(customPluginWidget);

        setHeight(genericSettingsWidget.getHeight() + customPluginWidget.getHeight());
    }


    protected abstract GuiElement getCustomPluginWidget();



    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int width = getWidth();
        int height = getHeight();

        genericSettingsWidget.setBounds(0,0,width, genericSettingsWidget.getHeight());
        customPluginWidget.setBounds(0, genericSettingsWidget.getHeight(), width, height - genericSettingsWidget.getHeight());
    }
}
