package net.kroia.stockmarket;

import net.kroia.stockmarket.entity.custom.StockMarketBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;

public class StockMarketClientHooks {
    public static InteractionResult openStockMarketBlockScreen(BlockEntity entity, BlockPos pos)
    {
        if(entity instanceof StockMarketBlockEntity stockMarketBlockEntity)
        {
            // todo: replace this
            //Minecraft.getInstance().submit(() -> {
            //    TradeScreen.openScreen(stockMarketBlockEntity);
            //});
        }
        else
        {
            StockMarketMod.LOGGER.warn("Block entity at position: "+pos+" is not of type StockMarketBlockEntity");
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }
    public static void openStockMarketBlockScreen()
    {
        // todo: replace this
        //Minecraft.getInstance().submit(() -> {
        //    TradeScreen.openScreen();
        //});
    }
    public static void openBotSettingsScreen()
    {
        // todo: replace this
        //Minecraft.getInstance().submit(()->{BotSettingsScreen.openScreen();});
    }
    public static void openStockMarketManagementScreen()
    {
        // todo: replace this
        //Minecraft.getInstance().submit(()->{
        //    StockMarketManagementScreen.openScreen();});
    }
}
