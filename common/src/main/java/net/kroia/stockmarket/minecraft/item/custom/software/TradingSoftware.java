package net.kroia.stockmarket.minecraft.item.custom.software;

import net.kroia.banksystem.minecraft.block.custom.TerminalBlock;
import net.kroia.banksystem.minecraft.item.custom.software.Software;
import net.kroia.stockmarket.StockMarketMod;
import net.kroia.stockmarket.minecraft.block.StockMarketBlocks;
import net.minecraft.resources.ResourceLocation; // mc<=1.21.10
//import net.minecraft.resources.Identifier; // mc>=1.21.11
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class TradingSoftware extends Software {
    public static final String NAME = "trading_software";
    public TradingSoftware() {
        // Pass the registered id so the BankSystem base class can apply RegistryCompat (multi-version support).
        // Must match the id used in StockMarketItems registration.
        super(ResourceLocation.fromNamespaceAndPath(StockMarketMod.MOD_ID, NAME)); // mc<=1.21.10
        //super(Identifier.fromNamespaceAndPath(StockMarketMod.MOD_ID, NAME)); // mc>=1.21.11
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
            // todo: replace the functions below
            //SyncTradeItemsPacket.sendPacket(player);
            //OpenScreenPacket.sendPacket(player, OpenScreenPacket.ScreenType.STOCK_MARKET);
        }
    }
}
