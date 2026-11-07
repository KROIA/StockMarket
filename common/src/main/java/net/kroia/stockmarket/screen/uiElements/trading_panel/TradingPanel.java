package net.kroia.stockmarket.screen.uiElements.trading_panel;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TradingPanel extends TabElement {
    private static class Texts{
        private static final String PREFIX = "gui."+ StockMarketMod.MOD_ID + ".trade_panel.";
        private static final Component MARKET_ORDER_TAB_TITLE = Component.translatable(PREFIX +"market_order_tab_title");
        private static final Component LIMIT_ORDER_TAB_TITLE = Component.translatable(PREFIX +"limit_order_tab_title");
    }

    // Tab index constants
    private static final int MARKET_ORDER_TAB = 0;
    private static final int LIMIT_ORDER_TAB = 1;

    //private final TabElement tabElement;
    private final MarketOrderPanel marketOrderPanel;
    private final LimitOrderPanel limitOrderPanel;

    // Track the previously selected tab to detect tab switches
    private int lastSelectedTab = -1;


    public TradingPanel(Consumer<Double> onBuyMarket, Consumer<Double> onSellMarket,
                        BiConsumer<Double, Double> onLimitBuy, BiConsumer<Double, Double> onLimitSell)
    {
        //tabElement = new TabElement();
        marketOrderPanel = new MarketOrderPanel(onBuyMarket, onSellMarket);
        addTab(Texts.MARKET_ORDER_TAB_TITLE.getString(), marketOrderPanel);

        limitOrderPanel = new LimitOrderPanel(onLimitBuy, onLimitSell);
        addTab(Texts.LIMIT_ORDER_TAB_TITLE.getString(), limitOrderPanel);

        //addChild(tabElement);
    }

    public void setItemName(String itemName)
    {
        marketOrderPanel.setItemName(itemName);
        limitOrderPanel.setItemName(itemName);
    }
    public void setCurrencyName(String currencyName)
    {
        limitOrderPanel.setCurrencyName(currencyName);
        marketOrderPanel.setCurrencyName(currencyName);
    }
    public void setCurrentMarketPrice(double price)
    {
        limitOrderPanel.setCurrentMarketPrice(price);
        marketOrderPanel.setCurrentMarketPrice(price);
    }
    public void setMoneyBalance(double balance)
    {
        limitOrderPanel.setMoneyBalance(balance);
        marketOrderPanel.setMoneyBalance(balance);
    }
    public void setItemBalance(double balance)
    {
        limitOrderPanel.setItemBalance(balance);
        marketOrderPanel.setItemBalance(balance);
    }
    public void setLimitQuantity(double quantity)
    {
        limitOrderPanel.setQuantity(quantity);
    }
    public void setMarketQuantity(double quantity)
    {
        marketOrderPanel.setQuantity(quantity);
    }
    public double getLimitQuantity()
    {
        return limitOrderPanel.getQuantity();
    }
    public double getMarketQuantity()
    {
        return marketOrderPanel.getQuantity();
    }
    public void setLimitPrice(double limitPrice)
    {
        limitOrderPanel.setPrice(limitPrice);
    }
    public double getLimitPrice()
    {
        return limitOrderPanel.getPrice();
    }

    public void setMarketOpen(boolean open) {
        marketOrderPanel.setMarketOpen(open);
        limitOrderPanel.setMarketOpen(open);
    }

    /**
     * T-179: limits the amount boxes of both tabs to the market's max order size.
     * @param maxQuantity limit in real items; &le; 0 = unlimited
     */
    public void setMaxQuantity(double maxQuantity) {
        marketOrderPanel.setMaxQuantity(maxQuantity);
        limitOrderPanel.setMaxQuantity(maxQuantity);
    }

    /**
     * T-179: greys out buy/sell on both tabs while the player's trade cooldown runs.
     * @param remainingMs remaining cooldown in ms; &le; 0 = ready
     */
    public void setTradeCooldown(long remainingMs) {
        marketOrderPanel.setTradeCooldown(remainingMs);
        limitOrderPanel.setTradeCooldown(remainingMs);
    }

    /**
     * T-179: shared by all trading sub-panels. While {@code remainingMs > 0} the button stays
     * visible but ignores clicks and shows the countdown, e.g. "Buy (0.4s)".
     *
     * @param button      the buy or sell button
     * @param text        the button's normal label
     * @param remainingMs remaining cooldown in ms; &le; 0 = ready
     */
    static void applyTradeCooldown(Button button, String text, long remainingMs) {
        boolean ready = remainingMs <= 0;
        button.setClickable(ready);
        String label = ready ? text : text + String.format(Locale.ROOT, " (%.1fs)", remainingMs / 1000.0);
        if (!label.equals(button.getText()))
            button.setText(label);
    }

    @Override
    protected void render() {
        super.render();
        // Detect tab switch: when the limit order tab becomes active, populate the price
        // with the current market price
        int currentTab = getSelectedTab();
        if(currentTab != lastSelectedTab) {
            lastSelectedTab = currentTab;
            if(currentTab == LIMIT_ORDER_TAB) {
                limitOrderPanel.setPrice(limitOrderPanel.getCurrentMarketPrice());
            }
        }
    }
}
