package net.kroia.stockmarket.networking.packet.server_sender.update;

import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.StockMarketClientHooks;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class OpenScreenPacket extends NetworkPacket {

    public enum ScreenType
    {
        BOT_SETTINGS,
        STOCKMARKET_MANAGEMENT
    }

    ScreenType screenType;
    private OpenScreenPacket()
    {
        super();
    }
    public OpenScreenPacket(FriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(ServerPlayer player, ScreenType screenType)
    {
        OpenScreenPacket packet = new OpenScreenPacket();
        packet.screenType = screenType;
        StockMarketNetworking.sendToClient(player, packet);
    }
    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(screenType.name());
    }

    @Override
    public void fromBytes(FriendlyByteBuf buf) {
        screenType = ScreenType.valueOf(buf.readUtf());
    }

    @Override
    protected void handleOnClient() {
        switch(screenType)
        {
            case BOT_SETTINGS:
                StockMarketClientHooks.openBotSettingsScreen();
                break;
            case STOCKMARKET_MANAGEMENT:
                StockMarketClientHooks.openStockMarketManagementScreen();
                break;
        }
    }
}
