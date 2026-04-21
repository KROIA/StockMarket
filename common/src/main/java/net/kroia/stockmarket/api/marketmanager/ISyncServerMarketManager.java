package net.kroia.stockmarket.api.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ISyncServerMarketManager {

    ItemID getTradingCurrencyID();
    List<ItemID> getAvailableMarketIDs();
    boolean marketExists(@NotNull ItemID marketID);

    @Nullable IServerMarket createMarket(@NotNull ItemID marketID);
    boolean deleteMarket(@NotNull ItemID marketID);
    @Nullable IServerMarket getMarket(@NotNull ItemID marketID);




    List<MarketPriceStruct>  getCurrentMarketPricesAndStartNewCandle();
    void update();
}
