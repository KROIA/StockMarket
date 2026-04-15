package net.kroia.stockmarket.screen;

import com.mojang.brigadier.Command;
import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.Button;
import net.kroia.modutilities.gui.elements.TextBox;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * This class is used for development for faster testing of different features
 */
public class DevTestScreen extends StockMarketGuiScreen {

    private static class Texts{
        private static final String PREFIX = "gui."+ StockMarketMod.MOD_ID + ".dev_test_screen.";
        private static final Component TITLE = Component.translatable(PREFIX +"title");
    }

    private static class CommandField extends StockMarketGuiElement
    {
        private final TextBox textBox;
        private final Button executeButton;
        private final int buttonWidth;

        public CommandField(String buttonText, Consumer<String> callback)
        {
            this(buttonText, callback, false);
        }
        public CommandField(String buttonText, Consumer<String> callback, boolean allowLetters)
        {
            this.textBox = new TextBox();
            this.buttonWidth = getTextWidth(buttonText);
            this.executeButton = new Button(buttonText, ()->{
                callback.accept(textBox.getText());
            });
            if(!allowLetters)
            {
                textBox.setAllowLetters(false);
            }
            addChild(textBox);
            addChild(executeButton);
        }

        @Override
        protected void render() {

        }

        @Override
        protected void layoutChanged() {
            int height = getHeight();
            int width = getWidth();
            textBox.setBounds(0,0, width -  buttonWidth, height);
            executeButton.setBounds(width - buttonWidth, 0, buttonWidth, height);
        }
    }

    private final CandlestickChart candlestickChart;

    private final VerticalListView listView;
    private final Button requestMarketsButton;
    private final CommandField placeLimitOrder;
    private final CommandField placeMarketOrder;


    private ItemID currentMarketID;
    private @Nullable ClientMarket market;
    private int selfBankAccountNr;

    public DevTestScreen() {
        super(Texts.TITLE);

        listView =  new VerticalListView();
        LayoutVertical layoutVertical = new LayoutVertical();
        layoutVertical.stretchX = true;
        layoutVertical.stretchY = false;
        listView.setLayout(layoutVertical);


        requestMarketsButton = new Button("Request Markets", ()->
        {
            getMarketManager().requestMarkets();
        });
        requestMarketsButton.setHeight(20);
        listView.addChild(requestMarketsButton);

        placeLimitOrder = new CommandField("Place limit order", (txt)->
        {
            if(market != null) {
                String[] strs = txt.split(",");
                if(strs.length == 2) {
                    double amount = Double.parseDouble(strs[0]);
                    double price = Double.parseDouble(strs[1]);
                    createLimitOrder(amount, price);
                }
                else
                {
                    info("Invalid input: '"+txt+"'. Input something like: '1.5,5' to buy 1.5 at a price of 5");
                }
            }
        },true);
        placeLimitOrder.setHeight(requestMarketsButton.getHeight());
        listView.addChild(placeLimitOrder);
        placeMarketOrder = new CommandField("Place market order", (txt)->
        {
            if(market != null) {
                int amount = Integer.parseInt(txt);
                createMarketOrder(amount);
            }
        });
        placeMarketOrder.setHeight(requestMarketsButton.getHeight());
        listView.addChild(placeMarketOrder);



        candlestickChart = new CandlestickChart();
        candlestickChart.setData(null);


        addElement(candlestickChart);
        addElement(listView);


        getBankManager().getPersonalBankAccountDataAsync(getThisPlayerUUID()).thenAccept(bankAccountData -> {
            if(bankAccountData != null) {
                selfBankAccountNr = bankAccountData.accountNumber;
                List<ItemID> markets = getAvailableMarkets();
                if(markets.isEmpty())
                {
                    info("No markets available");
                    return;
                }
                currentMarketID =  markets.getFirst();
                market = getMarket(currentMarketID);
                if(market != null) {
                    market.subscribeToMarketPriceUpdate();
                    candlestickChart.setData(market.getPriceHistoryData());
                }
            }
        });



    }

    @Override
    protected void updateLayout(Gui gui) {
        int width = getWidth();
        int height = getHeight();

        candlestickChart.setBounds(0, 0, width/2, height/2);

        listView.setBounds(candlestickChart.getRight(),0, width-candlestickChart.getRight(),height);
    }

    private void createLimitOrder(double amount, double price)
    {
        market.createLimitOrder(selfBankAccountNr, amount, price).thenAccept(result->{
           info("Order creation response: "+result.status);
        });
    }
    private void createMarketOrder(int amount)
    {
        market.createMarketOrder(selfBankAccountNr, amount).thenAccept(result->{
            info("Order creation response: "+result.status);
        });
    }
}
