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

public class MarketPriceHistoryRequest extends StockMarketGenericRequest<MarketPriceHistoryRequest.InputData, PriceHistoryData>
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

    @Override
    public String getRequestTypeID() {
        return MarketPriceHistoryRequest.class.getName();
    }


    @Override
    public CompletableFuture<PriceHistoryData> handleOnServer(InputData input, ServerPlayer sender)
    {
        CompletableFuture<PriceHistoryData> future = new CompletableFuture<>();
        info("MarketPriceHistoryRequest started for item: " + input);
        CompletableFuture<List<MarketPriceStruct>>  fut = BACKEND_INSTANCES.MARKET_PRICE_HISTORY_MANAGER.getHistory(
                Optional.of(new DateFilter(input.minTimestamp, input.maxTimestamp)),
                Optional.of(new EqualityFilter(input.item.getShort())), -1);
        fut.thenAccept(list -> {

            PriceHistoryData data = PriceHistoryData.fromSqlData(list, getCurrentMarketPrice(input.item));
            if(data == null)
            {
                warn("MarketPriceHistoryRequest failed to fetch data for item: " + input.item);
            }
            future.complete(data);
        });
        return future;
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, PriceHistoryData output) {
        PriceHistoryData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public PriceHistoryData decodeOutput(RegistryFriendlyByteBuf buf) {
        return PriceHistoryData.STREAM_CODEC.decode(buf);
    }
}
