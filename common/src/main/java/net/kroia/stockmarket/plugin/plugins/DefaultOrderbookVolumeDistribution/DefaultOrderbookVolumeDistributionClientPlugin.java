package net.kroia.stockmarket.plugin.plugins.DefaultOrderbookVolumeDistribution;

import net.kroia.stockmarket.plugin.base.ClientMarketPlugin;
import net.kroia.stockmarket.plugin.base.ClientMarketPluginGuiElement;
import net.minecraft.network.FriendlyByteBuf;

public class DefaultOrderbookVolumeDistributionClientPlugin extends ClientMarketPlugin {

    @Override
    protected void setup() {

    }

    @Override
    protected void onStreamPacketReceived(FriendlyByteBuf buf) {

    }

    @Override
    protected ClientMarketPluginGuiElement getSettingsGuiElement() {
        return null;
    }


    @Override
    protected void encodeSettings(FriendlyByteBuf buf) {

    }

    @Override
    protected void decodeSettings(FriendlyByteBuf buf) {

    }




}
