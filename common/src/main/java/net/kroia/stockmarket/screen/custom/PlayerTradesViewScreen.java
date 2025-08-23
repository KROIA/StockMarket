package net.kroia.stockmarket.screen.custom;

import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.networking.arrs.AsynchronousRequestResponseSystem;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.order.OrderDataRecord;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.networking.packet.request.FetchOrderHistoryRequest;
import net.kroia.stockmarket.screen.uiElements.PlayerTradesView;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

public class PlayerTradesViewScreen extends StockMarketGuiScreen {

    public static final class TEXTS {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".player_trades_view_screen.";
        public static final Component TITLE = Component.translatable(PREFIX + "title");
    }
    private final List<OrderDataRecord> records = new ArrayList<>();
    private final Map<UUID, String> nameMap = new HashMap<>();
    private TradingPair currentView;
    private boolean lookingAtPlayerTrades;
    private final Screen parent;
    private final PlayerTradesView playerTradesView;
    public PlayerTradesViewScreen(Screen parent)
    {
        super(TEXTS.TITLE);
        this.parent = parent;

        playerTradesView = new PlayerTradesView();
        fetchNewData();

        addElement(playerTradesView);
    }

    @Override
    public void onClose()
    {
        // Gets called when the player presses "ESC" to close the screen
        super.onClose();
        if (parent != null) {
            this.minecraft.setScreen(parent);
        }
    }


    @Override
    protected void updateLayout(Gui gui) {
        // Gets called when the window gets resized

        // Fill the entire screen with the player trades view
        playerTradesView.setBounds(0,0,getWidth(),getHeight());
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
            playerTradesView.addPlayerTrade(trade, output.nameMap.get(trade.player));
        }
    }
}
