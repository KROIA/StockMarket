package net.kroia.stockmarket.screen.uiElements.botsetup;

import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.minecraft.network.chat.Component;

public class BotSetup_estimatedPrice extends BotSetupGuiElement {

    private static final String NAME = "bot_settings_setup_screen_estimatedValue";
    public static final String PREFIX = "gui."+ StockMarketMod.MOD_ID+"."+NAME+".";

    private static final Component QUESTION = Component.translatable(PREFIX+"question");
    private final Label questionLabel;
    private final TextBox textBox;

    public BotSetup_estimatedPrice(ServerVolatilityBot.Settings settings) {
        super(settings);

        questionLabel = new Label(QUESTION.getString());
        textBox = new TextBox();
        textBox.setAllowLetters(false);

        questionLabel.setAlignment(Alignment.TOP_LEFT);


        addChild(questionLabel);
        addChild(textBox);

        textBox.setText(100);

    }



    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        int padding = 5;

        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;

        questionLabel.setBounds(padding, padding, width, height-20);
        textBox.setBounds(padding, questionLabel.getBottom(), width, 20);
    }

    public int getEstimatedPrice() {
        return textBox.getInt();
    }
    public void setEstimatedPrice(int estimatedPrice) {
        textBox.setText(estimatedPrice);
    }
}
