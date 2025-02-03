package net.kroia.stockmarket.screen.uiElements.botsetup;

import net.kroia.modutilities.gui.elements.base.GuiElement;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;

public abstract class BotSetupGuiElement extends GuiElement {

    protected final ServerVolatilityBot.Settings settings;

    public BotSetupGuiElement(ServerVolatilityBot.Settings settings) {
        super();
        this.settings = settings;
    }
}
