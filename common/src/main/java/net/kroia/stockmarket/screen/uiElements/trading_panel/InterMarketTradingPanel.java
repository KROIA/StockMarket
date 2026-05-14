package net.kroia.stockmarket.screen.uiElements.trading_panel;

import net.kroia.modutilities.gui.elements.TabElement;
import net.kroia.stockmarket.StockMarketMod;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Trading panel for inter-market (pair) mode. Provides two tabs:
 * - Market Exchange: sell "have" items at market rate to receive "want" items
 * - Limit Exchange: place a limit order with a max exchange rate
 *
 * The panel displays estimated yield based on the current cross-rate between
 * the two paired markets, and validates quantity against the player's "have"
 * item balance before enabling the exchange button.
 */
public class InterMarketTradingPanel extends TabElement {
    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".inter_market_trading_panel.";
        private static final Component MARKET_EXCHANGE_TAB_TITLE = Component.translatable(PREFIX + "market_exchange_tab_title");
        private static final Component LIMIT_EXCHANGE_TAB_TITLE = Component.translatable(PREFIX + "limit_exchange_tab_title");
    }

    // Tab index constants
    private static final int MARKET_EXCHANGE_TAB = 0;
    private static final int LIMIT_EXCHANGE_TAB = 1;

    private final InterMarketMarketExchangePanel marketExchangePanel;
    private final InterMarketLimitExchangePanel limitExchangePanel;

    // Track the previously selected tab to detect tab switches
    private int lastSelectedTab = -1;

    /**
     * @param onMarketExchange called with quantity of "have" items to exchange at market rate
     * @param onLimitExchange  called with (quantity of "have" items, rate limit in have-per-want)
     */
    public InterMarketTradingPanel(Consumer<Double> onMarketExchange, BiConsumer<Double, Double> onLimitExchange) {
        marketExchangePanel = new InterMarketMarketExchangePanel(onMarketExchange);
        addTab(Texts.MARKET_EXCHANGE_TAB_TITLE.getString(), marketExchangePanel);

        limitExchangePanel = new InterMarketLimitExchangePanel(onLimitExchange);
        addTab(Texts.LIMIT_EXCHANGE_TAB_TITLE.getString(), limitExchangePanel);
    }

    // --- State setters called from TradeScreen ---

    /** Sets the display name of the "have" item (item being sold). */
    public void setHaveItemName(String name) {
        marketExchangePanel.setHaveItemName(name);
        limitExchangePanel.setHaveItemName(name);
    }

    /** Sets the display name of the "want" item (item being acquired). */
    public void setWantItemName(String name) {
        marketExchangePanel.setWantItemName(name);
        limitExchangePanel.setWantItemName(name);
    }

    /** Sets the current cross-rate (have items per 1 want item). */
    public void setCurrentRate(double rate) {
        marketExchangePanel.setCurrentRate(rate);
        limitExchangePanel.setCurrentRate(rate);
    }

    /** Sets the player's balance of the "have" item for validation. */
    public void setHaveBalance(double balance) {
        marketExchangePanel.setHaveBalance(balance);
        limitExchangePanel.setHaveBalance(balance);
    }

    /** Enables or disables exchange when either market is closed. */
    public void setMarketOpen(boolean open) {
        marketExchangePanel.setMarketOpen(open);
        limitExchangePanel.setMarketOpen(open);
    }

    /** Sets the quantity in both sub-panels. */
    public void setQuantity(double quantity) {
        marketExchangePanel.setQuantity(quantity);
        limitExchangePanel.setQuantity(quantity);
    }

    /** Gets the quantity from the currently active tab. */
    public double getQuantity() {
        if (getSelectedTab() == LIMIT_EXCHANGE_TAB)
            return limitExchangePanel.getQuantity();
        return marketExchangePanel.getQuantity();
    }

    @Override
    protected void render() {
        super.render();
        // When switching to the limit tab, auto-fill the rate limit with the current rate
        int currentTab = getSelectedTab();
        if (currentTab != lastSelectedTab) {
            lastSelectedTab = currentTab;
            if (currentTab == LIMIT_EXCHANGE_TAB) {
                limitExchangePanel.setRateLimit(limitExchangePanel.getCurrentRate());
            }
        }
    }
}
