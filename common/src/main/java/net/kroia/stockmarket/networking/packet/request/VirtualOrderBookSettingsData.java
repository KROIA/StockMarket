package net.kroia.stockmarket.networking.packet.request;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.clientdata.TradingPairData;
import net.kroia.stockmarket.market.server.VirtualOrderBook;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class VirtualOrderBookSettingsData implements INetworkPayloadEncoder {

    public final TradingPairData tradingPairData;
    public VirtualOrderBook.Settings settings;
    public VirtualOrderBookSettingsData(@NotNull TradingPair pair, @NotNull VirtualOrderBook.Settings settings) {
        this.tradingPairData = new TradingPairData(pair);
        this.settings = settings;
    }

    public VirtualOrderBookSettingsData(FriendlyByteBuf buf)
    {
        this.tradingPairData = TradingPairData.decode(buf);
        this.settings = new VirtualOrderBook.Settings();
        this.settings.decode(buf);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        tradingPairData.encode(buf);
        settings.encode(buf);
    }

    public static VirtualOrderBookSettingsData decode(FriendlyByteBuf buf) {
        return new VirtualOrderBookSettingsData(buf);
    }
}
