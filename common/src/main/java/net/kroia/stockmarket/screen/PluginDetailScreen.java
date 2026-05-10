package net.kroia.stockmarket.screen;

import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Label;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.pluginsystem.plugin.core.PluginSyncData;
import net.kroia.stockmarket.pluginsystem.screen.PluginGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.network.chat.Component;

/**
 * Dedicated screen for a single plugin that needs a full custom view.
 * Shows the plugin name and description at the top, with the
 * PluginGuiElement filling the remaining space below.
 */
public class PluginDetailScreen extends StockMarketGuiScreen {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".plugin_detail_screen.";
        private static final Component TITLE = Component.translatable(PREFIX + "title");
    }

    private final StockMarketGuiScreen parent;
    private final PluginSyncData pluginData;
    private final Label nameLabel;
    private final Label descriptionLabel;
    private final PluginGuiElement pluginGuiElement;

    /**
     * Creates a plugin detail screen for the given plugin.
     *
     * @param parent          the parent screen to return to on close
     * @param pluginData      the plugin sync data containing name and description
     * @param pluginGuiElement the plugin's GUI element to display
     */
    public PluginDetailScreen(StockMarketGuiScreen parent, PluginSyncData pluginData, PluginGuiElement pluginGuiElement) {
        super(Texts.TITLE);
        this.parent = parent;
        this.pluginData = pluginData;
        this.pluginGuiElement = pluginGuiElement;

        nameLabel = new Label(pluginData.getName());
        nameLabel.setAlignment(Label.Alignment.CENTER);

        descriptionLabel = new Label(pluginData.getDescription() != null ? pluginData.getDescription() : "");

        addElement(nameLabel);
        addElement(descriptionLabel);
        addElement(pluginGuiElement);
    }

    @Override
    public void onClose() {
        // Stop the runtime data stream when closing the screen
        if (pluginGuiElement != null) {
            pluginGuiElement.stopDataStream();
        }
        super.onClose();
        if (parent != null) {
            setScreen(parent);
        }
    }

    @Override
    protected void updateLayout(Gui gui) {
        int p = StockMarketGuiElement.padding;
        int s = StockMarketGuiElement.spacing;
        int w = getWidth() - 2 * p;
        int eh = StockMarketGuiElement.defaultElementHeight;

        nameLabel.setBounds(p, p, w, eh);
        descriptionLabel.setBounds(p, nameLabel.getBottom() + s, w, eh);
        // PluginGuiElement fills remaining space
        int guiTop = descriptionLabel.getBottom() + s;
        pluginGuiElement.setBounds(p, guiTop, w, getHeight() - guiTop - p);
    }
}
