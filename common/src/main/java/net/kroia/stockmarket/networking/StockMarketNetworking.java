package net.kroia.stockmarket.networking;


import net.kroia.modutilities.networking.NetworkManager;
import net.kroia.modutilities.networking.arrs.AsynchronousRequestResponseSystem;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateStockMarketBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.request.*;
import net.kroia.stockmarket.networking.packet.server_sender.update.OpenScreenPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.entity.SyncStockMarketBlockEntityPacket;

public class StockMarketNetworking extends NetworkManager {

    public static BotSettingsRequest BOT_SETTINGS_REQUEST = (BotSettingsRequest) AsynchronousRequestResponseSystem.register(new BotSettingsRequest());
    public static CreateMarketRequest CREATE_MARKET_REQUEST = (CreateMarketRequest) AsynchronousRequestResponseSystem.register(new CreateMarketRequest());
    public static CreateMarketsRequest CREATE_MARKETS_REQUEST = (CreateMarketsRequest) AsynchronousRequestResponseSystem.register(new CreateMarketsRequest());
    public static RemoveMarketRequest REMOVE_MARKET_REQUEST = (RemoveMarketRequest) AsynchronousRequestResponseSystem.register(new RemoveMarketRequest());
    public static OrderBookVolumeRequest ORDER_BOOK_VOLUME_REQUEST = (OrderBookVolumeRequest) AsynchronousRequestResponseSystem.register(new OrderBookVolumeRequest());
    public static OrderCancelRequest ORDER_CANCEL_REQUEST = (OrderCancelRequest) AsynchronousRequestResponseSystem.register(new OrderCancelRequest());
    public static OrderChangeRequest ORDER_CHANGE_REQUEST = (OrderChangeRequest) AsynchronousRequestResponseSystem.register(new OrderChangeRequest());
    public static OrderCreateRequest ORDER_CREATE_REQUEST = (OrderCreateRequest) AsynchronousRequestResponseSystem.register(new OrderCreateRequest());
    public static PlayerOrderReadDataListRequest PLAYER_ORDER_READ_DATA_LIST_REQUEST = (PlayerOrderReadDataListRequest) AsynchronousRequestResponseSystem.register(new PlayerOrderReadDataListRequest());
    public static PriceHistoryRequest PRICE_HISTORY_REQUEST = (PriceHistoryRequest) AsynchronousRequestResponseSystem.register(new PriceHistoryRequest());
    public static GetServerMarketSettingsRequest GET_SERVER_MARKET_SETTINGS_REQUEST = (GetServerMarketSettingsRequest) AsynchronousRequestResponseSystem.register(new GetServerMarketSettingsRequest());
    public static SetServerMarketSettingsRequest SET_SERVER_MARKET_SETTINGS_REQUEST = (SetServerMarketSettingsRequest) AsynchronousRequestResponseSystem.register(new SetServerMarketSettingsRequest());
    public static TradingPairListRequest TRADING_PAIR_LIST_REQUEST = (TradingPairListRequest) AsynchronousRequestResponseSystem.register(new TradingPairListRequest());
    public static TradingViewDataRequest TRADING_VIEW_DATA_REQUEST = (TradingViewDataRequest) AsynchronousRequestResponseSystem.register(new TradingViewDataRequest());
    public static BotTargetPriceRequest BOT_TARGET_PRICE_REQUEST = (BotTargetPriceRequest) AsynchronousRequestResponseSystem.register(new BotTargetPriceRequest());
    public static DefaultMarketSetupDataGroupsRequest DEFAULT_MARKET_SETUP_DATA_GROUPS_REQUEST = (DefaultMarketSetupDataGroupsRequest) AsynchronousRequestResponseSystem.register(new DefaultMarketSetupDataGroupsRequest());
    public static DefaultMarketSetupDataGroupRequest DEFAULT_MARKET_SETUP_DATA_GROUP_REQUEST = (DefaultMarketSetupDataGroupRequest) AsynchronousRequestResponseSystem.register(new DefaultMarketSetupDataGroupRequest());
    public static DefaultMarketSetupDataRequest DEFAULT_MARKET_SETUP_DATA_REQUEST = (DefaultMarketSetupDataRequest) AsynchronousRequestResponseSystem.register(new DefaultMarketSetupDataRequest());
    public static PotentialTradingItemsRequest POTENTIAL_TRADING_ITEMS_REQUEST = (PotentialTradingItemsRequest) AsynchronousRequestResponseSystem.register(new PotentialTradingItemsRequest());
    public static IsTradingPairAllowedRequest IS_TRADING_PAIR_ALLOWED_REQUEST = (IsTradingPairAllowedRequest) AsynchronousRequestResponseSystem.register(new IsTradingPairAllowedRequest());
    public static GetRecommendedPriceRequest GET_RECOMMENDED_PRICE_REQUEST = (GetRecommendedPriceRequest) AsynchronousRequestResponseSystem.register(new GetRecommendedPriceRequest());
    public static ChartResetRequest CHART_RESET_REQUEST = (ChartResetRequest) AsynchronousRequestResponseSystem.register(new ChartResetRequest());
    public static SetMarketOpenRequest SET_MARKET_OPEN_REQUEST = (SetMarketOpenRequest) AsynchronousRequestResponseSystem.register(new SetMarketOpenRequest());
    public static DefaultPriceAjustmentFactorsDataRequest DEFAULT_PRICE_ADJUSTMENT_FACTORS_REQUEST = (DefaultPriceAjustmentFactorsDataRequest) AsynchronousRequestResponseSystem.register(new DefaultPriceAjustmentFactorsDataRequest());
    public static OrderReadDataRequest ORDER_READ_DATA_REQUEST = (OrderReadDataRequest) AsynchronousRequestResponseSystem.register(new OrderReadDataRequest());
    public static FetchOrderHistoryRequest ORDER_HISTORY_REQUEST = (FetchOrderHistoryRequest) AsynchronousRequestResponseSystem.register(new FetchOrderHistoryRequest());


    public StockMarketNetworking()
    {
        super(StockMarketMod.MOD_ID, "stockmarket_channel");
        setupARRS();
        setupClientReceiverPackets();
        setupServerReceiverPackets();
    }

    @Override
    public void setupClientReceiverPackets()
    {
       //register(SyncPricePacket.class, SyncPricePacket::encode, SyncPricePacket::new, SyncPricePacket::receive);
       register(SyncStockMarketBlockEntityPacket.class, SyncStockMarketBlockEntityPacket::encode, SyncStockMarketBlockEntityPacket::new, SyncStockMarketBlockEntityPacket::receive);
       register(SyncTradeItemsPacket.class, SyncTradeItemsPacket::encode, SyncTradeItemsPacket::new, SyncTradeItemsPacket::receive);
       //register(SyncOrderPacket.class, SyncOrderPacket::encode, SyncOrderPacket::new, SyncOrderPacket::receive);
       register(OpenScreenPacket.class, OpenScreenPacket::encode, OpenScreenPacket::new, OpenScreenPacket::receive);
       //register(SyncBotSettingsPacket.class, SyncBotSettingsPacket::encode, SyncBotSettingsPacket::new, SyncBotSettingsPacket::receive);
       //register(SyncBotTargetPricePacket.class, SyncBotTargetPricePacket::encode, SyncBotTargetPricePacket::new, SyncBotTargetPricePacket::receive);

    }

    @Override
    public void setupServerReceiverPackets()
    {



        //register(RequestPricePacket.class, RequestPricePacket::encode, RequestPricePacket::new, RequestPricePacket::receive);
        //register(RequestOrderPacket.class, RequestOrderPacket::encode, RequestOrderPacket::new, RequestOrderPacket::receive);
        //register(UpdateSubscribeMarketEventsPacket.class, UpdateSubscribeMarketEventsPacket::encode, UpdateSubscribeMarketEventsPacket::new, UpdateSubscribeMarketEventsPacket::receive);
        //register(RequestTradeItemsPacket.class, RequestTradeItemsPacket::encode, RequestTradeItemsPacket::new, RequestTradeItemsPacket::receive);
        //register(RequestOrderCancelPacket.class, RequestOrderCancelPacket::encode, RequestOrderCancelPacket::new, RequestOrderCancelPacket::receive);
        register(UpdateStockMarketBlockEntityPacket.class, UpdateStockMarketBlockEntityPacket::encode, UpdateStockMarketBlockEntityPacket::new, UpdateStockMarketBlockEntityPacket::receive);
        //register(RequestBotSettingsPacket.class, RequestBotSettingsPacket::encode, RequestBotSettingsPacket::new, RequestBotSettingsPacket::receive);
        //register(UpdateBotSettingsPacket.class, UpdateBotSettingsPacket::encode, UpdateBotSettingsPacket::new, UpdateBotSettingsPacket::receive);
        //register(RequestOrderChangePacket.class, RequestOrderChangePacket::encode, RequestOrderChangePacket::new, RequestOrderChangePacket::receive);
        //register(RequestManageTradingItemPacket.class, RequestManageTradingItemPacket::encode, RequestManageTradingItemPacket::new, RequestManageTradingItemPacket::receive);
        //register(RequestBotTargetPricePacket.class, RequestBotTargetPricePacket::encode, RequestBotTargetPricePacket::new, RequestBotTargetPricePacket::receive);


    }
}