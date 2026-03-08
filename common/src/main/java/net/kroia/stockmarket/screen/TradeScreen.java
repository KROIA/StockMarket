package net.kroia.stockmarket.screen;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.market.client.ClientMarket;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class TradeScreen extends StockMarketGuiScreen {

    private static class Texts{
        private static final String PREFIX = "gui."+ StockMarketMod.MOD_ID + ".trade_screen.";
        private static final Component TITLE = Component.translatable(PREFIX +"title");
    }

    private final StockMarketBlockEntity blockEntity;


    private final CandlestickChart  candlestickChart;
    private @Nullable ItemID currentMarketID = null;

    public TradeScreen(StockMarketBlockEntity blockEntity)
    {
        super(Texts.TITLE);
        this.blockEntity = blockEntity;

        candlestickChart =  new CandlestickChart();
        candlestickChart.setData(null);

        addElement(candlestickChart);

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
        }
    }


    public static void openScreen(StockMarketBlockEntity blockEntity)
    {
        TradeScreen screen = new TradeScreen(blockEntity);
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
        int width = getWidth();
        int height = getHeight();

        candlestickChart.setBounds(0, 0, width, height);
    }
}
