package net.kroia.stockmarket.plugin.plugins;

import net.kroia.modutilities.TimerMillis;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.ServerPlugin;
import net.kroia.stockmarket.plugin.interaction.MarketInterfaces;
import net.kroia.stockmarket.util.NormalizedRandomPriceGenerator;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Random;

public class VolatilityPlugin extends ServerPlugin {
    private static final Random random = new Random();
    private final TimerMillis randomWalkTimer = new TimerMillis(false);
    private final NormalizedRandomPriceGenerator priceGenerator;

    public VolatilityPlugin()
    {
        super();
        priceGenerator = new NormalizedRandomPriceGenerator(5);
        randomWalkTimer.start(random.nextInt(10000));
    }

    @Override
    public void init() {

    }

    @Override
    public void deInit() {

    }

    @Override
    public void update(List<MarketInterfaces> markets) {
        if(randomWalkTimer.check())
        {
            randomWalkTimer.start(100+random.nextLong(100 * 10L + 1));
            priceGenerator.getNextValue();
        }
        for(MarketInterfaces marketInterfaces : markets)
        {
            float defaultPrice = marketInterfaces.market.getDefaultPrice();
            float randomWalkValue = (float)(priceGenerator.getCurrentValue() * 10.f * defaultPrice);
            marketInterfaces.market.setTargetPrice(randomWalkValue + defaultPrice);
        }
    }

    @Override
    public void finalize(List<MarketInterfaces> markets) {

    }

    @Override
    public void onMarketSubscribed(TradingPair tradingPair) {

    }

    @Override
    public void onMarketUnsubscribed(TradingPair tradingPair) {

    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    @Override
    public boolean saveData(CompoundTag tag) {
        return false;
    }

    @Override
    public boolean loadData(CompoundTag tag) {
        return false;
    }
}
