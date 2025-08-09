package net.kroia.stockmarket.api;

import net.kroia.modutilities.ServerSaveable;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.*;
import net.kroia.stockmarket.market.server.OrderBook;
import net.kroia.stockmarket.market.server.VirtualOrderBook;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.kroia.stockmarket.market.server.order.Order;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface IServerMarket extends ServerSaveable {


    void update(MinecraftServer server);

    @Nullable BotSettingsData getBotSettingsData();
    @Nullable VirtualOrderBookSettingsData getVirtualOrderBookSettingsData();
    TradingPairData getTradingPairData();
    OrderBookVolumeData getOrderBookVolumeData(int historyViewCount, float minPrice, float maxPrice, int tileCount);
    OrderBookVolumeData getOrderBookVolumeData();
    OrderReadData getOrderReadData(long orderID);
    OrderReadListData getOrderReadListData(List<Long> orderIDs);
    OrderReadListData getOrderReadListData(UUID playerUUID);
    PriceHistoryData getPriceHistoryData(int maxHistoryPointCount);
    TradingViewData getTradingViewData(UUID player,int maxHistoryPointCount, float minVisiblePrice, float maxVisiblePrice, int orderBookTileCount, boolean requestBotTargetPrice);
    TradingViewData getTradingViewData(UUID player);
    ServerMarketSettingsData getMarketSettingsData();
    boolean setMarketSettingsData(@Nullable ServerMarketSettingsData settingsData);
    boolean setBotSettingsData(@Nullable BotSettingsData botSettingsData);
    boolean setBotSettings(ServerVolatilityBot.Settings settings);

    boolean setVirtualOrderBookSettingsData(@Nullable VirtualOrderBookSettingsData virtualOrderBookSettingsData);
    boolean setVirtualOrderBookSettings(VirtualOrderBook.Settings settings);

    int getPriceScaleFactor();

    int getBotTargetPrice();
    float getBotTargetPriceF();
    void resetHistoricalMarketData();



    void setShiftPriceCandleIntervalMS(long shiftPriceCandleIntervalMS);
    long getShiftPriceCandleIntervalMS();




    void createVolatilityBot(ServerVolatilityBot.Settings settings);
    boolean destroyVolatilityBot();
    boolean hasVolatilityBot();



    void createVirtualOrderBook(int realVolumeBookSize, VirtualOrderBook.Settings settings);
    boolean destroyVirtualOrderBook();
    boolean hasVirtualOrderBook();



    TradingPair getTradingPair();
    OrderBook getOrderBook();
    int getCurrentRawPrice();
    float getCurrentRealPrice();
    int mapToRawPrice(float realPrice);
    float mapToRealPrice(int rawPrice);
    boolean isMarketOpen();
    void openMarket();
    void closeMarket();
    void setMarketOpen(boolean marketOpen);




    boolean createLimitOrder(UUID playerUUID, long amount, float price);
    boolean createMarketOrder(UUID playerUUID, long amount);
    boolean createBotLimitOrder(long amount, float price);
    boolean createBotMarketOrder(long amount);



    boolean cancelOrder(long orderID);
    boolean cancelOrder(Order order);
    void cancelAllBotOrders();



    long getItemImbalance();
    void addItemImbalance(long amount);
    void setItemImbalance(long itemImbalance);





    @Override
    boolean save(CompoundTag tag);

    @Override
    boolean load(CompoundTag tag);
}
