package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class  ServerMarketSettingsData implements INetworkPayloadEncoder {

    public final TradingPairData tradingPairData;   // referenced from BotSettingsData if available
    public final BotSettingsData botSettingsData;   // Can be null if no bot is created or settings of bot should not be applyed by the server

    public final boolean marketOpen;                // Can be changed by the client
    public final long itemImbalance;                // Read only
    public final long shiftPriceCandleIntervalMS;   // Can be changed by the client
    public final long notifySubscriberIntervalMS;   // Can be changed by the client


    public boolean doCreateBotIfNotExists = false;  // Client set only, Server read only
    public boolean doDestroyBotIfExists = false;    // Client set only, Server read only


    public ServerMarketSettingsData(@NotNull TradingPair pair, @Nullable ServerVolatilityBot.Settings settings,
                                    boolean marketOpen, long itemImbalance,
                                    long shiftPriceCandleIntervalMS, long notifySubscriberIntervalMS) {
        if(settings == null) {
            this.botSettingsData = null;
            this.tradingPairData = new TradingPairData(pair);
        }
        else {
            this.botSettingsData = new BotSettingsData(pair, settings);
            tradingPairData = botSettingsData.tradingPairData;
        }


        this.marketOpen = marketOpen;
        this.itemImbalance = itemImbalance;
        this.shiftPriceCandleIntervalMS = shiftPriceCandleIntervalMS;
        this.notifySubscriberIntervalMS = notifySubscriberIntervalMS;
    }

    private ServerMarketSettingsData(TradingPairData pair, BotSettingsData settingsData,
                                     boolean marketOpen, long itemImbalance,
                                     long shiftPriceCandleIntervalMS, long notifySubscriberIntervalMS) {
        this.tradingPairData = pair;
        this.botSettingsData = settingsData;

        this.marketOpen = marketOpen;
        this.itemImbalance = itemImbalance;
        this.shiftPriceCandleIntervalMS = shiftPriceCandleIntervalMS;
        this.notifySubscriberIntervalMS = notifySubscriberIntervalMS;
    }


    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(botSettingsData != null);
        if(botSettingsData != null) {
            botSettingsData.encode(buf);
        }
        else {
            tradingPairData.encode(buf);
        }
        buf.writeBoolean(marketOpen);
        buf.writeLong(itemImbalance);
        buf.writeLong(shiftPriceCandleIntervalMS);
        buf.writeLong(notifySubscriberIntervalMS);
        buf.writeBoolean(doCreateBotIfNotExists);
        buf.writeBoolean(doDestroyBotIfExists);
    }

    public static ServerMarketSettingsData decode(FriendlyByteBuf buf) {
        TradingPairData tradingPairData = null;
        BotSettingsData botSettingsData = null;

        if(buf.readBoolean()) {
            botSettingsData = BotSettingsData.decode(buf);
            tradingPairData = botSettingsData.tradingPairData;
        }
        else {
            tradingPairData = TradingPairData.decode(buf);
        }
        boolean marketOpen = buf.readBoolean();
        long itemImbalance = buf.readLong();
        long shiftPriceCandleIntervalMS = buf.readLong();
        long notifySubscriberIntervalMS = buf.readLong();

        ServerMarketSettingsData data = new ServerMarketSettingsData(tradingPairData, botSettingsData,
                                            marketOpen, itemImbalance,
                                            shiftPriceCandleIntervalMS, notifySubscriberIntervalMS);
        // Read the flag for creating a bot if it does not exist
        data.doCreateBotIfNotExists = buf.readBoolean();
        data.doDestroyBotIfExists = buf.readBoolean();
        return data;
    }
}
