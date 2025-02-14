package net.kroia.stockmarket.item.custom.software;

import net.kroia.banksystem.block.custom.TerminalBlock;
import net.kroia.banksystem.item.custom.software.Software;
import net.kroia.banksystem.networking.packet.server_sender.SyncOpenGUIPacket;
import net.kroia.stockmarket.StockMarketClientHooks;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.networking.packet.server_sender.update.OpenScreenPacket;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class TradingSoftware extends Software {
    public static final String NAME = "trading_software";
    public TradingSoftware() {
        super();
    }

    @Override
    public TerminalBlock getProgrammedBlock()
    {
        return StockMarketBlocks.STOCK_MARKET_BLOCK.get();
    }


    @Override
    protected void onRightClickedServerSide(ServerPlayer player)
    {
        if(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
            SyncTradeItemsPacket.sendPacket(player);
            OpenScreenPacket.sendPacket(player, OpenScreenPacket.ScreenType.STOCK_MARKET);
        }
    }
}
