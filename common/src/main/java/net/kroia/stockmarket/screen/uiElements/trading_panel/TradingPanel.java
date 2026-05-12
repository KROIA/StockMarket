package net.kroia.stockmarket.screen.uiElements.trading_panel;

import net.kroia.modutilities.ColorUtilities;
import net.kroia.modutilities.gui.elements.*;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.screen.UI_Colors;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.minecraft.network.chat.Component;

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
