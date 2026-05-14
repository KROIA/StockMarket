package net.kroia.stockmarket.networking.request;

import net.kroia.banksystem.util.ItemID;
import net.kroia.modutilities.networking.ExtraCodecUtils;
import net.kroia.stockmarket.api.market.IServerMarket;
import net.kroia.stockmarket.stockmarket.market.core.TradingPair;
import net.kroia.stockmarket.util.MultiServerUtils;
import net.kroia.stockmarket.util.StockMarketGenericRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Returns all valid trading pairs (combinations of open markets) in canonical form.
 * <p>
 * Input: Integer (dummy, 0) <br>
 * Output: List of TradingPair - all N*(N-1)/2 canonical pairs from open markets
 */
public class GetAvailablePairsRequest extends StockMarketGenericRequest<Integer, List<TradingPair>> {

    @Override
    public String getRequestTypeID() {
        return GetAvailablePairsRequest.class.getName();
    }

    @Override
    protected List<TradingPair> getDefaultResponse() {
        return List.of();
    }

    @Override
    public CompletableFuture<List<TradingPair>> handleOnMasterServer(Integer input, String slaveID, UUID playerSender) {
        if (needsRoutingToMaster() && !MultiServerUtils.canInteractWithStockMarket(playerSender))
            return CompletableFuture.completedFuture(List.of());

        // Collect all open market IDs
        List<ItemID> openMarkets = new ArrayList<>();
        for (ItemID id : getServerMarketManager().getAvailableMarketIDs()) {
            IServerMarket m = getServerMarketManager().getMarket(id);
            if (m != null && m.isMarketOpen()) openMarkets.add(id);
        }

        // Generate all N*(N-1)/2 pairs in canonical form
        List<TradingPair> pairs = new ArrayList<>();
        for (int i = 0; i < openMarkets.size(); i++) {
            for (int j = i + 1; j < openMarkets.size(); j++) {
                pairs.add(new TradingPair(openMarkets.get(i), openMarkets.get(j)).canonical());
            }
        }

        return CompletableFuture.completedFuture(pairs);
    }

    @Override
    public void encodeInput(RegistryFriendlyByteBuf buf, Integer input) {
        ByteBufCodecs.INT.encode(buf, input);
    }

    @Override
    public void encodeOutput(RegistryFriendlyByteBuf buf, List<TradingPair> output) {
        ExtraCodecUtils.listStreamCodec(TradingPair.STREAM_CODEC).encode(buf, output);
    }

    @Override
    public Integer decodeInput(RegistryFriendlyByteBuf buf) {
        return ByteBufCodecs.INT.decode(buf);
    }

    @Override
    public List<TradingPair> decodeOutput(RegistryFriendlyByteBuf buf) {
        return ExtraCodecUtils.listStreamCodec(TradingPair.STREAM_CODEC).decode(buf);
    }
}
