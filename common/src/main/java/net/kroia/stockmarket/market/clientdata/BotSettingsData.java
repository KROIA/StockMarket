package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class BotSettingsData implements INetworkPayloadEncoder {

    public final TradingPairData tradingPairData;


    public boolean enabled;
    public long updateTimerIntervallMS;
    public int defaultPrice;
    public float orderBookVolumeScale;
    public float nearMarketVolumeScale;
    public float volumeAccumulationRate;
    public float volumeFastAccumulationRate;
    public float volumeDecumulationRate;
    public float volumeScale = 2f;
    public boolean enableTargetPrice = true;
    public float targetPriceSteeringFactor = 0.1f;
    public boolean enableVolumeTracking = true;
    public float volumeSteeringFactor = 1.0E-7f;
    public boolean enableRandomWalk = true;
    public float volatility; // 0-1 or higher
    public int targetPrice = 0; //Just for visualisation on the bot settings menu

    public BotSettingsData(@NotNull TradingPair pair, @NotNull ServerVolatilityBot.Settings settings) {
        this.tradingPairData = new TradingPairData(pair);
        this.enabled = settings.enabled;
        this.updateTimerIntervallMS = settings.updateTimerIntervallMS;
        this.defaultPrice = settings.defaultPrice;
        this.orderBookVolumeScale = settings.orderBookVolumeScale;
        this.nearMarketVolumeScale = settings.nearMarketVolumeScale;
        this.volumeAccumulationRate = settings.volumeAccumulationRate;
        this.volumeFastAccumulationRate = settings.volumeFastAccumulationRate;
        this.volumeDecumulationRate = settings.volumeDecumulationRate;
        this.volumeScale = settings.volumeScale;
        this.enableTargetPrice = settings.enableTargetPrice;
        this.targetPriceSteeringFactor = settings.targetPriceSteeringFactor;
        this.enableVolumeTracking = settings.enableVolumeTracking;
        this.volumeSteeringFactor = settings.volumeSteeringFactor;
        this.enableRandomWalk = settings.enableRandomWalk;
        this.volatility = settings.volatility;
        this.targetPrice = settings.targetPrice;
    }

    public BotSettingsData(FriendlyByteBuf buf)
    {
        this.tradingPairData = TradingPairData.decode(buf);
        this.enabled = buf.readBoolean();
        this.updateTimerIntervallMS = buf.readLong();
        this.defaultPrice = buf.readInt();
        this.orderBookVolumeScale = buf.readFloat();
        this.nearMarketVolumeScale = buf.readFloat();
        this.volumeAccumulationRate = buf.readFloat();
        this.volumeFastAccumulationRate = buf.readFloat();
        this.volumeDecumulationRate = buf.readFloat();
        this.volumeScale = buf.readFloat();
        this.enableTargetPrice = buf.readBoolean();
        this.targetPriceSteeringFactor = buf.readFloat();
        this.enableVolumeTracking = buf.readBoolean();
        this.volumeSteeringFactor = buf.readFloat();
        this.enableRandomWalk = buf.readBoolean();
        this.volatility = buf.readFloat();
        this.targetPrice = buf.readInt();
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        tradingPairData.encode(buf);

        buf.writeBoolean(enabled);
        buf.writeLong(updateTimerIntervallMS);
        buf.writeInt(defaultPrice);
        buf.writeFloat(orderBookVolumeScale);
        buf.writeFloat(nearMarketVolumeScale);
        buf.writeFloat(volumeAccumulationRate);
        buf.writeFloat(volumeFastAccumulationRate);
        buf.writeFloat(volumeDecumulationRate);
        buf.writeFloat(volumeScale);
        buf.writeBoolean(enableTargetPrice);
        buf.writeFloat(targetPriceSteeringFactor);
        buf.writeBoolean(enableVolumeTracking);
        buf.writeFloat(volumeSteeringFactor);
        buf.writeBoolean(enableRandomWalk);
        buf.writeFloat(volatility);
        buf.writeInt(targetPrice);
    }

    public static BotSettingsData decode(FriendlyByteBuf buf) {
        return new BotSettingsData(buf);
    }

    public ServerVolatilityBot.Settings toSettings() {
        ServerVolatilityBot.Settings settings = new ServerVolatilityBot.Settings();
        settings.enabled = this.enabled;
        settings.updateTimerIntervallMS = this.updateTimerIntervallMS;
        settings.defaultPrice = this.defaultPrice;
        settings.orderBookVolumeScale = this.orderBookVolumeScale;
        settings.nearMarketVolumeScale = this.nearMarketVolumeScale;
        settings.volumeAccumulationRate = this.volumeAccumulationRate;
        settings.volumeFastAccumulationRate = this.volumeFastAccumulationRate;
        settings.volumeDecumulationRate = this.volumeDecumulationRate;
        settings.volumeScale = this.volumeScale;
        settings.enableTargetPrice = this.enableTargetPrice;
        settings.targetPriceSteeringFactor = this.targetPriceSteeringFactor;
        settings.enableVolumeTracking = this.enableVolumeTracking;
        settings.volumeSteeringFactor = this.volumeSteeringFactor;
        settings.enableRandomWalk = this.enableRandomWalk;
        settings.volatility = this.volatility;
        settings.targetPrice = this.targetPrice;
        return settings;
    }
}
