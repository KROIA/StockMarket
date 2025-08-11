package net.kroia.stockmarket.market.clientdata;

import net.kroia.banksystem.api.IBankUser;
import net.kroia.banksystem.banking.clientdata.MinimalBankData;
import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class TradingViewData implements INetworkPayloadEncoder {
    public final TradingPairData tradingPairData;
    public final PriceHistoryData priceHistoryData;
    public final OrderBookVolumeData orderBookVolumeData;
    public final OrderReadListData openOrdersData;

    public final MinimalBankData itemBankData;
    public final MinimalBankData currencyBankData;
    public final boolean marketIsOpen;
    public final float botTargetPrice;
    public final float smalestTradableVolume;

    public TradingViewData(@NotNull TradingPairData pair,
                           @NotNull PriceHistoryData history,
                           @NotNull IBankUser bankUser,
                           @NotNull OrderBookVolumeData orderBookVolumeData,
                           @NotNull OrderReadListData openOrders,
                           boolean marketIsOpen,
                           float botTargetPrice,
                           float smalestTradableVolume) {
        this.tradingPairData = pair;
        this.priceHistoryData = history;

        this.orderBookVolumeData = orderBookVolumeData;
        this.openOrdersData = openOrders;

        this.itemBankData = bankUser.getMinimalBankData(pair.getItem());
        this.currencyBankData = bankUser.getMinimalBankData(pair.getCurrency());

        this.marketIsOpen = marketIsOpen;
        this.botTargetPrice = botTargetPrice;
        this.smalestTradableVolume = smalestTradableVolume;
    }
    private TradingViewData(@NotNull TradingPairData tradingPairData,
                            @NotNull PriceHistoryData priceHistoryData,
                            @NotNull OrderBookVolumeData orderBookVolumeData,
                            @NotNull OrderReadListData openOrdersData,
                            @NotNull MinimalBankData itemBankData,
                            @NotNull MinimalBankData currencyBankData,
                            boolean marketIsOpen,
                            float botTargetPrice,
                            float smalestTradableVolume) {
        this.tradingPairData = tradingPairData;
        this.priceHistoryData = priceHistoryData;
        this.orderBookVolumeData = orderBookVolumeData;
        this.openOrdersData = openOrdersData;
        this.itemBankData = itemBankData;
        this.currencyBankData = currencyBankData;
        this.marketIsOpen = marketIsOpen;
        this.botTargetPrice = botTargetPrice;
        this.smalestTradableVolume = smalestTradableVolume;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        tradingPairData.encode(buf);
        priceHistoryData.encode(buf);
        orderBookVolumeData.encode(buf);
        openOrdersData.encode(buf);
        itemBankData.encode(buf);
        currencyBankData.encode(buf);
        buf.writeBoolean(marketIsOpen);
        buf.writeFloat(botTargetPrice);
        buf.writeFloat(smalestTradableVolume);
    }


    public static TradingViewData decode(FriendlyByteBuf buf) {
        TradingPairData tradingPairData = TradingPairData.decode(buf);
        PriceHistoryData priceHistoryData = PriceHistoryData.decode(buf);
        OrderBookVolumeData orderBookVolumeData = OrderBookVolumeData.decode(buf);
        OrderReadListData openOrdersData = OrderReadListData.decode(buf);
        MinimalBankData itemBankData = MinimalBankData.decode(buf);
        MinimalBankData currencyBankData = MinimalBankData.decode(buf);
        boolean marketIsOpen = buf.readBoolean();
        float botTargetPrice = buf.readFloat();
        float smalestTradableVolume = buf.readFloat();
        return new TradingViewData(tradingPairData, priceHistoryData, orderBookVolumeData,
                openOrdersData, itemBankData, currencyBankData, marketIsOpen, botTargetPrice, smalestTradableVolume);
    }
}
