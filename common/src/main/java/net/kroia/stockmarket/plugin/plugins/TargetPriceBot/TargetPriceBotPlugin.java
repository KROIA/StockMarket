package net.kroia.stockmarket.plugin.plugins.TargetPriceBot;

import net.kroia.stockmarket.plugin.base.IMarketPluginInterface;
import net.kroia.stockmarket.plugin.base.IPluginSettings;
import net.kroia.stockmarket.plugin.base.MarketPlugin;
import net.kroia.stockmarket.util.PID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class TargetPriceBotPlugin extends MarketPlugin {

    public static class Settings implements IPluginSettings
    {
        private final class TAGS {
            public static final String VOLUME_SCALE = "volumeScale";
        }
        public float volumeScale = 1.0f;
        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(volumeScale);
        }

        public void decode(FriendlyByteBuf buf) {
            volumeScale = buf.readFloat();
        }

        @Override
        public boolean save(CompoundTag tag) {
            tag.putFloat(TAGS.VOLUME_SCALE, volumeScale);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag.contains(TAGS.VOLUME_SCALE))
                volumeScale = tag.getFloat(TAGS.VOLUME_SCALE);
            return true;
        }
    }

    private final PID pid = new PID(0.1f, 0.1f, 0, 0.1f);
    private final Settings settings = new Settings();
    private int tickCounter = 0;

    private float targetPrice = 0;
    @Override
    public void encodeClientStreamData(FriendlyByteBuf buf) {
        buf.writeFloat(targetPrice);
    }

    @Override
    protected void setup() {
        pluginInterface.setStreamPacketSendTickInterval(5);
    }

    @Override
    protected void update() {
        tickCounter++;
        if(tickCounter < 5) //update once per second
            return;
        tickCounter = 0;
        IMarketPluginInterface pluginInterface = getPluginInterface();
        targetPrice = pluginInterface.getTargetPrice();
        float currentPrice = pluginInterface.getPrice();

        float output = pid.update(targetPrice - currentPrice);
        float normalized = (Math.min(Math.max(-10, output*5),10) * settings.volumeScale);
        float volumeToTarget = pluginInterface.getOrderBook().getVolume(currentPrice, targetPrice);
        if(normalized < 0 && volumeToTarget > 0)
            normalized = Math.max(-volumeToTarget, normalized);
        else if(normalized > 0 && volumeToTarget < 0)
            normalized = Math.min(-volumeToTarget, normalized);
        else if(normalized != 0)
            normalized = 0; //we are at target price
        long marketOrderAmount = Math.round(normalized);
        if(marketOrderAmount != 0)
        {
            pluginInterface.placeOrder(marketOrderAmount);
            info("Placing order: " + marketOrderAmount + " (output: " + output + ", normalized: " + normalized +" , volumeToTarget: " + volumeToTarget + ")");
        }
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
