package net.kroia.stockmarket.market.clientdata;

import net.kroia.banksystem.api.IBankAccount;
import net.kroia.banksystem.banking.clientdata.BankData;
import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TradingViewData implements INetworkPayloadEncoder {
    public final TradingPairData tradingPairData;
    public final PriceHistoryData priceHistoryData;
    public final OrderBookVolumeData orderBookVolumeData;
    public final OrderReadListData openOrdersData;

    public final BankData itemBankData;
    public final BankData currencyBankData;
    public final boolean marketIsOpen;
    //public final float botTargetPrice;
    public final float smalestTradableVolume;

    public TradingViewData(@NotNull TradingPairData pair,
                           @NotNull PriceHistoryData history,
                           @Nullable IBankAccount bankAccount,
                           @NotNull OrderBookVolumeData orderBookVolumeData,
                           @NotNull OrderReadListData openOrders,
                           boolean marketIsOpen,
                           //float botTargetPrice,
                           float smalestTradableVolume) {
        this.tradingPairData = pair;
        this.priceHistoryData = history;

        this.orderBookVolumeData = orderBookVolumeData;
        this.openOrdersData = openOrders;

        if(bankAccount != null) {
            this.itemBankData = bankAccount.getBankData(pair.getItem());
            this.currencyBankData = bankAccount.getBankData(pair.getCurrency());
        }
        else {
            this.itemBankData = null;
            this.currencyBankData = null;
        }
        this.marketIsOpen = marketIsOpen;
        //this.botTargetPrice = botTargetPrice;
        this.smalestTradableVolume = smalestTradableVolume;
    }
    private TradingViewData(@NotNull TradingPairData tradingPairData,
                            @NotNull PriceHistoryData priceHistoryData,
                            @NotNull OrderBookVolumeData orderBookVolumeData,
                            @NotNull OrderReadListData openOrdersData,
                            @Nullable BankData itemBankData,
                            @Nullable BankData currencyBankData,
                            boolean marketIsOpen,
                            //float botTargetPrice,
                            float smalestTradableVolume) {
        this.tradingPairData = tradingPairData;
        this.priceHistoryData = priceHistoryData;
        this.orderBookVolumeData = orderBookVolumeData;
        this.openOrdersData = openOrdersData;
        this.itemBankData = itemBankData;
        this.currencyBankData = currencyBankData;
        this.marketIsOpen = marketIsOpen;
        //this.botTargetPrice = botTargetPrice;
        this.smalestTradableVolume = smalestTradableVolume;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        tradingPairData.encode(buf);
        priceHistoryData.encode(buf);
        orderBookVolumeData.encode(buf);
        openOrdersData.encode(buf);
        buf.writeBoolean(itemBankData != null);
        if (itemBankData != null) {
            itemBankData.encode(buf);
        }

        buf.writeBoolean(currencyBankData != null);
        if (currencyBankData != null) {
            currencyBankData.encode(buf);
        }
        buf.writeBoolean(marketIsOpen);
        //buf.writeFloat(botTargetPrice);
        buf.writeFloat(smalestTradableVolume);
    }


    public static TradingViewData decode(FriendlyByteBuf buf) {
        TradingPairData tradingPairData = TradingPairData.decode(buf);
        PriceHistoryData priceHistoryData = PriceHistoryData.decode(buf);
        OrderBookVolumeData orderBookVolumeData = OrderBookVolumeData.decode(buf);
        OrderReadListData openOrdersData = OrderReadListData.decode(buf);
        BankData itemBankData = null;
        if(buf.readBoolean()) {
            itemBankData = BankData.decode(buf);
        }
        BankData currencyBankData = null;
        if(buf.readBoolean()) {
            currencyBankData = BankData.decode(buf);
        }
        boolean marketIsOpen = buf.readBoolean();
        //float botTargetPrice = buf.readFloat();
        float smalestTradableVolume = buf.readFloat();
        return new TradingViewData(tradingPairData, priceHistoryData, orderBookVolumeData,
                openOrdersData, itemBankData, currencyBankData, marketIsOpen, smalestTradableVolume);
    }
}
