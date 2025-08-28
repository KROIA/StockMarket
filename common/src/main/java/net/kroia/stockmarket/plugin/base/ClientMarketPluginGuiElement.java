package net.kroia.stockmarket.plugin.base;

import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.util.StockMarketGuiElement;

public abstract class ClientMarketPluginGuiElement extends StockMarketGuiElement {

    private class GenericPluginWidget extends GuiElement
    {
        public GenericPluginWidget()
        {

        }
        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {

        }
    }


    private final ClientMarketPlugin plugin;

    private final GuiElement customPluginWidget;



    public ClientMarketPluginGuiElement(ClientMarketPlugin plugin) {
        super();
        this.plugin = plugin;


        customPluginWidget = getCustomPluginWidget();
        if(customPluginWidget != null)
            addChild(customPluginWidget);
    }


    protected abstract GuiElement getCustomPluginWidget();



    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {

    }
}
