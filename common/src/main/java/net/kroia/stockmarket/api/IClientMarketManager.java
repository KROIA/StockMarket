package net.kroia.stockmarket.api;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.DefaultPriceAjustmentFactorsData;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.minecraft.util.Tuple;

import java.util.List;
import java.util.function.Consumer;

public interface IClientMarketManager {

    void init();
    IClientMarket getClientMarket(TradingPair pair);


    void requestCreateMarket(TradingPair pair, Consumer<Boolean> callback );
    void requestCreateMarket(MarketFactory.DefaultMarketSetupData setupData, Consumer<Boolean> callback );
    void requestCreateMarkets(List<MarketFactory.DefaultMarketSetupData> setupDataList, Consumer<List<Boolean>> callback);
    void requestRemoveMarket(TradingPair pair, Consumer<Boolean> callback);
    void requestRemoveMarket(List<TradingPair> pairs, Consumer<List<Boolean>> callback );
    void requestChartReset(List<TradingPair> pairs, Consumer<List<Boolean>> callback);
    void requestSetMarketOpen(TradingPair pair, boolean open, Consumer<Boolean> callback);
    void requestSetMarketOpen(List<Tuple<TradingPair, Boolean>> pairs, Consumer<List<Boolean>> callback);
    void requestSetMarketOpen(List<TradingPair> pairs, boolean allOpen, Consumer<List<Boolean>> callback);
    void requestTradingPairs(Consumer<List<TradingPair>> callback );
    void requestMarketCategories(Consumer<List<MarketFactory.DefaultMarketSetupDataGroup>> callback);
    void requestIsTradingPairAllowed(TradingPair pair, Consumer<Boolean> callback );
    void requestRecommendedPrice(TradingPair pair, Consumer<Integer> callback );
    void requestPotentialTradeItems(String searchText, Consumer<List<ItemID>> callback);

    void requestDefaultPriceAjustmentFactors(Consumer<DefaultPriceAjustmentFactorsData> callback);
    void updateDefaultPriceAjustmentFactors(DefaultPriceAjustmentFactorsData data, Consumer<DefaultPriceAjustmentFactorsData> callback);




    void handlePacket(SyncTradeItemsPacket packet);
}
