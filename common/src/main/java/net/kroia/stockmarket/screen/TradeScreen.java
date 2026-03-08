package net.kroia.stockmarket.screen;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.networking.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.networking.request.MarketPriceHistoryRequest;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.UUID;


public class TradeScreen extends StockMarketGuiScreen {

    private static class Texts{
        private static final String PREFIX = "gui."+ StockMarketMod.MOD_ID + ".trade_screen.";
        private static final Component TITLE = Component.translatable(PREFIX +"title");
    }

    private final StockMarketBlockEntity blockEntity;


    private final CandlestickChart  candlestickChart;
    private final PriceHistoryData priceHistoryData;
    private final UUID streamID;


    public TradeScreen(StockMarketBlockEntity blockEntity)
    {
        super(Texts.TITLE);
        this.blockEntity = blockEntity;

        priceHistoryData = new PriceHistoryData(ItemID.of(Items.GOLD_INGOT.getDefaultInstance()));
        candlestickChart =  new CandlestickChart();
        candlestickChart.setData(priceHistoryData);

        addElement(candlestickChart);

        ItemID itemID = ItemID.of(Items.GOLD_INGOT.getDefaultInstance());

        MarketPriceHistoryRequest.InputData priceChunkRequestData = new MarketPriceHistoryRequest.InputData(itemID, 0, Long.MAX_VALUE);
        StreamSystem.startServerToClientStream(BACKEND_INSTANCES.NETWORKING.MARKET_PRICE_HISTORY_REQUEST, priceChunkRequestData, (historyData) ->
        {
            BACKEND_INSTANCES.LOGGER.info("Price chunck received");
            priceHistoryData.insert(historyData);
        }, () ->
        {
            // Stream stopped
            BACKEND_INSTANCES.LOGGER.info("MARKET_PRICE_HISTORY_REQUEST stopped");
        });

        streamID = StreamSystem.startServerToClientStream(BACKEND_INSTANCES.NETWORKING.MARKET_PRICE_STREAM, itemID, (price)->
        {
            BACKEND_INSTANCES.LOGGER.info("Price received: " + price);
            priceHistoryData.setCurrentMarketPrice(price);
        },()->
        {
            // Stream stopped
            BACKEND_INSTANCES.LOGGER.info("MARKET_PRICE_STREAM stopped");
        });
    }


    public static void openScreen(StockMarketBlockEntity blockEntity)
    {
        TradeScreen screen = new TradeScreen(blockEntity);
        Minecraft.getInstance().setScreen(screen);
    }
    @Override
    public void onClose()
    {
        StreamSystem.stopStream(streamID);
        super.onClose();
    }


    @Override
    protected void updateLayout(Gui gui) {
        int width = getWidth();
        int height = getHeight();

        candlestickChart.setBounds(0, 0, width, height);
    }
}
