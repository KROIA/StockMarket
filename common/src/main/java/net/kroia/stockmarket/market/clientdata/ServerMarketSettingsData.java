package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.VirtualOrderBook;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class  ServerMarketSettingsData implements INetworkPayloadEncoder {

    public TradingPairData tradingPairData;   // referenced from BotSettingsData if available
    public @Nullable BotSettingsData botSettingsData = null;   // Can be null if no bot is created or settings of bot should not be applyed by the server
    public @Nullable VirtualOrderBookSettingsData virtualOrderBookSettingsData = null;
    public boolean marketOpen;                // Can be changed by the client
    public long itemImbalance;                //
    public boolean overwriteItemImbalance = false; // If true, the server will overwrite the item imbalance with the value from the client
    public long shiftPriceCandleIntervalMS;   // Can be changed by the client
    //public long notifySubscriberIntervalMS;   // Can be changed by the client


    public boolean doCreateBotIfNotExists = false;  // Client set only, Server read only
    public boolean doDestroyBotIfExists = false;    // Client set only, Server read only
    public boolean doCreateVirtualOrderBookIfNotExists = false; // Client set only, Server read only
    public boolean doDestroyVirtualOrderBookIfExists = false; // Client set only, Server read only


    public ServerMarketSettingsData(@NotNull TradingPair pair, @Nullable ServerVolatilityBot.Settings settings,
                                    @Nullable VirtualOrderBook.Settings virtualOrderBookSettings,
                                    boolean marketOpen, long itemImbalance,
                                    long shiftPriceCandleIntervalMS/*, long notifySubscriberIntervalMS*/) {
        this.tradingPairData = new TradingPairData(pair);

        if(settings != null) {
            this.botSettingsData = new BotSettingsData(pair, settings);
        }
        if(virtualOrderBookSettings != null) {
            this.virtualOrderBookSettingsData = new VirtualOrderBookSettingsData(pair, virtualOrderBookSettings);
        }



        this.marketOpen = marketOpen;
        this.itemImbalance = itemImbalance;
        this.shiftPriceCandleIntervalMS = shiftPriceCandleIntervalMS;
        //this.notifySubscriberIntervalMS = notifySubscriberIntervalMS;
    }

    private ServerMarketSettingsData(TradingPairData pair, BotSettingsData settingsData,
                                     VirtualOrderBookSettingsData virtualOrderBookSettingsData,
                                     boolean marketOpen, long itemImbalance,
                                     long shiftPriceCandleIntervalMS/*, long notifySubscriberIntervalMS*/) {
        this.tradingPairData = pair;
        this.botSettingsData = settingsData;
        this.virtualOrderBookSettingsData = virtualOrderBookSettingsData;

        this.marketOpen = marketOpen;
        this.itemImbalance = itemImbalance;
        this.shiftPriceCandleIntervalMS = shiftPriceCandleIntervalMS;
        //this.notifySubscriberIntervalMS = notifySubscriberIntervalMS;
    }


    @Override
    public void encode(FriendlyByteBuf buf) {
        tradingPairData.encode(buf);
        buf.writeBoolean(botSettingsData != null);
        if(botSettingsData != null) {
            botSettingsData.encode(buf);
        }
        buf.writeBoolean(virtualOrderBookSettingsData != null);
        if(virtualOrderBookSettingsData != null) {
            virtualOrderBookSettingsData.encode(buf);
        }
        buf.writeBoolean(marketOpen);
        buf.writeBoolean(overwriteItemImbalance);
        buf.writeLong(itemImbalance);
        buf.writeLong(shiftPriceCandleIntervalMS);
        //buf.writeLong(notifySubscriberIntervalMS);
        buf.writeBoolean(doCreateBotIfNotExists);
        buf.writeBoolean(doDestroyBotIfExists);
        buf.writeBoolean(doCreateVirtualOrderBookIfNotExists);
        buf.writeBoolean(doDestroyVirtualOrderBookIfExists);
    }

    public static ServerMarketSettingsData decode(FriendlyByteBuf buf) {
        TradingPairData tradingPairData = TradingPairData.decode(buf);
        BotSettingsData botSettingsData = null;
        VirtualOrderBookSettingsData virtualOrderBookSettingsData = null;

        if(buf.readBoolean()) {
            botSettingsData = BotSettingsData.decode(buf);
        }
        if(buf.readBoolean()) {
            virtualOrderBookSettingsData = VirtualOrderBookSettingsData.decode(buf);
        }
        boolean marketOpen = buf.readBoolean();
        boolean overwriteItemImbalance = buf.readBoolean();
        long itemImbalance = buf.readLong();
        long shiftPriceCandleIntervalMS = buf.readLong();
        //long notifySubscriberIntervalMS = buf.readLong();

        ServerMarketSettingsData data = new ServerMarketSettingsData(tradingPairData, botSettingsData,
                virtualOrderBookSettingsData, marketOpen, itemImbalance,
                                            shiftPriceCandleIntervalMS/*, notifySubscriberIntervalMS*/);
        // Read the flag for creating a bot if it does not exist
        data.doCreateBotIfNotExists = buf.readBoolean();
        data.doDestroyBotIfExists = buf.readBoolean();
        data.doCreateVirtualOrderBookIfNotExists = buf.readBoolean();
        data.doDestroyVirtualOrderBookIfExists = buf.readBoolean();
        data.overwriteItemImbalance = overwriteItemImbalance;
        return data;
    }
}
