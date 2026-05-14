package net.kroia.stockmarket.screen;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.gui.Gui;
import net.kroia.modutilities.gui.elements.VerticalListView;
import net.kroia.modutilities.gui.elements.base.ListView;
import net.kroia.modutilities.gui.layout.LayoutVertical;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.screen.widgets.CandlestickChart;
import net.kroia.stockmarket.screen.widgets.MarketSettingsWidget;
import net.kroia.stockmarket.screen.widgets.OrderbookVolumeHistogram;
import net.kroia.stockmarket.stockmarket.market.ClientMarket;
import net.kroia.stockmarket.util.StockMarketGuiElement;
import net.kroia.stockmarket.util.StockMarketGuiScreen;
import net.minecraft.network.chat.Component;

public class MarketManagementScreen extends StockMarketGuiScreen {

    private static class Texts {
        private static final String PREFIX = "gui." + StockMarketMod.MOD_ID + ".market_management_screen.";
        private static final Component TITLE = Component.translatable(PREFIX + "title");
    }

    private final StockMarketGuiScreen parent;
    private final ItemID marketID;
    private final ClientMarket market;

    private final CandlestickChart candlestickChart;
    private final OrderbookVolumeHistogram orderbookVolumeHistogram;

    private final ListView listView;
    private final MarketSettingsWidget marketSettingsWidget;

    public MarketManagementScreen(StockMarketGuiScreen parent, ItemID marketID) {
        super(Texts.TITLE);
        this.parent = parent;
        this.marketID = marketID;
        market = getMarket(marketID);

        candlestickChart = new CandlestickChart();
        candlestickChart.setMarket(market);

        orderbookVolumeHistogram = new OrderbookVolumeHistogram(candlestickChart);

        listView = new VerticalListView();
        LayoutVertical layout = new LayoutVertical();
        layout.stretchX = true;
        layout.stretchY = false;
        listView.setLayout(layout);

        marketSettingsWidget = new MarketSettingsWidget(market);
        listView.addChild(marketSettingsWidget);

        addElement(candlestickChart);
        addElement(orderbookVolumeHistogram);
        addElement(listView);
    }

    @Override
    public void onClose() {
        super.onClose();
        if(parent != null)
            setScreen(parent);
    }

    @Override
    protected void updateLayout(Gui gui) {
        int padding = StockMarketGuiElement.padding;
        int spacing = StockMarketGuiElement.spacing;
        int width = getWidth() - 2 * padding;
        int height = getHeight() - 2 * padding;

        int orderbookVolumeWidth = width / 10;
        int chartWidth = width / 2 - orderbookVolumeWidth;

        candlestickChart.setBounds(padding, padding, chartWidth, height / 2);
        orderbookVolumeHistogram.setBounds(candlestickChart.getRight(), candlestickChart.getTop(), orderbookVolumeWidth, candlestickChart.getHeight());
        listView.setBounds(orderbookVolumeHistogram.getRight() + spacing, padding, width - (orderbookVolumeHistogram.getRight() - padding + spacing), height);
    }
}
