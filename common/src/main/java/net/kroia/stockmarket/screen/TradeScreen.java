package net.kroia.stockmarket.screen;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ClientPlayerUtilities;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Frame;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.screen.uiElements.trading_panel.TradingPanel;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class TradeScreen extends StockMarketGuiScreen {

    private static class Texts{
        private static final String PREFIX = "gui."+ StockMarketMod.MOD_ID + ".trade_screen.";
        private static final Component TITLE = Component.translatable(PREFIX +"title");
    }



    private final CandlestickChart  candlestickChart;
    private final Frame placeholder1;
    private final Frame placeholder2;
    private final TradingPanel tradingPanel;
    private @Nullable ItemID currentMarketID = null;
    private int selectedBankAccountNr = -1;

    public TradeScreen()
    {
        super(Texts.TITLE);

        candlestickChart = new CandlestickChart();
        candlestickChart.setData(null);

        tradingPanel = new TradingPanel(this::onBuyMarket, this::onSellMarket, this::onBuyLimit,  this::onSellLimit);

        placeholder1 = new Frame();
        placeholder2 = new Frame();

        addElement(candlestickChart);
        addElement(placeholder1);
        addElement(placeholder2);
        addElement(tradingPanel);

        List<ItemID> markets = getAvailableMarkets();
        if(markets.isEmpty())
        {
            info("No markets available");
            return;
        }
        currentMarketID =  markets.getFirst();
        ClientMarket market = getMarket(currentMarketID);
        if(market != null) {
            market.subscribeToMarketPriceUpdate();
            candlestickChart.setData(market.getPriceHistoryData());
            tradingPanel.setItemName(ClientPlayerUtilities.getItemDisplayText(currentMarketID.getStack()));
            tradingPanel.setLimitPrice(market.getPriceHistoryData().getCurrentMarketRealPrice());
        }
        getMarketManager().getTradingCurrencyIDAsync().thenAccept(currencyID -> {
            if(currentMarketID == null)
                tradingPanel.setCurrencyName("?");
            else
                tradingPanel.setCurrencyName(ClientPlayerUtilities.getItemDisplayText(currencyID.getStack()));
        });

        getBankManager().getPersonalBankAccountDataAsync(getThisPlayerUUID()).thenAccept(bankAccountData -> {
            if(bankAccountData != null) {
                selectedBankAccountNr = bankAccountData.accountNumber;
            }
        });
    }


    public static void openScreen()
    {
        TradeScreen screen = new TradeScreen();
        Minecraft.getInstance().setScreen(screen);
    }
    @Override
    public void onClose()
    {
        if(currentMarketID != null)
        {
            ClientMarket market = getMarket(currentMarketID);
            if(market != null)
            {
                market.unsubscribeFromMarketPriceUpdate();
            }

            candlestickChart.setData(null);
            currentMarketID = null;
        }
        super.onClose();
    }


    @Override
    protected void updateLayout(Gui gui) {
        int padding = StockMarketGuiElement.padding;
        int spacing =  StockMarketGuiElement.spacing;
        int width = getWidth() - padding*2;
        int height = getHeight() - padding*2;

        candlestickChart.setBounds(padding, padding, (width*3)/4, (height*2)/3);
        placeholder1.setBounds(candlestickChart.getRight()+spacing, padding, width - (candlestickChart.getRight()), (height-spacing)/2);
        tradingPanel.setBounds(candlestickChart.getRight()+spacing, placeholder1.getBottom()+spacing, placeholder1.getWidth(), height - (placeholder1.getBottom()));
        placeholder2.setBounds(padding, candlestickChart.getBottom()+spacing, tradingPanel.getLeft()-spacing-padding, height- (candlestickChart.getBottom()));
    }

    private void onBuyMarket(double quantity)
    {
        ClientMarket market = getMarket(currentMarketID);
        market.createMarketOrder(selectedBankAccountNr, quantity).thenAccept(result->{
            info("Order creation response: "+result.status);
        });
    }
    private void onSellMarket(double quantity)
    {
        ClientMarket market = getMarket(currentMarketID);
        market.createMarketOrder(selectedBankAccountNr, -quantity).thenAccept(result->{
            info("Order creation response: "+result.status);
        });
    }
    private void onBuyLimit(double quantity, double price)
    {
        ClientMarket market = getMarket(currentMarketID);
        market.createLimitOrder(selectedBankAccountNr, quantity, price).thenAccept(result->{
            info("Order creation response: "+result.status);
        });
    }
    private void onSellLimit(double quantity, double price)
    {
        ClientMarket market = getMarket(currentMarketID);
        market.createLimitOrder(selectedBankAccountNr, -quantity, price).thenAccept(result->{
            info("Order creation response: "+result.status);
        });
    }

}
