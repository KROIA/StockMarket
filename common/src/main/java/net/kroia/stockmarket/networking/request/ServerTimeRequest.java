package net.kroia.stockmarket.networking.request;

import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ServerTimeRequest extends StockMarketGenericRequest<Long, ServerTimeRequest.OutputData> {

    public record OutputData(long serverTime, long clientTimeEcho)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, OutputData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, p -> p.serverTime,
                ByteBufCodecs.VAR_LONG, p -> p.clientTimeEcho,
                OutputData::new
        );
    }

    @Override
    public String getRequestTypeID() {
        return ServerTimeRequest.class.getName();
    }

    @Override
    public CompletableFuture<OutputData> handleOnMasterServer(Long input, String slaveID, @Nullable UUID playerSender) {
        return CompletableFuture.completedFuture(new OutputData(System.currentTimeMillis(), input));
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, Long input) {
        ByteBufCodecs.VAR_LONG.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, OutputData output) {
        OutputData.STREAM_CODEC.encode(buf, output);
    }

    @Override
    public Long decodeInput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.VAR_LONG.decode(buf);
    }

    @Override
    public OutputData decodeOutput(RegistryFriendlyByteBuf buf) {
        return OutputData.STREAM_CODEC.decode(buf);
    }
}
