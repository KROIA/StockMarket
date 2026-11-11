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

    private final ItemID marketID;
    private final ClientMarket market;

    private final CandlestickChart candlestickChart;
    private final OrderbookVolumeHistogram orderbookVolumeHistogram;

    private final ListView listView;
    private final MarketSettingsWidget marketSettingsWidget;

    public MarketManagementScreen(StockMarketGuiScreen parent, ItemID marketID) {
        // Parent goes to the GuiScreen base so its onClose() returns to it and restores
        // the cursor. Closing with a null parent and then calling setScreen(parent)
        // re-grabs/releases the mouse, which re-centers the cursor.
        super(Texts.TITLE, parent);
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
    protected void updateLayout(Gui gui) {
        int padding = StockMarketGuiElement.padding;
        int spacing = StockMarketGuiElement.spacing;
        int width = getWidth() - 2 * padding;
        int height = getHeight() - 2 * padding;

        // Right section (settings listView) is 3/8 of the width (1.5x the former w/4) so the
        // grown MarketSettingsWidget rows (trading hours, apply-all, status, server time)
        // have room. Chart + histogram take the rest and span the full height.
        // The listView is a VerticalListView, so the settings column scrolls on its own
        // whenever the widget is taller than the screen (e.g. GUI scale 3-4).
        int rightWidth = width * 3 / 8;
        int orderbookVolumeWidth = width / 10;
        int chartWidth = width - rightWidth - orderbookVolumeWidth - spacing;

        candlestickChart.setBounds(padding, padding, chartWidth, height);
        orderbookVolumeHistogram.setBounds(candlestickChart.getRight(), candlestickChart.getTop(), orderbookVolumeWidth, candlestickChart.getHeight());
        listView.setBounds(orderbookVolumeHistogram.getRight() + spacing, padding, rightWidth, height);
    }
}
