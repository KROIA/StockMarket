package net.kroia.stockmarket.networking.packet.server_sender.update;

import net.kroia.modutilities.PlayerUtilities;
import net.kroia.stockmarket.util.StockMarketClientHooks;
import net.kroia.stockmarket.util.StockMarketNetworkPacket;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class OpenScreenPacket extends StockMarketNetworkPacket {

    public enum ScreenType
    {
        BOT_SETTINGS,
        STOCKMARKET_MANAGEMENT,
        STOCK_MARKET
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
        if(screenType == ScreenType.STOCKMARKET_MANAGEMENT)
        {
            // check if player is in creative mode
            if(!player.isCreative())
            {
                PlayerUtilities.printToClientConsole(player, StockMarketTextMessages.getNeedCreativeModeForThisScreenMessage());
                return;
            }
        }
        packet.sendToClient(player);
    }
    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(screenType.name());
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
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
            case STOCK_MARKET:
                StockMarketClientHooks.openStockMarketBlockScreen();
        }
    }
}
