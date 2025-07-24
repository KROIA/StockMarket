package net.kroia.stockmarket.networking.packet.server_sender.update;

import net.kroia.banksystem.util.BankSystemTextMessages;
import net.kroia.modutilities.PlayerUtilities;
import net.kroia.modutilities.networking.NetworkPacket;
import net.kroia.stockmarket.StockMarketClientHooks;
import net.kroia.stockmarket.networking.StockMarketNetworking;
import net.kroia.stockmarket.util.StockMarketTextMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class OpenScreenPacket extends NetworkPacket {

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
            case STOCK_MARKET:
                StockMarketClientHooks.openStockMarketBlockScreen();
        }
    }
}
