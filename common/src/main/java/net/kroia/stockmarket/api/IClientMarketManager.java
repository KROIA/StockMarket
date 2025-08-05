package net.kroia.stockmarket.api;

import net.kroia.banksystem.util.ItemID;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.MarketFactory;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.minecraft.util.Tuple;

import java.util.List;
import java.util.function.Consumer;

public interface IClientMarketManager {

    public void init();
    public IClientMarket getClientMarket(TradingPair pair);


    public void requestCreateMarket(TradingPair pair, Consumer<Boolean> callback );
    public void requestCreateMarket(MarketFactory.DefaultMarketSetupData setupData, Consumer<Boolean> callback );
    public void requestCreateMarkets(List<MarketFactory.DefaultMarketSetupData> setupDataList, Consumer<List<Boolean>> callback);
    public void requestRemoveMarket(TradingPair pair, Consumer<Boolean> callback);
    public void requestRemoveMarket(List<TradingPair> pairs, Consumer<List<Boolean>> callback );
    public void requestChartReset(List<TradingPair> pairs, Consumer<List<Boolean>> callback);
    public void requestSetMarketOpen(TradingPair pair, boolean open, Consumer<Boolean> callback);
    public void requestSetMarketOpen(List<Tuple<TradingPair, Boolean>> pairs, Consumer<List<Boolean>> callback);
    public void requestSetMarketOpen(List<TradingPair> pairs, boolean allOpen, Consumer<List<Boolean>> callback);
    public void requestTradingPairs(Consumer<List<TradingPair>> callback );
    public void requestMarketCategories(Consumer<List<MarketFactory.DefaultMarketSetupDataGroup>> callback);
    public void requestIsTradingPairAllowed(TradingPair pair, Consumer<Boolean> callback );
    public void requestRecommendedPrice(TradingPair pair, Consumer<Integer> callback );
    public void requestPotentialTradeItems(String searchText, Consumer<List<ItemID>> callback);




    public void handlePacket(SyncTradeItemsPacket packet);
}
