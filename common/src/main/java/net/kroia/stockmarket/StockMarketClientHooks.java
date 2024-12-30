package net.kroia.stockmarket;

import net.kroia.banksystem.entity.custom.BankTerminalBlockEntity;
import net.kroia.banksystem.screen.custom.BankSystemSettingScreen;
import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.kroia.stockmarket.screen.custom.TradeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

public class StockMarketClientHooks {
    public static InteractionResult openStockMarketBlockScreen(BlockEntity entity, BlockPos pos)
    {
        if(entity instanceof StockMarketBlockEntity stockMarketBlockEntity)
        {
            Minecraft.getInstance().submit(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                TradeScreen screen = new TradeScreen(stockMarketBlockEntity);
                //screen.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                minecraft.setScreen(screen);
            });
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
