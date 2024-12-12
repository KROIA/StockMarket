package net.kroia.stockmarket.market.server.bot;

import net.kroia.stockmarket.banking.ServerBankManager;
import net.kroia.stockmarket.banking.bank.Bank;
import net.kroia.stockmarket.banking.bank.BotMoneyBank;
import net.kroia.stockmarket.market.server.MarketManager;
import net.kroia.stockmarket.market.server.MatchingEngine;
import net.kroia.stockmarket.market.server.order.MarketOrder;
import net.kroia.stockmarket.util.MeanRevertingRandomWalk;
import net.minecraft.nbt.CompoundTag;

public class ServerVolatilityBot extends ServerTradingBot {
    public static class Settings extends ServerTradingBot.Settings
    {
        public double volatility = 5;
        public Settings()
        {
            super();
            this.updateTimerIntervallMS = 100;
        }
        @Override
        public boolean save(CompoundTag tag) {
            boolean success = super.save(tag);
            tag.putDouble("volatility", volatility);
            return success;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag == null)
                return false;
            boolean success = super.load(tag);
            if(!tag.contains("volatility"))
                return false;
            volatility = tag.getDouble("volatility");
            return success;
        }
    }
    private MeanRevertingRandomWalk randomWalk;

    public ServerVolatilityBot() {
        super(new Settings());
        randomWalk = new MeanRevertingRandomWalk(0.1, 0.05);
    }

    @Override
    public void update()
    {
        createOrders();
    }


    @Override
    public void createOrders() {

        int orderVolume = (int)((randomWalk.nextValue())*((Settings)this.settings).volatility);
        if(orderVolume == 0)
            return;
        Bank moneyBank = ServerBankManager.getBotUser().getMoneyBank();
        Bank itemBank = ServerBankManager.getBotUser().getBank(getItemID());


        MarketOrder order = MarketOrder.createBotOrder(getUUID(),moneyBank,itemBank, getItemID(), orderVolume);
        getMatchingEngine().addOrder(order);
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
        double exp = Math.exp(-fX*1.f/this.settings.volumeSpread);
        double random = Math.random()+1;

        double volume = (super.settings.volumeScale*random) * (1 - exp) * exp;

        if(x < 0)
            return (int)-volume;
        return (int)volume;

        //return -x*;
    }


    @Override
    public void setSettings(ServerTradingBot.Settings settings) {
        if(settings instanceof Settings) {
            super.setSettings(settings);
        }
        else
            throw new IllegalArgumentException("Settings must be of type ServerVolatilityBot.Settings");
    }
    @Override
    public boolean save(CompoundTag tag) {
        boolean success = super.save(tag);
        tag.putDouble("volatility", ((Settings)this.settings).volatility);
        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        boolean success = super.load(tag);
        if(!tag.contains("volatility"))
            return false;
        ((Settings)this.settings).volatility = tag.getInt("volatility");
        return success;
    }

}
