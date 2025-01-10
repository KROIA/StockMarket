package net.kroia.stockmarket.networking;


import dev.architectury.impl.NetworkAggregator;
import dev.architectury.networking.simple.*;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.kroia.banksystem.networking.packet.server_sender.update.SyncBankDataPacket;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.networking.packet.client_sender.request.*;
import net.kroia.stockmarket.networking.packet.client_sender.update.UpdateBotSettingsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.UpdateSubscribeMarketEventsPacket;
import net.kroia.stockmarket.networking.packet.client_sender.update.entity.UpdateStockMarketBlockEntityPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.*;
import net.kroia.stockmarket.networking.packet.server_sender.update.entity.SyncStockMarketBlockEntityPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class StockMarketNetworking {

    public static final SimpleNetworkManager CHANNEL = SimpleNetworkManager.create(StockMarketMod.MOD_ID);

    public static MessageType SYNC_PRICE = registerS2C(getClassName(SyncPricePacket.class.getName()), SyncPricePacket::new);
    public static MessageType SYNC_STOCK_MARKET_BLOCK_ENTITY = registerS2C(getClassName(SyncStockMarketBlockEntityPacket.class.getName()), SyncStockMarketBlockEntityPacket::new);
    public static MessageType SYNC_TRADE_ITEMS = registerS2C(getClassName(SyncTradeItemsPacket.class.getName()), SyncTradeItemsPacket::new);
    public static MessageType SYNC_ORDER = registerS2C(getClassName(SyncOrderPacket.class.getName()), SyncOrderPacket::new);
    public static MessageType OPEN_SCREEN = registerS2C(getClassName(OpenScreenPacket.class.getName()), OpenScreenPacket::new);
    public static MessageType SYNC_BOT_SETTINGS = registerS2C(getClassName(SyncBotSettingsPacket.class.getName()), SyncBotSettingsPacket::new);



    public static MessageType REQUEST_PRICE = registerC2S(getClassName(RequestPricePacket.class.getName()), RequestPricePacket::new);
    public static MessageType REQUEST_ORDER = registerC2S(getClassName(RequestOrderPacket.class.getName()), RequestOrderPacket::new);
    public static MessageType UPDATE_SUBSCRIBE_MARKET_EVENTS = registerC2S(getClassName(UpdateSubscribeMarketEventsPacket.class.getName()), UpdateSubscribeMarketEventsPacket::new);
    public static MessageType REQUEST_TRADE_ITEMS = registerC2S(getClassName(RequestTradeItemsPacket.class.getName()), RequestTradeItemsPacket::new);
    public static MessageType REQUEST_ORDER_CANCEL = registerC2S(getClassName(RequestOrderCancelPacket.class.getName()), RequestOrderCancelPacket::new);
    public static MessageType UPDATE_STOCK_MARKET_BLOCK_ENTITY = registerC2S(getClassName(UpdateStockMarketBlockEntityPacket.class.getName()), UpdateStockMarketBlockEntityPacket::new);
    public static MessageType REQUEST_BOT_SETTINGS = registerC2S(getClassName(RequestBotSettingsPacket.class.getName()), RequestBotSettingsPacket::new);
    public static MessageType UPDATE_BOT_SETTINGS = registerC2S(getClassName(UpdateBotSettingsPacket.class.getName()), UpdateBotSettingsPacket::new);
    public static MessageType REQUEST_ORDER_CHANGE = registerC2S(getClassName(RequestOrderChangePacket.class.getName()), RequestOrderChangePacket::new);


    public static void init() {

    }
    private static String getClassName(String name) {
        String sub = name.substring(name.lastIndexOf(".")+1).toLowerCase();
        return sub;
    }

    public static MessageType registerS2C(String name, MessageDecoder<BaseS2CMessage> decoder)
    {
        MessageType registeredMsg = CHANNEL.registerS2C(name, decoder);
        if (Platform.getEnvironment() == Env.SERVER)
        {
            NetworkAggregator.registerS2CType(registeredMsg.getId(), List.of());
        }
        return registeredMsg;
    }
    public static MessageType registerC2S(String name, MessageDecoder<BaseC2SMessage> decoder)
    {
        MessageType registeredMsg = CHANNEL.registerC2S(name, decoder);
        return registeredMsg;
    }
}