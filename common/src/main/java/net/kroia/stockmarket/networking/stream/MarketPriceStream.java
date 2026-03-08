package net.kroia.stockmarket.networking.stream;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.streaming.GenericStream;
import net.kroia.stockmarket.market.server.Market;
import net.kroia.stockmarket.market.server.MarketManager;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;

public class MarketPriceStream extends StockMarketGenericStream<ItemID, Long>
{
    long updateInterval = 100;
    long lastTimeMs = System.currentTimeMillis();
    ItemID itemID;
    long lastPrice = -1;

    @Override
    public GenericStream<ItemID, Long> copy() {
        return new MarketPriceStream();
    }

    @Override
    public String getStreamTypeID() {
        return MarketPriceStream.class.getName();
    }

    @Override
    public void onStartStreamSendingOnSever() {
        itemID = getContextData();
        info("MarketPriceStream started for item: " + itemID);
    }
    @Override
    public void onStopStreamSendingOnServer() {
        info("MarketPriceStream ended for item: " + itemID);
    }

    @Override
    protected void updateOnServer() {
        long now = System.currentTimeMillis();
        if (now - lastTimeMs > updateInterval) {
            lastTimeMs = now;
            MarketManager manager = getMarketManager();
            Market market = manager.getMarket(itemID);
            if(market != null) {
                 long currentPrice = market.getCurrentMarketPrice();
                 if(currentPrice != lastPrice) {
                     lastPrice = currentPrice;
                     sendPacket();
                 }
            }
        }
    }

    @Override
    public Long provideStreamPacketOnServer() {
        return lastPrice;
    }

    @Override
    public void encodeContextData(RegistryFriendlyByteBuf buffer, ItemID context) {
        ItemID.STREAM_CODEC.encode(buffer, context);
    }

    @Override
    public ItemID decodeContextData(RegistryFriendlyByteBuf buffer) {
        return ItemID.STREAM_CODEC.decode(buffer);
    }

    @Override
    public void encodeData(RegistryFriendlyByteBuf buffer, Long aLong) {
        ByteBufCodecs.VAR_LONG.encode(buffer, aLong);
    }

    @Override
    public Long decodeData(RegistryFriendlyByteBuf buffer) {
        return ByteBufCodecs.VAR_LONG.decode(buffer);
    }
}
