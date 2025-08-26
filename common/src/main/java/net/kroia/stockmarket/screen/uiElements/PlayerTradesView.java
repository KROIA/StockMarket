package net.kroia.stockmarket.screen.uiElements;

import net.kroia.modutilities.gui.elements.*;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.Layout;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.modutilities.networking.arrs.AsynchronousRequestResponseSystem;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.market.server.order.OrderDataRecord;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.request.FetchOrderHistoryRequest;
import net.kroia.stockmarket.screen.custom.PlayerTradesViewScreen;
import net.kroia.stockmarket.util.StockMarketGuiElement;

import java.util.*;

import static net.kroia.stockmarket.screen.custom.TradeScreen.CHANGE_MARKET_BUTTON;

public class PlayerTradesView extends StockMarketGuiElement {

    public static final class TEXTS {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".player_trades_view.";
        //public static final Component TEST_BUTTON = Component.translatable(PREFIX + "test_button");
    }


    private final List<OrderDataRecord> records = new ArrayList<>();
    private final Map<UUID, String> nameMap = new HashMap<>();
    private TradingPair currentView;
    private boolean lookingAtPlayerTrades;
    private final Button selectMarketButton;



    // Gui elements
    private final Button swapViewButton;


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

    public PlayerTradesView(PlayerTradesViewScreen screen)
    {
        playersListView = new VerticalListView();
        Layout layout = new LayoutVertical();
        layout.stretchX = true; // Only change the width of the list view childs
        layout.padding = 2;
        layout.spacing = 2;
        playersListView.setLayout(layout);
        addChild(playersListView);


        swapViewButton = new Button("Player Order History");
        swapViewButton.setOnFallingEdge(this::swapTradeView);
        swapViewButton.setSize(100, 20);
        addChild(swapViewButton);

        selectMarketButton = new Button(CHANGE_MARKET_BUTTON.getString());
        selectMarketButton.setOnFallingEdge(screen::onSelectItemButtonPressed);
        selectMarketButton.setSize(100, 20);
        addChild(selectMarketButton);

        fetchNewData();
    }

    public void addPlayerTrade(OrderDataRecord record, String name){
        playersListView.addChild(new PlayerTradeView(record, name));
    }
    public void addPlayerTrade(OrderDataRecord record, String name, int index){
        playersListView.getChilds().add(index, new PlayerTradeView(record, name));
    }

    public void clear(){
        playersListView.removeChilds();
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
        swapViewButton.setBounds(playersListView.getRight(), padding, 150, 20);
        selectMarketButton.setBounds(playersListView.getRight(), swapViewButton.getBottom() + padding, 150, 20);
    }



    private void swapTradeView(){
        this.clear();
        lookingAtPlayerTrades = !lookingAtPlayerTrades;
        currentView = null;
        records.clear();
        nameMap.clear();
        fetchNewData();
        swapViewButton.setLabel(lookingAtPlayerTrades ? "Swap to Global Order History" : "Swap to Player Order History");

    }

    public void fetchNewData(){
        FetchOrderHistoryRequest.Input input = new FetchOrderHistoryRequest.Input(records.size(), currentView, lookingAtPlayerTrades);
        AsynchronousRequestResponseSystem.sendRequestToServer(StockMarketNetworking.ORDER_HISTORY_REQUEST, input, (output) -> {
            records.addAll(output.records);
            nameMap.putAll(output.nameMap);
            addNewTrades(output);

        });
    }

    protected void addNewTrades(FetchOrderHistoryRequest.Output output){
        for(OrderDataRecord trade : output.records){
            addPlayerTrade(trade, output.nameMap.get(trade.getPlayer()));
        }
    }

    public void setCurrentView(TradingPair pair){
        currentView = pair;
        clear();
        fetchNewData();
    }



}
