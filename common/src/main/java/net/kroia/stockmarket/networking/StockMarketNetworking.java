package net.kroia.stockmarket.networking;

import net.kroia.banksystem.BankSystemMod;
import net.kroia.modutilities.networking.NetworkPacketManager;
import net.kroia.modutilities.networking.client_server.arrs.AsynchronousRequestResponseSystem;
import net.kroia.modutilities.networking.client_server.streaming.StreamSystem;
import net.kroia.stockmarket.StockMarketModBackend;
import net.kroia.stockmarket.networking.entity.UpdateDisplayViewportPacket;
import net.kroia.stockmarket.networking.entity.UpdateStockMarketDisplayConfigPacket;
import net.kroia.stockmarket.networking.packet.MarketRemovedPacket;
import net.kroia.stockmarket.networking.packet.NewsPublishedPacket;
import net.kroia.stockmarket.networking.packet.OpenUIPacket;
import net.kroia.stockmarket.networking.packet.PlayerJoinSyncPacket;
import net.kroia.stockmarket.networking.packet.VillagerTradePriceTablePacket;
import net.kroia.stockmarket.networking.request.*;
import net.kroia.stockmarket.networking.stream.ActiveOrdersStream;
import net.kroia.stockmarket.networking.stream.MarketPriceStream;
import net.kroia.stockmarket.networking.stream.PluginRuntimeDataStream;
import net.kroia.stockmarket.minecraft.command.AsyncStockMarketCommandHandler;
import net.kroia.stockmarket.stockmarket.market.AsyncMarket;
import net.kroia.stockmarket.stockmarket.market.preset.AsyncPresetManager;
import net.kroia.stockmarket.stockmarket.marketmanager.AsyncMarketManager;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;

public class StockMarketNetworking extends NetworkPacketManager {
    public static void setBackend(StockMarketModBackend.ServerInstances backend) {
        StockMarketGenericStream.setBackend(backend);
        StockMarketGenericRequest.setBackend(backend);
        StockMarketNetworkPacket.setBackend(backend);
    }
    public static void setBackend(StockMarketModBackend.CommonInstances common) {
        StockMarketNetworkPacket.setBackend(common);
    }
    public static void setBackend(StockMarketModBackend.ClientInstances common) {
        StockMarketNetworkPacket.setBackend(common);
    }

    public final MarketPriceStream MARKET_PRICE_STREAM = (MarketPriceStream) StreamSystem.register(new MarketPriceStream());
    public final PluginRuntimeDataStream PLUGIN_RUNTIME_DATA_STREAM = (PluginRuntimeDataStream) StreamSystem.register(new PluginRuntimeDataStream());
    public final ActiveOrdersStream ACTIVE_ORDERS_STREAM = (ActiveOrdersStream) StreamSystem.register(new ActiveOrdersStream());

    public final MarketPriceHistoryRequest MARKET_PRICE_HISTORY_REQUEST = (MarketPriceHistoryRequest) AsynchronousRequestResponseSystem.register(new MarketPriceHistoryRequest());
    public final MarketsRequest MARKETS_REQUEST = (MarketsRequest) AsynchronousRequestResponseSystem.register(new MarketsRequest());
    public final CreateOrderRequest CREATE_ORDER_REQUEST = (CreateOrderRequest) AsynchronousRequestResponseSystem.register(new CreateOrderRequest());
    public final ActiveOrdersRequest ACTIVE_ORDERS_REQUEST = (ActiveOrdersRequest) AsynchronousRequestResponseSystem.register(new ActiveOrdersRequest());
    public final ServerTimeRequest SERVER_TIME_REQUEST = (ServerTimeRequest) AsynchronousRequestResponseSystem.register(new ServerTimeRequest());
    public final OrderbookVolumeRequest ORDERBOOK_VOLUME_REQUEST = (OrderbookVolumeRequest) AsynchronousRequestResponseSystem.register(new OrderbookVolumeRequest());
    public final PluginListRequest PLUGIN_LIST_REQUEST = (PluginListRequest) AsynchronousRequestResponseSystem.register(new PluginListRequest());
    public final PluginSettingsRequest PLUGIN_SETTINGS_REQUEST = (PluginSettingsRequest) AsynchronousRequestResponseSystem.register(new PluginSettingsRequest());
    public final PluginReorderRequest PLUGIN_REORDER_REQUEST = (PluginReorderRequest) AsynchronousRequestResponseSystem.register(new PluginReorderRequest());
    public final PluginCustomSettingsRequest PLUGIN_CUSTOM_SETTINGS_REQUEST = (PluginCustomSettingsRequest) AsynchronousRequestResponseSystem.register(new PluginCustomSettingsRequest());
    public final PluginSubscriptionRequest PLUGIN_SUBSCRIPTION_REQUEST = (PluginSubscriptionRequest) AsynchronousRequestResponseSystem.register(new PluginSubscriptionRequest());
    public final PlayerPreferencesGetRequest PLAYER_PREFERENCES_GET_REQUEST = (PlayerPreferencesGetRequest) AsynchronousRequestResponseSystem.register(new PlayerPreferencesGetRequest());
    public final PlayerPreferencesUpdateRequest PLAYER_PREFERENCES_UPDATE_REQUEST = (PlayerPreferencesUpdateRequest) AsynchronousRequestResponseSystem.register(new PlayerPreferencesUpdateRequest());
    public final CancelOrderRequest CANCEL_ORDER_REQUEST = (CancelOrderRequest) AsynchronousRequestResponseSystem.register(new CancelOrderRequest());
    public final OrderHistoryRequest ORDER_HISTORY_REQUEST = (OrderHistoryRequest) AsynchronousRequestResponseSystem.register(new OrderHistoryRequest());
    public final TransactionHistoryRequest TRANSACTION_HISTORY_REQUEST = (TransactionHistoryRequest) AsynchronousRequestResponseSystem.register(new TransactionHistoryRequest());
    public final PresetUpdateRequest PRESET_UPDATE_REQUEST = (PresetUpdateRequest) AsynchronousRequestResponseSystem.register(new PresetUpdateRequest());
    public final PluginCreateRequest PLUGIN_CREATE_REQUEST = (PluginCreateRequest) AsynchronousRequestResponseSystem.register(new PluginCreateRequest());
    public final PluginDeleteRequest PLUGIN_DELETE_REQUEST = (PluginDeleteRequest) AsynchronousRequestResponseSystem.register(new PluginDeleteRequest());
    public final PlaceInterMarketOrderRequest PLACE_INTER_MARKET_ORDER_REQUEST = (PlaceInterMarketOrderRequest) AsynchronousRequestResponseSystem.register(new PlaceInterMarketOrderRequest());
    public final CancelInterMarketOrderRequest CANCEL_INTER_MARKET_ORDER_REQUEST = (CancelInterMarketOrderRequest) AsynchronousRequestResponseSystem.register(new CancelInterMarketOrderRequest());
    public final GetAvailablePairsRequest GET_AVAILABLE_PAIRS_REQUEST = (GetAvailablePairsRequest) AsynchronousRequestResponseSystem.register(new GetAvailablePairsRequest());
    /** GET/SET of the server's settings.json for the master-only "Mod Settings" admin screen. */
    public final ModSettingsRequest MOD_SETTINGS_REQUEST = (ModSettingsRequest) AsynchronousRequestResponseSystem.register(new ModSettingsRequest());
    /** Paginated newest-first news history pages for the newspaper screen (T-073, not admin-gated). */
    public final NewsHistoryRequest NEWS_HISTORY_REQUEST = (NewsHistoryRequest) AsynchronousRequestResponseSystem.register(new NewsHistoryRequest());
    /** Admin-gated news operations (RELOAD/TRIGGER/LIST/STOP) for commands + plugin GUI (T-076). */
    public final NewsAdminRequest NEWS_ADMIN_REQUEST = (NewsAdminRequest) AsynchronousRequestResponseSystem.register(new NewsAdminRequest());
    /** Hash-batched published news-picture fetch for the client picture cache (T-089, not admin-gated, rate-limited). */
    public final NewsPictureRequest NEWS_PICTURE_REQUEST = (NewsPictureRequest) AsynchronousRequestResponseSystem.register(new NewsPictureRequest());

    //public final MarketSettingsGetRequest MARKET_SETTINGS_GET_REQUEST = (MarketSettingsGetRequest) AsynchronousRequestResponseSystem.register(new MarketSettingsGetRequest());


    public StockMarketNetworking()
    {
        super(BankSystemMod.MOD_ID, "stockmarket_channel");

        setupClientReceiverPackets();
        setupServerReceiverPackets();
        setupServerServerPackets();

        AsyncMarket.setupNetworkPacket();
        AsyncMarketManager.setupNetworkPacket();
        AsyncPresetManager.setupNetworkPacket();
        AsyncStockMarketCommandHandler.setupNetworkPacket();

        this.setupARRS(); // Setup the Asynchronous Request Response System (ARRS)
        this.setupStreamSystem();
    }

    @Override
    public void setupClientReceiverPackets() {
        registerS2C(PlayerJoinSyncPacket.TYPE, PlayerJoinSyncPacket.STREAM_CODEC);
        registerS2C(OpenUIPacket.TYPE, OpenUIPacket.STREAM_CODEC);
        // Market-deleted broadcast. The default registerS2C overload also registers
        // the packet with the MultiServerPacketRegistry, enabling the master→slave
        // relay used to reach players connected to slave servers.
        registerS2C(MarketRemovedPacket.TYPE, MarketRemovedPacket.STREAM_CODEC);
        // News-published broadcast (T-073). Same registration shape as
        // MarketRemovedPacket: registerS2C also registers the packet with the
        // MultiServerPacketRegistry, enabling the master→slave relay used to
        // reach players connected to slave servers.
        registerS2C(NewsPublishedPacket.TYPE, NewsPublishedPacket.STREAM_CODEC);
    }

    @Override
    public void setupServerReceiverPackets() {
        registerC2S(UpdateStockMarketDisplayConfigPacket.TYPE, UpdateStockMarketDisplayConfigPacket.STREAM_CODEC);
        registerC2S(UpdateDisplayViewportPacket.TYPE, UpdateDisplayViewportPacket.STREAM_CODEC);
    }

    @Override
    public void setupServerServerPackets() {
        // Master→slave villager-trade price table broadcast (pure S2S packet:
        // registered only with the MultiServerPacketRegistry, never with a
        // client/server receiver).
        registerS2S(VillagerTradePriceTablePacket.TYPE, VillagerTradePriceTablePacket.STREAM_CODEC);
    }
}
