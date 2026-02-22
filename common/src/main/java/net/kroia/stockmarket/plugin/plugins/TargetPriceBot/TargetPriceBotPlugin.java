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
        private static final class TAGS {
            public static final String VOLUME_SCALE = "volumeScale";
            public static final String KP = "kp";
            public static final String KI = "ki";
            public static final String IBOUND = "iBound";
            public static final String KD = "kd";

        }
        public float volumeScale = 1.0f;
        public float kp = 0.1f;
        public float ki = 0.1f;
        public float iBound = 0.1f;
        public float kd = 0.0f;

        @Override
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(volumeScale);
            buf.writeFloat(kp);
            buf.writeFloat(ki);
            buf.writeFloat(iBound);
            buf.writeFloat(kd);
        }

        @Override
        public void decode(FriendlyByteBuf buf) {
            volumeScale = buf.readFloat();
            kp = buf.readFloat();
            ki = buf.readFloat();
            iBound = buf.readFloat();
            kd = buf.readFloat();
        }

        @Override
        public boolean save(CompoundTag tag) {
            tag.putFloat(TAGS.VOLUME_SCALE, volumeScale);
            tag.putFloat(TAGS.KP, kp);
            tag.putFloat(TAGS.KI, ki);
            tag.putFloat(TAGS.IBOUND, iBound);
            tag.putFloat(TAGS.KD, kd);
            return true;
        }

        @Override
        public boolean load(CompoundTag tag) {
            if(tag.contains(TAGS.VOLUME_SCALE))
                volumeScale = tag.getFloat(TAGS.VOLUME_SCALE);
            if(tag.contains(TAGS.KP))
                kp = tag.getFloat(TAGS.KP);
            if(tag.contains(TAGS.KI))
                ki = tag.getFloat(TAGS.KI);
            if(tag.contains(TAGS.IBOUND))
                iBound = tag.getFloat(TAGS.IBOUND);
            if(tag.contains(TAGS.KD))
                kd = tag.getFloat(TAGS.KD);
            return true;
        }
    }

    private final PID pid = new PID(0.1f, 0.1f, 0, 0.1f);
    private final Settings settings = new Settings();
    private int tickCounter = 0;

    private float targetPrice = 0;

    public static String getNameStatic()
    {
        return "Target Price Bot";
    }
    public static String getDescriptionStatic()
    {
        return "A bot that tries to move the price towards a target price using a PID controller.";
    }

    public TargetPriceBotPlugin()
    {
        setCustomSettings(settings);
    }

    @Override
    public void encodeClientStreamData(FriendlyByteBuf buf) {
        buf.writeFloat(targetPrice);
    }

    @Override
    protected void setup() {
        getPluginInterface().setStreamPacketSendTickInterval(5);
    }

    @Override
    protected void update() {
        tickCounter++;
        if(tickCounter < 5) //update once per second
            return;
        tickCounter = 0;
        IMarketPluginInterface pluginInterface = getPluginInterface();
        targetPrice = pluginInterface.getPreviousTargetPrice();
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
            info("Placing order: " + marketOrderAmount + " (PID-OUT: " + output + ", normalized-order-size: " + normalized +" , volumeToTarget: " + volumeToTarget + ")");
        }
    }


    @Override
    protected void decodeSettings(FriendlyByteBuf buf) {
        super.decodeSettings(buf);
        pid.setKP(settings.kp);
        pid.setKI(settings.ki);
        pid.setIBound(settings.iBound);
        pid.setKD(settings.kd);
    }

    @Override
    protected boolean saveToFilesystem(CompoundTag tag) {
        CompoundTag customDataTag = new CompoundTag();
        customDataTag.putFloat("targetPrice", targetPrice);

        CompoundTag settingsTag  = new CompoundTag();
        settings.save(settingsTag);

        tag.put("customData", customDataTag);
        tag.put("settings", settingsTag);
        return true;
    }

    @Override
    protected boolean loadFromFilesystem(CompoundTag tag) {
        CompoundTag customDataTag = tag.getCompound("customData");
        targetPrice = customDataTag.getFloat("targetPrice");

        CompoundTag settingsTag = tag.getCompound("settings");
        return settings.load(settingsTag);
    }
}
