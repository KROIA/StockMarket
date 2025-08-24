package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.market.server.order.OrderDataRecord;
import net.kroia.stockmarket.util.StockMarketGuiElement;

public class PlayerTradesView extends StockMarketGuiElement {

    public static final class TEXTS {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".player_trades_view.";
        //public static final Component TEST_BUTTON = Component.translatable(PREFIX + "test_button");
    }


    private static class PlayerTradeView extends StockMarketGuiElement
    {
        private final Label description;

        public PlayerTradeView(OrderDataRecord record, String playerName)
        {

           description = new Label(playerName + " executed a " +
                   (record.getType() == Order.Type.LIMIT ? "LIMIT" : "MARKET") + " order to " + (record.getAmount() > 0 ? "BUY " : "SELL " ) + Math.abs(record.getAmount()));
           description.setBounds(this.getX(), this.getY(), 200, 16);
           description.setPadding(3);
           ItemView tex = new ItemView(record.tradingPair.getItem().getStack());
           tex.setBounds(description.getTextWidth(description.getText()) + 6, description.getY(), ItemView.DEFAULT_WIDTH, ItemView.DEFAULT_WIDTH);
           addChild(description);
           addChild(tex);

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

            description.setBounds(padding, padding, width - 2 * padding, height - 2 * padding);
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
        Button button = new Button("TEST");
        addChild(playersListView);
        addChild(button);

    }

    public void addPlayerTrade(OrderDataRecord record, String name){
        playersListView.addChild(new PlayerTradeView(record, name));
    }
    public void addPlayerTrade(OrderDataRecord record, String name, int index){
        playersListView.getChilds().add(index, new PlayerTradeView(record, name));
    }

    public void clear(){
        //playersListView.removeChilds();
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
