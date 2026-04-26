package net.kroia.stockmarket.api.marketmanager;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface ISyncServerMarketManager {

    ItemID getTradingCurrencyID();
    List<ItemID> getAvailableMarketIDs();
    boolean marketExists(@NotNull ItemID marketID);

    @Nullable IServerMarket createMarket(@NotNull ItemID marketID);
    boolean deleteMarket(@NotNull ItemID marketID);
    @Nullable IServerMarket getMarket(@NotNull ItemID marketID);

    @Nullable UUID getPlayerUUID(String playerName);
    boolean setStockmarketAdminMode(UUID playerUUID, boolean isAdmin);
    boolean isStockmarketAdmin(UUID playerUUID);

    /**
     * Call this function when a player joins the server to setup its bank account
     * @param playerUUID
     * @param playerName
     */
    void onPlayerJoin(UUID playerUUID, String playerName);

    List<MarketPriceStruct>  getCurrentMarketPricesAndStartNewCandle();
    void update();
}
