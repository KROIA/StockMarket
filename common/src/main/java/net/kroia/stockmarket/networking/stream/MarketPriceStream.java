package net.kroia.stockmarket.networking.stream;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.streaming.GenericStream;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.api.marketmanager.IServerMarketManager;
import net.kroia.stockmarket.stockmarket.market.ServerMarket;
import net.kroia.stockmarket.stockmarket.marketmanager.ServerMarketManager;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

public class MarketPriceStream extends StockMarketGenericStream<ItemID, MarketPriceStream.ResponseData>
{
    public static class ResponseData
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, ResponseData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, p -> p.timestamp,
                ByteBufCodecs.VAR_LONG, p -> p.marketPrice,
                ByteBufCodecs.FLOAT, p -> p.tradedVolume,
                ResponseData::new
        );

        public long timestamp;
        public long marketPrice;
        public float tradedVolume;

        public ResponseData(long timestamp, long marketPrice, float tradedVolume)
        {
            this.timestamp = timestamp;
            this.marketPrice = marketPrice;
            this.tradedVolume = tradedVolume;
        }
    }

    private long updateInterval = 100;
    private long lastTimeMs = System.currentTimeMillis();
    private ItemID itemID;
    private final ResponseData lastPrice = new ResponseData(-1,-1, 0f);

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
        //info("MarketPriceStream started for item: " + itemID);
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
            @Nullable IServerMarketManager manager = getMarketManager();
            if(manager == null) {
                stopStream();
                return;
            }
            IServerMarket serverMarket = manager.getMarket(itemID);
            if(serverMarket != null) {
                 long currentPrice = serverMarket.getCurrentMarketPrice();
                 long currentTime = serverMarket.getCurrentTime();
                 float currentVolume = serverMarket.getCurrentCandleTradedVolume();
                 if(currentPrice != lastPrice.marketPrice ||
                    currentTime != lastPrice.timestamp ||
                    currentVolume != lastPrice.tradedVolume) {
                     lastPrice.marketPrice = currentPrice;
                     lastPrice.timestamp = currentTime;
                     lastPrice.tradedVolume = currentVolume;
                     sendPacket();
                 }
            }
        }
    }

    @Override
    public ResponseData provideStreamPacketOnServer() {
        //info("MarketPriceStream provided for item: " + itemID + " price: " + lastPrice.marketPrice);
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
