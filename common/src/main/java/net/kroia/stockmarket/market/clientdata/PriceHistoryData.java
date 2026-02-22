package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.util.PriceHistory;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class PriceHistoryData implements INetworkPayloadEncoder {

    private final PriceHistory history;


    public PriceHistoryData(@NotNull PriceHistory history, int historyViewCount)
    {
        this.history = PriceHistory.copy(history, historyViewCount);
    }
    public PriceHistoryData(@NotNull PriceHistory history)
    {
        this.history = PriceHistory.copy(history, -1);
    }

    public PriceHistory toHistory() {
        return history;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        history.encode(buf);
    }

    public static PriceHistoryData decode(FriendlyByteBuf buf) {
        PriceHistory history = new PriceHistory(0);
        history.decode(buf);
        return new PriceHistoryData(history);
    }
}
