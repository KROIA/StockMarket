package net.kroia.stockmarket.networking.packet.server_sender.update;

import dev.architectury.networking.simple.MessageType;
import net.kroia.modutilities.networking.NetworkPacketS2C;
import net.kroia.stockmarket.StockMarketClientHooks;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class OpenScreenPacket extends NetworkPacketS2C {

    public enum ScreenType
    {
        BOT_SETTINGS,
        STOCKMARKET_MANAGEMENT
    }

    ScreenType screenType;

    @Override
    public MessageType getType() {
        return StockMarketNetworking.OPEN_SCREEN;
    }
    private OpenScreenPacket()
    {
        super();
    }



    public OpenScreenPacket(RegistryFriendlyByteBuf buf)
    {
        super(buf);
    }

    public static void sendPacket(ServerPlayer player, ScreenType screenType)
    {
        OpenScreenPacket packet = new OpenScreenPacket();
        packet.screenType = screenType;
        packet.sendTo(player);
    }
    @Override
    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(screenType.name());
    }

    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf) {
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
