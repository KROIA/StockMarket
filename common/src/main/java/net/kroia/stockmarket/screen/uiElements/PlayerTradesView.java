package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.Label;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.util.StockMarketGuiElement;

public class PlayerTradesView extends StockMarketGuiElement {

    public static final class TEXTS {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".player_trades_view.";
        //public static final Component TEST_BUTTON = Component.translatable(PREFIX + "test_button");
    }


    private static class PlayerTradeView extends StockMarketGuiElement
    {
        private final Label nameLabel;
        public PlayerTradeView(String playerName)
        {
            // Initialize the player trade view element
            nameLabel = new Label(playerName);
            nameLabel.setHoverTooltipSupplier(() -> "This is a tooltip for " + playerName);
            nameLabel.setHoverTooltipMousePositionAlignment(Alignment.LEFT);

            addChild(nameLabel);

            // Set the height of this element to 20. The layout of the list view will not change the height,
            // only its width.
            setHeight(20);
        }

        @Override
        protected void render() {
            // Render the player trade view
        }

        @Override
        protected void layoutChanged() {
            // Handle layout changes for the player trade view
            int width = getWidth();
            int height = getHeight();
            int padding = 0;
            int spacing = StockMarketGuiElement.spacing;

            nameLabel.setBounds(padding, padding, width - 2 * padding, height - 2 * padding);
        }

    }


    private final ListView playersListView;

    public PlayerTradesView()
    {
        playersListView = new VerticalListView();
        Layout layout = new LayoutVertical();
        layout.stretchX = true; // Only change the width of the list view childs
        layout.padding = 2;
        layout.spacing = 2;
        playersListView.setLayout(layout);
        addChild(playersListView);


        // Populate the list with some example player elements
        playersListView.addChild(new PlayerTradeView("Player1"));
        playersListView.addChild(new PlayerTradeView("Player2"));
        playersListView.addChild(new PlayerTradeView("Player3"));
        playersListView.addChild(new PlayerTradeView("Player4"));
        playersListView.addChild(new PlayerTradeView("Player5"));


        // Requesting all markets that exist
        getMarketManager().requestTradingPairs((pairs)->
        {
            if(!pairs.isEmpty())
            {
                // select a market to use the getSelectedMarket() methode
                this.selectMarket(pairs.get(0));

                // Request the orders in the price range from 0 to 1000 for the selected market
                // Alternative use: getSelectedMarket().requestOrders((orders)->{}); to get all orders without a range
                getSelectedMarket().requestOrders(0,1000.f, (orders)->
                {
                    // Handle the received orders for the selected market
                    for (var order : orders) {
                        info("Received order: " + order.orderID + " volume: " + order.amount + " price: " + order.limitPrice);
                    }
                });
            }
        });
    }


    @Override
    protected void render() {

    }

    @Override
    protected void layoutChanged() {
        // Handle layout changes for the player trade view
        int width = getWidth();
        int height = getHeight();
        int padding = StockMarketGuiElement.padding;
        int spacing = StockMarketGuiElement.spacing;

        playersListView.setBounds(padding, padding, width/2, height - 2 * padding);
    }
}
