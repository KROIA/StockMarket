package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MarketsRequest extends StockMarketGenericRequest<Integer, List<ItemID>> {


    @Override
    public String getRequestTypeID() {
        return MarketsRequest.class.getName();
    }

    @Override
    public CompletableFuture<List<ItemID>> handleOnServer(Integer input, ServerPlayer sender) {
        CompletableFuture<List<ItemID>> future = new CompletableFuture<>();
        future.complete(getServerMarketManager().getAvailableMarketIDs());
        return future;
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, Integer input) {
        ByteBufCodecs.INT.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, List<ItemID> output) {
        ExtraCodecUtils.listStreamCodec(ItemID.STREAM_CODEC).encode(buf, output);
    }

    @Override
    public Integer decodeInput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.INT.decode(buf);
    }

    @Override
    public List<ItemID> decodeOutput(RegistryFriendlyByteBuf buf) {
        return ExtraCodecUtils.listStreamCodec(ItemID.STREAM_CODEC).decode(buf);
    }
}
