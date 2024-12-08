package net.kroia.stockmarket.market.client;

import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.ResponseOrderPacket;
import net.kroia.stockmarket.networking.packet.SubscribeMarketEventsPacket;
import net.kroia.stockmarket.networking.packet.RequestOrderPacket;
import net.kroia.stockmarket.networking.packet.UpdatePricePacket;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ClientTradeItem {


    private final String itemID;
    private PriceHistory priceHistory;
    private OrderbookVolume orderBookVolume;

    private final Map<Long, Order> orders = new HashMap<>();


    public ClientTradeItem(String itemID)
    {
        this.itemID = itemID;
        this.priceHistory = new PriceHistory(itemID, 0);
    }

    public void handlePacket(UpdatePricePacket packet)
    {
        priceHistory = packet.getPriceHistory();
        orderBookVolume = packet.getOrderBookVolume();
    }
    public void handlePacket(ResponseOrderPacket packet)
    {
        Order order = packet.getOrder();
        Order oldOrder = orders.get(order.getOrderID());
        orders.put(order.getOrderID(), order);
        boolean hasChanged = true;
        if(oldOrder != null)
        {
            hasChanged &= !oldOrder.equals(order);
        }
        if(hasChanged)
        {
            // Print to user console
            //StockMarketMod.printToClientConsole("Order: " + order.getOrderID() + " has been updated: "+order.toString());
            switch(order.getStatus())
            {
                case PENDING:
                    StockMarketMod.printToClientConsole("Order: " + order.getOrderID() + " is open\n"+
                            "  "+order.getFilledAmount() + " of " + order.getAmount() + " filled");
                    break;
                case PROCESSED:
                    StockMarketMod.printToClientConsole("Order: " + order.getOrderID() + " has been filled\n"+
                            "  Average price: " + order.getAveragePrice());
                    break;
                case INVALID:
                    StockMarketMod.printToClientConsole("Order: " + order.getOrderID() + " is invalid and has been cancelled");
                    break;
            }
        }
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
        RequestOrderPacket.generateRequest(itemID, quantity, price);
        return true;
    }
    public boolean createOrder(int quantity)
    {
        RequestOrderPacket.generateRequest(itemID, quantity);
        return true;
    }

    public Order getOrder(long orderID)
    {
        return orders.get(orderID);
    }

    public ArrayList<Order> getOrders()
    {
        return new ArrayList<>(orders.values());
    }
    public void removeOrder(long orderID)
    {
        orders.remove(orderID);
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
