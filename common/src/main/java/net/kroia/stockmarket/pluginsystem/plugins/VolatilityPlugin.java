package net.kroia.stockmarket.pluginsystem.plugins;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.TimerMillis;
import net.kroia.stockmarket.pluginsystem.plugin.ServerPlugin;
import net.kroia.stockmarket.pluginsystem.interaction.MarketInterface;
import net.kroia.stockmarket.util.NormalizedRandomPriceGenerator;
import net.minecraft.nbt.CompoundTag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Random;

public class VolatilityPlugin extends ServerPlugin {
    private static final Random random = new Random();
    private final TimerMillis randomWalkTimer = new TimerMillis(false);
    private final NormalizedRandomPriceGenerator priceGenerator;
    private float volatilityScale = 1.0f;

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
    public void update(List<MarketInterface> markets) {
        if(randomWalkTimer.check())
        {
            randomWalkTimer.start(100+random.nextLong(100 * 10L + 1));
            priceGenerator.getNextValue();
        }
        for(MarketInterface marketInterfaces : markets)
        {
            double defaultPrice = marketInterfaces.market.getDefaultRealPrice();
            float randomWalkValue = (float)(priceGenerator.getCurrentValue() * volatilityScale * defaultPrice);
            marketInterfaces.market.setTargetPrice(Math.max(0, randomWalkValue + defaultPrice));
        }
    }

    @Override
    public void finalize(List<MarketInterface> markets) {

    }

    @Override
    public void onMarketSubscribed(ItemID marketID) {

    }

    @Override
    public void onMarketUnsubscribed(ItemID marketID) {

    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }

    @Override
    public boolean save(CompoundTag tag) {
        tag.putFloat("volatilityScale", volatilityScale);
        return true;
    }

    @Override
    public boolean load(CompoundTag tag) {
        if (tag.contains("volatilityScale")) volatilityScale = tag.getFloat("volatilityScale");
        return true;
    }

    @Override
    public byte[] provideCustomSettings() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeFloat(volatilityScale);
            dos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean applyCustomSettings(byte[] payload) {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload));
            volatilityScale = dis.readFloat();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
