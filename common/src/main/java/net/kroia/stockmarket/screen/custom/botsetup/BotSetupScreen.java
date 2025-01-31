package net.kroia.stockmarket.screen.custom.botsetup;

import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.GuiScreen;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.screen.uiElements.botsetup.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class BotSetupScreen extends GuiScreen {

    private static final String NAME = "bot_settings_setup_screen";
    public static final String PREFIX = "gui."+ StockMarketMod.MOD_ID+"."+NAME+".";

    private static final Component TITLE = Component.translatable(PREFIX+"title");
    private static final Component CANCEL = Component.translatable(PREFIX+"cancel");
    private static final Component BACK = Component.translatable(PREFIX+"back");
    private static final Component NEXT = Component.translatable(PREFIX+"next");
    private static final Component APPLY = Component.translatable(PREFIX+"apply");

    private final ArrayList<BotSetupGuiElement> pages = new ArrayList<>();
    private int currentPage = 0;

    private final Button backButton;
    private final Button nextButton;

    private final Runnable onApply;
    private final Runnable onCancel;
    private final ServerVolatilityBot.Settings settings;

    private boolean autoChangeMoneyBalance;
    private boolean autoChangeItemBalance;
    private long botItemBalance;
    private long botMoneyBalance;

    // Question pages
    private final BotSetup_rarity rarityPage;
    private final BotSetup_estimatedPrice estimatedPricePage;
    private final BotSetup_volatility volatilityPage;
    private final BotSetup_marketSpeed marketSpeedPage;
    private final BotSetup_changeBotStocks changeBotStocksPage;


    public BotSetupScreen(Runnable onApply, Runnable onCancel, ServerVolatilityBot.Settings settings) {
        super(TITLE);
        this.onApply = onApply;
        this.onCancel = onCancel;
        this.settings = settings;


        backButton = new Button(CANCEL.getString(), this::onBackButtonClicked);
        nextButton = new Button(NEXT.getString(), this::onNextButtonClicked);


        // Question pages
        pages.add(rarityPage = new BotSetup_rarity(settings));
        pages.add(estimatedPricePage = new BotSetup_estimatedPrice(settings));
        pages.add(volatilityPage = new BotSetup_volatility(settings));
        pages.add(marketSpeedPage = new BotSetup_marketSpeed(settings));
        pages.add(changeBotStocksPage = new BotSetup_changeBotStocks(settings));

        rarityPage.setTooltipSupplyer(this::getRarityTooltip);
        marketSpeedPage.setTooltipSupplyer(this::getMarketSpeedTooltip);
        volatilityPage.setTooltipSupplyer(this::getVolatilityTooltip);


        addElement(backButton);
        addElement(nextButton);


        //backButton.setEnabled(false);

        for (BotSetupGuiElement current : pages) {
            addElement(current);
            current.setEnabled(false);
        }
        if(!pages.isEmpty())
            pages.get(0).setEnabled(true);
    }


    @Override
    protected void updateLayout(Gui gui) {
        int padding = 5;
        int width = getWidth()-2*padding;
        int height = getHeight()-2*padding;

        for(BotSetupGuiElement page : pages) {
            page.setBounds(padding, padding, width, height - 20-padding);
        }

        backButton.setBounds(padding, getHeight()-padding-20, width/2-padding/2, 20);
        nextButton.setBounds(padding+width/2+padding/2, getHeight()-padding-20, width/2-padding/2, 20);
    }

    private void onBackButtonClicked() {
        // Go to previous page
        if(currentPage > 0)
        {
            pages.get(currentPage).setEnabled(false);
            currentPage--;
            pages.get(currentPage).setEnabled(true);
            if(currentPage == pages.size()-2)
                nextButton.setLabel(NEXT.getString());
            else if(currentPage == 0)
            {
                backButton.setLabel(CANCEL.getString());
            }
        }
        else {
            if(onCancel != null)
                onCancel.run();
        }
    }
    private void onNextButtonClicked() {
        // Go to next page
        if(currentPage == pages.size()-1)
        {
            onApplyButtonClicked();
        }
        else {
            pages.get(currentPage).setEnabled(false);
            currentPage++;
            pages.get(currentPage).setEnabled(true);
            backButton.setLabel(BACK.getString());
            //backButton.setEnabled(true);
            if(currentPage == pages.size()-1)
                nextButton.setLabel(APPLY.getString());
        }
    }

    public long getBotItemBalance() {
        return botItemBalance;
    }
    public long getBotMoneyBalance() {
        return botMoneyBalance;
    }
    public boolean getAutoChangeMoneyBalance() {
        return autoChangeMoneyBalance;
    }
    public boolean getAutoChangeItemBalance() {
        return autoChangeItemBalance;
    }
    private void onApplyButtonClicked() {
        // Apply changes
        /*for(BotSetupGuiElement page : pages) {
            page.applyChanges();
        }*/
        int price = estimatedPricePage.getEstimatedPrice();
        double rarity = rarityPage.getRarity();
        double volatility = volatilityPage.getVolatility();

        botItemBalance = (long)(((1-rarity) * (1-rarity)) * 100000)+5000;
        botMoneyBalance = botItemBalance * price * 1000;
        autoChangeItemBalance = changeBotStocksPage.getAutoChangeItemBalance();
        autoChangeMoneyBalance = changeBotStocksPage.getAutoChangeMoneyBalance();

        settings.volatility = volatility*100;
        settings.imbalancePriceRange = price * 2;
        settings.targetItemBalance = botItemBalance;
        settings.updateTimerIntervallMS = getMarketSpeedMS();

        if(volatility > 0.25)
        {
            settings.imbalancePriceChangeQuadFactor = (volatility-0.25) * 8;
        }else {
            settings.imbalancePriceChangeQuadFactor = 0;
        }
        settings.imbalancePriceChangeFactor = volatility*0.1;
        settings.volumeRandomness = volatility*2;
        settings.volumeScale = (1-rarity) * 100;
        settings.orderRandomness = volatility * (1-rarity) * 10+1;


        if(onApply != null)
            onApply.run();
    }

    public String getVolatilityTooltip()
    {
        double vol = volatilityPage.getVolatility();
        if(vol < 0.2) {
            return "Low";
        }
        else if(vol < 0.4) {
            return "Medium";
        }
        else if(vol < 0.6) {
            return "High";
        }
        else if(vol < 0.8) {
            return "Very High";
        }
        else {
            return "Crypto mode";
        }
    }
    public String getRarityTooltip()
    {
        double rarity = rarityPage.getRarity();
        if(rarity < 0.2) {
            return "Common";
        }
        else if(rarity < 0.4) {
            return "Uncommon";
        }
        else if(rarity < 0.6) {
            return "Rare";
        }
        else if(rarity < 0.8) {
            return "Very Rare";
        }
        else {
            return "Legendary";
        }
    }
    public String getMarketSpeedTooltip()
    {
        double speed = marketSpeedPage.getMarketSpeed();
        long ms = getMarketSpeedMS();
        if(speed < 0.2) {
            return "Slow "+ms+"ms";
        }
        else if(speed < 0.4) {
            return "Medium "+ms+"ms";
        }
        else if(speed < 0.6) {
            return "Fast "+ms+"ms";
        }
        else if(speed < 0.8) {
            return "Very Fast "+ms+"ms";
        }
        else {
            return "Steroids "+ms+"ms";
        }
    }
    public long getMarketSpeedMS()
    {
        double speed = marketSpeedPage.getMarketSpeed();
        // map from 100 to 10000
        return (long)((1-speed)*9900+100);
    }

}
