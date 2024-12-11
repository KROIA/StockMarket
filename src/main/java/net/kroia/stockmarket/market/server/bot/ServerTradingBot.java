package net.kroia.stockmarket.market.server.bot;

import net.kroia.stockmarket.banking.BankUser;
import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.BotMoneyBank;
import net.kroia.stockmarket.market.server.MarketManager;
import net.kroia.stockmarket.market.server.MatchingEngine;
import net.kroia.stockmarket.market.server.order.LimitOrder;
import net.kroia.stockmarket.market.server.order.Order;
import net.kroia.stockmarket.util.ServerSaveable;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.UUID;

/**
 * The ServerTradingBot simulates buy and sell orders to provide liquidity to the market.
 *
 *
 */
public class ServerTradingBot implements ServerSaveable {

    protected static long lastBotID = 0;
    protected MarketManager parent;
    protected MatchingEngine matchingEngine;

    protected final int maxOrderCount = 100;

    protected ArrayList<LimitOrder> buyOrders = new ArrayList<>();
    protected ArrayList<LimitOrder> sellOrders = new ArrayList<>();

    public ServerTradingBot(MarketManager parent, MatchingEngine matchingEngine) {
        this.parent = parent;
        this.matchingEngine = matchingEngine;
    }

    public void update()
    {
        clearOrders();
        createOrders();
    }

    public UUID getUUID()
    {
        return ServerBankManager.getBotUser().getOwnerUUID();
    }
    public String getItemID()
    {
        return parent.getItemID();
    }


    protected void clearOrders()
    {
        matchingEngine.removeBuyOrder_internal(buyOrders);
        matchingEngine.removeSellOrder_internal(sellOrders);

        for(LimitOrder order : buyOrders)
        {
            order.markAsCancelled();
        }
        for(LimitOrder order : sellOrders)
        {
            order.markAsCancelled();
        }
        buyOrders.clear();
        sellOrders.clear();
    }

    protected void createOrders()
    {
        BankUser user = ServerBankManager.getBotUser();
        Bank moneyBank = user.getMoneyBank();
        String itemID = parent.getItemID();
        Bank itemBank = user.getBank(itemID);
        String botUUID = getUUID().toString();

        int priceIncerement = 1;
        int currentPrice = matchingEngine.getPrice();
        for(int i=1; i<=maxOrderCount/2; i++)
        {
            int sellPrice = currentPrice + i*priceIncerement;
            int buyPrice = currentPrice - i*priceIncerement;


            int buyVolume = getAvailableVolume(buyPrice);
            if(buyVolume > 0) {

                LimitOrder buyOrder = LimitOrder.createBotOrder(botUUID, moneyBank, itemBank, itemID, buyVolume, buyPrice);
                if(buyOrder != null) {
                    matchingEngine.addOrder(buyOrder);
                    buyOrders.add(buyOrder);
                }
            }

            int sellVolume = getAvailableVolume(sellPrice);
            if(sellVolume < 0) {
                LimitOrder sellOrder = LimitOrder.createBotOrder(botUUID, moneyBank, itemBank,  itemID, sellVolume, sellPrice);
                if(sellOrder != null)
                {
                    matchingEngine.addOrder(sellOrder);
                    sellOrders.add(sellOrder);
                }
            }




        }
    }

    protected int getAvailableVolume(int price)
    {
        if(price < 0)
            return 0;
        int currentPrice = matchingEngine.getPrice();
        return getVolumeDistribution(currentPrice - price);
    }

    /**
     * Creates a disribution that can be mapped to buy and sell orders
     * The distribution is normalized around x=0.
     *   x < 0: buy order volume
     *   x > 0: sell order volume
     */
    protected int getVolumeDistribution(int x)
    {
        double fX = (double)Math.abs(x);
        double scale = 100;
        double exp = Math.exp(-fX*0.05);
        double random = Math.random()+1;

        double volume = (scale*random) * (1 - exp) * exp;

        if(x < 0)
            return (int)-volume;
        return (int)volume;

        //return -x*;
    }

    @Override
    public void save(CompoundTag tag) {

    }

    @Override
    public void load(CompoundTag tag) {
        ArrayList<Order> orders = new ArrayList<>();
        matchingEngine.getOrders(getUUID().toString(), orders);
        for(Order order : orders)
        {
            if(order instanceof LimitOrder)
            {
                LimitOrder limitOrder = (LimitOrder) order;
                if(limitOrder.isBuy())
                {
                    buyOrders.add(limitOrder);
                }
                else
                {
                    sellOrders.add(limitOrder);
                }
            }
        }
    }
}
