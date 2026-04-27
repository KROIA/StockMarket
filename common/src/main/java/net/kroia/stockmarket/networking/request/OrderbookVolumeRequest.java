package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class OrderbookVolumeRequest extends StockMarketGenericRequest<OrderbookVolumeRequest.InputData, OrderbookVolumeRequest.OutputData> {



    public record InputData(ItemID marketID, double startPrice, double endPrice, int chunkCount)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, InputData> STREAM_CODEC = StreamCodec.composite(
                ItemID.STREAM_CODEC, p -> p.marketID,
                ByteBufCodecs.DOUBLE, p -> p.startPrice,
                ByteBufCodecs.DOUBLE, p -> p.endPrice,
                ByteBufCodecs.INT, p -> p.chunkCount,
                InputData::new
        );
    }
    public record OutputData(ItemID marketID, double startPrice, double endPrice, float[] volumes)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ItemID.STREAM_CODEC, p -> p.marketID,
                ByteBufCodecs.DOUBLE, p -> p.startPrice,
                ByteBufCodecs.DOUBLE, p -> p.endPrice,
                ExtraCodecUtils.FLOAT_ARRAY_CODEC, p -> p.volumes,
                OutputData::new
        );
    }


    @Override
    public String getRequestTypeID() {
        return OrderbookVolumeRequest.class.getName();
    }

    @Override
    protected OutputData getDefaultResponse() {
        return new OutputData(ItemID.INVALID_ID, 0.0,0.0, new float[0]);
    }

    public CompletableFuture<OutputData> handleOnMasterServer(InputData input, String slaveID, @Nullable UUID playerSender) {
        final int maxChunkCount = 1000;

        IServerMarket market = getServerMarketManager().getMarket(input.marketID);
        if(market == null || input.chunkCount <= 0)
        {
            return CompletableFuture.completedFuture(new OutputData(input.marketID, input.startPrice, input.endPrice, new float[0]));
        }
        long startPrice = realToBackendValue(Math.max(0, input.startPrice));
        long endPrice = realToBackendValue(Math.max(0, input.endPrice));

        if(startPrice > endPrice)
        {
            // Swap prices
            long tmp = startPrice;
            startPrice = endPrice;
            endPrice = tmp;
        }

        int chunkCount = Math.min(input.chunkCount, maxChunkCount);
        long priceRange = endPrice - startPrice;
        if(chunkCount > priceRange)
            chunkCount = (int)priceRange;

        if(chunkCount <= 0)
        {
            return CompletableFuture.completedFuture(new OutputData(input.marketID, input.startPrice, input.endPrice, new float[0]));
        }

        // Check if the price range can be split into the chunks, otherwise adjust add some
        // data points by increasing the end price
        long modC = priceRange % chunkCount;
        long chunkSize = Math.max(1, priceRange / chunkCount);
        if(modC != 0) {
            if(priceRange / chunkSize > maxChunkCount)
            {
                chunkSize++;
            }
            else
            {
                chunkCount = (int)(priceRange / chunkSize);
            }
            endPrice = startPrice + chunkSize * chunkCount;
        }
        priceRange = endPrice - startPrice;

        float[] array = new float[chunkCount];

        int arrayIndex = 0;
        long p1 = startPrice;
        //for(long price = startPrice; price < endPrice; price+=chunkSize)
        for(int i = 0; i < chunkCount; i++)
        {

            long p2 = p1 + chunkSize-1;
            array[i] = market.getRawVolume(p1, p2);
            p1 = p2+1;
        }
        endPrice = startPrice + chunkSize * chunkCount;

        double realStartPrice = backendToRealValue(startPrice);
        double realEndPrice = backendToRealValue(endPrice);

        return CompletableFuture.completedFuture(new OutputData(input.marketID, realStartPrice, realEndPrice, array));
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, InputData input) {
        InputData.STREAM_CODEC.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, OutputData output) {
        OutputData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public InputData decodeInput(RegistryFriendlyByteBuf buf) {
        return InputData.STREAM_CODEC.decode(buf);
    }

    @Override
    public OutputData decodeOutput(RegistryFriendlyByteBuf buf) {
        return OutputData.STREAM_CODEC.decode(buf);
    }
}
