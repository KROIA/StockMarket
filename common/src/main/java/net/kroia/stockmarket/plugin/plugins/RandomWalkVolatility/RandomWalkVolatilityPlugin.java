package net.kroia.stockmarket.plugin.plugins.RandomWalkVolatility;

import net.kroia.modutilities.TimerMillis;
import net.kroia.stockmarket.plugin.base.IMarketPluginInterface;
import net.kroia.stockmarket.plugin.base.IPluginSettings;
import net.kroia.stockmarket.plugin.base.MarketPlugin;
import net.kroia.stockmarket.util.NormalizedRandomPriceGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Random;

public class RandomWalkVolatilityPlugin extends MarketPlugin {

    public static class Settings implements IPluginSettings
    {
        private final class TAGS {
            public static final String VOLATILITY = "volatility";
            public static final String UPDATE_TIMER_INTERVAL_MS = "updateTimerIntervalMS";
        }
        public float volatility = 1.0f;
        public int updateTimerIntervallMS = 1000;
        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(volatility);
            buf.writeInt(updateTimerIntervallMS);
        }

        public void decode(FriendlyByteBuf buf) {
            volatility = buf.readFloat();
            updateTimerIntervallMS = buf.readInt();
        }

        @Override
        public boolean save(CompoundTag tag) {
            tag.putFloat(TAGS.VOLATILITY, volatility);
            tag.putInt(TAGS.UPDATE_TIMER_INTERVAL_MS, updateTimerIntervallMS);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag.contains(TAGS.VOLATILITY))
                volatility = tag.getFloat(TAGS.VOLATILITY);

            if(tag.contains(TAGS.UPDATE_TIMER_INTERVAL_MS))
                updateTimerIntervallMS = tag.getInt(TAGS.UPDATE_TIMER_INTERVAL_MS);
            return true;
        }
    }
    private final Settings settings = new Settings();
    private final TimerMillis randomWalkTimer = new TimerMillis(false);
    private static Random random = new Random();
    private final NormalizedRandomPriceGenerator priceGenerator;

    public RandomWalkVolatilityPlugin()
    {
        priceGenerator = new NormalizedRandomPriceGenerator(5);
        randomWalkTimer.start(random.nextInt(10000));
    }
    @Override
    public void encodeClientStreamData(FriendlyByteBuf buf) {
        //buf.writeFloat(targetPrice);
    }

    @Override
    protected void setup() {

    }

    @Override
    protected void update() {

        IMarketPluginInterface pluginInterface = getPluginInterface();
        if(randomWalkTimer.check())
        {
            randomWalkTimer.start(100+random.nextLong(settings.updateTimerIntervallMS * 10L + 1));
            priceGenerator.getNextValue();
        }
        float defaultPrice = pluginInterface.getDefaultPrice();
        float randomWalkValue = (float)(priceGenerator.getCurrentValue() * (double)settings.volatility * defaultPrice);
        pluginInterface.addToTargetPrice(randomWalkValue);
    }

    @Override
    protected void encodeSettings(FriendlyByteBuf buf) {
        settings.encode(buf);
    }

    @Override
    protected void decodeSettings(FriendlyByteBuf buf) {
        settings.decode(buf);
    }

    @Override
    protected boolean saveToFilesystem(CompoundTag tag) {
        return settings.save(tag);
    }

    @Override
    protected boolean loadFromFilesystem(CompoundTag tag) {
        return settings.load(tag);
    }
}
