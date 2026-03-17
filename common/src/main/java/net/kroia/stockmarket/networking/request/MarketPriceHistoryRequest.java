package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.client_server.streaming.GenericStream;
import net.kroia.stockmarket.data.filter.DateFilter;
import net.kroia.stockmarket.data.filter.EqualityFilter;
import net.kroia.stockmarket.data.table.record.MarketPriceStruct;
import net.kroia.stockmarket.util.PriceHistoryData;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.kroia.stockmarket.util.StockMarketGenericStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MarketPriceHistoryRequest extends StockMarketGenericStream<MarketPriceHistoryRequest.InputData, PriceHistoryData>
{

    public record InputData(ItemID item, long minTimestamp, long maxTimestamp)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                ItemID.STREAM_CODEC, p -> p.item,
                ByteBufCodecs.VAR_LONG, p -> p.minTimestamp,
                ByteBufCodecs.VAR_LONG, p -> p.maxTimestamp,
                InputData::new
        );
    }

    InputData inputData = null;
    PriceHistoryData data = null;

    @Override
    public GenericStream<InputData, PriceHistoryData> copy() {
        return new MarketPriceHistoryRequest();
    }

    @Override
    public String getStreamTypeID() {
        return MarketPriceHistoryRequest.class.getName();
    }

    @Override
    public void onStartStreamSendingOnSever() {
        inputData = getContextData();
        info("MarketPriceHistoryRequest started for item: " + inputData);
        CompletableFuture<List<MarketPriceStruct>>  fut = BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER.getHistory(
                        Optional.of(new DateFilter(inputData.minTimestamp, inputData.maxTimestamp)),
                        Optional.of(new EqualityFilter(inputData.item.getShort())), -1);
        fut.thenAccept(list -> {

            data = PriceHistoryData.fromSqlData(list, getCurrentMarketPrice(inputData.item));
            if(data == null)
            {
                warn("MarketPriceHistoryRequest failed to fetch data for item: " + inputData.item);
                stopStream();
            }
        });
    }

    @Override
    public void onStopStreamSendingOnServer() {

    }

    @Override
    protected void updateOnServer(){
        if(data != null)
        {
            sendPacket();
            stopStream(); // Stop the stream since it is not used as stream but as async request
        }
    }

    @Override
    public PriceHistoryData provideStreamPacketOnServer()
    {
        return data;
    }

    @Override
    public void encodeContextData(RegistryFriendlyByteBuf buffer, InputData context) {
        InputData.STREAM_CODEC.encode(buffer, context);
    }

    @Override
    public InputData decodeContextData(RegistryFriendlyByteBuf buffer) {
        return InputData.STREAM_CODEC.decode(buffer);
    }

    @Override
    public void encodeData(RegistryFriendlyByteBuf buffer, PriceHistoryData priceHistoryData) {
        PriceHistoryData.STREAM_CODEC.encode(buffer, priceHistoryData);
    }

    @Override
    public PriceHistoryData decodeData(RegistryFriendlyByteBuf buffer) {
        return PriceHistoryData.STREAM_CODEC.decode(buffer);
    }
}
