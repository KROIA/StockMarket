package net.kroia.stockmarket.market.clientdata;

import net.kroia.banksystem.api.IBank;
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

    public TradingViewData(@NotNull TradingPairData pair,
                           @NotNull PriceHistoryData history,
                           @NotNull IBank itemBank,
                           @NotNull IBank currencyBank,
                           @NotNull OrderBookVolumeData orderBookVolumeData,
                           @NotNull OrderReadListData openOrders,
                           boolean marketIsOpen,
                           float botTargetPrice) {
        this.tradingPairData = pair;
        this.priceHistoryData = history;

        this.orderBookVolumeData = orderBookVolumeData;
        this.openOrdersData = openOrders;

        this.itemBankData = itemBank.getMinimalData();
        this.currencyBankData = currencyBank.getMinimalData();

        this.marketIsOpen = marketIsOpen;
        this.botTargetPrice = botTargetPrice;
    }
    private TradingViewData(@NotNull TradingPairData tradingPairData,
                            @NotNull PriceHistoryData priceHistoryData,
                            @NotNull OrderBookVolumeData orderBookVolumeData,
                            @NotNull OrderReadListData openOrdersData,
                            @NotNull MinimalBankData itemBankData,
                            @NotNull MinimalBankData currencyBankData,
                            boolean marketIsOpen,
                            float botTargetPrice) {
        this.tradingPairData = tradingPairData;
        this.priceHistoryData = priceHistoryData;
        this.orderBookVolumeData = orderBookVolumeData;
        this.openOrdersData = openOrdersData;
        this.itemBankData = itemBankData;
        this.currencyBankData = currencyBankData;
        this.marketIsOpen = marketIsOpen;
        this.botTargetPrice = botTargetPrice;
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
        return new TradingViewData(tradingPairData, priceHistoryData, orderBookVolumeData,
                openOrdersData, itemBankData, currencyBankData, marketIsOpen, botTargetPrice);
    }
}
