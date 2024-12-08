package net.kroia.stockmarket.market.client;

import net.kroia.stockmarket.market.server.MarketManager;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.SubscribeMarketEventsPacket;
import net.kroia.stockmarket.networking.packet.TransactionRequestPacket;
import net.kroia.stockmarket.networking.packet.UpdatePricePacket;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;

public class ClientTradeItem {


    private final String itemID;
    private PriceHistory priceHistory;
    private OrderbookVolume orderBookVolume;


    public ClientTradeItem(String itemID)
    {
        this.itemID = itemID;
        this.priceHistory = new PriceHistory(itemID, 0);
    }

    public void updateFromPacket(UpdatePricePacket packet)
    {
        priceHistory = packet.getPriceHistory();
        orderBookVolume = packet.getOrderBookVolume();
    }

    public String getItemID()
    {
        return itemID;
    }

    public PriceHistory getPriceHistory()
    {
        return priceHistory;
    }

    public int getPrice()
    {
        return priceHistory.getCurrentPrice();
    }

    public boolean createOrder(int quantity, int price)
    {
        TransactionRequestPacket.generateRequest(itemID, quantity, price);
        return true;
    }
    public boolean createOrder(int quantity)
    {
        TransactionRequestPacket.generateRequest(itemID, quantity);
        return true;
    }

    public OrderbookVolume getOrderBookVolume() {
        return orderBookVolume;
    }

    public void subscribe()
    {
        SubscribeMarketEventsPacket.generateRequest(itemID, true);
    }

    public void unsubscribe()
    {
        SubscribeMarketEventsPacket.generateRequest(itemID, false);
    }
}
