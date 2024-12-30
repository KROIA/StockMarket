package net.kroia.stockmarket.market.client;

import net.kroia.modutilities.PlayerUtilities;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestOrderCancelPacket;
import net.kroia.stockmarket.networking.packet.client_sender.request.RequestOrderPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.UpdateSubscribeMarketEventsPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncOrderPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncPricePacket;
import net.kroia.stockmarket.util.OrderbookVolume;
import net.kroia.stockmarket.util.PriceHistory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ClientTradeItem {


    private final String itemID;
    private PriceHistory priceHistory;
    private OrderbookVolume orderBookVolume;
    private int visualMinPrice = 0;
    private int visualMaxPrice = 0;

    // Orders that belong to this user
    private final Map<Long, Order> orders = new HashMap<>();


    public ClientTradeItem(String itemID)
    {
        this.itemID = itemID;
        this.priceHistory = new PriceHistory(itemID, 0);
    }

    public void handlePacket(SyncPricePacket packet)
    {
        priceHistory = packet.getPriceHistory();
        orderBookVolume = packet.getOrderBookVolume();
        visualMinPrice = packet.getMinPrice();
        visualMaxPrice = packet.getMaxPrice();
        ArrayList<Order> orders = packet.getOrders();
        this.orders.clear();
        for(Order order : orders)
        {
            this.orders.put(order.getOrderID(), order);
        }
    }
    public void handlePacket(SyncOrderPacket packet)
    {
        Order order = packet.getOrder();
        Order oldOrder = orders.get(order.getOrderID());
        //orders.put(order.getOrderID(), order);
        boolean hasChanged = true;
        if(oldOrder != null)
        {
            hasChanged &= !oldOrder.equals(order);
            if(hasChanged)
                oldOrder.copyFrom(order);
        }
        else
            orders.put(order.getOrderID(), order);

        if(hasChanged)
        {

            // Print to user console
            //StockMarketMod.printToClientConsole("Order: " + order.getOrderID() + " has been updated: "+order.toString());

            String limitText = "";
            if(order instanceof LimitOrder)
                limitText = "\n  Limit price :" + ((LimitOrder) order).getPrice();
            switch(order.getStatus())
            {
                case PENDING:
                    PlayerUtilities.printToClientConsole((order.isBuy()?"Buy":"Sell") + " order has been placed"+
                                    "\n  Amount: "+ Math.abs(order.getAmount()) +
                                    limitText);
                    break;
                case PROCESSED:
                    PlayerUtilities.printToClientConsole((order.isBuy()?"Buy":"Sell") + " order has been filled"+
                                    "\n  Amount: "+ Math.abs(order.getAmount()) +
                                    limitText +
                                    "\n  Average price: " + order.getAveragePrice());
                    removeOrder(order.getOrderID());
                    break;

                case CANCELLED:
                    PlayerUtilities.printToClientConsole((order.isBuy()?"Buy":"Sell") + " order has been cancelled"+
                                    "\n  Amount: "+ Math.abs(order.getAmount()) +
                                    limitText);
                    removeOrder(order.getOrderID());
                    break;

                case INVALID:
                    PlayerUtilities.printToClientConsole((order.isBuy()?"Buy":"Sell") + " order has been cancelled"+
                                    "\n  Amount: "+ Math.abs(order.getAmount()) +
                                    "\n  Filled amount: " + order.getFilledAmount() +
                                    limitText +
                                    "\n  Invalid reason: " + order.getInvalidReason());
                    removeOrder(order.getOrderID());
                    break;
            }
        }
    }

    public void copyFrom(ClientTradeItem other)
    {
        priceHistory = other.priceHistory;
        orderBookVolume = other.orderBookVolume;
        visualMinPrice = other.visualMinPrice;
        visualMaxPrice = other.visualMaxPrice;
        orders.clear();
        orders.putAll(other.orders);
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

    public int getVisualMinPrice()
    {
        return visualMinPrice;
    }
    public int getVisualMaxPrice()
    {
        return visualMaxPrice;
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
    public void cancelOrder(long orderID)
    {
        RequestOrderCancelPacket.generateRequest(orderID);
    }



    public OrderbookVolume getOrderBookVolume() {
        return orderBookVolume;
    }

    public void subscribe()
    {
        UpdateSubscribeMarketEventsPacket.generateRequest(itemID, true);
    }

    public void unsubscribe()
    {
        UpdateSubscribeMarketEventsPacket.generateRequest(itemID, false);
    }
}
