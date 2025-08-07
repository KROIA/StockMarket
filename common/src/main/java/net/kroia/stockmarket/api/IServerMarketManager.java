package net.kroia.stockmarket.api;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.*;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface IServerMarketManager extends ServerSaveable {


    @Nullable BotSettingsData getBotSettingsData(@NotNull TradingPair pair);
    @Nullable TradingPairData getTradingPairData(@NotNull TradingPair pair);
    @Nullable OrderBookVolumeData getOrderBookVolumeData(@NotNull TradingPair pair, int historyViewCount, int minPrice, int maxPrice, int tileCount);
    @Nullable OrderBookVolumeData getOrderBookVolumeData(@NotNull TradingPair pair);
    @Nullable OrderReadData getOrderReadData(@NotNull TradingPair pair, long orderID);
    @Nullable OrderReadListData getOrderReadListData(@NotNull TradingPair pair, List<Long> orderIDs);
    @Nullable OrderReadListData getOrderReadListData(@NotNull TradingPair pair, UUID playerUUID);
    @Nullable PriceHistoryData getPriceHistoryData(@NotNull TradingPair pair, int maxHistoryPointCount);
    @Nullable TradingViewData getTradingViewData(@NotNull TradingPair pair, UUID player,
                                                        int maxHistoryPointCount,
                                                        int minVisiblePrice,
                                                        int maxVisiblePrice,
                                                        int orderBookTileCount,
                                                        boolean requestBotTargetPrice);
    @Nullable TradingViewData getTradingViewData(@NotNull TradingPair pair, UUID player);
    @Nullable ServerMarketSettingsData getMarketSettingsData(@NotNull TradingPair pair);
    @NotNull TradingPairListData getTradingPairListData();
    boolean setMarketSettingsData(@NotNull TradingPair pair, @Nullable ServerMarketSettingsData settingsData);
    boolean setBotSettingsData(@NotNull TradingPair pair, @Nullable BotSettingsData botSettingsData);

    boolean handleOrderCreateData(@NotNull OrderCreateData orderCreateData, ServerPlayer sender);
    boolean handleOrderChangeData(@NotNull OrderChangeData orderChangeData, ServerPlayer sender);
    boolean handleOrderCancelData(@NotNull OrderCancelData orderCancelData, ServerPlayer sender);


    void setShiftPriceCandleIntervalMS(long shiftPriceCandleIntervalMS);


    void setAllMarketsOpen(boolean open);
    boolean setMarketOpen(@NotNull TradingPair pair, boolean open);
    List<Boolean> setMarketOpen(List<Tuple<TradingPair, Boolean>> pairsAndOpenStates);

    ItemID getDefaultCurrencyItemID();
    boolean isItemAllowedForTrading(ItemID item);
    Set<ItemID> getNotTradableItems();
    boolean isTradingPairAllowedForTrading(TradingPair pair);

    IServerMarket getMarket(@NotNull TradingPair pair);
    List<TradingPair> getTradingPairs();
    List<ItemID> getPotentialTradingItems(String searchQuery);
    boolean marketExists(@NotNull TradingPair pair);
    int getRecommendedPrice(TradingPair pair);


    void onServerTick(MinecraftServer server);


    boolean createMarket(MarketFactory.DefaultMarketSetupData defaultMarketSetupData);
    boolean createMarket(MarketFactory.DefaultMarketSetupDataGroup category);

    boolean createMarket(@NotNull TradingPair pair, int startPrice);
    boolean createMarket(@NotNull ItemID itemID, @NotNull ItemID currency, int startPrice);
    boolean createMarket(@NotNull ItemID itemID, int startPrice);
    boolean createMarket(@NotNull ServerMarketSettingsData settingsData);
    List<Boolean> createMarkets(@NotNull List<MarketFactory.DefaultMarketSetupData> defaultMarketSetupDataList);

    boolean removeTradeItem(@NotNull TradingPair pair);
    boolean removeTradeItem(@NotNull ItemID itemID, @NotNull ItemID currency);




    @Override
    boolean save(CompoundTag tag);

    @Override
    boolean load(CompoundTag tag);
}
