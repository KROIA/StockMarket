package net.kroia.stockmarket.screen.uiElements.botsetup;

import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.util.StockMarketGuiElement;

public abstract class BotSetupGuiElement extends StockMarketGuiElement {

    protected final ServerVolatilityBot.Settings settings;

    public BotSetupGuiElement(ServerVolatilityBot.Settings settings) {
        super();
        this.settings = settings;
    }
}
