package net.kroia.stockmarket;

import net.kroia.stockmarket.entity.custom.BankTerminalBlockEntity;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.screen.custom.BankTerminalScreen;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class ClientHooks {
    public static InteractionResult openStockMarketBlockScreen(BlockEntity entity, BlockPos pos)
    {
        if(entity instanceof StockMarketBlockEntity stockMarketBlockEntity)
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Minecraft.getInstance().setScreen(new TradeScreen(stockMarketBlockEntity)));
      }
        else
        {
            StockMarketMod.LOGGER.warn("Block entity at position: "+pos+" is not of type StockMarketBlockEntity");
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }
    public static InteractionResult openBankTerminalBlockScreen(BlockEntity entity, BlockPos pos, Inventory playerInventory)
    {
        if(entity instanceof BankTerminalBlockEntity bankTerminalBlockEntity)
        {
            //DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Minecraft.getInstance().setScreen(new BankTerminalScreen(bankTerminalBlockEntity, playerInventory)));
        }
        else
        {
            StockMarketMod.LOGGER.warn("Block entity at position: "+pos+" is not of type BankTerminalBlockEntity");
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }
}
