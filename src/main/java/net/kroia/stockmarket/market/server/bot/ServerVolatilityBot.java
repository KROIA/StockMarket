package net.kroia.stockmarket.market.server.bot;

import net.kroia.stockmarket.market.server.MatchingEngine;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.util.MeanRevertingRandomWalk;
import net.minecraft.nbt.CompoundTag;

public class ServerVolatilityBot extends ServerTradingBot {
    private int volatility;
    private MeanRevertingRandomWalk randomWalk;

    public ServerVolatilityBot(MatchingEngine matchingEngine, String itemID, int volatility) {
        super(matchingEngine, itemID);
        this.volatility = volatility;
        randomWalk = new MeanRevertingRandomWalk(0.1, 0.05);
    }

    @Override
    public void update()
    {
        createOrders();
    }


    @Override
    public void createOrders() {

        int orderVolume = (int)(randomWalk.nextValue()*volatility);
        if(orderVolume == 0)
            return;

        MarketOrder order = new MarketOrder(this.botUUID, this.itemID, orderVolume, true);
        matchingEngine.addOrder(order);
    }

    /**
     * Creates a disribution that can be mapped to buy and sell orders
     * The distribution is normalized around x=0.
     *   x < 0: buy order volume
     *   x > 0: sell order volume
     */
    @Override
    public int getVolumeDistribution(int x)
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
        super.save(tag);
        tag.putInt("volatility", volatility);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        volatility = tag.getInt("volatility");
    }

}
