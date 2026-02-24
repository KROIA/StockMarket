package net.kroia.stockmarket.plugin.plugins.MarketTrend;

import net.kroia.stockmarket.market.TradingPair;
import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.minecraft.network.FriendlyByteBuf;

public class MarketTrendClientPlugin extends ClientMarketPlugin {

    public MarketTrendClientPlugin(TradingPair tradingPair, String pluginTypeID) {
        super(tradingPair, pluginTypeID);
    }

    @Override
    protected void close() {

    }

    @Override
    protected void onStreamPacketReceived(FriendlyByteBuf buf) {

    }

    @Override
    protected void setup() {

    }
}
