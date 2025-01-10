package net.kroia.stockmarket.item.custom.software;

import net.kroia.banksystem.block.custom.TerminalBlock;
import net.kroia.banksystem.item.custom.software.Software;
import net.kroia.stockmarket.StockMarketClientHooks;
import net.kroia.stockmarket.block.StockMarketBlocks;
import net.kroia.stockmarket.networking.packet.server_sender.update.SyncTradeItemsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

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
    protected void onRightClickedClientSide()
    {
        if(Minecraft.getInstance().player.hasPermissions(2))
            StockMarketClientHooks.openStockMarketBlockScreen();
    }

    @Override
    protected void onRightClickedServerSide(ServerPlayer player)
    {
        SyncTradeItemsPacket.sendPacket(player);
    }
}
