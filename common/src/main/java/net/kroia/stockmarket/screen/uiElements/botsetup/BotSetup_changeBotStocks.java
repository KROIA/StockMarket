package net.kroia.stockmarket.screen.uiElements.botsetup;

import net.kroia.modutilities.gui.elements.CheckBox;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.minecraft.network.chat.Component;

public class BotSetup_changeBotStocks extends BotSetupGuiElement{
    private static final String NAME = "bot_settings_setup_screen_change_bot_stocks";
    public static final String PREFIX = "gui."+ StockMarketMod.MOD_ID+"."+NAME+".";

    public static final Component AUTO_CHANGE_MONEY = Component.translatable(PREFIX+"auto_change_money_balance");
    public static final Component AUTO_CHANGE_ITEM = Component.translatable(PREFIX+"auto_change_item_balance");

    private final CheckBox autoChangeMoneyBalance;
    private final CheckBox autoChangeItemBalance;

    public BotSetup_changeBotStocks(ServerVolatilityBot.Settings settings) {
        super(settings);

        autoChangeMoneyBalance = new CheckBox(AUTO_CHANGE_MONEY.getString());
        autoChangeItemBalance = new CheckBox(AUTO_CHANGE_ITEM.getString());

        addChild(autoChangeMoneyBalance);
        addChild(autoChangeItemBalance);

        autoChangeMoneyBalance.setChecked(true);
        autoChangeItemBalance.setChecked(true);
        autoChangeMoneyBalance.setTextAlignment(Alignment.TOP_LEFT);
        autoChangeItemBalance.setTextAlignment(Alignment.TOP_LEFT);
    }



    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int padding = 5;

        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;

        int lineCount1 = AUTO_CHANGE_MONEY.getString().split("\n").length;
        int lineCount2 = AUTO_CHANGE_ITEM.getString().split("\n").length;
        autoChangeMoneyBalance.setBounds(padding, padding, width, (getFont().lineHeight+2)*lineCount1);
        autoChangeItemBalance.setBounds(padding, autoChangeMoneyBalance.getBottom(), width, (getFont().lineHeight+2)*lineCount2);

    }

    public boolean getAutoChangeMoneyBalance() {
        return autoChangeMoneyBalance.isChecked();
    }
    public boolean getAutoChangeItemBalance() {
        return autoChangeItemBalance.isChecked();
    }
    public void setAutoChangeMoneyBalance(boolean value) {
        autoChangeMoneyBalance.setChecked(value);
    }
    public void setAutoChangeItemBalance(boolean value) {
        autoChangeItemBalance.setChecked(value);
    }
}
