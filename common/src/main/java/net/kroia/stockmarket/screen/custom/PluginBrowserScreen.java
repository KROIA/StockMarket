package net.kroia.stockmarket.screen.custom;

import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.networking.PluginTypesRequest;
import net.kroia.stockmarket.screen.uiElements.TradingPairView;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PluginBrowserScreen extends StockMarketGuiScreen {

    public static final class TEXTS
    {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".plugin_browser_screen.";

        public static final Component TITLE = Component.translatable(PREFIX + "title");
        public static final Component OK_BUTTON = Component.translatable(PREFIX + "ok_button");

        // PluginWidget
        public static final Component PLUGIN_USED_CHECKBOX = Component.translatable(PREFIX + "plugin_used_checkbox");
        public static final Component PLUGIN_USED_CHECKBOX_TOOLTIP = Component.translatable(PREFIX + "plugin_used_checkbox.tooltip");

    }

    private final class PluginWidget extends StockMarketGuiElement
    {
        private final String pluginID;
        private final CheckBox pluginUsedCheckBox;
        private final Label pluginNameLabel;
        private final Label pluginDescriptionLabel;

        public PluginWidget(String pluginID, boolean isUsed, String name, String description)
        {
            super();
            this.pluginID = pluginID;
            pluginUsedCheckBox = new CheckBox(TEXTS.PLUGIN_USED_CHECKBOX.getString());
            pluginUsedCheckBox.setHoverTooltipSupplier(TEXTS.PLUGIN_USED_CHECKBOX_TOOLTIP::getString);
            pluginUsedCheckBox.setChecked(isUsed);
            pluginNameLabel = new Label(name);
            pluginDescriptionLabel = new Label(description);
            pluginDescriptionLabel.setAlignment(Alignment.TOP_LEFT);

            addChild(pluginUsedCheckBox);
            addChild(pluginNameLabel);
            addChild(pluginDescriptionLabel);

            setHeight(60);
        }

        String getPluginID()
        {
            return pluginID;
        }
        boolean isUsed()
        {
            return pluginUsedCheckBox.isChecked();
        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int width =  getWidth();
            int height = getHeight();
            int padding = 2;

            int splitXPos = width / 4;
            pluginNameLabel.setBounds(padding,padding, splitXPos, 20);
            pluginUsedCheckBox.setBounds(padding,pluginNameLabel.getBottom(), splitXPos, 20);
            pluginDescriptionLabel.setBounds(splitXPos,padding, width-splitXPos-padding, height-padding*2);
        }
    }


    private final TradingPair tradingPair;
    private final List<PluginWidget>  pluginWidgets = new ArrayList<>();
    private final Runnable onChangesApplied;

    private final TradingPairView tradingPairView;
    private final Label titleLabel;
    private final ListView pluginsListView;
    private final Button okButton;


    public PluginBrowserScreen(TradingPair tradingPair, Runnable onChangesApplied, Screen parent) {
        super(TEXTS.TITLE, parent);
        this.tradingPair = tradingPair;
        this.onChangesApplied = onChangesApplied;

        tradingPairView  = new TradingPairView(tradingPair);
        tradingPairView.setClickable(false);
        titleLabel = new Label(TEXTS.TITLE.getString());
        pluginsListView = new VerticalListView();
        Layout layout = new LayoutVertical();
        layout.stretchX = true;
        pluginsListView.setLayout(layout);

        okButton = new Button(TEXTS.OK_BUTTON.getString(), this::applyChanges);

        addElement(tradingPairView);
        addElement(titleLabel);
        addElement(pluginsListView);
        addElement(okButton);

        BACKEND_INSTANCES.CLIENT_PLUGIN_MANAGER.requestMarketPluginTypes(tradingPair, (usedPlugins)->
        {
            BACKEND_INSTANCES.CLIENT_PLUGIN_MANAGER.requestMarketPluginTypes((availablePlugins)->
            {
                populatePluginList(usedPlugins, availablePlugins.marketPlugins);
            });
        });
    }

    @Override
    protected void updateLayout(Gui gui) {
        int width =  getWidth();
        int height = getHeight();

        tradingPairView.setBounds(0, 0, 50,20);
        titleLabel.setBounds(tradingPairView.getRight(),0, tradingPairView.getWidth()-width, tradingPairView.getHeight());

        int okButtonWidth = 60;
        okButton.setBounds((width-okButtonWidth)/2,height-20,okButtonWidth,20);
        pluginsListView.setBounds(0,titleLabel.getBottom(),width,okButton.getTop()-titleLabel.getBottom());
    }



    private void applyChanges()
    {
        List<String> usedPluginIDs = new ArrayList<>();
        for (PluginWidget pluginWidget : pluginWidgets)
        {
            if(pluginWidget.isUsed())
            {
                usedPluginIDs.add(pluginWidget.getPluginID());
            }
        }
        sendPluginUpdateToSever(tradingPair, usedPluginIDs);
    }


    private void populatePluginList(List<String> usedPlugins, List<PluginTypesRequest.PluginInfo> allPlugins)
    {
        pluginWidgets.clear();
        pluginsListView.removeChilds();

        List<PluginTypesRequest.PluginInfo> sortedPlugins = new ArrayList<>(allPlugins);
        // Sort by name
        sortedPlugins.sort(Comparator.comparing(a -> a.displayName));

        for(PluginTypesRequest.PluginInfo pluginInfo : sortedPlugins)
        {
            boolean isUsed = usedPlugins.contains(pluginInfo.pluginTypeID);
            PluginWidget newWidget =  new PluginWidget(pluginInfo.pluginTypeID, isUsed,  pluginInfo.displayName, pluginInfo.description);
            pluginWidgets.add(newWidget);
            pluginsListView.addChild(newWidget);
        }
    }
    private void sendPluginUpdateToSever(TradingPair tradingPair, List<String> usedPlugins)
    {
        BACKEND_INSTANCES.CLIENT_PLUGIN_MANAGER.requestSetMarketPluginTypes(tradingPair, usedPlugins, (success)->
        {
            if(!success)
                error("Can't update market plugins");
            else if(onChangesApplied != null)
                onChangesApplied.run();
        });
    }
}
