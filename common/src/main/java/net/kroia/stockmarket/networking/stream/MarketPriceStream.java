package net.kroia.stockmarket.networking.stream;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.streaming.GenericStream;
import net.kroia.stockmarket.market.server.Market;
import net.kroia.stockmarket.market.server.MarketManager;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class MarketPriceStream extends StockMarketGenericStream<ItemID, MarketPriceStream.ResponseData>
{
    public static class ResponseData
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, ResponseData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, p -> p.timestamp,
                ByteBufCodecs.VAR_LONG, p -> p.marketPrice,
                ResponseData::new
        );

        public long timestamp;
        public long marketPrice;

        public ResponseData(long timestamp, long marketPrice)
        {
            this.timestamp = timestamp;
            this.marketPrice = marketPrice;
        }
    }

    private long updateInterval = 100;
    private long lastTimeMs = System.currentTimeMillis();
    private ItemID itemID;
    private final ResponseData lastPrice = new ResponseData(-1,-1);

    @Override
    public GenericStream<ItemID, ResponseData> copy() {
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
                 long currentTime = market.getCurrentTime();
                 if(currentPrice != lastPrice.marketPrice ||
                    currentTime != lastPrice.timestamp) {
                     lastPrice.marketPrice = currentPrice;
                     lastPrice.timestamp = currentTime;
                     sendPacket();
                 }
            }
        }
    }

    @Override
    public ResponseData provideStreamPacketOnServer() {
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
    public void encodeData(RegistryFriendlyByteBuf buffer, ResponseData aLong) {
        ResponseData.STREAM_CODEC.encode(buffer, aLong);
    }

    @Override
    public ResponseData decodeData(RegistryFriendlyByteBuf buffer) {
        return ResponseData.STREAM_CODEC.decode(buffer);
    }
}
