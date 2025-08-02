package net.kroia.stockmarket.market.clientdata;

import net.kroia.modutilities.networking.INetworkPayloadEncoder;
import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.market.server.bot.ServerVolatilityBot;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

public class BotSettingsData implements INetworkPayloadEncoder {

    public final TradingPairData tradingPairData;
    public ServerVolatilityBot.Settings settings;
    public BotSettingsData(@NotNull TradingPair pair, @NotNull ServerVolatilityBot.Settings settings) {
        this.tradingPairData = new TradingPairData(pair);
        this.settings = settings;
    }

    public BotSettingsData(FriendlyByteBuf buf)
    {
        this.tradingPairData = TradingPairData.decode(buf);
        this.settings = new ServerVolatilityBot.Settings();
        this.settings.decode(buf);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        tradingPairData.encode(buf);
        settings.encode(buf);
    }

    public static BotSettingsData decode(FriendlyByteBuf buf) {
        return new BotSettingsData(buf);
    }
}
