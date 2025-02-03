package net.kroia.stockmarket.market.server.bot;

import net.kroia.banksystem.banking.bank.Bank;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.market.server.MatchingEngine;
import net.kroia.stockmarket.market.server.ServerMarket;
import net.kroia.stockmarket.util.MeanRevertingRandomWalk;
import net.minecraft.nbt.CompoundTag;

public class ServerMarketMakerBot extends ServerTradingBot {
    public static class Settings extends ServerTradingBot.Settings
    {
        public Settings()
        {
            super();
            this.updateTimerIntervallMS = 1000;
        }
        @Override
        public boolean save(CompoundTag tag) {
            boolean success = super.save(tag);
            return success;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag == null)
                return false;
            boolean success = super.load(tag);
            return success;
        }
    }

    enum Action
    {
        IDLE,
        BOOK_SWIPE_UP,
        BOOK_SWIPE_DOWN,
        BULLISH,
        BEARISH,

        __COUNT
    }
    private MeanRevertingRandomWalk randomWalk;

    Action currentAction = Action.IDLE;

    long lastTimeMS = 0;
    long nextActionMillis = 2000;

    public ServerMarketMakerBot() {
        super(new Settings());
        randomWalk = new MeanRevertingRandomWalk(0.1, 0.05);
    }

    @Override
    public void update()
    {
        long currentTime = System.currentTimeMillis();
        if(currentTime - lastTimeMS > nextActionMillis) {
            lastTimeMS = currentTime;
            nextActionMillis = (long)(Math.random()*5000);

            currentAction = Action.values()[(int)(Math.random()*Action.__COUNT.ordinal())];
            if(getMatchingEngine().getPrice() <= 20)
            {
                if(currentAction == Action.BEARISH)
                    currentAction = Action.BULLISH;
                else if(currentAction == Action.BOOK_SWIPE_DOWN)
                    currentAction = Action.BOOK_SWIPE_UP;
            }

        }
        createOrders();
    }


    @Override
    public void createOrders() {
       switch (currentAction)
        {
            case IDLE:
                break;
            case BOOK_SWIPE_UP:
                action_book_swipe_up();
                break;
            case BOOK_SWIPE_DOWN:
                action_book_swipe_down();
                break;
            case BULLISH:
                action_bullish();
                break;
            case BEARISH:
                action_bearish();
                break;
        }
    }

    private void action_book_swipe_up()
    {
        MatchingEngine engine = getMatchingEngine();
        int currentPrice = engine.getPrice();
        if(currentPrice == 0)
            return;
        int upperPrice = currentPrice + 50;

        // Buy orders
        int orderVolume = -engine.getVolume(currentPrice, upperPrice)/4;
        if(orderVolume == 0)
            return;
        Bank moneyBank = ServerMarket.getBotUser().getMoneyBank();
        int maxVolume = (int)(moneyBank.getBalance()/upperPrice);

        orderVolume = Math.min(orderVolume, maxVolume);

        if(marketTrade(orderVolume))
            print("Book swipe up: "+orderVolume+" @ "+upperPrice);
        currentAction = Action.IDLE;
    }
    private void action_book_swipe_down()
    {
        MatchingEngine engine = getMatchingEngine();
        int currentPrice = engine.getPrice();
        if(currentPrice == 0)
            return;
        int lowerPrice = currentPrice - 50;
        if(lowerPrice < 0)
            lowerPrice = 0;

        // Sell orders
        int orderVolume = engine.getVolume(lowerPrice, currentPrice)/4;
        if(orderVolume == 0)
            return;
        Bank itemBank = ServerMarket.getBotUser().getBank(getItemID());

        int maxVolume = (int)(itemBank.getBalance());

        orderVolume = Math.min(orderVolume, maxVolume);

        if(marketTrade(-orderVolume))
            print("Book swipe down: "+orderVolume+" @ "+lowerPrice);
        currentAction = Action.IDLE;
    }
    private void action_bullish()
    {
        int orderVolume = (int)(randomWalk.nextValue()+1)*3;
        if(orderVolume == 0)
            return;

        if(marketTrade(orderVolume))
            print("Bullish: "+orderVolume);
    }
    private void action_bearish()
    {
        int orderVolume = (int)(randomWalk.nextValue()+1)*3;
        if(orderVolume == 0)
            return;
        MatchingEngine engine = getMatchingEngine();
        Bank moneyBank = ServerMarket.getBotUser().getMoneyBank();
        Bank itemBank = ServerMarket.getBotUser().getBank(getItemID());

        if(marketTrade(-orderVolume))
            print("Bearish: "+orderVolume);
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
            throw new IllegalArgumentException("Settings must be of type ServerMarketMakerBot.Settings");
    }
    @Override
    public boolean save(CompoundTag tag) {
        boolean success = super.save(tag);

        return success;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if(tag == null)
            return false;
        boolean success = super.load(tag);
        return success;
    }

    private static void print(String s)
    {
        StockMarketMod.LOGGER.info("[ServerMarketMakerBot] "+s);
    }


}
